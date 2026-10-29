package com.textlauncher.app.ui.widgets

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textlauncher.app.LauncherState
import com.textlauncher.app.data.CustomElement
import com.textlauncher.app.data.CustomWidget
import com.textlauncher.app.data.CustomWidgetStore
import com.textlauncher.app.data.DeviceStats
import com.textlauncher.app.data.QuickLinks
import com.textlauncher.app.data.ScreenTime
import com.textlauncher.app.ui.common.pressable
import com.textlauncher.app.ui.theme.Palette
import com.textlauncher.app.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_ELEMENTS = 8

// ---------------------------------------------------------------- the card a custom widget renders as

@Composable
internal fun CustomWidgetCard(widget: CustomWidget, state: LauncherState) {
    GlassCard {
        if (widget.title.isNotBlank()) CardLabel(widget.title)
        for (element in widget.elements) {
            ElementView(element, state)
        }
        if (widget.elements.isEmpty()) {
            Text("Empty widget", style = Type.caption, color = Palette.Tertiary)
        }
    }
}

@Composable
private fun ElementView(e: CustomElement, state: LauncherState) {
    val context = LocalContext.current
    when (e.type) {
        "text" -> Text(
            text = e.arg,
            style = Type.body,
            color = Palette.Label,
            modifier = Modifier.padding(top = 8.dp),
        )
        "clock" -> {
            val now = rememberMinuteClock(state.resumeTick)
            val pattern = if (state.prefs.clock24) "HH:mm" else "h:mm"
            val time = remember(now, pattern) { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now)) }
            Text(
                text = time,
                style = Type.clock.copy(fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1.6).sp),
                color = Palette.Label,
            )
        }
        "stat" -> StatLine(e.arg, state)
        "action" -> {
            val link = QuickLinks.byId(e.arg)
            if (link != null) {
                LinkTile(link, Modifier.padding(top = 8.dp)) { runQuickLink(context, state, link) }
            }
        }
    }
}

@Composable
private fun StatLine(kind: String, state: LauncherState) {
    val context = LocalContext.current
    val battery = rememberBattery()
    val now = rememberMinuteClock(state.resumeTick)
    val storage = remember(state.resumeTick) { DeviceStats.storage() }
    val memory = remember(state.resumeTick) { DeviceStats.memory(context) }
    var screen by remember { mutableStateOf(0L) }

    LaunchedEffect(kind, now) {
        if (kind == "screentime" && ScreenTime.hasAccess(context)) {
            screen = withContext(Dispatchers.Default) {
                try { ScreenTime.todayMillis(context) } catch (e: Exception) { 0L }
            }
        }
    }

    val label: String
    val value: String
    when (kind) {
        "battery" -> { label = "Battery"; value = battery.percent.toString() + "%" }
        "storage" -> { label = "Storage free"; value = Formatter.formatShortFileSize(context, storage.freeBytes) }
        "memory" -> { label = "Memory used"; value = (memory.usedFraction * 100).toInt().toString() + "%" }
        "steps" -> { label = "Steps today"; value = NumberFormat.getIntegerInstance().format(state.steps.stepsToday) }
        "screentime" -> { label = "Screen time"; value = ScreenTime.format(screen) }
        "date" -> { label = "Today"; value = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now)) }
        else -> { label = kind; value = "" }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = Type.caption, color = Palette.Secondary)
        Text(text = value, style = Type.bodyStrong, color = Palette.Label)
    }
}

// ---------------------------------------------------------------- the builder ("playground")

private fun describe(e: CustomElement): String = when (e.type) {
    "text" -> "Text: " + e.arg
    "clock" -> "Digital clock"
    "stat" -> "Stat: " + (CustomWidgetStore.statChoices.firstOrNull { it.first == e.arg }?.second ?: e.arg)
    "action" -> "Quick action: " + (QuickLinks.byId(e.arg)?.label ?: e.arg)
    else -> e.type
}

/**
 * Build a card from blocks: text, a digital clock, a system stat or a quick action. The preview updates live.
 * [editing] is the widget being changed, or null to make a new one.
 */
@Composable
fun CustomWidgetBuilder(
    state: LauncherState,
    editing: CustomWidget?,
    onDone: () -> Unit,
) {
    val prefs = state.prefs
    var title by remember { mutableStateOf(editing?.title ?: "") }
    val elements = remember {
        mutableStateListOf<CustomElement>().also { list -> if (editing != null) list.addAll(editing.elements) }
    }
    var adding by remember { mutableStateOf("") } // "", "text", "stat" or "action"
    var draft by remember { mutableStateOf("") }

    BackHandler(enabled = true) { onDone() }

    fun addElement(e: CustomElement) {
        if (elements.size < MAX_ELEMENTS) elements.add(e)
        adding = ""
        draft = ""
    }

    fun save() {
        val existing = CustomWidgetStore.parse(prefs.str("custom", ""))
        val id = editing?.id ?: System.currentTimeMillis()
        val widget = CustomWidget(id, title.trim(), elements.toList())
        val updated = if (editing != null) {
            existing.map { if (it.id == id) widget else it }
        } else {
            existing + widget
        }
        prefs.setStr("custom", CustomWidgetStore.toJson(updated))
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Cancel",
                        style = Type.bodyStrong,
                        color = Palette.Secondary,
                        modifier = Modifier.pressable(onClick = onDone).padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                    Text(if (editing != null) "Edit widget" else "New widget", style = Type.bodyStrong, color = Palette.Label)
                    Text(
                        "Save",
                        style = Type.bodyStrong,
                        color = Palette.Label,
                        modifier = Modifier.pressable(onClick = { save() }).padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
            }

            item {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    singleLine = true,
                    textStyle = Type.title.copy(color = Palette.Label),
                    cursorBrush = SolidColor(Palette.Label),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                    decorationBox = { inner ->
                        Box {
                            if (title.isEmpty()) Text("Title (optional)", style = Type.title, color = Palette.Tertiary)
                            inner()
                        }
                    },
                )
            }

            item {
                Text("PREVIEW", style = Type.section, color = Palette.Tertiary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                CustomWidgetCard(CustomWidget(0L, title, elements.toList()), state)
            }

            item {
                Text(
                    "BLOCKS (${elements.size}/$MAX_ELEMENTS)",
                    style = Type.section,
                    color = Palette.Tertiary,
                    modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
                )
            }
            itemsIndexed(elements.toList()) { index, element ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = describe(element),
                        style = Type.body,
                        color = Palette.Label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    )
                    BlockButton("↑") { if (index > 0) { val e = elements.removeAt(index); elements.add(index - 1, e) } }
                    BlockButton("↓") { if (index < elements.size - 1) { val e = elements.removeAt(index); elements.add(index + 1, e) } }
                    BlockButton("×") { elements.removeAt(index) }
                }
            }

            item {
                Text("ADD A BLOCK", style = Type.section, color = Palette.Tertiary, modifier = Modifier.padding(top = 28.dp, bottom = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AddChoice("Text", adding == "text") { adding = "text"; draft = "" }
                    AddChoice("Clock", false) { addElement(CustomElement("clock", "")) }
                    AddChoice("Stat", adding == "stat") { adding = "stat" }
                    AddChoice("Action", adding == "action") { adding = "action" }
                }
            }

            if (adding == "text") {
                item {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.take(120) },
                        textStyle = Type.body.copy(color = Palette.Label),
                        cursorBrush = SolidColor(Palette.Label),
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        decorationBox = { inner ->
                            Box {
                                if (draft.isEmpty()) Text("Type something…", style = Type.body, color = Palette.Tertiary)
                                inner()
                            }
                        },
                    )
                    Text(
                        "Add text",
                        style = Type.bodyStrong,
                        color = if (draft.isBlank()) Palette.Tertiary else Palette.Label,
                        modifier = Modifier
                            .pressable(onClick = { if (draft.isNotBlank()) addElement(CustomElement("text", draft.trim())) })
                            .padding(vertical = 12.dp),
                    )
                }
            }
            if (adding == "stat") {
                itemsIndexed(CustomWidgetStore.statChoices) { _, choice ->
                    Text(
                        text = choice.second,
                        style = Type.drawerItem,
                        color = Palette.Label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable(onClick = { addElement(CustomElement("stat", choice.first)) })
                            .padding(vertical = 10.dp),
                    )
                }
            }
            if (adding == "action") {
                itemsIndexed(QuickLinks.all) { _, link ->
                    Text(
                        text = link.label,
                        style = Type.drawerItem,
                        color = Palette.Label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable(onClick = { addElement(CustomElement("action", link.id)) })
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockButton(glyph: String, onClick: () -> Unit) {
    Text(
        text = glyph,
        style = Type.bodyStrong,
        color = Palette.Secondary,
        modifier = Modifier
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun AddChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = Type.body.copy(fontSize = 16.sp),
        color = if (selected) Palette.Label else Palette.Secondary,
        modifier = Modifier
            .pressable(onClick = onClick)
            .padding(end = 14.dp, top = 8.dp, bottom = 8.dp),
    )
}
