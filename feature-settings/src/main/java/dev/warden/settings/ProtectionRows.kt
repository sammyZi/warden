package dev.warden.settings

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import android.provider.OpenableColumns
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.warden.design.ButtonKind
import dev.warden.design.IconBadge
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.WardenButton
import dev.warden.design.WardenSwitch
import dev.warden.rules.AppRules
import dev.warden.rules.Routing
import dev.warden.design.R as DesignR

/** Lockdown: every app offline unless allowed on its own page. */
@Composable
internal fun LockdownRow() {
    val c = Warden.colors
    val on by AppRules.lockdown.collectAsState()
    val allowed by AppRules.allowed.collectAsState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconBadge(DesignR.drawable.ph_lock_simple, if (on) c.blocked else c.palette[3])
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.lockdown), style = Warden.type.body)
            Text(
                if (on) stringResource(R.string.lockdown_on, allowed.size) else stringResource(R.string.lockdown_off),
                style = Warden.type.label,
                color = if (on) c.blocked else c.muted,
            )
        }
        WardenSwitch(on, AppRules::setLockdown, contentDescription = stringResource(R.string.lockdown))
    }
}

/** Opt-in daily check for a new Warden release on GitHub, shown as a notification. */
@Composable
internal fun UpdateRow() {
    val c = Warden.colors
    val context = LocalContext.current
    var on by rememberSaveable { mutableStateOf(UpdateCheck.enabled(context)) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconBadge(DesignR.drawable.ph_bell_simple_light, c.palette[3])
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.update_row), style = Warden.type.body)
            Text(stringResource(R.string.update_row_note), style = Warden.type.label, color = c.muted)
        }
        WardenSwitch(on, {
            on = it
            UpdateCheck.setEnabled(context, it)
        }, contentDescription = stringResource(R.string.update_row))
    }
}

/** Saved WireGuard servers (one per country, say): pick the one "Your VPN" apps use, add, remove. */
@Composable
internal fun WireGuardRow() {
    val c = Warden.colors
    val context = LocalContext.current
    val servers by Routing.servers.collectAsState()
    val selected by Routing.selected.collectAsState()
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val badFile = stringResource(R.string.wg_unreadable)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        error = runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }
            Routing.addServer(text ?: throw IllegalArgumentException(badFile), displayName(context, uri))
        }.exceptionOrNull()?.let { it.message ?: badFile }
    }
    var manual by rememberSaveable { mutableStateOf(false) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanning = true else error = context.getString(R.string.wg_scan_no_camera)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconBadge(DesignR.drawable.ph_lock_key_light, c.palette[1])
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.wg_title), style = Warden.type.body)
            Text(
                stringResource(if (servers.isEmpty()) R.string.wg_none else R.string.wg_pick),
                style = Warden.type.label,
                color = c.muted,
            )
        }
        // Scan a provider's WireGuard QR code; the camera is asked for only here.
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    scanning = true
                } else {
                    askCamera.launch(Manifest.permission.CAMERA)
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(DesignR.drawable.ph_scan), stringResource(R.string.wg_scan), Modifier.size(24.dp), tint = c.text)
        }
    }
    servers.forEach { server ->
        val on = server.id == selected
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .selectable(on, role = Role.RadioButton) { Routing.select(server.id) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (on) Icon(painterResource(DesignR.drawable.ph_check), null, Modifier.size(20.dp), tint = c.accentInk)
            }
            Column(Modifier.weight(1f)) {
                Text(server.name, style = Warden.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(server.config.endpoint, style = Warden.type.label, color = c.muted, maxLines = 1)
            }
            WardenButton(stringResource(R.string.wg_remove), { removing = server.id }, kind = ButtonKind.Text)
        }
    }
    if (servers.isNotEmpty()) {
        val all by Routing.vpnForAll.collectAsState()
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.wg_all), Modifier.weight(1f), style = Warden.type.body)
            WardenSwitch(all, Routing::setVpnForAll, contentDescription = stringResource(R.string.wg_all))
        }
    }
    error?.let { Text(stringResource(R.string.wg_error, it), style = Warden.type.label, color = c.blocked) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        WardenButton(
            stringResource(R.string.wg_import),
            { pick.launch(arrayOf("*/*")) }, // .conf has no registered MIME type
            Modifier.weight(1f),
            kind = ButtonKind.Tonal,
        )
        WardenButton(stringResource(R.string.wg_manual), { manual = true }, Modifier.weight(1f), kind = ButtonKind.Tonal)
    }
    if (manual) ManualServerDialog { manual = false }
    if (scanning) {
        QrScanDialog(
            onResult = { text ->
                scanning = false
                error = runCatching { Routing.addServer(text, "") }.exceptionOrNull()?.let { it.message ?: badFile }
            },
            onDismiss = { scanning = false },
        )
    }
    servers.firstOrNull { it.id == removing }?.let { server ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.wg_remove_title, server.name), style = Warden.type.title) },
            text = { Text(stringResource(R.string.wg_remove_body), style = Warden.type.body) },
            confirmButton = {
                WardenButton(stringResource(R.string.wg_remove), {
                    Routing.removeServer(server.id)
                    removing = null
                    error = null
                }, kind = ButtonKind.Destructive)
            },
            dismissButton = { WardenButton(stringResource(R.string.settings_cancel), { removing = null }, kind = ButtonKind.Text) },
            containerColor = c.surface,
        )
    }
}

/** The picked file's name, e.g. "proton-JP-12.conf", used as the server's label. */
private fun displayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}.getOrNull().orEmpty()
