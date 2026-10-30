package dev.warden.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.warden.design.Radii
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.rules.Dns
import dev.warden.rules.Upstream
import dev.warden.design.R as DesignR

/**
 * Where lookups go, on Home: one line that says whether they're private, and a sheet to change it.
 * Same setting as Settings > DNS; a custom server's URL is still entered there.
 */
@Composable
internal fun DnsCard(onOpenSettings: () -> Unit) {
    val c = Warden.colors
    val config by Dns.config.collectAsState()
    var picking by rememberSaveable { mutableStateOf(false) }
    val encrypted = config.dohUrl != null
    val hue = if (encrypted) c.accentInk else c.warning
    val shape = RoundedCornerShape(Radii.medium)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.outline, shape)
            .clickable(role = Role.Button) { picking = true }
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(Modifier.size(40.dp).background(hue.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (encrypted) DesignR.drawable.ph_lock_key_light else DesignR.drawable.ph_lock_simple),
                null,
                Modifier.size(20.dp),
                tint = hue,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.dns_card_title, upstreamName(config.upstream)),
                style = Warden.type.body.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                stringResource(if (encrypted) R.string.dns_card_private else R.string.dns_card_visible),
                style = Warden.type.label,
                color = if (encrypted) c.muted else c.warning,
            )
        }
        Icon(
            painterResource(DesignR.drawable.ph_caret_right),
            stringResource(R.string.dns_card_change),
            Modifier.size(18.dp),
            tint = c.muted,
        )
    }

    if (picking) {
        DnsSheet(config.upstream, config.dohUrl != null || config.upstream != Upstream.Custom, onDismiss = { picking = false }) {
            if (it == Upstream.Custom) {
                // Typing a URL belongs on the settings page, not in a sheet.
                picking = false
                onOpenSettings()
            } else {
                Dns.setUpstream(it)
                picking = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsSheet(current: Upstream, currentWorks: Boolean, onDismiss: () -> Unit, onPick: (Upstream) -> Unit) {
    val c = Warden.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.background) {
        Column(
            Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(R.string.dns_sheet_title), style = Warden.type.title)
            Upstream.entries.forEach { option ->
                Option(option, selected = option == current && currentWorks) { onPick(option) }
            }
        }
    }
}

@Composable
private fun Option(option: Upstream, selected: Boolean, onClick: () -> Unit) {
    val c = Warden.colors
    val shape = RoundedCornerShape(Radii.medium)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(if (selected) c.accentInk.copy(alpha = 0.12f) else c.surface)
            .border(1.dp, if (selected) c.accentInk else c.outline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    when (option) {
                        Upstream.Phone -> R.string.dns_title_phone
                        Upstream.Cloudflare -> R.string.dns_name_cloudflare
                        Upstream.Quad9 -> R.string.dns_name_quad9
                        Upstream.Custom -> R.string.dns_title_custom
                    },
                ),
                style = Warden.type.body.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                stringResource(
                    when (option) {
                        Upstream.Phone -> R.string.dns_option_phone
                        Upstream.Cloudflare -> R.string.dns_option_cloudflare
                        Upstream.Quad9 -> R.string.dns_option_quad9
                        Upstream.Custom -> R.string.dns_option_custom
                    },
                ),
                style = Warden.type.label,
                color = c.muted,
            )
        }
        // Radio mark: filled ring when chosen.
        Box(
            Modifier.size(22.dp).border(2.dp, if (selected) c.accentInk else c.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(12.dp).background(c.accentInk, CircleShape))
        }
    }
}

@Composable
private fun upstreamName(u: Upstream) = stringResource(
    when (u) {
        Upstream.Phone -> R.string.dns_name_phone
        Upstream.Cloudflare -> R.string.dns_name_cloudflare
        Upstream.Quad9 -> R.string.dns_name_quad9
        Upstream.Custom -> R.string.dns_name_custom
    },
)
