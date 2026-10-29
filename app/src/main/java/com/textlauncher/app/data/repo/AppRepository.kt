package com.textlauncher.app.data.repo

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.textlauncher.app.data.model.AppInfo

object AppRepository {

    /** All launchable apps, A–Z. Safe to call from a background thread. */
    fun loadApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        val out = ArrayList<AppInfo>()
        for (info in pm.queryIntentActivities(intent, 0)) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) continue
            if (!seen.add(pkg)) continue
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
            out.add(AppInfo(pkg, label.ifEmpty { pkg }))
        }
        out.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        return out
    }

    /** [enterAnim] / [exitAnim] are animation resource ids; 0 keeps the system default. */
    fun launch(context: Context, packageName: String, enterAnim: Int = 0, exitAnim: Int = 0): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            if (enterAnim != 0) {
                val options = ActivityOptions.makeCustomAnimation(context, enterAnim, exitAnim)
                context.startActivity(intent, options.toBundle())
            } else {
                context.startActivity(intent)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openAppInfo(context: Context, packageName: String) {
        startSafely(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
    }

    fun uninstall(context: Context, packageName: String) {
        startSafely(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
    }

    fun openUrl(context: Context, url: String) {
        val full = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(full)))
    }

    fun startSafely(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Phone, messages, browser and camera if the device has them: sensible first-run favourites. */
    fun defaultFavorites(context: Context, installed: Set<String>): List<String> {
        val pm = context.packageManager
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        val out = ArrayList<String>()
        for (probe in probes) {
            val pkg = try {
                pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (e: Exception) {
                null
            }
            if (pkg != null && pkg in installed && pkg !in out) out.add(pkg)
        }
        return out
    }

    /** Phone and camera packages, if present: first-run defaults for swipe right / swipe left. */
    fun defaultShortcutApps(context: Context, installed: Set<String>): Pair<String?, String?> {
        val pm = context.packageManager
        fun resolve(intent: Intent): String? {
            val pkg = try {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (e: Exception) {
                null
            }
            return if (pkg != null && pkg in installed) pkg else null
        }
        val phone = resolve(Intent(Intent.ACTION_DIAL))
        val camera = resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        return Pair(phone, camera)
    }
}
