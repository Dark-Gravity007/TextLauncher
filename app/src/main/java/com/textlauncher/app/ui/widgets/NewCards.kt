package com.textlauncher.app.ui.widgets

import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textlauncher.app.LauncherState
import com.textlauncher.app.data.CalEvent
import com.textlauncher.app.data.CalendarRepo
import com.textlauncher.app.data.Checklist
import com.textlauncher.app.data.DeviceStats
import com.textlauncher.app.data.HabitLogic
import com.textlauncher.app.data.QuickLinks
import com.textlauncher.app.data.repo.AppRepository
import com.textlauncher.app.search.Calc
import com.textlauncher.app.ui.common.pressable
import com.textlauncher.app.ui.theme.Palette
import com.textlauncher.app.ui.theme.Type
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

// ================================================================== agenda (todoagenda-style)

private fun dayLabel(day: Int, today: Int, ms: Long): String = when (day) {
    today -> "TODAY"
    today + 1 -> "TOMORROW"
    else -> SimpleDateFormat("EEEE d MMM", Locale.getDefault()).format(Date(ms)).uppercase()
}

@Composable
internal fun CalendarCard(state: LauncherState, size: Int = 2) {
    val agenda = rememberAgenda(state, 6)
    if (size < 2) {
        AgendaSummary(agenda, size)
        return
    }
    val today = HabitLogic.today(agenda.now)
    val next: CalEvent? = agenda.events.firstOrNull { !it.allDay } ?: agenda.events.firstOrNull()

    GlassCard {
        CardLabel("Agenda")
        when {
            !agenda.granted -> {
                Text(
                    text = "Your next events with a live countdown, read from the calendar on this phone only.",
                    style = Type.body,
                    color = Palette.Secondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "Allow calendar access",
                    style = Type.bodyStrong,
                    color = Palette.Label,
                    modifier = Modifier
                        .pressable(onClick = agenda.request)
                        .padding(top = 10.dp, bottom = 4.dp),
                )
            }
            next == null -> Text(
                text = "Nothing coming up.",
                style = Type.body,
                color = Palette.Secondary,
                modifier = Modifier.padding(top = 12.dp),
            )
            else -> {
                Text(
                    text = CalendarRepo.countdown(next.begin, next.end, agenda.now),
                    style = Type.answer,
                    color = Palette.Label,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(text = next.title, style = Type.bodyStrong, color = Palette.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                var lastDay = Int.MIN_VALUE
                for (e in agenda.events) {
                    val day = HabitLogic.today(e.begin)
                    if (day != lastDay) {
                        lastDay = day
                        Text(
                            text = dayLabel(day, today, e.begin),
                            style = Type.section,
                            color = Palette.Tertiary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
                        )
                    }
                    Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (e.allDay) "All day" else timeText(e.begin),
                            style = Type.caption,
                            color = Palette.Tertiary,
                            modifier = Modifier.width(54.dp),
                        )
                        Text(
                            text = e.title,
                            style = Type.caption,
                            color = if (e === next) Palette.Label else Palette.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (e !== next && !e.allDay) {
                            Text(
                                text = CalendarRepo.countdown(e.begin, e.end, agenda.now).removePrefix("In "),
                                style = Type.caption,
                                color = Palette.Tertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaSummary(agenda: AgendaState, size: Int) {
    val next = agenda.events.firstOrNull { !it.allDay } ?: agenda.events.firstOrNull()
    val s = when {
        !agenda.granted -> Summary("Next up", "Calendar", "Tap to allow", actionLabel = "Allow", onAction = agenda.request, onTap = agenda.request)
        next == null -> Summary("Next up", "Free", "Nothing coming up")
        else -> Summary("Next up", CalendarRepo.countdown(next.begin, next.end, agenda.now), next.title)
    }
    SummaryCard(s, size)
}

// ================================================================== hydration + habits (FlowState-style)

private val habitNames = listOf("Read", "Move", "Focus")
private const val GLASSES = 8
private val Water = Color(0xFF64B5F6)

@Composable
internal fun TrackerCard(state: LauncherState, size: Int = 2) {
    if (size < 2) {
        TrackerSummary(state, size)
        return
    }
    val prefs = state.prefs
    val today = HabitLogic.today()
    val water = if (prefs.int("waterDay", 0) == today) prefs.int("waterCount", 0) else 0

    fun setWater(n: Int) {
        prefs.setInt("waterDay", today)
        prefs.setInt("waterCount", n.coerceAtLeast(0))
    }

    GlassCard {
        CardLabel("Hydration")
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until GLASSES) {
                Droplet(filled = i < water) { setWater(if (water >= i + 1) i else i + 1) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (water * 250).toString() + " / " + (GLASSES * 250) + " ml",
                style = Type.caption,
                color = Palette.Secondary,
            )
            Row {
                Text(
                    text = "−250",
                    style = Type.bodyStrong,
                    color = Palette.Secondary,
                    modifier = Modifier.pressable(onClick = { setWater(water - 1) }).padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    text = "+250 ml",
                    style = Type.bodyStrong,
                    color = Palette.Label,
                    modifier = Modifier.pressable(onClick = { setWater(water + 1) }).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Box(Modifier.height(14.dp))
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
                Text(text = name, style = Type.body, color = Palette.Label, modifier = Modifier.weight(1f).padding(start = 14.dp))
                if (streak > 0) {
                    Text(text = "$streak day" + (if (streak == 1) "" else "s"), style = Type.caption, color = Palette.Secondary)
                }
            }
        }
    }
}

/** A water drop: a circle with a pointed top, filled in when you've had that glass. */
@Composable
private fun Droplet(filled: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(
        targetValue = if (filled) Water else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "drop",
    )
    Canvas(
        modifier = Modifier
            .size(width = 26.dp, height = 36.dp)
            .pressable(onClick = onClick)
    ) {
        val r = size.width / 2f
        val cx = size.width / 2f
        val cy = size.height - r
        val alpha = Math.toDegrees(acos((r / cy).toDouble())).toFloat()
        val rad = Math.toRadians(alpha.toDouble())
        val path = Path().apply {
            moveTo(cx, 0f)
            lineTo(cx + r * sin(rad).toFloat(), cy - r * cos(rad).toFloat())
            arcTo(
                rect = Rect(Offset(cx - r, cy - r), Size(2 * r, 2 * r)),
                startAngleDegrees = -90f + alpha,
                sweepAngleDegrees = 360f - 2 * alpha,
                forceMoveTo = false,
            )
            close()
        }
        drawPath(path, color = fill)
        drawPath(path, color = Color.White.copy(alpha = 0.6f), style = Stroke(width = 1.5.dp.toPx()))
    }
}

// ================================================================== notes + checklist

@Composable
internal fun NotesCard(state: LauncherState, size: Int = 2) {
    if (size < 2) {
        NotesSummary(state, size)
        return
    }
    val prefs = state.prefs
    var text by remember { mutableStateOf(prefs.note) }
    val todosRaw = prefs.str("todos", "")
    val items = remember(todosRaw) { Checklist.parse(todosRaw) }
    var draft by remember { mutableStateOf("") }

    fun saveItems(list: List<com.textlauncher.app.data.CheckItem>) = prefs.setStr("todos", Checklist.encode(list))
    fun addDraft() {
        saveItems(Checklist.add(items, draft))
        draft = ""
    }

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
                .heightIn(min = 64.dp)
                .padding(top = 10.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("Write something…", style = Type.body, color = Palette.Tertiary)
                    inner()
                }
            },
        )

        Box(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CardLabel("Checklist")
            if (items.isNotEmpty()) {
                Text(
                    text = Checklist.doneCount(items).toString() + "/" + items.size,
                    style = Type.caption,
                    color = Palette.Secondary,
                )
            }
        }
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(filled = item.done, size = 24) { saveItems(Checklist.toggle(items, index)) }
                Text(
                    text = item.text,
                    style = Type.body.copy(textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None),
                    color = if (item.done) Palette.Tertiary else Palette.Label,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                Text(
                    text = "×",
                    style = Type.bodyStrong,
                    color = Palette.Tertiary,
                    modifier = Modifier
                        .pressable(onClick = { saveItems(Checklist.remove(items, index)) })
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(120) },
                singleLine = true,
                textStyle = Type.body.copy(color = Palette.Label),
                cursorBrush = SolidColor(Palette.Label),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addDraft() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(Modifier.padding(vertical = 6.dp)) {
                        if (draft.isEmpty()) Text("Add an item", style = Type.body, color = Palette.Tertiary)
                        inner()
                    }
                },
            )
            Text(
                text = "Add",
                style = Type.bodyStrong,
                color = if (draft.isBlank()) Palette.Tertiary else Palette.Label,
                modifier = Modifier
                    .pressable(onClick = { addDraft() })
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            )
        }
    }
}

// ================================================================== unified dashboard (Lwidget-style bento)

@Composable
internal fun DashboardCard(state: LauncherState, size: Int = 2) {
    if (size < 2) {
        DashboardSummary(state, size)
        return
    }
    val context = LocalContext.current
    val battery = rememberBattery()
    val memory = remember(state.resumeTick) { DeviceStats.memory(context) }
    val storage = remember(state.resumeTick) { DeviceStats.storage() }
    val cpu = remember(state.resumeTick) { DeviceStats.cpuTempC() }
    val steps = state.steps
    val goal = state.prefs.stepGoal
    val count = steps.stepsToday

    GlassCard {
        CardLabel("Dashboard")
        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BentoTile(
                    "Memory",
                    (memory.usedFraction * 100).toInt().toString() + "%",
                    Formatter.formatShortFileSize(context, memory.freeBytes) + " free",
                    memory.usedFraction,
                    if (memory.usedFraction > 0.9f) AccentLow else Palette.Label,
                    Modifier.weight(1f),
                )
                BentoTile(
                    "Storage",
                    Formatter.formatShortFileSize(context, storage.freeBytes),
                    "free of " + Formatter.formatShortFileSize(context, storage.totalBytes),
                    storage.usedFraction,
                    if (storage.usedFraction > 0.9f) AccentLow else Palette.Label,
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BentoTile(
                    "Temperature",
                    if (battery.temperatureC > 0f) String.format(Locale.getDefault(), "%.0f °C", battery.temperatureC) else "—",
                    if (cpu != null) String.format(Locale.getDefault(), "CPU %.0f °C", cpu) else "Battery " + battery.healthLabel,
                    null,
                    Palette.Label,
                    Modifier.weight(1f),
                )
                BentoTile(
                    "Steps",
                    if (steps.isAvailable && steps.permissionGranted) count.toString() else "—",
                    if (steps.isAvailable && steps.permissionGranted) "of $goal" else "needs access",
                    if (steps.isAvailable && steps.permissionGranted && goal > 0) count.toFloat() / goal else null,
                    Palette.Label,
                    Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Battery " + battery.percent + "%" + (if (battery.charging) " · charging" else ""),
                    style = Type.caption,
                    color = Palette.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
            ProgressBar(
                fraction = battery.percent / 100f,
                color = if (battery.percent <= 15 && !battery.charging) AccentLow else Palette.Label,
            )
        }
    }
}

@Composable
private fun BentoTile(
    label: String,
    value: String,
    sub: String,
    fraction: Float?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text = label, style = Type.caption, color = Palette.Tertiary)
        Text(text = value, style = Type.answer.copy(fontSize = 26.sp, lineHeight = 32.sp), color = Palette.Label, maxLines = 1)
        Text(text = sub, style = Type.caption, color = Palette.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f))
        if (fraction != null) ProgressBar(fraction = fraction, color = color)
    }
}

@Composable
private fun DashboardSummary(state: LauncherState, size: Int) {
    val context = LocalContext.current
    val battery = rememberBattery()
    val memory = remember(state.resumeTick) { DeviceStats.memory(context) }
    val storage = remember(state.resumeTick) { DeviceStats.storage() }
    val temp = if (battery.temperatureC > 0f) String.format(Locale.getDefault(), " · %.0f °C", battery.temperatureC) else ""
    SummaryCard(
        Summary(
            "Dashboard",
            "RAM " + (memory.usedFraction * 100).toInt() + "%",
            "Free " + Formatter.formatShortFileSize(context, storage.freeBytes) + " · Bat " + battery.percent + "%" + temp,
            fraction = memory.usedFraction,
        ),
        size,
    )
}

// ================================================================== calculator

private val calcRows = listOf(
    listOf("C", "⌫", "(", ")"),
    listOf("7", "8", "9", "÷"),
    listOf("4", "5", "6", "×"),
    listOf("1", "2", "3", "−"),
    listOf("0", ".", "=", "+"),
)

@Composable
internal fun CalculatorCard(state: LauncherState, size: Int = 2) {
    if (size < 2) {
        CalcSummary(state, size)
        return
    }
    val prefs = state.prefs
    var expr by remember { mutableStateOf(prefs.str("calcExpr", "")) }
    val preview = remember(expr) {
        if (Calc.looksLikeMath(expr)) Calc.evaluate(expr)?.let { Calc.format(it) } else null
    }

    fun press(key: String) {
        expr = when (key) {
            "C" -> ""
            "⌫" -> expr.dropLast(1)
            "=" -> {
                val v = if (Calc.looksLikeMath(expr)) Calc.evaluate(expr) else null
                if (v != null) Calc.format(v) else expr
            }
            "−" -> if (expr.length < 40) "$expr-" else expr
            else -> if (expr.length < 40) expr + key else expr
        }
        prefs.setStr("calcExpr", expr)
    }

    GlassCard {
        CardLabel("Calculator")
        Text(
            text = expr.ifEmpty { "0" },
            style = Type.answer,
            color = Palette.Label,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Text(
            text = if (preview != null) "= $preview" else " ",
            style = Type.caption,
            color = Palette.Secondary,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in calcRows) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (key in row) {
                        CalcKey(key, Modifier.weight(1f)) { press(key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKey(key: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isEquals = key == "="
    val isOp = key in listOf("÷", "×", "−", "+", "C", "⌫", "(", ")")
    Box(
        modifier = modifier
            .height(50.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isEquals -> Palette.Label
                    isOp -> Color.White.copy(alpha = 0.16f)
                    else -> Color.White.copy(alpha = 0.08f)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key,
            style = Type.drawerItem,
            color = if (isEquals) Color.Black else Palette.Label,
        )
    }
}

// ================================================================== hub, small versions

@Composable
internal fun HubCompact(state: LauncherState, size: Int) {
    val context = LocalContext.current
    val links = QuickLinks.all.take(4)
    GlassCard(modifier = if (size == 0) Modifier.height(156.dp) else Modifier) {
        CardLabel("Hub")
        Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (size == 0) {
                for (pair in links.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (link in pair) LinkTile(link, Modifier.weight(1f)) { runQuickLink(context, state, link) }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (link in links) LinkTile(link, Modifier.weight(1f)) { runQuickLink(context, state, link) }
                }
            }
        }
    }
}
