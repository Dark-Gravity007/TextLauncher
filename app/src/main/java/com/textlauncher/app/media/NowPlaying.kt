package com.textlauncher.app.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Observes the active media session and exposes track title / play state to Compose. */
class NowPlaying(private val context: Context) {

    var title by mutableStateOf<String?>(null)
        private set
    var artist by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var hasAccess by mutableStateOf(false)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var art by mutableStateOf<Bitmap?>(null)
        private set

    private var basePosition = 0L
    private var baseTime = 0L
    private var speed = 1f

    /** Live playback position, extrapolated from the last update the player sent. */
    fun positionMs(): Long {
        val p = if (playing) basePosition + ((SystemClock.elapsedRealtime() - baseTime) * speed).toLong() else basePosition
        return if (durationMs > 0L) p.coerceIn(0L, durationMs) else p.coerceAtLeast(0L)
    }

    private fun applyState(state: PlaybackState?) {
        playing = state?.state == PlaybackState.STATE_PLAYING
        basePosition = state?.position ?: 0L
        baseTime = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime()
        speed = state?.playbackSpeed ?: 1f
    }

    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val component = ComponentName(context, MediaListenerService::class.java)
    private var controller: MediaController? = null
    private var listening = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list)
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            applyMetadata(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            applyState(state)
        }

        override fun onSessionDestroyed() {
            clear()
        }
    }

    fun isAccessGranted(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabled != null && enabled.contains(context.packageName)
    }

    /** Safe to call repeatedly (e.g. every onResume). */
    fun start() {
        stop()
        hasAccess = isAccessGranted()
        if (!hasAccess) return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            listening = true
            pick(manager.getActiveSessions(component))
        } catch (e: SecurityException) {
            hasAccess = false
        }
    }

    fun stop() {
        if (listening) {
            try {
                manager.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (e: Exception) {
                // ignore
            }
            listening = false
        }
        controller?.unregisterCallback(callback)
        controller = null
        clear()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    private fun pick(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        if (best?.sessionToken == controller?.sessionToken && best != null) return
        controller?.unregisterCallback(callback)
        controller = best
        if (best == null) {
            clear()
            return
        }
        best.registerCallback(callback, handler)
        applyMetadata(best.metadata)
        applyState(best.playbackState)
    }

    private fun applyMetadata(metadata: MediaMetadata?) {
        val t = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        title = if (t.isNullOrBlank()) null else t
        val a = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        artist = if (a.isNullOrBlank()) null else a
        durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
    }

    private fun clear() {
        title = null
        artist = null
        playing = false
        durationMs = 0L
        art = null
        basePosition = 0L
    }
}
