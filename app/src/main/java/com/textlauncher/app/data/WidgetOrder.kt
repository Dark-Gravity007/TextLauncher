package com.textlauncher.app.data

/** Keeps the widget page's card order. Pure list logic. */
object WidgetOrder {

    /** Saved order first (dropping cards that no longer exist), then any new cards in their default order. */
    fun merge(saved: List<String>, defaults: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        for (id in saved) if (id in defaults) out.add(id)
        for (id in defaults) out.add(id)
        return out.toList()
    }

    /**
     * Moves [id] past its next visible neighbour. Hidden cards in between do not count,
     * so one tap always changes what you can see.
     */
    fun move(full: List<String>, visible: List<String>, id: String, delta: Int): List<String> {
        val vi = visible.indexOf(id)
        if (vi < 0) return full
        val target = visible.getOrNull(vi + delta) ?: return full
        val a = full.indexOf(id)
        val b = full.indexOf(target)
        if (a < 0 || b < 0) return full
        val out = full.toMutableList()
        out.removeAt(a)
        out.add(b, id)
        return out
    }
}
