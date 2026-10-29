package com.textlauncher.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.textlauncher.app.data.model.AppInfo
import com.textlauncher.app.ui.theme.Palette
import com.textlauncher.app.ui.theme.Type

/** Long-press menu for an app: a dimmed scrim and a short list of text actions rising from the bottom. */
@Composable
fun AppMenu(
    app: AppInfo,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onHide: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(durationMillis = 180)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(Palette.Scrim)
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding()
                .graphicsLayer { translationY = (1f - appear.value) * 40f }
                .padding(horizontal = 28.dp, vertical = 32.dp)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Text(
                text = app.label,
                style = Type.title,
                color = Palette.Label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            MenuItem(if (isFavorite) "Remove from home" else "Add to home") {
                onToggleFavorite()
                onDismiss()
            }
            MenuItem("Hide app") {
                onHide()
                onDismiss()
            }
            MenuItem("App info") {
                onAppInfo()
                onDismiss()
            }
            MenuItem("Uninstall") {
                onUninstall()
                onDismiss()
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = Type.drawerItem,
        color = Palette.Label,
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}
