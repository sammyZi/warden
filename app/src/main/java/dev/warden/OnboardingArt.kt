package dev.warden

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import dev.warden.design.Motion
import dev.warden.design.Warden

private enum class Ink { Line, Accent, Soft }

/** One stroke (or soft fill) of an illustration. One contour per path so PathMeasure sees all of it. */
private class Part(val path: Path, val ink: Ink, val dashed: Boolean = false)

/**
 * Line illustrations for the onboarding steps, drawn on a 240x200 grid in the design system's
 * one-accent style: structure in muted ink, the idea of each step in the accent. Strokes draw
 * themselves in one after another when a step appears.
 */
@Composable
internal fun OnboardingArt(step: Int, accent: Color, modifier: Modifier = Modifier) {
    val c = Warden.colors
    val parts = remember(step) { ART.getOrElse(step) { ART.first() }() }
    val reduceMotion = Warden.reduceMotion
    val progress = remember(step) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(step) { progress.animateTo(1f, tween(DRAW_MS, easing = Motion.Emphasized)) }
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }

    Canvas(modifier.aspectRatio(W / H)) {
        val solid = Stroke(STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val dashed = Stroke(STROKE, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f)))
        val slots = parts.size + 2f
        scale(size.width / W, size.width / W, pivot = Offset.Zero) {
            parts.forEachIndexed { i, part ->
                // Each part draws over a window of 3 slots, starting one slot after the previous.
                val t = ((progress.value - i / slots) * slots / 3f).coerceIn(0f, 1f)
                if (t == 0f) return@forEachIndexed
                when (part.ink) {
                    Ink.Soft -> drawPath(part.path, accent.copy(alpha = 0.14f * t))
                    else -> {
                        measure.setPath(part.path, false)
                        segment.reset()
                        measure.getSegment(0f, measure.length * t, segment, true)
                        drawPath(
                            segment,
                            if (part.ink == Ink.Accent) accent else c.muted,
                            style = if (part.dashed) dashed else solid,
                        )
                    }
                }
            }
        }
    }
}

private fun rr(l: Float, t: Float, r: Float, b: Float, radius: Float) =
    Path().apply { addRoundRect(RoundRect(l, t, r, b, CornerRadius(radius))) }

private fun circle(cx: Float, cy: Float, r: Float) = Path().apply { addOval(Rect(Offset(cx, cy), r)) }

private fun oval(l: Float, t: Float, r: Float, b: Float) = Path().apply { addOval(Rect(l, t, r, b)) }

private fun line(vararg xy: Float) = Path().apply {
    moveTo(xy[0], xy[1])
    for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
}

private fun path(block: Path.() -> Unit) = Path().apply(block)

/** Shield with its top centre at (cx, top), [w] wide and [h] tall. */
private fun shield(cx: Float, top: Float, w: Float, h: Float) = path {
    val half = w / 2
    moveTo(cx, top)
    lineTo(cx + half, top + h * 0.16f)
    lineTo(cx + half, top + h * 0.48f)
    cubicTo(cx + half, top + h * 0.76f, cx + half * 0.5f, top + h * 0.92f, cx, top + h)
    cubicTo(cx - half * 0.5f, top + h * 0.92f, cx - half, top + h * 0.76f, cx - half, top + h * 0.48f)
    lineTo(cx - half, top + h * 0.16f)
    close()
}

/** Padlock: body plus shackle, centred on [cx] with the body's top at [top]. */
private fun lock(cx: Float, top: Float, w: Float): List<Part> {
    val h = w * 0.9f
    val s = w * 0.3f
    return listOf(
        Part(rr(cx - w / 2, top, cx + w / 2, top + h, 4f), Ink.Soft),
        Part(path {
            moveTo(cx - s, top)
            lineTo(cx - s, top - s * 0.7f)
            cubicTo(cx - s, top - s * 2.4f, cx + s, top - s * 2.4f, cx + s, top - s * 0.7f)
            lineTo(cx + s, top)
        }, Ink.Accent),
        Part(rr(cx - w / 2, top, cx + w / 2, top + h, 4f), Ink.Accent),
    )
}

private val ART: List<() -> List<Part>> = listOf(
    // 1. A local VPN: app traffic funnels through a shield on the phone, then out to the world.
    {
        listOf(
            Part(rr(64f, 16f, 144f, 184f, 16f), Ink.Line),
            Part(line(92f, 28f, 116f, 28f), Ink.Line),
            Part(rr(76f, 44f, 92f, 60f, 5f), Ink.Line),
            Part(rr(96f, 44f, 112f, 60f, 5f), Ink.Soft),
            Part(rr(96f, 44f, 112f, 60f, 5f), Ink.Line),
            Part(rr(116f, 44f, 132f, 60f, 5f), Ink.Line),
            Part(path { moveTo(84f, 62f); cubicTo(84f, 82f, 104f, 80f, 104f, 98f) }, Ink.Accent, dashed = true),
            Part(line(104f, 62f, 104f, 98f), Ink.Accent, dashed = true),
            Part(path { moveTo(124f, 62f); cubicTo(124f, 82f, 104f, 80f, 104f, 98f) }, Ink.Accent, dashed = true),
            Part(shield(104f, 100f, 40f, 50f), Ink.Soft),
            Part(shield(104f, 100f, 40f, 50f), Ink.Accent),
            Part(line(95f, 124f, 102f, 131f, 114f, 117f), Ink.Accent),
            Part(line(124f, 126f, 178f, 126f), Ink.Accent, dashed = true),
            Part(circle(196f, 126f, 18f), Ink.Line),
            Part(oval(189f, 108f, 203f, 144f), Ink.Line),
            Part(line(178f, 126f, 214f, 126f), Ink.Line),
        )
    },
    // 2. Nothing leaves: the phone sits inside a boundary; the cloud stays outside, crossed out.
    {
        listOf(
            Part(circle(104f, 100f, 84f), Ink.Accent, dashed = true),
            Part(rr(80f, 40f, 128f, 160f, 12f), Ink.Line),
            Part(line(98f, 50f, 110f, 50f), Ink.Line),
        ) + lock(104f, 100f, 22f) + listOf(
            Part(path {
                moveTo(182f, 62f)
                cubicTo(172f, 62f, 172f, 48f, 180f, 47f)
                cubicTo(181f, 36f, 195f, 33f, 200f, 41f)
                cubicTo(206f, 33f, 219f, 38f, 217f, 49f)
                cubicTo(226f, 50f, 225f, 62f, 216f, 62f)
                close()
            }, Ink.Line),
            Part(line(174f, 72f, 224f, 30f), Ink.Accent),
        )
    },
    // 3. Where, never what: the address is readable, the envelope stays sealed.
    {
        listOf(
            Part(rr(36f, 62f, 196f, 168f, 10f), Ink.Line),
            Part(line(36f, 72f, 116f, 122f, 196f, 72f), Ink.Line),
        ) + lock(116f, 118f, 22f) + listOf(
            Part(line(150f, 48f, 168f, 38f), Ink.Line),
            Part(rr(168f, 18f, 228f, 50f, 7f), Ink.Soft),
            Part(rr(168f, 18f, 228f, 50f, 7f), Ink.Accent),
            Part(line(178f, 29f, 218f, 29f), Ink.Accent),
            Part(line(178f, 39f, 204f, 39f), Ink.Accent),
        )
    },
    // 4. Allow the VPN: one shield, one check, one switch.
    {
        listOf(
            Part(shield(112f, 22f, 104f, 152f), Ink.Soft),
            Part(shield(112f, 22f, 104f, 152f), Ink.Accent),
            Part(line(90f, 98f, 106f, 114f, 136f, 82f), Ink.Accent),
            Part(rr(168f, 148f, 212f, 172f, 12f), Ink.Line),
            Part(circle(200f, 160f, 7f), Ink.Accent),
        )
    },
    // 5. One quiet notification, with its Stop button.
    {
        listOf(
            Part(path {
                moveTo(60f, 196f)
                lineTo(60f, 36f)
                cubicTo(60f, 24f, 70f, 16f, 82f, 16f)
                lineTo(158f, 16f)
                cubicTo(170f, 16f, 180f, 24f, 180f, 36f)
                lineTo(180f, 196f)
            }, Ink.Line),
            Part(line(76f, 30f, 92f, 30f), Ink.Line),
            Part(circle(164f, 30f, 3f), Ink.Line),
            Part(rr(72f, 46f, 168f, 98f, 12f), Ink.Soft),
            Part(rr(72f, 46f, 168f, 98f, 12f), Ink.Accent),
            Part(circle(92f, 72f, 9f), Ink.Accent),
            Part(line(110f, 64f, 154f, 64f), Ink.Line),
            Part(line(110f, 78f, 138f, 78f), Ink.Line),
            Part(rr(128f, 106f, 168f, 126f, 10f), Ink.Accent),
            Part(line(76f, 146f, 164f, 146f), Ink.Line),
            Part(line(76f, 162f, 140f, 162f), Ink.Line),
        )
    },
    // 6. Keep running: a battery that stays topped up, on a loop.
    {
        listOf(
            Part(path { arcTo(Rect(102f, 42f, 138f, 78f), 200f, 280f, true) }, Ink.Accent),
            Part(line(114.4f, 82.8f, 111f, 75.6f, 119f, 74.9f), Ink.Accent),
            Part(rr(56f, 92f, 176f, 156f, 14f), Ink.Line),
            Part(rr(176f, 112f, 188f, 136f, 4f), Ink.Line),
            Part(rr(68f, 104f, 92f, 144f, 6f), Ink.Soft),
            Part(rr(68f, 104f, 92f, 144f, 6f), Ink.Accent),
            Part(rr(98f, 104f, 122f, 144f, 6f), Ink.Soft),
            Part(rr(98f, 104f, 122f, 144f, 6f), Ink.Accent),
            Part(rr(128f, 104f, 152f, 144f, 6f), Ink.Soft),
            Part(rr(128f, 104f, 152f, 144f, 6f), Ink.Accent),
        )
    },
)

private const val W = 240f
private const val H = 200f
private const val STROKE = 2.5f
private const val DRAW_MS = 1100
