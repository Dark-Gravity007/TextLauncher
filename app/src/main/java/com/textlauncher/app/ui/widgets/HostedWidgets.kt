package com.textlauncher.app.ui.widgets

import android.appwidget.AppWidgetHostView
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.textlauncher.app.ui.common.pressable
import com.textlauncher.app.ui.theme.Palette
import com.textlauncher.app.ui.theme.Type
import com.textlauncher.app.widgets.WidgetController

/** One widget from another app. [level]: 0 = compact, 1 = normal, 2 = tall. */
@Composable
internal fun HostedWidgetCard(controller: WidgetController, id: Int, level: Int) {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val info = remember(id) { controller.info(id) } ?: return

    // The widget's own preferred height, turned into dp, then scaled by the chosen size.
    val naturalDp = remember(info) { (info.minHeight / density.density).coerceIn(80f, 360f) }
    val targetDp = when (level) {
        0 -> minOf(naturalDp, 110f)
        2 -> minOf(naturalDp * 1.8f, 520f)
        else -> naturalDp
    }
    val animatedHeight by animateDpAsState(
        targetValue = targetDp.dp,
        animationSpec = spring(dampingRatio = 1f, stiffness = 380f),
        label = "widgetHeight",
    )
    val widthDp = screenWidthDp - 40

    AndroidView(
        factory = { context -> controller.createView(id) ?: View(context) },
        update = { view ->
            if (view is AppWidgetHostView) {
                // Tell the widget how much room it has so it can pick a suitable layout.
                view.updateAppWidgetSize(null, widthDp, targetDp.toInt(), widthDp, targetDp.toInt())
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(animatedHeight)
            .clip(RoundedCornerShape(26.dp)),
    )
}

/** Full-screen list of every widget the phone's apps offer. Tapping one starts the add flow. */
@Composable
internal fun WidgetPicker(controller: WidgetController, onDismiss: () -> Unit) {
    BackHandler(enabled = true) { onDismiss() }
    val providers = remember { controller.providers() }

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
                    Text("Add a widget", style = Type.title, color = Palette.Label, modifier = Modifier.weight(1f))
                    Text(
                        "Cancel",
                        style = Type.bodyStrong,
                        color = Palette.Secondary,
                        modifier = Modifier
                            .pressable(onClick = onDismiss)
                            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
                Text(
                    "Widgets are drawn by their own apps, so they keep their own look.",
                    style = Type.caption,
                    color = Palette.Tertiary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
            if (providers.isEmpty()) {
                item {
                    Text(
                        "No widgets found on this phone.",
                        style = Type.body,
                        color = Palette.Secondary,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            items(providers, key = { it.provider.flattenToString() }) { info ->
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(onClick = {
                            controller.add(info)
                            onDismiss()
                        })
                        .padding(vertical = 11.dp),
                ) {
                    Text(
                        text = controller.label(info),
                        style = Type.drawerItem,
                        color = Palette.Label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = controller.appLabel(info),
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
