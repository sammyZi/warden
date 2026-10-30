package dev.warden.dashboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.warden.data.AppTotals
import dev.warden.data.History
import dev.warden.data.Range
import dev.warden.data.SystemUsage
import dev.warden.design.IconBadge
import dev.warden.design.Motion
import dev.warden.design.SectionLabel
import dev.warden.design.Spacing
import dev.warden.design.Tile
import dev.warden.design.Warden
import dev.warden.design.WardenButton
import dev.warden.design.formatBytes
import dev.warden.design.tabular
import kotlin.math.roundToInt
import dev.warden.design.R as DesignR

/** One app's data for the Data screen. Wi-Fi and mobile are known only from Android's counters. */
internal class Usage(val uid: Int, val up: Long, val down: Long, val wifi: Long? = null, val mobile: Long? = null) {
    val total get() = up + down
}

internal fun List<AppTotals>.asUsage() = map { Usage(it.uid, it.bytesUp, it.bytesDown) }

/** Whether Warden may read Android's data counters, and a way to ask. Re-checked when settings returns. */
@Composable
internal fun rememberUsageAccess(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var access by remember { mutableStateOf(SystemUsage.hasAccess(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        access = SystemUsage.hasAccess(context)
    }
    return access to {
        // Straight to Warden's switch where the phone supports it, else the full list.
        try {
            launcher.launch(usageAccessIntent(context, direct = true))
        } catch (_: ActivityNotFoundException) {
            launcher.launch(usageAccessIntent(context, direct = false))
        }
    }
}

private fun usageAccessIntent(context: Context, direct: Boolean) = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
    if (direct && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) data = Uri.parse("package:${context.packageName}")
}

/** Per-app usage from Android's counters for [range], or null while loading / without access. */
@Composable
internal fun systemApps(range: Range, access: Boolean): List<Usage>? {
    val context = LocalContext.current
    val apps by produceState<List<Usage>?>(null, range, access) {
        value = if (!access) {
            null
        } else {
            SystemUsage.apps(context, History.start(range), System.currentTimeMillis())
                .map { Usage(it.uid, it.up, it.down, it.wifi, it.mobile) }
        }
    }
    return apps
}

/** Shown on the Data screen until Usage access is on. */
@Composable
internal fun UsageAccessCard(onAllow: () -> Unit) {
    val c = Warden.colors
    Tile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            IconBadge(DesignR.drawable.ph_cell_signal_full, c.palette[0])
            Text(
                stringResource(R.string.usage_access_title),
                Modifier.weight(1f),
                style = Warden.type.body.copy(fontWeight = FontWeight.Medium),
            )
        }
        Text(stringResource(R.string.usage_access_body), style = Warden.type.label, color = c.muted)
        WardenButton(stringResource(R.string.usage_access_button), onAllow, Modifier.fillMaxWidth())
    }
}

/** Colours for the two directions and the two networks, used by the overview and by each app's bar. */
@Composable
internal fun sentColor(): Color = Warden.colors.palette[4]

@Composable
internal fun receivedColor(): Color = Warden.colors.accentInk

@Composable
private fun wifiColor(): Color = Warden.colors.palette[0]

@Composable
private fun mobileColor(): Color = Warden.colors.palette[2]

/**
 * Top of the Data screen: the total as the hero, sent vs received, Wi-Fi vs mobile (Android's counters
 * only), traffic over time as columns, and the biggest apps as one stacked bar with a legend.
 */
@Composable
internal fun DataOverview(apps: List<Usage>, range: Range, appLabel: (Int) -> String, system: Boolean) {
    val c = Warden.colors
    val context = LocalContext.current
    val up = apps.sumOf { it.up }
    val down = apps.sumOf { it.down }
    val total = up + down
    val ownTraffic by remember(range) { History.traffic(range) }.collectAsState(emptyList())
    val systemTraffic by produceState(emptyList<Long>(), range, system) {
        if (system) value = SystemUsage.traffic(context, History.start(range), range.buckets)
    }
    val traffic = if (system) systemTraffic else ownTraffic

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Column {
            Text(formatBytes(total), style = Warden.type.display.tabular())
            Text(
                stringResource(if (system) R.string.data_total_system else R.string.data_total, stringResource(period(range))),
                style = Warden.type.body,
                color = c.muted,
            )
            if (system) Text(stringResource(R.string.data_source_system), style = Warden.type.label, color = c.muted)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            TwoBar(up, down, sentColor(), receivedColor(), Modifier.fillMaxWidth().height(12.dp))
            Row {
                Legend(sentColor(), stringResource(R.string.data_sent), formatBytes(up), Modifier.weight(1f))
                Legend(receivedColor(), stringResource(R.string.data_received), formatBytes(down), Modifier.weight(1f))
            }
        }

        if (system) {
            val wifi = apps.sumOf { it.wifi ?: 0 }
            val mobile = apps.sumOf { it.mobile ?: 0 }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SectionLabel(stringResource(R.string.data_by_network))
                TwoBar(wifi, mobile, wifiColor(), mobileColor(), Modifier.fillMaxWidth().height(12.dp))
                Row {
                    Legend(wifiColor(), stringResource(R.string.data_wifi), formatBytes(wifi), Modifier.weight(1f))
                    Legend(mobileColor(), stringResource(R.string.data_mobile), formatBytes(mobile), Modifier.weight(1f))
                }
            }
        }

        if (traffic.any { it > 0 }) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SectionLabel(stringResource(R.string.data_over_time))
                Columns(traffic, receivedColor(), Modifier.fillMaxWidth().height(120.dp))
                Row {
                    Text(stringResource(startLabel(range)), Modifier.weight(1f), style = Warden.type.caption, color = c.muted)
                    Text(stringResource(R.string.dashboard_activity_end), style = Warden.type.caption, color = c.muted)
                }
            }
        }

        val top = apps.filter { it.total > 0 }.sortedByDescending { it.total }
        if (top.isNotEmpty() && total > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SectionLabel(stringResource(R.string.data_by_app))
                val shown = top.take(TOP_SHARE)
                val rest = total - shown.sumOf { it.total }
                val parts = shown.mapIndexed { i, a -> c.palette[i % c.palette.size] to a.total } +
                    listOfNotNull((c.outline to rest).takeIf { rest > 0 })
                StackedBar(parts, Modifier.fillMaxWidth().height(20.dp))
                shown.forEachIndexed { i, a ->
                    ShareRow(c.palette[i % c.palette.size], appLabel(a.uid), a.total, total)
                }
                if (rest > 0) ShareRow(c.outline, stringResource(R.string.data_other), rest, total)
            }
        }
        SectionLabel(stringResource(R.string.data_all_apps))
    }
}

/** Sent then received, as one bar. */
@Composable
internal fun SplitBar(up: Long, down: Long, modifier: Modifier) = TwoBar(up, down, sentColor(), receivedColor(), modifier)

@Composable
private fun TwoBar(a: Long, b: Long, colorA: Color, colorB: Color, modifier: Modifier) {
    val sum = (a + b).coerceAtLeast(1)
    Row(modifier.clip(RoundedCornerShape(Spacing.xs)).background(Warden.colors.surfaceVariant)) {
        if (a > 0) Box(Modifier.weight(a.toFloat() / sum).fillMaxHeight().background(colorA))
        if (b > 0) Box(Modifier.weight(b.toFloat() / sum).fillMaxHeight().background(colorB))
    }
}

@Composable
private fun StackedBar(parts: List<Pair<Color, Long>>, modifier: Modifier) {
    val sum = parts.sumOf { it.second }.coerceAtLeast(1)
    Row(modifier.clip(RoundedCornerShape(Spacing.s)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        parts.filter { it.second > 0 }.forEach { (color, bytes) ->
            Box(Modifier.weight(bytes.toFloat() / sum).fillMaxHeight().background(color))
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, value: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Column {
            Text(value, style = Warden.type.title.tabular())
            Text(label.uppercase(), style = Warden.type.caption, color = Warden.colors.muted)
        }
    }
}

@Composable
private fun ShareRow(color: Color, label: String, bytes: Long, total: Long) {
    val c = Warden.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.size(Spacing.s))
        Text(label, Modifier.weight(1f), style = Warden.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatBytes(bytes), style = Warden.type.label.tabular(), color = c.muted)
        Text(
            "${(bytes * 100f / total).roundToInt()}%",
            Modifier.padding(start = Spacing.m).size(width = 40.dp, height = 20.dp),
            style = Warden.type.label.tabular(),
        )
    }
}

/** Bytes per bucket as rounded columns, drawn in left to right. The busiest column is full height. */
@Composable
private fun Columns(values: List<Long>, color: Color, modifier: Modifier) {
    val c = Warden.colors
    val reduceMotion = Warden.reduceMotion
    val reveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, Motion.emphasized(CHART_MS)) }
    val peak = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val description = stringResource(R.string.data_chart_description, formatBytes(peak))
    Canvas(modifier.semantics { contentDescription = description }) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        val r = CornerRadius(minOf(w / 2, 4.dp.toPx()))
        values.forEachIndexed { i, v ->
            val x = i * (w + gap)
            // Each column grows once the reveal sweep reaches it.
            val local = ((reveal.value * values.size) - i).coerceIn(0f, 1f)
            drawRoundRect(c.surfaceVariant, Offset(x, 0f), Size(w, size.height), r)
            val h = size.height * (v.toFloat() / peak) * local
            if (h > 0) drawRoundRect(color, Offset(x, size.height - h), Size(w, h), r)
        }
    }
}

private fun period(range: Range) = when (range) {
    Range.Today -> R.string.dashboard_period_today
    Range.Week -> R.string.dashboard_period_week
    Range.Month -> R.string.dashboard_period_month
}

private fun startLabel(range: Range) = when (range) {
    Range.Today -> R.string.dashboard_activity_midnight
    Range.Week -> R.string.dashboard_activity_week_ago
    Range.Month -> R.string.dashboard_activity_month_ago
}

private const val TOP_SHARE = 5
private const val CHART_MS = 900
