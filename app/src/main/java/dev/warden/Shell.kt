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
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.warden.design.LocalBarInset
import dev.warden.design.Motion
import dev.warden.design.PowerState
import dev.warden.design.Radii
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.ThemeMode
import dev.warden.design.glow
import dev.warden.data.History
import dev.warden.engine.Engine
import dev.warden.rules.Blocklists
import dev.warden.engine.WardenVpnService
import dev.warden.logs.LogScreen
import dev.warden.map.MapScreen
import dev.warden.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    // Where an app's detail was opened from, so Back returns there instead of to the Apps list.
    var returnTo by rememberSaveable { mutableStateOf<Tab?>(null) }
    val openFrom: (Int) -> Unit = {
        openApp = it
        returnTo = tab
        tab = Tab.Apps
    }
    // Each tab keeps its own saved state (open tool, country, scroll) while another tab is showing.
    val tabStates = rememberSaveableStateHolder()
    BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }

    val running by Engine.running.collectAsState()
    // Saved history, so lists survive restarts; the engine feeds it in 1.5s batches.
    val events by remember { History.recent() }.collectAsState(emptyList())
    val lists by Blocklists.states.collectAsState()
    val (power, onPower) = rememberPower(running)
    val apps = remember { AppInfo(context.packageManager, context) }
    val reduceMotion = Warden.reduceMotion

    // Content fills the screen and scrolls under a floating liquid-glass tab bar.
    val haze = rememberHazeState()
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBarInset provides barHeight) {
            AnimatedContent(
                tab,
                Modifier.fillMaxSize().hazeSource(haze).statusBarsPadding(),
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
                tabStates.SaveableStateProvider(t.name) {
                    when (t) {
                        Tab.Home -> DashboardScreen(
                            state = power,
                            appLabel = apps::label,
                            appIcon = apps::icon,
                            onPowerClick = onPower,
                            onOpenMap = { tab = Tab.Map },
                            blocklistsOn = lists.values.any { it.enabled },
                            onOpenSettings = { tab = Tab.Settings },
                            onOpenApp = openFrom,
                        )
                        Tab.Apps -> AppsScreen(events, apps::label, apps::icon, openApp, {
                            openApp = it
                            val back = returnTo
                            if (it == null && back != null) {
                                returnTo = null
                                tab = back
                            }
                        })
                        Tab.Map -> MapScreen(apps::label, apps::icon, onOpenApp = openFrom)
                        Tab.Log -> LogScreen(events, running, apps::label, apps::icon)
                        Tab.Settings -> SettingsScreen(
                            version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
                            themeMode = themeMode,
                            onThemeMode = onThemeMode,
                            onReplayIntro = onReplayIntro,
                            appLabel = apps::label,
                            appIcon = apps::icon,
                        )
                    }
                }
            }
        }
        BottomBar(
            tab,
            haze,
            Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeight = with(density) { it.height.toDp() } },
        ) {
            if (it == Tab.Apps && tab == Tab.Apps) openApp = null // re-tap the tab to get back to the list
            returnTo = null
            tab = it
        }
    }
}

/**
 * Floating liquid-glass tab bar: content refracts and blurs through it (Haze glass, GPU RenderEffect,
 * Balanced performance mode; a plain translucent fallback where the platform can't). One lime pill
 * springs to the selected tab.
 */
@Composable
private fun BottomBar(current: Tab, haze: HazeState, modifier: Modifier, onSelect: (Tab) -> Unit) {
    val c = Warden.colors
    val shape = RoundedCornerShape(Radii.large)
    // Frosted, not clear glass: enough blur and tint that text scrolling behind never fights the labels.
    val style = remember(c) {
        HazeBlurStyle {
            blurRadius(48.dp)
            backgroundColor(c.background)
            colorEffects(listOf(HazeColorEffect.tint(c.background.copy(alpha = 0.5f))))
            noiseFactor(0.04f)
        }
    }
    Box(modifier.navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(shape)
                .hazeBlur(HazeInput.Sources(haze), style, HazePerformanceMode.Balanced)
                .border(1.dp, c.outline.copy(alpha = 0.6f), shape)
                .padding(Spacing.s),
        ) {
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
    private val icons = mutableStateMapOf<Int, ImageBitmap?>()
    private val loading = HashSet<Int>() // main thread only
    private val scope = MainScope()

    private fun pkg(uid: Int) = if (uid >= Process.FIRST_APPLICATION_UID) pm.getPackagesForUid(uid)?.firstOrNull() else null

    fun label(uid: Int): String = labels.getOrPut(uid) {
        when {
            uid < 0 -> unknown
            uid < Process.FIRST_APPLICATION_UID -> system
            else -> pkg(uid)?.let { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrDefault(it) }
                ?: unknown
        }
    }

    /**
     * Cached icon, or null while it loads off the main thread. The cache is snapshot state, so
     * whoever read null recomposes when the icon lands. Decoding on the main thread caused stutter.
     */
    fun icon(uid: Int): ImageBitmap? {
        if (uid !in icons && loading.add(uid)) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { decode(uid) }
                icons[uid] = bitmap
            }
        }
        return icons[uid]
    }

    private fun decode(uid: Int): ImageBitmap? = pkg(uid)?.let { pkg ->
        runCatching {
            val d = pm.getApplicationIcon(pkg)
            Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888).also {
                d.setBounds(0, 0, iconPx, iconPx)
                d.draw(android.graphics.Canvas(it))
            }.asImageBitmap()
        }.getOrNull()
    }
}

private const val CONNECT_TIMEOUT_MS = 10_000L
