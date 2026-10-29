package com.textlauncher.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar

object ScreenTime {

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Milliseconds spent in apps since midnight (this launcher excluded). Call off the main thread. */
    fun todayMillis(context: Context): Long = todayByPackage(context).values.sum()

    /** Today's foreground time per app package, largest first. Call off the main thread. */
    fun todayByPackage(context: Context): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()

        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        val foregroundSince = HashMap<String, Long>()
        val totals = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> foregroundSince[pkg] = event.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val since = foregroundSince.remove(pkg)
                    if (since != null && pkg != context.packageName) {
                        totals[pkg] = (totals[pkg] ?: 0L) + (event.timeStamp - since)
                    }
                }
            }
        }
        for ((pkg, since) in foregroundSince) {
            if (pkg != context.packageName) totals[pkg] = (totals[pkg] ?: 0L) + (end - since)
        }
        return totals.filterValues { it > 0L }.entries.sortedByDescending { it.value }.associate { it.key to it.value }
    }

    fun format(millis: Long): String {
        val minutes = millis / 60_000L
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
