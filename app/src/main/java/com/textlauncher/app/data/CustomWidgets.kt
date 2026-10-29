package com.textlauncher.app.data

import org.json.JSONArray
import org.json.JSONObject

/** One building block of a custom widget. type: "text", "clock", "stat" or "action". */
class CustomElement(val type: String, val arg: String)

class CustomWidget(val id: Long, val title: String, val elements: List<CustomElement>)

object CustomWidgetStore {

    val statChoices: List<Pair<String, String>> = listOf(
        "battery" to "Battery",
        "storage" to "Storage free",
        "memory" to "Memory used",
        "steps" to "Steps today",
        "screentime" to "Screen time",
        "date" to "Today's date",
    )

    fun parse(json: String?): List<CustomWidget> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<CustomWidget>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val els = ArrayList<CustomElement>()
                val ea = o.getJSONArray("elements")
                for (j in 0 until ea.length()) {
                    val e = ea.getJSONObject(j)
                    els.add(CustomElement(e.getString("type"), e.optString("arg", "")))
                }
                out.add(CustomWidget(o.getLong("id"), o.optString("title", ""), els))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toJson(list: List<CustomWidget>): String {
        val arr = JSONArray()
        for (w in list) {
            val o = JSONObject()
            o.put("id", w.id)
            o.put("title", w.title)
            val ea = JSONArray()
            for (e in w.elements) {
                ea.put(JSONObject().put("type", e.type).put("arg", e.arg))
            }
            o.put("elements", ea)
            arr.put(o)
        }
        return arr.toString()
    }
}
