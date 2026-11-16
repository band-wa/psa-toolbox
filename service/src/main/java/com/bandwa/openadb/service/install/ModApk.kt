package com.bandwa.openadb.service.install

import android.content.Context
import android.util.Log
import com.bandwa.openadb.service.BuildConfig
import com.bandwa.openadb.service.install.ApkInstaller
import com.bandwa.openadb.util.HookState
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.apktools.ApkInspector
import com.bandwa.openadb.util.apktools.ApkSignUtil
import com.bandwa.openadb.util.apktools.DexInjector
import com.bandwa.openadb.util.apktools.FastApkPacker
import com.bandwa.openadb.util.apktools.SmaliMod
import com.bandwa.openadb.util.apktools.SmaliMod.Companion.checkValueExist
import com.bandwa.hook.Hook
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object ModApk {
    private const val TAG = "ModApk"

    /** 从 /system 原厂件打补丁并安装；已装补丁版本且未 force 时直接跳过。 */
    fun packageMod(
        context: Context,
        targetPackageName: String,
        force: Boolean = false,
        onComplete: (Boolean) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var success = false
            try {
                val installedPath = getInstalledApkPath(context, targetPackageName)
                val isInstalledInData = installedPath != null && installedPath.startsWith("/data/")
                Log.d(TAG, "$targetPackageName installed path: $installedPath (in data: $isInstalledInData)")

                val stockApkPath = when (targetPackageName) {
                    "com.cusc.misc" -> "/system/app/PSA_Misc/PSA_Misc.apk"
                    "com.android.launcher" -> "/system/app/PSA_Launcher/PSA_Launcher.apk"
                    else -> null
                }

                if (stockApkPath == null) {
                    throw Exception("Unknown core package: $targetPackageName")
                }
                if (!File(stockApkPath).exists()) {
                    throw Exception("Stock APK not found at $stockApkPath")
                }
                val sourceApkPath = stockApkPath

                val patched = if (targetPackageName == "com.cusc.misc") {
                    HookState.versionOf(targetPackageName) == Hook.VERSION
                } else {
                    val reported = HookState.versionOf(targetPackageName)
                    if (reported != null) {
                        reported == Hook.LAUNCHER_VERSION
                    } else {
                        ApkInspector.containsHookClasses(getInstalledApkPath(context, targetPackageName))
                    }
                }
                if (!force && isInstalledInData && patched) {
                    Log.d(TAG, "$targetPackageName already patched, skipping")
                    withContext(Dispatchers.Main) { onComplete(true) }
                    return@launch
                }

                if (isInstalledInData) uninstallUpdateAwait(context, targetPackageName)

                val workspaceDir = Utils.workspaceDir(context)

                if (workspaceDir.exists()) {
                    workspaceDir.deleteRecursively()
                }
                workspaceDir.mkdirs()

                val signTool = ApkSignUtil.loadKey(context)
                if (signTool == null) throw Exception("Failed to load signing key")

                success = runFastPack(context, targetPackageName, sourceApkPath, workspaceDir, signTool)
            } catch (e: Exception) {
                Log.e(TAG, "packageMod failed for $targetPackageName", e)
            }
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    private suspend fun uninstallUpdateAwait(context: Context, targetPackageName: String) {
        val success = ApkInstaller.uninstall(context, targetPackageName)
        Log.d(TAG, "$targetPackageName uninstall update finished, success=$success")
    }

    private suspend fun runFastPack(
        context: Context,
        targetPackageName: String,
        sourceApkPath: String,
        workspaceDir: File,
        signTool: ApkSignUtil,
    ): Boolean {
        return try {
            val config = readConfig(context, targetPackageName) ?: run {
                Log.e(TAG, "fast pack: config missing for $targetPackageName")
                return false
            }
            val types = targetTypes(config)
            if (types.isEmpty()) {
                Log.e(TAG, "fast pack: no target types for $targetPackageName")
                return false
            }

            val unsigned = File(workspaceDir, "${targetPackageName}_unsigned.apk")
            val outputApk = File(workspaceDir, "${targetPackageName}_patched.apk")
            val expectedFiles = config.modifications.map { it.filePath }.toSet()
            val appliedFiles = java.util.Collections.synchronizedSet(HashSet<String>())
            val packer = FastApkPacker(
                apkPath = sourceApkPath,
                outputApk = unsigned,
                targetTypes = types,
                skipEntry = { name -> FastApkPacker.defaultSkip(name) },
                applyModifications = { smaliDir ->
                    applyModificationsFromAssets(context, targetPackageName, smaliDir, appliedFiles)
                },
                extraClasses = loadHookClasses(context),
                injections = config.injections,
                onError = { Log.e(TAG, "fast pack failed", it) },
            )
            val packed = withContext(Dispatchers.Default) { packer.pack() }
            if (!packed) {
                Log.e(TAG, "fast pack: pack() returned false")
                return false
            }
            val missing = expectedFiles - appliedFiles
            if (missing.isNotEmpty()) {
                Log.e(TAG, "fast pack: classes not patched: $missing")
                return false
            }

            if (!signTool.sign(unsigned, outputApk, 24)) {
                Log.e(TAG, "fast pack: sign failed")
                return false
            }
            unsigned.delete()

            ApkInstaller.start(context, outputApk, targetPackageName)
            true
        } catch (e: Exception) {
            Log.e(TAG, "runFastPack failed", e)
            false
        }
    }

    private fun loadHookClasses(context: Context): List<ClassDef> {
        return try {
            context.assets.open("hook/hook.dex").use { stream ->
                DexBackedDexFile(Opcodes.getDefault(), stream.readBytes()).classes.toList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "hook dex not available", e)
            emptyList()
        }
    }

    private fun readConfig(context: Context, targetPackageName: String): ParsedModConfig? {
        return try {
            val content = context.assets.open("modapk/$targetPackageName/config.json")
                .use { it.reader().readText() }
            parseConfig(content, BuildConfig.DEBUG)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read config for $targetPackageName", e)
            null
        }
    }

    internal data class ParsedModConfig(
        val modifications: List<ModTarget>,
        val injections: List<DexInjector.Injection>,
    )

    internal data class ModTarget(val filePath: String, val operations: org.json.JSONArray)

    internal fun parseConfig(content: String, debug: Boolean): ParsedModConfig {
        val root = JSONObject(content)
        val mods = ArrayList<ModTarget>()
        val modifications = root.getJSONArray("modifications")
        for (i in 0 until modifications.length()) {
            val m = modifications.getJSONObject(i)
            if (m.optBoolean("debugOnly") && !debug) continue
            mods.add(ModTarget(m.getString("filePath"), m.getJSONArray("operations")))
        }
        val injections = root.optJSONArray("injections")?.let { DexInjector.parse(it) } ?: emptyList()
        return ParsedModConfig(mods, injections)
    }

    internal fun targetTypes(config: ParsedModConfig): List<String> =
        (config.modifications.mapNotNull { FastApkPacker.classTypeFromSmaliPath(it.filePath) } +
            config.injections.map { it.classType }).distinct()

    private fun getInstalledApkPath(context: Context, targetPackageName: String): String? {
        return try {
            context.packageManager.getPackageInfo(targetPackageName, 0).applicationInfo.sourceDir
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get installed path for $targetPackageName", e)
            null
        }
    }

    private fun applyModificationsFromAssets(
        context: Context,
        targetPackageName: String,
        baseDir: File,
        appliedFiles: MutableSet<String>,
    ): Boolean {
        val config = readConfig(context, targetPackageName) ?: return false
        return applyModifications(config, baseDir, appliedFiles) { path ->
            runCatching {
                context.assets.open("modapk/$targetPackageName/$path").use { it.reader().readText() }
            }.getOrNull()
        }
    }

    internal fun applyModifications(
        config: ParsedModConfig,
        baseDir: File,
        appliedFiles: MutableSet<String>,
        readContentFile: (String) -> String? = { null },
    ): Boolean {
        var anyModified = false
        var allRequiredApplied = true
        try {
            for (modification in config.modifications) {
                val filePath = modification.filePath
                val operations = modification.operations

                val targetFile = File(baseDir, filePath.removePrefix("smali/"))
                if (!targetFile.isFile) {
                    continue
                }

                for (j in 0 until operations.length()) {
                    val operation = operations.getJSONObject(j)
                    val operationType = operation.getString("type")

                    // 幂等：现场类里已经有这个串就跳过，避免重复注入
                    val skipIfContains = operation.optString("skipIfContains")
                    if (skipIfContains.isNotEmpty() &&
                        SmaliMod.checkValueExist(targetFile, skipIfContains)
                    ) {
                        anyModified = true
                        continue
                    }

                    val modified = when (operationType) {
                        "insertBeforeMethod" -> {
                            val content = readContentFile(operation.getString("contentFile"))
                            if (content != null) {
                                SmaliMod.insertBeforeMethod(
                                    targetFile, operation.getString("method"), content
                                )
                            } else {
                                false
                            }
                        }
                        "insertAfterRegisters" -> {
                            val content = readContentFile(operation.getString("contentFile"))
                            if (content != null) {
                                SmaliMod.insertAfterRegisters(
                                    targetFile, operation.getString("method"), content
                                )
                            } else {
                                false
                            }
                        }
                        "appendToClass" -> {
                            val content = readContentFile(operation.getString("contentFile"))
                            if (content != null) SmaliMod.appendToClass(targetFile, content) else false
                        }
                        "replaceValue" -> {
                            val oldValue = operation.getString("oldValue")
                            val newValue = operation.getString("newValue")
                            SmaliMod.replaceValue(targetFile, oldValue, newValue)
                        }
                        "replaceValueIfExists" -> {
                            val checkValue = operation.getString("checkValue")
                            val oldValue = operation.getString("oldValue")
                            val newValue = operation.getString("newValue")
                            SmaliMod.replaceValueIfExists(targetFile, checkValue, oldValue, newValue)
                        }
                        "replaceRegex" -> {
                            val pattern = operation.getString("pattern")
                            val newValue = operation.getString("newValue")
                            SmaliMod.replaceRegex(targetFile, pattern, newValue)
                        }
                        "replaceBetween" -> {
                            val startMarker = operation.getString("startMarker")
                            val endMarker = operation.getString("endMarker")
                            val newContent = operation.getString("newContent")
                            SmaliMod.replaceBetween(targetFile, startMarker, endMarker, newContent)
                        }
                        "replaceFile" -> {
                            if (operation.has("contentFile")) {
                                val content = readContentFile(operation.getString("contentFile"))
                                if (content != null) SmaliMod.replaceFile(targetFile, content) else false
                            } else if (operation.has("newContent")) {
                                val newContent = operation.getString("newContent")
                                SmaliMod.replaceFile(targetFile, newContent)
                            } else {
                                false
                            }
                        }
                        "checkValueExist" -> {
                            val value = operation.getString("value")
                            val skipIfExists = operation.optBoolean("skipIfExists", false)
                            if (skipIfExists && !checkValueExist(targetFile, value)) {
                                if (operation.has("elseOperation")) {
                                    val elseOperation = operation.getJSONObject("elseOperation")
                                    val elseOperationType = elseOperation.getString("type")
                                    when (elseOperationType) {
                                        "replaceValue" -> {
                                            val oldValue = elseOperation.getString("oldValue")
                                            val newValue = elseOperation.getString("newValue")
                                            SmaliMod.replaceValue(targetFile, oldValue, newValue)
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                    if (modified) {
                        anyModified = true
                    } else if (operationType != "replaceValueIfExists" && operationType != "checkValueExist") {
                        Log.e(TAG, "patch not applied: $filePath #$j ($operationType)")
                        allRequiredApplied = false
                    }
                }
                if (allRequiredApplied) {
                    appliedFiles.add(filePath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply modifications", e)
            return false
        }
        return anyModified && allRequiredApplied
    }
}
