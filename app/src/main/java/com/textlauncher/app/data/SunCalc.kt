package com.textlauncher.app.data

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise, sunset and golden hour from a latitude, longitude and date. Pure maths, fully offline.
 * (Standard solar-position formulas, accurate to about a minute.) Times are epoch milliseconds, null near the poles.
 */
object SunCalc {

    class Times(
        val sunrise: Long?,
        val sunset: Long?,
        val goldenMorningEnd: Long?,
        val goldenEveningStart: Long?,
        val dawn: Long?,
        val dusk: Long?,
    )

    private const val RAD = PI / 180.0
    private const val DAY_MS = 86_400_000.0
    private const val J1970 = 2440588.0
    private const val J2000 = 2451545.0
    private const val J0 = 0.0009
    private val OBLIQUITY = RAD * 23.4397

    private fun toDays(dateMs: Long): Double = dateMs / DAY_MS - 0.5 + J1970 - J2000
    private fun fromJulian(j: Double): Long = ((j + 0.5 - J1970) * DAY_MS).toLong()

    private fun declination(l: Double): Double = asin(sin(l) * sin(OBLIQUITY))

    private fun solarMeanAnomaly(d: Double) = RAD * (357.5291 + 0.98560028 * d)

    private fun eclipticLongitude(m: Double): Double {
        val c = RAD * (1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m))
        val p = RAD * 102.9372
        return m + c + p + PI
    }

    private fun julianCycle(d: Double, lw: Double) = Math.round(d - J0 - lw / (2 * PI)).toDouble()
    private fun approxTransit(ht: Double, lw: Double, n: Double) = J0 + (ht + lw) / (2 * PI) + n
    private fun solarTransit(ds: Double, m: Double, l: Double) = J2000 + ds + 0.0053 * sin(m) - 0.0069 * sin(2 * l)

    private fun hourAngle(h: Double, phi: Double, dec: Double): Double {
        return acos((sin(h) - sin(phi) * sin(dec)) / (cos(phi) * cos(dec)))
    }

    fun compute(latitude: Double, longitude: Double, dateMs: Long): Times {
        val lw = RAD * -longitude
        val phi = RAD * latitude
        val d = toDays(dateMs)
        val n = julianCycle(d, lw)
        val ds = approxTransit(0.0, lw, n)
        val m = solarMeanAnomaly(ds)
        val l = eclipticLongitude(m)
        val dec = declination(l)
        val noon = solarTransit(ds, m, l)

        fun pair(angleDeg: Double): Pair<Long?, Long?> {
            val w = hourAngle(angleDeg * RAD, phi, dec)
            if (w.isNaN()) return Pair(null, null)
            val set = solarTransit(approxTransit(w, lw, n), m, l)
            val rise = noon - (set - noon)
            return Pair(fromJulian(rise), fromJulian(set))
        }

        val sun = pair(-0.833)
        val golden = pair(6.0)
        val civil = pair(-6.0)
        return Times(
            sunrise = sun.first,
            sunset = sun.second,
            goldenMorningEnd = golden.first,
            goldenEveningStart = golden.second,
            dawn = civil.first,
            dusk = civil.second,
        )
    }

    /** 0.0 at sunrise, 1.0 at sunset, null when the sun is down. Used to place the dot on the arc. */
    fun dayFraction(times: Times, nowMs: Long): Float? {
        val rise = times.sunrise ?: return null
        val set = times.sunset ?: return null
        if (nowMs < rise || nowMs > set) return null
        return ((nowMs - rise).toDouble() / (set - rise)).toFloat()
    }

    @Suppress("unused")
    private fun unusedGuards() {
        floor(0.0); atan2(0.0, 1.0); tan(0.0)
    }
}
