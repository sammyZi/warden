package dev.warden.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/**
 * Warden's touch feedback: a soft radial glow that spreads from the finger and fades on release.
 * Installed as the theme's default indication, so every `clickable` gets it.
 */
fun glow(color: Color): Indication = GlowIndication(color)

private class GlowIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = GlowNode(interactionSource, color)
    override fun equals(other: Any?) = other is GlowIndication && other.color == color
    override fun hashCode() = color.hashCode()
}

private class GlowNode(private val source: InteractionSource, private val color: Color) : Modifier.Node(), DrawModifierNode {
    private val spread = Animatable(0f)
    private val strength = Animatable(0f)
    private var origin = Offset.Unspecified

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        origin = interaction.pressPosition
                        launch { strength.snapTo(1f) }
                        launch {
                            spread.snapTo(0f)
                            spread.animateTo(1f, tween(SPREAD_MS, easing = Motion.Emphasized))
                        }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel ->
                        launch { strength.animateTo(0f, tween(FADE_MS, easing = Motion.Emphasized)) }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = strength.value
        if (a <= 0f) return
        val o = if (origin.isSpecified) origin else center
        val reach = max(1f, hypot(max(o.x, size.width - o.x), max(o.y, size.height - o.y)) * spread.value)
        clipRect {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.26f * a), color.copy(alpha = 0.10f * a)), o, reach),
                radius = reach,
                center = o,
            )
        }
    }
}

private const val SPREAD_MS = 450
private const val FADE_MS = 380
