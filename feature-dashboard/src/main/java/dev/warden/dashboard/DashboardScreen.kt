package dev.warden.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
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
import dev.warden.design.BarSpacer
import dev.warden.design.Spacing
import dev.warden.design.SegmentedControl
import dev.warden.data.History
import dev.warden.data.Range
import dev.warden.data.RangeStats
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.warden.design.Sparkline
import dev.warden.design.StatSquare
import dev.warden.design.Tile
import dev.warden.design.Warden
import dev.warden.design.tabular
import dev.warden.design.R as DesignR

@Composable
fun DashboardScreen(
    state: PowerState,
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
    // Everything below the protection pill comes from saved history, for the chosen range.
    var range by rememberSaveable { mutableStateOf(Range.Today) }
    val stats by remember(range) { History.stats(range) }.collectAsState(RangeStats(0, 0, 0, 0, 0))
    val activity by remember(range) { History.activity(range) }.collectAsState(emptyList())
    val top by remember(range) { History.apps(range, limit = TOP_APPS) }.collectAsState(emptyList())
    var tool by rememberSaveable { mutableStateOf<Tool?>(null) }
    val scroll = rememberScrollState()
    val reduceMotion = Warden.reduceMotion

    AnimatedContent(
        tool,
        modifier,
        transitionSpec = {
            if (reduceMotion) ContentTransform(EnterTransition.None, ExitTransition.None) else drillDown(targetState != null)
        },
        label = "tool",
    ) { open ->
        if (open != null) {
            ToolScreen(open, range, appLabel, appIcon, onOpenApp, onBack = { tool = null })
            return@AnimatedContent
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.xl),
        ) {
            // Same header slot as every other tab.
            Text(
                stringResource(R.string.dashboard_title),
                Modifier.padding(top = Spacing.s, bottom = Spacing.s),
                style = Warden.type.headline,
            )
            Text(
                stringResource(if (state == PowerState.On) R.string.dashboard_on_detail else R.string.dashboard_off_detail),
                style = Warden.type.body,
                color = c.muted,
            )
            Spacer(Modifier.height(Spacing.l))
            ProtectionPill(state, onPowerClick)
            Spacer(Modifier.height(Spacing.s))
            DnsCard(onOpenSettings)
            PrivateDnsWarning()
            if (!blocklistsOn) {
                // Nothing is blocked until a list is on; say so where the zero shows up.
                Spacer(Modifier.height(Spacing.m))
                Tile(Modifier.fillMaxWidth(), onClick = onOpenSettings) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        IconBadge(DesignR.drawable.ph_prohibit, c.blocked)
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.dashboard_lists_title),
                                style = Warden.type.body.copy(fontWeight = FontWeight.Medium),
                            )
                            Text(stringResource(R.string.dashboard_lists_body), style = Warden.type.label, color = c.muted)
                        }
                        Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(18.dp), tint = c.muted)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            SegmentedControl(
                listOf(
                    stringResource(R.string.dashboard_range_today),
                    stringResource(R.string.dashboard_range_week),
                    stringResource(R.string.dashboard_range_month),
                ),
                range.ordinal,
                { range = Range.entries[it] },
            )
            Spacer(Modifier.height(Spacing.m))
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
                StatSquare(
                    DesignR.drawable.ph_squares_four,
                    p[3],
                    stats.apps,
                    stringResource(R.string.dashboard_sq_apps),
                    Modifier.weight(1f),
                )
                StatSquare(
                    DesignR.drawable.ph_globe_hemisphere_west,
                    p[4],
                    stats.domains,
                    stringResource(R.string.dashboard_sq_domains),
                    Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(Spacing.xl))
            SectionLabel(stringResource(R.string.dashboard_tools))
            Spacer(Modifier.height(Spacing.s))
            ToolsGrid(range) { tool = it }
            Spacer(Modifier.height(Spacing.xl))
            SectionLabel(
                stringResource(R.string.dashboard_activity) + " · " + stringResource(
                    when (range) {
                        Range.Today -> R.string.dashboard_range_today
                        Range.Week -> R.string.dashboard_range_week
                        Range.Month -> R.string.dashboard_range_month
                    },
                ),
            )
            Spacer(Modifier.height(Spacing.s))
            if (activity.any { it > 0 }) {
                Sparkline(
                    activity,
                    c.accentInk,
                    Modifier.fillMaxWidth().height(120.dp),
                    startLabel = stringResource(
                        when (range) {
                            Range.Today -> R.string.dashboard_activity_midnight
                            Range.Week -> R.string.dashboard_activity_week_ago
                            Range.Month -> R.string.dashboard_activity_month_ago
                        },
                    ),
                    endLabel = stringResource(R.string.dashboard_activity_end),
                )
            } else {
                Text(stringResource(R.string.dashboard_activity_none), style = Warden.type.body, color = c.muted)
            }

            Spacer(Modifier.height(Spacing.xl))
            SectionLabel(stringResource(R.string.dashboard_chatty))
            Spacer(Modifier.height(Spacing.s))
            Tile(Modifier.fillMaxWidth()) {
                if (top.isEmpty()) {
                    Text(stringResource(R.string.dashboard_chatty_empty), style = Warden.type.body, color = c.muted)
                }
                top.forEachIndexed { i, app ->
                    val uid = app.uid
                    val count = app.connections
                    if (i > 0) HorizontalDivider(color = c.outline)
                    ListRow({ onOpenApp(uid) }) {
                        val icon = appIcon(uid)
                        if (icon != null) {
                            Image(icon, null, Modifier.size(32.dp))
                        } else {
                            IconBadge(DesignR.drawable.ph_squares_four, c.palette[i])
                        }
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
            BarSpacer()
        }
    }
}

/** Drill-down: the tool screen slides in from the right, Home drifts a little the other way. */
private fun drillDown(forward: Boolean): ContentTransform {
    val dir = if (forward) 1 else -1
    return (slideInHorizontally(Motion.emphasized(Motion.LONG)) { dir * it / 4 } + fadeIn(Motion.emphasized(Motion.LONG)))
        .togetherWith(slideOutHorizontally(Motion.emphasized(Motion.LONG)) { -dir * it / 4 } + fadeOut(Motion.emphasized(Motion.MEDIUM)))
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

private const val TOP_APPS = 4

/**
 * Strict Private DNS (a hostname like dns.google in system settings) sends DNS over TLS straight past
 * Warden, so domain blocking silently stops. Detect it and point to the setting.
 */
@Composable
private fun PrivateDnsWarning() {
    val context = LocalContext.current
    val c = Warden.colors
    // Re-checked whenever Home is shown; it changes only from system settings.
    val strict = remember {
        if (Build.VERSION.SDK_INT < 28) return@remember null
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.getLinkProperties(cm.activeNetwork)?.privateDnsServerName
    } ?: return
    Spacer(Modifier.height(Spacing.m))
    Tile(Modifier.fillMaxWidth(), onClick = { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            IconBadge(DesignR.drawable.ph_lock_simple, c.warning)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_pdns_title), style = Warden.type.body.copy(fontWeight = FontWeight.Medium))
                Text(stringResource(R.string.dashboard_pdns_body, strict), style = Warden.type.label, color = c.muted)
            }
            Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(18.dp), tint = c.muted)
        }
    }
}
