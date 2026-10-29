package dev.warden.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 2.x: text needs 4.5:1 against every surface it can sit on. */
class ContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(colors: WardenColors) {
        val surfaces =
            mapOf("background" to colors.background, "surface" to colors.surface, "variant" to colors.surfaceVariant)
        val texts = mapOf(
            "text" to colors.text, "muted" to colors.muted, "accentInk" to colors.accentInk,
            "blocked" to colors.blocked, "warning" to colors.warning,
        )
        for ((tn, t) in texts) for ((sn, s) in surfaces) {
            val r = ratio(t, s)
            assertTrue("$tn on $sn is ${"%.2f".format(r)}:1 (dark=${colors.isDark})", r >= 4.5)
        }
        assertTrue("onPrimary", ratio(colors.onPrimary, colors.primary) >= 4.5)
        assertTrue("onBlocked", ratio(colors.onBlocked, colors.blocked) >= 4.5)
    }

    @Test fun lightTextPasses() = check(LightColors)

    @Test fun darkTextPasses() = check(DarkColors)
}
