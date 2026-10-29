package dev.warden.logs

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.warden.design.ButtonKind
import dev.warden.design.EmptyState
import dev.warden.design.WardenButton
import androidx.compose.material3.AlertDialog
import dev.warden.design.FilterChip
import dev.warden.design.IconBadge
import dev.warden.design.BarSpacer
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.formatBytes
import dev.warden.design.staggerIn
import dev.warden.design.tabular
import dev.warden.engine.NetEvent
import androidx.compose.runtime.collectAsState
import dev.warden.design.ChoiceChips
import dev.warden.rules.DomainChoice
import dev.warden.rules.Rules
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import dev.warden.design.R as DesignR

private enum class Filter(val label: Int, val keep: (NetEvent) -> Boolean) {
    All(R.string.log_filter_all, { true }),
    Blocked(R.string.log_filter_blocked, { it.blocked }),
    Dns(R.string.log_filter_dns, { it.kind == "dns" }),
    Connections(R.string.log_filter_connections, { it.kind != "dns" }),
}

/** Live stream of DNS answers and connections. Tap-to-block arrives with per-domain rules in Milestone 6. */
@Composable
fun LogScreen(
    events: List<NetEvent>,
    running: Boolean,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Warden.colors
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    val shown = remember(events, filter) { events.filter(filter.keep) }
    val blocked = events.count { it.blocked }

    // Stagger only on first appearance, never while scrolling or as new rows arrive.
    var firstAppearance by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(FIRST_APPEARANCE_MS)
        firstAppearance = false
    }
    val time = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }

    // Title, live line and filters stay put; only the stream below scrolls.
    Column(modifier.fillMaxSize()) {
        var confirmClear by remember { mutableStateOf(false) }
        Column(Modifier.padding(horizontal = Spacing.xl).padding(top = Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.log_title), Modifier.weight(1f), style = Warden.type.headline)
                if (events.isNotEmpty()) {
                    WardenButton(stringResource(R.string.log_clear), { confirmClear = true }, kind = ButtonKind.Text)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                LiveDot(running)
                Text(
                    stringResource(if (running) R.string.log_live else R.string.log_paused, events.size, blocked).uppercase(),
                    style = Warden.type.caption.tabular(),
                    color = c.muted,
                )
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.xl, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Filter.entries.forEach { FilterChip(stringResource(it.label), filter == it, { filter = it }) }
        }
        HorizontalDivider(color = c.outline)
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text(stringResource(R.string.log_clear_title), style = Warden.type.title) },
                text = { Text(stringResource(R.string.log_clear_body), style = Warden.type.body) },
                confirmButton = {
                    WardenButton(stringResource(R.string.log_clear), {
                        onClear()
                        confirmClear = false
                    }, kind = ButtonKind.Destructive)
                },
                dismissButton = { WardenButton(stringResource(R.string.log_cancel), { confirmClear = false }, kind = ButtonKind.Text) },
                containerColor = c.surface,
            )
        }
        LogList(shown, events.isEmpty(), firstAppearance, time, appLabel, appIcon)
    }
}

@Composable
private fun ColumnScope.LogList(
    shown: List<NetEvent>,
    noEvents: Boolean,
    firstAppearance: Boolean,
    time: DateFormat,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
) {
    val c = Warden.colors
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        if (shown.isEmpty()) {
            item {
                EmptyState(
                    DesignR.drawable.ph_pulse_light,
                    stringResource(if (noEvents) R.string.log_empty_title else R.string.log_filtered_title),
                    stringResource(if (noEvents) R.string.log_empty_body else R.string.log_filtered_body),
                    Modifier.fillMaxWidth(),
                )
            }
        }
        itemsIndexed(shown, key = { _, e -> e.id }) { index, e ->
            var open by rememberSaveable(e.id) { mutableStateOf(false) }
            Row(
                Modifier
                    .staggerIn(index, firstAppearance)
                    .fillMaxWidth()
                    .clickable(enabled = e.domain != null) { open = !open }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = Spacing.xl, vertical = Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = appIcon(e.uid)
                if (icon != null) Image(icon, null, Modifier.size(36.dp)) else IconBadge(DesignR.drawable.ph_squares_four, c.muted)
                Column(Modifier.weight(1f)) {
                    Text(
                        e.domain ?: e.target,
                        style = Warden.type.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (e.blocked) c.blocked else c.text,
                    )
                    Text(
                        listOf(appLabel(e.uid), e.kind, time.format(Date(e.time))).joinToString(" · ").uppercase(),
                        style = Warden.type.caption.tabular(),
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    // Icon-free but never colour alone: the word is the signal.
                    e.blocked -> Text(
                        stringResource(R.string.log_blocked).uppercase(),
                        Modifier
                            .background(c.blocked.copy(alpha = 0.16f), RoundedCornerShape(50))
                            .padding(horizontal = Spacing.s, vertical = 3.dp),
                        style = Warden.type.caption,
                        color = c.blocked,
                    )
                    e.bytesUp + e.bytesDown > 0 -> Text(
                        "↑${formatBytes(e.bytesUp)}\n↓${formatBytes(e.bytesDown)}",
                        style = Warden.type.caption.tabular(),
                        color = c.muted,
                    )
                }
            }
            val domain = e.domain
            if (open && domain != null) {
                DomainRuleChips(
                    e.uid,
                    domain,
                    Modifier.padding(start = Spacing.xl + 36.dp + Spacing.m, end = Spacing.xl, bottom = Spacing.m),
                )
            }
            HorizontalDivider(Modifier.padding(start = Spacing.xl + 36.dp + Spacing.m), color = c.outline)
        }
        item { BarSpacer() }
    }
}

@Composable
private fun LiveDot(running: Boolean) {
    val c = Warden.colors
    if (running && !Warden.reduceMotion) {
        // Alpha is read in the layer, not in composition: the pulse costs a redraw, not a recomposition.
        val alpha = rememberInfiniteTransition("live").animateFloat(0.25f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), "a")
        Box(Modifier.size(7.dp).graphicsLayer { this.alpha = alpha.value }.background(c.accentInk, CircleShape))
    } else {
        Box(Modifier.size(7.dp).background(if (running) c.accentInk else c.muted, CircleShape))
    }
}

private const val FIRST_APPEARANCE_MS = 600L

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
