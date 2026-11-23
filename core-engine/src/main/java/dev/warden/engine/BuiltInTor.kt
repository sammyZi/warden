package dev.warden.engine

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Failed: Tor stopped or reported an error while apps still wanted it (a port taken, say). */
enum class TorState { Off, Starting, On, Failed }

/**
 * Tor built into Warden (tor-android, BSD-3-Clause, Guardian Project), started by the VPN only while
 * some app is routed through it. It runs in its own ":tor" process (see the manifest): Tor and
 * firestack's Go runtime in one process stopped the tunnel answering anything. This process must
 * never touch the TorService class itself, since loading it loads libtor here.
 * Warden's own traffic skips its VPN, so Tor's connections go straight out. [state] turns On once
 * Tor has built its first circuit. Tor's DNS port resolves names through Tor, so lookups don't leak.
 */
object BuiltInTor {
    // Fixed ports, so this process needn't ask Tor. ponytail: if one is taken Tor fails (shown as Failed,
    // with Try again) and Tor apps stay offline; pick free ports at start if that's ever reported.
    const val SOCKS_PORT = 9065
    const val DNS_PORT = 9066

    private val _state = MutableStateFlow(TorState.Off)
    val state: StateFlow<TorState> = _state.asStateFlow()

    private var bound: Context? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) = Unit

        override fun onServiceDisconnected(name: ComponentName?) {
            _state.value = TorState.Starting // the :tor process died; Android rebinds and Tor starts again
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // stop() unregisters first, so an OFF/STOPPING or an error arriving here wasn't asked for.
            _state.value = when {
                intent.action == ACTION_ERROR -> TorState.Failed
                intent.getStringExtra(EXTRA_STATUS) == "ON" -> TorState.On
                intent.getStringExtra(EXTRA_STATUS) == "STARTING" -> TorState.Starting
                else -> TorState.Failed
            }
        }
    }

    @Synchronized fun start(context: Context) {
        if (bound != null) return
        val app = context.applicationContext
        // tor-android's torrc (TorService.getTorrc); these override its SOCKSPort default of 9050.
        runCatching {
            File(app.getDir("TorService", Context.MODE_PRIVATE), "torrc")
                .writeText("SOCKSPort 127.0.0.1:$SOCKS_PORT\nDNSPort 127.0.0.1:$DNS_PORT\n")
        }
        // Not exported: only our own app (the :tor process) can report, so no other app can fake "connected".
        val filter = IntentFilter(ACTION_STATUS).apply { addAction(ACTION_ERROR) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(receiver, filter)
        }
        _state.value = TorState.Starting
        app.bindService(Intent().setClassName(app, TOR_SERVICE), connection, Context.BIND_AUTO_CREATE)
        bound = app
    }

    @Synchronized fun stop() {
        val app = bound ?: return
        bound = null
        runCatching { app.unbindService(connection) }
        runCatching { app.unregisterReceiver(receiver) }
        // Tor runs once per process, so end :tor; the next start gets a fresh one.
        app.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty()
            .filter { it.processName == app.packageName + TOR_PROCESS }
            .forEach { Process.killProcess(it.pid) }
        _state.value = TorState.Off
    }

    /** Start Tor again from scratch, e.g. after it failed. */
    @Synchronized fun retry(context: Context) {
        stop()
        start(context)
    }

    private const val TOR_PROCESS = ":tor" // matches android:process in the manifest
    private const val TOR_SERVICE = "org.torproject.jni.TorService"
    private const val ACTION_STATUS = "org.torproject.android.intent.action.STATUS"
    private const val ACTION_ERROR = "org.torproject.android.intent.action.ERROR"
    private const val EXTRA_STATUS = "org.torproject.android.intent.extra.STATUS"
}
