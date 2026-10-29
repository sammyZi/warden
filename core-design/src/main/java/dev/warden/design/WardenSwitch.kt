package dev.warden.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Custom switch: 52x32 track inside a 56x48 touch target. The thumb rides a spring
 * (damping 0.6) so it overshoots slightly and settles. Off = outlined track, on = filled accent.
 */
@Composable
fun WardenSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Warden.colors
    val haptic = LocalHapticFeedback.current
    val t by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (Warden.reduceMotion) snap() else Motion.press(),
        label = "thumb",
    )
    val track by animateColorAsState(
        targetValue = if (checked) c.primary else Color.Transparent,
        animationSpec = Motion.emphasized(Motion.SHORT),
        label = "track",
    )
    Box(
        modifier
            .size(56.dp, 48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .semantics { this.contentDescription = contentDescription }
            .toggleable(checked, enabled = enabled, role = Role.Switch, interactionSource = null, indication = null) {
                haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onCheckedChange(it)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(52.dp, 32.dp)) {
            val r = size.height / 2
            val fraction = t.coerceIn(0f, 1f)
            drawRoundRect(track, cornerRadius = CornerRadius(r))
            if (fraction < 1f) {
                drawRoundRect(
                    c.muted.copy(alpha = 1f - fraction),
                    topLeft = Offset(0.75.dp.toPx(), 0.75.dp.toPx()),
                    size = size.copy(size.width - 1.5.dp.toPx(), size.height - 1.5.dp.toPx()),
                    cornerRadius = CornerRadius(r),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            // Thumb grows from 16dp to 24dp; t (not fraction) drives position so the spring overshoot shows.
            val thumb = (8.dp + 4.dp * fraction).toPx()
            val inset = 4.dp.toPx() + thumb
            drawCircle(
                lerp(c.muted, c.onPrimary, fraction),
                radius = thumb,
                center = Offset(inset + (size.width - 2 * inset) * t, center.y),
            )
        }
    }
}
