package dev.warden.logs

import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import androidx.core.content.FileProvider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import android.content.Intent
import android.content.Context
import kotlinx.coroutines.launch
import java.util.Locale
import java.text.SimpleDateFormat
import dev.warden.data.History
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Icon
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import dev.warden.design.ScreenTitle
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
import dev.warden.design.EmptyState
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
        Column(Modifier.padding(horizontal = Spacing.xl).padding(top = Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle(stringResource(R.string.log_title), Modifier.weight(1f))
                ExportButton(appLabel)
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
                    DomainChoice.AllowEverywhere -> R.string.rule_allow_everywhere
                    DomainChoice.BlockEverywhere -> R.string.rule_block_everywhere
                },
            )
        },
        current.ordinal,
        { Rules.choose(uid, domain, DomainChoice.entries[it]) },
        modifier,
    )
}

/**
 * Export the whole history (up to 30 days) as CSV: share a copy with another app, or save it on the phone.
 * A PCAP isn't offered: Warden never records packets, only who connected where, so it would be made up.
 */
@Composable
private fun ExportButton(appLabel: (Int) -> String) {
    val c = Warden.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    // Writing the CSV takes a few seconds on a long history: show it's working and ignore taps until done.
    var busy by remember { mutableStateOf(false) }
    val busyText = stringResource(R.string.log_export_busy)
    val name = "warden-log-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".csv"
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val rows = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { History.exportCsv(it, appLabel) }
            }.getOrNull()
            busy = false
            toast(context, rows)
        }
    }
    Box {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(enabled = !busy, role = Role.Button) { open = true }
                .semantics { if (busy) stateDescription = busyText },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), color = c.accentInk, strokeWidth = 2.dp)
            } else {
                Icon(painterResource(DesignR.drawable.ph_export), stringResource(R.string.log_export), Modifier.size(24.dp), tint = c.text)
            }
        }
        DropdownMenu(open, { open = false }, containerColor = c.surface) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.log_export_share), style = Warden.type.body) },
                onClick = {
                    open = false
                    busy = true
                    scope.launch {
                        val ok = shareCsv(context, name, appLabel)
                        busy = false
                        if (!ok) toast(context, null)
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.log_export_save), style = Warden.type.body) },
                onClick = {
                    open = false
                    save.launch(name)
                },
            )
        }
    }
}

/** Writes the CSV to a private cache folder and opens the share sheet on a copy of it. */
private suspend fun shareCsv(context: Context, name: String, appLabel: (Int) -> String): Boolean {
    val file = runCatching {
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // one export at a time; no history piling up in the cache
            File(dir, name).also { f -> f.outputStream().use { History.exportCsv(it, appLabel) } }
        }
    }.getOrNull() ?: return false
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
    return true
}

private fun toast(context: Context, rows: Int?) {
    val text = if (rows == null) {
        context.getString(R.string.log_export_failed)
    } else {
        context.resources.getQuantityString(R.plurals.log_exported, rows, rows)
    }
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

/** Its own FileProvider subclass, since an app can declare the plain class only once (the report card has it). */
class ExportFileProvider : FileProvider()
