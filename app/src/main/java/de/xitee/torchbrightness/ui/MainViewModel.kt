package de.xitee.torchbrightness.ui

import android.app.Application
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.xitee.torchbrightness.App
import de.xitee.torchbrightness.brightness.LightSensors
import de.xitee.torchbrightness.brightness.SystemBrightness
import de.xitee.torchbrightness.service.ControlStatus
import de.xitee.torchbrightness.service.TorchBrightnessService
import de.xitee.torchbrightness.service.TorchRuntime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Adaptive brightness on/off and the manual brightness value as currently set in the system. */
data class SystemBrightnessState(val automatic: Boolean, val value: Int)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val prefs = app.prefs
    private val brightness = SystemBrightness(application)
    private val sensors = LightSensors(application)

    val settings = prefs.settings
    val status = TorchRuntime.status
    val torchOn = TorchRuntime.torchOn
    val writtenValue = TorchRuntime.writtenValue
    val curve = TorchRuntime.curve
    val curveAccess = app.curveReader.access
    val hasRearSensor = sensors.rear != null

    private val _canWrite = MutableStateFlow(brightness.canWrite())
    val canWrite: StateFlow<Boolean> = _canWrite.asStateFlow()

    val frontLux: StateFlow<Float?> = sensors.lux(sensors.front)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), null)
    val rearLux: StateFlow<Float?> = sensors.lux(sensors.rear)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), null)

    val systemBrightness: StateFlow<SystemBrightnessState> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        val resolver = application.contentResolver
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
        .map { SystemBrightnessState(brightness.isAutomatic(), brightness.value()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), SystemBrightnessState(brightness.isAutomatic(), brightness.value()))

    init {
        if (prefs.settings.value.enabled && status.value == ControlStatus.OFF) TorchBrightnessService.start(application)
    }

    /** Called when the app comes to the foreground: permissions may have changed in the meantime. */
    fun refresh() {
        _canWrite.value = brightness.canWrite()
        app.curveReader.refreshAccess()
        if (prefs.settings.value.enabled) TorchBrightnessService.start(getApplication())
    }

    fun setEnabled(enabled: Boolean) {
        prefs.setEnabled(enabled)
        if (enabled) TorchBrightnessService.start(getApplication()) else TorchBrightnessService.stop(getApplication())
    }

    fun setAdjustmentPercent(percent: Int) = prefs.setAdjustmentPercent(percent)

    fun requestShizukuPermission() = app.curveReader.requestShizukuPermission()

    fun writeSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${getApplication<App>().packageName}"))
}
