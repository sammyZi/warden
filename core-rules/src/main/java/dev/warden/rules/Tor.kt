package dev.warden.rules

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Apps whose traffic goes through Tor, via Orbot's local SOCKS proxy. Orbot (BSD-licensed, Guardian
 * Project) runs Tor; Warden only points chosen apps at it. If Orbot isn't running those apps get no
 * connection at all rather than quietly going direct.
 */
object Tor {
    const val ORBOT = "org.torproject.android"
    const val DEFAULT_SOCKS_PORT = 9050

    private val _apps = MutableStateFlow<Set<Int>>(emptySet())
    val apps: StateFlow<Set<Int>> = _apps.asStateFlow()

    /** Orbot's SOCKS port, as it last reported (it can be changed in Orbot's settings). */
    @Volatile var socksPort = DEFAULT_SOCKS_PORT
        private set

    private val started = AtomicBoolean()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        prefs = context.applicationContext.getSharedPreferences("tor", Context.MODE_PRIVATE)
        _apps.value = prefs.getStringSet(APPS, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        socksPort = prefs.getInt(PORT, DEFAULT_SOCKS_PORT)
    }

    fun uses(uid: Int) = uid in _apps.value

    fun set(uid: Int, on: Boolean) {
        val next = if (on) _apps.value + uid else _apps.value - uid
        prefs.edit().putStringSet(APPS, next.map { it.toString() }.toSet()).apply()
        _apps.value = next
        Rules.restart() // the tunnel adds the Tor route only while some app uses it
    }

    fun reportSocksPort(port: Int) {
        if (port == socksPort) return
        socksPort = port
        prefs.edit().putInt(PORT, port).apply()
        if (_apps.value.isNotEmpty()) Rules.restart()
    }

    private const val APPS = "apps"
    private const val PORT = "socks_port"
}
