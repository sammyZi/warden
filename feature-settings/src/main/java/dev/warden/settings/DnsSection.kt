package dev.warden.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.warden.design.ButtonKind
import dev.warden.design.ChoiceChips
import dev.warden.design.Radii
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.WardenButton
import dev.warden.design.WardenSwitch
import dev.warden.rules.Dns
import dev.warden.rules.Upstream
import dev.warden.rules.isValidDohUrl

/** Upstream resolver, and stopping apps' own encrypted DNS. */
@Composable
internal fun ColumnScope.DnsSettings() {
    val c = Warden.colors
    val config by Dns.config.collectAsState()

    Text(stringResource(R.string.dns_upstream), style = Warden.type.body)
    ChoiceChips(
        Upstream.entries.map {
            stringResource(
                when (it) {
                    Upstream.Phone -> R.string.dns_upstream_phone
                    Upstream.Cloudflare -> R.string.dns_upstream_cloudflare
                    Upstream.Quad9 -> R.string.dns_upstream_quad9
                    Upstream.Custom -> R.string.dns_upstream_custom
                },
            )
        },
        config.upstream.ordinal,
        // Custom without a saved URL keeps using the phone's DNS until one is saved below.
        { Dns.setUpstream(Upstream.entries[it]) },
    )
    Text(
        stringResource(
            when (config.upstream) {
                Upstream.Phone -> R.string.dns_upstream_phone_note
                Upstream.Custom -> R.string.dns_upstream_custom_note
                else -> R.string.dns_upstream_doh_note
            },
        ),
        style = Warden.type.label,
        color = c.muted,
    )
    if (config.upstream == Upstream.Custom) CustomUrl(config.customUrl)

    HorizontalDivider(color = c.outline)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Spacing.m)) {
            Text(stringResource(R.string.dns_bypass), style = Warden.type.body)
            Text(stringResource(R.string.dns_bypass_note), style = Warden.type.label, color = c.muted)
        }
        WardenSwitch(config.stopBypass, Dns::setStopBypass, contentDescription = stringResource(R.string.dns_bypass))
    }
}

@Composable
private fun CustomUrl(saved: String) {
    val c = Warden.colors
    var url by rememberSaveable(saved) { mutableStateOf(saved.ifEmpty { "https://" }) }
    val valid = isValidDohUrl(url.trim())
    BasicTextField(
        url,
        { url = it },
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.small))
            .background(c.surfaceVariant)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        singleLine = true,
        textStyle = Warden.type.body.copy(color = c.text),
        cursorBrush = SolidColor(c.accentInk),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        decorationBox = { field ->
            Box {
                if (url.isEmpty()) Text(stringResource(R.string.dns_custom_hint), style = Warden.type.body, color = c.muted)
                field()
            }
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            stringResource(
                when {
                    saved.isNotEmpty() && saved == url.trim() -> R.string.dns_custom_in_use
                    valid -> R.string.dns_custom_ready
                    else -> R.string.dns_custom_invalid
                },
            ),
            Modifier.weight(1f),
            style = Warden.type.label,
            color = if (valid) c.muted else c.blocked,
        )
        WardenButton(
            stringResource(R.string.dns_custom_save),
            { Dns.setUpstream(Upstream.Custom, url.trim()) },
            kind = ButtonKind.Tonal,
            enabled = valid && url.trim() != saved,
        )
    }
}
