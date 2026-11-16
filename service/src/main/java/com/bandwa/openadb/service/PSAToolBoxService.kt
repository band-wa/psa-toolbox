package com.bandwa.openadb.service

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager.CarPropertyEventListener
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.bandwa.openadb.receiver.AppInstallReceiver
import com.bandwa.openadb.receiver.CarlifeBroadcastReceiver
import com.bandwa.openadb.receiver.CarlifeBroadcastSender
import com.bandwa.openadb.receiver.HicarBroadcastReceiver
import com.bandwa.openadb.receiver.HicarBroadcastSender
import com.bandwa.openadb.util.AutoClean
import com.bandwa.openadb.util.CarGateway
import com.bandwa.openadb.service.ToolBoxServiceWrapper.Companion.ACTION_START_PROXY_AIR_CONDITION
import com.bandwa.openadb.service.ToolBoxServiceWrapper.Companion.ACTION_STOP_PROXY_AIR_CONDITION
import com.bandwa.openadb.service.ToolBoxServiceWrapper.Companion.ACTION_START_PROJECTION
import com.bandwa.openadb.service.ToolBoxServiceWrapper.Companion.ACTION_STOP_PROJECTION
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.Utils.HVAC_HOOK_KEY
import com.bandwa.openadb.util.Utils.PREFS_NAME
import com.bandwa.openadb.util.Utils.getSystemProp
import com.bandwa.openadb.util.IndependentDisplayPresentation
import com.bandwa.openadb.util.GestureBarController
import com.bandwa.openadb.util.HookState
import com.bandwa.openadb.util.PsaExt
import com.bandwa.hook.Hook
import com.bandwa.openadb.util.KeyInjector.sendKeyDownUpSync
import com.bandwa.openadb.util.VideoViewPresentation
import com.bandwa.openadb.presentation.overlay.FuelAmountOverlay
import com.bandwa.openadb.util.FuelLedger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PSAToolBoxService : Service() {
    private val mCarCabinCallback = object : CarPropertyEventListener {
        override fun onChangeEvent(var1: CarPropertyValue<*>?) {
            // car HAL 回调线程：立即切到 IO，避免阻塞 HAL 事件队列
            serviceScope.launch(Dispatchers.IO) {
                try {
                    when (var1?.propertyId) {
                        PROP_MCU_KEY_INFO -> {
                            val propertyValues =
                                var1.value as? Array<*> ?: error("Invalid property value type")
                            val propertyId = propertyValues[0]
                            var keyState = propertyValues[1]

                            if (keyState == 2) {
                                keyState = 0
                            }

                            handleMatchedPattern(propertyId.toString(), keyState.toString())
                        }

                        PROP_MCU_KEY_POWER_LEFT -> {
                            if (shouldHookHvac) {
                                hvacKnobControl("tempSub")
                            }
                        }

                        PROP_MCU_KEY_POWER_RIGHT -> {
                            if (shouldHookHvac) {
                                hvacKnobControl("tempAdd")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(Utils.SERVICE_TAG, e.message.toString())
                }
            }
        }

        override fun onErrorEvent(var1: Int, var2: Int) {
            Log.d(Utils.SERVICE_TAG, "onErrorEvent $var1 $var2")
        }
    }

    private var isHvacForeground = false

    @Volatile

    var shouldHookHvac = false

    private var hvacForegroundMonitor: HvacForegroundMonitor? = null

    private var videoViewPresentation: VideoViewPresentation? = null
    var isProjectionRunning = false
        private set

    private var independentDisplayPresentation: IndependentDisplayPresentation? = null
    var isIndependentProjectionRunning = false
        private set

    private val gestureBarController by lazy { GestureBarController(this) }

    private var fuelListener: CarPropertyEventListener? = null
    private var mileageListener: CarPropertyEventListener? = null
    private var fuelOverlayShowing = false
    @Volatile
    private var fuelRefLow = -1
    @Volatile
    private var lastTotalMileageKm: Float? = null
    private var fuelLastFillAt = 0L
    private var pendingOilBefore = 0
    private val pendingOilAfterFlow = MutableStateFlow(0)

    private val channelID = "PSAToolBoxServiceChannel"
    private val notificationID = 2

    private val defaultMediaPkgName = "com.cusc.media"



    private var rightBtnPressDownTime = 0L

    private var mediaBtnPressDownTime = 0L
    private var mapBtnPressDownTime = 0L

    private var mediaJob: Job? = null
    private var mapJob: Job? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val hicarBroadcastReceiver = HicarBroadcastReceiver()

    private val carlifeBroadcastReceiver = CarlifeBroadcastReceiver()

    private val appInstallReceiver = AppInstallReceiver()

    private var projectionJob: Job? = null

    private var mHasFocusList = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        isRunning = true
        startForeground(notificationID, createNotification())

        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        shouldHookHvac = sharedPreferences.getBoolean(HVAC_HOOK_KEY, false)

        if (sharedPreferences.getBoolean(GestureBarController.PREF_ENABLED, false)) {
            gestureBarController.start()
        }

        com.bandwa.openadb.util.Utils.cleanupWorkspace(this)

        init()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(EXTRA_ACTION_KEY)) {
            ACTION_START_PROXY_AIR_CONDITION -> {
                shouldHookHvac = true
                startHvacForegroundMonitoring()
            }

            ACTION_STOP_PROXY_AIR_CONDITION -> {
                shouldHookHvac = false
                stopHvacForegroundMonitoring()
            }

            ACTION_START_PROJECTION -> {
                if (!isProjectionRunning) {
                    startMirrorProjection()
                }
                dispatchHookEvent("projection_started")
            }

            ACTION_STOP_PROJECTION -> {
                if (isProjectionRunning) {
                    stopMirrorProjection()
                }
                dispatchHookEvent("projection_stopped")
            }

            ToolBoxServiceWrapper.ACTION_START_INDEPENDENT_PROJECTION -> {
                if (!isIndependentProjectionRunning) {
                    startIndependentProjection()
                }
            }

            ToolBoxServiceWrapper.ACTION_STOP_INDEPENDENT_PROJECTION -> {
                if (isIndependentProjectionRunning) {
                    stopIndependentProjection()
                }
            }

            ToolBoxServiceWrapper.ACTION_START_FULLSCREEN_GESTURE -> {
                gestureBarController.start()
            }

            ToolBoxServiceWrapper.ACTION_STOP_FULLSCREEN_GESTURE -> {
                gestureBarController.stop()
            }

            else -> {
            }
        }
        return START_STICKY
    }

    private fun dispatchHookEvent(event: String, payload: String = "") {
        HookBridgeHub.dispatch(event, payload)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()

        isRunning = false

        stopMirrorProjection()
        stopIndependentProjection()

        gestureBarController.stop()

        stopHvacForegroundMonitoring()

        serviceScope.cancel()

        CarGateway.unregister(mCarCabinCallback)
        fuelListener?.let { CarGateway.unregister(it) }
        mileageListener?.let { CarGateway.unregister(it) }
        if (fuelOverlayShowing) {
            FuelAmountOverlay.dismiss()
            fuelOverlayShowing = false
        }

        stopForeground(STOP_FOREGROUND_DETACH)
        try {
            unregisterReceiver(hicarBroadcastReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            unregisterReceiver(carlifeBroadcastReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            unregisterReceiver(appInstallReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification(): Notification {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.getNotificationChannel(channelID) == null) {
            val channel = NotificationChannel(
                channelID,
                "PSAToolBox Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelID)
            .setSmallIcon(R.mipmap.ic_service_launcher)
            .setOngoing(true)
            .setContentTitle("PSAToolBox Service")
            .setContentText("PSAToolBox Service is running")
            .build()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun init() {
        AutoClean.start(this, serviceScope)
        try {
            serviceScope.launch(Dispatchers.IO) {
                if (shouldHookHvac) {
                    startHvacForegroundMonitoring()
                } else {
                    HvacAccessibilityHelper.disable(this@PSAToolBoxService)
                }

                withContext(Dispatchers.Main) {
                    val hicarIntentFilter = IntentFilter()
                    hicarIntentFilter.addAction("Musichelper.HicarStop")
                    hicarIntentFilter.addAction("Musichelper.HicarResume")
                    registerReceiver(hicarBroadcastReceiver, hicarIntentFilter)

                    val carlifeIntentFilter = IntentFilter()
                    carlifeIntentFilter.addAction("Musichelper.CarlifeStop")
                    carlifeIntentFilter.addAction("Musichelper.CarlifeResume")
                    registerReceiver(carlifeBroadcastReceiver, carlifeIntentFilter)

                    val appInstallIntentFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
                    appInstallIntentFilter.addDataScheme("package")
                    registerReceiver(appInstallReceiver, appInstallIntentFilter)
                }

                CarGateway.ensureStarted(this@PSAToolBoxService)
                registerCabinProps()
                startFuelMonitor()
            }
        } catch (e: Exception) {
            Log.d(Utils.SERVICE_TAG, "Init failed. ${e.message.toString()}")
        }
    }

    private fun startFuelMonitor() {
        fuelRefLow = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(KEY_FUEL_REF_LOW, -1)
        fuelLastFillAt = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getLong(KEY_FUEL_LAST_FILL_AT, 0L)

        val oilListener = object : CarPropertyEventListener {
            override fun onChangeEvent(value: CarPropertyValue<*>?) {
                val percent = value.numberOrNull()?.toInt() ?: return
                serviceScope.launch(Dispatchers.Main) {
                    runCatching { onFuelLevelChanged(percent) }
                        .onFailure { Log.w(Utils.SERVICE_TAG, "fuel handle failed: ${it.message}") }
                }
            }

            override fun onErrorEvent(propId: Int, areaId: Int) {
                Log.d(Utils.SERVICE_TAG, "fuel onErrorEvent $propId $areaId")
            }
        }
        fuelListener = oilListener
        CarGateway.register(oilListener, PROP_REMAINING_OIL, 0f)

        val kmListener = object : CarPropertyEventListener {
            override fun onChangeEvent(value: CarPropertyValue<*>?) {
                val km = value.numberOrNull()?.toFloat() ?: return
                if (!km.isFinite() || km <= 0f) return
                serviceScope.launch(Dispatchers.Main) { lastTotalMileageKm = km }
            }

            override fun onErrorEvent(propId: Int, areaId: Int) {
                Log.d(Utils.SERVICE_TAG, "mileage onErrorEvent $propId $areaId")
            }
        }
        mileageListener = kmListener
        CarGateway.register(kmListener, PROP_TOTAL_MILEAGE, 0f)

        serviceScope.launch(Dispatchers.IO) {
            CarGateway.manager.collect { manager ->
                if (manager == null) return@collect
                val percent = runCatching {
                    manager.getProperty<Any>(PROP_REMAINING_OIL, 0).numberOrNull()?.toInt()
                }.getOrNull()
                val mileage = runCatching {
                    manager.getProperty<Any>(PROP_TOTAL_MILEAGE, 0).numberOrNull()?.toFloat()
                }.getOrNull()
                withContext(Dispatchers.Main) {
                    mileage?.takeIf { it.isFinite() && it > 0f }?.let { lastTotalMileageKm = it }
                    percent?.let {
                        runCatching { onFuelLevelChanged(it) }
                            .onFailure { e -> Log.w(Utils.SERVICE_TAG, "fuel snapshot failed: ${e.message}") }
                    }
                }
            }
        }
    }

    private fun currentMileageKm(): Float? {
        val fresh = runCatching {
            CarGateway.manager.value?.getProperty<Any>(PROP_TOTAL_MILEAGE, 0).numberOrNull()?.toFloat()
        }.getOrNull()
        if (fresh != null && fresh.isFinite() && fresh > 0f) {
            lastTotalMileageKm = fresh
            return fresh
        }
        return lastTotalMileageKm
    }

    private fun onFuelLevelChanged(percent: Int) {
        if (percent !in 1..FUEL_PERCENT_VALID_MAX) return

        if (fuelOverlayShowing) {
            if (percent > pendingOilAfterFlow.value) pendingOilAfterFlow.value = percent
            return
        }

        if (fuelRefLow < 0 || percent < fuelRefLow) {
            setFuelRefLow(percent)
            return
        }
        if (percent - fuelRefLow <= FUEL_PROMPT_DELTA) return

        if (!getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(Utils.FUEL_PROMPT_KEY, false)) {
            setFuelRefLow(percent)
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (fuelLastFillAt > 0 && now >= fuelLastFillAt && now - fuelLastFillAt < FUEL_COOLDOWN_MS) {
            setFuelRefLow(percent)
            return
        }

        pendingOilBefore = fuelRefLow
        pendingOilAfterFlow.value = percent
        fuelOverlayShowing = true
        val shown = runCatching {
            FuelAmountOverlay.show(
                context = this,
                percentBefore = pendingOilBefore,
                percentAfter = pendingOilAfterFlow,
                onConfirm = { amount ->
                    fuelOverlayShowing = false
                    setFuelLastFillAt()
                    setFuelRefLow(pendingOilAfterFlow.value)
                    val saved = FuelLedger.append(
                        this,
                        FuelLedger.Entry(
                            timestamp = System.currentTimeMillis(),
                            amount = amount,
                            percentBefore = pendingOilBefore,
                            percentAfter = pendingOilAfterFlow.value,
                            totalMileageKm = currentMileageKm(),
                        ),
                    )
                    Toast.makeText(
                        this,
                        if (saved) "已记录加油 ¥$amount" else "记录保存失败，可在手动补录中补录",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onDismiss = {
                    fuelOverlayShowing = false
                    setFuelLastFillAt()
                    setFuelRefLow(pendingOilAfterFlow.value)

                    FuelLedger.append(
                        this,
                        FuelLedger.Entry(
                            timestamp = System.currentTimeMillis(),
                            amount = 0,
                            percentBefore = pendingOilBefore,
                            percentAfter = pendingOilAfterFlow.value,
                            totalMileageKm = currentMileageKm(),
                            skipped = true,
                        ),
                    )
                    Toast.makeText(this, "已跳过，可在手动补录中补录金额", Toast.LENGTH_SHORT).show()
                },
            )
        }.getOrElse { e ->
            Log.w(Utils.SERVICE_TAG, "fuel overlay failed: ${e.javaClass.simpleName} ${e.message}")
            false
        }
        if (!shown) {
            fuelOverlayShowing = false
            setFuelLastFillAt()
            setFuelRefLow(pendingOilAfterFlow.value)
        }
    }

    private fun setFuelLastFillAt() {
        fuelLastFillAt = SystemClock.elapsedRealtime()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putLong(KEY_FUEL_LAST_FILL_AT, fuelLastFillAt).apply()
    }

    private fun setFuelRefLow(percent: Int) {
        fuelRefLow = percent

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putInt(KEY_FUEL_REF_LOW, percent).commit()
    }

    private fun CarPropertyValue<*>?.numberOrNull(): Number? = this?.value as? Number

    @Suppress("DEPRECATION")
    private fun getTopActivityPackageName(): String {
        val runningTasks = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return runningTasks?.getRunningTasks(1)?.getOrNull(0)?.topActivity?.packageName ?: ""
    }

    private fun getMyPrefs() = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    private fun startHvacForegroundMonitoring() {
        synchronized(this) {
            if (hvacForegroundMonitor != null) return
            val monitor = HvacForegroundMonitor(this) { foreground ->
                onHvacForegroundChanged(foreground)
            }
            hvacForegroundMonitor = monitor
            monitor.start()
        }
    }

    private fun stopHvacForegroundMonitoring() {
        synchronized(this) {
            hvacForegroundMonitor?.stop()
            hvacForegroundMonitor = null
        }
        onHvacForegroundChanged(false)
    }

    private fun onHvacForegroundChanged(foreground: Boolean) {
        val effective = shouldHookHvac && foreground
        if (effective != HookBridgeHub.hvacForeground) {
            HookBridgeHub.hvacForeground = effective
            dispatchHookEvent("hvac_foreground", effective.toString())
        }
    }

    private fun isDefaultMediaPlay(): Boolean {
        return try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val m = android.media.AudioManager::class.java
                .getDeclaredMethod("getCurrentActiveAudioPkg")
            m.isAccessible = true
            val pkg = m.invoke(audioManager) as? String
            defaultMediaPkgName == pkg
        } catch (e: Exception) {
            Log.d(Utils.SERVICE_TAG, e.message.toString())
            false
        }
    }

    private fun handleMatchedPattern(keyCode: String, action: String) {
        mHasFocusList = Settings.Global.getInt(this.contentResolver, "KeyEvent.Focus", 0) == 1

        when (keyCode) {
            "2" -> when (action) {
                "0" -> if (shouldHookHvac) {
                    hvacVolumeKeyControl("windAdd")
                }
            }

            "3" -> when (action) {
                "0" -> if (shouldHookHvac) {
                    hvacVolumeKeyControl("windSub")
                }
            }

            "5" -> when (action) {
                "0" -> if (getMyPrefs().getBoolean("isListMod", false)) {
                    if (isAvmActive()) {
                        closeAvm()
                    } else {
                        val intent = Intent()
                        intent.setClassName("com.cusc.misc", "com.cusc.misc.evs.AVM")
                        startActivity(intent)
                    }
                }
            }

            "6" -> when (action) {
                "0" -> if (HicarBroadcastReceiver.hicarStopped && CarlifeBroadcastReceiver.carlifeStopped && !mHasFocusList) {
                    simulateMediaPreviousKeyPress()
                } else if (CarlifeBroadcastReceiver.carlifeStopped && !mHasFocusList) {
                    HicarBroadcastSender.sendBroadcast(
                        this,
                        HicarBroadcastSender.ACTION_MEDIA_PREVIOUS
                    )
                } else if (HicarBroadcastReceiver.hicarStopped && !mHasFocusList) {
                    CarlifeBroadcastSender.sendBroadcast(
                        this,
                        CarlifeBroadcastSender.ACTION_MEDIA_PREVIOUS
                    )
                } else {
                    HicarBroadcastReceiver.hicarStopped = true
                    CarlifeBroadcastReceiver.carlifeStopped = true
                    Log.d(Utils.SERVICE_TAG, "Key control status error, try to fix it")
                }
            }

            "10" -> when (action) {
                "0" -> {
                    rightBtnPressDownTime = System.currentTimeMillis()
                    projectionJob?.cancel()
                    projectionJob = serviceScope.launch {
                        delay(2000)
                        val quickSwitchProjection =
                            getMyPrefs().getBoolean("is_quick_switch_projection", false)
                        if (quickSwitchProjection) {
                            val isMirrorMode = getMyPrefs().getBoolean("is_mirror_projection_mode", true)
                            if (isMirrorMode) {
                                if (isProjectionRunning) {
                                    stopMirrorProjection()
                                } else {
                                    startMirrorProjection()
                                }
                            } else {
                                if (isIndependentProjectionRunning) {
                                    stopIndependentProjection()
                                } else {
                                    startIndependentProjection()
                                }
                            }
                        }
                    }
                }

                "1" -> {
                    val rightBtnPressUpTime = System.currentTimeMillis()
                    if (rightBtnPressUpTime - rightBtnPressDownTime < 2000) {
                        projectionJob?.cancel()
                        if (HicarBroadcastReceiver.hicarStopped && CarlifeBroadcastReceiver.carlifeStopped && !mHasFocusList) {
                            simulateMediaPlayPauseKeyPress()
                        } else if (CarlifeBroadcastReceiver.carlifeStopped && !mHasFocusList) {
                            HicarBroadcastSender.sendBroadcast(
                                this,
                                HicarBroadcastSender.ACTION_MEDIA_PLAY_PAUSE
                            )
                        } else if (HicarBroadcastReceiver.hicarStopped && !mHasFocusList) {
                            CarlifeBroadcastSender.sendBroadcast(
                                this,
                                CarlifeBroadcastSender.ACTION_MEDIA_PLAY_PAUSE
                            )
                        } else {
                            HicarBroadcastReceiver.hicarStopped = true
                            CarlifeBroadcastReceiver.carlifeStopped = true
                            Log.d(Utils.SERVICE_TAG, "Key control status error, try to fix it")
                        }
                    }
                }
            }

            "17" -> when (action) {
                "0" -> if (shouldHookHvac) {
                    hvacAcModeSwitch()
                }
            }

            "26" -> when (action) {
                "0" -> if (HicarBroadcastReceiver.hicarStopped && CarlifeBroadcastReceiver.carlifeStopped && !mHasFocusList) {
                    simulateMediaNextKeyPress()
                } else if (CarlifeBroadcastReceiver.carlifeStopped && !mHasFocusList) {
                    HicarBroadcastSender.sendBroadcast(this, HicarBroadcastSender.ACTION_MEDIA_NEXT)
                } else if (HicarBroadcastReceiver.hicarStopped && !mHasFocusList) {
                    CarlifeBroadcastSender.sendBroadcast(
                        this,
                        CarlifeBroadcastSender.ACTION_MEDIA_NEXT
                    )
                } else {
                    HicarBroadcastReceiver.hicarStopped = true
                    CarlifeBroadcastReceiver.carlifeStopped = true
                    Log.d(Utils.SERVICE_TAG, "Key control status error, try to fix it")
                }
            }

            // 音乐键(0x0b)/地图键(0x0d)：注入层打过补丁且用户配了值时长按由
            // psa_handle.smali 的 :psa_r9 / :psa_r10 处理，这里让位；其余情况（没注入、
            // 注入失败、属性没配过）由下面的分支兜底，否则长按完全没反应。
            "11" -> handleMediaLongPress(action, isMedia = true)

            "13" -> handleMediaLongPress(action, isMedia = false)

        }
    }


    /**
     * 音乐/地图键的长按兜底：按下起延时 2 秒，抬起早于 2 秒就取消。
     *
     * @param action "0" = 按下，"1" = 抬起
     * @param isMedia true = 音乐键(0x0b)，false = 地图键(0x0d)
     */
    private fun handleMediaLongPress(action: String, isMedia: Boolean) {
        val prop = if (isMedia) "persist.bandwa.music.longclick" else "persist.bandwa.map.longclick"
        val fallback = if (isMedia) defaultMediaPkgName else "com.autonavi.amapauto"

        // 注入层在场且用户配了具体目标 ⇒ 长按已经在 misc 里启动过了
        val raw = getSystemProp(prop)
        val injected = HookState.versionOf(PsaExt.PKG_MISC) == Hook.VERSION
        if (raw == "none" || (injected && raw.isNotEmpty())) return

        if (action == "0") {
            if (isMedia) {
                mediaBtnPressDownTime = System.currentTimeMillis()
                mediaJob?.cancel()
                mediaJob = serviceScope.launch {
                    delay(2000)
                    launchByPropOr(prop, fallback)
                }
            } else {
                mapBtnPressDownTime = System.currentTimeMillis()
                mapJob?.cancel()
                mapJob = serviceScope.launch {
                    delay(2000)
                    launchByPropOr(prop, fallback)
                }
            }
        } else {
            val held = System.currentTimeMillis() -
                (if (isMedia) mediaBtnPressDownTime else mapBtnPressDownTime)
            if (held < 2000) {
                if (isMedia) mediaJob?.cancel() else mapJob?.cancel()
            }
        }
    }

    private fun launchByPropOr(prop: String, fallback: String) {
        val raw = getSystemProp(prop)
        val pkg = if (raw.isEmpty() || raw == "none") fallback else raw
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        runCatching { startActivity(intent) }
    }

    private fun simulateMediaPreviousKeyPress() {
        if (!isDefaultMediaPlay()) {
            sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        } else {
            Log.d(
                Utils.SERVICE_TAG,
                "In the default media is working, there is no repeated previous"
            )
        }
    }

    private fun simulateMediaPlayPauseKeyPress() {
        sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    private fun simulateMediaNextKeyPress() {
        if (!isDefaultMediaPlay()) {
            sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_NEXT)
        } else {
            Log.d(Utils.SERVICE_TAG, "In the default media is working, there is no repeated next")
        }
    }

    private fun registerCabinProps() {
        listOf(
            PROP_MCU_KEY_INFO,
            PROP_MCU_KEY_POWER_LEFT,
            PROP_MCU_KEY_POWER_RIGHT
        ).forEach { propId -> CarGateway.register(mCarCabinCallback, propId, 0f) }
    }

    private fun sendHvacStep(propId: Int, direction: Int, start: Boolean) {
        try {
            CarGateway.manager.value?.setIntArrayProperty(
                propId, AREA_DRIVER, intArrayOf(direction, if (start) 2 else 1)
            )
        } catch (e: Exception) {
            Log.d(Utils.SERVICE_TAG, "sendHvacStep 0x${propId.toString(16)} failed: ${e.message}")
        }
    }

    private fun hvacKnobControl(action: String) {
        isHvacForeground = (getTopActivityPackageName() == "com.cusc.hvac")
        if (isHvacForeground && shouldHookHvac) {
            val direction = if (action == "tempAdd") 1 else 0
            sendHvacStep(PROP_HVAC_TEMPERATURE_ADJUST, direction, true)
            sendHvacStep(PROP_HVAC_TEMPERATURE_ADJUST, direction, false)
        }
    }

    private fun hvacVolumeKeyControl(action: String) {
        isHvacForeground = (getTopActivityPackageName() == "com.cusc.hvac")
        if (isHvacForeground && shouldHookHvac) {
            val direction = if (action == "windAdd") 1 else 0
            sendHvacStep(PROP_HVAC_FAN_SPEED_ADJUST, direction, true)
            sendHvacStep(PROP_HVAC_FAN_SPEED_ADJUST, direction, false)
        }
    }

    private fun hvacAcModeSwitch() {
        isHvacForeground = (getTopActivityPackageName() == "com.cusc.hvac")
        if (isHvacForeground && shouldHookHvac) {
            pressAcKey()
        }
    }

    private fun pressAcKey() {
        try {
            CarGateway.manager.value?.setIntProperty(PROP_HVAC_AC_ON_SET, AREA_DRIVER, 2)
            CarGateway.manager.value?.setIntProperty(PROP_HVAC_AC_ON_SET, AREA_DRIVER, 1)
        } catch (e: Exception) {
            Log.d(Utils.SERVICE_TAG, "pressAcKey failed: ${e.message}")
        }
    }

    private fun isAvmActive(): Boolean {
        return try {
            val pm = CarGateway.manager.value
            if (pm != null) {
                val state = pm.getIntProperty(PROP_AVM_DISPLAY, 0)
                state == AVM_ON
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun closeAvm() {
        try {
            val pm = CarGateway.manager.value
            if (pm != null) {
                pm.setIntArrayProperty(PROP_AVM_SWITCH, 0, intArrayOf(AVM_CMD_OFF, AVM_SOURCE_LAUNCHER))
            }
        } catch (e: Exception) {
        }
    }

    private fun startMirrorProjection() {
        try {
            if (isIndependentProjectionRunning) {
                stopIndependentProjection()
            }
            setupMirrorProjection()
        } catch (e: Exception) {
            Log.d(Utils.SERVICE_TAG, "startMirrorProjection failed: ${e.message}")
            isProjectionRunning = false
        }
    }

    private fun stopMirrorProjection() {
        try {
            videoViewPresentation?.dismiss()
            videoViewPresentation = null
        } catch (e: Exception) {
            Log.w(Utils.SERVICE_TAG, "dismiss mirror projection failed: ${e.message}")
        }

        isProjectionRunning = false
        Toast.makeText(this, "仪表投屏已暂停", Toast.LENGTH_SHORT).show()
    }

    private fun startIndependentProjection() {
        try {
            val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            val targetPackage = prefs.getString("projection_app", "")
            if (targetPackage.isNullOrEmpty()) {
                Toast.makeText(this, "请先在设置中选择要独立投屏的APP", Toast.LENGTH_SHORT).show()
                return
            }

            try {
                packageManager.getApplicationInfo(targetPackage, 0)
            } catch (_: Exception) {
                Toast.makeText(this, "目标APP未安装，请重新选择", Toast.LENGTH_SHORT).show()
                return
            }

            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val secondaryDisplay = dm.displays.firstOrNull { it.displayId != android.view.Display.DEFAULT_DISPLAY }
            if (secondaryDisplay == null) {
                Toast.makeText(this, "未检测到副屏，独立投屏不可用", Toast.LENGTH_SHORT).show()
                return
            }

            if (isProjectionRunning) {
                stopMirrorProjection()
            }

            independentDisplayPresentation?.dismiss()
            independentDisplayPresentation = null

            independentDisplayPresentation = IndependentDisplayPresentation(this, secondaryDisplay, targetPackage)
            independentDisplayPresentation?.show()
            isIndependentProjectionRunning = true
            Toast.makeText(this, "独立投屏已启动", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(Utils.SERVICE_TAG, "startIndependentProjection failed: ${e.message}", e)
            Toast.makeText(this, "独立投屏启动失败", Toast.LENGTH_SHORT).show()
            isIndependentProjectionRunning = false
        }
    }

    private fun stopIndependentProjection() {
        try {
            independentDisplayPresentation?.dismiss()
            independentDisplayPresentation = null
        } catch (e: Exception) {
            Log.w(Utils.SERVICE_TAG, "dismiss independent projection failed: ${e.message}")
        }
        IndependentDisplayPresentation.dismissActive()
        isIndependentProjectionRunning = false
        Toast.makeText(this, "独立投屏已暂停", Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    private fun setupMirrorProjection() {
        try {
            videoViewPresentation?.dismiss()

            val displays = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).displays
            val secondaryDisplay = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

            if (secondaryDisplay != null) {
                videoViewPresentation = VideoViewPresentation(this, secondaryDisplay)
                videoViewPresentation?.show()
                isProjectionRunning = true
            }
        } catch (e: Exception) {
            Log.d(Utils.SERVICE_TAG, "setupMirrorProjection failed: ${e.message}")
        }
    }

    companion object {
        const val EXTRA_ACTION_KEY = "ACTION_KEY"

        @Volatile
        var isRunning = false

        // VHAL 属性 ID，随车型/固件变化需同步调整
        private const val PROP_AVM_DISPLAY = 557903540
        private const val PROP_AVM_SWITCH = 557969072

        private const val AVM_ON = 2

        private const val PROP_REMAINING_OIL = 0x2140f334

        private const val PROP_TOTAL_MILEAGE = 0x2160f338

        private const val FUEL_PROMPT_DELTA = 5

        private const val FUEL_COOLDOWN_MS = 10 * 60 * 1000L

        private const val FUEL_PERCENT_VALID_MAX = 100

        private const val KEY_FUEL_LAST_FILL_AT = "fuel_last_fill_at"

        private const val KEY_FUEL_REF_LOW = "fuel_ref_low"

        private const val AVM_CMD_OFF = 1
        private const val AVM_SOURCE_LAUNCHER = 1

        // 座舱按键与 HVAC 属性，AREA_DRIVER=1 为主驾区
        private const val PROP_MCU_KEY_INFO = 0x2141F04B
        private const val PROP_MCU_KEY_POWER_LEFT = 0x2140F04C
        private const val PROP_MCU_KEY_POWER_RIGHT = 0x2140F04D
        private const val PROP_HVAC_TEMPERATURE_ADJUST = 0x25411912
        private const val PROP_HVAC_FAN_SPEED_ADJUST = 0x25411908
        private const val PROP_HVAC_AC_ON_SET = 0x2540051E
        private const val AREA_DRIVER = 1
    }
}
