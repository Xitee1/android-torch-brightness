package de.xitee.torchbrightness.brightness

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/** Front light sensor (the standard one) and, on Motorola devices, the rear "alternate light" sensor. */
class LightSensors(context: Context) {

    private val manager = context.getSystemService(SensorManager::class.java)

    val front: Sensor? = manager.getDefaultSensor(Sensor.TYPE_LIGHT)

    val rear: Sensor? = manager.getSensorList(Sensor.TYPE_ALL).firstOrNull {
        it.type == MOTOROLA_ALTERNATE_LIGHT_TYPE || it.stringType.contains("alternate_light")
    }

    /** Lux readings (first value of each event) while collected. */
    fun lux(sensor: Sensor?): Flow<Float> {
        if (sensor == null) return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    trySend(event.values[0])
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            awaitClose { manager.unregisterListener(listener) }
        }
    }

    private companion object {
        /** `com.motorola.sensor.alternate_light` (tcs344x_als, next to the rear cameras). */
        const val MOTOROLA_ALTERNATE_LIGHT_TYPE = 65578
    }
}
