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
import androidx.compose.animation.animateColorAsState
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
import dev.warden.design.WardenColors

private enum class Step(@StringRes val title: Int, @StringRes val body: Int, @StringRes val action: Int) {
    Local(R.string.onb_local_title, R.string.onb_local_body, R.string.onb_next),
    Private(R.string.onb_private_title, R.string.onb_private_body, R.string.onb_next),
    NoDecryption(R.string.onb_tls_title, R.string.onb_tls_body, R.string.onb_next),
    Vpn(R.string.onb_vpn_title, R.string.onb_vpn_body, R.string.onb_vpn_action),
    Notifications(R.string.onb_notif_title, R.string.onb_notif_body, R.string.onb_notif_action),
    Battery(R.string.onb_battery_title, R.string.onb_battery_body, R.string.onb_finish),
}

/** Each step gets its own hue from the palette; the VPN step keeps the protected green. */
private fun Step.hue(c: WardenColors) = when (this) {
    Step.Local -> c.palette[0]
    Step.Private -> c.palette[3]
    Step.NoDecryption -> c.palette[1]
    Step.Vpn -> c.accentInk
    Step.Notifications -> c.palette[4]
    Step.Battery -> c.palette[2]
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
        val barHue by animateColorAsState(step.hue(c), Motion.emphasized(Motion.LONG), label = "bar")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            steps.indices.forEach {
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(if (it <= index) barHue else c.outline, RoundedCornerShape(2.dp)),
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
                OnboardingArt(s.ordinal, s.hue(c), Modifier.fillMaxWidth())
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
