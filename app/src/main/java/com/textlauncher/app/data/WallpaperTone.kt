package com.textlauncher.app.data

import android.app.WallpaperManager
import android.content.Context
import android.os.Build

/** Rough "is the wallpaper bright?" check, used only to pick how strong the readability scrim should be. */
object WallpaperTone {
    fun isLight(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 27) return false
        return try {
            val colors = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return false
            colors.primaryColor.luminance() > 0.5f
        } catch (e: Exception) {
            false
        }
    }
}
