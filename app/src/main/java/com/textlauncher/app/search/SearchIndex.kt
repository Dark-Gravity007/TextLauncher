package com.textlauncher.app.search

/**
 * Searchable list. Labels are prepared once; results for recent queries are cached (small LRU),
 * so retyping or backspacing is instant. Safe to call from any thread.
 */
class SearchIndex<T>(items: List<T>, label: (T) -> String) {

    class Hit<T>(val item: T, val score: Int, val indices: IntArray, val label: String)

    private class Entry<T>(val item: T, val prepared: Fuzzy.Prepared)

    private val entries: List<Entry<T>> = items.map { Entry(it, Fuzzy.Prepared(label(it))) }

    private val cache = object : LinkedHashMap<String, List<Hit<T>>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Hit<T>>>?): Boolean {
            return size > MAX_CACHED
        }
    }

    fun search(query: String): List<Hit<T>> {
        val q = Fuzzy.normalizeQuery(query)
        if (q.isEmpty()) return emptyList()
        synchronized(cache) {
            val cached = cache[q]
            if (cached != null) return cached
        }
        val out = ArrayList<Hit<T>>()
        for (e in entries) {
            val m = Fuzzy.matchPrepared(q, e.prepared) ?: continue
            out.add(Hit(e.item, m.score, m.indices, e.prepared.label))
        }
        out.sortWith(
            compareByDescending<Hit<T>> { it.score }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        )
        synchronized(cache) { cache[q] = out }
        return out
    }

    private companion object {
        const val MAX_CACHED = 48
    }
}
