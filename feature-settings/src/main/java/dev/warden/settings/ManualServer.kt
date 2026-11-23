package dev.warden.settings

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import dev.warden.design.ButtonKind
import dev.warden.design.Radii
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.WardenButton
import dev.warden.rules.Routing
import dev.warden.rules.WireGuard

/** WireGuard details typed in, for providers that show them on a web page instead of giving a file. */
@Composable
internal fun ManualServerDialog(onDone: () -> Unit) {
    val c = Warden.colors
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var privateKey by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var dns by rememberSaveable { mutableStateOf("") }
    var publicKey by rememberSaveable { mutableStateOf("") }
    var endpoint by rememberSaveable { mutableStateOf("") }
    var presharedKey by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.wg_manual_title), style = Warden.type.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Field(R.string.wg_f_name, name, { name = it }, R.string.wg_f_name_hint)
                Field(R.string.wg_f_private, privateKey, { privateKey = it }, R.string.wg_f_key_hint, secret = true)
                Field(R.string.wg_f_address, address, { address = it }, R.string.wg_f_address_hint)
                Field(R.string.wg_f_dns, dns, { dns = it }, R.string.wg_f_dns_hint)
                Field(R.string.wg_f_public, publicKey, { publicKey = it }, R.string.wg_f_key_hint, secret = true)
                Field(R.string.wg_f_endpoint, endpoint, { endpoint = it }, R.string.wg_f_endpoint_hint)
                Field(R.string.wg_f_psk, presharedKey, { presharedKey = it }, R.string.wg_f_optional, secret = true)
                error?.let { Text(stringResource(R.string.wg_error, it), style = Warden.type.label, color = c.blocked) }
            }
        },
        confirmButton = {
            WardenButton(stringResource(R.string.wg_manual_save), {
                error = runCatching {
                    Routing.addServer(WireGuard.conf(privateKey, address, dns, publicKey, endpoint, presharedKey), name)
                }.exceptionOrNull()?.let { problemText(context, it, context.getString(R.string.wg_unreadable)) }
                if (error == null) onDone()
            })
        },
        dismissButton = { WardenButton(stringResource(R.string.settings_cancel), onDone, kind = ButtonKind.Text) },
        containerColor = c.surface,
    )
}

@Composable
internal fun Field(label: Int, value: String, onChange: (String) -> Unit, hint: Int, secret: Boolean = false) {
    val c = Warden.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(label), style = Warden.type.label, color = c.muted)
        BasicTextField(
            value,
            onChange,
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.small))
                .background(c.surfaceVariant)
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            singleLine = true,
            textStyle = Warden.type.body.copy(color = c.text),
            cursorBrush = SolidColor(c.accentInk),
            // Keys as "password" input so the keyboard doesn't learn or suggest them.
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii,
                autoCorrectEnabled = false,
            ),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) Text(stringResource(hint), style = Warden.type.body, color = c.muted)
                    field()
                }
            },
        )
    }
}
