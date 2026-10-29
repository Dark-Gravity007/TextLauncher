package com.textlauncher.app.search

/** What the search bar can do besides finding apps. Pure logic, no Android types. */
sealed class QuickAction {
    abstract val title: String
    abstract val subtitle: String?

    class Answer(val expression: String, val result: String) : QuickAction() {
        override val title: String get() = "= $result"
        override val subtitle: String? get() = expression
    }

    class Conversion(val text: String) : QuickAction() {
        override val title: String get() = text
        override val subtitle: String? get() = null
    }

    class OpenUrl(val url: String) : QuickAction() {
        override val title: String get() = "Open $url"
        override val subtitle: String? get() = null
    }

    class WebSearch(val url: String, val label: String, val query: String) : QuickAction() {
        override val title: String get() = "$label “$query”"
        override val subtitle: String? get() = null
    }
}

object QuickActions {

    val engines: List<Pair<String, String>> = listOf(
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Google" to "https://www.google.com/search?q=",
        "Brave" to "https://search.brave.com/search?q=",
    )

    private val prefixes: List<Triple<String, String, String>> = listOf(
        Triple("yt", "YouTube", "https://www.youtube.com/results?search_query="),
        Triple("w", "Wikipedia", "https://en.wikipedia.org/w/index.php?search="),
        Triple("maps", "Maps", "https://www.google.com/maps/search/?api=1&query="),
        Triple("g", "Google", "https://www.google.com/search?q="),
        Triple("ddg", "DuckDuckGo", "https://duckduckgo.com/?q="),
    )

    private val urlPattern =
        Regex("^(https?://)?[a-z0-9-]+(\\.[a-z0-9-]+)+(:\\d+)?(/\\S*)?$", RegexOption.IGNORE_CASE)

    fun forQuery(raw: String, engineIndex: Int, encode: (String) -> String): List<QuickAction> {
        val query = raw.trim()
        if (query.isEmpty()) return emptyList()
        val out = ArrayList<QuickAction>()

        val conversion = UnitConverter.convert(query)
        if (conversion != null) out.add(QuickAction.Conversion(conversion))

        if (Calc.looksLikeMath(query)) {
            val value = Calc.evaluate(query)
            if (value != null) out.add(QuickAction.Answer(query, Calc.format(value)))
        }

        if (!query.contains(' ') && urlPattern.matches(query)) {
            out.add(QuickAction.OpenUrl(query))
        }

        val space = query.indexOf(' ')
        if (space > 0) {
            val key = query.substring(0, space).lowercase()
            val rest = query.substring(space + 1).trim()
            val match = prefixes.firstOrNull { it.first == key }
            if (match != null && rest.isNotEmpty()) {
                out.add(QuickAction.WebSearch(match.third + encode(rest), match.second, rest))
            }
        }

        val engine = engines[engineIndex.coerceIn(0, engines.size - 1)]
        out.add(QuickAction.WebSearch(engine.second + encode(query), "Search " + engine.first + " for", query))
        return out
    }
}
