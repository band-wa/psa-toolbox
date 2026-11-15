package com.bandwa.openadb.util.apktools

import android.annotation.SuppressLint
import com.android.tools.smali.baksmali.Baksmali
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31c
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedStringReference
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.android.zipflinger.BytesSource
import com.android.zipflinger.Source
import com.android.zipflinger.ZipArchive
import com.android.zipflinger.ZipSource
import java.io.File
import java.nio.file.Paths
import java.util.zip.Deflater
import java.util.zip.ZipFile

/** 选择性反汇编 + DexPool 重建 + zipflinger 零拷贝重打包的快速 APK 修补器。 */
class FastApkPacker(
    private val apkPath: String,
    private val outputApk: File,
    private val targetTypes: List<String>,
    private val skipEntry: (String) -> Boolean,
    private val applyModifications: (File) -> Boolean,
    private val extraClasses: List<ClassDef> = emptyList(),
    private val injections: List<DexInjector.Injection> = emptyList(),
    private val onError: ((Exception) -> Unit)? = null,
) {
    companion object {
        private val META_SIG = Regex("^META-INF/.*\\.(SF|RSA|DSA|MF)$")
        private val DEX_ENTRY = Regex("classes\\d*\\.dex")
        private const val ALIGN_4K = 4096L

        private const val EXTRAS_ROOM = 512

        private const val JUMBO_STRING_LIMIT = 65000

        // native 库与旧签名文件不参与重打包
        fun defaultSkip(name: String): Boolean =
            name.startsWith("lib/") || META_SIG.matches(name)

        fun classTypeFromSmaliPath(path: String): String? {
            if (!path.endsWith(".smali")) return null
            val p = path.removePrefix("smali/").removeSuffix(".smali")
            if (p.isEmpty() || p.startsWith("/") || p.contains("..")) return null
            return "L$p;"
        }

        fun smaliRelPath(type: String): String =
            type.removePrefix("L").removeSuffix(";") + ".smali"

        private fun dexVersion(bytes: ByteArray): Int {
            if (bytes.size < 8) return 35
            val v = String(bytes, 4, 3, Charsets.US_ASCII)
            return v.trim().toIntOrNull() ?: 35
        }
    }

    fun pack(): Boolean {
        return try {
            val types = targetTypes.distinct()
            if (types.isEmpty()) {
                return false
            }

            val outerQueries = types.mapNotNull { t ->
                val path = t.removePrefix("L").removeSuffix(";")
                val outer = path.substringBeforeLast('$', "")
                if (outer.isNotEmpty() && path != outer) "L$outer;" else null
            }.filter { it !in types }.distinct()
            val locatedAll = locateDexForClasses(types + outerQueries)
            val dexForExisting = locatedAll.filterKeys { it in types.toSet() }
            val newTypes = types.filterNot { it in dexForExisting }

            val dexForNew = assignNewTypes(locatedAll, newTypes)
            if (dexForNew == null) {
                return false
            }

            val existingByDex = LinkedHashMap<String, MutableList<String>>()
            for ((t, dex) in dexForExisting) existingByDex.getOrPut(dex) { mutableListOf() }.add(t)
            val newByDex = LinkedHashMap<String, MutableList<String>>()
            for ((t, dex) in dexForNew) newByDex.getOrPut(dex) { mutableListOf() }.add(t)

            val replaced = LinkedHashMap<String, ByteArray>()

            // hook 类优先放字段/方法余量最大的 dex，余量不足时整体搬迁注入目标类
            val primaryDex = chooseExtrasDex(existingByDex.keys)

            val relocateOut = LinkedHashMap<String, Set<String>>()
            for ((dexName, types) in existingByDex) {
                val injected = injections.filter { it.classType in types }
                if (injected.isEmpty()) continue
                val needed = injected.flatMap { inj -> inj.ops.mapNotNull { it.callee } }.toSet().size
                if (dexRoom(dexName) < needed + 8) {
                    relocateOut[dexName] = injected.map { it.classType }.toSet()
                }
            }
            val hostForSource = relocateOut.mapValues { (source, types) ->
                chooseRelocationHost(source, types, primaryDex, relocateOut.keys)
            }
            val workDir = File(outputApk.parentFile, "fastpack_${System.currentTimeMillis()}").apply {
                mkdirs()
            }
            try {
                val dexNames = (existingByDex.keys + newByDex.keys + listOfNotNull(primaryDex) +
                    hostForSource.values).distinct()
                if (relocateOut.isEmpty()) {
                    val pool = java.util.concurrent.Executors.newFixedThreadPool(dexNames.size.coerceAtMost(4))
                    try {
                        val futures = dexNames.map { dexName ->
                            val existingForDex = existingByDex[dexName].orEmpty()
                            val newForDex = newByDex[dexName].orEmpty()
                            val extras = if (dexName == primaryDex) extraClasses else emptyList()
                            dexName to pool.submit<ByteArray> {
                                rebuildDex(dexName, existingForDex, newForDex, workDir, extras).bytes
                            }
                        }
                        for ((dexName, future) in futures) {
                            replaced[dexName] = future.get()
                        }
                    } finally {
                        pool.shutdown()
                    }
                } else {
                    val relocatedDefs = LinkedHashMap<String, MutableList<ClassDef>>()
                    val ordered = dexNames.filter { it in relocateOut } + dexNames.filter { it !in relocateOut }
                    for (dexName in ordered) {
                        val extras = (if (dexName == primaryDex) extraClasses else emptyList()) +
                            relocatedDefs[dexName].orEmpty()
                        val result = rebuildDex(
                            dexName,
                            existingByDex[dexName].orEmpty(),
                            newByDex[dexName].orEmpty(),
                            workDir,
                            extras,
                            relocateOut[dexName].orEmpty(),
                        )
                        replaced[dexName] = result.bytes
                        if (result.relocated.isNotEmpty()) {
                            val host = hostForSource.getValue(dexName)
                            relocatedDefs.getOrPut(host) { mutableListOf() }.addAll(result.relocated)
                        }
                    }
                }

                repack(replaced)
            } finally {
                workDir.deleteRecursively()
            }
            true
        } catch (e: Exception) {
            onError?.invoke(e)
            false
        }
    }

    private fun assignNewTypes(located: Map<String, String>, newTypes: List<String>): Map<String, String>? {
        val assigned = LinkedHashMap<String, String>()
        val mainDex = located.entries.groupBy({ it.value }) { it.key }
            .maxByOrNull { it.value.size }?.key
        val pending = newTypes.toMutableList()

        var progressed = true
        while (pending.isNotEmpty() && progressed) {
            progressed = false
            val iter = pending.iterator()
            while (iter.hasNext()) {
                val newType = iter.next()
                val path = newType.removePrefix("L").removeSuffix(";")
                var dex: String? = null
                val outerPath = path.substringBeforeLast('$', "")
                if (outerPath.isNotEmpty() && outerPath != path) {
                    dex = located["L$outerPath;"] ?: assigned["L$outerPath;"]
                }
                if (dex == null) {
                    val pkg = path.substringBeforeLast('/', "")
                    if (pkg.isNotEmpty()) {
                        val samePkg = located.keys.firstOrNull {
                            it.removePrefix("L").removeSuffix(";").substringBeforeLast('/') == pkg
                        }
                        dex = samePkg?.let { located[it] }
                    }
                }
                if (dex == null) dex = mainDex
                if (dex != null) {
                    assigned[newType] = dex
                    iter.remove()
                    progressed = true
                }
            }
        }
        return if (pending.isEmpty()) assigned else null
    }

    private fun locateDexForClasses(types: List<String>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val remaining = types.toMutableSet()
        ZipFile(apkPath).use { zf ->
            for (entry in zf.entries()) {
                if (!DEX_ENTRY.matches(entry.name)) continue
                if (remaining.isEmpty()) break
                val bytes = zf.getInputStream(entry).use { it.readBytes() }
                val dex = DexBackedDexFile(Opcodes.getDefault(), bytes)
                for (cls in dex.classes) {
                    val t = cls.type
                    if (t in remaining) {
                        result[t] = entry.name
                        remaining.remove(t)
                    }
                }
            }
        }
        return result
    }

    private fun chooseExtrasDex(affected: Collection<String>): String? {
        if (extraClasses.isEmpty()) return null
        val bestAffected = affected.maxByOrNull { dexRoom(it) }
        if (bestAffected != null && dexRoom(bestAffected) >= EXTRAS_ROOM) return bestAffected
        val all = ZipFile(apkPath).use { zf ->
            zf.entries().asSequence().map { it.name }.filter { DEX_ENTRY.matches(it) }.toList()
        }
        return all.maxByOrNull { dexRoom(it) } ?: bestAffected
    }

    private fun dexRoom(dexName: String): Int = try {
        val dex = DexBackedDexFile(Opcodes.getDefault(), dexBytes(dexName))
        minOf(0x10000 - dex.fieldSection.size, 0x10000 - dex.methodSection.size)
    } catch (e: Exception) {
        Int.MIN_VALUE
    }

    private fun classFootprint(dexName: String, types: Set<String>): Int = try {
        DexBackedDexFile(Opcodes.getDefault(), dexBytes(dexName)).classes
            .filter { it.type in types }
            .sumOf { c -> c.methods.count() + c.fields.count() }
    } catch (e: Exception) {
        types.size * 64
    }

    private fun chooseRelocationHost(
        source: String,
        types: Set<String>,
        primaryDex: String?,
        exclude: Set<String>,
    ): String {
        val footprint = classFootprint(source, types)
        val extrasFootprint = extraClasses.sumOf { it.methods.count() + it.fields.count() }
        val all = ZipFile(apkPath).use { zf ->
            zf.entries().asSequence().map { it.name }.filter { DEX_ENTRY.matches(it) }.toList()
        }
        val candidates = all.filter { it != source && it !in exclude }
        val best = candidates.maxByOrNull { dexRoom(it) }
            ?: throw IllegalStateException("No dex available to host relocated classes from $source")
        val required = footprint + EXTRAS_ROOM + if (best == primaryDex) extrasFootprint else 0
        if (dexRoom(best) < required) {
            throw IllegalStateException(
                "No dex has room for ${types.size} relocated class(es) from $source " +
                    "(need $required, best $best has ${dexRoom(best)})"
            )
        }
        return best
    }

    private fun dexBytes(dexName: String): ByteArray = ZipFile(apkPath).use { zf ->
        val e = zf.getEntry(dexName) ?: throw IllegalStateException("$dexName not found")
        zf.getInputStream(e).use { it.readBytes() }
    }

    private fun needsJumbo(cls: ClassDef): Boolean = cls.methods.any { m ->
        m.implementation?.instructions?.any insns@{ insn ->
            if (insn.opcode != Opcode.CONST_STRING) return@insns false
            val ref = (insn as? ReferenceInstruction)?.reference as? StringReference
                ?: return@insns false
            val index = (ref as? DexBackedStringReference)?.stringIndex ?: Int.MAX_VALUE
            index >= JUMBO_STRING_LIMIT
        } == true
    }

    private fun jumboClass(cls: ClassDef, force: Boolean = false): ClassDef {
        if (!force && !needsJumbo(cls)) return cls
        val methods = cls.methods.map { jumboMethod(it, force) }
        return ImmutableClassDef(
            cls.type,
            cls.accessFlags,
            cls.superclass,
            cls.interfaces,
            cls.sourceFile,
            cls.annotations,
            cls.staticFields,
            cls.instanceFields,
            methods.filter { isDirect(it) },
            methods.filter { !isDirect(it) },
        )
    }

    private fun jumboMethod(m: Method, force: Boolean = false): Method {
        val impl = m.implementation ?: return m
        var mutable: MutableMethodImplementation? = null
        for ((i, insn) in impl.instructions.withIndex()) {
            if (insn.opcode != Opcode.CONST_STRING) continue
            val ref = (insn as? ReferenceInstruction)?.reference as? StringReference ?: continue
            val index = (ref as? DexBackedStringReference)?.stringIndex ?: Int.MAX_VALUE
            if (!force && index < JUMBO_STRING_LIMIT) continue
            val target = mutable ?: MutableMethodImplementation(impl).also { mutable = it }
            val reg = (insn as OneRegisterInstruction).registerA
            target.replaceInstruction(i, BuilderInstruction31c(Opcode.CONST_STRING_JUMBO, reg, ref))
        }
        val target = mutable ?: return m
        return ImmutableMethod(
            m.definingClass,
            m.name,
            m.parameters,
            m.returnType,
            m.accessFlags,
            m.annotations,
            m.hiddenApiRestrictions,
            target,
        )
    }

    private fun isDirect(m: Method): Boolean {
        val flags = m.accessFlags
        val mask = AccessFlags.STATIC.value or AccessFlags.PRIVATE.value or AccessFlags.CONSTRUCTOR.value
        return flags and mask != 0
    }

    private class DexRebuild(val bytes: ByteArray, val relocated: List<ClassDef>)

    private fun rebuildDex(
        dexName: String,
        existingTypes: List<String>,
        newTypes: List<String>,
        workDir: File,
        extraClasses: List<ClassDef>,
        relocateOut: Set<String> = emptySet(),
    ): DexRebuild {
        val bytes = ZipFile(apkPath).use { zf ->
            val e = zf.getEntry(dexName) ?: throw IllegalStateException("$dexName not found")
            zf.getInputStream(e).use { it.readBytes() }
        }
        val opcodes = Opcodes.forDexVersion(dexVersion(bytes))
        val smaliDir = File(workDir, dexName).apply { mkdirs() }

        val options = BaksmaliOptions().apply {
            deodex = false
            implicitReferences = false
            parameterRegisters = true
            localsDirective = true
            sequentialLabels = true
            debugInfo = false
            codeOffsets = false
            accessorComments = false
            registerInfo = 0
            inlineResolver = null
        }
        val threads = minOf(Runtime.getRuntime().availableProcessors(), 6).coerceAtLeast(1)
        val dexFile = DexBackedDexFile(opcodes, bytes)
        if (existingTypes.isNotEmpty()) {
            val ok = Baksmali.disassembleDexFile(dexFile, smaliDir, threads, options, existingTypes)
            if (!ok) throw IllegalStateException("Selective disassembly failed: $dexName")
        }

        if (existingTypes.isNotEmpty() || newTypes.isNotEmpty()) {
            if (!applyModifications(smaliDir)) {
                throw IllegalStateException("Not all patches were applied: $dexName")
            }
        }

        val allTypes = existingTypes + newTypes
        val smaliFiles = ArrayList<File>(allTypes.size)
        for (t in allTypes) {
            val f = File(smaliDir, smaliRelPath(t))
            if (!f.isFile) throw IllegalStateException("Missing smali file: $f")
            smaliFiles.add(f)
        }
        val defByType = if (smaliFiles.isEmpty()) {
            emptyMap()
        } else {
            SmaliAssembler.assembleToClassDefs(smaliFiles, opcodes)
        }
        val injectedDefs = DexInjector.apply(
            defByType.filterKeys { it in allTypes },
            injections,
        )
        val modifiedDefs = ArrayList<ClassDef>(allTypes.size)
        for (t in allTypes) {
            modifiedDefs.add(injectedDefs[t] ?: throw IllegalStateException("Assembled result is missing class: $t"))
        }

        val pool = DexPool(opcodes)
        val modifiedSet = allTypes.toSet()

        val jumbo = dexFile.stringSection.size > JUMBO_STRING_LIMIT
        for (cls in dexFile.classes) {
            if (cls.type !in modifiedSet) pool.internClass(jumboClass(cls, jumbo))
        }
        val relocated = ArrayList<ClassDef>()
        for (cls in modifiedDefs) {
            if (cls.type in relocateOut) {
                for (inj in injections) {
                    if (inj.classType != cls.type) continue
                    val method = cls.methods.firstOrNull { it.name == inj.methodName }
                    val injected = method?.implementation?.instructions?.any { insn ->
                        val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference
                        ref != null && inj.ops.any { it.callee == ref.toString() }
                    } ?: false
                    if (!injected) {
                        throw IllegalStateException("Injection not applied: ${inj.classType}->${inj.methodName}${inj.proto}")
                    }
                }
                relocated.add(jumboClass(cls, jumbo))
            } else {
                pool.internClass(jumboClass(cls, jumbo))
            }
        }

        val presentTypes = dexFile.classes.mapTo(HashSet()) { it.type }
        presentTypes.addAll(allTypes)
        for (cls in extraClasses) {
            if (presentTypes.add(cls.type)) {
                pool.internClass(jumboClass(cls, jumbo))
            }
        }

        val outDex = File(smaliDir, "out.dex")
        pool.writeTo(FileDataStore(outDex))
        if (injections.isNotEmpty()) {
            val written = DexBackedDexFile(opcodes, outDex.readBytes())
            for (inj in injections) {
                if (inj.classType in relocateOut) continue
                val cls = written.classes.firstOrNull { it.type == inj.classType } ?: continue
                val method = cls.methods.firstOrNull { it.name == inj.methodName }
                val injected = method?.implementation?.instructions?.any { insn ->
                    val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference
                    ref != null && inj.ops.any { it.callee == ref.toString() }
                } ?: false
                if (!injected) {
                    throw IllegalStateException("Injection not applied: ${inj.classType}->${inj.methodName}${inj.proto}")
                }
            }
        }
        return DexRebuild(outDex.readBytes(), relocated)
    }

    @SuppressLint("NewApi")
    private fun repack(replaced: Map<String, ByteArray>) {
        val outTmp = File(outputApk.parentFile, outputApk.name + ".tmp")
        outTmp.delete()
        ZipArchive(outTmp.toPath()).use { archive ->
            val src = ZipSource(Paths.get(apkPath))
            for (e in src.entries().values) {
                val name = e.name

                if (replaced.containsKey(name) || skipEntry(name)) continue

                val alignment = if (e.isCompressed) Source.NO_ALIGNMENT else ALIGN_4K
                src.select(name, name, ZipSource.COMPRESSION_NO_CHANGE, alignment)
            }
            archive.add(src)

            for ((name, bytes) in replaced) {
                archive.add(BytesSource(bytes, name, Deflater.BEST_SPEED))
            }
        }
        if (outputApk.exists()) outputApk.delete()
        if (!outTmp.renameTo(outputApk)) {
            outTmp.copyTo(outputApk, overwrite = true)
            outTmp.delete()
        }
    }
}
