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
