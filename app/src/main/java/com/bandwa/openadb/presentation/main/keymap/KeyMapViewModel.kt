package com.bandwa.openadb.presentation.main.keymap

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.util.AppPick
import com.bandwa.openadb.util.PickApp
import com.bandwa.openadb.util.Utils.getSystemProp
import com.bandwa.openadb.util.Utils.setSystemProp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile
import javax.inject.Inject

@HiltViewModel
class KeyMapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(KeyMapUIState(rows = KEY_ROWS))
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val payloadInstalled = withContext(Dispatchers.IO) { hostHasPayload() }
        val rows = withContext(Dispatchers.IO) {
            KEY_ROWS.map { row ->
                if (row.listToggle) {
                    row
                } else {
                    row.copy(
                        shortApp = PickApp.of(context, getSystemProp(row.shortProp)),
                        longApp = row.longProp?.let { PickApp.of(context, getSystemProp(it)) },
                    )
                }
            }
        }
        val list360 = withContext(Dispatchers.IO) { listModEnabled() }
        _uiState.update { it.copy(rows = rows, payloadInstalled = payloadInstalled, list360 = list360) }
    }

    fun loadInstalledApps() = viewModelScope.launch {
        val apps = withContext(Dispatchers.IO) { AppPick.list(context) }
        _uiState.update { it.copy(installedApps = apps) }
    }

    fun bind(index: Int, target: String) = bind(index, target, false)

    fun bindLong(index: Int, target: String) = bind(index, target, true)

    fun clearShort(index: Int) = bind(index, NONE, false)

    fun clearLong(index: Int) = bind(index, NONE, true)

    /**
     * LIST 键只有一个布尔语义：开 = 短按开关 360，关 = 走原厂。
     *
     * 它不读属性 —— 工具箱的 service 直接读 MyPrefs 的 isListMod，
     * 所以这里必须写同一个 prefs 键，写属性是不会生效的。
     */
    fun setList360(on: Boolean) {
        runCatching {
            context.getSharedPreferences(LIST_PREF, Context.MODE_PRIVATE)
                .edit().putBoolean(LIST_KEY, on).commit()
        }
        _uiState.update {
            it.copy(
                list360 = on,
                message = if (on) "LIST 键已接管：短按开关 360 全景" else "LIST 键已恢复原厂",
            )
        }
    }

    private fun listModEnabled(): Boolean = runCatching {
        context.getSharedPreferences(LIST_PREF, Context.MODE_PRIVATE)
            .getBoolean(LIST_KEY, false)
    }.getOrDefault(false)

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun bind(index: Int, target: String, long: Boolean) {
        val row = _uiState.value.rows.getOrNull(index) ?: return
        val prop = if (long) row.longProp else row.shortProp
        if (prop.isNullOrEmpty()) return
        setSystemProp(prop, target)
        val app = PickApp.of(context, target)
        val rows = _uiState.value.rows.toMutableList()
        rows[index] = if (long) row.copy(longApp = app) else row.copy(shortApp = app)
        _uiState.update {
            it.copy(
                rows = rows,
                message = if (app == null) "${row.name}已清空" else "${row.name}已绑定 ${app.label}",
            )
        }
    }

    /**
     * 宿主 com.cusc.misc 里是否带我们的载荷指纹。
     *
     * 2026-11-16 修：原来只查 `persist.bandwa.stalk.shortclick` 一个串，用户实测
     * 「按键都能正常用了，页面还显示没装」。两个原因：
     *  ① 只认一个指纹，注入层只要换个分层方式（补丁 dex 名/合并方式不同）就漏判；
     *  ② 更主要的是 `init` 只跑一次，而 Hilt 的 ViewModel 挂在 Activity 上，
     *     切页不会重建 ⇒ 注入完成后回到本页看到的仍是注入前的旧状态。
     * 现在：**多指纹任一命中即算已注入**，并且页面每次进入都会重新检测（见 KeyMapRoute 的 LaunchedEffect）。
     */
    private fun hostHasPayload(): Boolean {
        val apk = runCatching {
            context.packageManager.getPackageInfo(HOST_PACKAGE, 0).applicationInfo.sourceDir
        }.getOrNull() ?: return false
        return runCatching {
            ZipFile(apk).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                    .any { entry ->
                        // 一个 dex 一个 dex 地查，多个指纹逐个重开流（dex 只有几个，代价可忽略）。
                        MARKERS.any { marker ->
                            zip.getInputStream(entry).use { stream ->
                                streamContains(stream, marker.toByteArray(Charsets.US_ASCII))
                            }
                        }
                    }
            }
        }.getOrDefault(false)
    }

    private fun streamContains(stream: java.io.InputStream, marker: ByteArray): Boolean {
        val chunk = ByteArray(64 * 1024 + marker.size)
        var carry = 0
        while (true) {
            val read = stream.read(chunk, carry, chunk.size - carry)
            if (read <= 0) return false
            val length = carry + read
            var i = 0
            while (i <= length - marker.size) {
                var matched = true
                for (j in marker.indices) {
                    if (chunk[i + j] != marker[j]) {
                        matched = false
                        break
                    }
                }
                if (matched) return true
                i++
            }
            carry = minOf(marker.size - 1, length)
            System.arraycopy(chunk, length - carry, chunk, 0, carry)
        }
    }

    companion object {
        const val NONE = "none"

        private const val HOST_PACKAGE = "com.cusc.misc"

        /** 与 ExpertModeViewModel 同一个 prefs 与键：service 侧读的就是它。 */
        private const val LIST_PREF = "MyPrefs"
        private const val LIST_KEY = "isListMod"

        private const val MARKER = "persist.bandwa.stalk.shortclick"

        /**
         * 指纹们：任一命中即算「注入层在」。
         *
         * 主指纹仍是 stalk.shortclick（KeyConfig 那条验证脚本 `verify_inject_toolbox_mode.py`
         * 与 `InjectVerify` 的装前硬闸 ⑥ 都用它）；后面几个是我们载荷里的固定串，
         * 用来兜住「补丁被合并进别的 dex / 分层方式不同」导致的漏判。
         */
        private val MARKERS = listOf(
            MARKER,
            "persist.bandwa.stalk.longclick",
            "psaHandle",
            "persist.bandwa.btphone.shortclick",
        )

        /**
         * 13 行：7 个可配长短按的注入键 + SRC + 音乐 + 地图 + LIST + 会员中心 + 车机设置。
         *
         * 音乐/地图的属性是工具箱自己发明的（由 `Hook.launchByProp` 消费）；
         * LIST 没有属性、只有 `MyPrefs.isListMod` 这个布尔（service 直接读它）。
         */
        val KEY_ROWS = listOf(
            // 灯杆顶端只给短按：原厂 onKeyLongClick 里 0x07 与 0x17 共用「长按 2 秒唤醒语音助手」
            // 这条分支（sendKeyEvent 0xe7），给它长按会把这个原厂动作顶掉。KeyConfig 一直是 longProp=null。
            KeyMapRow(
                "灯杆顶端（仅原功能为唤醒语音助手）",
                "persist.bandwa.stalk.shortclick",
            ),
            KeyMapRow("蓝牙电话", "persist.bandwa.btphone.shortclick", "persist.bandwa.btphone.longclick"),
            KeyMapRow("方向盘语音键", "persist.bandwa.voice.shortclick", "persist.bandwa.voice.longclick"),
            KeyMapRow("空调钢琴键", "persist.bandwa.ac.shortclick", "persist.bandwa.ac.longclick"),
            KeyMapRow("车辆设置钢琴键", "persist.bandwa.car.shortclick", "persist.bandwa.car.longclick"),
            KeyMapRow("蓝牙电话钢琴键", "persist.bandwa.btpiano.shortclick", "persist.bandwa.btpiano.longclick"),
            KeyMapRow("手机互联钢琴键", "persist.bandwa.connect.shortclick", "persist.bandwa.connect.longclick"),
            KeyMapRow(
                "SRC 切源键",
                "persist.bandwa.src.shortclick",
                note = "由 com.cusc.media 消费，不是我们注入的类",
            ),
            // 音乐/地图：短按仍由工具箱的 Hook.launchByProp 执行（它读 .shortclick），
            // 长按由我们的载荷接管 —— psa_handle.smali 里 0x0b / 0x0d 两条分支只在
            // 长按时 resolve .longclick，短按直接 return false 让原厂/Hook 继续走。
            // 前提是「修复PSA工具箱」注入了我们的补丁层；没注入时长按仍是原厂行为。
            KeyMapRow(
                "音乐键",
                "persist.bandwa.music.shortclick",
                "persist.bandwa.music.longclick",
                note = "短按由工具箱的 Hook 接管；长按由按键映射的注入层接管",
            ),
            KeyMapRow(
                "地图键",
                "persist.bandwa.map.shortclick",
                "persist.bandwa.map.longclick",
                note = "短按由工具箱的 Hook 接管；长按由按键映射的注入层接管",
            ),
            KeyMapRow(
                "会员中心键（有该按键的车型才有）",
                "persist.psa.membercenter.shortclick",
            ),
            KeyMapRow(
                "车机设置键（有该按键的车型才有）",
                "persist.psa.carsettings.shortclick",
            ),
            // LIST 只有「开/关 360」两种状态，没有可选的目标应用，所以放最后一行、单独占位。
            KeyMapRow(
                "LIST启动360",
                "",
                note = "原厂语义就是开关 360 全景，只能开/关，没有可变的目标应用",
                listToggle = true,
            ),
        )
    }
}
