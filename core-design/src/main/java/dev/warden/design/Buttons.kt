package dev.warden.design

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ButtonKind { Primary, Tonal, Destructive, Text }

/**
 * Solid, flat button. 52dp tall (48dp for [ButtonKind.Text]), 16dp radius, no ripple or shadow:
 * the press feel is a 0.96 spring scale plus a light haptic.
 */
@Composable
fun WardenButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val c = Warden.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !Warden.reduceMotion) 0.96f else 1f,
        animationSpec = Motion.press(),
        label = "press",
    )
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(Radii.medium)
    val fill = when (kind) {
        ButtonKind.Primary -> c.primary
        ButtonKind.Tonal -> c.surfaceVariant
        ButtonKind.Destructive, ButtonKind.Text -> Color.Transparent
    }
    val content = when (kind) {
        ButtonKind.Primary -> c.onPrimary
        ButtonKind.Tonal -> c.text
        ButtonKind.Destructive -> c.blocked
        ButtonKind.Text -> c.accentInk
    }
    val border = if (kind == ButtonKind.Destructive) BorderStroke(1.5.dp, c.blocked) else null

    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else DISABLED_ALPHA
            }
            .heightIn(min = if (kind == ButtonKind.Text) 48.dp else 52.dp)
            .clip(shape)
            .background(fill)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(source, indication = remember(content) { glow(content) }, enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = if (kind == ButtonKind.Text) Spacing.m else Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(20.dp), tint = content)
        Text(text, style = Warden.type.body.copy(fontWeight = FontWeight.Medium), color = content)
    }
}

internal const val DISABLED_ALPHA = 0.38f
