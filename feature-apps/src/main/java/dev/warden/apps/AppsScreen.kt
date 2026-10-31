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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.warden.design.IconBadge
import dev.warden.design.Motion
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.tabular
import dev.warden.engine.NetEvent
import kotlinx.coroutines.delay
import dev.warden.design.R as DesignR

/**
 * Every app that can go online, busiest first; tap one for its detail. The icon and name fly into the
 * detail header (shared element).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppsScreen(
    events: List<NetEvent>,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    openUid: Int?,
    onOpen: (Int?) -> Unit,
    onShowOnMap: (Int) -> Unit,
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
                AppDetail(uid, events, appLabel, appIcon, shared, onBack = { onOpen(null) }, onShowOnMap = onShowOnMap)
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
