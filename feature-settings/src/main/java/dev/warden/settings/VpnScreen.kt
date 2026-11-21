package dev.warden.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Process
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.warden.design.BarSpacer
import dev.warden.design.ButtonKind
import dev.warden.design.Radii
import dev.warden.design.SectionLabel
import dev.warden.design.Spacing
import dev.warden.design.Tile
import dev.warden.design.Warden
import dev.warden.design.WardenButton
import dev.warden.design.WardenSwitch
import dev.warden.rules.Route
import dev.warden.rules.Routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.warden.design.R as DesignR

/**
 * Your VPN on its own screen, opened from Home's tools. Top to bottom: what's in use (server and who
 * uses it), then which apps go through the VPN and which through Tor, then the saved servers.
 */
@Composable
fun VpnScreen(
    backLabel: String,
    countryOf: suspend (ip: String) -> String?,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    onBack: () -> Unit,
) {
    val c = Warden.colors
    val routes by Routing.routes.collectAsState()
    var picking by rememberSaveable { mutableStateOf<Route?>(null) } // which route Choose apps is for
    // Loaded and sorted off the main thread as soon as this screen opens, so Choose apps opens at once:
    // each name is a system lookup, and doing them on the main thread made the page stall.
    val context = LocalContext.current
    val apps by produceState(emptyList<Int>()) {
        value = withContext(Dispatchers.IO) { internetApps(context.packageManager).sortedBy { appLabel(it).lowercase() } }
    }
    // Choose apps is a full page of its own; Back from it returns here.
    picking?.let { route ->
        AppPicker(route, apps, appLabel, appIcon) { picking = null }
        return
    }
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(painterResource(DesignR.drawable.ph_arrow_left), stringResource(R.string.vpn_back), Modifier.size(20.dp), tint = c.text)
            SectionLabel(backLabel)
        }
        Text(stringResource(R.string.wg_title), style = Warden.type.headline)
        StatusCard(countryOf)

        val all by Routing.vpnForAll.collectAsState()
        // Each route is one compact card however many apps use it: their icons and a count. The list
        // itself, with on/off switches, is on the Choose apps page.
        listOf(Route.Vpn, Route.Tor).forEach { route ->
            val uids = routes.filterValues { it == route }.keys.sortedBy(appLabel)
            SectionLabel(
                stringResource(if (route == Route.Vpn) R.string.vpn_apps_vpn else R.string.vpn_apps_tor),
                Modifier.padding(top = Spacing.l),
            )
            Tile(Modifier.fillMaxWidth()) {
                when {
                    route == Route.Vpn && all -> Text(stringResource(R.string.vpn_apps_all), style = Warden.type.body, color = c.muted)
                    uids.isEmpty() -> Text(stringResource(R.string.vpn_apps_none), style = Warden.type.body, color = c.muted)
                    else -> AppStack(uids, appIcon) { picking = route }
                }
                WardenButton(stringResource(R.string.vpn_add_apps), { picking = route }, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal)
            }
        }

        SectionLabel(stringResource(R.string.vpn_servers), Modifier.padding(top = Spacing.l))
        Tile(Modifier.fillMaxWidth()) { WireGuardRow(countryOf) }
        BarSpacer()
    }
}

/** What's in use: the ticked server with its flag and country, who goes through it, and "all apps". */
@Composable
private fun StatusCard(countryOf: suspend (ip: String) -> String?) {
    val c = Warden.colors
    val servers by Routing.servers.collectAsState()
    val selected by Routing.selected.collectAsState()
    val routes by Routing.routes.collectAsState()
    val all by Routing.vpnForAll.collectAsState()
    val server = servers.firstOrNull { it.id == selected }
    val iso by produceState<String?>(null, server?.config?.endpoint) { value = server?.config?.endpoint?.let { countryOf(it) } }
    Tile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Box(
                Modifier.size(48.dp).background(c.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val flag = iso?.let(::flagOf)
                if (flag != null) {
                    Text(flag, fontSize = 24.sp)
                } else {
                    Icon(painterResource(DesignR.drawable.ph_lock_key_light), null, Modifier.size(22.dp), tint = c.muted)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    server?.name ?: stringResource(R.string.vpn_no_server),
                    style = Warden.type.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    iso?.let(::countryName) ?: server?.config?.endpoint ?: stringResource(R.string.vpn_no_server_body),
                    style = Warden.type.label,
                    color = c.muted,
                    maxLines = 1,
                )
            }
        }
        if (server != null) {
            val count = routes.values.count { it == Route.Vpn }
            Text(
                when {
                    all -> stringResource(R.string.vpn_status_all)
                    count == 0 -> stringResource(R.string.vpn_status_none)
                    else -> pluralStringResource(R.plurals.vpn_status_apps, count, count)
                },
                style = Warden.type.body,
            )
            HorizontalDivider(color = c.outline)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wg_all), Modifier.weight(1f), style = Warden.type.body)
                WardenSwitch(all, Routing::setVpnForAll, contentDescription = stringResource(R.string.wg_all))
            }
        }
    }
}

/** The apps on a route as one row: up to [STACK] icons, then "+N", and the count. Tap to manage them. */
@Composable
private fun AppStack(uids: List<Int>, appIcon: (Int) -> ImageBitmap?, onClick: () -> Unit) {
    val c = Warden.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(Radii.small)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        uids.take(STACK).forEach { AppIcon(appIcon(it)) }
        if (uids.size > STACK) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(Radii.small)).background(c.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("+${uids.size - STACK}", style = Warden.type.label, color = c.muted)
            }
        }
        Text(
            pluralStringResource(R.plurals.vpn_n_apps, uids.size, uids.size),
            Modifier.weight(1f),
            style = Warden.type.body,
            color = c.muted,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

private const val STACK = 5

@Composable
private fun AppIcon(bitmap: ImageBitmap?) {
    if (bitmap != null) {
        Image(bitmap, null, Modifier.size(36.dp).clip(RoundedCornerShape(Radii.small)))
    } else {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(Radii.small)).background(Warden.colors.surfaceVariant))
    }
}

/**
 * A full page of every app that can go online, each with a switch for [route] (Your VPN or Tor).
 * Apps already on it come first, in the order they had when the page opened, so rows don't jump
 * while you switch them. Search narrows the list.
 */
@Composable
private fun AppPicker(route: Route, apps: List<Int>, appLabel: (Int) -> String, appIcon: (Int) -> ImageBitmap?, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val c = Warden.colors
    val routes by Routing.routes.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val ordered = remember(apps) { apps.sortedByDescending { Routing.routes.value[it] == route } }
    val shown = remember(ordered, query) {
        if (query.isBlank()) ordered else ordered.filter { appLabel(it).contains(query.trim(), true) }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onDone),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(
                    painterResource(DesignR.drawable.ph_arrow_left),
                    stringResource(R.string.vpn_back),
                    Modifier.size(20.dp),
                    tint = c.text,
                )
                SectionLabel(stringResource(R.string.wg_title))
            }
            Text(
                stringResource(if (route == Route.Vpn) R.string.vpn_apps_vpn else R.string.vpn_apps_tor),
                style = Warden.type.headline,
            )
            Field(R.string.vpn_search, query, { query = it }, R.string.vpn_search_hint)
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = Spacing.s), contentPadding = PaddingValues(horizontal = Spacing.xl)) {
            items(shown, key = { it }) { uid ->
                val on = routes[uid] == route
                val other = routes[uid]?.takeIf { it != route }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    AppIcon(appIcon(uid))
                    Column(Modifier.weight(1f)) {
                        Text(appLabel(uid), style = Warden.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // On the other route now: switching it on here moves it.
                        if (other != null) {
                            Text(
                                stringResource(if (other == Route.Tor) R.string.vpn_on_tor else R.string.vpn_on_vpn),
                                style = Warden.type.label,
                                color = c.muted,
                            )
                        }
                    }
                    WardenSwitch(
                        on,
                        { Routing.set(uid, if (it) route else Route.Direct) },
                        contentDescription = appLabel(uid),
                    )
                }
            }
            item { BarSpacer() }
        }
    }
}

/**
 * Installed apps that can use the internet, by UID, Warden itself left out.
 * ponytail: same filter as the Apps tab's private list; share it if a third screen needs it.
 */
private fun internetApps(pm: PackageManager): List<Int> =
    runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        .filter { app ->
            app.uid >= Process.FIRST_APPLICATION_UID && app.uid != Process.myUid() && runCatching {
                pm.checkPermission(Manifest.permission.INTERNET, app.packageName) == PackageManager.PERMISSION_GRANTED &&
                    pm.getLaunchIntentForPackage(app.packageName) != null
            }.getOrDefault(false)
        }
        .map { it.uid }
        .distinct()
