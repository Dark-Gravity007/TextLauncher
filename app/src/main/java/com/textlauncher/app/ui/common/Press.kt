package com.textlauncher.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Tap target with instant feedback: the text dims the moment the finger lands (not on release)
 * and eases back after. Long-press gives a light haptic.
 *
 * Written as a composable modifier factory instead of `composed {}`, which is noticeably cheaper
 * when it is used on every row of a long list.
 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val clickState = rememberUpdatedState(onClick)
    val longState = rememberUpdatedState(onLongClick)
    val hasLong = onLongClick != null
    var pressed by remember { mutableStateOf(false) }
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.3f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 0 else 200),
        label = "press"
    )
    return this
        .graphicsLayer { alpha = pressAlpha }
        .pointerInput(hasLong) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onLongPress = if (hasLong) {
                    { _: Offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        longState.value?.invoke()
                    }
                } else {
                    null
                },
                onTap = { clickState.value() },
            )
        }
}
