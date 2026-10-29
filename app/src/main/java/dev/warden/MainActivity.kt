package dev.warden

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.warden.design.Motion
import dev.warden.design.ThemeMode
import dev.warden.design.Warden
import dev.warden.design.WardenTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferHighestRefreshRate()
        // ponytail: one boolean in SharedPreferences; DataStore arrives with real settings in M7.
        val prefs = getSharedPreferences("warden", MODE_PRIVATE)

        setContent {
            var themeMode by remember {
                mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null).orEmpty()) }.getOrDefault(ThemeMode.System))
            }
            val dark = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Status/nav bar icons follow the in-app theme, not just the system one.
            LaunchedEffect(dark) {
                val bars = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(bars, bars)
            }
            WardenTheme(darkTheme = dark) {
                var onboarded by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false)) }
                AnimatedContent(
                    onboarded,
                    Modifier.fillMaxSize().background(Warden.colors.background),
                    transitionSpec = { fadeIn(Motion.emphasized(Motion.LONG)) togetherWith fadeOut(Motion.emphasized(Motion.SHORT)) },
                    label = "screen",
                ) { done ->
                    if (done) {
                        Shell(
                            themeMode = themeMode,
                            onThemeMode = {
                                themeMode = it
                                prefs.edit().putString(KEY_THEME, it.name).apply()
                            },
                            onReplayIntro = { onboarded = false },
                        )
                    } else {
                        Onboarding(onDone = {
                            prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                            onboarded = true
                        })
                    }
                }
            }
        }
    }

    /**
     * Ask for the panel's fastest mode at the current resolution (120Hz on most recent phones).
     * Only applies while this window is visible; the VPN service never renders.
     */
    private fun preferHighestRefreshRate() {
        val display = if (Build.VERSION.SDK_INT >= 30) display else
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        val current = display?.mode ?: return
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = best.modeId
            preferredRefreshRate = best.refreshRate
        }
    }

    private companion object {
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_THEME = "theme"
    }
}
