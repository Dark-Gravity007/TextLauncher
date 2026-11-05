package com.textlauncher.app.data

/** Sticky-note checklist, stored as one line per item: "0|text" (open) or "1|text" (done). */
class CheckItem(val done: Boolean, val text: String)

object Checklist {

    fun parse(raw: String): List<CheckItem> =
        raw.split("\n").mapNotNull { line ->
            if (line.length < 3 || line[1] != '|') null
            else CheckItem(line[0] == '1', line.substring(2))
        }

    fun encode(items: List<CheckItem>): String =
        items.joinToString("\n") { (if (it.done) "1|" else "0|") + it.text.replace("\n", " ") }

    fun add(items: List<CheckItem>, text: String): List<CheckItem> {
        val t = text.trim().take(120)
        return if (t.isEmpty() || items.size >= 30) items else items + CheckItem(false, t)
    }

    fun toggle(items: List<CheckItem>, index: Int): List<CheckItem> =
        items.mapIndexed { i, it -> if (i == index) CheckItem(!it.done, it.text) else it }

    fun remove(items: List<CheckItem>, index: Int): List<CheckItem> =
        items.filterIndexed { i, _ -> i != index }

    fun doneCount(items: List<CheckItem>): Int = items.count { it.done }
}
