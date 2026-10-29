package dev.warden.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class PowerState { Off, Connecting, On }

/**
 * The signature control. Off: an ink ring. Connecting: a rotating arc. On: filled green with a
 * soft ring that pulses out every ~3s. The infinite animations only exist in the state that needs
 * them, so an idle dashboard costs no frames.
 */
@Composable
fun PowerButton(state: PowerState, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val c = Warden.colors
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !Warden.reduceMotion) 0.96f else 1f, Motion.press(), label = "press")
    val fill by animateColorAsState(
        if (state == PowerState.On) c.primary else Color.Transparent,
        Motion.emphasized(Motion.LONG),
        label = "fill",
    )
    val ink by animateColorAsState(
        when (state) {
            PowerState.Off -> c.text
            PowerState.Connecting -> c.accentInk
            PowerState.On -> c.onPrimary
        },
        Motion.emphasized(Motion.LONG),
        label = "ink",
    )
    val stateLabel = stringResource(
        when (state) {
            PowerState.Off -> R.string.power_state_off
            PowerState.Connecting -> R.string.power_state_connecting
            PowerState.On -> R.string.power_state_on
        },
    )
    val description = stringResource(R.string.power_description)

    // Extra room around the circle for the pulse ring.
    Box(modifier.size(size * PULSE_ROOM), contentAlignment = Alignment.Center) {
        if (state == PowerState.On && !Warden.reduceMotion) PulseRing(c.accentInk)
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .clickable(
                    source,
                    indication = remember(state, c) { glow(if (state == PowerState.On) c.onPrimary else c.accentInk) },
                    role = Role.Button,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
                .semantics {
                    contentDescription = description
                    stateDescription = stateLabel
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            val ring = c.text
            val track = c.outline
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 2.dp.toPx()
                drawCircle(fill)
                when (state) {
                    PowerState.Off -> drawCircle(ring, radius = (this.size.minDimension - stroke) / 2, style = Stroke(stroke))
                    PowerState.Connecting -> drawCircle(track, radius = (this.size.minDimension - stroke) / 2, style = Stroke(stroke))
                    PowerState.On -> Unit
                }
            }
            if (state == PowerState.Connecting) ConnectingArc(c.accentInk, Warden.reduceMotion)
            Icon(painterResource(R.drawable.ph_power), null, Modifier.size(size * ICON_RATIO), tint = ink)
        }
    }
}

@Composable
private fun ConnectingArc(color: Color, reduceMotion: Boolean) {
    // Rotation is read inside the draw lambda, so spinning only redraws.
    val rotation = if (reduceMotion) {
        null
    } else {
        rememberInfiniteTransition("connecting").animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), "spin")
    }
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 3.dp.toPx()
        drawArc(
            color,
            startAngle = (rotation?.value ?: 0f) - 90f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun PulseRing(color: Color) {
    val transition = rememberInfiniteTransition("pulse")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3000
                0f at 0
                1f at 1600 using Motion.Emphasized
                1f at 3000
            },
            RepeatMode.Restart,
        ),
        label = "pulse",
    )
    Canvas(Modifier.fillMaxSize()) {
        val base = size.minDimension / PULSE_ROOM / 2
        drawCircle(
            color.copy(alpha = 0.35f * (1f - p)),
            radius = base * (1f + (PULSE_ROOM - 1f) * p),
            style = Stroke(2.dp.toPx()),
        )
    }
}

private const val PULSE_ROOM = 1.3f
private const val ICON_RATIO = 0.3f
