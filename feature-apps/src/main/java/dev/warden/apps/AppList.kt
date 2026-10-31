package dev.warden.apps

import dev.warden.design.ScreenTitle
import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.warden.design.BarSpacer
import dev.warden.design.EmptyState
import dev.warden.design.FilterChip
import dev.warden.design.Radii
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.staggerIn
import dev.warden.design.tabular
import dev.warden.engine.NetEvent
import dev.warden.rules.AppRule
import dev.warden.rules.AppRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.warden.design.R as DesignR

/** One row: an installed app (or a UID seen in traffic), with what it did recently. */
private class AppRow(val uid: Int, val packages: List<String>, val connections: Int, val domains: Int, val blocked: Int, val events: Int) {
    // Lookups count: an app that only resolved names has still been active.
    val active get() = events > 0
}

private enum class AppsFilter { Active, All, Ruled }

/**
 * Every app that can use the internet, not only the ones that have: so an app can be blocked before
 * it ever connects. Search by name; filter to apps seen recently, all of them, or ones with a rule.
 */
@Composable
internal fun AppList(
    events: List<NetEvent>,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    shared: Shared,
    listState: LazyListState,
    firstAppearance: Boolean,
    onOpen: (Int) -> Unit,
) {
    val c = Warden.colors
    val context = LocalContext.current
    val installed by produceState(emptyMap<Int, List<String>>()) {
        value = withContext(Dispatchers.IO) {
            // Names are looked up here, off the main thread, so the first scroll through All apps doesn't stutter.
            internetApps(context.packageManager).also { apps -> apps.keys.forEach { appLabel(it) } }
        }
    }
    val rules by AppRules.rules.collectAsState()
    val excluded by AppRules.excluded.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppsFilter.Active) }

    val all = remember(events, installed) {
        val seen = events.groupBy { it.uid }
        // Warden's own lookups are recorded too; it isn't one of "your apps".
        (installed.keys + seen.keys - Process.myUid()).map { uid ->
            val list = seen[uid].orEmpty()
            AppRow(
                uid = uid,
                packages = installed[uid].orEmpty(),
                connections = list.count { it.kind != "dns" },
                domains = list.mapNotNull { it.domain }.distinct().size,
                blocked = list.count { it.blocked },
                events = list.size,
            )
        }
    }
    val shown = remember(all, query, filter, rules, excluded) {
        all.filter { a ->
            val ruled = rules[a.uid] != null || a.packages.any { it in excluded }
            when (filter) {
                AppsFilter.Active -> a.active
                AppsFilter.All -> true
                AppsFilter.Ruled -> ruled
            } && (query.isBlank() || appLabel(a.uid).contains(query.trim(), ignoreCase = true))
        }.sortedWith(
            // All apps reads A to Z; the other views put the busiest first.
            if (filter == AppsFilter.All) {
                compareBy { appLabel(it.uid).lowercase() }
            } else {
                compareByDescending<AppRow> { it.connections + it.blocked }.thenBy { appLabel(it.uid).lowercase() }
            },
        )
    }
    val alphabetical = filter == AppsFilter.All && query.isBlank() && shown.isNotEmpty()
    val most = shown.maxOfOrNull { it.connections }?.coerceAtLeast(1) ?: 1

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = Spacing.s, bottom = Spacing.l, end = if (alphabetical) INDEX_WIDTH else 0.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.m)) {
                    ScreenTitle(stringResource(R.string.apps_title))
                    Text(
                        pluralStringResource(R.plurals.apps_summary_all, all.size, all.size),
                        style = Warden.type.body.tabular(),
                        color = c.muted,
                    )
                }
            }
            item { SearchField(query) { query = it } }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.xl),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier.padding(vertical = Spacing.m),
                ) {
                    AppsFilter.entries.forEach { f ->
                        item {
                            FilterChip(
                                stringResource(
                                    when (f) {
                                        AppsFilter.Active -> R.string.apps_filter_active
                                        AppsFilter.All -> R.string.apps_filter_all
                                        AppsFilter.Ruled -> R.string.apps_filter_ruled
                                    },
                                ),
                                filter == f,
                                { filter = f },
                            )
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    // Nothing active yet reads as "turn Warden on"; a search or filter with no hits says so instead.
                    val idle = filter == AppsFilter.Active && query.isBlank()
                    EmptyState(
                        DesignR.drawable.ph_squares_four_light,
                        stringResource(if (idle) R.string.apps_empty_title else R.string.apps_none_match),
                        stringResource(if (idle) R.string.apps_empty_body else R.string.apps_none_match_body),
                        Modifier.fillMaxWidth().padding(top = Spacing.xl),
                    )
                }
            }
            // One panel with thin dividers rather than a stack of separate cards.
            itemsIndexed(shown, key = { _, a -> a.uid }) { index, a ->
                AppListRow(
                    a,
                    appLabel,
                    appIcon,
                    shared,
                    rules[a.uid],
                    a.packages.any { it in excluded },
                    if (a.active) a.connections / most.toFloat() else null,
                    c.palette[index % c.palette.size],
                    first = index == 0,
                    last = index == shown.lastIndex,
                    modifier = Modifier.staggerIn(index, firstAppearance),
                ) { onOpen(a.uid) }
            }
            item { BarSpacer() }
        }
        if (alphabetical) {
            // Rows start after the title, search and filter items.
            AlphabetIndex(shown.map { appLabel(it.uid) }, listState, firstRow = HEADER_ITEMS, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val c = Warden.colors
    BasicTextField(
        query,
        onQuery,
        Modifier
            .padding(horizontal = Spacing.xl)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.small))
            .background(c.surfaceVariant)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        singleLine = true,
        textStyle = Warden.type.body.copy(color = c.text),
        cursorBrush = SolidColor(c.accentInk),
        decorationBox = { field ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Icon(painterResource(DesignR.drawable.ph_magnifying_glass), null, Modifier.size(18.dp), tint = c.muted)
                Box {
                    if (query.isEmpty()) Text(stringResource(R.string.apps_search), style = Warden.type.body, color = c.muted)
                    field()
                }
            }
        },
    )
}

@Composable
private fun AppListRow(
    a: AppRow,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    shared: Shared,
    rule: AppRule?,
    skipped: Boolean,
    share: Float?,
    hue: androidx.compose.ui.graphics.Color,
    first: Boolean,
    last: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = Warden.colors
    val r = Radii.medium
    val shape =
        RoundedCornerShape(if (first) r else 0.dp, if (first) r else 0.dp, if (last) r else 0.dp, if (last) r else 0.dp)
    Column(modifier.padding(horizontal = Spacing.l)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.surface)
                .clickable(onClick = onClick)
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(a.uid, appIcon, 40.dp, shared.icon(a.uid))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(
                            appLabel(a.uid),
                            shared.name(a.uid).weight(1f, fill = false),
                            style = Warden.type.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        RuleTag(rule, skipped, a.blocked)
                    }
                    Text(
                        if (a.active) {
                            pluralStringResource(R.plurals.apps_connections, a.connections, a.connections) + "  ·  " +
                                pluralStringResource(R.plurals.apps_domains, a.domains, a.domains)
                        } else {
                            stringResource(R.string.apps_no_traffic)
                        },
                        style = Warden.type.label.tabular(),
                        color = c.muted,
                        maxLines = 1,
                    )
                }
                Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(16.dp), tint = c.muted)
            }
            if (share != null) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(c.surfaceVariant, RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).fillMaxHeight().background(hue, RoundedCornerShape(2.dp)))
                }
            }
        }
        if (!last) HorizontalDivider(Modifier.background(c.surface).padding(horizontal = Spacing.l), color = c.outline)
    }
}

/** The app's rule if it has one ("Offline", "No Wi-Fi", "Skips Warden"), else how much was blocked. */
@Composable
private fun RuleTag(rule: AppRule?, skipped: Boolean, blocked: Int) {
    val c = Warden.colors
    val text = when {
        skipped -> stringResource(R.string.apps_rule_skipped)
        rule?.blockWifi == true && rule.blockCellular -> stringResource(R.string.apps_rule_offline)
        rule?.blockWifi == true -> stringResource(R.string.apps_rule_no_wifi)
        rule?.blockCellular == true -> stringResource(R.string.apps_rule_no_mobile)
        else -> null
    }
    if (text == null) {
        if (blocked > 0) BlockedTag(blocked)
        return
    }
    val hue = if (skipped) c.warning else c.blocked
    Text(
        text,
        Modifier.clip(RoundedCornerShape(Radii.small)).background(hue.copy(alpha = 0.14f)).padding(horizontal = Spacing.s, vertical = 2.dp),
        style = Warden.type.caption,
        color = hue,
    )
}

/** Installed apps that can use the internet, by UID (apps sharing a UID come as one). Warden itself excluded. */
private fun internetApps(pm: PackageManager): Map<Int, List<String>> {
    val packages: List<PackageInfo> = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
    return packages
        .filter { p ->
            val uid = p.applicationInfo?.uid ?: return@filter false
            // System UIDs (shared by Settings and other built-ins) already show as one "Android system" row.
            uid >= Process.FIRST_APPLICATION_UID && uid != Process.myUid() &&
                p.requestedPermissions?.contains(Manifest.permission.INTERNET) == true &&
                // System services without a launcher icon are noise here; they still show once they connect.
                pm.getLaunchIntentForPackage(p.packageName) != null
        }
        .groupBy({ it.applicationInfo!!.uid }, { it.packageName })
}

private val INDEX_WIDTH = 24.dp
private const val HEADER_ITEMS = 3
