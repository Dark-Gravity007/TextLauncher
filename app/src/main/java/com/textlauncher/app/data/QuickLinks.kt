package com.textlauncher.app.data

import android.content.Intent
import android.provider.Settings

/** One-tap shortcuts used by the Launcher Hub widget and the custom widget builder. */
class QuickLink(val id: String, val label: String, val intent: Intent?)

object QuickLinks {
    // intent == null means the launcher handles it itself (flashlight, lock screen)
    val all: List<QuickLink> by lazy {
        listOf(
            QuickLink("wifi", "Wi-Fi", Intent(Settings.ACTION_WIFI_SETTINGS)),
            QuickLink("bluetooth", "Bluetooth", Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),
            QuickLink("display", "Display", Intent(Settings.ACTION_DISPLAY_SETTINGS)),
            QuickLink("sound", "Sound", Intent(Settings.ACTION_SOUND_SETTINGS)),
            QuickLink("battery", "Battery saver", Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)),
            QuickLink("data", "Data usage", Intent(Settings.ACTION_DATA_USAGE_SETTINGS)),
            QuickLink("storage", "Storage", Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
            QuickLink("location", "Location", Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
            QuickLink("flashlight", "Flashlight", null),
            QuickLink("lock", "Lock screen", null),
        )
    }

    fun byId(id: String): QuickLink? = all.firstOrNull { it.id == id }
}
