package dev.warden.dashboard

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.warden.data.AppTotals
import dev.warden.data.History
import dev.warden.data.Range
import dev.warden.design.BarSpacer
import dev.warden.design.IconBadge
import dev.warden.design.Radii
import dev.warden.design.SectionLabel
import dev.warden.design.SegmentedControl
import dev.warden.design.Spacing
import dev.warden.design.Tile
import dev.warden.design.Warden
import dev.warden.design.formatBytes
import dev.warden.design.tabular
import dev.warden.rules.Trackers
import dev.warden.rules.summarizeTrackers
import dev.warden.design.R as DesignR

/** Home's tools. Each is a box on Home and a screen of its own; new tools add an entry here. */
internal enum class Tool(@StringRes val title: Int, @DrawableRes val icon: Int) {
    Trackers(R.string.tool_trackers, DesignR.drawable.ph_eye_slash_light),
    Data(R.string.tool_data, DesignR.drawable.ph_cell_signal_full),
}

/** Headline numbers for every tool over one range, from saved history. */
private class ToolData(
    val all: List<AppTotals>,
    val domains: List<String>,
)

@Composable
private fun toolData(range: Range): ToolData {
    val all by remember(range) { History.apps(range, limit = MAX_APPS) }.collectAsState(emptyList())
    val domains by remember(range) { History.domains(range) }.collectAsState(emptyList())
    return ToolData(all, domains)
}

/** Two-by-two grid of tool boxes. */
@Composable
internal fun ToolsGrid(range: Range, onOpen: (Tool) -> Unit) {
    val d = toolData(range)
    val trackers = remember(d.domains) { summarizeTrackers(d.domains) }
    val (access, _) = rememberUsageAccess()
    val dataTotal = (systemApps(range, access) ?: d.all.asUsage()).sumOf { it.total }
    val values = mapOf(
        Tool.Trackers to (
            trackers.trackers.toString() to
                pluralStringResource(R.plurals.dashboard_n_companies, trackers.companies, trackers.companies)
            ),
        Tool.Data to (formatBytes(dataTotal) to stringResource(if (access) R.string.tool_data_box_system else R.string.tool_data_box)),
    )
    val p = Warden.colors.palette
    val hues = listOf(Warden.colors.blocked, p[0], p[3], p[4])
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Tool.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                pair.forEach { tool ->
                    val (value, caption) = values.getValue(tool)
                    ToolBox(tool, hues[tool.ordinal % hues.size], value, caption, Modifier.weight(1f)) { onOpen(tool) }
                }
            }
        }
    }
}

@Composable
private fun ToolBox(tool: Tool, hue: Color, value: String, caption: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Warden.colors
    Column(
        modifier
            .aspectRatio(1.25f)
            .clip(RoundedCornerShape(Radii.medium))
            .background(hue.copy(alpha = if (c.isDark) 0.22f else 0.13f))
            .clickable(onClick = onClick)
            .padding(Spacing.m),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(tool.icon), null, Modifier.size(18.dp), tint = hue)
            Text(
                stringResource(tool.title).uppercase(),
                Modifier.weight(1f).padding(start = Spacing.s),
                style = Warden.type.caption,
                color = c.muted,
                maxLines = 1,
            )
            Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(14.dp), tint = c.muted)
        }
        Column {
            Text(value, style = Warden.type.headline.tabular(), maxLines = 1)
            Text(caption, style = Warden.type.label, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A tool's own screen: range, a one-line answer, then the apps (or companies) behind it. */
@Composable
internal fun ToolScreen(
    tool: Tool,
    initialRange: Range,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    onOpenApp: (Int) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val c = Warden.colors
    var range by rememberSaveable { mutableStateOf(initialRange) }
    val d = toolData(range)
    // Data comes from Android's counters when Usage access is on (works with Warden off), else Warden's own history.
    val (access, requestAccess) = rememberUsageAccess()
    val system = systemApps(range, access)
    val usage = if (access) system.orEmpty() else d.all.asUsage()
    val rows = remember(tool, d, usage) { rows(tool, usage) }
    val most = rows.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(
                    painterResource(DesignR.drawable.ph_arrow_left),
                    stringResource(R.string.tool_back),
                    Modifier.size(20.dp),
                    tint = c.text,
                )
                SectionLabel(stringResource(R.string.dashboard_title))
            }
        }
        item {
            Text(stringResource(tool.title), style = Warden.type.headline)
            Text(stringResource(about(tool)), style = Warden.type.body, color = c.muted)
        }
        item {
            SegmentedControl(
                listOf(
                    stringResource(R.string.dashboard_range_today),
                    stringResource(R.string.dashboard_range_week),
                    stringResource(R.string.dashboard_range_month),
                ),
                range.ordinal,
                { range = Range.entries[it] },
                Modifier.padding(vertical = Spacing.s),
            )
        }
        if (tool == Tool.Data && !access) item { UsageAccessCard(requestAccess) }
        if (tool == Tool.Data && rows.isNotEmpty()) item { DataOverview(usage, range, system = access) }
        if (tool == Tool.Trackers) {
            item { TrackerCompanies(d.domains, range) }
        } else if (rows.isEmpty()) {
            item { Text(stringResource(R.string.tool_empty), style = Warden.type.body, color = c.muted) }
        } else {
            itemsIndexed(rows, key = { _, r -> r.uid }) { i, r ->
                AppRow(r, appLabel, appIcon, c.palette[i % c.palette.size], r.value / most) { onOpenApp(r.uid) }
            }
        }
        item { BarSpacer() }
    }
}

private class AppLine(val uid: Int, val value: Float, val usage: Usage)

private fun rows(tool: Tool, usage: List<Usage>): List<AppLine> = when (tool) {
    Tool.Trackers -> emptyList()
    Tool.Data -> usage.map { AppLine(it.uid, it.total.toFloat(), it) }.filter { it.value > 0 }
}.sortedByDescending { it.value }

@Composable
private fun AppRow(
    r: AppLine,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    hue: Color,
    share: Float,
    onClick: () -> Unit,
) {
    val c = Warden.colors
    Tile(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            val icon = appIcon(r.uid)
            if (icon != null) Image(icon, null, Modifier.size(36.dp)) else IconBadge(DesignR.drawable.ph_squares_four, hue)
            Column(Modifier.weight(1f)) {
                Text(appLabel(r.uid), style = Warden.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                UsageCaption(r.usage)
            }
            Text(formatBytes(r.usage.total), style = Warden.type.body.copy(fontWeight = FontWeight.Medium).tabular())
            Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(16.dp), tint = c.muted)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(c.surfaceVariant, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).fillMaxHeight().background(hue, RoundedCornerShape(2.dp)))
        }
    }
}

/**
 * Wi-Fi and mobile on their own lines (Android's counters), the amounts lined up in one column so they
 * compare at a glance; or sent / received on one line (Warden's own history).
 */
@Composable
private fun UsageCaption(u: Usage) {
    val style = Warden.type.label.tabular()
    val color = Warden.colors.muted
    if (u.wifi == null || u.mobile == null) {
        Text(stringResource(R.string.tool_data_caption, formatBytes(u.up), formatBytes(u.down)), style = style, color = color, maxLines = 1)
        return
    }
    listOf(R.string.tool_data_wifi to u.wifi, R.string.tool_data_mobile to u.mobile).forEach { (label, bytes) ->
        Row {
            Text(stringResource(label), Modifier.width(CAPTION_LABEL_WIDTH), style = style, color = color, maxLines = 1)
            Text(formatBytes(bytes), style = style, color = color, maxLines = 1)
        }
    }
}

private val CAPTION_LABEL_WIDTH = 56.dp

/** Every company seen, with what it does and which of its domains were contacted. */
@Composable
private fun TrackerCompanies(domains: List<String>, range: Range) {
    val c = Warden.colors
    val t = remember(domains) { summarizeTrackers(domains) }
    val byCompany =
        remember(domains) { domains.filter { Trackers.lookup(it) != null }.groupBy { Trackers.lookup(it)!!.company } }
    val period = stringResource(
        when (range) {
            Range.Today -> R.string.dashboard_period_today
            Range.Week -> R.string.dashboard_period_week
            Range.Month -> R.string.dashboard_period_month
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            if (t.trackers == 0) {
                stringResource(R.string.dashboard_trackers_none, period)
            } else {
                stringResource(
                    R.string.dashboard_trackers_sentence,
                    pluralStringResource(R.plurals.dashboard_n_trackers, t.trackers, t.trackers),
                    pluralStringResource(R.plurals.dashboard_n_companies, t.companies, t.companies),
                    period,
                )
            },
            style = Warden.type.title,
        )
        t.byCompany.forEach { (company, categories) ->
            Tile(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(company, Modifier.weight(1f), style = Warden.type.body.copy(fontWeight = FontWeight.Medium))
                    Text(categories.joinToString(" · "), style = Warden.type.label, color = c.muted)
                }
                HorizontalDivider(color = c.outline)
                byCompany[company].orEmpty().sorted().forEach {
                    Text(it, style = Warden.type.label, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@StringRes
private fun about(tool: Tool): Int = when (tool) {
    Tool.Trackers -> R.string.tool_trackers_about
    Tool.Data -> R.string.tool_data_about
}

private const val MAX_APPS = 500
