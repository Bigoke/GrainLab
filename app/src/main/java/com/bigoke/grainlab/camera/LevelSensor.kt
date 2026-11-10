package com.bigoke.grainlab.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.atan2

/** Phone tilt in degrees around the lens axis (0 = level in portrait), smoothed. */
@Composable
fun rememberRoll(enabled: Boolean): State<Float> {
    val context = LocalContext.current
    val roll = remember { mutableFloatStateOf(0f) }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]
                val y = e.values[1]
                val deg = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
                roll.floatValue = roll.floatValue * 0.8f + deg * 0.2f
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return roll
}
