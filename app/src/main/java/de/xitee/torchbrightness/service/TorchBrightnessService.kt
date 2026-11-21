package de.xitee.torchbrightness.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import de.xitee.torchbrightness.App
import de.xitee.torchbrightness.R
import de.xitee.torchbrightness.brightness.LightSensors
import de.xitee.torchbrightness.brightness.LuxSmoother
import de.xitee.torchbrightness.brightness.SystemBrightness
import de.xitee.torchbrightness.curve.BrightnessCurve
import de.xitee.torchbrightness.curve.CurveAccess
import de.xitee.torchbrightness.curve.DumpParser
import de.xitee.torchbrightness.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Watches the flashlight (whoever turns it on). While it is on and adaptive brightness was enabled,
 * adaptive brightness is paused and the brightness is set from the front light sensor only, using the
 * system's own curve read from `dumpsys display`. Otherwise the system would also use the rear sensor
 * (on some Motorola devices), which the flashlight blinds. Restores everything when the flashlight turns
 * off. Main thread only.
 */
class TorchBrightnessService : LifecycleService() {

    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { (application as App).prefs }
    private lateinit var brightness: SystemBrightness
    private lateinit var sensors: LightSensors
    private lateinit var cameraManager: CameraManager
    private val curveReader by lazy { (application as App).curveReader }
    private val smoother = LuxSmoother()

    private val torchStates = HashMap<String, Boolean>()
    /** True while settings are changed by us (adaptive brightness paused). */
    private var controlling = false
    private var userOverride = false
    private var skipReason: ControlStatus? = null
    private var curveUnsupported = false
    private var lastWritten = -1
    private var controlJob: Job? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            torchStates[cameraId] = enabled
            onTorchChanged()
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            torchStates[cameraId] = false
            onTorchChanged()
        }
    }

    private val brightnessObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            if (controlling && !userOverride && brightness.value() != lastWritten) {
                userOverride = true
                updateStatus()
            }
        }
    }

    private val modeObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            // We only ever switch to manual; auto while controlling means the user turned it back on.
            if (controlling && brightness.isAutomatic()) {
                Log.i(TAG, "Adaptive brightness re-enabled by the user, giving up control")
                controlJob?.cancel()
                controlling = false
                prefs.clearState()
                updateStatus()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        brightness = SystemBrightness(this)
        sensors = LightSensors(this)
        cameraManager = getSystemService(CameraManager::class.java)
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, brightnessObserver,
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, modeObserver,
        )
        // Reports the current state of every torch right away, which also triggers crash recovery.
        cameraManager.registerTorchCallback(torchCallback, main)
        lifecycleScope.launch {
            refreshCurve()
            curveReader.access.collect { updateStatus() }
        }
        updateStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started again from the UI, e.g. after a permission was granted.
        curveReader.refreshAccess()
        updateStatus()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        cameraManager.unregisterTorchCallback(torchCallback)
        contentResolver.unregisterContentObserver(brightnessObserver)
        contentResolver.unregisterContentObserver(modeObserver)
        stopControl()
        restoreSavedState()
        TorchRuntime.status.value = ControlStatus.OFF
        TorchRuntime.writtenValue.value = null
        super.onDestroy()
    }

    private fun onTorchChanged() {
        val on = torchStates.values.any { it }
        TorchRuntime.torchOn.value = on
        when {
            on && controlJob == null -> startControl()
            !on && controlJob != null -> {
                stopControl()
                restoreSavedState()
                // Pick up curve changes (e.g. brightness slider adjustments) for the next time.
                lifecycleScope.launch { refreshCurve() }
            }
            // Recovery: the app was killed while the flashlight was on.
            !on && prefs.hasSavedState -> restoreSavedState()
        }
        updateStatus()
    }

    /** Reads the curve; returns null (and remembers why) if that is not possible. */
    private suspend fun refreshCurve(): BrightnessCurve? {
        val result = runCatching { curveReader.read() }
            .onFailure { Log.w(TAG, "Cannot read brightness curve", it) }
            .getOrNull()
        curveUnsupported = result is DumpParser.Result.NotFound
        if (result is DumpParser.Result.Success) TorchRuntime.curve.value = result.curve
        updateStatus()
        return (result as? DumpParser.Result.Success)?.curve
    }

    private fun startControl() {
        skipReason = null
        controlJob = lifecycleScope.launch {
            if (!brightness.canWrite()) return@launch
            // Start the front sensor right away; it lives as long as this job.
            val front = sensors.lux(sensors.front).shareIn(this, SharingStarted.Eagerly, replay = 1)
            // Use the cached curve right away; reading a fresh one can take a moment.
            val curve = TorchRuntime.curve.value ?: refreshCurve()
            if (curve == null) {
                skipReason = if (curveUnsupported) ControlStatus.CURVE_UNSUPPORTED else ControlStatus.NO_CURVE_ACCESS
                updateStatus()
                return@launch
            }
            if (!prefs.hasSavedState) {
                if (!brightness.isAutomatic()) {
                    skipReason = ControlStatus.SKIPPED_MANUAL
                    updateStatus()
                    return@launch
                }
                prefs.saveState(brightness.value())
            }
            controlling = true
            userOverride = false
            // Wait briefly for a fresh reading; the system only reacts to the rear sensor after ~500 ms.
            val initial = withTimeoutOrNull(FIRST_READING_TIMEOUT_MS) { front.first() } ?: prefs.lastLux
            smoother.reset(initial, SystemClock.elapsedRealtime())
            runCatching {
                apply(initial, force = true)
                if (brightness.isAutomatic()) brightness.setAutomatic(false)
            }.onFailure { Log.e(TAG, "Cannot change brightness", it) }
            updateStatus()
            launch { if (refreshCurve() != null) runCatching { apply(smoother.value ?: initial, force = false) } }
            front.collect { lux ->
                val smoothed = smoother.update(lux, SystemClock.elapsedRealtime())
                runCatching { apply(smoothed, force = false) }
            }
        }
    }

    private fun stopControl() {
        controlJob?.cancel()
        controlJob = null
        smoother.value?.let { prefs.lastLux = it }
        controlling = false
        userOverride = false
        skipReason = null
        lastWritten = -1
        TorchRuntime.writtenValue.value = null
    }

    private fun restoreSavedState() {
        if (!prefs.hasSavedState) return
        runCatching {
            brightness.setValue(prefs.savedBrightness)
            brightness.setAutomatic(true)
        }.onFailure { Log.e(TAG, "Cannot restore brightness", it) }
        prefs.clearState()
    }

    private fun apply(lux: Float, force: Boolean) {
        if (userOverride) return
        val curve = TorchRuntime.curve.value ?: return
        val target = curve.settingFor(lux, prefs.settings.value.adjustmentPercent / 100f)
        if (force || abs(target - lastWritten) >= MIN_STEP) {
            lastWritten = target
            brightness.setValue(target)
            TorchRuntime.writtenValue.value = target
        }
    }

    private fun updateStatus() {
        val on = TorchRuntime.torchOn.value
        val access = curveReader.access.value
        TorchRuntime.status.value = when {
            !brightness.canWrite() -> ControlStatus.NO_PERMISSION
            controlling && userOverride -> ControlStatus.USER_OVERRIDE
            controlling -> ControlStatus.CONTROLLING
            on && skipReason != null -> skipReason!!
            TorchRuntime.curve.value == null && curveUnsupported -> ControlStatus.CURVE_UNSUPPORTED
            TorchRuntime.curve.value == null &&
                (access == CurveAccess.NONE || access == CurveAccess.SHIZUKU_NO_PERMISSION) -> ControlStatus.NO_CURVE_ACCESS
            else -> ControlStatus.WAITING
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startInForeground() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_MIN),
        )
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    private fun buildNotification(): Notification {
        val text = getString(
            when (TorchRuntime.status.value) {
                ControlStatus.CONTROLLING -> R.string.notification_controlling
                ControlStatus.NO_PERMISSION, ControlStatus.NO_CURVE_ACCESS, ControlStatus.CURVE_UNSUPPORTED ->
                    R.string.notification_setup_needed
                else -> R.string.notification_waiting
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_torch)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "TorchBrightness"
        private const val CHANNEL_ID = "service"
        private const val NOTIFICATION_ID = 1
        private const val FIRST_READING_TIMEOUT_MS = 350L
        /** Minimum change of the setting value before writing, avoids constant tiny writes. */
        private const val MIN_STEP = 2

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TorchBrightnessService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TorchBrightnessService::class.java))
        }
    }
}
