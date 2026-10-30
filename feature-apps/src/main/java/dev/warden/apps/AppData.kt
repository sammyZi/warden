package dev.warden.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
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
    Tile(Modifier.fillMaxWidth()) {
        Row {
            val labels = listOf(R.string.apps_range_today, R.string.apps_range_week, R.string.apps_range_month)
            labels.forEachIndexed { i, label ->
                Column(Modifier.weight(1f)) {
                    Text(usage?.get(i)?.let { formatBytes(it.up + it.down) } ?: "–", style = Warden.type.title.tabular(), maxLines = 1)
                    Text(stringResource(label).uppercase(), style = Warden.type.caption, color = c.muted)
                }
            }
        }
        HorizontalDivider(color = c.outline)
        val month = usage?.last()
        Text(
            if (access && month != null) {
                stringResource(R.string.apps_data_networks, formatBytes(month.wifi), formatBytes(month.mobile))
            } else {
                stringResource(R.string.apps_data_warden_only)
            },
            style = Warden.type.label,
            color = c.muted,
        )
    }
}

private const val APPS_LIMIT = 500
