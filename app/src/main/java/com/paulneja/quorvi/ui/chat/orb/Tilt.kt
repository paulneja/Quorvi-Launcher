package com.paulneja.quorvi.ui.chat.orb

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun rememberTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled) {
        val sm = if (enabled) context.getSystemService(SensorManager::class.java) else null
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (sm == null || sensor == null) {
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        val rot = FloatArray(9)
        val angles = FloatArray(3)
        var rest: FloatArray? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, angles)
                val base = rest ?: floatArrayOf(angles[1], angles[2]).also { rest = it }
                val x = ((angles[2] - base[1]) / 0.55f).coerceIn(-1f, 1f)
                val y = ((angles[1] - base[0]) / 0.55f).coerceIn(-1f, 1f)
                base[0] += (angles[1] - base[0]) * 0.008f
                base[1] += (angles[2] - base[1]) * 0.008f
                val old = tilt.value
                tilt.value = Offset(old.x * 0.82f + x * 0.18f, old.y * 0.82f + y * 0.18f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return tilt
}
