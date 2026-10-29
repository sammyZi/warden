package dev.warden.map

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import dev.warden.design.Radii
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.warden.design.Tile
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import dev.warden.design.R as DesignR

@Composable
fun MapScreen(
    events: List<NetEvent>,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    onOpenApp: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val geo by produceState<Geo?>(null) { value = withContext(Dispatchers.IO) { loadGeo(context) } }
    val g = geo ?: return Box(modifier.fillMaxSize()) // ~150ms load, not worth a spinner

    // Optional app filter: the globe, arcs and country list then show only that app's traffic.
    // Empty = every app. ponytail: plain remember, so a rotation resets the selection.
    var appFilter by remember { mutableStateOf(emptySet<Int>()) }
    val topApps = remember(events) {
        events.filter { it.kind != "dns" }.groupingBy { it.uid }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
    val destinations = remember(events, g, appFilter) {
        aggregate(if (appFilter.isEmpty()) events else events.filter { it.uid in appFilter }, g, homeCountry(context)?.let(g::country))
    }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
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
                g, destinations, onOpen = { detail = it },
                filter = { AppFilter(topApps, appFilter, { appFilter = it }, appLabel, appIcon) },
            )
        } else {
            CountryDetail(d, appLabel, appIcon, onBack = { detail = null }, onOpenApp = onOpenApp)
        }
    }
}

@Composable
private fun Overview(g: Geo, destinations: List<Destination>, onOpen: (String) -> Unit, filter: @Composable () -> Unit) {
    val context = LocalContext.current
    val c = Warden.colors
    val home = remember(g) { homeCountry(context)?.let(g::country) }
    val camera = remember(g) { GlobeCamera(home?.lat ?: DEFAULT_LAT, home?.lon ?: 0f) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val total = destinations.sumOf { it.connections }.coerceAtLeast(1)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = Spacing.xl).padding(top = Spacing.s)) {
                Text(stringResource(R.string.map_title_sans), style = Warden.type.headline)
            }
        }
        item { filter() }
        item {
            Globe(
                geo = g,
                home = home,
                destinations = destinations,
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
        items(destinations, key = { it.country.iso }) { d ->
            CountryRow(d, total) {
                selected = d.country.iso
                scope.launch { camera.focus(d.country.lat, d.country.lon) }
                onOpen(d.country.iso)
            }
        }
        item { BarSpacer() }
    }
}

/** A country as a card: code badge in its hue, name and counts, share of all traffic, share bar. */
@Composable
private fun CountryRow(d: Destination, total: Int, onClick: () -> Unit) {
    val c = Warden.colors
    val hue = countryHue(d.country.iso, c.palette)
    Tile(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs).fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Box(Modifier.size(40.dp).background(hue.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                Text(
                    d.country.iso.removePrefix(Geo.EDGE_PREFIX),
                    style = Warden.type.label.copy(fontWeight = FontWeight.SemiBold),
                    color = hue,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(countryName(d.country.iso), style = Warden.type.body.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                Text(
                    pluralStringResource(R.plurals.map_connections, d.connections, d.connections) + " · " +
                        pluralStringResource(R.plurals.map_n_apps, d.apps, d.apps),
                    style = Warden.type.label.tabular(),
                    color = c.muted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${(d.connections * 100f / total).roundToInt()}%", style = Warden.type.title.tabular())
                if (d.blocked > 0) BlockedTag(d.blocked)
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(c.surfaceVariant, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(d.connections / total.toFloat()).fillMaxHeight().background(hue, RoundedCornerShape(2.dp)))
        }
    }
}

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
) {
    if (apps.isEmpty()) return
    val c = Warden.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val any = selected.isNotEmpty()
    Box(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.s).heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (any) c.primary else c.surface)
                .border(1.dp, if (any) c.primary else c.outline, RoundedCornerShape(50))
                .clickable(role = Role.Button) { open = true }
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
        ModalBottomSheet(onDismissRequest = { open = false }, containerColor = c.background) {
            AppPicker(apps, selected, onSelect, appLabel, appIcon, onDone = { open = false })
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
    Column(Modifier.fillMaxHeight(0.85f)) {
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
