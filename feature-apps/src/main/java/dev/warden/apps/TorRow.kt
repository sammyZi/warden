package dev.warden.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.warden.design.ButtonKind
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.WardenButton
import dev.warden.design.WardenSwitch
import dev.warden.rules.Tor
import dev.warden.design.R as DesignR

/** What Orbot last told us. Unknown until it answers a status request. */
private enum class OrbotState { Missing, Unknown, Off, Starting, On }

/** "Route through Tor" for one app, with Orbot's state and a way to get or start it. */
@Composable
internal fun TorRow(uid: Int, name: String) {
    val c = Warden.colors
    val context = LocalContext.current
    val apps by Tor.apps.collectAsState()
    val on = uid in apps
    val orbot = rememberOrbotState()

    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(DesignR.drawable.ph_lock_key_light), null, Modifier.size(20.dp), tint = if (on) c.accentInk else c.text)
        Column(Modifier.weight(1f).padding(start = Spacing.m)) {
            Text(stringResource(R.string.tor_route), style = Warden.type.body)
            Text(
                stringResource(
                    when {
                        orbot == OrbotState.Missing -> R.string.tor_needs_orbot
                        !on -> R.string.tor_off
                        orbot == OrbotState.On -> R.string.tor_on
                        orbot == OrbotState.Starting -> R.string.tor_starting
                        else -> R.string.tor_waiting
                    },
                ).uppercase(),
                style = Warden.type.caption,
                color = if (on && orbot != OrbotState.On) c.warning else if (on) c.accentInk else c.muted,
            )
        }
        WardenSwitch(
            on,
            { Tor.set(uid, it) },
            contentDescription = stringResource(R.string.tor_route_for, name),
            enabled = orbot != OrbotState.Missing || on,
        )
    }
    when {
        orbot == OrbotState.Missing -> {
            Text(stringResource(R.string.tor_get_orbot_body), style = Warden.type.label, color = c.muted)
            WardenButton(
                stringResource(R.string.tor_get_orbot),
                { openOrbotListing(context) },
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Tonal,
            )
        }
        on && orbot != OrbotState.On -> {
            // Fail closed: until Tor is up, this app has no connection rather than going direct.
            Text(stringResource(R.string.tor_offline_note, name), style = Warden.type.label, color = c.muted)
            WardenButton(
                stringResource(R.string.tor_start_orbot),
                { requestOrbot(context) },
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Tonal,
            )
        }
    }
}

/**
 * Asks Orbot for its status (which also starts Tor if it's allowed to start in the background) and
 * listens for the answer while this is on screen. Orbot also reports its SOCKS port, which we keep.
 */
@Composable
private fun rememberOrbotState(): OrbotState {
    val context = LocalContext.current
    val installed = remember { runCatching { context.packageManager.getPackageInfo(Tor.ORBOT, 0) }.isSuccess }
    var state by remember { mutableStateOf(if (installed) OrbotState.Unknown else OrbotState.Missing) }
    if (!installed) return state
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getStringExtra(EXTRA_PACKAGE) != null && intent.getStringExtra(EXTRA_PACKAGE) != ctx.packageName) return
                state = when (intent.getStringExtra(EXTRA_STATUS)) {
                    "ON" -> OrbotState.On
                    "STARTING" -> OrbotState.Starting
                    else -> OrbotState.Off
                }
                intent.getIntExtra(EXTRA_SOCKS_PORT, -1).takeIf { it > 0 }?.let(Tor::reportSocksPort)
            }
        }
        val filter = IntentFilter(ACTION_STATUS)
        // Orbot is another app, so the receiver has to be exported for its reply to arrive.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        requestOrbot(context)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/** Orbot's documented start request; it answers with a status broadcast. */
private fun requestOrbot(context: Context) {
    context.sendBroadcast(
        Intent(ACTION_START).setPackage(Tor.ORBOT).putExtra(EXTRA_PACKAGE, context.packageName),
    )
}

private fun openOrbotListing(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${Tor.ORBOT}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(market) }.onFailure {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://orbot.app/download")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private const val ACTION_START = "org.torproject.android.intent.action.START"
private const val ACTION_STATUS = "org.torproject.android.intent.action.STATUS"
private const val EXTRA_PACKAGE = "org.torproject.android.intent.extra.PACKAGE_NAME"
private const val EXTRA_STATUS = "org.torproject.android.intent.extra.STATUS"
private const val EXTRA_SOCKS_PORT = "org.torproject.android.intent.extra.SOCKS_PROXY_PORT"
