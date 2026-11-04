package com.textlauncher.app.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.textlauncher.app.data.prefs.PreferencesManager

/**
 * Lets the widget page show ordinary Android widgets from other apps, using the system's AppWidgetHost.
 * Widgets are drawn and updated by their own apps; this launcher only gives them a place to live.
 */
class WidgetController(
    private val activity: Activity,
    private val prefs: PreferencesManager,
) {
    companion object {
        const val HOST_ID = 4242
        const val REQ_BIND = 7001
        const val REQ_CONFIGURE = 7002
    }

    private val appContext = activity.applicationContext
    private val host = AppWidgetHost(appContext, HOST_ID)
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    private var listening = false
    private var pendingId = -1

    /** Ids of widgets currently on the page. */
    var hosted by mutableStateOf(load())
        private set

    private fun load(): List<Int> =
        prefs.str("hostedWidgets", "").split(",")
            .mapNotNull { it.toIntOrNull() }
            .filter { manager.getAppWidgetInfo(it) != null }

    private fun save(ids: List<Int>) {
        hosted = ids
        prefs.setStr("hostedWidgets", ids.joinToString(","))
    }

    fun start() {
        if (!listening) {
            try {
                host.startListening()
                listening = true
            } catch (e: Exception) {
                listening = false
            }
        }
        hosted = load()
    }

    fun stop() {
        if (listening) {
            try {
                host.stopListening()
            } catch (e: Exception) {
                // ignore
            }
            listening = false
        }
    }

    fun providers(): List<AppWidgetProviderInfo> = try {
        manager.installedProviders.sortedBy { label(it).lowercase() }
    } catch (e: Exception) {
        emptyList()
    }

    fun label(info: AppWidgetProviderInfo): String =
        info.loadLabel(appContext.packageManager) ?: info.provider.className

    fun appLabel(info: AppWidgetProviderInfo): String = try {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
    } catch (e: Exception) {
        info.provider.packageName
    }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun createView(id: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return host.createView(activity, id, info)
    }

    /** Starts the add flow: bind (asking the user if Android requires it), then the widget's own setup screen if it has one. */
    fun add(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            afterBind(id)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, REQ_BIND)
            } catch (e: Exception) {
                discard(id)
            }
        }
    }

    private fun afterBind(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info?.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, null)
            } catch (e: Exception) {
                finish(id)
            }
        } else {
            finish(id)
        }
    }

    /** Call from the activity's onActivityResult. Returns true if the result was ours. */
    fun onActivityResult(requestCode: Int, resultCode: Int, @Suppress("UNUSED_PARAMETER") data: Intent?): Boolean {
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIGURE) return false
        val id = pendingId
        if (id < 0) return true
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQ_BIND) afterBind(id) else finish(id)
        } else {
            discard(id)
        }
        return true
    }

    private fun finish(id: Int) {
        if (id !in hosted) save(hosted + id)
        pendingId = -1
    }

    private fun discard(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (e: Exception) {
            // ignore
        }
        pendingId = -1
    }

    fun remove(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (e: Exception) {
            // ignore
        }
        save(hosted.filter { it != id })
    }
}
