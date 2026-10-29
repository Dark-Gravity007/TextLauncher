package com.textlauncher.app.data.prefs

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.textlauncher.app.data.GestureAction

/**
 * All launcher settings. SharedPreferences (synchronous, so the home screen is right on the first frame)
 * mirrored into Compose state so the UI updates the moment a value changes.
 */
class PreferencesManager(context: Context) {

    companion object {
        const val MAX_FAVORITES = 8
        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2
        const val POS_TOP = 0
        const val POS_CENTER = 1
        const val POS_BOTTOM = 2
    }

    private val sp = context.getSharedPreferences("textlauncher", Context.MODE_PRIVATE)

    // Generic keyed values that Compose can observe. Used by the many small widget settings.
    private val flags = mutableStateMapOf<String, Boolean>()
    private val ints = mutableStateMapOf<String, Int>()
    private val strings = mutableStateMapOf<String, String>()

    fun flag(key: String, default: Boolean): Boolean = flags[key] ?: sp.getBoolean(key, default)
    fun setFlag(key: String, v: Boolean) { flags[key] = v; sp.edit().putBoolean(key, v).apply() }
    fun int(key: String, default: Int): Int = ints[key] ?: sp.getInt(key, default)
    fun setInt(key: String, v: Int) { ints[key] = v; sp.edit().putInt(key, v).apply() }
    fun str(key: String, default: String): String = strings[key] ?: (sp.getString(key, default) ?: default)
    fun setStr(key: String, v: String) { strings[key] = v; sp.edit().putString(key, v).apply() }

    /** Apps hidden from the drawer and search. They can still be launched from home, and unhidden in Settings. */
    fun hiddenApps(): Set<String> = str("hidden", "").split(",").filter { it.isNotEmpty() }.toSet()
    fun hideApp(pkg: String) = setStr("hidden", (hiddenApps() + pkg).joinToString(","))
    fun unhideApp(pkg: String) = setStr("hidden", (hiddenApps() - pkg).joinToString(","))

    var favorites by mutableStateOf(
        (sp.getString("favorites", "") ?: "").split(",").filter { it.isNotEmpty() }
    )
        private set

    var alignment by mutableStateOf(sp.getInt("alignment", ALIGN_LEFT))
        private set
    var position by mutableStateOf(sp.getInt("position", POS_CENTER))
        private set
    var textSize by mutableStateOf(sp.getInt("textSize", 1)) // 0 small, 1 medium, 2 large
        private set
    var clock24 by mutableStateOf(sp.getBoolean("clock24", DateFormat.is24HourFormat(context)))
        private set
    var showScreenTime by mutableStateOf(sp.getBoolean("showScreenTime", true))
        private set
    var showNowPlaying by mutableStateOf(sp.getBoolean("showNowPlaying", true))
        private set
    var showQuote by mutableStateOf(sp.getBoolean("showQuote", false))
        private set
    var searchEngine by mutableStateOf(sp.getInt("searchEngine", 0))
        private set
    var seeded by mutableStateOf(sp.getBoolean("seeded", false))
        private set

    /** 0 = auto (stronger on bright wallpapers), 1 = off, 2 = light, 3 = medium, 4 = strong */
    var dim by mutableStateOf(sp.getInt("dim", 0))
        private set

    var widgetClock by mutableStateOf(sp.getBoolean("wClock", true))
        private set
    var widgetBattery by mutableStateOf(sp.getBoolean("wBattery", true))
        private set
    var widgetSteps by mutableStateOf(sp.getBoolean("wSteps", true))
        private set
    var widgetStorage by mutableStateOf(sp.getBoolean("wStorage", true))
        private set
    var widgetNotes by mutableStateOf(sp.getBoolean("wNotes", true))
        private set
    var stepGoal by mutableStateOf(sp.getInt("stepGoal", 8000))
        private set
    var note by mutableStateOf(sp.getString("note", "") ?: "")
        private set
    var widgetsMigrated by mutableStateOf(sp.getBoolean("widgetsMigrated", false))
        private set

    var swipeLeft by mutableStateOf(GestureAction.decode(sp.getString("swipeLeft", "none")))
        private set
    var swipeRight by mutableStateOf(GestureAction.decode(sp.getString("swipeRight", "none")))
        private set
    var doubleTap by mutableStateOf(GestureAction.decode(sp.getString("doubleTap", "lock")))
        private set
    var gesturesSeeded by mutableStateOf(sp.getBoolean("gesturesSeeded", false))
        private set

    fun updateFavorites(list: List<String>) {
        favorites = list
        sp.edit().putString("favorites", list.joinToString(",")).apply()
    }

    fun addFavorite(pkg: String): Boolean {
        if (pkg in favorites) return true
        if (favorites.size >= MAX_FAVORITES) return false
        updateFavorites(favorites + pkg)
        return true
    }

    fun removeFavorite(pkg: String) = updateFavorites(favorites.filter { it != pkg })

    fun moveFavorite(pkg: String, delta: Int) {
        val from = favorites.indexOf(pkg)
        val to = from + delta
        if (from < 0 || to < 0 || to >= favorites.size) return
        val list = favorites.toMutableList()
        list.removeAt(from)
        list.add(to, pkg)
        updateFavorites(list)
    }

    fun markSeeded() {
        seeded = true
        sp.edit().putBoolean("seeded", true).apply()
    }

    fun updateAlignment(v: Int) { alignment = v; sp.edit().putInt("alignment", v).apply() }
    fun updatePosition(v: Int) { position = v; sp.edit().putInt("position", v).apply() }
    fun updateTextSize(v: Int) { textSize = v; sp.edit().putInt("textSize", v).apply() }
    fun updateClock24(v: Boolean) { clock24 = v; sp.edit().putBoolean("clock24", v).apply() }
    fun updateShowScreenTime(v: Boolean) { showScreenTime = v; sp.edit().putBoolean("showScreenTime", v).apply() }
    fun updateShowNowPlaying(v: Boolean) { showNowPlaying = v; sp.edit().putBoolean("showNowPlaying", v).apply() }
    fun updateShowQuote(v: Boolean) { showQuote = v; sp.edit().putBoolean("showQuote", v).apply() }
    fun updateSearchEngine(v: Int) { searchEngine = v; sp.edit().putInt("searchEngine", v).apply() }

    fun updateDim(v: Int) { dim = v; sp.edit().putInt("dim", v).apply() }
    fun updateWidgetClock(v: Boolean) { widgetClock = v; sp.edit().putBoolean("wClock", v).apply() }
    fun updateWidgetBattery(v: Boolean) { widgetBattery = v; sp.edit().putBoolean("wBattery", v).apply() }
    fun updateWidgetSteps(v: Boolean) { widgetSteps = v; sp.edit().putBoolean("wSteps", v).apply() }
    fun updateWidgetStorage(v: Boolean) { widgetStorage = v; sp.edit().putBoolean("wStorage", v).apply() }
    fun updateWidgetNotes(v: Boolean) { widgetNotes = v; sp.edit().putBoolean("wNotes", v).apply() }
    fun updateStepGoal(v: Int) { stepGoal = v; sp.edit().putInt("stepGoal", v).apply() }
    fun updateNote(v: String) { note = v; sp.edit().putString("note", v).apply() }

    /** One-time: the widget page takes over "swipe right" (it can be changed back in Settings). */
    fun migrateWidgetsGesture() {
        if (widgetsMigrated) return
        if (gesturesSeeded) updateSwipeRight(GestureAction.Widgets)
        widgetsMigrated = true
        sp.edit().putBoolean("widgetsMigrated", true).apply()
    }

    fun updateSwipeLeft(a: GestureAction) { swipeLeft = a; sp.edit().putString("swipeLeft", a.encode()).apply() }
    fun updateSwipeRight(a: GestureAction) { swipeRight = a; sp.edit().putString("swipeRight", a.encode()).apply() }
    fun updateDoubleTap(a: GestureAction) { doubleTap = a; sp.edit().putString("doubleTap", a.encode()).apply() }

    fun seedGestures(left: GestureAction, right: GestureAction) {
        updateSwipeLeft(left)
        updateSwipeRight(right)
        gesturesSeeded = true
        sp.edit().putBoolean("gesturesSeeded", true).apply()
    }
}
