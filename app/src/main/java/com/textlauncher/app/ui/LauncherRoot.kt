package com.textlauncher.app.ui

import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.textlauncher.app.LauncherState
import com.textlauncher.app.R
import com.textlauncher.app.data.GestureAction
import com.textlauncher.app.data.model.AppInfo
import com.textlauncher.app.data.repo.AppRepository
import com.textlauncher.app.ui.common.AppMenu
import com.textlauncher.app.ui.drawer.AppDrawer
import com.textlauncher.app.ui.drawer.hideKeyboard
import com.textlauncher.app.ui.home.HomeScreen
import com.textlauncher.app.ui.settings.SettingsScreen
import com.textlauncher.app.ui.widgets.WidgetPage
import com.textlauncher.app.system.LockAccessibilityService
import com.textlauncher.app.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Owns the layers (home, drawer, widget page, settings) and the gestures between them.
 *
 * Motion follows the apple-design notes: the drawer tracks the finger 1:1, release velocity is handed to a
 * critically damped spring, the landing point is projected from that velocity, and any gesture can be
 * grabbed again mid-flight because the position lives in one Animatable.
 */
@Composable
fun LauncherRoot(state: LauncherState) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val spec: AnimationSpec<Float> = remember(reduceMotion) {
        if (reduceMotion) snap<Float>() else spring<Float>(dampingRatio = 1f, stiffness = 380f)
    }

    // 0 = drawer closed, 1 = fully open.
    val progress = remember { Animatable(0f) }
    var drawerOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var heightPx by remember { mutableStateOf(1f) }
    var dragTotal by remember { mutableStateOf(0f) }
    val drawerVisible by remember { derivedStateOf { progress.value > 0.001f } }
    val notifyThresholdPx = with(density) { 90.dp.toPx() }

    // Widget page: 0 = hidden, 1 = fully shown. It lives to the left (-1) or right (+1) of home.
    val pageProgress = remember { Animatable(0f) }
    var pageOpen by remember { mutableStateOf(false) }
    var pageSide by remember { mutableStateOf(-1) }
    var widthPx by remember { mutableStateOf(1f) }
    var hTotal by remember { mutableStateOf(0f) }
    var pageStart by remember { mutableStateOf(0f) }
    val nudge = remember { Animatable(0f) }
    val pageVisible by remember { derivedStateOf { pageProgress.value > 0.001f } }
    val swipeCommitPx = with(density) { 72.dp.toPx() }
    val flickPx = with(density) { 700.dp.toPx() }

    fun settle(open: Boolean, velocity: Float) {
        drawerOpen = open
        if (!open) {
            focusManager.clearFocus()
            hideKeyboard(view)
        }
        scope.launch { progress.animateTo(if (open) 1f else 0f, spec, velocity) }
    }

    fun closeNow() {
        drawerOpen = false
        scope.launch { progress.snapTo(0f) }
    }

    fun settlePage(open: Boolean, velocity: Float) {
        pageOpen = open
        if (!open) {
            focusManager.clearFocus()
            hideKeyboard(view)
        }
        scope.launch { pageProgress.animateTo(if (open) 1f else 0f, spec, velocity) }
    }

    fun openPage(side: Int) {
        pageSide = side
        settlePage(true, 0f)
    }

    /** [slide]: 1 = the app slides in from the right (swipe left), -1 = from the left (swipe right), 0 = default. */
    fun perform(action: GestureAction, slide: Int = 0) {
        when (action) {
            GestureAction.None -> Unit
            GestureAction.Drawer -> settle(true, 0f)
            GestureAction.Settings -> showSettings = true
            GestureAction.Widgets -> openPage(if (slide == 1) 1 else -1)
            GestureAction.Notifications -> expandNotifications(context)
            GestureAction.Flashlight -> {
                if (!state.torch.toggle()) {
                    Toast.makeText(context, "No flashlight available", Toast.LENGTH_SHORT).show()
                }
            }
            GestureAction.LockScreen -> {
                if (!LockAccessibilityService.lock()) {
                    val message = if (!LockAccessibilityService.isSupported()) {
                        "Locking the screen needs Android 9 or newer"
                    } else {
                        "Turn on the lock service in Settings → Access"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
            is GestureAction.App -> {
                val enter = when (slide) {
                    1 -> R.anim.slide_in_from_right
                    -1 -> R.anim.slide_in_from_left
                    else -> 0
                }
                val exit = when (slide) {
                    1 -> R.anim.slide_out_to_left
                    -1 -> R.anim.slide_out_to_right
                    else -> 0
                }
                state.launch(action.packageName, enter, exit)
            }
        }
    }

    // Horizontal swipes on home. If the swipe is mapped to the widget page it follows the finger 1:1;
    // otherwise the content nudges with resistance and the action fires on release.
    val horizontalState = rememberDraggableState { delta ->
        hTotal += delta
        if (pageStart > 0f) {
            scope.launch { pageProgress.snapTo((pageStart - pageSide * hTotal / widthPx).coerceIn(0f, 1f)) }
        } else {
            val side = if (hTotal > 0f) -1 else 1
            val action = if (side == -1) state.prefs.swipeRight else state.prefs.swipeLeft
            if (action == GestureAction.Widgets) {
                pageSide = side
                scope.launch { pageProgress.snapTo((-side * hTotal / widthPx).coerceIn(0f, 1f)) }
            } else {
                scope.launch {
                    if (pageProgress.value > 0f) pageProgress.snapTo(0f)
                    nudge.snapTo(hTotal)
                }
            }
        }
    }

    // Swipe up anywhere on home to pull the drawer up; swipe down on the drawer's header to put it away.
    val dragState = rememberDraggableState { delta ->
        dragTotal += delta
        if (drawerOpen || progress.value > 0f || delta < 0f) {
            scope.launch { progress.snapTo((progress.value - delta / heightPx).coerceIn(0f, 1f)) }
        }
    }

    // Pulling the app list down while it is already at the top moves the drawer instead of overscrolling.
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y < 0f && progress.value < 1f) {
                    scope.launch { progress.snapTo((progress.value - available.y / heightPx).coerceIn(0f, 1f)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y > 0f) {
                    scope.launch { progress.snapTo((progress.value - available.y / heightPx).coerceIn(0f, 1f)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (progress.value < 1f) {
                    val v = -available.y / heightPx
                    settle(progress.value + 0.5f * v > 0.5f, v)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            menuApp != null -> menuApp = null
            showSettings -> showSettings = false
            pageOpen -> settlePage(false, 0f)
            drawerOpen -> settle(false, 0f)
            else -> Unit // already home
        }
    }

    LaunchedEffect(state.homeTick) {
        if (state.homeTick > 0) {
            menuApp = null
            showSettings = false
            if (pageOpen || pageProgress.value > 0f) settlePage(false, 0f)
            if (drawerOpen || progress.value > 0f) settle(false, 0f)
        }
    }

    LaunchedEffect(Unit) { state.refreshApps() }

    // Readability scrim: strength follows the user's setting; "auto" goes stronger on bright wallpapers.
    val baseDim = when (state.prefs.dim) {
        1 -> 0f
        2 -> 0.22f
        3 -> 0.38f
        4 -> 0.55f
        else -> if (state.wallpaperLight) 0.50f else 0.30f
    }
    val homeScrim = remember(baseDim) {
        Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = (baseDim * 1.25f).coerceAtMost(0.9f)),
                Color.Black.copy(alpha = baseDim * 0.7f),
                Color.Black.copy(alpha = baseDim * 0.7f),
                Color.Black.copy(alpha = (baseDim * 1.35f).coerceAtMost(0.9f)),
            )
        )
    }
    val drawerScrim = (baseDim + 0.32f).coerceIn(0.55f, 0.9f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                heightPx = it.height.toFloat().coerceAtLeast(1f)
                widthPx = it.width.toFloat().coerceAtLeast(1f)
            }
            .draggable(
                state = horizontalState,
                orientation = Orientation.Horizontal,
                enabled = !showSettings && menuApp == null && !drawerVisible,
                onDragStarted = {
                    hTotal = 0f
                    pageStart = pageProgress.value
                    pageProgress.stop()
                    nudge.stop()
                },
                onDragStopped = { velocity ->
                    if (pageStart > 0f || pageProgress.value > 0f) {
                        val v = -pageSide * velocity / widthPx
                        settlePage(pageProgress.value + 0.5f * v > 0.5f, v)
                    } else {
                        val side = if (hTotal > 0f) -1 else 1
                        val action = if (side == -1) state.prefs.swipeRight else state.prefs.swipeLeft
                        val committed = abs(hTotal) > swipeCommitPx || abs(velocity) > flickPx
                        scope.launch { nudge.animateTo(0f, spec) }
                        if (committed && action != GestureAction.None) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (action == GestureAction.Widgets) {
                                openPage(side)
                            } else {
                                // Swipe left (finger moves left) brings the next screen in from the right, and vice versa.
                                perform(action, if (side == 1) 1 else -1)
                            }
                        }
                    }
                },
            )
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = !showSettings && menuApp == null && !pageVisible,
                onDragStarted = {
                    dragTotal = 0f
                    progress.stop()
                    if (drawerOpen) {
                        focusManager.clearFocus()
                        hideKeyboard(view)
                    }
                },
                onDragStopped = { velocity ->
                    if (progress.value > 0f) {
                        val v = -velocity / heightPx
                        settle(progress.value + 0.5f * v > 0.5f, v)
                    } else if (dragTotal > notifyThresholdPx) {
                        expandNotifications(context)
                    }
                },
            )
    ) {
        Box(Modifier.fillMaxSize().background(homeScrim))

        HomeScreen(
            state = state,
            onOpenSettings = { showSettings = true },
            onAppMenu = { menuApp = it },
            onGesture = { perform(it) },
            modifier = Modifier.graphicsLayer {
                val p = progress.value
                val q = pageProgress.value
                alpha = (1f - 0.65f * p) * (1f - 0.7f * q)
                val s = 1f - 0.06f * p
                scaleX = s
                scaleY = s
                // Home drifts away from the incoming page, like a parallax.
                translationX = nudge.value * 0.22f - pageSide * q * size.width * 0.22f
            },
        )

        if (pageVisible) {
            WidgetPage(
                state = state,
                scrimAlpha = drawerScrim,
                modifier = Modifier.graphicsLayer {
                    translationX = pageSide * (1f - pageProgress.value) * widthPx
                },
            )
        }

        if (drawerVisible) {
            AppDrawer(
                state = state,
                active = drawerOpen,
                nestedScroll = nested,
                scrimAlpha = drawerScrim,
                onLaunch = { app ->
                    state.launch(app.packageName)
                    closeNow()
                },
                onAppMenu = { menuApp = it },
                onDone = { closeNow() },
                modifier = Modifier.graphicsLayer {
                    translationY = (1f - progress.value) * heightPx
                },
            )
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = if (reduceMotion) fadeIn(tween(0)) else
                slideInVertically(animationSpec = spring(dampingRatio = 1f, stiffness = 380f)) { it } + fadeIn(tween(160)),
            exit = if (reduceMotion) fadeOut(tween(0)) else
                slideOutVertically(animationSpec = spring(dampingRatio = 1f, stiffness = 380f)) { it } + fadeOut(tween(160)),
        ) {
            SettingsScreen(state = state, onClose = { showSettings = false })
        }

        val app = menuApp
        if (app != null) {
            val isFavorite = app.packageName in state.prefs.favorites
            AppMenu(
                app = app,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    if (isFavorite) {
                        state.prefs.removeFavorite(app.packageName)
                    } else if (!state.prefs.addFavorite(app.packageName)) {
                        android.widget.Toast.makeText(context, "Home is full (8 apps)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onHide = {
                    state.prefs.hideApp(app.packageName)
                    Toast.makeText(context, app.label + " hidden. Unhide it in Settings.", Toast.LENGTH_SHORT).show()
                },
                onAppInfo = { AppRepository.openAppInfo(context, app.packageName) },
                onUninstall = { AppRepository.uninstall(context, app.packageName) },
                onDismiss = { menuApp = null },
            )
        }
    }
}

/** Opens the notification shade. Uses a long-standing system hook; silently does nothing if a device blocks it. */
private fun expandNotifications(context: Context) {
    try {
        val service = context.getSystemService("statusbar") ?: return
        service.javaClass.getMethod("expandNotificationsPanel").invoke(service)
    } catch (e: Exception) {
        // not available on this device
    }
}
