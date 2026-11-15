package com.bandwa.openadb.util.apktools

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.bandwa.openadb.service.install.ModApk
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.zip.ZipFile

class PatchRegressionTest {
    private val projectRoot: File = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile }
        ?: File(System.getProperty("user.dir") ?: ".")
    private val assetsRoot = File(projectRoot, "app/src/main/assets")
    private val fixturesRoot = System.getProperty("patch.fixtures")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
    private val limit = System.getProperty("patch.fixtures.limit")?.toIntOrNull() ?: Int.MAX_VALUE

    private val packages = listOf("com.cusc.misc", "com.android.launcher")

    @Test
    fun patchAllFixtureApks() {
        assumeTrue("patch.fixtures not set", fixturesRoot != null)
        assumeTrue("fixtures dir not found: $fixturesRoot", fixturesRoot!!.isDirectory)

        val hookClasses = loadHookClasses()
        assumeTrue("hook.dex not built", hookClasses.isNotEmpty())

        val failures = StringBuilder()
        var total = 0

        for (pkg in packages) {
            val configFile = File(assetsRoot, "modapk/$pkg/config.json")
            assumeTrue("config not found: $configFile", configFile.isFile)
            val configText = configFile.readText()

            val apks = File(fixturesRoot, pkg).listFiles()
                ?.filter { it.isDirectory }
                ?.flatMap { dir -> dir.listFiles { f -> f.isFile && f.name.endsWith(".apk") }?.toList() ?: emptyList() }
                ?.sortedBy { it.absolutePath }
                ?.take(limit)
                ?: emptyList()

            for (apk in apks) {
                for (debug in listOf(true, false)) {
                    total++
                    val label = "$pkg/${apk.parentFile.name}/${apk.name} debug=$debug"
                    try {
                        runCase(pkg, configText, apk, debug, hookClasses)
                        println("PASS $label")
                    } catch (e: Throwable) {
                        val chain = generateSequence(e) { it.cause }
                            .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }
                        e.printStackTrace()
                        println("FAIL $label: $chain")
                        failures.appendLine("$label: $chain")
                    }
                }
            }
        }

        assumeTrue("no fixture apks found", total > 0)
        assertTrue("patch regression failures ($total cases):\n$failures", failures.isEmpty())
    }

    private fun runCase(
        pkg: String,
        configText: String,
        apk: File,
        debug: Boolean,
        hookClasses: List<ClassDef>,
    ) {
        val config = ModApk.parseConfig(configText, debug)
        val types = ModApk.targetTypes(config)
        assertTrue("no target types", types.isNotEmpty())

        val workDir = Files.createTempDirectory("patch-regression").toFile()
        try {
            val unsigned = File(workDir, "unsigned.apk")
            val applied = Collections.synchronizedSet(HashSet<String>())
            var packError: Exception? = null
            val packer = FastApkPacker(
                apkPath = apk.absolutePath,
                outputApk = unsigned,
                targetTypes = types,
                skipEntry = { FastApkPacker.defaultSkip(it) },
                applyModifications = { smaliDir ->
                    ModApk.applyModifications(config, smaliDir, applied) { path ->
                        File(assetsRoot, "modapk/$pkg/$path").takeIf { it.isFile }?.readText()
                    }
                },
                extraClasses = hookClasses,
                injections = config.injections,
                onError = { packError = it },
            )

            val packed = packer.pack()
            if (!packed) packError?.printStackTrace()
            assertTrue("pack() returned false: ${packError?.message}", packed)

            val missing = config.modifications.map { it.filePath }.toSet() - applied
            assertTrue("patches not applied: $missing", missing.isEmpty())

            assertDexInvariants(unsigned, types)
        } finally {
            workDir.deleteRecursively()
            System.gc()
        }
    }

    private fun assertDexInvariants(apk: File, targetTypes: List<String>) {
        val dexName = Regex("classes\\d*\\.dex")
        var hookFound = false
        ZipFile(apk).use { zip ->
            for (entry in zip.entries()) {
                if (!dexName.matches(entry.name)) continue
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val dex = DexBackedDexFile(Opcodes.getDefault(), bytes)
                assertTrue(
                    "${entry.name} field_ids overflow: ${dex.fieldSection.size}",
                    dex.fieldSection.size <= 0xFFFF
                )
                assertTrue(
                    "${entry.name} method_ids overflow: ${dex.methodSection.size}",
                    dex.methodSection.size <= 0xFFFF
                )
                if (dex.classes.any { it.type == HOOK_TYPE }) hookFound = true
                assertInvokeKinds(entry.name, dex.classes, targetTypes.toSet() + HOOK_TYPE)
            }
        }
        assertTrue("hook classes not injected", hookFound)
    }

    private fun assertInvokeKinds(dexName: String, classes: Iterable<ClassDef>, scanTypes: Set<String>) {
        val declared = HashMap<String, Int>()
        for (cls in classes) {
            for (m in cls.methods) {
                declared[methodKey(cls.type, m.name, m.parameterTypes, m.returnType)] = m.accessFlags
            }
        }
        for (cls in classes) {
            if (cls.type !in scanTypes) continue
            for (m in cls.methods) {
                val impl = m.implementation ?: continue
                for (insn in impl.instructions) {
                    val opcode = insn.opcode
                    if (opcode != Opcode.INVOKE_DIRECT && opcode != Opcode.INVOKE_VIRTUAL) continue
                    val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                    if (ref.name == "<init>" || ref.name == "<clinit>") continue
                    val flags = declared[methodKey(ref.definingClass, ref.name, ref.parameterTypes, ref.returnType)]
                        ?: continue
                    val isPrivate = (flags and AccessFlags.PRIVATE.value) != 0
                    if (opcode == Opcode.INVOKE_DIRECT && !isPrivate) {
                        throw AssertionError(
                            "$dexName ${cls.type}->${m.name}: invoke-direct targets non-private method " +
                                "${ref.definingClass}->${ref.name} (IncompatibleClassChangeError at runtime)"
                        )
                    }
                    if (opcode == Opcode.INVOKE_VIRTUAL && isPrivate) {
                        throw AssertionError(
                            "$dexName ${cls.type}->${m.name}: invoke-virtual targets private method " +
                                "${ref.definingClass}->${ref.name} (VerifyError at runtime)"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun invokeKindCheckDetectsPrivateVirtual() {
        val dir = Files.createTempDirectory("invoke-kind").toFile()
        try {
            val src = File(dir, "smali/com/example/Widget.smali")
            src.parentFile.mkdirs()
            src.writeText(
                """
                .class public Lcom/example/Widget;
                .super Ljava/lang/Object;

                .method public constructor <init>()V
                    .registers 1

                    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

                    return-void
                .end method

                .method private getInfo()I
                    .registers 2

                    const/4 v0, 0x1

                    return v0
                .end method

                .method public update()V
                    .registers 2

                    invoke-virtual {p0}, Lcom/example/Widget;->getInfo()I

                    move-result v0

                    return-void
                .end method
                """.trimIndent()
            )
            val classes = SmaliAssembler.assembleToClassDefs(listOf(src), Opcodes.getDefault())
            var message: String? = null
            try {
                assertInvokeKinds("synthetic.dex", classes.values, setOf("Lcom/example/Widget;"))
            } catch (e: AssertionError) {
                message = e.message
            }
            assertTrue(
                "checker must reject invoke-virtual on a private method, got: $message",
                message != null && message.contains("VerifyError")
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun methodKey(
        type: CharSequence,
        name: CharSequence,
        parameters: List<out CharSequence>,
        returnType: CharSequence,
    ): String = "$type->$name(${parameters.joinToString("")})$returnType"

    private fun loadHookClasses(): List<ClassDef> {
        val candidates = listOf(
            File(projectRoot, "app/build/generated/hookAssets/hook/hook.dex"),
            File(projectRoot, "hook/build/outputs/hook/hook.dex"),
        )
        val dexFile = candidates.firstOrNull { it.isFile } ?: return emptyList()
        return DexBackedDexFile(Opcodes.getDefault(), dexFile.readBytes()).classes.toList()
    }

    private companion object {
        const val HOOK_TYPE = "Lcom/bandwa/hook/Hook;"
    }
}
