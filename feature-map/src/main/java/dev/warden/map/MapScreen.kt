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
import androidx.compose.ui.unit.dp
import dev.warden.design.IconBadge
import dev.warden.design.Motion
import dev.warden.design.SectionLabel
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val geo by produceState<Geo?>(null) { value = withContext(Dispatchers.IO) { loadGeo(context) } }
    val g = geo ?: return Box(modifier.fillMaxSize()) // ~150ms load, not worth a spinner

    val destinations = remember(events, g) { aggregate(events, g) }
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
            Overview(g, destinations, onOpen = { detail = it })
        } else {
            CountryDetail(d, appLabel, appIcon, onBack = { detail = null })
        }
    }
}

@Composable
private fun Overview(g: Geo, destinations: List<Destination>, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val c = Warden.colors
    val home = remember(g) { homeCountry(context)?.let(g::country) }
    val camera = remember(g) { GlobeCamera(home?.lat ?: DEFAULT_LAT, home?.lon ?: 0f) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val total = destinations.sumOf { it.connections }.coerceAtLeast(1)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = Spacing.xl).padding(top = Spacing.xl)) {
                Text(stringResource(R.string.map_title_sans), style = Warden.type.headline)
            }
        }
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
                    Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.l),
                    style = Warden.type.body,
                    color = c.muted,
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
            item { SectionLabel(stringResource(R.string.map_countries), Modifier.padding(horizontal = Spacing.xl)) }
        }
        items(destinations, key = { it.country.iso }) { d ->
            CountryRow(d, total) {
                selected = d.country.iso
                scope.launch { camera.focus(d.country.lat, d.country.lon) }
                onOpen(d.country.iso)
            }
        }
        item { Box(Modifier.height(Spacing.xxl)) }
    }
}

@Composable
private fun CountryRow(d: Destination, total: Int, onClick: () -> Unit) {
    val c = Warden.colors
    val hue = countryHue(d.country.iso, c.palette)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.xl, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Swatch(hue)
            Text(countryName(d.country.iso), Modifier.weight(1f), style = Warden.type.body)
            if (d.blocked > 0) BlockedTag(d.blocked)
            Text(d.connections.toString(), style = Warden.type.caption.tabular(), color = c.muted)
            Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(16.dp), tint = c.muted)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.surfaceVariant, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(d.connections / total.toFloat()).fillMaxHeight().background(hue, RoundedCornerShape(2.dp)))
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = Spacing.xl), color = c.outline)
}

/** Everything that went to one country: totals, the apps that went there, every connection. */
@Composable
private fun CountryDetail(d: Destination, appLabel: (Int) -> String, appIcon: (Int) -> ImageBitmap?, onBack: () -> Unit) {
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
                    SectionLabel(d.country.iso)
                }
                Text(countryName(d.country.iso), style = Warden.type.headline)
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
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                AppIcon(uid, appIcon)
                Text(appLabel(uid), Modifier.weight(1f), style = Warden.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(count.toString(), style = Warden.type.caption.tabular(), color = c.muted)
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
        item { Box(Modifier.height(Spacing.xxl)) }
    }
}

@Composable
private fun AppIcon(uid: Int, appIcon: (Int) -> ImageBitmap?) {
    val icon = remember(uid) { appIcon(uid) }
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

private fun countryName(iso: String): String = Locale.Builder().setRegion(iso).build().displayCountry.ifEmpty { iso }

/** Phone's network or SIM country: no location permission needed. */
private fun homeCountry(context: Context): String? {
    val tm = context.getSystemService(TelephonyManager::class.java)
    return listOf(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
        .firstOrNull { !it.isNullOrBlank() }
}

/** Groups connections (not DNS answers) by destination country, busiest first. */
internal fun aggregate(events: List<NetEvent>, geo: Geo): List<Destination> =
    events.asSequence()
        .filter { it.kind != "dns" }
        .mapNotNull { e -> geo.lookup(e.target.substringBeforeLast(':').trim('[', ']'))?.let { it to e } }
        .groupBy({ it.first }, { it.second })
        .map { (country, list) -> Destination(country, list) }
        .sortedByDescending { it.connections }

@Volatile private var cachedGeo: Geo? = null

// ~5 MB of tables; load once per process, off the main thread.
private fun loadGeo(context: Context): Geo = cachedGeo ?: synchronized(Geo::class) {
    cachedGeo ?: Geo.load(context.assets::open).also { cachedGeo = it }
}

private const val DEFAULT_LAT = 20f
