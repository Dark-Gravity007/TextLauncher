package com.textlauncher.app.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.textlauncher.app.LauncherState
import com.textlauncher.app.data.CustomWidget
import com.textlauncher.app.data.CustomWidgetStore
import com.textlauncher.app.data.GestureAction
import com.textlauncher.app.data.ScreenTime
import com.textlauncher.app.data.prefs.PreferencesManager
import com.textlauncher.app.data.repo.AppRepository
import com.textlauncher.app.search.QuickActions
import com.textlauncher.app.system.LockAccessibilityService
import com.textlauncher.app.ui.common.pressable
import com.textlauncher.app.ui.theme.Palette
import com.textlauncher.app.ui.theme.Type
import com.textlauncher.app.ui.widgets.CustomWidgetBuilder

@Composable
fun SettingsScreen(
    state: LauncherState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = state.prefs
    val favorites = remember(state.apps, prefs.favorites) { state.favoriteApps() }
    var builderOpen by remember { mutableStateOf(false) }
    var builderEditing by remember { mutableStateOf<CustomWidget?>(null) }
    val customJson = prefs.str("custom", "")
    val customWidgets = remember(customJson) { CustomWidgetStore.parse(customJson) }
    val hiddenSet = prefs.hiddenApps()
    val hiddenApps = state.apps.filter { it.packageName in hiddenSet }
    var pickerSlot by remember { mutableStateOf(-1) } // 0 = swipe left, 1 = swipe right, 2 = double tap
    val favoritePackages = prefs.favorites.toSet()
    val others = state.apps.filter { it.packageName !in favoritePackages }

    val usageGranted = remember(state.resumeTick) { ScreenTime.hasAccess(context) }
    val notificationGranted = remember(state.resumeTick) { state.nowPlaying.isAccessGranted() }
    val lockEnabled = remember(state.resumeTick) { LockAccessibilityService.isEnabled(context) }

    fun describe(action: GestureAction): String = when (action) {
        GestureAction.None -> "Nothing"
        GestureAction.Drawer -> "App drawer"
        GestureAction.Settings -> "Launcher settings"
        GestureAction.Flashlight -> "Flashlight"
        GestureAction.LockScreen -> "Lock screen"
        GestureAction.Notifications -> "Notifications"
        GestureAction.Widgets -> "Widget page"
        is GestureAction.App -> state.apps.firstOrNull { it.packageName == action.packageName }?.label ?: action.packageName
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.Background)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Settings", style = Type.title, color = Palette.Label)
                    Text(
                        "Done",
                        style = Type.bodyStrong,
                        color = Palette.Label,
                        modifier = Modifier
                            .pressable(onClick = onClose)
                            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }

            item { Section("HOME") }
            item {
                OptionRow(
                    label = "Alignment",
                    options = listOf("Left", "Center", "Right"),
                    selected = prefs.alignment,
                    onSelect = { prefs.updateAlignment(it) },
                )
            }
            item {
                OptionRow(
                    label = "List position",
                    options = listOf("Top", "Center", "Bottom"),
                    selected = prefs.position,
                    onSelect = { prefs.updatePosition(it) },
                )
            }
            item {
                OptionRow(
                    label = "Text size",
                    options = listOf("Small", "Medium", "Large"),
                    selected = prefs.textSize,
                    onSelect = { prefs.updateTextSize(it) },
                )
            }

            item {
                OptionRow(
                    label = "Wallpaper dimming",
                    options = listOf("Auto", "Off", "Light", "Medium", "Strong"),
                    selected = prefs.dim,
                    onSelect = { prefs.updateDim(it) },
                )
            }

            item { Section("GESTURES") }
            item { ValueRow("Swipe left", describe(prefs.swipeLeft)) { pickerSlot = 0 } }
            item { ValueRow("Swipe right", describe(prefs.swipeRight)) { pickerSlot = 1 } }
            item { ValueRow("Double tap", describe(prefs.doubleTap)) { pickerSlot = 2 } }
            item {
                Text(
                    "Swipe up opens the drawer. Swipe down opens notifications. Tap the date for your calendar.",
                    style = Type.caption,
                    color = Palette.Tertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item { Section("WIDGETS") }
            item { ToggleRow("24-hour clock", prefs.clock24) { prefs.updateClock24(!prefs.clock24) } }
            item { ToggleRow("Screen time", prefs.showScreenTime) { prefs.updateShowScreenTime(!prefs.showScreenTime) } }
            item { ToggleRow("Now playing", prefs.showNowPlaying) { prefs.updateShowNowPlaying(!prefs.showNowPlaying) } }
            item { ToggleRow("Daily line", prefs.showQuote) { prefs.updateShowQuote(!prefs.showQuote) } }

            item { Section("WIDGET PAGE") }
            item { ToggleRow("Clock & date", prefs.widgetClock) { prefs.updateWidgetClock(!prefs.widgetClock) } }
            item { ToggleRow("Battery", prefs.widgetBattery) { prefs.updateWidgetBattery(!prefs.widgetBattery) } }
            item { ToggleRow("Steps", prefs.widgetSteps) { prefs.updateWidgetSteps(!prefs.widgetSteps) } }
            item { ToggleRow("Storage & memory", prefs.widgetStorage) { prefs.updateWidgetStorage(!prefs.widgetStorage) } }
            item { ToggleRow("Notes", prefs.widgetNotes) { prefs.updateWidgetNotes(!prefs.widgetNotes) } }
            flagRow(prefs, "Next up (calendar)", "wCalendar")
            flagRow(prefs, "Cassette player", "wCassette")
            flagRow(prefs, "Screen time", "wScreenTime")
            flagRow(prefs, "System health", "wHealth")
            flagRow(prefs, "Data meter", "wData")
            flagRow(prefs, "Sun & golden hour", "wSun")
            flagRow(prefs, "Water & habits", "wTracker")
            flagRow(prefs, "Hub shortcuts", "wHub")
            flagRow(prefs, "Daily quote", "wQuote")
            item {
                OptionRow(
                    label = "Clock card style",
                    options = listOf("Classic", "Stacked"),
                    selected = prefs.int("clockStyle", 0),
                    onSelect = { prefs.setInt("clockStyle", it) },
                )
            }
            item {
                Text(
                    "Open it with the gesture set to “Widget page” (swipe right by default).",
                    style = Type.caption,
                    color = Palette.Tertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item { Section("CUSTOM WIDGETS") }
            items(customWidgets, key = { "cw" + it.id }) { w ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = w.title.ifBlank { "Untitled widget" },
                        style = Type.body,
                        color = Palette.Label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    )
                    SmallAction("Edit") { builderEditing = w; builderOpen = true }
                    SmallAction("×") {
                        prefs.setStr("custom", CustomWidgetStore.toJson(customWidgets.filter { it.id != w.id }))
                    }
                }
            }
            item { ValueRow("Build a new widget", "Open") { builderEditing = null; builderOpen = true } }

            item { Section("HIDDEN APPS") }
            if (hiddenApps.isEmpty()) {
                item {
                    Text(
                        "None. Long-press an app in the drawer and choose Hide app.",
                        style = Type.body,
                        color = Palette.Tertiary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(hiddenApps, key = { "hid" + it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(onClick = { prefs.unhideApp(app.packageName) })
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = app.label,
                        style = Type.body,
                        color = Palette.Label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Unhide", style = Type.caption, color = Palette.Secondary)
                }
            }

            item { Section("ACCESS") }
            item {
                ValueRow("Usage access (screen time)", if (usageGranted) "Allowed" else "Allow") {
                    AppRepository.startSafely(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            item {
                ValueRow("Notification access (now playing)", if (notificationGranted) "Allowed" else "Allow") {
                    AppRepository.startSafely(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
            item {
                ValueRow("Lock service (double tap)", if (lockEnabled) "Allowed" else "Allow") {
                    AppRepository.startSafely(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            if (!lockEnabled) {
                item {
                    Text(
                        "On Android 13, sideloaded apps are blocked from accessibility until you open this app's info page, tap ⋮ (top right) and choose Allow restricted settings.",
                        style = Type.caption,
                        color = Palette.Tertiary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            item {
                ValueRow("Default home app", "Change") {
                    AppRepository.startSafely(context, Intent(Settings.ACTION_HOME_SETTINGS))
                }
            }

            item { Section("SEARCH") }
            item {
                OptionRow(
                    label = "Web search engine",
                    options = QuickActions.engines.map { it.first },
                    selected = prefs.searchEngine,
                    onSelect = { prefs.updateSearchEngine(it) },
                )
            }

            item { Section("HOME APPS  ${favorites.size}/${PreferencesManager.MAX_FAVORITES}") }
            if (favorites.isEmpty()) {
                item {
                    Text(
                        "Nothing on home yet. Add apps below.",
                        style = Type.body,
                        color = Palette.Tertiary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(favorites, key = { "f" + it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = app.label,
                        style = Type.body,
                        color = Palette.Label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp),
                    )
                    SmallAction("↑") { prefs.moveFavorite(app.packageName, -1) }
                    SmallAction("↓") { prefs.moveFavorite(app.packageName, 1) }
                    SmallAction("×") { prefs.removeFavorite(app.packageName) }
                }
            }

            item { Section("ADD APPS") }
            items(others, key = { "o" + it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(onClick = {
                            if (!prefs.addFavorite(app.packageName)) {
                                Toast.makeText(
                                    context,
                                    "Home is full (" + PreferencesManager.MAX_FAVORITES + " apps)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = app.label,
                        style = Type.body,
                        color = Palette.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("+", style = Type.body, color = Palette.Tertiary)
                }
            }
        }

        if (builderOpen) {
            CustomWidgetBuilder(
                state = state,
                editing = builderEditing,
                onDone = { builderOpen = false },
            )
        }

        if (pickerSlot >= 0) {
            val title = when (pickerSlot) {
                0 -> "Swipe left"
                1 -> "Swipe right"
                else -> "Double tap"
            }
            GesturePicker(
                title = title,
                state = state,
                onPick = { action ->
                    when (pickerSlot) {
                        0 -> prefs.updateSwipeLeft(action)
                        1 -> prefs.updateSwipeRight(action)
                        else -> prefs.updateDoubleTap(action)
                    }
                    pickerSlot = -1
                },
                onDismiss = { pickerSlot = -1 },
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = Type.section,
        color = Palette.Tertiary,
        modifier = Modifier.padding(top = 32.dp, bottom = 6.dp),
    )
}

@Composable
private fun OptionRow(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = Type.caption, color = Palette.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, name ->
                val on = index == selected
                Text(
                    text = name,
                    style = Type.body.copy(fontWeight = if (on) FontWeight.W600 else FontWeight.W400),
                    color = if (on) Palette.Label else Palette.Tertiary,
                    modifier = Modifier
                        .pressable(onClick = { onSelect(index) })
                        .padding(end = 12.dp, top = 8.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onToggle)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = Type.body, color = Palette.Label)
        Text(
            text = if (on) "On" else "Off",
            style = Type.body,
            color = if (on) Palette.Label else Palette.Tertiary,
        )
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = Type.body, color = Palette.Label, modifier = Modifier.weight(1f))
        Text(value, style = Type.body, color = Palette.Secondary)
    }
}

@Composable
private fun SmallAction(glyph: String, onClick: () -> Unit) {
    Text(
        text = glyph,
        style = Type.bodyStrong,
        color = Palette.Secondary,
        modifier = Modifier
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

private fun LazyListScope.flagRow(prefs: com.textlauncher.app.data.prefs.PreferencesManager, label: String, key: String) {
    item {
        ToggleRow(label, prefs.flag(key, true)) { prefs.setFlag(key, !prefs.flag(key, true)) }
    }
}
