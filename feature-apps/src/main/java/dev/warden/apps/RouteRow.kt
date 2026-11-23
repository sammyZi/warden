package dev.warden.apps

import dev.warden.design.WardenButton
import dev.warden.design.ButtonKind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.warden.design.SegmentedControl
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.engine.BuiltInTor
import dev.warden.engine.Engine
import dev.warden.engine.TorState
import dev.warden.rules.Route
import dev.warden.rules.Routing
import dev.warden.design.R as DesignR

/** Where this app's traffic goes: direct, through Tor (built in), or through the user's WireGuard server. */
@Composable
internal fun RouteRow(uid: Int, name: String) {
    val c = Warden.colors
    val routes by Routing.routes.collectAsState()
    val wireGuard by Routing.wireGuard.collectAsState()
    val route = routes[uid] ?: Route.Direct

    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(DesignR.drawable.ph_lock_key_light),
            null,
            Modifier.size(20.dp),
            tint = if (route == Route.Direct) c.text else c.accentInk,
        )
        Text(stringResource(R.string.route_title), Modifier.weight(1f).padding(start = Spacing.m), style = Warden.type.body)
    }
    SegmentedControl(
        listOf(stringResource(R.string.route_direct), stringResource(R.string.route_tor), stringResource(R.string.route_vpn)),
        route.ordinal,
        { Routing.set(uid, Route.entries[it]) },
    )
    when (route) {
        Route.Direct -> if (Routing.vpnForAll.collectAsState().value && wireGuard != null) {
            Note(stringResource(R.string.route_vpn_all), warn = false)
        }
        Route.Tor -> TorStatus(name)
        Route.Vpn -> {
            val wg = wireGuard
            if (wg == null) {
                Note(stringResource(R.string.route_vpn_missing, name), warn = true)
            } else {
                Note(stringResource(R.string.route_vpn_on, wg.endpoint), warn = false)
            }
        }
    }
}

/** Tor runs inside Warden; until it has connected, this app stays offline rather than going direct. */
@Composable
private fun TorStatus(name: String) {
    val state by BuiltInTor.state.collectAsState()
    val running by Engine.running.collectAsState()
    val context = LocalContext.current
    when {
        !running -> Note(stringResource(R.string.tor_when_on), warn = false)
        state == TorState.On -> Note(stringResource(R.string.tor_on), warn = false)
        state == TorState.Failed -> {
            Note(stringResource(R.string.tor_failed, name), warn = true)
            WardenButton(
                stringResource(R.string.tor_retry),
                { BuiltInTor.retry(context) },
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Tonal,
            )
        }
        else -> Note(stringResource(R.string.tor_connecting, name), warn = true)
    }
}

@Composable
private fun Note(text: String, warn: Boolean) {
    Text(text, style = Warden.type.label, color = if (warn) Warden.colors.warning else Warden.colors.muted)
}
