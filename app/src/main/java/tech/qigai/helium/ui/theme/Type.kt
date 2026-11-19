@file:OptIn(ExperimentalTextApi::class)

package tech.qigai.helium.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import tech.qigai.helium.R

/**
 * Bundled OFL fonts (see third_party/fonts):
 *  - Noto Sans SC (variable wght) — default UI family, covers CJK + Latin.
 *  - Google Sans Flex (variable, ROND 100) — display numerals and Latin headings (18:40, ¥1,299, 22°).
 */
private fun noto(weight: Int) = Font(
    R.font.noto_sans_sc,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun flex(weight: Int) = Font(
    R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("ROND", 100f),
    ),
)

val HeSans: FontFamily = FontFamily(noto(400), noto(500), noto(600), noto(700))
val HeDisplay: FontFamily = FontFamily(flex(400), flex(500), flex(600), flex(700))

/** Display numerals: tabular figures + rounded Google Sans Flex. */
object HeNum {
    fun style(size: Int, weight: FontWeight = FontWeight.Medium) = TextStyle(
        fontFamily = HeDisplay,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * 1.1).sp,
        fontFeatureSettings = "tnum",
        letterSpacing = (-0.01).em,
    )
}

private fun s(size: Int, line: Int, weight: FontWeight, family: FontFamily = HeSans) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

val HeTypography = Typography(
    displayLarge = s(56, 64, FontWeight.Bold),
    displayMedium = s(48, 56, FontWeight.Bold),
    displaySmall = s(40, 48, FontWeight.Bold),
    headlineLarge = s(32, 40, FontWeight.Bold),
    headlineMedium = s(28, 36, FontWeight.Bold),
    headlineSmall = s(24, 32, FontWeight.Bold),
    titleLarge = s(20, 28, FontWeight.Bold),
    titleMedium = s(17, 24, FontWeight.SemiBold),
    titleSmall = s(15, 22, FontWeight.SemiBold),
    bodyLarge = s(16, 24, FontWeight.Normal),
    bodyMedium = s(15, 22, FontWeight.Normal),
    bodySmall = s(13, 18, FontWeight.Normal),
    labelLarge = s(15, 20, FontWeight.SemiBold),
    labelMedium = s(13, 18, FontWeight.Medium),
    labelSmall = s(12, 16, FontWeight.Medium),
)
