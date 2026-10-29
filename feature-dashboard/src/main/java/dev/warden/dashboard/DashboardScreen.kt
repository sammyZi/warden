package dev.warden.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.warden.design.IconBadge
import dev.warden.design.Motion
import dev.warden.design.PowerButton
import dev.warden.design.PowerState
import dev.warden.design.Radii
import dev.warden.design.SectionLabel
import dev.warden.design.Spacing
import dev.warden.design.Sparkline
import dev.warden.design.StatSquare
import dev.warden.design.Tile
import dev.warden.design.Warden
import dev.warden.design.tabular
import dev.warden.engine.NetEvent
import dev.warden.engine.activity
import dev.warden.design.R as DesignR

@Composable
fun DashboardScreen(
    state: PowerState,
    events: List<NetEvent>,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    onPowerClick: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenApp: (Int) -> Unit,
    blocklistsOn: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Warden.colors
    val stats = remember(events) { Stats(events) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        Text(
            stringResource(if (state == PowerState.On) R.string.dashboard_on_detail else R.string.dashboard_off_detail),
            style = Warden.type.body,
            color = c.muted,
        )
        Spacer(Modifier.height(Spacing.l))
        ProtectionPill(state, onPowerClick)
        if (!blocklistsOn) {
            // Nothing is blocked until a list is on; say so where the zero shows up.
            Spacer(Modifier.height(Spacing.m))
            Tile(Modifier.fillMaxWidth(), onClick = onOpenSettings) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    IconBadge(DesignR.drawable.ph_prohibit, c.blocked)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dashboard_lists_title), style = Warden.type.body.copy(fontWeight = FontWeight.Medium))
                        Text(stringResource(R.string.dashboard_lists_body), style = Warden.type.label, color = c.muted)
                    }
                    Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(18.dp), tint = c.muted)
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val p = c.palette
            StatSquare(
                DesignR.drawable.ph_prohibit,
                c.blocked,
                stats.blocked,
                stringResource(R.string.dashboard_sq_blocked),
                Modifier.weight(1f),
            )
            StatSquare(
                DesignR.drawable.ph_pulse,
                p[0],
                stats.connections,
                stringResource(R.string.dashboard_sq_connections),
                Modifier.weight(1f),
            )
            StatSquare(DesignR.drawable.ph_squares_four, p[3], stats.apps, stringResource(R.string.dashboard_sq_apps), Modifier.weight(1f))
            StatSquare(
                DesignR.drawable.ph_globe_hemisphere_west,
                p[4],
                stats.domains,
                stringResource(R.string.dashboard_sq_domains),
                Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(Spacing.xl))
        SectionLabel(stringResource(R.string.dashboard_activity))
        Spacer(Modifier.height(Spacing.s))
        if (stats.activity.any { it > 0 }) {
            Sparkline(stats.activity, c.accentInk, Modifier.fillMaxWidth().height(72.dp))
        } else {
            Text(stringResource(R.string.dashboard_activity_none), style = Warden.type.body, color = c.muted)
        }

        Spacer(Modifier.height(Spacing.xl))
        SectionLabel(stringResource(R.string.dashboard_chatty))
        Spacer(Modifier.height(Spacing.s))
        Tile(Modifier.fillMaxWidth()) {
            if (stats.top.isEmpty()) {
                Text(stringResource(R.string.dashboard_chatty_empty), style = Warden.type.body, color = c.muted)
            }
            stats.top.forEachIndexed { i, (uid, count) ->
                if (i > 0) HorizontalDivider(color = c.outline)
                ListRow({ onOpenApp(uid) }) {
                    val icon = remember(uid) { appIcon(uid) }
                    if (icon != null) Image(icon, null, Modifier.size(32.dp)) else IconBadge(DesignR.drawable.ph_squares_four, c.palette[i])
                    Text(appLabel(uid), Modifier.weight(1f), style = Warden.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(count.toString(), style = Warden.type.caption.tabular(), color = c.muted)
                }
            }
        }

        Spacer(Modifier.height(Spacing.m))
        Tile(Modifier.fillMaxWidth(), onClick = onOpenMap) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                IconBadge(DesignR.drawable.ph_globe_hemisphere_west, c.palette[4])
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.dashboard_map_title),
                        style = Warden.type.body.copy(fontWeight = FontWeight.Medium),
                    )
                    Text(
                        pluralStringResource(R.plurals.dashboard_map_body, stats.domains, stats.domains),
                        style = Warden.type.label.tabular(),
                        color = c.muted,
                    )
                }
                Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(18.dp), tint = c.muted)
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}

/** The on/off control: a wide pill, lime when protecting. The power glyph keeps its three states. */
@Composable
private fun ProtectionPill(state: PowerState, onClick: () -> Unit) {
    val c = Warden.colors
    val on = state == PowerState.On
    val fill by animateColorAsState(if (on) c.primary else c.surface, Motion.emphasized(Motion.LONG), label = "pill")
    val ink by animateColorAsState(if (on) c.onPrimary else c.text, Motion.emphasized(Motion.LONG), label = "ink")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .clip(RoundedCornerShape(Radii.large))
            .background(fill)
            .border(1.dp, if (on) fill else c.outline, RoundedCornerShape(Radii.large))
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        PowerButton(state, onClick, size = 80.dp)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    when (state) {
                        PowerState.Off -> R.string.dashboard_off
                        PowerState.Connecting -> R.string.dashboard_connecting
                        PowerState.On -> R.string.dashboard_on
                    },
                ),
                style = Warden.type.headline,
                color = ink,
            )
            Text(
                stringResource(if (on) R.string.dashboard_pill_on else R.string.dashboard_pill_off).uppercase(),
                style = Warden.type.caption,
                color = ink.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ListRow(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        content = content,
    )
}

/** Everything the dashboard shows, derived once per events update. */
private class Stats(events: List<NetEvent>) {
    val blocked = events.count { it.blocked }
    val connections = events.count { it.kind != "dns" }
    val apps = events.map { it.uid }.distinct().size
    val domains = events.mapNotNull { it.domain }.distinct().size
    val top: List<Pair<Int, Int>> = events.filter { it.kind != "dns" }.groupingBy { it.uid }.eachCount()
        .entries.sortedByDescending { it.value }.take(TOP_APPS).map { it.key to it.value }

    val activity = events.activity()
}

private const val TOP_APPS = 4
