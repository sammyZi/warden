package dev.warden.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class WardenColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val text: Color,
    val muted: Color,
    /** Lime: the one main colour. Fills only (buttons, pills, switch track, power button). */
    val primary: Color,
    val onPrimary: Color,
    /** The accent as text, strokes and icons on the background: olive on light (lime vanishes there), lime on dark. */
    val accentInk: Color,
    val blocked: Color,
    val onBlocked: Color,
    val warning: Color,
    /**
     * Categorical hues for identity (countries, stat squares, illustrations), in fixed order. Kept clear of
     * the status colours (protected green, blocked vermilion, warning amber). Validated with the
     * dataviz six-checks script on both surfaces. Graphics only: text stays in text tokens.
     */
    val palette: List<Color>,
    val isDark: Boolean,
)

// Light muted/blocked/warning are darkened so they pass 4.5:1 as text on every surface; lime is
// fill-only on light (1.1:1 against the background), so text-weight accents use accentInk.
// ContrastTest guards this; docs/design-system.md has the table.
val LightColors = WardenColors(
    background = Color(0xFFF4F4EE),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EAE2),
    outline = Color(0xFFD7D8CE),
    text = Color(0xFF121310),
    muted = Color(0xFF5E605A),
    primary = Color(0xFFD8F23C),
    onPrimary = Color(0xFF121310),
    accentInk = Color(0xFF4E6300),
    blocked = Color(0xFFB9391F),
    onBlocked = Color(0xFFFFFFFF),
    warning = Color(0xFF876000),
    palette = listOf(Color(0xFF2E6BD1), Color(0xFFC2408A), Color(0xFFA8651A), Color(0xFF6E4FD8), Color(0xFF0093AE)),
    isDark = false,
)

val DarkColors = WardenColors(
    background = Color(0xFF0E0F0D),
    surface = Color(0xFF18191A),
    surfaceVariant = Color(0xFF232427),
    outline = Color(0xFF2C2E30),
    text = Color(0xFFF3F4EE),
    muted = Color(0xFF9A9C97),
    primary = Color(0xFFD8F23C),
    onPrimary = Color(0xFF0E0F0D),
    accentInk = Color(0xFFD8F23C),
    blocked = Color(0xFFFF6B4A),
    onBlocked = Color(0xFF0E0F0D),
    warning = Color(0xFFF2BC45),
    palette = listOf(Color(0xFF4A86E3), Color(0xFFD55B9F), Color(0xFFBB772B), Color(0xFF8A73EA), Color(0xFF15A5C0)),
    isDark = true,
)

/** All fonts are bundled TTFs in res/font (OFL): no downloadable fonts, no network. */
val Rubik = FontFamily(
    Font(R.font.rubik, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.rubik, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.rubik, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

@Immutable
data class WardenType(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    /** Small uppercase Rubik for section labels and metadata. */
    val caption: TextStyle,
)

val WardenTypography = WardenType(
    display = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 44.sp, lineHeight = 48.sp),
    headline = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    title = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    body = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    label = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    caption = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
)

/** Tabular figures, so counters don't jitter as digits change. Use on every number. */
fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radii {
    val small = 10.dp // chips, inputs
    val medium = 16.dp // buttons, cards
    val large = 28.dp // sheets
}

object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val SHORT = 150
    const val MEDIUM = 200
    const val LONG = 320
    const val COUNT_UP = 600
    const val STAGGER = 30
    const val STAGGER_ITEMS = 6

    fun <T> emphasized(durationMillis: Int = MEDIUM, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis, Emphasized)

    /** Press feel for buttons and the switch thumb. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 500f)
}
