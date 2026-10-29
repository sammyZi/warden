package dev.warden.design

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** User's appearance choice in Settings. */
enum class ThemeMode { System, Light, Dark }

private val LocalWardenColors = staticCompositionLocalOf { LightColors }
private val LocalReduceMotion = staticCompositionLocalOf { false }

/** Read design tokens inside composables: `Warden.colors.primary`, `Warden.type.body`. */
object Warden {
    val colors: WardenColors
        @Composable @ReadOnlyComposable
        get() = LocalWardenColors.current

    val type: WardenType get() = WardenTypography

    /** True when the system "Remove animations" setting is on: skip decorative motion. */
    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalReduceMotion.current
}

@Composable
fun WardenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // opt-in only; off by default per the design system
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base = if (darkTheme) DarkColors else LightColors
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
        base.withDynamic(if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
    } else {
        base
    }
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(LocalWardenColors provides colors, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = Typography(
                displayMedium = WardenTypography.display,
                headlineMedium = WardenTypography.headline,
                titleLarge = WardenTypography.title,
                bodyLarge = WardenTypography.body,
                bodyMedium = WardenTypography.body,
                labelLarge = WardenTypography.label,
                labelMedium = WardenTypography.label,
            ),
            shapes = Shapes(
                small = RoundedCornerShape(Radii.small),
                medium = RoundedCornerShape(Radii.medium),
                large = RoundedCornerShape(Radii.large),
                extraLarge = RoundedCornerShape(Radii.large),
            ),
        ) {
            val indication = remember(colors.accentInk) { glow(colors.accentInk) }
            CompositionLocalProvider(
                LocalContentColor provides colors.text,
                LocalIndication provides indication,
                content = content,
            )
        }
    }
}

// Dynamic color swaps neutrals and the accent; blocked/warning keep their meaning.
private fun WardenColors.withDynamic(s: ColorScheme) = copy(
    background = s.background,
    surface = s.surface,
    surfaceVariant = s.surfaceContainerHigh,
    outline = s.outlineVariant,
    text = s.onSurface,
    muted = s.onSurfaceVariant,
    primary = s.primaryContainer,
    onPrimary = s.onPrimaryContainer,
    accentInk = s.primary,
)

private fun WardenColors.toMaterial(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = muted,
        outline = outline,
        outlineVariant = outline,
        error = blocked,
        onError = onBlocked,
    )
}
