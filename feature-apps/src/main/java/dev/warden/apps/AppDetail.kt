package dev.warden.apps

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.warden.design.Motion
import dev.warden.design.Radii
import dev.warden.design.SectionLabel
import dev.warden.design.BarSpacer
import dev.warden.design.Spacing
import dev.warden.design.Sparkline
import dev.warden.design.CountUpText
import dev.warden.design.Warden
import dev.warden.design.formatBytes
import dev.warden.design.Tile
import dev.warden.design.WardenSwitch
import dev.warden.rules.AppRule
import dev.warden.rules.AppRules
import dev.warden.rules.TrackerSummary
import dev.warden.rules.summarizeTrackers
import dev.warden.design.ChoiceChips
import dev.warden.rules.DomainChoice
import dev.warden.rules.Rules
import androidx.compose.runtime.collectAsState
import dev.warden.design.tabular
import dev.warden.engine.NetEvent
import dev.warden.engine.activity
import java.text.DateFormat
import java.util.Date
import dev.warden.design.R as DesignR

/** One domain (or bare IP) this app reached, with every event for it, newest first. */
private class DomainGroup(val name: String, val events: List<NetEvent>) {
    val connections = events.count { it.kind != "dns" }
    val lookups = events.count { it.kind == "dns" }
    val blocked = events.count { it.blocked }
    val last = events.maxOf { it.time }
}

/**
 * Everything one app did, summary first: a plain sentence, three numbers, its activity line, then
 * the domains it reached. Each domain expands in place (one level deep) to show its connections.
 */
@Composable
internal fun AppDetail(
    uid: Int,
    events: List<NetEvent>,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    shared: Shared,
    onBack: () -> Unit,
) {
    val c = Warden.colors
    val mine = remember(events, uid) { events.filter { it.uid == uid } }
    val domains = remember(mine) {
        mine.groupBy { it.domain ?: it.target.substringBeforeLast(':').trim('[', ']') }
            .map { (name, list) -> DomainGroup(name, list.sortedByDescending { it.time }) }
            .sortedWith(compareByDescending<DomainGroup> { it.blocked > 0 }.thenByDescending { it.events.size })
    }
    val connections = mine.count { it.kind != "dns" }
    val blocked = mine.count { it.blocked }
    val time = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }
    val name = appLabel(uid)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onBack).padding(horizontal = Spacing.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(
                    painterResource(DesignR.drawable.ph_arrow_left),
                    stringResource(R.string.apps_back),
                    Modifier.size(20.dp),
                    tint = c.text,
                )
                SectionLabel(stringResource(R.string.apps_title))
            }
        }
        // Header: who, when, and the grade. The grade opens the report card.
        item {
            var showReport by remember { mutableStateOf(false) }
            val report = rememberReport(uid)
            Row(
                Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                AppIcon(uid, appIcon, 56.dp, shared.icon(uid))
                Column(Modifier.weight(1f)) {
                    Text(name, shared.name(uid), style = Warden.type.headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (mine.isNotEmpty()) {
                        SectionLabel(stringResource(R.string.apps_last_seen, time.format(Date(mine.maxOf { it.time }))))
                    }
                }
            }
            ReportRow(report.grade.letter, Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.l)) { showReport = true }
            if (showReport) ReportCardSheet(report, name, appIcon(uid)) { showReport = false }
        }
        // Three numbers, no boxes.
        item {
            Row(Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.l)) {
                Number(connections, stringResource(R.string.apps_sq_connections), c.text, Modifier.weight(1f))
                Number(domains.size, stringResource(R.string.apps_sq_domains), c.text, Modifier.weight(1f))
                Number(blocked, stringResource(R.string.apps_sq_blocked), if (blocked > 0) c.blocked else c.text, Modifier.weight(1f))
            }
        }
        item {
            Column(
                Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                SectionLabel(stringResource(R.string.apps_activity))
                val activity = remember(mine) { mine.activity() }
                if (activity.any { it > 0 }) {
                    Sparkline(activity, c.accentInk, Modifier.fillMaxWidth().height(56.dp))
                } else {
                    Text(stringResource(R.string.apps_activity_none), style = Warden.type.body, color = c.muted)
                }
            }
        }
        item {
            SectionLabel(stringResource(R.string.apps_access_label), Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.s))
            AccessCard(uid, name)
        }
        item {
            val trackers = remember(domains) { summarizeTrackers(domains.map { it.name }) }
            TrackersSection(trackers)
        }
        item {
            SectionLabel(
                stringResource(R.string.apps_domains_label),
                Modifier.padding(horizontal = Spacing.xl).padding(top = Spacing.xl, bottom = Spacing.s),
            )
        }
        val most = domains.maxOfOrNull { it.events.size } ?: 1
        items(domains, key = { it.name }) { d -> DomainRow(uid, d, most, time) }
        item { BarSpacer() }
    }
}

/** A domain with its share bar; tap to expand its recent connections in place. */
@Composable
private fun DomainRow(uid: Int, d: DomainGroup, most: Int, time: DateFormat) {
    val c = Warden.colors
    var open by rememberSaveable(d.name) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 90f else 0f, Motion.emphasized(Motion.MEDIUM), label = "caret")
    val stateText = stringResource(if (open) R.string.apps_expanded else R.string.apps_collapsed)
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(Motion.emphasized(Motion.LONG))
            .clickable(role = Role.Button) { open = !open }
            .semantics { stateDescription = stateText }
            .padding(horizontal = Spacing.xl, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Column(Modifier.weight(1f)) {
                Text(
                    d.name,
                    style = Warden.type.body,
                    color = if (d.blocked > 0) c.blocked else c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        pluralStringResource(R.plurals.apps_connections, d.connections, d.connections).takeIf { d.connections > 0 },
                        pluralStringResource(R.plurals.apps_lookups, d.lookups, d.lookups).takeIf { d.lookups > 0 },
                        time.format(Date(d.last)),
                    ).joinToString(" · "),
                    style = Warden.type.label.tabular(),
                    color = c.muted,
                )
            }
            if (d.blocked > 0) BlockedTag(d.blocked)
            Icon(
                painterResource(DesignR.drawable.ph_caret_right),
                null,
                Modifier.size(16.dp).graphicsLayer { rotationZ = turn },
                tint = c.muted,
            )
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.surfaceVariant, RoundedCornerShape(2.dp))) {
            Box(
                Modifier
                    .fillMaxWidth(d.events.size / most.toFloat())
                    .fillMaxHeight()
                    .background(if (d.blocked > 0) c.blocked else c.accentInk, RoundedCornerShape(2.dp)),
            )
        }
        if (open) {
            DomainRuleChips(uid, d.name)
            d.events.take(MAX_EXPANDED).forEach { e ->
                Text(
                    listOfNotNull(
                        e.kind.uppercase(),
                        e.target,
                        time.format(Date(e.time)),
                        if (e.bytesUp + e.bytesDown > 0) "↑${formatBytes(e.bytesUp)} ↓${formatBytes(e.bytesDown)}" else null,
                        if (e.blocked) stringResource(R.string.apps_blocked_word).uppercase() else null,
                    ).joinToString("  ·  "),
                    Modifier
                        .fillMaxWidth()
                        .background(c.surface, RoundedCornerShape(Spacing.s))
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    style = Warden.type.caption.tabular(),
                    color = if (e.blocked) c.blocked else c.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (d.events.size > MAX_EXPANDED) {
                Text(stringResource(R.string.apps_more, d.events.size - MAX_EXPANDED), style = Warden.type.label, color = c.muted)
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = Spacing.xl), color = c.outline)
}

private const val MAX_EXPANDED = 10

/** Network access for this app. Switch on = allowed. Applies to new connections. */
@Composable
private fun AccessCard(uid: Int, name: String) {
    val c = Warden.colors
    val rules by AppRules.rules.collectAsState()
    val rule = rules[uid] ?: AppRule()
    Tile(Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl).fillMaxWidth()) {
        AccessRow(
            DesignR.drawable.ph_wifi_high,
            stringResource(R.string.apps_wifi),
            allowed = !rule.blockWifi,
            description = stringResource(R.string.apps_wifi_for, name),
        ) { AppRules.set(uid, rule.copy(blockWifi = !it)) }
        HorizontalDivider(color = c.outline)
        AccessRow(
            DesignR.drawable.ph_cell_signal_full,
            stringResource(R.string.apps_cellular),
            allowed = !rule.blockCellular,
            description = stringResource(R.string.apps_cellular_for, name),
        ) { AppRules.set(uid, rule.copy(blockCellular = !it)) }
        HorizontalDivider(color = c.outline)
        // Skipping routes the app around the VPN entirely: no filtering, and nothing logged.
        val context = LocalContext.current
        val packages = remember(uid) { context.packageManager.getPackagesForUid(uid)?.toList().orEmpty() }
        val excluded by AppRules.excluded.collectAsState()
        val skipped = packages.isNotEmpty() && packages.any { it in excluded }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(DesignR.drawable.ph_eye_slash_light),
                null,
                Modifier.size(20.dp),
                tint = if (skipped) c.warning else c.text,
            )
            Column(Modifier.weight(1f).padding(start = Spacing.m)) {
                Text(stringResource(R.string.apps_skip), style = Warden.type.body)
                Text(
                    stringResource(if (skipped) R.string.apps_skip_on else R.string.apps_skip_off).uppercase(),
                    style = Warden.type.caption,
                    color = if (skipped) c.warning else c.muted,
                )
            }
            WardenSwitch(
                skipped,
                { AppRules.setExcluded(packages, it) },
                contentDescription = stringResource(R.string.apps_skip_for, name),
                enabled = packages.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun AccessRow(icon: Int, label: String, allowed: Boolean, description: String, onChange: (Boolean) -> Unit) {
    val c = Warden.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = if (allowed) c.text else c.blocked)
        Column(Modifier.weight(1f).padding(start = Spacing.m)) {
            Text(label, style = Warden.type.body)
            Text(
                stringResource(if (allowed) R.string.apps_allowed else R.string.apps_blocked_word).uppercase(),
                style = Warden.type.caption,
                color = if (allowed) c.muted else c.blocked,
            )
        }
        WardenSwitch(allowed, onChange, contentDescription = description)
    }
}

/** Block or allow this domain for this app, or everywhere. Takes effect for new connections. */
@Composable
internal fun DomainRuleChips(uid: Int, domain: String, modifier: Modifier = Modifier) {
    val rules by Rules.domains.collectAsState()
    val current = remember(rules, uid, domain) { Rules.choice(uid, domain) }
    ChoiceChips(
        DomainChoice.entries.map {
            stringResource(
                when (it) {
                    DomainChoice.Default -> R.string.rule_default
                    DomainChoice.AllowHere -> R.string.rule_allow_here
                    DomainChoice.BlockHere -> R.string.rule_block_here
                    DomainChoice.BlockEverywhere -> R.string.rule_block_everywhere
                },
            )
        },
        current.ordinal,
        { Rules.choose(uid, domain, DomainChoice.entries[it]) },
        modifier,
    )
}

/** "8 trackers from 4 companies", then one line per company with what it does. */
@Composable
private fun TrackersSection(t: TrackerSummary) {
    val c = Warden.colors
    Column(Modifier.padding(horizontal = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionLabel(stringResource(R.string.apps_trackers))
        Text(
            if (t.trackers == 0) {
                stringResource(R.string.apps_trackers_none)
            } else {
                stringResource(
                    R.string.apps_trackers_sentence,
                    pluralStringResource(R.plurals.apps_n_trackers, t.trackers, t.trackers),
                    pluralStringResource(R.plurals.apps_n_companies, t.companies, t.companies),
                )
            },
            style = Warden.type.body,
            color = if (t.trackers == 0) c.muted else c.text,
        )
        t.byCompany.forEach { (company, categories) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(company, Modifier.weight(1f), style = Warden.type.body)
                Text(categories.joinToString(" · "), style = Warden.type.label, color = c.muted)
            }
        }
    }
}

/** The grade with words beside it, so it reads as something to tap: "Privacy grade B · See the report card". */
@Composable
private fun ReportRow(letter: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Warden.colors
    val shape = RoundedCornerShape(Radii.medium)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        GradeBadge(letter, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.report_row_title, letter), style = Warden.type.body.copy(fontWeight = FontWeight.Medium))
            Text(stringResource(R.string.report_row_body), style = Warden.type.label, color = c.muted)
        }
        Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(16.dp), tint = c.muted)
    }
}

@Composable
private fun Number(value: Int, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        CountUpText(value, Warden.type.headline, color = color)
        Text(label.uppercase(), style = Warden.type.caption, color = Warden.colors.muted)
    }
}
