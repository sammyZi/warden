package dev.warden.settings

import dev.warden.rules.Verdict
import dev.warden.design.ChoiceChips
import androidx.compose.animation.animateContentSize
import dev.warden.rules.Rules
import dev.warden.design.Radii
import androidx.compose.ui.draw.clip
import java.util.Date
import java.text.NumberFormat
import java.text.DateFormat
import dev.warden.design.tabular
import dev.warden.design.SegmentedControl
import dev.warden.data.Range
import dev.warden.data.History
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import dev.warden.design.BarSpacer
import dev.warden.design.SectionLabel
import dev.warden.design.Spacing
import dev.warden.design.Tile
import dev.warden.design.Warden
import dev.warden.rules.BlocklistState
import dev.warden.rules.Blocklists
import dev.warden.design.R as DesignR

/** Blocklists on their own screen, opened from Home's tools: built-in lists, then ones you added. */
@Composable
fun BlocklistsScreen(backLabel: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val c = Warden.colors
    val sources by Blocklists.sources.collectAsState()
    val states by Blocklists.states.collectAsState()
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
        Text(stringResource(R.string.settings_blocklists), style = Warden.type.headline)
        Text(stringResource(R.string.settings_blocklists_note), style = Warden.type.body, color = c.muted)
        Tile(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
            sources.forEachIndexed { i, source ->
                if (i > 0) HorizontalDivider(color = c.outline)
                BlocklistRow(source, states[source.id] ?: BlocklistState())
            }
            AddListButton()
        }
        BlockedDomains()
        BarSpacer()
    }
}

/**
 * What the lists (and your rules) actually stopped: blocked domains in a time range, most often first,
 * each with how many times, how many apps tried it, and when it last happened.
 */
@Composable
private fun BlockedDomains() {
    val c = Warden.colors
    var range by rememberSaveable { mutableStateOf(Range.Today) }
    val rows by remember(range) { History.blockedDomains(range) }.collectAsState(emptyList())
    val total = rows.sumOf { it.count }
    val rules by Rules.domains.collectAsState() // re-read each domain's rule when one changes
    SectionLabel(stringResource(R.string.bl_blocked_title), Modifier.padding(top = Spacing.l))
    SegmentedControl(
        listOf(stringResource(R.string.bl_range_today), stringResource(R.string.bl_range_week), stringResource(R.string.bl_range_month)),
        range.ordinal,
        { range = Range.entries[it] },
    )
    Tile(Modifier.fillMaxWidth()) {
        if (rows.isEmpty()) {
            Text(stringResource(R.string.bl_blocked_none), style = Warden.type.body, color = c.muted)
            return@Tile
        }
        // One line of context, then each domain with a bar for its share of all blocks.
        Text(
            pluralStringResource(R.plurals.bl_blocked_total, total, NumberFormat.getIntegerInstance().format(total), rows.size),
            style = Warden.type.body,
        )
        val most = rows.first().count.coerceAtLeast(1)
        val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
        rows.forEach { d ->
            HorizontalDivider(color = c.outline)
            val rule = remember(rules, d.domain) { Rules.everywhere(d.domain) }
            val allowed = rule == Verdict.Allow
            // Tap to open the same rule chips as the live log; a domain here spans apps, so they're global.
            var open by rememberSaveable(d.domain) { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth().animateContentSize().clickable { open = !open }.padding(vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(d.domain, Modifier.weight(1f), style = Warden.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (allowed) AllowedTag()
                    Text(
                        NumberFormat.getIntegerInstance().format(d.count),
                        style = Warden.type.body.tabular(),
                        color = if (allowed) c.muted else c.blocked,
                    )
                }
                Text(
                    pluralStringResource(R.plurals.bl_blocked_apps, d.apps, d.apps) + " · " +
                        stringResource(R.string.bl_blocked_last, time.format(Date(d.lastTime))),
                    style = Warden.type.label,
                    color = c.muted,
                    maxLines = 1,
                )
                Box(Modifier.fillMaxWidth().height(3.dp).background(c.surfaceVariant, RoundedCornerShape(2.dp))) {
                    Box(
                        Modifier.fillMaxWidth(d.count / most.toFloat()).height(3.dp)
                            .background((if (allowed) c.muted else c.blocked).copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
                    )
                }
                if (open) GlobalRuleChips(d.domain, rule, Modifier.padding(top = Spacing.xs))
            }
        }
    }
}

/** Default (the lists decide), Allow everywhere, or Block everywhere: the live log's chips, for every app. */
@Composable
private fun GlobalRuleChips(domain: String, rule: Verdict?, modifier: Modifier = Modifier) {
    val options = listOf(null, Verdict.Allow, Verdict.Block)
    ChoiceChips(
        listOf(stringResource(R.string.bl_rule_default), stringResource(R.string.bl_rule_allow), stringResource(R.string.bl_rule_block)),
        options.indexOf(rule),
        { Rules.setEverywhere(domain, options[it]) },
        modifier,
    )
}

@Composable
private fun AllowedTag() {
    val c = Warden.colors
    Text(
        stringResource(R.string.settings_tag_allowed).uppercase(),
        Modifier.clip(RoundedCornerShape(Radii.small)).background(c.accentInk.copy(alpha = 0.14f))
            .padding(horizontal = Spacing.s, vertical = 2.dp),
        style = Warden.type.caption,
        color = c.accentInk,
    )
}
