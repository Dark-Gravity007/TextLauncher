package com.textlauncher.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

/** Last position the phone already knows. We never request a new fix and never use the network ourselves. */
object SunLocation {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** (latitude, longitude) or null. */
    fun lastKnown(context: Context): Pair<Double, Double>? {
        if (!hasPermission(context)) return null
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: android.location.Location? = null
            for (provider in listOf(LocationManager.PASSIVE_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
                val loc = try { lm.getLastKnownLocation(provider) } catch (e: Exception) { null }
                if (loc != null && (best == null || loc.time > best.time)) best = loc
            }
            if (best == null) null else Pair(best.latitude, best.longitude)
        } catch (e: Exception) {
            null
        }
    }
}
