package com.textlauncher.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract

class CalEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

/** Reads the calendar already on the phone. No account sign-in, no network. */
object CalendarRepo {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events that have not finished yet, soonest first, within the next [horizonMs]. */
    fun upcoming(context: Context, nowMs: Long, limit: Int, horizonMs: Long = 2L * 86_400_000L): List<CalEvent> {
        if (!hasPermission(context)) return emptyList()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, nowMs)
        ContentUris.appendId(builder, nowMs + horizonMs)
        val out = ArrayList<CalEvent>()
        try {
            val cursor = context.contentResolver.query(
                builder.build(),
                arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                ),
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC",
            ) ?: return out
            cursor.use {
                while (it.moveToNext() && out.size < limit * 3) {
                    val title = it.getString(0)?.trim().orEmpty().ifEmpty { "Event" }
                    val begin = it.getLong(1)
                    val end = it.getLong(2)
                    val allDay = it.getInt(3) == 1
                    if (end > nowMs) out.add(CalEvent(title, begin, end, allDay))
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return out.take(limit)
    }

    /** "In 42 min", "In 2 h 5 min", "Now" ... */
    fun countdown(beginMs: Long, endMs: Long, nowMs: Long): String {
        if (nowMs >= beginMs && nowMs < endMs) return "Now"
        val minutes = ((beginMs - nowMs) / 60_000L).coerceAtLeast(0L)
        return when {
            minutes < 1 -> "Starting"
            minutes < 60 -> "In $minutes min"
            minutes < 24 * 60 -> {
                val h = minutes / 60
                val m = minutes % 60
                if (m == 0L) "In $h h" else "In $h h $m min"
            }
            else -> "In ${minutes / (24 * 60)} d"
        }
    }
}
