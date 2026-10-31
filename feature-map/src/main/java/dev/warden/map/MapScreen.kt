package dev.warden.map

import dev.warden.design.ScreenTitle
import android.content.Context
import android.telephony.TelephonyManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import dev.warden.design.Radii
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import dev.warden.data.History
import dev.warden.data.Range
import dev.warden.rules.Trackers
import androidx.compose.runtime.collectAsState
import dev.warden.design.WardenButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.runtime.LaunchedEffect
import dev.warden.design.IconBadge
import dev.warden.design.Motion
import dev.warden.design.SectionLabel
import dev.warden.design.BarSpacer
import dev.warden.design.Spacing
import dev.warden.design.StatSquare
import dev.warden.design.Warden
import dev.warden.design.tabular
import dev.warden.engine.NetEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import dev.warden.design.R as DesignR

@Composable
fun MapScreen(
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    onOpenApp: (Int) -> Unit,
    modifier: Modifier = Modifier,
    focusApp: Int? = null,
    onFocusApplied: () -> Unit = {},
) {
    val context = LocalContext.current
    val geo by produceState<Geo?>(null) { value = withContext(Dispatchers.IO) { loadGeo(context) } }
    val g = geo ?: return Box(modifier.fillMaxSize()) // ~150ms load, not worth a spinner

    // Filters: time span, apps (empty = all), blocked only, trackers only.
    // ponytail: plain remember, so a rotation resets them.
    var scope by remember { mutableStateOf(Scope.Session) }
    var appFilter by remember { mutableStateOf(emptySet<Int>()) }
    // "See on map" from an app's detail: show just that app, over today (this session may be empty).
    LaunchedEffect(focusApp) {
        val uid = focusApp ?: return@LaunchedEffect
        appFilter = setOf(pickerUid(uid))
        scope = Scope.Today
        onFocusApplied()
    }
    var blockedOnly by remember { mutableStateOf(false) }
    var trackersOnly by remember { mutableStateOf(false) }
    // Tagged with the scope they were loaded for, so the reveal below restarts only once new rows arrive.
    val loaded by remember(scope) { History.connectionsSince(scope.since()).distinctUntilChanged().map { scope to it } }
        .collectAsState(scope to emptyList())
    val events = loaded.second
    val topApps = remember(events) {
        events.groupingBy { pickerUid(it.uid) }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
    // Up to 5,000 rows of geo lookups: grouped off the main thread.
    // Paired with the filters that produced it: when that changes, the globe and list animate in again.
    val result by produceState(Any() to emptyList<Destination>(), events, g, appFilter, blockedOnly, trackersOnly) {
        val filters = listOf(loaded.first, appFilter, blockedOnly, trackersOnly)
        val next = withContext(Dispatchers.Default) {
            val shown = events.filter { e ->
                (appFilter.isEmpty() || pickerUid(e.uid) in appFilter) &&
                    (!blockedOnly || e.blocked) &&
                    (!trackersOnly || Trackers.lookup(e.domain) != null)
            }
            aggregate(shown, g, homeCountry(context)?.let(g::country))
        }
        // Room can emit the same rows twice right after a switch; publishing that again replayed the animations.
        if (filters != value.first || next.signature() != value.second.signature()) value = filters to next
    }
    val destinations = result.second
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    // The app picker sheet; the globe stops its idle spin while it's open so the list scrolls smoothly.
    var picking by rememberSaveable { mutableStateOf(false) }
    // Lives outside the detail transition so coming back from a country keeps the scroll position.
    val listState = rememberLazyListState()
    BackHandler(enabled = detail != null) { detail = null }
    val reduceMotion = Warden.reduceMotion

    AnimatedContent(
        detail,
        modifier.fillMaxSize(),
        transitionSpec = {
            val dir = if (targetState != null) 1 else -1
            if (reduceMotion) {
                fadeIn(Motion.emphasized(0)) togetherWith fadeOut(Motion.emphasized(0))
            } else {
                (slideInHorizontally(Motion.emphasized(Motion.LONG)) { dir * it / 4 } + fadeIn(Motion.emphasized(Motion.LONG)))
                    .togetherWith(
                        slideOutHorizontally(Motion.emphasized(Motion.LONG)) { -dir * it / 6 } +
                            fadeOut(Motion.emphasized(Motion.SHORT)),
                    )
            }
        },
        label = "map",
    ) { iso ->
        val d = destinations.firstOrNull { it.country.iso == iso }
        if (iso == null || d == null) {
            Overview(
                g, destinations, result.first, listState, spin = !picking, onOpen = { detail = it },
                filter = {
                    Row(
                        Modifier.padding(horizontal = Spacing.xl),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppFilter(topApps, appFilter, { appFilter = it }, appLabel, appIcon, picking) { picking = it }
                        TimeFilter(scope, { scope = it }, blockedOnly, { blockedOnly = it }, trackersOnly, { trackersOnly = it })
                    }
                },
            )
        } else {
            CountryDetail(d, appLabel, appIcon, onBack = { detail = null }, onOpenApp = onOpenApp)
        }
    }
}

@Composable
private fun Overview(
    g: Geo,
    destinations: List<Destination>,
    revealKey: Any,
    listState: LazyListState,
    spin: Boolean,
    onOpen: (String) -> Unit,
    filter: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val c = Warden.colors
    val home = remember(g) { homeCountry(context)?.let(g::country) }
    val camera = remember(g) { GlobeCamera(home?.lat ?: DEFAULT_LAT, home?.lon ?: 0f) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val total = destinations.sumOf { it.connections }.coerceAtLeast(1)
    val reduceMotion = Warden.reduceMotion
    // One reveal for the whole screen: arcs, dots and every share ring grow together, once per filter change.
    // A fresh progress per key, created at 0 in the same frame as the new data, so no frame shows it fully drawn first.
    val reveal = remember(revealKey) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, Motion.emphasized(REVEAL_MS)) }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item {
            Column(Modifier.padding(horizontal = Spacing.xl).padding(top = Spacing.s)) {
                ScreenTitle(stringResource(R.string.map_title_sans))
            }
        }
        item { filter() }
        item {
            Globe(
                geo = g,
                home = home,
                destinations = destinations,
                progress = { reveal.value },
                spin = spin,
                selected = selected,
                camera = camera,
                onSelect = { selected = it },
                description = pluralStringResource(R.plurals.map_globe_description, destinations.size, destinations.size),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
        }
        item {
            val d = destinations.firstOrNull { it.country.iso == selected }
            if (d == null) {
                Text(
                    stringResource(if (destinations.isEmpty()) R.string.map_empty else R.string.map_hint),
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.s),
                    style = Warden.type.label,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                )
            } else {
                // Tapped on the globe: a one-line summary that opens the full traffic view.
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(d.country.iso) }.padding(horizontal = Spacing.xl, vertical = Spacing.l),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Swatch(countryHue(d.country.iso, c.palette))
                    Column(Modifier.weight(1f)) {
                        Text(countryName(d.country.iso), style = Warden.type.title)
                        Text(
                            pluralStringResource(R.plurals.map_connections, d.connections, d.connections),
                            style = Warden.type.body,
                            color = c.muted,
                        )
                    }
                    Text(stringResource(R.string.map_see_all).uppercase(), style = Warden.type.caption, color = c.accentInk)
                }
            }
        }
        if (destinations.isNotEmpty()) {
            item {
                Row(Modifier.padding(horizontal = Spacing.xl).padding(top = Spacing.l, bottom = Spacing.s)) {
                    SectionLabel(stringResource(R.string.map_countries), Modifier.weight(1f))
                    SectionLabel(destinations.size.toString())
                }
            }
        }
        itemsIndexed(destinations, key = { _, d -> d.country.iso }) { i, d ->
            Box {
                CountryRow(d, total, first = i == 0, last = i == destinations.lastIndex, progress = { reveal.value }) {
                    selected = d.country.iso
                    scope.launch { camera.focus(d.country.lat, d.country.lon) }
                    onOpen(d.country.iso)
                }
            }
        }
        item { BarSpacer() }
    }
}

/** A country as a card: code badge in its hue, name and counts, share of all traffic, share bar. */
/** One row of the countries table. The rows join into a single panel with thin dividers; a ring shows each share. */
@Composable
private fun CountryRow(d: Destination, total: Int, first: Boolean, last: Boolean, progress: () -> Float, onClick: () -> Unit) {
    val c = Warden.colors
    val hue = countryHue(d.country.iso, c.palette)
    val r = Radii.medium
    val shape =
        RoundedCornerShape(if (first) r else 0.dp, if (first) r else 0.dp, if (last) r else 0.dp, if (last) r else 0.dp)
    Column(Modifier.padding(horizontal = Spacing.l)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.surface)
                .clickable(onClick = onClick)
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            CountryMark(d.country.iso, hue)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(
                        countryName(d.country.iso),
                        Modifier.weight(1f, fill = false),
                        style = Warden.type.body.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (d.blocked > 0) BlockedTag(d.blocked)
                }
                Text(
                    pluralStringResource(R.plurals.map_connections, d.connections, d.connections) + " · " +
                        pluralStringResource(R.plurals.map_n_apps, d.apps, d.apps),
                    style = Warden.type.label.tabular(),
                    color = c.muted,
                    maxLines = 1,
                )
            }
            ShareRing(d.connections / total.toFloat(), hue, progress)
        }
        if (!last) HorizontalDivider(Modifier.background(c.surface), color = c.outline)
    }
}

/** Share of all connections as a thin ring with the percentage inside. Fills with the globe's reveal. */
@Composable
private fun ShareRing(share: Float, hue: Color, progress: () -> Float) {
    val c = Warden.colors
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(c.surfaceVariant, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(hue, -90f, 360f * share * progress(), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${(share * 100).roundToInt()}%", style = Warden.type.label.tabular())
    }
}

/** The country's flag; CDN edges have no flag, so they keep a short tinted code. */
@Composable
private fun CountryMark(iso: String, hue: Color) {
    val flag = flagOf(iso)
    Box(Modifier.size(40.dp).background(hue.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
        if (flag != null) {
            Text(flag, fontSize = 22.sp)
        } else {
            Text(iso.removePrefix(Geo.EDGE_PREFIX), style = Warden.type.label.copy(fontWeight = FontWeight.SemiBold), color = hue)
        }
    }
}

/** Flag emoji from a two-letter country code (regional indicator pairs); null for edges and unknowns. */
internal fun flagOf(iso: String): String? {
    if (iso.length != 2 || !iso.all { it in 'A'..'Z' }) return null
    return iso.map { String(Character.toChars(REGIONAL_A + (it - 'A'))) }.joinToString("")
}

private const val REGIONAL_A = 0x1F1E6

/** Everything that went to one country: totals, the apps that went there, every connection. */
@Composable
private fun CountryDetail(
    d: Destination,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    onBack: () -> Unit,
    onOpenApp: (Int) -> Unit,
) {
    val c = Warden.colors
    val hue = countryHue(d.country.iso, c.palette)
    val time = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }
    val byApp = remember(d) { d.events.groupingBy { it.uid }.eachCount().entries.sortedByDescending { it.value } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onBack).padding(horizontal = Spacing.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(
                    painterResource(DesignR.drawable.ph_arrow_left),
                    stringResource(R.string.map_back),
                    Modifier.size(20.dp),
                    tint = c.text,
                )
                SectionLabel(stringResource(R.string.map_title_sans))
            }
        }
        item {
            Column(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.s)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Swatch(hue)
                    SectionLabel(d.country.iso.removePrefix(Geo.EDGE_PREFIX))
                }
                Text(countryName(d.country.iso), style = Warden.type.headline)
                if (d.country.iso.startsWith(Geo.EDGE_PREFIX)) {
                    Text(stringResource(R.string.map_edge_explain), style = Warden.type.body, color = c.muted)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                StatSquare(DesignR.drawable.ph_pulse, hue, d.connections, stringResource(R.string.map_sq_connections), Modifier.weight(1f))
                StatSquare(
                    DesignR.drawable.ph_squares_four,
                    c.palette[3],
                    d.apps,
                    stringResource(R.string.map_sq_apps),
                    Modifier.weight(1f),
                )
                StatSquare(
                    DesignR.drawable.ph_globe_hemisphere_west,
                    c.palette[4],
                    d.domains,
                    stringResource(R.string.map_sq_domains),
                    Modifier.weight(1f),
                )
                StatSquare(DesignR.drawable.ph_prohibit, c.blocked, d.blocked, stringResource(R.string.map_sq_blocked), Modifier.weight(1f))
            }
        }
        item { SectionLabel(stringResource(R.string.map_apps), Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.s)) }
        items(byApp, key = { "app" + it.key }) { (uid, count) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpenApp(uid) }.padding(horizontal = Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                AppIcon(uid, appIcon)
                Text(appLabel(uid), Modifier.weight(1f), style = Warden.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(count.toString(), style = Warden.type.caption.tabular(), color = c.muted)
                Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(16.dp), tint = c.muted)
            }
        }
        item {
            SectionLabel(
                pluralStringResource(R.plurals.map_all_traffic, d.events.size, d.events.size),
                Modifier.padding(horizontal = Spacing.xl).padding(top = Spacing.xl, bottom = Spacing.s),
            )
        }
        items(d.events, key = { it.id }) { e ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.xl, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        e.domain ?: e.target,
                        style = Warden.type.body,
                        color = if (e.blocked) c.blocked else c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(appLabel(e.uid), e.kind, e.target.substringBeforeLast(':').trim('[', ']'), time.format(Date(e.time)))
                            .joinToString(" · ").uppercase(),
                        style = Warden.type.caption.tabular(),
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (e.blocked) BlockedTag(1)
            }
            HorizontalDivider(Modifier.padding(horizontal = Spacing.xl), color = c.outline)
        }
        item { BarSpacer() }
    }
}

@Composable
private fun AppIcon(uid: Int, appIcon: (Int) -> ImageBitmap?) {
    val icon = appIcon(uid)
    if (icon != null) Image(icon, null, Modifier.size(32.dp)) else IconBadge(DesignR.drawable.ph_squares_four, Warden.colors.muted)
}

@Composable
private fun Swatch(color: Color) = Box(Modifier.size(10.dp).background(color, CircleShape))

@Composable
private fun BlockedTag(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(painterResource(DesignR.drawable.ph_prohibit), null, Modifier.size(14.dp), tint = Warden.colors.blocked)
        Text(
            pluralStringResource(R.plurals.map_blocked, count, count).uppercase(),
            style = Warden.type.caption.tabular(),
            color = Warden.colors.blocked,
        )
    }
}

@Composable
private fun countryName(iso: String): String = when (iso.removePrefix(Geo.EDGE_PREFIX).takeIf { iso.startsWith(Geo.EDGE_PREFIX) }) {
    null -> Locale.Builder().setRegion(iso).build().displayCountry.ifEmpty { iso }
    "CF" -> stringResource(R.string.map_edge, "Cloudflare")
    "GO" -> stringResource(R.string.map_edge, "Google")
    "AW" -> stringResource(R.string.map_edge, "Amazon CloudFront")
    "FA" -> stringResource(R.string.map_edge, "Fastly")
    else -> stringResource(R.string.map_edge, iso.removePrefix(Geo.EDGE_PREFIX))
}

/** Phone's network or SIM country: no location permission needed. */
private fun homeCountry(context: Context): String? {
    val tm = context.getSystemService(TelephonyManager::class.java)
    return listOf(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
        .firstOrNull { !it.isNullOrBlank() }
}

/**
 * Groups connections (not DNS answers) by destination country, busiest first. Anycast edges have no
 * honest country, so they sit at [home]: they answer from a city near the user.
 */
internal fun aggregate(events: List<NetEvent>, geo: Geo, home: Country?): List<Destination> =
    events.asSequence()
        .filter { it.kind != "dns" }
        .mapNotNull { e -> geo.lookup(e.target.substringBeforeLast(':').trim('[', ']'))?.let { it to e } }
        .map { (country, e) ->
            val edge = country.iso.startsWith(Geo.EDGE_PREFIX)
            (if (edge) country.copy(lat = home?.lat ?: DEFAULT_LAT, lon = home?.lon ?: 0f) else country) to e
        }
        .groupBy({ it.first }, { it.second })
        .map { (country, list) -> Destination(country, list) }
        .sortedByDescending { it.connections }

@Volatile private var cachedGeo: Geo? = null

// ~5 MB of tables; load once per process, off the main thread.
private fun loadGeo(context: Context): Geo = cachedGeo ?: synchronized(Geo::class) {
    cachedGeo ?: Geo.load(context.assets::open).also { cachedGeo = it }
}

private const val DEFAULT_LAT = 20f

/**
 * One pill showing the current choice; tapping opens a searchable sheet of every app that connected,
 * busiest first, where any number of apps can be picked. Empty selection = all apps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppFilter(
    apps: List<Pair<Int, Int>>,
    selected: Set<Int>,
    onSelect: (Set<Int>) -> Unit,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    open: Boolean,
    onOpen: (Boolean) -> Unit,
) {
    // Always shown, even with nothing to pick, so the filter row doesn't jump when data arrives.
    val c = Warden.colors
    val any = selected.isNotEmpty()
    Box(Modifier.padding(vertical = Spacing.s).heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (any) c.primary else c.surface)
                .border(1.dp, if (any) c.primary else c.outline, RoundedCornerShape(50))
                .clickable(enabled = apps.isNotEmpty(), role = Role.Button) { onOpen(true) }
                .padding(start = if (any) Spacing.s else Spacing.l, end = Spacing.m, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            val ink = if (any) c.onPrimary else c.text
            // Up to three overlapping icons for the picked apps.
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                selected.take(3).forEach { uid -> appIcon(uid)?.let { Image(it, null, Modifier.size(24.dp).clip(CircleShape)) } }
            }
            Text(
                when (selected.size) {
                    0 -> stringResource(R.string.map_all_apps)
                    1 -> appLabel(selected.first())
                    else -> pluralStringResource(R.plurals.map_n_apps, selected.size, selected.size)
                },
                style = Warden.type.body,
                color = ink,
                maxLines = 1,
            )
            Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(14.dp).graphicsLayer { rotationZ = 90f }, tint = ink)
        }
    }
    if (open) {
        // Fully open from the start: half-open, a scroll in the list also dragged the sheet and it jittered.
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // No drag on the sheet itself: at the list's ends a scroll was handed to the sheet and it bounced.
        // It still closes with Done, Back or a tap outside.
        ModalBottomSheet(
            onDismissRequest = { onOpen(false) },
            sheetState = sheet,
            sheetGesturesEnabled = false,
            containerColor = c.background,
        ) {
            // A snapshot taken when the sheet opens: live counts re-sorted the rows under your finger.
            val frozen = remember { apps }
            AppPicker(frozen, selected, onSelect, appLabel, appIcon, onDone = { onOpen(false) })
        }
    }
}

@Composable
private fun AppPicker(
    apps: List<Pair<Int, Int>>,
    selected: Set<Int>,
    onSelect: (Set<Int>) -> Unit,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    onDone: () -> Unit,
) {
    val c = Warden.colors
    var query by rememberSaveable { mutableStateOf("") }
    val shown =
        remember(apps, query) { apps.filter { (uid, _) -> query.isBlank() || appLabel(uid).contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxHeight()) {
        BasicTextField(
            query,
            { query = it },
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
                        if (query.isEmpty()) Text(stringResource(R.string.map_search_apps), style = Warden.type.body, color = c.muted)
                        field()
                    }
                }
            },
        )
        LazyColumn(Modifier.weight(1f).padding(top = Spacing.s)) {
            if (query.isBlank()) {
                item { PickerRow(stringResource(R.string.map_all_apps), null, null, selected.isEmpty()) { onSelect(emptySet()) } }
            }
            items(shown, key = { it.first }) { (uid, count) ->
                PickerRow(appLabel(uid), appIcon(uid), count, uid in selected) {
                    onSelect(if (uid in selected) selected - uid else selected + uid)
                }
            }
        }
        WardenButton(
            if (selected.isEmpty()) {
                stringResource(R.string.map_done)
            } else {
                pluralStringResource(R.plurals.map_show_n, selected.size, selected.size)
            },
            onDone,
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.m),
        )
    }
}

@Composable
private fun PickerRow(label: String, icon: ImageBitmap?, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val c = Warden.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (selected) c.surfaceVariant else c.background)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        when {
            icon != null -> Image(icon, null, Modifier.size(32.dp))
            count == null -> IconBadge(DesignR.drawable.ph_globe_hemisphere_west, c.palette[4])
            else -> IconBadge(DesignR.drawable.ph_squares_four, c.muted)
        }
        Text(label, Modifier.weight(1f), style = Warden.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) Text(count.toString(), style = Warden.type.caption.tabular(), color = c.muted)
        if (selected) Icon(painterResource(DesignR.drawable.ph_check), null, Modifier.size(18.dp), tint = c.accentInk)
    }
}

/**
 * The real globe with made-up traffic, for onboarding: arcs from the user's country to a few common
 * destinations, two of them blocked. Same renderer and offline data as the Map tab.
 */
@Composable
fun SampleGlobe(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val geo by produceState<Geo?>(null) { value = withContext(Dispatchers.IO) { loadGeo(context) } }
    val g = geo ?: return Box(modifier)
    val home = remember(g) { homeCountry(context)?.let(g::country) }
    val destinations = remember(g) {
        SAMPLE.mapNotNull { (iso, count, blocked) ->
            g.country(iso)?.let { country ->
                Destination(country, List(count) { i -> NetEvent("s$iso$i", 0L, 0, "tcp", null, "", i < blocked) })
            }
        }
    }
    val camera = remember(g) { GlobeCamera(home?.lat ?: DEFAULT_LAT, home?.lon ?: 0f) }
    Globe(g, home, destinations, null, camera, onSelect = {}, description = "", modifier = modifier)
}

private val SAMPLE = listOf(
    Triple("US", 42, 6), Triple("DE", 18, 0), Triple("SG", 15, 2), Triple("IE", 12, 0),
    Triple("JP", 9, 0), Triple("GB", 7, 0), Triple("NL", 5, 0),
)

/** How far back the map looks. */
internal enum class Scope { Session, Today, Week, Month }

private fun Scope.since(): Long = when (this) {
    Scope.Session -> History.sessionStart
    Scope.Today -> History.start(Range.Today)
    Scope.Week -> History.start(Range.Week)
    Scope.Month -> History.start(Range.Month)
}

/** A pill like the app picker: time span plus Blocked / Trackers toggles in one menu. Lime when not default. */
@Composable
private fun TimeFilter(
    scope: Scope,
    onScope: (Scope) -> Unit,
    blockedOnly: Boolean,
    onBlockedOnly: (Boolean) -> Unit,
    trackersOnly: Boolean,
    onTrackersOnly: (Boolean) -> Unit,
) {
    val c = Warden.colors
    var open by remember { mutableStateOf(false) }
    val active = scope != Scope.Session || blockedOnly || trackersOnly
    val ink = if (active) c.onPrimary else c.text
    val label = listOfNotNull(
        scopeLabel(scope),
        stringResource(R.string.map_blocked_only).takeIf { blockedOnly },
        stringResource(R.string.map_trackers_only).takeIf { trackersOnly },
    ).joinToString(" · ")
    Box(Modifier.padding(vertical = Spacing.s).heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (active) c.primary else c.surface)
                .border(1.dp, if (active) c.primary else c.outline, RoundedCornerShape(50))
                .clickable(role = Role.Button) { open = true }
                .padding(start = Spacing.l, end = Spacing.m, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(label, style = Warden.type.body, color = ink, maxLines = 1)
            Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(14.dp).graphicsLayer { rotationZ = 90f }, tint = ink)
        }
        DropdownMenu(open, { open = false }, containerColor = c.surface, shape = RoundedCornerShape(Radii.medium)) {
            Scope.entries.forEach { s ->
                MenuRow(scopeLabel(s), s == scope) {
                    onScope(s)
                    open = false
                }
            }
            HorizontalDivider(color = c.outline)
            MenuRow(stringResource(R.string.map_blocked_only), blockedOnly) { onBlockedOnly(!blockedOnly) }
            MenuRow(stringResource(R.string.map_trackers_only), trackersOnly) { onTrackersOnly(!trackersOnly) }
        }
    }
}

@Composable
private fun MenuRow(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = Warden.type.body, color = Warden.colors.text) },
        onClick = onClick,
        trailingIcon = {
            if (checked) Icon(painterResource(DesignR.drawable.ph_check), null, Modifier.size(18.dp), tint = Warden.colors.accentInk)
        },
    )
}

@Composable
private fun scopeLabel(s: Scope): String = stringResource(
    when (s) {
        Scope.Session -> R.string.map_scope_session
        Scope.Today -> R.string.map_scope_today
        Scope.Week -> R.string.map_scope_week
        Scope.Month -> R.string.map_scope_month
    },
)

/** What the screen shows for a set of destinations: country and count, in order. */
private fun List<Destination>.signature() = map { it.country.iso to it.connections }

/** System services use many UIDs below 10000 that all read "Android system"; the picker shows them as one. */
private fun pickerUid(uid: Int) = if (uid in 0 until android.os.Process.FIRST_APPLICATION_UID) android.os.Process.SYSTEM_UID else uid
