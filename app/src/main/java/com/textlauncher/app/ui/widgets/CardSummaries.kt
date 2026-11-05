package com.textlauncher.app.ui.widgets

import android.Manifest
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textlauncher.app.LauncherState
import com.textlauncher.app.data.CalEvent
import com.textlauncher.app.data.CalendarRepo
import com.textlauncher.app.search.Calc
import com.textlauncher.app.data.Checklist
import com.textlauncher.app.data.DataUsage
import com.textlauncher.app.data.DeviceStats
import com.textlauncher.app.data.HabitLogic
import com.textlauncher.app.data.ScreenTime
import com.textlauncher.app.data.SunCalc
import com.textlauncher.app.data.SunLocation
import com.textlauncher.app.data.repo.AppRepository
import com.textlauncher.app.ui.common.pressable
import com.textlauncher.app.ui.theme.Palette
import com.textlauncher.app.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What a card says when it is shown small. The same facts, laid out for a 1×1 tile or a 2×2 strip,
 * with a live action where it makes sense (play/pause, +250 ml, allow access) so small cards still do something.
 */
internal class Summary(
    val label: String,
    val value: String,
    val sub: String? = null,
    val fraction: Float? = null,
    val accent: Color? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val onTap: (() -> Unit)? = null,
    val thin: Boolean = false,
    val textValue: Boolean = false,
)

/** [size]: 0 = 1×1 tile (half width), 1 = 2×2 strip (full width). Larger sizes are drawn by each card itself. */
@Composable
internal fun SummaryCard(s: Summary, size: Int, modifier: Modifier = Modifier) {
    val accent = s.accent ?: Palette.Label
    val tap = s.onTap
    val cardModifier = if (tap != null) modifier.pressable(onClick = tap) else modifier

    if (size == 0) {
        GlassCard(modifier = cardModifier.height(156.dp)) {
            CardLabel(s.label)
            Box(Modifier.height(6.dp))
            SummaryValue(s, tile = true)
            if (s.sub != null) {
                Text(
                    text = s.sub,
                    style = Type.caption,
                    color = Palette.Secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.weight(1f))
            if (s.fraction != null) ProgressBar(fraction = s.fraction, color = accent)
            if (s.actionLabel != null && s.onAction != null) {
                Text(
                    text = s.actionLabel,
                    style = Type.bodyStrong,
                    color = Palette.Label,
                    modifier = Modifier
                        .pressable(onClick = s.onAction)
                        .padding(top = if (s.fraction != null) 8.dp else 0.dp),
                )
            }
        }
    } else {
        GlassCard(modifier = cardModifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (s.fraction != null) {
                    Box(Modifier.size(64.dp)) {
                        ProgressRing(fraction = s.fraction, color = accent, modifier = Modifier.fillMaxSize(), stroke = 7.dp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    CardLabel(s.label)
                    SummaryValue(s, tile = false)
                    if (s.sub != null) {
                        Text(
                            text = s.sub,
                            style = Type.caption,
                            color = Palette.Secondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (s.actionLabel != null && s.onAction != null) {
                    Text(
                        text = s.actionLabel,
                        style = Type.bodyStrong,
                        color = Palette.Label,
                        modifier = Modifier
                            .pressable(onClick = s.onAction)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryValue(s: Summary, tile: Boolean) {
    when {
        s.textValue -> Text(
            text = s.value,
            style = Type.bodyStrong,
            color = Palette.Label,
            maxLines = if (tile) 4 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        s.thin -> Text(
            text = s.value,
            style = Type.clock.copy(fontSize = if (tile) 40.sp else 52.sp, lineHeight = if (tile) 44.sp else 56.sp, letterSpacing = (-1.4).sp),
            color = Palette.Label,
            maxLines = 1,
        )
        else -> Text(
            text = s.value,
            style = Type.answer.copy(fontSize = if (tile) 28.sp else 34.sp, lineHeight = if (tile) 34.sp else 40.sp),
            color = Palette.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------------ shared data helpers

internal class AgendaState(val granted: Boolean, val events: List<CalEvent>, val now: Long, val request: () -> Unit)

@Composable
internal fun rememberAgenda(state: LauncherState, limit: Int): AgendaState {
    val context = LocalContext.current
    val now = rememberMinuteClock(state.resumeTick)
    var granted by remember { mutableStateOf(CalendarRepo.hasPermission(context)) }
    var events by remember { mutableStateOf<List<CalEvent>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) {
            Toast.makeText(context, "Allow Calendar in App info to see events", Toast.LENGTH_LONG).show()
            AppRepository.openAppInfo(context, context.packageName)
        }
    }

    LaunchedEffect(now, granted, state.resumeTick, limit) {
        granted = CalendarRepo.hasPermission(context)
        events = if (granted) {
            withContext(Dispatchers.Default) { CalendarRepo.upcoming(context, System.currentTimeMillis(), limit) }
        } else {
            emptyList()
        }
    }
    return AgendaState(granted, events, now) { launcher.launch(Manifest.permission.READ_CALENDAR) }
}

internal class SunState(
    val granted: Boolean,
    val located: Boolean,
    val today: SunCalc.Times?,
    val tomorrow: SunCalc.Times?,
    val now: Long,
    val request: () -> Unit,
)

@Composable
internal fun rememberSun(state: LauncherState): SunState {
    val context = LocalContext.current
    val prefs = state.prefs
    val now = rememberMinuteClock(state.resumeTick)
    var granted by remember { mutableStateOf(SunLocation.hasPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) Toast.makeText(context, "Allow Location in App info for sun times", Toast.LENGTH_LONG).show()
    }
    val located = remember(granted, state.resumeTick) {
        val fresh = SunLocation.lastKnown(context)
        if (fresh != null) prefs.setStr("sunLoc", fresh.first.toString() + "," + fresh.second.toString())
        val saved = prefs.str("sunLoc", "").split(",")
        if (saved.size == 2) {
            val lat = saved[0].toDoubleOrNull()
            val lon = saved[1].toDoubleOrNull()
            if (lat != null && lon != null) Pair(lat, lon) else null
        } else {
            null
        }
    }
    val today = if (located != null) remember(now, located) { SunCalc.compute(located.first, located.second, now) } else null
    val tomorrow = if (located != null) remember(now, located) { SunCalc.compute(located.first, located.second, now + 86_400_000L) } else null
    return SunState(granted, located != null, today, tomorrow, now) { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
}

/** The next sun event after now: its name and time. */
internal fun nextSunEvent(s: SunState): Pair<String, Long>? {
    val today = s.today ?: return null
    val tomorrow = s.tomorrow ?: return null
    val list = listOf(
        "Sunrise" to today.sunrise,
        "Golden hour" to today.goldenEveningStart,
        "Sunset" to today.sunset,
        "Dusk" to today.dusk,
        "Sunrise" to tomorrow.sunrise,
    )
    for (e in list) {
        val t = e.second
        if (t != null && t > s.now) return Pair(e.first, t)
    }
    return null
}

// ------------------------------------------------------------------ small versions of each card

@Composable
internal fun ClockSummary(state: LauncherState, size: Int) {
    val context = LocalContext.current
    val now = rememberMinuteClock(state.resumeTick)
    val pattern = if (state.prefs.clock24) "HH:mm" else "h:mm"
    val time = remember(now, pattern) { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now)) }
    SummaryCard(
        Summary("Clock", time, date, thin = true, onTap = {
            AppRepository.startSafely(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
        }),
        size,
    )
}

@Composable
internal fun BatterySummary(size: Int) {
    val battery = rememberBattery()
    val low = battery.percent <= 15 && !battery.charging
    val accent = when {
        low -> AccentLow
        battery.charging -> AccentGood
        else -> Palette.Label
    }
    val status = when {
        battery.full -> "Fully charged"
        battery.charging -> "Charging"
        else -> "On battery"
    }
    val temp = if (battery.temperatureC > 0f) String.format(Locale.getDefault(), " · %.0f °C", battery.temperatureC) else ""
    SummaryCard(Summary("Battery", battery.percent.toString() + "%", status + temp, battery.percent / 100f, accent), size)
}

@Composable
internal fun StepsSummary(state: LauncherState, size: Int) {
    val context = LocalContext.current
    val steps = state.steps
    val number = remember { NumberFormat.getIntegerInstance() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            steps.start()
        } else {
            Toast.makeText(context, "Allow Physical activity in App info to count steps", Toast.LENGTH_LONG).show()
            AppRepository.openAppInfo(context, context.packageName)
        }
    }
    val ask = { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
    val s = when {
        !steps.isAvailable -> Summary("Steps", "—", "No step sensor")
        !steps.permissionGranted -> Summary("Steps", "Off", "Tap to allow", actionLabel = "Allow", onAction = ask, onTap = ask)
        else -> {
            val goal = state.prefs.stepGoal
            val count = steps.stepsToday
            Summary(
                "Steps",
                number.format(count),
                "Goal " + number.format(goal),
                fraction = if (goal > 0) count.toFloat() / goal else 0f,
                accent = if (count >= goal) AccentGood else null,
            )
        }
    }
    SummaryCard(s, size)
}

@Composable
internal fun ScreenTimeSummary(state: LauncherState, size: Int) {
    val context = LocalContext.current
    val now = rememberMinuteClock(state.resumeTick)
    var granted by remember { mutableStateOf(true) }
    var perApp by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(now, state.resumeTick) {
        granted = ScreenTime.hasAccess(context)
        if (granted) {
            perApp = withContext(Dispatchers.Default) {
                try { ScreenTime.todayByPackage(context) } catch (e: Exception) { emptyMap() }
            }
        }
    }
    val s = if (!granted) {
        Summary("Screen time", "—", "Tap to allow usage access", onTap = {
            AppRepository.startSafely(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
    } else {
        val top = perApp.entries.firstOrNull()
        val topLabel = if (top != null) state.apps.firstOrNull { it.packageName == top.key }?.label ?: top.key else null
        Summary("Screen time", ScreenTime.format(perApp.values.sum()), if (topLabel != null) "Most: $topLabel" else "Today")
    }
    SummaryCard(s, size)
}

@Composable
internal fun HealthSummary(state: LauncherState, size: Int) {
    val context = LocalContext.current
    val battery = rememberBattery()
    val memory = remember(state.resumeTick) { DeviceStats.memory(context) }
    val storage = remember(state.resumeTick) { DeviceStats.storage() }
    val temp = if (battery.temperatureC > 0f) String.format(Locale.getDefault(), " · %.0f °C", battery.temperatureC) else ""
    SummaryCard(
        Summary(
            "System",
            "RAM " + (memory.usedFraction * 100).toInt() + "%",
            "Free " + Formatter.formatShortFileSize(context, storage.freeBytes) + temp,
            fraction = memory.usedFraction,
            accent = if (memory.usedFraction > 0.9f) AccentLow else null,
        ),
        size,
    )
}

@Composable
internal fun StorageSummary(state: LauncherState, size: Int) {
    val context = LocalContext.current
    val storage = remember(state.resumeTick) { DeviceStats.storage() }
    SummaryCard(
        Summary(
            "Storage",
            Formatter.formatShortFileSize(context, storage.freeBytes),
            "free of " + Formatter.formatShortFileSize(context, storage.totalBytes),
            fraction = storage.usedFraction,
            accent = if (storage.usedFraction > 0.9f) AccentLow else null,
        ),
        size,
    )
}

@Composable
internal fun DataSummary(state: LauncherState, size: Int) {
    val context = LocalContext.current
    val now = rememberMinuteClock(state.resumeTick)
    val limitGb = state.prefs.int("dataLimitGb", 10)
    var granted by remember { mutableStateOf(true) }
    var month by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(now, state.resumeTick) {
        granted = ScreenTime.hasAccess(context)
        if (granted) {
            month = withContext(Dispatchers.Default) {
                DataUsage.bytes(context, true, DataUsage.monthStart(), System.currentTimeMillis())
            }
        }
    }
    val used = month
    val s = when {
        !granted -> Summary("Data", "—", "Tap to allow usage access", onTap = {
            AppRepository.startSafely(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        used == null -> Summary("Data", "—", "Not available on this phone")
        else -> {
            val limit = limitGb * 1_000_000_000L
            val f = if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f
            Summary(
                "Data this month",
                Formatter.formatShortFileSize(context, used),
                "of $limitGb GB",
                fraction = f,
                accent = if (f > 0.9f) AccentLow else null,
            )
        }
    }
    SummaryCard(s, size)
}

@Composable
internal fun SunSummary(state: LauncherState, size: Int) {
    val sun = rememberSun(state)
    val s = when {
        !sun.located && !sun.granted ->
            Summary("Sun", "Off", "Tap to allow location", actionLabel = "Allow", onAction = sun.request, onTap = sun.request)
        !sun.located -> Summary("Sun", "—", "No location known yet")
        else -> {
            val next = nextSunEvent(sun)
            if (next == null) {
                Summary("Sun", "—", "No sun times today")
            } else {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(next.second))
                Summary("Sun", next.first, CalendarRepo.countdown(next.second, next.second, sun.now) + " · " + time)
            }
        }
    }
    SummaryCard(s, size)
}

@Composable
internal fun TrackerSummary(state: LauncherState, size: Int) {
    val prefs = state.prefs
    val today = HabitLogic.today()
    val glasses = if (prefs.int("waterDay", 0) == today) prefs.int("waterCount", 0) else 0
    var doneHabits = 0
    for (i in 0 until 3) {
        if (HabitLogic.doneToday(HabitLogic.State(prefs.int("h${i}Last", 0), prefs.int("h${i}Streak", 0)), today)) doneHabits++
    }
    SummaryCard(
        Summary(
            "Hydration",
            (glasses * 250).toString() + " ml",
            "$doneHabits of 3 habits done",
            fraction = (glasses / 8f).coerceIn(0f, 1f),
            accent = Color(0xFF64B5F6),
            actionLabel = "+250 ml",
            onAction = {
                prefs.setInt("waterDay", today)
                prefs.setInt("waterCount", glasses + 1)
            },
        ),
        size,
    )
}

@Composable
internal fun QuoteSummary(state: LauncherState, size: Int) {
    val now = rememberMinuteClock(state.resumeTick)
    val custom = state.prefs.str("quotes", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val pool = if (custom.isNotEmpty()) custom else builtInQuotes
    val quote = pool[Math.floorMod(HabitLogic.today(now), pool.size)]
    SummaryCard(Summary("Today", quote, textValue = true), size)
}

@Composable
internal fun CassetteSummary(state: LauncherState, size: Int) {
    val np = state.nowPlaying
    var position by remember { mutableStateOf(0L) }
    val title = np.title
    LaunchedEffect(np.playing, title) {
        while (true) {
            position = np.positionMs()
            kotlinx.coroutines.delay(500)
        }
    }
    val s = if (title == null) {
        Summary("Music", "—", if (np.hasAccess) "Nothing playing" else "Allow notification access in Settings")
    } else {
        val d = np.durationMs
        Summary(
            "Music",
            title,
            np.artist,
            fraction = if (d > 0L) (position.toFloat() / d).coerceIn(0f, 1f) else null,
            actionLabel = if (np.playing) "Pause" else "Play",
            onAction = { np.togglePlayPause() },
            textValue = true,
        )
    }
    SummaryCard(s, size)
}

@Composable
internal fun NotesSummary(state: LauncherState, size: Int) {
    val prefs = state.prefs
    val items = Checklist.parse(prefs.str("todos", ""))
    val first = prefs.note.lines().firstOrNull { it.isNotBlank() }
    val sub = when {
        items.isNotEmpty() -> Checklist.doneCount(items).toString() + " of " + items.size + " done"
        else -> "Tap to open"
    }
    SummaryCard(
        Summary(
            "Notes",
            first ?: "Empty",
            sub,
            fraction = if (items.isNotEmpty()) Checklist.doneCount(items).toFloat() / items.size else null,
            textValue = true,
            onTap = { prefs.setInt("size_notes", 2) },
        ),
        size,
    )
}

@Composable
internal fun CalcSummary(state: LauncherState, size: Int) {
    val prefs = state.prefs
    val expr = prefs.str("calcExpr", "")
    val result = if (Calc.looksLikeMath(expr)) Calc.evaluate(expr)?.let { Calc.format(it) } else null
    SummaryCard(
        Summary(
            "Calculator",
            result ?: expr.ifEmpty { "0" },
            if (result != null) expr else "Tap to open",
            onTap = { prefs.setInt("size_calculator", 2) },
        ),
        size,
    )
}
