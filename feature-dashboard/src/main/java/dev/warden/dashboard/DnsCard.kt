package dev.warden.dashboard

import androidx.annotation.StringRes
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.warden.design.Radii
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.rules.Dns
import dev.warden.rules.Upstream
import dev.warden.design.R as DesignR

/**
 * Where lookups go, on Home: the provider as a badge and name, and a status pill that says whether
 * lookups are private right now. Tapping opens a sheet to switch. Custom URLs are entered in Settings.
 */
@Composable
internal fun DnsCard(active: Boolean, onOpenSettings: () -> Unit) {
    val c = Warden.colors
    val config by Dns.config.collectAsState()
    var picking by rememberSaveable { mutableStateOf(false) }
    // The choice only applies while protection is on; with it off the phone resolves on its own, unencrypted.
    val encrypted = active && config.dohUrl != null
    val hue = upstreamHue(config.upstream, config.dohUrl != null)
    val shape = RoundedCornerShape(Radii.medium)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.outline, shape)
            .clickable(role = Role.Button) { picking = true }
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ProviderBadge(config.upstream, if (active) hue else c.muted, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.dns_card_overline).uppercase(), style = Warden.type.caption, color = c.muted)
            Text(upstreamName(config.upstream), style = Warden.type.title, maxLines = 1)
        }
        when {
            !active -> Pill(stringResource(R.string.dns_pill_off), c.muted, null)
            encrypted -> Pill(stringResource(R.string.dns_tag_encrypted), hue, DesignR.drawable.ph_lock_key_light)
            else -> Pill(stringResource(R.string.dns_tag_plain), c.warning, null)
        }
    }

    if (picking) {
        DnsSheet(config.upstream, onDismiss = { picking = false }) {
            picking = false
            // Typing a URL belongs on the settings page, not in a sheet.
            if (it == Upstream.Custom && config.dohUrl == null) onOpenSettings() else Dns.setUpstream(it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsSheet(current: Upstream, onDismiss: () -> Unit, onPick: (Upstream) -> Unit) {
    val c = Warden.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.background) {
        Column(
            Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(R.string.dns_sheet_title), Modifier.padding(bottom = Spacing.s), style = Warden.type.headline)
            Upstream.entries.forEach { option -> Option(option, option == current) { onPick(option) } }
        }
    }
}

/** One provider: badge, name, a couple of short tags, and a radio mark in the provider's colour. */
@Composable
private fun Option(option: Upstream, selected: Boolean, onClick: () -> Unit) {
    val c = Warden.colors
    val hue = upstreamHue(option, option != Upstream.Phone)
    val shape = RoundedCornerShape(Radii.medium)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(if (selected) hue.copy(alpha = if (c.isDark) 0.18f else 0.10f) else c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) hue else c.outline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ProviderBadge(option, hue, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(optionTitle(option)), style = Warden.type.body.copy(fontWeight = FontWeight.Medium))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                optionTags(option).forEach { (text, tagHue) -> Tag(stringResource(text), tagHue) }
            }
        }
        Box(
            Modifier.size(24.dp).border(2.dp, if (selected) hue else c.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(12.dp).background(hue, CircleShape))
        }
    }
}

/** Monogram for the public resolvers, an icon for the phone's own network and a custom server. */
@Composable
private fun ProviderBadge(u: Upstream, hue: Color, size: Dp) {
    Box(Modifier.size(size).background(hue.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
        when (u) {
            Upstream.Cloudflare, Upstream.Quad9 -> Text(
                if (u == Upstream.Cloudflare) "CF" else "Q9",
                style = Warden.type.label.copy(fontWeight = FontWeight.SemiBold),
                color = hue,
            )
            Upstream.Phone -> Icon(painterResource(DesignR.drawable.ph_wifi_high), null, Modifier.size(size / 2), tint = hue)
            Upstream.Custom -> Icon(painterResource(DesignR.drawable.ph_gear_six), null, Modifier.size(size / 2), tint = hue)
        }
    }
}

@Composable
private fun Pill(text: String, hue: Color, icon: Int?) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(hue.copy(alpha = 0.16f))
            .padding(horizontal = Spacing.m, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(14.dp), tint = hue)
        Text(text, style = Warden.type.label.copy(fontWeight = FontWeight.Medium), color = hue)
    }
}

@Composable
private fun Tag(text: String, hue: Color) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(Radii.small))
            .background(hue.copy(alpha = 0.14f))
            .padding(horizontal = Spacing.s, vertical = 2.dp),
        style = Warden.type.caption,
        color = hue,
    )
}

@StringRes
private fun optionTitle(u: Upstream) = when (u) {
    Upstream.Phone -> R.string.dns_title_phone
    Upstream.Cloudflare -> R.string.dns_name_cloudflare
    Upstream.Quad9 -> R.string.dns_name_quad9
    Upstream.Custom -> R.string.dns_title_custom
}

@Composable
private fun optionTags(u: Upstream): List<Pair<Int, Color>> {
    val c = Warden.colors
    val hue = upstreamHue(u, u != Upstream.Phone)
    return when (u) {
        Upstream.Phone -> listOf(R.string.dns_tag_plain to c.warning)
        Upstream.Cloudflare -> listOf(R.string.dns_tag_encrypted to hue, R.string.dns_tag_fast to c.muted)
        Upstream.Quad9 -> listOf(R.string.dns_tag_encrypted to hue, R.string.dns_tag_malware to c.muted)
        Upstream.Custom -> listOf(R.string.dns_tag_encrypted to hue, R.string.dns_tag_yours to c.muted)
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

/** One colour per provider, so the card reads at a glance; the phone's own DNS is amber (not private). */
@Composable
private fun upstreamHue(u: Upstream, encrypted: Boolean): Color {
    val c = Warden.colors
    if (!encrypted) return c.warning
    return when (u) {
        Upstream.Cloudflare -> c.palette[2]
        Upstream.Quad9 -> c.palette[3]
        else -> c.palette[4]
    }
}
