package com.textlauncher.app.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs

/** Everything here is read from the device itself. No network, no permissions. */
class BatteryInfo(
    val percent: Int,
    val charging: Boolean,
    val full: Boolean,
    val temperatureC: Float,
    val health: Int = BatteryManager.BATTERY_HEALTH_UNKNOWN,
) {
    val healthLabel: String
        get() = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Worn out"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            else -> "Unknown"
        }

    companion object {
        /** [intent] is the sticky ACTION_BATTERY_CHANGED broadcast. */
        fun from(intent: Intent?): BatteryInfo {
            if (intent == null) return BatteryInfo(0, false, false, 0f)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val percent = if (level >= 0 && scale > 0) (level * 100f / scale).toInt().coerceIn(0, 100) else 0
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
            val full = status == BatteryManager.BATTERY_STATUS_FULL
            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            return BatteryInfo(percent, charging, full, temp, health)
        }
    }
}

class SpaceInfo(val usedBytes: Long, val totalBytes: Long) {
    val freeBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0L)
    val usedFraction: Float get() = if (totalBytes <= 0L) 0f else (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

object DeviceStats {

    /**
     * CPU temperature in °C, or null. Recent Android versions often hide the thermal sensors from apps,
     * so the widget simply leaves this out when it cannot be read.
     */
    fun cpuTempC(): Float? {
        return try {
            val zones = java.io.File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
            var best: Float? = null
            for (z in zones) {
                val type = try { java.io.File(z, "type").readText().trim().lowercase() } catch (e: Exception) { "" }
                if (!(type.contains("cpu") || type.contains("soc") || type.contains("tsens") || type.contains("big") || type.contains("little"))) continue
                val raw = try { java.io.File(z, "temp").readText().trim().toFloatOrNull() } catch (e: Exception) { null } ?: continue
                val c = if (raw > 1000f) raw / 1000f else raw
                if (c in 15f..120f && (best == null || c > best)) best = c
            }
            best
        } catch (e: Exception) {
            null
        }
    }

    fun storage(): SpaceInfo {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            SpaceInfo(total - free, total)
        } catch (e: Exception) {
            SpaceInfo(0L, 0L)
        }
    }

    fun memory(context: Context): SpaceInfo {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            SpaceInfo(info.totalMem - info.availMem, info.totalMem)
        } catch (e: Exception) {
            SpaceInfo(0L, 0L)
        }
    }
}
