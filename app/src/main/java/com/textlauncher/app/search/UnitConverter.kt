package com.textlauncher.app.search

import java.math.BigDecimal
import java.math.MathContext

/** Offline unit conversion for text like "5 km to mi" or "20 c in f". */
object UnitConverter {

    private class U(val category: String, val toBase: Double)

    private val units: Map<String, U> = HashMap<String, U>().also { m ->
        fun add(cat: String, factor: Double, vararg names: String) {
            for (n in names) m[n] = U(cat, factor)
        }
        // length (base: metre)
        add("length", 0.001, "mm")
        add("length", 0.01, "cm")
        add("length", 1.0, "m")
        add("length", 1000.0, "km")
        add("length", 0.0254, "in", "inch", "inches")
        add("length", 0.3048, "ft", "foot", "feet")
        add("length", 0.9144, "yd", "yard", "yards")
        add("length", 1609.344, "mi", "mile", "miles")
        // mass (base: kilogram)
        add("mass", 0.000001, "mg")
        add("mass", 0.001, "g")
        add("mass", 1.0, "kg")
        add("mass", 0.45359237, "lb", "lbs", "pound", "pounds")
        add("mass", 0.028349523125, "oz", "ounce", "ounces")
        // volume (base: litre)
        add("volume", 0.001, "ml")
        add("volume", 1.0, "l", "litre", "liter", "litres", "liters")
        add("volume", 3.785411784, "gal", "gallon", "gallons")
        add("volume", 0.0295735295625, "floz")
        add("volume", 0.2365882365, "cup", "cups")
        // speed (base: m/s)
        add("speed", 1.0, "m/s", "ms")
        add("speed", 1000.0 / 3600.0, "kmh", "kph", "km/h")
        add("speed", 0.44704, "mph")
        add("speed", 0.514444, "kn", "knot", "knots")
        // time (base: second)
        add("time", 1.0, "s", "sec", "secs", "second", "seconds")
        add("time", 60.0, "min", "mins", "minute", "minutes")
        add("time", 3600.0, "h", "hr", "hrs", "hour", "hours")
        add("time", 86400.0, "d", "day", "days")
        add("time", 604800.0, "wk", "week", "weeks")
        // data (base: byte)
        add("data", 1.0, "b", "byte", "bytes")
        add("data", 1e3, "kb")
        add("data", 1e6, "mb")
        add("data", 1e9, "gb")
        add("data", 1e12, "tb")
    }

    private val temps = setOf("c", "f", "k", "°c", "°f", "celsius", "fahrenheit", "kelvin")

    private val pattern = Regex(
        "^(-?\\d+(?:\\.\\d+)?)\\s*([a-z°/]+)\\s+(?:to|in|as|->|=)\\s+([a-z°/]+)$"
    )

    /** Returns e.g. "5 km = 3.106856 mi", or null when the text is not a conversion. */
    fun convert(input: String): String? {
        val m = pattern.matchEntire(input.trim().lowercase()) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        val from = m.groupValues[2]
        val to = m.groupValues[3]

        if (from in temps && to in temps) {
            val result = convertTemperature(value, tempKey(from), tempKey(to))
            return Calc.format(value) + " " + tempLabel(from) + " = " + round(result) + " " + tempLabel(to)
        }

        val a = units[from] ?: return null
        val b = units[to] ?: return null
        if (a.category != b.category) return null
        val result = value * a.toBase / b.toBase
        return Calc.format(value) + " " + from + " = " + round(result) + " " + to
    }

    private fun tempKey(name: String): Char = when (name.removePrefix("°")) {
        "c", "celsius" -> 'c'
        "f", "fahrenheit" -> 'f'
        else -> 'k'
    }

    private fun tempLabel(name: String): String = when (tempKey(name)) {
        'c' -> "°C"
        'f' -> "°F"
        else -> "K"
    }

    private fun convertTemperature(v: Double, from: Char, to: Char): Double {
        val celsius = when (from) {
            'c' -> v
            'f' -> (v - 32.0) * 5.0 / 9.0
            else -> v - 273.15
        }
        return when (to) {
            'c' -> celsius
            'f' -> celsius * 9.0 / 5.0 + 32.0
            else -> celsius + 273.15
        }
    }

    private fun round(v: Double): String =
        BigDecimal(v).round(MathContext(7)).stripTrailingZeros().toPlainString()
}
