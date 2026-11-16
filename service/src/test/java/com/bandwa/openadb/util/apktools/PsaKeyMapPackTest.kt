package com.bandwa.openadb.util.apktools

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.bandwa.openadb.service.install.ModApk
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.zip.ZipFile

/**
 * 按键映射注入自证：拿一台真车原包打一遍，检查 4 个类里该有的东西都在。
 *
 * 用法：-Dpsa.apk=<原包> -Dpsa.out=<产物>
 * 不设参数时整个测试跳过（CI 上没原包）。
 */
class PsaKeyMapPackTest {
    private val projectRoot: File = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile }
        ?: File(System.getProperty("user.dir") ?: ".")
    private val assetsRoot = File(projectRoot, "app/src/main/assets")
    private val apkPath = (System.getProperty("psa.apk") ?: System.getenv("PSA_APK"))?.takeIf { it.isNotBlank() }
    private val outPath = (System.getProperty("psa.out") ?: System.getenv("PSA_OUT"))?.takeIf { it.isNotBlank() }

    @Test
    fun packCarriesKeyMapPayload() {
        assumeTrue("psa.apk not set", apkPath != null)
        assumeTrue("psa.out not set", outPath != null)
        val apk = File(apkPath!!)
        assumeTrue("apk not found: $apk", apk.isFile)

        val pkg = "com.cusc.misc"
        val configText = File(assetsRoot, "modapk/$pkg/config.json").readText()
        val config = ModApk.parseConfig(configText, false)
        val types = ModApk.targetTypes(config)

        val hookClasses = loadHookClasses()
        assumeTrue("hook.dex not built", hookClasses.isNotEmpty())

        val out = File(outPath!!)
        out.parentFile?.mkdirs()
        val applied = Collections.synchronizedSet(HashSet<String>())
        var packError: Exception? = null
        val ok = FastApkPacker(
            apkPath = apk.absolutePath,
            outputApk = out,
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
        ).pack()
        if (!ok) packError?.printStackTrace()
        assertTrue("pack() returned false: ${packError?.message}", ok)
        assertTrue("output missing", out.isFile && out.length() > 0)

        val missing = config.modifications.map { it.filePath }.toSet() - applied
        assertTrue("patches not applied: $missing", missing.isEmpty())

        assertPayload(out)
    }

    private fun assertPayload(apk: File) {
        val need = mapOf(
            "Lcom/cusc/misc/observer/KeyEventEmitter;" to listOf("psaHandle", "psaLaunch"),
            "Lcom/android/car/manager/CarPropertyManager;" to listOf("stopAVMDisplay"),
            "Lcom/cusc/misc/BootReceiver;" to listOf("psaBoot"),
            // 标签 :psa_net_off 不进 dex，只能靠方法体首指令是 return-void 判。
            "Lcom/cusc/misc/data4g/Data4gImpl;" to listOf("showNetState-returns-immediately"),
        )
        val found = HashMap<String, MutableSet<String>>()
        ZipFile(apk).use { zip ->
            for (entry in zip.entries()) {
                if (!Regex("classes\\d*\\.dex").matches(entry.name)) continue
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val dex = DexBackedDexFile(Opcodes.getDefault(), bytes)
                for (cls in dex.classes) {
                    val bundle = found.getOrPut(cls.type) { HashSet() }
                    need[cls.type]?.forEach { marker -> if (classHasText(cls, marker)) bundle.add(marker) }
                    if (cls.type == "Lcom/cusc/misc/data4g/Data4gImpl;" &&
                        methodOf(cls, "showNetState")?.let { firstInsnIsReturnVoid(it) } == true
                    ) {
                        bundle.add("showNetState-returns-immediately")
                    }
                }
            }
        }
        for ((type, markers) in need) {
            val got = found[type] ?: emptySet<String>()
            for (marker in markers) {
                assertTrue("$type missing $marker (got $got)", got.contains(marker))
            }
        }
        val data4g = found["Lcom/cusc/misc/data4g/Data4gImpl;"] ?: emptySet<String>()
        assertTrue("showNetState body must return immediately", data4g.contains("showNetState-returns-immediately"))

        assertCallsAndClassSet(apk)
    }

    /** 验插桩真的插进了方法体，且类集合只多不少。 */
    private fun assertCallsAndClassSet(outApk: File) {
        val src = DexBackedDexFile(Opcodes.getDefault(), File(apkPath!!).readBytes0("classes.dex"))
        val src2 = DexBackedDexFile(Opcodes.getDefault(), File(apkPath!!).readBytes0("classes2.dex"))
        val srcTypes = (src.classes + src2.classes).map { it.type }.toSet()

        val outTypes = HashSet<String>()
        var kee: ClassDef? = null
        var boot: ClassDef? = null
        ZipFile(outApk).use { zip ->
            for (entry in zip.entries()) {
                if (!Regex("classes\\d*\\.dex").matches(entry.name)) continue
                val dex = DexBackedDexFile(Opcodes.getDefault(), zip.getInputStream(entry).use { it.readBytes() })
                dex.classes.forEach {
                    outTypes.add(it.type)
                    when (it.type) {
                        "Lcom/cusc/misc/observer/KeyEventEmitter;" -> kee = it
                        "Lcom/cusc/misc/BootReceiver;" -> boot = it
                    }
                }
            }
        }
        val lost = srcTypes - outTypes
        assertTrue("classes disappeared from product: $lost", lost.isEmpty())

        val keeper = kee ?: throw AssertionError("KeyEventEmitter missing in product")
        val click = keeper.methods.first { it.name == "onKeyClick" }
        assertTrue("onKeyClick must call psaHandle", callsMethod(click, "psaHandle"))
        val longClick = keeper.methods.first { it.name == "onKeyLongClick" }
        assertTrue("onKeyLongClick must call psaHandle", callsMethod(longClick, "psaHandle"))
        val booter = boot ?: throw AssertionError("BootReceiver missing in product")
        val onReceive = booter.methods.first { it.name == "onReceive" }
        assertTrue("onReceive must call psaBoot", callsMethod(onReceive, "psaBoot"))
    }

    private fun callsMethod(
        method: com.android.tools.smali.dexlib2.iface.Method,
        name: String,
    ): Boolean = method.implementation?.instructions?.any { insn ->
        val ref = (insn as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)?.reference
        ref is com.android.tools.smali.dexlib2.iface.reference.MethodReference && ref.name == name
    } == true

    private fun File.readBytes0(entryName: String): ByteArray = ZipFile(this).use { zip ->
        val entry = zip.getEntry(entryName) ?: throw AssertionError("$this 里没有 $entryName")
        zip.getInputStream(entry).use { it.readBytes() }
    }

    private fun classHasText(cls: ClassDef, text: String): Boolean {
        if (cls.methods.any { it.name == text }) return true
        return cls.methods.any { m ->
            m.implementation?.instructions?.any { insn ->
                (insn as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)
                    ?.reference?.toString()?.contains(text) == true
            } == true
        }
    }

    private fun methodOf(cls: ClassDef, name: String) = cls.methods.firstOrNull { it.name == name }

    private fun firstInsnIsReturnVoid(method: com.android.tools.smali.dexlib2.iface.Method): Boolean {
        val first = method.implementation?.instructions?.firstOrNull() ?: return false
        return first.opcode == com.android.tools.smali.dexlib2.Opcode.RETURN_VOID
    }

    private fun loadHookClasses(): List<ClassDef> {
        val candidates = listOf(
            File(projectRoot, "app/build/generated/hookAssets/hook/hook.dex"),
            File(projectRoot, "hook/build/outputs/hook/hook.dex"),
        )
        val dexFile = candidates.firstOrNull { it.isFile } ?: return emptyList()
        return DexBackedDexFile(Opcodes.getDefault(), dexFile.readBytes()).classes.toList()
    }
}
