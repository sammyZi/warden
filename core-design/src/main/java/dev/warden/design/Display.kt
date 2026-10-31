package dev.warden.design

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/** Pixelify Sans (SIL OFL 1.1), bundled: the display face for screen titles and a few key words. */
val Pixel = FontFamily(Font(R.font.pixelify_sans))

/**
 * Pastel left-to-right gradient for display text: sky blue, lilac, pink, peach. Deeper tones on the
 * light theme so the words keep their contrast on paper.
 */
@Composable
fun displayGradient(): Brush {
    val colors = if (Warden.colors.isDark) {
        listOf(Color(0xFF9FD3F5), Color(0xFFC6B7E8), Color(0xFFF3A9C4), Color(0xFFF7A97E))
    } else {
        listOf(Color(0xFF2F78B7), Color(0xFF7A5BB8), Color(0xFFC94A7C), Color(0xFFC9632F))
    }
    return remember(colors) { Brush.horizontalGradient(colors) }
}

/** A screen's title in the pixel display face with the pastel gradient. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = Warden.type.headline.copy(fontFamily = Pixel, fontSize = 32.sp, lineHeight = 38.sp, brush = displayGradient()),
    )
}

/**
 * Text where words wrapped in asterisks (*like this*) are set in the pixel face with the gradient, for
 * the one or two words in a sentence that carry it. The asterisks are removed.
 */
@Composable
fun highlighted(text: String): AnnotatedString {
    val brush = displayGradient()
    return remember(text, brush) {
        buildAnnotatedString {
            text.split('*').forEachIndexed { i, part ->
                if (i % 2 == 1) withStyle(SpanStyle(fontFamily = Pixel, brush = brush)) { append(part) } else append(part)
            }
        }
    }
}
