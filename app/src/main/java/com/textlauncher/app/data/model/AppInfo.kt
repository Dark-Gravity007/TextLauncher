package com.textlauncher.app.data.model

/** One launchable app. Text-only launcher, so no icon is ever loaded. */
class AppInfo(
    val packageName: String,
    val label: String,
) {
    /** A–Z, or '#' for anything else. Used for the alphabet index. */
    val letter: Char = run {
        val c = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        if (c != null && c in 'A'..'Z') c else '#'
    }
}
