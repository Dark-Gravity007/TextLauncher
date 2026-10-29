package com.textlauncher.app.ui.widgets

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textlauncher.app.LauncherState
import com.textlauncher.app.data.CalendarRepo
import com.textlauncher.app.data.CalEvent
import com.textlauncher.app.data.DataUsage
import com.textlauncher.app.data.DeviceStats
import com.textlauncher.app.data.HabitLogic
import com.textlauncher.app.data.QuickLink
import com.textlauncher.app.data.QuickLinks
import com.textlauncher.app.data.ScreenTime
import com.textlauncher.app.data.SunCalc
import com.textlauncher.app.data.SunLocation
import com.textlauncher.app.data.repo.AppRepository
import com.textlauncher.app.system.LockAccessibilityService
import com.textlauncher.app.ui.common.pressable
import com.textlauncher.app.ui.theme.Palette
import com.textlauncher.app.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Runs a shortcut tile: opens a settings page, or lets the launcher do it (flashlight, lock). */
internal fun runQuickLink(context: Context, state: LauncherState, link: QuickLink) {
    val intent = link.intent
    when {
        intent != null -> AppRepository.startSafely(context, Intent(intent))
        link.id == "flashlight" -> {
            if (!state.torch.toggle()) Toast.makeText(context, "No flashlight available", Toast.LENGTH_SHORT).show()
        }
        link.id == "lock" -> {
            if (!LockAccessibilityService.lock()) {
                Toast.makeText(context, "Turn on the lock service in Settings → Access", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun timeText(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

// ---------------------------------------------------------------- screen time

@Composable
internal fun ScreenTimeCard(state: LauncherState) {
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

    GlassCard {
        CardLabel("Screen time")
        if (!granted) {
            Text(
                text = "Allow usage access to see how long you used your phone today.",
                style = Type.body,
                color = Palette.Secondary,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = "Open usage access",
                style = Type.bodyStrong,
                color = Palette.Label,
                modifier = Modifier
                    .pressable(onClick = { AppRepository.startSafely(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
                    .padding(top = 10.dp, bottom = 4.dp),
            )
        } else {
            val total = perApp.values.sum()
            Text(
                text = ScreenTime.format(total),
                style = Type.clock.copy(fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.5).sp),
                color = Palette.Label,
                modifier = Modifier.padding(top = 8.dp),
            )
            val top = perApp.entries.take(3)
            val biggest = (top.firstOrNull()?.value ?: 1L).coerceAtLeast(1L)
            for (entry in top) {
                val label = state.apps.firstOrNull { it.packageName == entry.key }?.label ?: entry.key
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = label,
                            style = Type.caption,
                            color = Palette.Label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(text = ScreenTime.format(entry.value), style = Type.caption, color = Palette.Secondary)
                    }
                    Box(Modifier.height(6.dp))
                    ProgressBar(fraction = entry.value.toFloat() / biggest, color = Palette.Label)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- system health

@Composable
internal fun HealthCard(state: LauncherState) {
    val context = LocalContext.current
    val battery = rememberBattery()
    val memory = remember(state.resumeTick) { DeviceStats.memory(context) }
    val storage = remember(state.resumeTick) { DeviceStats.storage() }
    val cpu = remember(state.resumeTick) { DeviceStats.cpuTempC() }

    GlassCard {
        CardLabel("System health")
        Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HealthTile("Memory", (memory.usedFraction * 100).toInt().toString() + "%", Modifier.weight(1f))
            HealthTile("Free", Formatter.formatShortFileSize(context, storage.freeBytes), Modifier.weight(1f))
        }
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HealthTile(
                "Battery",
                if (battery.temperatureC > 0f) String.format(Locale.getDefault(), "%.0f °C", battery.temperatureC) else "—",
                Modifier.weight(1f),
                sub = battery.healthLabel,
            )
            HealthTile(
                "CPU",
                if (cpu != null) String.format(Locale.getDefault(), "%.0f °C", cpu) else "—",
                Modifier.weight(1f),
                sub = if (cpu == null) "not exposed" else null,
            )
        }
    }
}

@Composable
private fun HealthTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text = label, style = Type.caption, color = Palette.Tertiary)
        Text(text = value, style = Type.bodyStrong.copy(fontSize = 22.sp, lineHeight = 28.sp), color = Palette.Label)
        if (sub != null) Text(text = sub, style = Type.caption, color = Palette.Secondary)
    }
}

// ---------------------------------------------------------------- calendar: next up + short agenda

@Composable
internal fun CalendarCard(state: LauncherState) {
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

    LaunchedEffect(now, granted, state.resumeTick) {
        granted = CalendarRepo.hasPermission(context)
        events = if (granted) {
            withContext(Dispatchers.Default) { CalendarRepo.upcoming(context, System.currentTimeMillis(), 4) }
        } else {
            emptyList()
        }
    }

    GlassCard {
        CardLabel("Next up")
        if (!granted) {
            Text(
                text = "Show your next event with a countdown. Read from the calendar on this phone only.",
                style = Type.body,
                color = Palette.Secondary,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = "Allow calendar access",
                style = Type.bodyStrong,
                color = Palette.Label,
                modifier = Modifier
                    .pressable(onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) })
                    .padding(top = 10.dp, bottom = 4.dp),
            )
        } else {
            val next = events.firstOrNull { !it.allDay } ?: events.firstOrNull()
            if (next == null) {
                Text(
                    text = "Nothing coming up.",
                    style = Type.body,
                    color = Palette.Secondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                Text(
                    text = CalendarRepo.countdown(next.begin, next.end, now),
                    style = Type.answer,
                    color = Palette.Label,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = next.title,
                    style = Type.bodyStrong,
                    color = Palette.Label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val rest = events.filter { it !== next }.take(3)
                for (e in rest) {
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (e.allDay) "All day" else timeText(e.begin),
                            style = Type.caption,
                            color = Palette.Tertiary,
                            modifier = Modifier.width(52.dp),
                        )
                        Text(
                            text = e.title,
                            style = Type.caption,
                            color = Palette.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- data + Wi-Fi meter

private val dataLimits = listOf(1, 2, 5, 10, 20, 50)

@Composable
internal fun DataCard(state: LauncherState) {
    val context = LocalContext.current
    val prefs = state.prefs
    val now = rememberMinuteClock(state.resumeTick)
    val limitGb = prefs.int("dataLimitGb", 10)
    var granted by remember { mutableStateOf(true) }
    var mobileToday by remember { mutableStateOf<Long?>(null) }
    var mobileMonth by remember { mutableStateOf<Long?>(null) }
    var wifiToday by remember { mutableStateOf<Long?>(null) }
    var speed by remember { mutableStateOf(0L) }

    LaunchedEffect(now, state.resumeTick) {
        granted = ScreenTime.hasAccess(context)
        if (granted) {
            withContext(Dispatchers.Default) {
                val end = System.currentTimeMillis()
                mobileToday = DataUsage.bytes(context, true, DataUsage.dayStart(), end)
                mobileMonth = DataUsage.bytes(context, true, DataUsage.monthStart(), end)
                wifiToday = DataUsage.bytes(context, false, DataUsage.dayStart(), end)
            }
        }
    }

    // Live speed: bytes moved by the whole phone in the last second, from the system's own counters.
    LaunchedEffect(Unit) {
        var last = TrafficStats.getTotalRxBytes()
        while (true) {
            delay(1000)
            val cur = TrafficStats.getTotalRxBytes()
            speed = if (last >= 0L && cur >= last) cur - last else 0L
            last = cur
        }
    }

    val limitBytes = limitGb * 1_000_000_000L
    val dailyBudget = limitBytes / DataUsage.daysInMonth()

    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CardLabel("Data")
            Text(
                text = "Limit $limitGb GB",
                style = Type.caption,
                color = Palette.Secondary,
                modifier = Modifier.pressable(onClick = {
                    prefs.setInt("dataLimitGb", dataLimits.firstOrNull { it > limitGb } ?: dataLimits.first())
                }),
            )
        }
        if (!granted) {
            Text(
                text = "Allow usage access to read data totals from the phone.",
                style = Type.body,
                color = Palette.Secondary,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = "Open usage access",
                style = Type.bodyStrong,
                color = Palette.Label,
                modifier = Modifier
                    .pressable(onClick = { AppRepository.startSafely(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
                    .padding(top = 10.dp, bottom = 4.dp),
            )
        } else {
            val month = mobileMonth
            val today = mobileToday
            if (month == null || today == null) {
                Text(
                    text = "This phone won't share mobile data totals with apps.",
                    style = Type.body,
                    color = Palette.Secondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                UsageBar("This month", month, limitBytes, context, Modifier.padding(top = 12.dp))
                UsageBar("Today", today, dailyBudget, context, Modifier.padding(top = 14.dp))
            }
            Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    text = "↓ " + Formatter.formatShortFileSize(context, speed) + "/s",
                    style = Type.caption,
                    color = Palette.Label,
                )
                val wifi = wifiToday
                if (wifi != null) {
                    Text(
                        text = "Wi-Fi today " + Formatter.formatShortFileSize(context, wifi),
                        style = Type.caption,
                        color = Palette.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageBar(label: String, used: Long, budget: Long, context: Context, modifier: Modifier = Modifier) {
    val fraction = if (budget > 0L) (used.toFloat() / budget).coerceIn(0f, 1f) else 0f
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = Type.bodyStrong, color = Palette.Label)
            Text(
                text = Formatter.formatShortFileSize(context, used) + " of " + Formatter.formatShortFileSize(context, budget),
                style = Type.caption,
                color = Palette.Secondary,
            )
        }
        Box(Modifier.height(8.dp))
        ProgressBar(fraction = fraction, color = if (fraction > 0.9f) AccentLow else Palette.Label)
    }
}

// ---------------------------------------------------------------- launcher hub

@Composable
internal fun HubCard(state: LauncherState) {
    val context = LocalContext.current
    GlassCard {
        CardLabel("Hub")
        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in QuickLinks.all.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (link in row) {
                        LinkTile(link, Modifier.weight(1f)) { runQuickLink(context, state, link) }
                    }
                    for (i in row.size until 3) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun LinkTile(link: QuickLink, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(vertical = 14.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = link.label,
            style = Type.caption,
            color = Palette.Label,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

// ---------------------------------------------------------------- water + habits

private val habitNames = listOf("Read", "Move", "Focus")
private const val WATER_GOAL = 8

@Composable
internal fun TrackerCard(state: LauncherState) {
    val prefs = state.prefs
    val today = HabitLogic.today()
    val water = if (prefs.int("waterDay", 0) == today) prefs.int("waterCount", 0) else 0

    GlassCard {
        CardLabel("Water")
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until WATER_GOAL) {
                Dot(filled = i < water, size = 28) {
                    val next = if (water >= i + 1) i else i + 1
                    prefs.setInt("waterDay", today)
                    prefs.setInt("waterCount", next)
                }
            }
        }
        Text(
            text = "$water of $WATER_GOAL glasses",
            style = Type.caption,
            color = Palette.Secondary,
            modifier = Modifier.padding(top = 8.dp),
        )

        Box(Modifier.height(18.dp))
        CardLabel("Habits")
        for ((i, name) in habitNames.withIndex()) {
            val habit = HabitLogic.State(prefs.int("h${i}Last", 0), prefs.int("h${i}Streak", 0))
            val done = HabitLogic.doneToday(habit, today)
            val streak = HabitLogic.visibleStreak(habit, today)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(filled = done, size = 28) {
                    val next = HabitLogic.toggle(habit, today)
                    prefs.setInt("h${i}Last", next.lastDone)
                    prefs.setInt("h${i}Streak", next.streak)
                }
                Text(
                    text = name,
                    style = Type.body,
                    color = Palette.Label,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                )
                if (streak > 0) {
                    Text(text = "$streak day" + (if (streak == 1) "" else "s"), style = Type.caption, color = Palette.Secondary)
                }
            }
        }
    }
}

@Composable
private fun Dot(filled: Boolean, size: Int, onClick: () -> Unit) {
    val fill by animateColorAsState(
        targetValue = if (filled) Palette.Label else Color.Transparent,
        animationSpec = tween(durationMillis = 160),
        label = "dot",
    )
    Box(
        modifier = Modifier
            .size(size.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
    )
}

// ---------------------------------------------------------------- sunrise / golden hour

@Composable
internal fun SunCard(state: LauncherState) {
    val context = LocalContext.current
    val prefs = state.prefs
    val now = rememberMinuteClock(state.resumeTick)
    var granted by remember { mutableStateOf(SunLocation.hasPermission(context)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) Toast.makeText(context, "Allow Location in App info for sun times", Toast.LENGTH_LONG).show()
    }

    // Remember the last known spot so the card still works later without asking again.
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

    GlassCard {
        CardLabel("Sun")
        if (located == null) {
            Text(
                text = if (granted) {
                    "No location known yet. Open Maps once, then come back."
                } else {
                    "Show sunrise, golden hour and sunset. Uses only the last location your phone already knows."
                },
                style = Type.body,
                color = Palette.Secondary,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (!granted) {
                Text(
                    text = "Allow location access",
                    style = Type.bodyStrong,
                    color = Palette.Label,
                    modifier = Modifier
                        .pressable(onClick = { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) })
                        .padding(top = 10.dp, bottom = 4.dp),
                )
            }
        } else {
            val today = remember(now) { SunCalc.compute(located.first, located.second, now) }
            val tomorrow = remember(now) { SunCalc.compute(located.first, located.second, now + 86_400_000L) }
            val fraction = SunCalc.dayFraction(today, now)

            SunArc(fraction = fraction, modifier = Modifier.padding(top = 12.dp))

            val events = listOf(
                "Sunrise" to today.sunrise,
                "Golden hour" to today.goldenEveningStart,
                "Sunset" to today.sunset,
                "Dusk" to today.dusk,
                "Sunrise" to tomorrow.sunrise,
            ).filter { it.second != null && it.second!! > now }
            val next = events.firstOrNull()
            if (next != null) {
                Text(
                    text = next.first + " " + CalendarRepo.countdown(next.second!!, next.second!!, now).lowercase(),
                    style = Type.bodyStrong,
                    color = Palette.Label,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                today.sunrise?.let { Text("Rise " + timeText(it), style = Type.caption, color = Palette.Secondary) }
                today.goldenEveningStart?.let { Text("Golden " + timeText(it), style = Type.caption, color = Palette.Secondary) }
                today.sunset?.let { Text("Set " + timeText(it), style = Type.caption, color = Palette.Secondary) }
            }
        }
    }
}

@Composable
private fun SunArc(fraction: Float?, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(86.dp)) {
        val stroke = 3.dp.toPx()
        val pad = 14.dp.toPx()
        val r = (size.width - 2 * pad) / 2f
        val cx = size.width / 2f
        val cy = size.height - 10.dp.toPx()
        // horizon
        drawLine(Color.White.copy(alpha = 0.25f), Offset(pad / 2, cy), Offset(size.width - pad / 2, cy), strokeWidth = 1.5.dp.toPx())
        // the sun's path
        drawArc(
            color = Color.White.copy(alpha = 0.2f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(2 * r, 2 * r),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (fraction != null) {
            drawArc(
                color = Color(0xFFFFC857),
                startAngle = 180f,
                sweepAngle = 180f * fraction,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val theta = PI * (1.0 - fraction)
            val sx = cx + r * cos(theta).toFloat()
            val sy = cy - r * sin(theta).toFloat()
            drawCircle(Color(0xFFFFC857), radius = 9.dp.toPx(), center = Offset(sx, sy))
        }
    }
}

// ---------------------------------------------------------------- quote card

private val builtInQuotes = listOf(
    "One thing at a time.",
    "Be where your feet are.",
    "Make it simple, then make it better.",
    "Slow is smooth, smooth is fast.",
    "Notice more. Scroll less.",
    "Quiet is a feature.",
    "Start where you are.",
    "Small steps still count.",
    "Look up.",
    "Enough is a decision.",
)

@Composable
internal fun QuoteCard(state: LauncherState) {
    val context = LocalContext.current
    val prefs = state.prefs
    val now = rememberMinuteClock(state.resumeTick)
    val custom = prefs.str("quotes", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val pool = if (custom.isNotEmpty()) custom else builtInQuotes
    val dayIndex = HabitLogic.today(now)
    val quote = pool[Math.floorMod(dayIndex, pool.size)]

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { it.take(240) }.take(500)
                if (lines.isEmpty()) {
                    Toast.makeText(context, "That file has no text lines", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.setStr("quotes", lines.joinToString("\n"))
                    Toast.makeText(context, "Loaded " + lines.size + " lines", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    GlassCard {
        CardLabel("Today")
        Text(
            text = quote,
            style = Type.drawerItem.copy(fontSize = 24.sp, lineHeight = 32.sp),
            color = Palette.Label,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                text = "Import .txt",
                style = Type.caption,
                color = Palette.Secondary,
                modifier = Modifier
                    .pressable(onClick = { picker.launch(arrayOf("text/*")) })
                    .padding(vertical = 6.dp),
            )
            if (custom.isNotEmpty()) {
                Text(
                    text = "Use built-in",
                    style = Type.caption,
                    color = Palette.Secondary,
                    modifier = Modifier
                        .pressable(onClick = { prefs.setStr("quotes", "") })
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- cassette player

@Composable
internal fun CassetteCard(state: LauncherState) {
    val context = LocalContext.current
    val np = state.nowPlaying
    var angle by remember { mutableStateOf(0f) }
    var position by remember { mutableStateOf(0L) }
    val title = np.title

    // Reels turn only while music plays.
    LaunchedEffect(np.playing) {
        if (np.playing) {
            var last = androidx.compose.runtime.withFrameMillis { it }
            while (true) {
                val t = androidx.compose.runtime.withFrameMillis { it }
                angle = (angle + (t - last) * 0.12f) % 360f
                last = t
            }
        }
    }
    LaunchedEffect(np.playing, title) {
        while (true) {
            position = np.positionMs()
            delay(500)
        }
    }

    GlassCard {
        CardLabel("Cassette")
        if (title == null) {
            Text(
                text = if (np.hasAccess) "Nothing playing. Start some music." else "Allow notification access to show what is playing.",
                style = Type.body,
                color = Palette.Secondary,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (!np.hasAccess) {
                Text(
                    text = "Open notification access",
                    style = Type.bodyStrong,
                    color = Palette.Label,
                    modifier = Modifier
                        .pressable(onClick = {
                            AppRepository.startSafely(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        })
                        .padding(top = 10.dp, bottom = 4.dp),
                )
            }
        } else {
            Box(modifier = Modifier.padding(top = 12.dp).fillMaxWidth().height(150.dp)) {
                Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                    val body = CornerRadius(18.dp.toPx(), 18.dp.toPx())
                    drawRoundRect(Color.White.copy(alpha = 0.07f), cornerRadius = body)
                    drawRoundRect(Color.White.copy(alpha = 0.28f), cornerRadius = body, style = Stroke(1.5.dp.toPx()))
                    // paper label
                    drawRoundRect(
                        Color.White.copy(alpha = 0.14f),
                        topLeft = Offset(14.dp.toPx(), 12.dp.toPx()),
                        size = Size(size.width - 28.dp.toPx(), 56.dp.toPx()),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    )
                    // tape window with two reels
                    val windowTop = 78.dp.toPx()
                    val windowH = 58.dp.toPx()
                    drawRoundRect(
                        Color.Black.copy(alpha = 0.35f),
                        topLeft = Offset(size.width * 0.18f, windowTop),
                        size = Size(size.width * 0.64f, windowH),
                        cornerRadius = CornerRadius(windowH / 2, windowH / 2),
                    )
                    val reelR = 21.dp.toPx()
                    val cy = windowTop + windowH / 2
                    for (cx in listOf(size.width * 0.32f, size.width * 0.68f)) {
                        val center = Offset(cx, cy)
                        drawCircle(Color.White.copy(alpha = 0.85f), radius = reelR, center = center, style = Stroke(2.dp.toPx()))
                        rotate(degrees = angle, pivot = center) {
                            for (k in 0 until 6) {
                                rotate(degrees = k * 60f, pivot = center) {
                                    drawLine(
                                        Color.White.copy(alpha = 0.85f),
                                        Offset(cx, cy - reelR * 0.88f),
                                        Offset(cx, cy - reelR * 0.38f),
                                        strokeWidth = 2.dp.toPx(),
                                        cap = StrokeCap.Round,
                                    )
                                }
                            }
                        }
                        drawCircle(Color.White.copy(alpha = 0.85f), radius = 5.dp.toPx(), center = center)
                    }
                }
                Row(
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, top = 18.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val art = np.art
                    if (art != null) {
                        Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                        Box(Modifier.width(12.dp))
                    }
                    Column {
                        Text(text = title, style = Type.bodyStrong, color = Palette.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val artist = np.artist
                        if (artist != null) {
                            Text(text = artist, style = Type.caption, color = Palette.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            val duration = np.durationMs
            if (duration > 0L) {
                Box(Modifier.height(10.dp))
                ProgressBar(fraction = position.toFloat() / duration, color = Palette.Label)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text("Prev", style = Type.bodyStrong, color = Palette.Secondary,
                    modifier = Modifier.pressable(onClick = { np.previous() }).padding(12.dp))
                Text(if (np.playing) "Pause" else "Play", style = Type.bodyStrong, color = Palette.Label,
                    modifier = Modifier.pressable(onClick = { np.togglePlayPause() }).padding(12.dp))
                Text("Next", style = Type.bodyStrong, color = Palette.Secondary,
                    modifier = Modifier.pressable(onClick = { np.next() }).padding(12.dp))
            }
        }
    }
}
