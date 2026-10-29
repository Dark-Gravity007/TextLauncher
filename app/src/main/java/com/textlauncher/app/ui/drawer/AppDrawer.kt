package com.textlauncher.app.ui.drawer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.textlauncher.app.LauncherState
import com.textlauncher.app.data.model.AppInfo
import com.textlauncher.app.data.repo.AppRepository
import com.textlauncher.app.search.QuickAction
import com.textlauncher.app.search.QuickActions
import com.textlauncher.app.search.SearchIndex
import com.textlauncher.app.ui.common.highlightedLabel
import com.textlauncher.app.ui.common.pressable
import com.textlauncher.app.ui.theme.Palette
import com.textlauncher.app.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SEARCH_DEBOUNCE_MS = 40L

/**
 * Shared between the A–Z strip (which drives it) and the app rows (which react to it).
 * [fingerY] is the finger position in root coordinates, smoothed by a spring; [strength] fades the effect in and out.
 */
class IndexWave {
    val fingerY = Animatable(0f)
    val strength = Animatable(0f)
}

/** Smooth bell: 1 at the finger, 0 at distance 1 and beyond. */
private fun bump(d: Float): Float = if (d >= 1f) 0f else 0.5f * (1f + cos(PI.toFloat() * d))

private class ListRow(val key: String, val letter: Char?, val app: AppInfo?)

fun hideKeyboard(view: android.view.View) {
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}

/**
 * Full-screen text list of every app, A–Z, with a Spotlight-style search field on top.
 * [active] is true once the drawer has been committed open (it then grabs focus and raises the keyboard).
 */
@Composable
fun AppDrawer(
    state: LauncherState,
    active: Boolean,
    nestedScroll: NestedScrollConnection,
    scrimAlpha: Float,
    onLaunch: (AppInfo) -> Unit,
    onAppMenu: (AppInfo) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val prefs = state.prefs
    val hidden = prefs.hiddenApps()
    val apps = remember(state.apps, hidden) { state.apps.filter { it.packageName !in hidden } }
    val wave = remember { IndexWave() }

    var query by remember { mutableStateOf("") }
    var bubbleLetter by remember { mutableStateOf<Char?>(null) }
    val trimmed = query.trim()

    // Labels are prepared once per app list; recent queries are cached inside the index.
    val index = remember(apps) { SearchIndex(apps) { it.label } }
    var hits by remember { mutableStateOf<List<SearchIndex.Hit<AppInfo>>>(emptyList()) }

    // Filtering runs on a background thread. A new keystroke cancels the previous search, and the
    // short wait (40 ms) only collapses very fast typing into one search.
    LaunchedEffect(trimmed, index) {
        if (trimmed.isEmpty()) {
            hits = emptyList()
        } else {
            delay(SEARCH_DEBOUNCE_MS)
            hits = withContext(Dispatchers.Default) { index.search(trimmed) }
        }
    }

    val actions: List<QuickAction> = remember(trimmed, prefs.searchEngine) {
        QuickActions.forQuery(trimmed, prefs.searchEngine) { Uri.encode(it) }
    }

    val rows: List<ListRow> = remember(apps) {
        val out = ArrayList<ListRow>()
        val order = ('A'..'Z').toList() + '#'
        for (letter in order) {
            val group = apps.filter { it.letter == letter }
            if (group.isEmpty()) continue
            out.add(ListRow("h$letter", letter, null))
            for (app in group) out.add(ListRow("a" + app.packageName, null, app))
        }
        out
    }
    val letters: List<Char> = remember(rows) { rows.mapNotNull { it.letter } }

    LaunchedEffect(active) {
        if (active) {
            delay(40)
            try {
                focusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // text field not attached yet; the user can tap it
            }
        } else {
            focusManager.clearFocus()
            hideKeyboard(view)
        }
    }

    // Scrolling the list means "I'm browsing": put the keyboard away so the A–Z strip is usable.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            focusManager.clearFocus()
            hideKeyboard(view)
        }
    }

    fun perform(action: QuickAction) {
        when (action) {
            is QuickAction.Answer -> copy(context, action.result)
            is QuickAction.Conversion -> copy(context, action.text.substringAfter("= "))
            is QuickAction.OpenUrl -> {
                AppRepository.openUrl(context, action.url)
                onDone()
            }
            is QuickAction.WebSearch -> {
                AppRepository.openUrl(context, action.url)
                onDone()
            }
        }
    }

    fun submit() {
        val special = actions.firstOrNull { it !is QuickAction.WebSearch }
        // Search synchronously here (cached, so cheap) so Enter never acts on stale results.
        val firstApp = if (trimmed.isEmpty()) null else index.search(trimmed).firstOrNull()?.item
        when {
            special != null -> perform(special)
            firstApp != null -> onLaunch(firstApp)
            actions.isNotEmpty() -> perform(actions.last())
        }
    }

    // Only changes when the keyboard appears or disappears, not on every frame of its animation.
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val imeVisible by remember { derivedStateOf { imeInsets.getBottom(density) > 0 } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Search field: a single line of text over a hairline, no box.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 28.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = Type.search.copy(color = Palette.Label),
                    cursorBrush = SolidColor(Palette.Label),
                    keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box(Modifier.padding(vertical = 8.dp)) {
                            if (query.isEmpty()) {
                                Text("Search", style = Type.search, color = Palette.Tertiary)
                            }
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Text(
                        text = "Clear",
                        style = Type.caption,
                        color = Palette.Secondary,
                        modifier = Modifier
                            .pressable(onClick = { query = "" })
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Palette.Separator)
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScroll),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 28.dp,
                        end = 52.dp,
                        top = 8.dp,
                        bottom = 24.dp,
                    ),
                ) {
                    if (trimmed.isEmpty()) {
                        items(rows, key = { it.key }) { row ->
                            if (row.letter != null) {
                                Text(
                                    text = row.letter.toString(),
                                    style = Type.section,
                                    color = Palette.Tertiary,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
                                )
                            } else if (row.app != null) {
                                AppRow(row.app, onLaunch, onAppMenu, null, wave)
                            }
                        }
                    } else {
                        items(actions) { action ->
                            ActionRow(action) { perform(action) }
                        }
                        if (hits.isNotEmpty()) {
                            item {
                                Text(
                                    text = "APPS",
                                    style = Type.section,
                                    color = Palette.Tertiary,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
                                )
                            }
                            items(hits, key = { "m" + it.item.packageName }) { hit ->
                                AppRow(hit.item, onLaunch, onAppMenu, hit.indices)
                            }
                        }
                    }
                }

                if (trimmed.isEmpty() && !imeVisible && letters.size > 1) {
                    AlphabetIndex(
                        letters = letters,
                        wave = wave,
                        onSelect = { letter ->
                            bubbleLetter = letter
                            val index = rows.indexOfFirst { it.letter == letter }
                            if (index >= 0) scope.launch { listState.scrollToItem(index) }
                        },
                        onEnd = { bubbleLetter = null },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(36.dp)
                            .padding(vertical = 8.dp),
                    )
                }

                val bubble = bubbleLetter
                if (bubble != null) {
                    Text(
                        text = bubble.toString(),
                        style = Type.bigLetter,
                        color = Palette.Label,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("result", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

@Composable
private fun AppRow(
    app: AppInfo,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit,
    indices: IntArray? = null,
    wave: IndexWave? = null,
) {
    // Rows near the finger on the A–Z strip swell and lean toward it, like a bubble travelling down the list.
    val centerY = remember { floatArrayOf(0f) }
    val waveModifier = if (wave == null) {
        Modifier
    } else {
        Modifier
            .onGloballyPositioned { centerY[0] = it.positionInRoot().y + it.size.height / 2f }
            .graphicsLayer {
                val s = wave.strength.value
                if (s > 0.001f) {
                    val d = abs(centerY[0] - wave.fingerY.value) / 120.dp.toPx()
                    val b = bump(d) * s
                    val scale = 1f + 0.18f * b
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationX = 20.dp.toPx() * b
                }
            }
    }
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(waveModifier)
        .pressable(onClick = { onLaunch(app) }, onLongClick = { onMenu(app) })
        .padding(vertical = 10.dp)
    if (indices == null) {
        Text(
            text = app.label,
            style = Type.drawerItem,
            color = Palette.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = rowModifier,
        )
    } else {
        val annotated = remember(app.label, indices) { highlightedLabel(app.label, indices) }
        Text(
            text = annotated,
            style = Type.drawerItem,
            color = Palette.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = rowModifier,
        )
    }
}

@Composable
private fun ActionRow(action: QuickAction, onClick: () -> Unit) {
    val isAnswer = action is QuickAction.Answer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = action.title,
            style = if (isAnswer) Type.answer else Type.drawerItem,
            color = Palette.Label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val sub = action.subtitle
        if (sub != null) {
            Text(
                text = sub,
                style = Type.caption,
                color = Palette.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/**
 * A–Z strip on the right edge. Touch and slide to jump. While you drag, the letters near your finger bulge out
 * to the left along a smooth arc and grow, following the finger on a spring (a bit livelier than the UI default,
 * since this is a playful moment), and the app names next to it swell too.
 */
@Composable
private fun AlphabetIndex(
    letters: List<Char>,
    wave: IndexWave,
    onSelect: (Char) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableStateOf(1) }
    var topInRoot by remember { mutableStateOf(0f) }
    var last by remember { mutableStateOf<Char?>(null) }

    fun pick(y: Float, snap: Boolean) {
        if (letters.isEmpty()) return
        val target = topInRoot + y
        scope.launch {
            if (snap) {
                wave.fingerY.snapTo(target)
            } else {
                wave.fingerY.animateTo(target, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium))
            }
        }
        val i = (y / heightPx * letters.size).toInt().coerceIn(0, letters.size - 1)
        val c = letters[i]
        if (c != last) {
            last = c
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(c)
        }
    }

    fun begin(y: Float) {
        scope.launch { wave.strength.animateTo(1f, spring(dampingRatio = 1f, stiffness = 500f)) }
        pick(y, true)
    }

    fun finish() {
        last = null
        scope.launch { wave.strength.animateTo(0f, spring(dampingRatio = 1f, stiffness = 380f)) }
        onEnd()
    }

    Column(
        modifier = modifier
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .onGloballyPositioned { topInRoot = it.positionInRoot().y }
            .pointerInput(letters) {
                detectTapGestures(
                    onPress = { offset ->
                        begin(offset.y)
                        tryAwaitRelease()
                        finish()
                    }
                )
            }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> begin(offset.y) },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        pick(change.position.y, false)
                    },
                )
            },
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEachIndexed { index, letter ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val s = wave.strength.value
                        if (s > 0.001f) {
                            val cell = heightPx.toFloat() / letters.size
                            val cy = topInRoot + (index + 0.5f) * cell
                            val b = bump(abs(cy - wave.fingerY.value) / 84.dp.toPx()) * s
                            translationX = -46.dp.toPx() * b
                            val scale = 1f + 1.2f * b
                            scaleX = scale
                            scaleY = scale
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter.toString(),
                    style = Type.index,
                    color = Palette.Secondary,
                )
            }
        }
    }
}
