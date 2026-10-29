package com.textlauncher.app.data

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import java.util.Calendar

/** Mobile and Wi-Fi traffic totals the phone has already counted. Read locally; needs Usage access. */
object DataUsage {

    fun dayStart(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun monthStart(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun daysInMonth(): Int = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)

    /** Total bytes (down + up), or null if the phone will not tell us. */
    fun bytes(context: Context, mobile: Boolean, startMs: Long, endMs: Long): Long? {
        return try {
            val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val type = if (mobile) ConnectivityManager.TYPE_MOBILE else ConnectivityManager.TYPE_WIFI
            val bucket = nsm.querySummaryForDevice(type, null, startMs, endMs)
            bucket.rxBytes + bucket.txBytes
        } catch (e: Exception) {
            null
        }
    }
}
