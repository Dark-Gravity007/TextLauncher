package com.textlauncher.app.search

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.pow
import kotlin.math.sqrt

/** Tiny expression evaluator: + - * / ^ ( ) sqrt() pi e. Returns null if the text is not a valid expression. */
object Calc {

    fun evaluate(input: String): Double? {
        val cleaned = input.lowercase()
            .replace('×', '*')
            .replace('÷', '/')
            .trim()
        if (cleaned.isEmpty()) return null
        val result = Parser(cleaned).parse() ?: return null
        return if (result.isNaN() || result.isInfinite()) null else result
    }

    /** True when the text looks like maths (has an operator or function), not just a bare number. */
    fun looksLikeMath(input: String): Boolean {
        val t = input.lowercase()
        if (t.none { it.isDigit() } && !t.contains("pi")) return false
        return t.any { it in "+-*/^()×÷" } || t.contains("sqrt")
    }

    fun format(value: Double): String {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return value.toLong().toString()
        }
        val bd = BigDecimal(value).round(MathContext(10)).stripTrailingZeros()
        return bd.toPlainString()
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): Double? = try {
            val v = expr()
            skipSpaces()
            if (pos == s.length) v else null
        } catch (e: IllegalArgumentException) {
            null
        }

        private fun skipSpaces() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? {
            skipSpaces()
            return if (pos < s.length) s[pos] else null
        }

        private fun expr(): Double {
            var v = term()
            var c = peek()
            while (c == '+' || c == '-') {
                pos++
                val r = term()
                v = if (c == '+') v + r else v - r
                c = peek()
            }
            return v
        }

        private fun term(): Double {
            var v = power()
            var c = peek()
            while (c == '*' || c == '/') {
                pos++
                val r = power()
                v = if (c == '*') v * r else v / r
                c = peek()
            }
            return v
        }

        private fun power(): Double {
            val base = unary()
            if (peek() == '^') {
                pos++
                return base.pow(power())
            }
            return base
        }

        private fun unary(): Double {
            val c = peek()
            if (c == '-') {
                pos++
                return -unary()
            }
            if (c == '+') {
                pos++
                return unary()
            }
            return atom()
        }

        private fun atom(): Double {
            val c = peek() ?: throw IllegalArgumentException("unexpected end")
            if (c == '(') {
                pos++
                val v = expr()
                if (peek() != ')') throw IllegalArgumentException("missing )")
                pos++
                return v
            }
            if (c.isDigit() || c == '.') {
                val start = pos
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
                return s.substring(start, pos).toDouble()
            }
            if (c.isLetter()) {
                val start = pos
                while (pos < s.length && s[pos].isLetter()) pos++
                return when (s.substring(start, pos)) {
                    "pi" -> Math.PI
                    "e" -> Math.E
                    "sqrt" -> {
                        if (peek() != '(') throw IllegalArgumentException("sqrt needs (")
                        sqrt(atom())
                    }
                    else -> throw IllegalArgumentException("unknown name")
                }
            }
            throw IllegalArgumentException("unexpected character")
        }
    }
}
