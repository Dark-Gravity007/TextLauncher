package com.textlauncher.app.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale following the apple-design guidance: system font, tracking tightens as size grows
 * (about -0.02em on display sizes) and opens slightly on small text; leading is tight on big text.
 * Hierarchy comes from size + weight together.
 */
object Type {
    private val font = FontFamily.Default

    /** A soft dark halo keeps white text readable on bright patches of a wallpaper without looking heavy. */
    private val halo = Shadow(color = Color(0x99000000), offset = Offset(0f, 2f), blurRadius = 10f)

    private fun TextStyle.shaded(): TextStyle = copy(shadow = halo)

    /** The clock: very large, thin, tight tracking and leading. */
    val clock = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.W200,
        fontSize = 80.sp,
        lineHeight = 84.sp,
        letterSpacing = (-2.4).sp,
    ).shaded()

    val date = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ).shaded()

    /** Home favourites. [size] is 0 = small, 1 = medium, 2 = large. */
    fun favorite(size: Int): TextStyle {
        val sp = when (size) {
            0 -> 24
            2 -> 34
            else -> 29
        }
        return TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.W300,
            fontSize = sp.sp,
            lineHeight = (sp * 1.2f).sp,
            letterSpacing = (-0.02f * sp).sp,
        ).shaded()
    }

    val search = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.W300,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp,
    ).shaded()

    val drawerItem = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.W300,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ).shaded()

    val answer = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.W300,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp,
    ).shaded()

    val title = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.W600,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ).shaded()

    val body = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.W400,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ).shaded()

    val bodyStrong = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.W600,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ).shaded()

    /** Small text opens up slightly for legibility. */
    val caption = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ).shaded()

    val section = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.W600,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.6.sp,
    ).shaded()

    val index = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.W600,
        fontSize = 11.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    ).shaded()

    val bigLetter = TextStyle(
        fontFamily = font,
        fontWeight = FontWeight.W200,
        fontSize = 96.sp,
        lineHeight = 100.sp,
        letterSpacing = (-2.5).sp,
    ).shaded()
}
