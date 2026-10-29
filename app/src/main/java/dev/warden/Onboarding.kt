package dev.warden

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import dev.warden.design.Tile
import dev.warden.map.SampleGlobe
import androidx.annotation.DrawableRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.warden.design.ButtonKind
import dev.warden.design.Motion
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.WardenButton

private enum class Step(@StringRes val title: Int, @StringRes val body: Int, @StringRes val action: Int) {
    Local(R.string.onb_local_title, R.string.onb_local_body, R.string.onb_next),
    Private(R.string.onb_private_title, R.string.onb_private_body, R.string.onb_next),
    NoDecryption(R.string.onb_tls_title, R.string.onb_tls_body, R.string.onb_next),
    Vpn(R.string.onb_vpn_title, R.string.onb_vpn_body, R.string.onb_vpn_action),
    Notifications(R.string.onb_notif_title, R.string.onb_notif_body, R.string.onb_notif_action),
    Battery(R.string.onb_battery_title, R.string.onb_battery_body, R.string.onb_finish),
}

/** Three intro screens, then VPN consent, notification permission and battery guidance. */
@Composable
internal fun Onboarding(onDone: () -> Unit) {
    val context = LocalContext.current
    val c = Warden.colors
    val steps = remember { Step.entries.filter { it != Step.Notifications || Build.VERSION.SDK_INT >= 33 } }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    val next = { if (index == steps.lastIndex) onDone() else index++ }
    BackHandler(enabled = index > 0) { index-- }

    // Permission steps move on whatever the answer: everything can be granted later.
    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { next() }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { next() }
    val reduceMotion = Warden.reduceMotion

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.xl)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            steps.indices.forEach {
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(if (it <= index) c.accentInk else c.outline, RoundedCornerShape(2.dp)),
                )
            }
        }
        AnimatedContent(
            index,
            Modifier.weight(1f),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                if (reduceMotion) {
                    fadeIn(Motion.emphasized(0)) togetherWith fadeOut(Motion.emphasized(0))
                } else {
                    (slideInHorizontally(Motion.emphasized(Motion.LONG)) { dir * it / 6 } + fadeIn(Motion.emphasized(Motion.LONG)))
                        .togetherWith(
                            slideOutHorizontally(Motion.emphasized(Motion.LONG)) { -dir * it / 8 } +
                                fadeOut(Motion.emphasized(Motion.SHORT)),
                        )
                }
            },
            label = "onboarding",
        ) { i ->
            val s = steps[i]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                OnboardingHero(s, Modifier.fillMaxWidth().height(300.dp))
                Spacer(Modifier.height(Spacing.xxl))
                Text(stringResource(s.title), style = Warden.type.headline)
                Spacer(Modifier.height(Spacing.m))
                Text(
                    if (s == Step.Battery) {
                        stringResource(s.body, Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
                    } else {
                        stringResource(s.body)
                    },
                    style = Warden.type.body,
                    color = c.muted,
                )
            }
        }

        if (step == Step.Battery) {
            WardenButton(
                stringResource(R.string.onb_battery_action),
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Tonal,
            )
            Spacer(Modifier.height(Spacing.m))
        }
        WardenButton(
            stringResource(step.action),
            onClick = {
                when (step) {
                    Step.Vpn -> VpnService.prepare(context)?.let(vpnConsent::launch) ?: next()
                    Step.Notifications -> notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> next()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        val secondary = when (step) {
            Step.Local, Step.Private, Step.NoDecryption -> R.string.onb_skip
            Step.Vpn, Step.Notifications -> R.string.onb_not_now
            Step.Battery -> null
        }
        if (secondary != null) {
            WardenButton(
                stringResource(secondary),
                onClick = { if (secondary == R.string.onb_skip) index = steps.indexOf(Step.Vpn) else next() },
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Text,
            )
        } else {
            Spacer(Modifier.height(48.dp)) // keep the primary button from jumping
        }
    }
}

/**
 * Each step shows the real thing where it can (the live globe, actual log rows) and a lime-tinted
 * 3D object (3dicons, CC0) where an object says it better. Objects float gently unless motion is off.
 */
@Composable
private fun OnboardingHero(step: Step, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (step) {
            Step.Local -> SampleGlobe(Modifier.fillMaxHeight().aspectRatio(1f))
            Step.Private -> FloatingObject(R.drawable.onb_lock)
            Step.NoDecryption -> SampleLog()
            Step.Vpn -> FloatingObject(R.drawable.onb_shield)
            Step.Notifications -> FloatingObject(R.drawable.onb_bell)
            Step.Battery -> FloatingObject(R.drawable.onb_battery)
        }
    }
}

@Composable
private fun FloatingObject(@DrawableRes art: Int) {
    val c = Warden.colors
    val bob = if (Warden.reduceMotion) {
        null
    } else {
        rememberInfiniteTransition("float")
            .animateFloat(-6f, 6f, infiniteRepeatable(tween(2600, easing = Motion.Emphasized), RepeatMode.Reverse), "bob")
    }
    Box(contentAlignment = Alignment.Center) {
        // Soft lime light behind the object.
        Box(
            Modifier
                .size(220.dp)
                .background(Brush.radialGradient(listOf(c.primary.copy(alpha = 0.22f), c.primary.copy(alpha = 0f))), CircleShape),
        )
        Image(
            painterResource(art),
            null,
            Modifier.size(200.dp).graphicsLayer { translationY = (bob?.value ?: 0f) * density },
        )
    }
}

/** Three real-looking log rows: Warden sees names like these, never what's inside. */
@Composable
private fun SampleLog() {
    val c = Warden.colors
    Tile(Modifier.fillMaxWidth()) {
        listOf(
            Triple("graph.facebook.com", "Instagram · TCP", true),
            Triple("api.weather.com", "Weather · TCP", false),
            Triple("app-measurement.com", "Weather · UDP", true),
            Triple("mtalk.google.com", "Play services · TCP", false),
        ).forEachIndexed { i, (domain, meta, blocked) ->
            if (i > 0) HorizontalDivider(color = c.outline)
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(domain, style = Warden.type.body, color = if (blocked) c.blocked else c.text, maxLines = 1)
                    Text(meta.uppercase(), style = Warden.type.caption, color = c.muted)
                }
                if (blocked) {
                    Text(
                        stringResource(R.string.onb_blocked).uppercase(),
                        Modifier
                            .background(c.blocked.copy(alpha = 0.16f), RoundedCornerShape(50))
                            .padding(horizontal = Spacing.s, vertical = 2.dp),
                        style = Warden.type.caption,
                        color = c.blocked,
                    )
                }
            }
        }
    }
}
