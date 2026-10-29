package com.textlauncher.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.textlauncher.app.data.GestureAction
import com.textlauncher.app.data.StepCounter
import com.textlauncher.app.data.WallpaperTone
import com.textlauncher.app.data.model.AppInfo
import com.textlauncher.app.data.prefs.PreferencesManager
import com.textlauncher.app.data.repo.AppRepository
import com.textlauncher.app.media.NowPlaying
import com.textlauncher.app.system.Torch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the UI observes. Lives as long as the activity. */
class LauncherState(private val context: Context) {

    val prefs = PreferencesManager(context)
    val nowPlaying = NowPlaying(context)
    val torch = Torch(context)
    val steps = StepCounter(context)

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    /** True when the wallpaper is bright; the scrim gets stronger so white text stays readable. */
    var wallpaperLight by mutableStateOf(false)
        private set

    /** Bumped on every onResume so widgets refresh after returning from another app or from Settings. */
    var resumeTick by mutableStateOf(0)
        private set

    /** Bumped when the Home button is pressed so the UI can close the drawer / sheets. */
    var homeTick by mutableStateOf(0)
        private set

    fun onResume() {
        resumeTick += 1
        wallpaperLight = WallpaperTone.isLight(context)
        nowPlaying.start()
        steps.start()
    }

    fun onPause() {
        nowPlaying.stop()
        steps.stop()
    }

    fun onHomePressed() {
        homeTick += 1
    }

    suspend fun refreshApps() {
        val loaded = withContext(Dispatchers.Default) { AppRepository.loadApps(context) }
        apps = loaded
        if (!prefs.seeded && loaded.isNotEmpty()) {
            val defaults = AppRepository.defaultFavorites(context, loaded.map { it.packageName }.toSet())
            prefs.updateFavorites(defaults)
            prefs.markSeeded()
        }
        if (!prefs.gesturesSeeded && loaded.isNotEmpty()) {
            val (phone, camera) = AppRepository.defaultShortcutApps(context, loaded.map { it.packageName }.toSet())
            prefs.seedGestures(
                left = if (camera != null) GestureAction.App(camera) else GestureAction.None,
                right = GestureAction.Widgets,
            )
        }
        prefs.migrateWidgetsGesture()
    }

    fun favoriteApps(): List<AppInfo> {
        val byPackage = apps.associateBy { it.packageName }
        return prefs.favorites.mapNotNull { byPackage[it] }
    }

    fun release() {
        nowPlaying.stop()
        torch.release()
        steps.stop()
    }

    fun launch(packageName: String, enterAnim: Int = 0, exitAnim: Int = 0) {
        AppRepository.launch(context, packageName, enterAnim, exitAnim)
    }
}
