package dev.warden.design

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** A bento tile: surface fill, 16dp corners, optional tap with the theme's glow. */
@Composable
fun Tile(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radii.medium))
            .background(Warden.colors.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        content = content,
    )
}

/** Small icon on a soft disc of its own hue: where the palette shows up in the chrome. */
@Composable
fun IconBadge(@DrawableRes icon: Int, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(36.dp).background(color.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = color)
    }
}

/** Pill with a live dot. On = lime fill with ink, dot breathing; off = quiet neutral. */
@Composable
fun StatusChip(on: Boolean, label: String, modifier: Modifier = Modifier) {
    val c = Warden.colors
    val pulse = if (on && !Warden.reduceMotion) {
        rememberInfiniteTransition("chip").animateFloat(0.3f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), "dot").value
    } else {
        1f
    }
    val ink = if (on) c.onPrimary else c.muted
    Row(
        modifier
            .background(if (on) c.primary else c.surfaceVariant, RoundedCornerShape(50))
            .padding(horizontal = Spacing.m, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(ink.copy(alpha = pulse), CircleShape))
        Text(label.uppercase(), style = Warden.type.caption, color = if (on) c.onPrimary else c.text)
    }
}

/** Section heading: small uppercase monospace, muted. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = Warden.type.caption, color = Warden.colors.muted)
}

/** Filter chip: lime fill with ink when selected, hairline outline otherwise. 36dp tall in a 48dp target. */
@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Warden.colors
    Box(modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) c.primary else Color.Transparent)
                .border(1.dp, if (selected) c.primary else c.outline, RoundedCornerShape(50))
                .selectable(selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            style = Warden.type.label,
            color = if (selected) c.onPrimary else c.text,
        )
    }
}

/** Square stat on a soft tint of its hue: icon, big tabular number, small caption. */
@Composable
fun StatSquare(@DrawableRes icon: Int, hue: Color, value: Int, label: String, modifier: Modifier = Modifier) {
    val c = Warden.colors
    Column(
        modifier
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(Radii.medium))
            .background(hue.copy(alpha = if (c.isDark) 0.22f else 0.13f))
            .padding(Spacing.m),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(painterResource(icon), null, Modifier.size(18.dp), tint = hue)
        Column {
            // Shrinks to fit the square rather than clipping ("1.1K" at large font scales).
            CountUpText(
                value,
                Warden.type.headline,
                compact = true,
                autoSize = TextAutoSize.StepBased(12.sp, Warden.type.headline.fontSize),
            )
            Text(
                label.uppercase(),
                style = Warden.type.caption.copy(fontSize = 9.sp, letterSpacing = 0.3.sp),
                color = c.muted,
                maxLines = 1,
            )
        }
    }
}

/**
 * Activity line: a smoothed curve with a soft gradient beneath, faint guide lines, and a glowing dot
 * on the latest value. Draws in left to right on first show. One hue only.
 */
@Composable
fun Sparkline(
    values: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    endLabel: String? = null,
) {
    val c = Warden.colors
    val reduceMotion = Warden.reduceMotion
    val reveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, Motion.emphasized(900)) }
    val line = remember { Path() }
    val fill = remember { Path() }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (values.size < 2) return@Canvas
            val max = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
            val step = size.width / (values.size - 1)
            val top = 10.dp.toPx()
            val bottom = size.height - 2.dp.toPx()
            fun y(v: Int) = top + (bottom - top) * (1f - v / max)

            // Faint dashed guides at 0, 50% and 100% of the peak.
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
            listOf(top, (top + bottom) / 2, bottom).forEach {
                drawLine(c.outline, Offset(0f, it), Offset(size.width, it), 1.dp.toPx(), pathEffect = dash)
            }

            // Smooth curve: cubic segments with control points at the horizontal midpoints.
            line.reset()
            line.moveTo(0f, y(values[0]))
            for (i in 1 until values.size) {
                val x0 = (i - 1) * step
                val x1 = i * step
                val mid = (x0 + x1) / 2
                line.cubicTo(mid, y(values[i - 1]), mid, y(values[i]), x1, y(values[i]))
            }
            fill.reset()
            fill.addPath(line)
            fill.lineTo(size.width, bottom)
            fill.lineTo(0f, bottom)
            fill.close()

            clipRect(right = size.width * reveal.value) {
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f)), top, bottom))
                drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (reveal.value == 1f) {
                val last = Offset((values.size - 1) * step, y(values.last()))
                drawCircle(color.copy(alpha = 0.22f), 9.dp.toPx(), last)
                drawCircle(color, 4.dp.toPx(), last)
                drawCircle(c.background, 1.5.dp.toPx(), last)
            }
        }
        if (startLabel != null || endLabel != null) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                Text(startLabel.orEmpty(), Modifier.weight(1f), style = Warden.type.caption, color = c.muted)
                Text(endLabel.orEmpty(), style = Warden.type.caption, color = c.muted)
            }
        }
    }
}

/** Mutually exclusive options as a wrapping row of chips. */
@Composable
fun ChoiceChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        options.forEachIndexed { i, label -> FilterChip(label, i == selected, { onSelect(i) }) }
    }
}

/** Segmented control: a lime pill slides between options. For small, exclusive settings. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Warden.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(c.surfaceVariant)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(
            segment * selected,
            if (Warden.reduceMotion) snap() else spring(dampingRatio = 0.75f, stiffness = 420f),
            label = "segment",
        )
        Box(Modifier.offset(x = x).width(segment).fillMaxHeight().background(c.primary, RoundedCornerShape(50)))
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .selectable(i == selected, role = Role.RadioButton) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = Warden.type.label, color = if (i == selected) c.onPrimary else c.text)
                }
            }
        }
    }
}
