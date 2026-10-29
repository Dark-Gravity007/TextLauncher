package com.textlauncher.app.ui.widgets

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.textlauncher.app.LauncherState
import com.textlauncher.app.data.BatteryInfo
import com.textlauncher.app.data.DeviceStats
import com.textlauncher.app.data.SpaceInfo
import com.textlauncher.app.data.repo.AppRepository
import com.textlauncher.app.ui.common.pressable
import com.textlauncher.app.ui.theme.Palette
import com.textlauncher.app.ui.theme.Type
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val AccentLow = Color(0xFFFF6961)
internal val AccentGood = Color(0xFF5AD67D)
private val stepGoals = listOf(4000, 6000, 8000, 10000, 12000)

/**
 * The secondary page: offline widgets on frosted-style cards. Everything here is read from the phone itself;
 * nothing touches the network.
 */
@Composable
fun WidgetPage(
    state: LauncherState,
    scrimAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val prefs = state.prefs
    val customJson = prefs.str("custom", "")
    val custom = remember(customJson) { com.textlauncher.app.data.CustomWidgetStore.parse(customJson) }
    val extrasOn = listOf("wCalendar", "wCassette", "wScreenTime", "wHealth", "wData", "wSun", "wTracker", "wHub", "wQuote")
        .any { prefs.flag(it, true) }
    val anyOn = extrasOn || custom.isNotEmpty() ||
        prefs.widgetClock || prefs.widgetBattery || prefs.widgetSteps || prefs.widgetStorage || prefs.widgetNotes

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (prefs.widgetClock) ClockCard(state)
            if (prefs.flag("wCalendar", true)) CalendarCard(state)
            if (prefs.flag("wCassette", true)) CassetteCard(state)
            if (prefs.widgetBattery) BatteryCard()
            if (prefs.widgetSteps) StepsCard(state)
            if (prefs.flag("wScreenTime", true)) ScreenTimeCard(state)
            if (prefs.flag("wHealth", true)) HealthCard(state)
            if (prefs.widgetStorage) StorageCard(state)
            if (prefs.flag("wData", true)) DataCard(state)
            if (prefs.flag("wSun", true)) SunCard(state)
            if (prefs.flag("wTracker", true)) TrackerCard(state)
            if (prefs.flag("wHub", true)) HubCard(state)
            if (prefs.flag("wQuote", true)) QuoteCard(state)
            for (w in custom) CustomWidgetCard(w, state)
            if (prefs.widgetNotes) NotesCard(state)
            if (!anyOn) {
                Text(
                    text = "No widgets turned on.\nEnable them in Settings → WIDGET PAGE.",
                    style = Type.body,
                    color = Palette.Tertiary,
                )
            }
        }
    }
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(20.dp),
        content = content,
    )
}

@Composable
internal fun CardLabel(text: String) {
    Text(text = text.uppercase(), style = Type.section, color = Palette.Tertiary)
}

// ---------------------------------------------------------------- clock + complications

@Composable
private fun ClockCard(state: LauncherState) {
    val context = LocalContext.current
    val now = rememberMinuteClock(state.resumeTick)
    val pattern = if (state.prefs.clock24) "HH:mm" else "h:mm"
    val time = remember(now, pattern) { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(now)) }
    val week = remember(now) { Calendar.getInstance().get(Calendar.WEEK_OF_YEAR) }
    val alarm = remember(now) { nextAlarmText(context) }

    GlassCard(
        modifier = Modifier.pressable(onClick = {
            AppRepository.startSafely(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
        })
    ) {
        if (state.prefs.int("clockStyle", 0) == 1) {
            // Stacked: hours over minutes, very thin and very large.
            val parts = time.split(":")
            val big = Type.clock.copy(fontSize = 104.sp, lineHeight = 98.sp, letterSpacing = (-4).sp)
            Text(text = parts[0], style = big, color = Palette.Label)
            Text(text = parts.getOrElse(1) { "" }, style = big, color = Palette.Label.copy(alpha = 0.55f))
        } else {
            Text(
                text = time,
                style = Type.clock.copy(fontSize = 68.sp, lineHeight = 72.sp, letterSpacing = (-2).sp),
                color = Palette.Label,
            )
        }
        Text(text = date, style = Type.date, color = Palette.Secondary)
        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(text = "Week $week", style = Type.caption, color = Palette.Tertiary)
            Text(
                text = if (alarm != null) "Alarm $alarm" else "No alarm set",
                style = Type.caption,
                color = Palette.Tertiary,
            )
        }
    }
}

private fun nextAlarmText(context: Context): String? {
    return try {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val info = am.nextAlarmClock ?: return null
        SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(info.triggerTime))
    } catch (e: Exception) {
        null
    }
}

// ---------------------------------------------------------------- battery

@Composable
private fun BatteryCard() {
    val battery = rememberBattery()
    val low = battery.percent <= 15 && !battery.charging
    val color = when {
        low -> AccentLow
        battery.charging -> AccentGood
        else -> Palette.Label
    }
    val status = when {
        battery.full -> "Fully charged"
        battery.charging -> "Charging"
        else -> "On battery"
    }

    GlassCard {
        CardLabel("Battery")
        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            RingWithText(
                fraction = battery.percent / 100f,
                color = color,
                text = battery.percent.toString() + "%",
            )
            Column {
                Text(text = status, style = Type.bodyStrong, color = Palette.Label)
                if (battery.temperatureC > 0f) {
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f °C", battery.temperatureC),
                        style = Type.caption,
                        color = Palette.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun rememberBattery(): BatteryInfo {
    val context = LocalContext.current
    var info by remember {
        mutableStateOf(BatteryInfo.from(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))))
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                info = BatteryInfo.from(intent)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (sticky != null) info = BatteryInfo.from(sticky)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return info
}

// ---------------------------------------------------------------- steps

@Composable
private fun StepsCard(state: LauncherState) {
    val context = LocalContext.current
    val steps = state.steps
    val prefs = state.prefs
    val number = remember { NumberFormat.getIntegerInstance() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            steps.start()
        } else {
            Toast.makeText(context, "Allow Physical activity in App info to count steps", Toast.LENGTH_LONG).show()
            AppRepository.openAppInfo(context, context.packageName)
        }
    }

    GlassCard {
        CardLabel("Activity")
        when {
            !steps.isAvailable -> {
                Text(
                    text = "This phone has no step sensor.",
                    style = Type.body,
                    color = Palette.Secondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            !steps.permissionGranted -> {
                Text(
                    text = "Count your steps with the phone's own sensor. Nothing leaves the device.",
                    style = Type.body,
                    color = Palette.Secondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "Allow activity access",
                    style = Type.bodyStrong,
                    color = Palette.Label,
                    modifier = Modifier
                        .pressable(onClick = { permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) })
                        .padding(top = 10.dp, bottom = 4.dp),
                )
            }
            else -> {
                val count = steps.stepsToday
                val goal = prefs.stepGoal
                val km = count * 0.75 / 1000.0
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    RingWithText(
                        fraction = if (goal > 0) count.toFloat() / goal else 0f,
                        color = if (count >= goal) AccentGood else Palette.Label,
                        text = number.format(count),
                        textSize = 17,
                    )
                    Column {
                        Text(text = "steps today", style = Type.bodyStrong, color = Palette.Label)
                        Text(
                            text = "Goal " + number.format(goal),
                            style = Type.caption,
                            color = Palette.Secondary,
                            modifier = Modifier
                                .pressable(onClick = {
                                    val next = stepGoals.firstOrNull { it > goal } ?: stepGoals.first()
                                    prefs.updateStepGoal(next)
                                })
                                .padding(vertical = 6.dp),
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "about %.1f km", km),
                            style = Type.caption,
                            color = Palette.Tertiary,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- storage + memory

@Composable
private fun StorageCard(state: LauncherState) {
    val context = LocalContext.current
    val storage = remember(state.resumeTick) { DeviceStats.storage() }
    val memory = remember(state.resumeTick) { DeviceStats.memory(context) }

    GlassCard {
        CardLabel("Storage")
        SpaceRow(context, "Storage", storage, Modifier.padding(top = 12.dp))
        SpaceRow(context, "Memory", memory, Modifier.padding(top = 16.dp))
    }
}

@Composable
internal fun SpaceRow(context: Context, label: String, space: SpaceInfo, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = Type.bodyStrong, color = Palette.Label)
            Text(
                text = Formatter.formatShortFileSize(context, space.freeBytes) + " free of " +
                    Formatter.formatShortFileSize(context, space.totalBytes),
                style = Type.caption,
                color = Palette.Secondary,
            )
        }
        Spacer6()
        ProgressBar(
            fraction = space.usedFraction,
            color = if (space.usedFraction > 0.9f) AccentLow else Palette.Label,
        )
    }
}

@Composable
internal fun Spacer6() {
    Box(Modifier.height(8.dp))
}

@Composable
internal fun ProgressBar(fraction: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 1f, stiffness = 120f),
        label = "bar",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.14f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

// ---------------------------------------------------------------- notes

@Composable
private fun NotesCard(state: LauncherState) {
    val prefs = state.prefs
    var text by remember { mutableStateOf(prefs.note) }

    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CardLabel("Notes")
            if (text.isNotEmpty()) {
                Text(
                    text = "Clear",
                    style = Type.caption,
                    color = Palette.Secondary,
                    modifier = Modifier.pressable(onClick = {
                        text = ""
                        prefs.updateNote("")
                    }),
                )
            }
        }
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                prefs.updateNote(it)
            },
            textStyle = Type.body.copy(color = Palette.Label),
            cursorBrush = SolidColor(Palette.Label),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .padding(top = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text("Write something…", style = Type.body, color = Palette.Tertiary)
                    }
                    inner()
                }
            },
        )
    }
}

// ---------------------------------------------------------------- shared drawing

@Composable
internal fun RingWithText(fraction: Float, color: Color, text: String, textSize: Int = 20) {
    Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        ProgressRing(fraction = fraction, color = color, modifier = Modifier.fillMaxSize())
        Text(
            text = text,
            style = Type.bodyStrong.copy(fontSize = textSize.sp, letterSpacing = (-0.4).sp),
            color = Palette.Label,
        )
    }
}

@Composable
internal fun ProgressRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 9.dp,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 1f, stiffness = 120f),
        label = "ring",
    )
    Canvas(modifier = modifier) {
        val w = stroke.toPx()
        val topLeft = Offset(w / 2f, w / 2f)
        val arc = Size(size.width - w, size.height - w)
        drawArc(
            color = Color.White.copy(alpha = 0.14f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arc,
            style = Stroke(width = w, cap = StrokeCap.Round),
        )
        if (animated > 0.001f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(width = w, cap = StrokeCap.Round),
            )
        }
    }
}

/** Current time in millis, refreshed every minute and whenever the launcher resumes. */
@Composable
internal fun rememberMinuteClock(resumeTick: Int): Long {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(resumeTick) {
        now = System.currentTimeMillis()
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                now = System.currentTimeMillis()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return now
}
