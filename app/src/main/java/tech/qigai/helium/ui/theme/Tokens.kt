package tech.qigai.helium.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Helium design tokens (design v0.3, see design/svg/README.md and design/svg/src/hel.py).
 * One accent (he-447), neutral ink / canvas / hairline, status colours only on small dots and tags.
 */
object HeColors {
    val Accent = Color(0xFF4F6BFF)        // he-447 — the ONE accent colour
    val AccentTint = Color(0xFFECEEFF)    // accent ~8% on white: active nav pill, selected states
    val Cyan = Color(0xFF19C6E6)          // he-492 (only inside hero gradients)
    val Mint = Color(0xFF2FD6A0)          // he-501
    val Amber = Color(0xFFFFC53D)         // he-587
    val Coral = Color(0xFFFF6B6B)         // he-668
    val Glow = Color(0xFFB9A7FF)
    val Blush = Color(0xFFFF9ECF)

    val Ink = Color(0xFF12131A)
    val Ink2 = Color(0xFF5A5F73)
    val Ink3 = Color(0xFF9AA0B4)
    val Canvas = Color(0xFFF4F5FB)
    val Surface = Color(0xFFFFFFFF)
    val Mist = Color(0xFFE9ECF7)
    val Mist2 = Color(0xFFF0F2F8)
    val Mist3 = Color(0xFFF6F7FB)
    val Hair = Color(0xFFE6E8F0)
    val Outline = Color(0xFFCDD1DE)
    val ShadowInk = Color(0xFF1B2150)

    val AmberInk = Color(0xFF8A5A00)
    val AmberTint = Color(0xFFFFF4D9)
    val MintInk = Color(0xFF0E8A63)
    val MintTint = Color(0xFFE3F7EF)
    val CoralInk = Color(0xFFB3261E)
    val CoralTint = Color(0xFFFFE3E3)
}

/** 8dp grid; page margin 24, card gap 16, card padding 20/24. */
object HeSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val page = 24.dp
    val cardGap = 16.dp
    val cardPad = 20.dp
    val cardPadL = 24.dp
}

/** Radius: row 20 / card 28 / panel 32. */
object HeRadius {
    val row = 20.dp
    val card = 28.dp
    val panel = 32.dp
}

object HeShapes {
    val Row = RoundedCornerShape(HeRadius.row)
    val Card = RoundedCornerShape(HeRadius.card)
    val Panel = RoundedCornerShape(HeRadius.panel)
    val Pill = RoundedCornerShape(percent = 50)
    val Tag = RoundedCornerShape(8.dp)
}

/** Button heights: 56 / 48 / 36. */
object HeButton {
    val Large = 56.dp
    val Medium = 48.dp
    val Small = 36.dp
}
