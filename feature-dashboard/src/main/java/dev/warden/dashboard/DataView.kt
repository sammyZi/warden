package dev.warden.dashboard

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.warden.data.AppTotals
import dev.warden.data.History
import dev.warden.data.Range
import dev.warden.design.Motion
import dev.warden.design.SectionLabel
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.formatBytes
import dev.warden.design.tabular
import kotlin.math.roundToInt

/** Colours for the two directions, used by the overview and by each app's bar. */
@Composable
internal fun sentColor(): Color = Warden.colors.palette[4]

@Composable
internal fun receivedColor(): Color = Warden.colors.accentInk

/**
 * Top of the Data screen: the total as the hero, sent vs received, traffic over time as columns,
 * and the biggest apps as one stacked bar with a legend.
 */
@Composable
internal fun DataOverview(apps: List<AppTotals>, range: Range, appLabel: (Int) -> String) {
    val c = Warden.colors
    val up = apps.sumOf { it.bytesUp }
    val down = apps.sumOf { it.bytesDown }
    val total = up + down
    val traffic by remember(range) { History.traffic(range) }.collectAsState(emptyList())

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Column {
            Text(formatBytes(total), style = Warden.type.display.tabular())
            Text(stringResource(R.string.data_total, stringResource(period(range))), style = Warden.type.body, color = c.muted)
        }

        // Sent vs received, one bar split in two.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SplitBar(up, down, Modifier.fillMaxWidth().height(12.dp))
            Row {
                Legend(sentColor(), stringResource(R.string.data_sent), formatBytes(up), Modifier.weight(1f))
                Legend(receivedColor(), stringResource(R.string.data_received), formatBytes(down), Modifier.weight(1f))
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

        val top = apps.filter { it.bytesUp + it.bytesDown > 0 }.sortedByDescending { it.bytesUp + it.bytesDown }
        if (top.isNotEmpty() && total > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SectionLabel(stringResource(R.string.data_by_app))
                val shown = top.take(TOP_SHARE)
                val rest = total - shown.sumOf { it.bytesUp + it.bytesDown }
                val parts = shown.mapIndexed { i, a -> c.palette[i % c.palette.size] to (a.bytesUp + a.bytesDown) } +
                    listOfNotNull((c.outline to rest).takeIf { rest > 0 })
                StackedBar(parts, Modifier.fillMaxWidth().height(20.dp))
                shown.forEachIndexed { i, a ->
                    ShareRow(c.palette[i % c.palette.size], appLabel(a.uid), a.bytesUp + a.bytesDown, total)
                }
                if (rest > 0) ShareRow(c.outline, stringResource(R.string.data_other), rest, total)
            }
        }
        SectionLabel(stringResource(R.string.data_all_apps))
    }
}

@Composable
internal fun SplitBar(up: Long, down: Long, modifier: Modifier) {
    val sum = (up + down).coerceAtLeast(1)
    Row(modifier.clip(RoundedCornerShape(Spacing.xs)).background(Warden.colors.surfaceVariant)) {
        if (up > 0) Box(Modifier.weight(up.toFloat() / sum).fillMaxHeight().background(sentColor()))
        if (down > 0) Box(Modifier.weight(down.toFloat() / sum).fillMaxHeight().background(receivedColor()))
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
