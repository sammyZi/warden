package dev.warden.design

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.roundToInt

/** Compact byte count for metadata lines: 812B, 4.1K, 1.2M. */
fun formatBytes(n: Long): String = when {
    n >= 1_048_576 -> "%.1fM".format(n / 1_048_576f)
    n >= 1024 -> "%.1fK".format(n / 1024f)
    else -> "${n}B"
}

/** A number that counts up from 0 on first show (~600ms), then animates to each new value. */
@Composable
fun CountUpText(value: Int, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val reduceMotion = Warden.reduceMotion
    val anim = remember { Animatable(if (reduceMotion) value.toFloat() else 0f) }
    LaunchedEffect(value) {
        if (reduceMotion) anim.snapTo(value.toFloat()) else anim.animateTo(value.toFloat(), Motion.emphasized(Motion.COUNT_UP))
    }
    val format = remember { NumberFormat.getIntegerInstance() }
    val final = format.format(value)
    Text(
        format.format(anim.value.roundToInt()),
        // Screen readers get the final number, not every intermediate frame.
        modifier.clearAndSetSemantics { text = AnnotatedString(final) },
        style = style.tabular(),
        color = color,
    )
}

/** One-colour line illustration plus short, human copy. */
@Composable
fun EmptyState(@DrawableRes illustration: Int, title: String, body: String, modifier: Modifier = Modifier) {
    val c = Warden.colors
    Column(
        modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(painterResource(illustration), null, Modifier.size(72.dp).padding(bottom = Spacing.l), tint = c.muted)
        Text(title, style = Warden.type.title, textAlign = TextAlign.Center)
        Text(body, style = Warden.type.body, color = c.muted, textAlign = TextAlign.Center)
    }
}

/**
 * Fades and lifts the first [Motion.STAGGER_ITEMS] list items in, [Motion.STAGGER]ms apart.
 * Pass `enabled = false` after the first appearance so scrolling never replays it.
 */
@Composable
fun Modifier.staggerIn(index: Int, enabled: Boolean): Modifier {
    if (!enabled || index >= Motion.STAGGER_ITEMS || Warden.reduceMotion) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * Motion.STAGGER.toLong())
        progress.animateTo(1f, Motion.emphasized(Motion.LONG))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 12.dp.toPx()
    }
}

/** Height of the floating tab bar (incl. system nav inset); content scrolls under it. */
val LocalBarInset = staticCompositionLocalOf { 0.dp }

/** Last item of every scrolling screen, so the end of the content clears the floating bar. */
@Composable
fun BarSpacer() {
    Spacer(Modifier.height(LocalBarInset.current + Spacing.l))
}
