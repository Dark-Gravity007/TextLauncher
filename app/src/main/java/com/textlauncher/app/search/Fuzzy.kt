package com.textlauncher.app.search

import java.text.Normalizer

/** Result of matching one query against one label. [indices] are positions in the original label, for highlighting. */
class FuzzyMatch(val score: Int, val indices: IntArray)

/**
 * Typo-tolerant matcher. Tiers, best first:
 *  1. prefix of the name        ("chr"  -> Chrome)
 *  2. prefix of any word        ("music" -> YouTube Music)
 *  3. substring                 ("tube" -> YouTube)
 *  4. letters in order          ("ytm"  -> YouTube Music)
 *  5. typos, by edit distance   ("chorme" -> Chrome, "wattsapp" -> WhatsApp)
 * Accents are ignored and spaces in the query are ignored.
 */
object Fuzzy {

    /** Lower-cases and strips accents one character at a time, so the result has the same length as the input. */
    fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val lower = ch.lowercaseChar()
            if (lower.code < 128) {
                sb.append(lower)
            } else {
                val base = Normalizer.normalize(lower.toString(), Normalizer.Form.NFD)
                sb.append(if (base.isNotEmpty()) base[0] else lower)
            }
        }
        return sb.toString()
    }

    fun normalizeQuery(query: String): String = fold(query).filter { !it.isWhitespace() }

    /** Convenience for tests and one-off calls. For lists use [SearchIndex]. */
    fun match(query: String, label: String): FuzzyMatch? {
        val q = normalizeQuery(query)
        if (q.isEmpty()) return null
        return matchPrepared(q, Prepared(label))
    }

    /** Pre-computed view of one label so repeated searches do no string work. */
    class Prepared(val label: String) {
        val folded: String = fold(label)
        val compact: String
        val compactToOriginal: IntArray
        val wordStarts: IntArray

        init {
            val c = StringBuilder()
            val map = ArrayList<Int>()
            for (i in folded.indices) {
                if (!folded[i].isWhitespace()) {
                    c.append(folded[i])
                    map.add(i)
                }
            }
            compact = c.toString()
            compactToOriginal = map.toIntArray()

            val starts = ArrayList<Int>()
            var prevIsLetter = false
            for (i in folded.indices) {
                val isLetter = folded[i].isLetterOrDigit()
                if (isLetter && !prevIsLetter) starts.add(i)
                prevIsLetter = isLetter
            }
            wordStarts = starts.toIntArray()
        }
    }

    fun matchPrepared(q: String, p: Prepared): FuzzyMatch? {
        val t = p.folded
        val len = q.length

        // 1. prefix of the whole name
        if (p.compact.startsWith(q)) {
            return FuzzyMatch(1000 - t.length, mapRange(p, 0, len))
        }

        // 2. prefix of any word
        for (ws in p.wordStarts) {
            if (t.regionMatches(ws, q, 0, len)) {
                return FuzzyMatch(900 - ws - t.length, range(ws, len))
            }
        }

        // 3. substring
        val sub = p.compact.indexOf(q)
        if (sub >= 0) {
            return FuzzyMatch(700 - sub * 3 - t.length, mapRange(p, sub, len))
        }

        // 4. letters in order, close together
        if (len >= 2) {
            val hit = subsequence(q, p)
            if (hit != null) return hit
        }

        // 5. typos
        if (len >= 4) {
            val typo = typo(q, p)
            if (typo != null) return typo
        }
        return null
    }

    private fun range(start: Int, count: Int) = IntArray(count) { start + it }

    private fun mapRange(p: Prepared, start: Int, count: Int) =
        IntArray(count) { p.compactToOriginal[start + it] }

    private fun subsequence(q: String, p: Prepared): FuzzyMatch? {
        val c = p.compact
        val picked = IntArray(q.length)
        var qi = 0
        var streak = 0
        var points = 0
        var ci = 0
        while (ci < c.length && qi < q.length) {
            if (c[ci] == q[qi]) {
                streak++
                points += 10 + streak * 4
                picked[qi] = ci
                qi++
            } else {
                streak = 0
                points -= 1
            }
            ci++
        }
        if (qi < q.length) return null
        val span = picked[q.length - 1] - picked[0] + 1
        if (span > q.length * 3 + 2) return null
        val score = (100 + points).coerceIn(1, 600)
        return FuzzyMatch(score, IntArray(q.length) { p.compactToOriginal[picked[it]] })
    }

    private fun typo(q: String, p: Prepared): FuzzyMatch? {
        val maxDist = if (q.length <= 5) 1 else 2
        var bestDist = Int.MAX_VALUE
        var bestStart = 0
        var bestLen = 0
        var bestIsCompact = false

        fun consider(text: String, start: Int, isCompact: Boolean) {
            val maxLen = minOf(text.length - start, q.length + maxDist)
            val minLen = maxOf(1, q.length - maxDist)
            var k = minLen
            while (k <= maxLen) {
                val d = editDistance(q, text, start, k)
                if (d < bestDist || (d == bestDist && k > bestLen)) {
                    bestDist = d
                    bestStart = start
                    bestLen = k
                    bestIsCompact = isCompact
                }
                k++
            }
        }

        for (ws in p.wordStarts) consider(p.folded, ws, false)
        consider(p.compact, 0, true)

        if (bestDist > maxDist) return null
        val score = 500 - bestDist * 120 - p.folded.length
        val idx = if (bestIsCompact) mapRange(p, bestStart, bestLen) else range(bestStart, bestLen)
        return FuzzyMatch(score.coerceAtLeast(1), idx)
    }

    /**
     * Optimal-string-alignment distance between [a] and b[start, start+count):
     * insert, delete, substitute, or swap two neighbouring letters, each costing 1.
     */
    fun editDistance(a: String, b: String, start: Int, count: Int): Int {
        val n = a.length
        val m = count
        if (n == 0) return m
        if (m == 0) return n
        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[start + j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[start + j - 2] && a[i - 2] == b[start + j - 1]) {
                    v = minOf(v, prev2[j - 2] + 1)
                }
                cur[j] = v
            }
            val tmp = prev2
            prev2 = prev
            prev = cur
            cur = tmp
        }
        return prev[m]
    }
}
