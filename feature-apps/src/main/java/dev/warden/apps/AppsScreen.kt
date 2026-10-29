package dev.warden.apps

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.warden.design.EmptyState
import dev.warden.design.IconBadge
import dev.warden.design.Motion
import dev.warden.design.Spacing
import dev.warden.design.Tile
import dev.warden.design.Warden
import dev.warden.design.staggerIn
import dev.warden.design.tabular
import dev.warden.engine.NetEvent
import kotlinx.coroutines.delay
import dev.warden.design.R as DesignR

private class AppSummary(val uid: Int, val connections: Int, val domains: Int, val blocked: Int)

/**
 * Apps seen connecting, busiest first; tap one for its detail. The icon and name fly into the
 * detail header (shared element). Per-app Wi-Fi/mobile rules arrive with the rule engine in Milestone 4.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppsScreen(
    events: List<NetEvent>,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    openUid: Int?,
    onOpen: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = openUid != null) { onOpen(null) }
    // Owned here, not by the list, so opening an app and coming back keeps the scroll position
    // and doesn't replay the entrance stagger.
    val listState = rememberLazyListState()
    var firstAppearance by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(FIRST_APPEARANCE_MS)
        firstAppearance = false
    }
    val reduceMotion = Warden.reduceMotion
    SharedTransitionLayout(modifier.fillMaxSize()) {
        AnimatedContent(
            openUid,
            transitionSpec = {
                val dir = if (targetState != null) 1 else -1
                if (reduceMotion) {
                    fadeIn(Motion.emphasized(0)) togetherWith fadeOut(Motion.emphasized(0))
                } else {
                    (slideInHorizontally(Motion.emphasized(Motion.LONG)) { dir * it / 6 } + fadeIn(Motion.emphasized(Motion.LONG)))
                        .togetherWith(
                            slideOutHorizontally(Motion.emphasized(Motion.LONG)) { -dir * it / 8 } +
                                fadeOut(Motion.emphasized(Motion.SHORT)),
                        )
                }
            },
            label = "apps",
        ) { uid ->
            val shared = Shared(this@SharedTransitionLayout, this)
            if (uid == null) {
                AppList(events, appLabel, appIcon, shared, listState, firstAppearance, onOpen = { onOpen(it) })
            } else {
                AppDetail(uid, events, appLabel, appIcon, shared, onBack = { onOpen(null) })
            }
        }
    }
}

/** The two scopes a shared element needs, bundled so screens can tag their icon and name. */
@OptIn(ExperimentalSharedTransitionApi::class)
internal class Shared(private val layout: SharedTransitionScope, private val visibility: AnimatedVisibilityScope) {
    @Composable
    fun icon(uid: Int): Modifier = with(layout) { Modifier.sharedElement(rememberSharedContentState("icon-$uid"), visibility) }

    @Composable
    fun name(uid: Int): Modifier = with(layout) { Modifier.sharedBounds(rememberSharedContentState("name-$uid"), visibility) }
}

@Composable
internal fun AppIcon(uid: Int, appIcon: (Int) -> ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    val icon = appIcon(uid)
    if (icon != null) {
        Image(icon, null, modifier.size(size))
    } else {
        IconBadge(DesignR.drawable.ph_squares_four, Warden.colors.muted, modifier)
    }
}

@Composable
private fun AppList(
    events: List<NetEvent>,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    shared: Shared,
    listState: LazyListState,
    firstAppearance: Boolean,
    onOpen: (Int) -> Unit,
) {
    val c = Warden.colors
    val apps = remember(events) {
        events.groupBy { it.uid }.map { (uid, list) ->
            AppSummary(
                uid = uid,
                connections = list.count { it.kind != "dns" },
                domains = list.mapNotNull { it.domain }.distinct().size,
                blocked = list.count { it.blocked },
            )
        }.sortedByDescending { it.connections + it.blocked }
    }
    if (apps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                DesignR.drawable.ph_squares_four_light,
                stringResource(R.string.apps_empty_title),
                stringResource(R.string.apps_empty_body),
            )
        }
        return
    }
    val most = apps.first().connections.coerceAtLeast(1)

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.s, bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item {
            Column(Modifier.padding(start = Spacing.s, bottom = Spacing.m)) {
                Text(stringResource(R.string.apps_title), style = Warden.type.headline)
                Text(
                    pluralStringResource(R.plurals.apps_summary, apps.size, apps.size),
                    style = Warden.type.body.tabular(),
                    color = c.muted,
                )
            }
        }
        itemsIndexed(apps, key = { _, a -> a.uid }) { index, a ->
            val hue = c.palette[index % c.palette.size]
            Tile(Modifier.staggerIn(index, firstAppearance).fillMaxWidth(), onClick = { onOpen(a.uid) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    // Real app icons carry the colour; the chrome stays quiet.
                    AppIcon(a.uid, appIcon, 40.dp, shared.icon(a.uid))
                    Column(Modifier.weight(1f)) {
                        Text(
                            appLabel(a.uid),
                            shared.name(a.uid),
                            style = Warden.type.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            pluralStringResource(R.plurals.apps_connections, a.connections, a.connections) + "  ·  " +
                                pluralStringResource(R.plurals.apps_domains, a.domains, a.domains),
                            style = Warden.type.label.tabular(),
                            color = c.muted,
                        )
                    }
                    if (a.blocked > 0) BlockedTag(a.blocked)
                    Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(16.dp), tint = c.muted)
                }
                Box(Modifier.fillMaxWidth().height(4.dp).background(c.surfaceVariant, RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(a.connections / most.toFloat()).fillMaxHeight().background(hue, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

@Composable
internal fun BlockedTag(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(painterResource(DesignR.drawable.ph_prohibit), null, Modifier.size(14.dp), tint = Warden.colors.blocked)
        Text(
            pluralStringResource(R.plurals.apps_blocked, count, count),
            style = Warden.type.label.tabular(),
            color = Warden.colors.blocked,
        )
    }
}

private const val FIRST_APPEARANCE_MS = 600L
