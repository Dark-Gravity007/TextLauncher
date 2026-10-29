package com.textlauncher.app.system

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent

/**
 * Exists for one reason: Android only lets an app turn the screen off through an accessibility service.
 * It listens to no events and reads no screen content.
 */
class LockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // deliberately empty
    }

    override fun onInterrupt() {
        // deliberately empty
    }

    companion object {
        @Volatile
        private var instance: LockAccessibilityService? = null

        /** Android 9+ only. Returns false if unsupported or the service is not switched on. */
        fun lock(): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < 28) return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 28

        fun isEnabled(context: Context): Boolean {
            val setting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expected = ComponentName(context, LockAccessibilityService::class.java)
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(setting)
            while (splitter.hasNext()) {
                val name = ComponentName.unflattenFromString(splitter.next())
                if (name != null && name == expected) return true
            }
            return false
        }
    }
}
