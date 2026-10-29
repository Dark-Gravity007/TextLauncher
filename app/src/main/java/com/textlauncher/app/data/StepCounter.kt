package com.textlauncher.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar

/**
 * Today's steps from the phone's own step-counter chip. Works fully offline.
 *
 * The chip keeps a running total since boot. We remember the last total we saw and add the difference each time,
 * restarting at midnight. Steps taken before the launcher first reads the chip on a given day are not counted.
 */
class StepCounter(private val context: Context) : SensorEventListener {

    private val sp = context.getSharedPreferences("textlauncher_steps", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private var listening = false

    val isAvailable: Boolean = sensor != null

    var permissionGranted by mutableStateOf(checkPermission())
        private set

    var stepsToday by mutableStateOf(storedStepsForToday())
        private set

    private fun checkPermission(): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private fun dayKey(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    private fun storedStepsForToday(): Long =
        if (sp.getInt("day", 0) == dayKey()) sp.getLong("steps", 0L) else 0L

    /** Call on resume: re-checks the permission, rolls the day over and (re)starts listening. */
    fun start() {
        stop()
        permissionGranted = checkPermission()
        stepsToday = storedStepsForToday()
        if (sensor == null || !permissionGranted) return
        listening = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (listening) {
            manager.unregisterListener(this)
            listening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val total = event.values[0].toLong()
        val today = dayKey()
        val sameDay = sp.getInt("day", 0) == today
        val last = sp.getLong("last", -1L)
        var steps = if (sameDay) sp.getLong("steps", 0L) else 0L
        if (sameDay && last >= 0L) {
            steps += if (total >= last) total - last else total // total went down: the phone rebooted
        }
        sp.edit().putInt("day", today).putLong("last", total).putLong("steps", steps).apply()
        stepsToday = steps
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // not needed
    }
}
