package dev.warden.settings

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.warden.design.IconBadge
import dev.warden.design.Motion
import dev.warden.design.SectionLabel
import dev.warden.design.SegmentedControl
import dev.warden.design.Spacing
import dev.warden.design.StatSquare
import dev.warden.design.ThemeMode
import dev.warden.design.Tile
import dev.warden.design.Warden
import dev.warden.design.WardenSwitch
import dev.warden.rules.AppRules
import dev.warden.rules.BlocklistSource
import dev.warden.rules.BlocklistState
import dev.warden.rules.Blocklists
import dev.warden.rules.Rules
import java.text.NumberFormat
import dev.warden.design.R as DesignR

/** Settings home plus the "Your rules" page it opens. DNS and retention settings land in Milestone 7. */
@Composable
fun SettingsScreen(
    version: String,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onReplayIntro: () -> Unit,
    appLabel: (Int) -> String,
    appIcon: (Int) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    var showRules by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showRules) { showRules = false }
    val reduceMotion = Warden.reduceMotion
    AnimatedContent(
        showRules,
        modifier.fillMaxSize(),
        transitionSpec = {
            val dir = if (targetState) 1 else -1
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
        label = "settings",
    ) { rules ->
        if (rules) {
            RulesScreen(appLabel, appIcon, onBack = { showRules = false })
        } else {
            SettingsHome(version, themeMode, onThemeMode, onReplayIntro, onOpenRules = { showRules = true })
        }
    }
}

@Composable
private fun SettingsHome(
    version: String,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onReplayIntro: () -> Unit,
    onOpenRules: () -> Unit,
) {
    val c = Warden.colors
    val appRules by AppRules.rules.collectAsState()
    val excluded by AppRules.excluded.collectAsState()
    val domains by Rules.domains.collectAsState()
    val lists by Blocklists.states.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(
            stringResource(R.string.settings_title_sans),
            Modifier.padding(top = Spacing.s, bottom = Spacing.m),
            style = Warden.type.headline,
        )

        // Rules overview first: it's what people come here for.
        Tile(Modifier.fillMaxWidth(), onClick = onOpenRules) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_rules), style = Warden.type.title)
                    Text(stringResource(R.string.settings_rules_body), style = Warden.type.label, color = c.muted)
                }
                Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(18.dp), tint = c.muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                StatSquare(
                    DesignR.drawable.ph_prohibit,
                    c.blocked,
                    appRules.size,
                    stringResource(R.string.settings_sq_apps),
                    Modifier.weight(1f),
                )
                StatSquare(
                    DesignR.drawable.ph_globe_hemisphere_west,
                    c.palette[4],
                    domains.size,
                    stringResource(R.string.settings_sq_domains),
                    Modifier.weight(1f),
                )
                StatSquare(
                    DesignR.drawable.ph_eye_slash_light,
                    c.palette[2],
                    excluded.size,
                    stringResource(R.string.settings_sq_skipped),
                    Modifier.weight(1f),
                )
                StatSquare(
                    DesignR.drawable.ph_shield_check,
                    c.palette[0],
                    lists.values.count { it.enabled },
                    stringResource(R.string.settings_sq_lists),
                    Modifier.weight(1f),
                )
            }
        }

        Section(stringResource(R.string.settings_blocklists), note = stringResource(R.string.settings_blocklists_note)) {
            Blocklists.catalog.forEachIndexed { i, source ->
                if (i > 0) HorizontalDivider(color = c.outline)
                BlocklistRow(source, lists.getValue(source.id))
            }
        }

        Section(stringResource(R.string.settings_appearance)) {
            SegmentedControl(
                ThemeMode.entries.map {
                    stringResource(
                        when (it) {
                            ThemeMode.System -> R.string.settings_theme_system
                            ThemeMode.Light -> R.string.settings_theme_light
                            ThemeMode.Dark -> R.string.settings_theme_dark
                        },
                    )
                },
                themeMode.ordinal,
                { onThemeMode(ThemeMode.entries[it]) },
            )
        }

        Section(stringResource(R.string.settings_general)) {
            LinkRow(DesignR.drawable.ph_device_mobile_light, stringResource(R.string.settings_replay_intro), onReplayIntro)
        }

        Section(stringResource(R.string.settings_about)) {
            Text(stringResource(R.string.settings_promise), style = Warden.type.body)
            HorizontalDivider(color = c.outline)
            Credits()
            Text(stringResource(R.string.settings_version, version).uppercase(), style = Warden.type.caption, color = c.muted)
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun Section(title: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    SectionLabel(title, Modifier.padding(top = Spacing.l))
    // One short line of context sits outside the card, so the card holds only controls.
    if (note != null) Text(note, style = Warden.type.label, color = Warden.colors.muted)
    Tile(Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun LinkRow(icon: Int, label: String, onClick: () -> Unit) {
    val c = Warden.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconBadge(icon, c.palette[3])
        Text(label, Modifier.weight(1f), style = Warden.type.body)
        Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(18.dp), tint = c.muted)
    }
}

/** Open-source credits, folded away until asked for. */
@Composable
private fun Credits() {
    val c = Warden.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 90f else 0f, Motion.emphasized(Motion.MEDIUM), label = "credits")
    Column(Modifier.fillMaxWidth().animateContentSize(Motion.emphasized(Motion.LONG))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_credits_title), Modifier.weight(1f), style = Warden.type.body)
            Icon(
                painterResource(DesignR.drawable.ph_caret_right),
                null,
                Modifier.size(16.dp).graphicsLayer { rotationZ = turn },
                tint = c.muted,
            )
        }
        if (open) Text(stringResource(R.string.settings_credits), style = Warden.type.label, color = c.muted)
    }
}

/** One list: name, what state it's in, and its switch. The status line retries or refreshes on tap. */
@Composable
private fun BlocklistRow(source: BlocklistSource, state: BlocklistState) {
    val c = Warden.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(source.name, style = Warden.type.body)
            val status = when {
                state.busy -> stringResource(R.string.settings_bl_downloading)
                state.failed -> stringResource(R.string.settings_bl_failed)
                state.enabled && state.entries > 0 -> stringResource(
                    R.string.settings_bl_entries,
                    NumberFormat.getIntegerInstance().format(state.entries),
                    DateUtils.getRelativeTimeSpanString(
                        state.updatedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString(),
                )
                else -> stringResource(R.string.settings_bl_license, source.license)
            }
            val canTap = !state.busy && (state.failed || state.enabled)
            Text(
                status,
                if (canTap) Modifier.clickable { Blocklists.refresh(source.id) }.padding(vertical = Spacing.xs) else Modifier,
                style = Warden.type.label,
                color = if (state.failed) c.blocked else c.muted,
            )
        }
        WardenSwitch(state.enabled, { Blocklists.setEnabled(source.id, it) }, contentDescription = source.name)
    }
}
