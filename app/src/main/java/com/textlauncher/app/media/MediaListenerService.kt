package com.textlauncher.app.media

import android.service.notification.NotificationListenerService

/**
 * Intentionally empty. Android only lets an app read the active media sessions
 * when it has an enabled notification listener, so this class is that hook.
 * It never reads or stores any notification.
 */
class MediaListenerService : NotificationListenerService()
