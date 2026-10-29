package dev.warden.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.warden.design.FilterChip
import dev.warden.design.SectionLabel
import dev.warden.design.Spacing
import dev.warden.design.ThemeMode
import dev.warden.design.Tile
import dev.warden.design.Warden
import dev.warden.design.R as DesignR
import android.text.format.DateUtils
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.warden.design.WardenSwitch
import dev.warden.rules.BlocklistSource
import dev.warden.rules.BlocklistState
import dev.warden.rules.Blocklists
import java.text.NumberFormat

/** Milestone 1 settings: appearance and about. DNS, blocklists and retention land in Milestone 7. */
@Composable
fun SettingsScreen(
    version: String,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onReplayIntro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Warden.colors
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(Modifier.padding(top = Spacing.xl, bottom = Spacing.l)) {
            Text(stringResource(R.string.settings_title_sans), style = Warden.type.headline)
        }

        SectionLabel(stringResource(R.string.settings_appearance))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    stringResource(
                        when (mode) {
                            ThemeMode.System -> R.string.settings_theme_system
                            ThemeMode.Light -> R.string.settings_theme_light
                            ThemeMode.Dark -> R.string.settings_theme_dark
                        },
                    ),
                    selected = mode == themeMode,
                    onClick = { onThemeMode(mode) },
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_blocklists), Modifier.padding(top = Spacing.l))
        Text(stringResource(R.string.settings_blocklists_note), style = Warden.type.label, color = c.muted)
        val lists by Blocklists.states.collectAsState()
        Tile(Modifier.fillMaxWidth()) {
            Blocklists.catalog.forEachIndexed { i, source ->
                if (i > 0) HorizontalDivider(color = c.outline)
                BlocklistRow(source, lists.getValue(source.id))
            }
        }

        SectionLabel(stringResource(R.string.settings_general), Modifier.padding(top = Spacing.l))
        Tile(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onReplayIntro),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_replay_intro), Modifier.weight(1f), style = Warden.type.body)
                Icon(painterResource(DesignR.drawable.ph_caret_right), null, Modifier.size(18.dp), tint = c.muted)
            }
        }

        SectionLabel(stringResource(R.string.settings_about), Modifier.padding(top = Spacing.l))
        Tile(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_promise), style = Warden.type.body)
            Text(stringResource(R.string.settings_credits), style = Warden.type.label, color = c.muted)
            Text(stringResource(R.string.settings_version, version).uppercase(), style = Warden.type.caption, color = c.muted)
        }
        Spacer(Modifier.height(Spacing.xxl))
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
                    DateUtils.getRelativeTimeSpanString(state.updatedAt).toString(),
                )
                else -> stringResource(R.string.settings_bl_license, source.license)
            }
            val canTap = !state.busy && (state.failed || state.enabled)
            Text(
                status.uppercase(),
                if (canTap) Modifier.clickable { Blocklists.refresh(source.id) }.padding(vertical = Spacing.xs) else Modifier,
                style = Warden.type.caption,
                color = if (state.failed) c.blocked else c.muted,
            )
        }
        WardenSwitch(state.enabled, { Blocklists.setEnabled(source.id, it) }, contentDescription = source.name)
    }
}
