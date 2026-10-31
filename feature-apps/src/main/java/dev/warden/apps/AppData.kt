package dev.warden.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.warden.design.Radii
import dev.warden.design.Spacing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.warden.data.AppUsage
import dev.warden.data.History
import dev.warden.data.Range
import dev.warden.data.SystemUsage
import dev.warden.design.Tile
import dev.warden.design.Warden
import dev.warden.design.formatBytes
import dev.warden.design.tabular
import kotlinx.coroutines.flow.first

/**
 * How much data this app used today, this week and this month. From Android's own counters when
 * Usage access is on (so it includes time Warden was off), else from Warden's saved history.
 */
@Composable
internal fun DataUsedCard(uid: Int) {
    val c = Warden.colors
    val context = LocalContext.current
    val access = remember { SystemUsage.hasAccess(context) }
    // One AppUsage per range; Wi-Fi/mobile are zero when only Warden's history is available.
    val usage by produceState<List<AppUsage>?>(null, uid, access) {
        value = Range.entries.map { range ->
            if (access) {
                SystemUsage.apps(context, History.start(range), System.currentTimeMillis()).firstOrNull { it.uid == uid }
            } else {
                // Network unknown here; only the up/down totals are shown.
                History.apps(range, limit = APPS_LIMIT).first().firstOrNull { it.uid == uid }
                    ?.let { AppUsage(uid, it.bytesUp, it.bytesDown, 0, 0) }
            } ?: AppUsage(uid, 0, 0, 0, 0)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        // Three tinted boxes, one per period, in the same style as Home's stats.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val periods = listOf(R.string.apps_range_today, R.string.apps_range_week, R.string.apps_range_month)
            val hues = listOf(c.palette[0], c.palette[3], c.palette[4])
            periods.forEachIndexed { i, label ->
                PeriodBox(usage?.get(i)?.let { formatBytes(it.up + it.down) } ?: "–", stringResource(label), hues[i], Modifier.weight(1f))
            }
        }
        val month = usage?.last()
        if (access && month != null && month.wifi + month.mobile > 0) {
            // Wi-Fi vs mobile over 30 days, same colours as the Data screen.
            Tile(Modifier.fillMaxWidth()) {
                val wifiHue = c.palette[0]
                val mobileHue = c.palette[2]
                val total = (month.wifi + month.mobile).toFloat()
                Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(Spacing.xs)).background(c.surfaceVariant)) {
                    if (month.wifi > 0) Box(Modifier.weight(month.wifi / total).fillMaxHeight().background(wifiHue))
                    if (month.mobile > 0) Box(Modifier.weight(month.mobile / total).fillMaxHeight().background(mobileHue))
                }
                Row {
                    Legend(wifiHue, stringResource(R.string.apps_data_wifi), formatBytes(month.wifi), Modifier.weight(1f))
                    Legend(mobileHue, stringResource(R.string.apps_data_mobile), formatBytes(month.mobile), Modifier.weight(1f))
                }
            }
        } else if (!access) {
            Text(stringResource(R.string.apps_data_warden_only), style = Warden.type.label, color = c.muted)
        }
    }
}

@Composable
private fun PeriodBox(value: String, label: String, hue: Color, modifier: Modifier) {
    val c = Warden.colors
    Column(
        modifier
            .clip(RoundedCornerShape(Radii.medium))
            .background(hue.copy(alpha = if (c.isDark) 0.22f else 0.13f))
            .padding(Spacing.m),
    ) {
        Text(value, style = Warden.type.title.tabular(), maxLines = 1)
        Text(label.uppercase(), style = Warden.type.caption, color = c.muted, maxLines = 1)
    }
}

@Composable
private fun Legend(hue: Color, label: String, value: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Box(Modifier.size(10.dp).background(hue, CircleShape))
        Column {
            Text(value, style = Warden.type.body.tabular())
            Text(label.uppercase(), style = Warden.type.caption, color = Warden.colors.muted)
        }
    }
}

private const val APPS_LIMIT = 500
