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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import dev.warden.design.Radii
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

/**
 * Top of the Data screen, in reading order: how much in total, Wi-Fi vs mobile (Android's counters only),
 * when it happened with the busiest moment spelled out, then the apps list below takes over.
 */
@Composable
internal fun DataOverview(apps: List<Usage>, range: Range, system: Boolean) {
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

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        // 1. The answer.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(formatBytes(total), style = Warden.type.display.tabular())
            Text(
                stringResource(if (system) R.string.data_total_system else R.string.data_total, stringResource(period(range))),
                style = Warden.type.body,
                color = c.muted,
            )
            Text(
                stringResource(R.string.data_up_down, formatBytes(up), formatBytes(down)),
                style = Warden.type.label.tabular(),
                color = c.muted,
            )
        }

        // 2. Which network it went over.
        if (system) {
            val wifi = apps.sumOf { it.wifi ?: 0 }
            val mobile = apps.sumOf { it.mobile ?: 0 }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                NetworkCard(
                    DesignR.drawable.ph_wifi_high,
                    c.palette[0],
                    stringResource(R.string.data_wifi),
                    wifi,
                    total,
                    Modifier.weight(1f),
                )
                NetworkCard(
                    DesignR.drawable.ph_cell_signal_full,
                    c.palette[2],
                    stringResource(R.string.data_mobile),
                    mobile,
                    total,
                    Modifier.weight(1f),
                )
            }
        }

        // 3. When, with the busiest slot named so the chart needs no reading.
        if (traffic.any { it > 0 }) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SectionLabel(stringResource(R.string.data_over_time))
                val peak = traffic.indices.maxBy { traffic[it] }
                val step = (System.currentTimeMillis() - History.start(range)) / range.buckets + 1
                Text(
                    stringResource(R.string.data_busiest, slotLabel(range, History.start(range) + peak * step), formatBytes(traffic[peak])),
                    style = Warden.type.body,
                )
                Columns(traffic, c.accentInk, Modifier.fillMaxWidth().height(120.dp), highlight = peak)
                Row {
                    Text(stringResource(startLabel(range)), Modifier.weight(1f), style = Warden.type.caption, color = c.muted)
                    Text(stringResource(R.string.dashboard_activity_end), style = Warden.type.caption, color = c.muted)
                }
            }
        }

        Column {
            SectionLabel(stringResource(R.string.data_all_apps))
            if (system) Text(stringResource(R.string.data_source_system), style = Warden.type.label, color = c.muted)
        }
    }
}

/** Wi-Fi or mobile: the amount, and its share of the total. */
@Composable
private fun NetworkCard(@DrawableRes icon: Int, hue: Color, label: String, bytes: Long, total: Long, modifier: Modifier) {
    val c = Warden.colors
    Column(
        modifier
            .clip(RoundedCornerShape(Radii.medium))
            .background(hue.copy(alpha = if (c.isDark) 0.22f else 0.13f))
            .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(painterResource(icon), null, Modifier.size(18.dp), tint = hue)
            Text(label.uppercase(), style = Warden.type.caption, color = c.muted, maxLines = 1)
        }
        Text(formatBytes(bytes), style = Warden.type.headline.tabular(), maxLines = 1)
        val share = if (total > 0) bytes.toFloat() / total else 0f
        Box(Modifier.fillMaxWidth().height(4.dp).background(c.surfaceVariant, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(hue, RoundedCornerShape(2.dp)))
        }
        Text(stringResource(R.string.data_share, (share * 100).roundToInt()), style = Warden.type.label.tabular(), color = c.muted)
    }
}

/** "2 PM" today, "Tue 6 PM" over a week, "Oct 28" over a month. */
private fun slotLabel(range: Range, time: Long): String {
    val skeleton = when (range) {
        Range.Today -> "ha"
        Range.Week -> "EEEha"
        Range.Month -> "MMMd"
    }
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
}

/** Bytes per bucket as rounded columns, drawn in left to right. The busiest column is full height. */
@Composable
private fun Columns(values: List<Long>, color: Color, modifier: Modifier, highlight: Int = -1) {
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
            val fill = if (highlight < 0 || i == highlight) color else color.copy(alpha = 0.45f)
            if (h > 0) drawRoundRect(fill, Offset(x, size.height - h), Size(w, h), r)
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

private const val CHART_MS = 900
