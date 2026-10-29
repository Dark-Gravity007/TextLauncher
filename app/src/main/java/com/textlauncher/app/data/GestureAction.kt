package com.textlauncher.app.data

/** Something a gesture can do. Stored in preferences as a short string. */
sealed class GestureAction {
    object None : GestureAction()
    object Drawer : GestureAction()
    object Settings : GestureAction()
    object Flashlight : GestureAction()
    object LockScreen : GestureAction()
    object Notifications : GestureAction()
    object Widgets : GestureAction()
    class App(val packageName: String) : GestureAction()

    fun encode(): String = when (this) {
        None -> "none"
        Drawer -> "drawer"
        Settings -> "settings"
        Flashlight -> "flashlight"
        LockScreen -> "lock"
        Notifications -> "notifications"
        Widgets -> "widgets"
        is App -> "app:$packageName"
    }

    companion object {
        fun decode(value: String?): GestureAction = when {
            value == null -> None
            value == "drawer" -> Drawer
            value == "settings" -> Settings
            value == "flashlight" -> Flashlight
            value == "lock" -> LockScreen
            value == "notifications" -> Notifications
            value == "widgets" -> Widgets
            value.startsWith("app:") && value.length > 4 -> App(value.substring(4))
            else -> None
        }

        /** What the picker offers besides "an app". */
        val standard: List<Pair<String, GestureAction>> = listOf(
            "Nothing" to None,
            "App drawer" to Drawer,
            "Flashlight" to Flashlight,
            "Lock screen" to LockScreen,
            "Widget page" to Widgets,
            "Notifications" to Notifications,
            "Launcher settings" to Settings,
        )
    }
}
