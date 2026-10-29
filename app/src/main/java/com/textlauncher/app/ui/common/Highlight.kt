package com.textlauncher.app.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.textlauncher.app.ui.theme.Palette

/** Matched letters go bold and white; the rest stays in the Text's own (dimmer) colour. */
fun highlightedLabel(label: String, indices: IntArray): AnnotatedString = buildAnnotatedString {
    append(label)
    var i = 0
    while (i < indices.size) {
        val start = indices[i]
        var end = start + 1
        var j = i + 1
        while (j < indices.size && indices[j] == end) {
            end++
            j++
        }
        if (start >= 0 && end <= label.length) {
            addStyle(SpanStyle(fontWeight = FontWeight.W700, color = Palette.Label), start, end)
        }
        i = j
    }
}
