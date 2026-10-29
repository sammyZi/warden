package dev.warden

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.VpnService
import android.os.Process
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.warden.apps.AppsScreen
import dev.warden.dashboard.DashboardScreen
import dev.warden.design.Motion
import dev.warden.design.PowerState
import dev.warden.design.Radii
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.ThemeMode
import dev.warden.design.glow
import dev.warden.engine.Engine
import dev.warden.rules.Blocklists
import dev.warden.engine.WardenVpnService
import dev.warden.logs.LogScreen
import dev.warden.map.MapScreen
import dev.warden.settings.SettingsScreen
import kotlinx.coroutines.delay
import dev.warden.design.R as DesignR

private enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Home(R.string.tab_home, DesignR.drawable.ph_shield_check),
    Apps(R.string.tab_apps, DesignR.drawable.ph_squares_four),
    Map(R.string.tab_map, DesignR.drawable.ph_globe_hemisphere_west),
    Log(R.string.tab_log, DesignR.drawable.ph_pulse),
    Settings(R.string.tab_settings, DesignR.drawable.ph_gear_six),
}

@Composable
internal fun Shell(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit, onReplayIntro: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var openApp by rememberSaveable { mutableStateOf<Int?>(null) }
    BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }

    val running by Engine.running.collectAsState()
    val events by Engine.events.collectAsState()
    val lists by Blocklists.states.collectAsState()
    val (power, onPower) = rememberPower(running)
    val apps = remember { AppInfo(context.packageManager, context) }
    val reduceMotion = Warden.reduceMotion

    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            tab,
            Modifier.weight(1f).statusBarsPadding(),
            transitionSpec = {
                if (reduceMotion) {
                    ContentTransform(EnterTransition.None, ExitTransition.None)
                } else {
                    // Sideways, in the direction of travel; the outgoing screen drifts a third for depth.
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(Motion.emphasized(Motion.LONG)) { dir * it } + fadeIn(Motion.emphasized(Motion.LONG)))
                        .togetherWith(
                            slideOutHorizontally(Motion.emphasized(Motion.LONG)) { -dir * it / 3 } +
                                fadeOut(Motion.emphasized(Motion.MEDIUM)),
                        )
                }
            },
            label = "tabs",
        ) { t ->
            when (t) {
                Tab.Home -> DashboardScreen(
                    state = power,
                    events = events,
                    appLabel = apps::label,
                    appIcon = apps::icon,
                    onPowerClick = onPower,
                    onOpenMap = { tab = Tab.Map },
                    blocklistsOn = lists.values.any { it.enabled },
                    onOpenSettings = { tab = Tab.Settings },
                    onOpenApp = {
                        openApp = it
                        tab = Tab.Apps
                    },
                )
                Tab.Apps -> AppsScreen(events, apps::label, apps::icon, openApp, { openApp = it })
                Tab.Map -> MapScreen(events, apps::label, apps::icon, onOpenApp = {
                    openApp = it
                    tab = Tab.Apps
                })
                Tab.Log -> LogScreen(events, running, apps::label, apps::icon)
                Tab.Settings -> SettingsScreen(
                    version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
                    themeMode = themeMode,
                    onThemeMode = onThemeMode,
                    onReplayIntro = onReplayIntro,
                )
            }
        }
        BottomBar(tab) {
            if (it == Tab.Apps && tab == Tab.Apps) openApp = null // re-tap the tab to get back to the list
            tab = it
        }
    }
}

/** Tab bar: one lime pill springs to the selected tab; selected icon and label turn ink on it. */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = Warden.colors
    Column(Modifier.background(c.background).navigationBarsPadding()) {
        HorizontalDivider(color = c.outline)
        BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = Spacing.s, vertical = Spacing.s)) {
            val itemWidth = maxWidth / Tab.entries.size
            val x by animateDpAsState(
                itemWidth * current.ordinal,
                if (Warden.reduceMotion) spring(stiffness = 10_000f) else spring(dampingRatio = 0.72f, stiffness = 380f),
                label = "pill",
            )
            Box(
                Modifier
                    .offset(x = x)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(horizontal = Spacing.xs)
                    .background(c.primary, RoundedCornerShape(Radii.medium)),
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                Tab.entries.forEach { t -> TabItem(t, t == current, Modifier.weight(1f)) { onSelect(t) } }
            }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Warden.colors
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.9f else 1f, Motion.press(), label = "press")
    // Icon pops a little when it becomes selected.
    val pop by animateFloatAsState(
        if (selected) 1.12f else 1f,
        if (Warden.reduceMotion) spring(stiffness = 10_000f) else spring(dampingRatio = 0.45f, stiffness = 420f),
        label = "pop",
    )
    val tint by animateColorAsState(if (selected) c.onPrimary else c.muted, Motion.emphasized(Motion.MEDIUM), label = "tint")
    val label by animateColorAsState(if (selected) c.onPrimary else c.muted, Motion.emphasized(Motion.MEDIUM), label = "label")
    Column(
        modifier
            .fillMaxHeight()
            .padding(horizontal = Spacing.xs)
            .clip(RoundedCornerShape(Radii.medium))
            .selectable(selected, source, remember(c.accentInk) { glow(c.accentInk) }, role = Role.Tab) {
                if (!selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .graphicsLayer {
                scaleX = press
                scaleY = press
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painterResource(tab.icon),
            null,
            Modifier.size(22.dp).graphicsLayer {
                scaleX = pop
                scaleY = pop
            },
            tint = tint,
        )
        Text(stringResource(tab.label), style = Warden.type.label, color = label)
    }
}

/** Power button state plus its click: asks for VPN consent when needed, shows Connecting until the engine reports in. */
@Composable
private fun rememberPower(running: Boolean): Pair<PowerState, () -> Unit> {
    val context = LocalContext.current
    var connecting by remember { mutableStateOf(false) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) WardenVpnService.start(context) else connecting = false
    }
    LaunchedEffect(running) { if (running) connecting = false }
    LaunchedEffect(connecting) {
        if (connecting) {
            delay(CONNECT_TIMEOUT_MS) // engine failed to start; fall back to Off rather than spin forever
            connecting = false
        }
    }
    val state = when {
        running -> PowerState.On
        connecting -> PowerState.Connecting
        else -> PowerState.Off
    }
    return state to {
        when {
            running -> WardenVpnService.stop(context)
            !connecting -> {
                connecting = true
                VpnService.prepare(context)?.let(consent::launch) ?: WardenVpnService.start(context)
            }
        }
    }
}

/** UID to a human app name and icon, cached. :core-attribution takes this over in Milestone 4. */
internal class AppInfo(private val pm: PackageManager, context: Context) {
    private val unknown = context.getString(R.string.uid_unknown)
    private val system = context.getString(R.string.uid_system)
    private val iconPx = (40 * context.resources.displayMetrics.density).toInt()
    private val labels = HashMap<Int, String>()
    private val icons = HashMap<Int, ImageBitmap?>()

    private fun pkg(uid: Int) = if (uid >= Process.FIRST_APPLICATION_UID) pm.getPackagesForUid(uid)?.firstOrNull() else null

    fun label(uid: Int): String = labels.getOrPut(uid) {
        when {
            uid < 0 -> unknown
            uid < Process.FIRST_APPLICATION_UID -> system
            else -> pkg(uid)?.let { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrDefault(it) }
                ?: unknown
        }
    }

    fun icon(uid: Int): ImageBitmap? {
        if (uid in icons) return icons[uid]
        val bitmap = pkg(uid)?.let { pkg ->
            runCatching {
                val d = pm.getApplicationIcon(pkg)
                Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888).also {
                    d.setBounds(0, 0, iconPx, iconPx)
                    d.draw(android.graphics.Canvas(it))
                }.asImageBitmap()
            }.getOrNull()
        }
        icons[uid] = bitmap
        return bitmap
    }
}

private const val CONNECT_TIMEOUT_MS = 10_000L
