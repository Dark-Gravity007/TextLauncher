package com.textlauncher.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.textlauncher.app.LauncherState
import com.textlauncher.app.data.GestureAction
import com.textlauncher.app.ui.common.pressable
import com.textlauncher.app.ui.theme.Palette
import com.textlauncher.app.ui.theme.Type

/** Full-screen list: built-in actions first, then every app. Tapping a row picks it. */
@Composable
fun GesturePicker(
    title: String,
    state: LauncherState,
    onPick: (GestureAction) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }

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
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = Type.title, color = Palette.Label, modifier = Modifier.weight(1f))
                    Text(
                        "Cancel",
                        style = Type.bodyStrong,
                        color = Palette.Secondary,
                        modifier = Modifier
                            .pressable(onClick = onDismiss)
                            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
            items(GestureAction.standard, key = { "s" + it.first }) { entry ->
                Text(
                    text = entry.first,
                    style = Type.drawerItem,
                    color = Palette.Label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(onClick = { onPick(entry.second) })
                        .padding(vertical = 11.dp),
                )
            }
            item {
                Text(
                    text = "OPEN AN APP",
                    style = Type.section,
                    color = Palette.Tertiary,
                    modifier = Modifier.padding(top = 28.dp, bottom = 6.dp),
                )
            }
            items(state.apps, key = { "a" + it.packageName }) { app ->
                Text(
                    text = app.label,
                    style = Type.drawerItem,
                    color = Palette.Label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(onClick = { onPick(GestureAction.App(app.packageName)) })
                        .padding(vertical = 11.dp),
                )
            }
        }
    }
}
