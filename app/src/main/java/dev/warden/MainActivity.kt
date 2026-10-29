package dev.warden

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.warden.engine.Engine
import dev.warden.engine.NetEvent
import dev.warden.engine.WardenVpnService
import java.text.DateFormat
import java.util.Date

/** M0 spike screen: start/stop plus a raw event list. Replaced by the real UI in M1. */
class MainActivity : ComponentActivity() {

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) WardenVpnService.start(this)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            MaterialTheme {
                val running by Engine.running.collectAsState()
                val events by Engine.events.collectAsState()
                SpikeScreen(running, events, packageManager, onToggle = { toggle(running) })
            }
        }
    }

    private fun toggle(running: Boolean) {
        if (running) return WardenVpnService.stop(this)
        val consent = VpnService.prepare(this)
        if (consent != null) vpnConsent.launch(consent) else WardenVpnService.start(this)
    }
}

@Composable
private fun SpikeScreen(running: Boolean, events: List<NetEvent>, pm: PackageManager, onToggle: () -> Unit) {
    val labels = remember { HashMap<Int, String>() }
    fun label(uid: Int) = labels.getOrPut(uid) {
        if (uid < 0) "Unknown/System" else pm.getNameForUid(uid) ?: "uid $uid"
    }
    val time = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Warden engine spike", style = MaterialTheme.typography.headlineSmall)
        Text("Blocks ${WardenVpnService.BLOCKED_DOMAIN} and its subdomains. Everything else is allowed.")
        Button(onClick = onToggle, modifier = Modifier.fillMaxWidth()) { Text(if (running) "Stop" else "Start") }
        Text("${events.size} events, ${events.count { it.blocked }} blocked", style = MaterialTheme.typography.labelLarge)
        LazyColumn(Modifier.fillMaxSize()) {
            items(events, key = { it.id }) { e ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(time.format(Date(e.time)), style = MaterialTheme.typography.labelSmall)
                        Text(e.kind.uppercase(), style = MaterialTheme.typography.labelSmall)
                        Text(
                            if (e.blocked) "BLOCKED" else "allowed",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (e.blocked) Color(0xFFB9391F) else Color(0xFF1F6F5B),
                        )
                    }
                    Text(label(e.uid), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOfNotNull(e.domain, e.target).joinToString("  →  ") +
                            if (e.bytesUp + e.bytesDown > 0) "  (↑${e.bytesUp} ↓${e.bytesDown} B)" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
