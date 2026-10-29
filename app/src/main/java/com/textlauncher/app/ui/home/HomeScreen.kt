package com.textlauncher.app.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.textlauncher.app.LauncherState
import com.textlauncher.app.data.GestureAction
import com.textlauncher.app.data.ScreenTime
import com.textlauncher.app.data.model.AppInfo
import com.textlauncher.app.data.prefs.PreferencesManager
import com.textlauncher.app.data.repo.AppRepository
import com.textlauncher.app.ui.common.pressable
import com.textlauncher.app.ui.theme.Palette
import com.textlauncher.app.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val quotes = listOf(
    "One thing at a time.",
    "Be where your feet are.",
    "Nothing urgent.",
    "Make it simple.",
    "Slow is smooth.",
    "Look up.",
    "Enough is enough.",
    "Quiet is a feature.",
    "What matters right now?",
    "Do less, notice more.",
    "Presence over pings.",
    "Breathe.",
)

@Composable
fun HomeScreen(
    state: LauncherState,
    onOpenSettings: () -> Unit,
    onAppMenu: (AppInfo) -> Unit,
    onGesture: (GestureAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = state.prefs
    val haptic = LocalHapticFeedback.current
    val favorites = remember(state.apps, prefs.favorites) { state.favoriteApps() }


    val horizontal: Alignment.Horizontal = when (prefs.alignment) {
        PreferencesManager.ALIGN_CENTER -> Alignment.CenterHorizontally
        PreferencesManager.ALIGN_RIGHT -> Alignment.End
        else -> Alignment.Start
    }
    val textAlign = when (prefs.alignment) {
        PreferencesManager.ALIGN_CENTER -> TextAlign.Center
        PreferencesManager.ALIGN_RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Double-tap only exists here, on empty space, so it never delays a real tap.
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenSettings()
                    },
                    onDoubleTap = { onGesture(prefs.doubleTap) },
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = horizontal,
        ) {
            Widgets(state, horizontal, textAlign)

            val position = prefs.position
            if (position == PreferencesManager.POS_TOP) {
                Spacer(Modifier.height(40.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }

            if (favorites.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = horizontal,
                ) {
                    for (app in favorites) {
                        Text(
                            text = app.label,
                            style = Type.favorite(prefs.textSize),
                            color = Palette.Label,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable(
                                    onClick = { state.launch(app.packageName) },
                                    onLongClick = { onAppMenu(app) },
                                )
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }

            if (position == PreferencesManager.POS_CENTER) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Widgets(
    state: LauncherState,
    horizontal: Alignment.Horizontal,
    textAlign: TextAlign,
) {
    val context = LocalContext.current
    val prefs = state.prefs
    val now = rememberNow(state.resumeTick)

    val pattern = if (prefs.clock24) "HH:mm" else "h:mm"
    val time = remember(now, pattern) { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = horizontal,
    ) {
        Text(
            text = time,
            style = Type.clock,
            color = Palette.Label,
            textAlign = textAlign,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .pressable(onClick = {
                    AppRepository.startSafely(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
                }),
        )
        Text(
            text = date,
            style = Type.date,
            color = Palette.Secondary,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .pressable(onClick = {
                    AppRepository.startSafely(
                        context,
                        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                    )
                })
                .padding(vertical = 4.dp),
        )

        ScreenTimeWidget(state, now, textAlign)
        NowPlayingWidget(state, horizontal, textAlign)

        if (prefs.showQuote) {
            val day = remember(now) { Calendar.getInstance().get(Calendar.DAY_OF_YEAR) }
            Spacer(Modifier.height(18.dp))
            Text(
                text = quotes[day % quotes.size],
                style = Type.caption,
                color = Palette.Tertiary,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ScreenTimeWidget(state: LauncherState, now: Long, textAlign: TextAlign) {
    val context = LocalContext.current
    val enabled = state.prefs.showScreenTime
    var granted by remember { mutableStateOf(true) }
    var millis by remember { mutableStateOf(0L) }

    LaunchedEffect(enabled, now, state.resumeTick) {
        if (!enabled) return@LaunchedEffect
        granted = ScreenTime.hasAccess(context)
        if (granted) {
            millis = withContext(Dispatchers.Default) {
                try {
                    ScreenTime.todayMillis(context)
                } catch (e: Exception) {
                    0L
                }
            }
        }
    }

    if (!enabled) return
    Spacer(Modifier.height(18.dp))
    if (granted) {
        Text(
            text = ScreenTime.format(millis) + " on screen today",
            style = Type.caption,
            color = Palette.Secondary,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(
            text = "Allow usage access to show screen time",
            style = Type.caption,
            color = Palette.Tertiary,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .pressable(onClick = {
                    AppRepository.startSafely(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }),
        )
    }
}

@Composable
private fun NowPlayingWidget(
    state: LauncherState,
    horizontal: Alignment.Horizontal,
    textAlign: TextAlign,
) {
    if (!state.prefs.showNowPlaying) return
    val np = state.nowPlaying
    val title = np.title ?: return
    val artist = np.artist

    Spacer(Modifier.height(18.dp))
    Text(
        text = title,
        style = Type.bodyStrong,
        color = Palette.Label,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
    if (artist != null) {
        Text(
            text = artist,
            style = Type.caption,
            color = Palette.Secondary,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, horizontal),
    ) {
        Text(
            text = "Prev",
            style = Type.caption,
            color = Palette.Secondary,
            modifier = Modifier
                .pressable(onClick = { np.previous() })
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Text(
            text = if (np.playing) "Pause" else "Play",
            style = Type.caption,
            color = Palette.Label,
            modifier = Modifier
                .pressable(onClick = { np.togglePlayPause() })
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Text(
            text = "Next",
            style = Type.caption,
            color = Palette.Secondary,
            modifier = Modifier
                .pressable(onClick = { np.next() })
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

/** Current time in millis, refreshed every minute and whenever the app resumes. */
@Composable
private fun rememberNow(resumeTick: Int): Long {
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
