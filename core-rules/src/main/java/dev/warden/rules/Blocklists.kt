package dev.warden.rules

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** A downloadable list. Nothing is bundled: lists come from their publishers when switched on. */
data class BlocklistSource(val id: String, val name: String, val url: String, val license: String)

data class BlocklistState(
    val enabled: Boolean = false,
    val entries: Int = 0,
    val updatedAt: Long = 0,
    val busy: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Downloads, stores and matches blocklists. Shared by the VPN service (matching) and the UI
 * (switches). Both live in one process, so this is a plain object; [init] is idempotent.
 */
object Blocklists {
    val catalog = listOf(
        BlocklistSource(
            "hagezi-light", "HaGeZi Light",
            "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/light-onlydomains.txt", "GPL-3.0",
        ),
        BlocklistSource(
            "hagezi-normal", "HaGeZi Multi Normal",
            "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/multi-onlydomains.txt", "GPL-3.0",
        ),
        BlocklistSource("stevenblack", "StevenBlack Unified", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", "MIT"),
        BlocklistSource("oisd-small", "OISD Small", "https://small.oisd.nl/domainswild", "GPL-3.0"),
    )

    private val _states = MutableStateFlow(catalog.associate { it.id to BlocklistState() })
    val states: StateFlow<Map<String, BlocklistState>> = _states.asStateFlow()

    @Volatile private var matcher = DomainSet(emptySet())
    private val started = AtomicBoolean()
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dir: File
    private lateinit var prefs: SharedPreferences
    private lateinit var cm: ConnectivityManager

    /** True when [domain] (or a parent of it) is on an enabled list. Safe from any thread. */
    fun isBlocked(domain: String): Boolean = matcher.matches(domain)

    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        dir = File(app.filesDir, "blocklists").apply { mkdirs() }
        prefs = app.getSharedPreferences("blocklists", Context.MODE_PRIVATE)
        cm = app.getSystemService(ConnectivityManager::class.java)
        _states.value = catalog.associate {
            it.id to BlocklistState(
                enabled = prefs.getBoolean("on.${it.id}", false),
                entries = prefs.getInt("n.${it.id}", 0),
                updatedAt = prefs.getLong("t.${it.id}", 0),
            )
        }
        scope.launch {
            rebuild()
            refreshStale()
        }
    }

    fun setEnabled(id: String, on: Boolean) {
        prefs.edit().putBoolean("on.$id", on).apply()
        set(id) { it.copy(enabled = on, failed = false) }
        scope.launch {
            if (on && !file(id).exists()) download(id)
            rebuild()
        }
    }

    fun refresh(id: String) {
        scope.launch {
            download(id)
            rebuild()
        }
    }

    private fun file(id: String) = File(dir, "$id.txt")

    private fun set(id: String, f: (BlocklistState) -> BlocklistState) = _states.update { it + (id to f(it.getValue(id))) }

    private suspend fun download(id: String) {
        val source = catalog.first { it.id == id }
        set(id) { it.copy(busy = true, failed = false) }
        try {
            val tmp = File(dir, "$id.tmp")
            fetch(source.url, tmp)
            val entries = tmp.useLines { BlocklistParser.parse(it, HashSet()) }
            if (entries == 0) throw IOException("no domains in ${source.url}")
            lock.withLock { tmp.renameTo(file(id)) || throw IOException("could not save $id") }
            val now = System.currentTimeMillis()
            prefs.edit().putInt("n.$id", entries).putLong("t.$id", now).apply()
            set(id) { it.copy(entries = entries, updatedAt = now, busy = false) }
        } catch (e: IOException) {
            Log.w(TAG, "download $id failed", e)
            set(id) { it.copy(busy = false, failed = true) }
        }
    }

    private fun fetch(url: String, to: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("User-Agent", "Warden")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.use { input -> to.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
    }

    /** Re-reads every enabled list into one matcher; swapped in atomically when ready. */
    private suspend fun rebuild() = lock.withLock {
        val all = HashSet<String>()
        _states.value.filterValues { it.enabled }.keys.forEach { id ->
            file(id).takeIf { it.exists() }?.useLines { BlocklistParser.parse(it, all) }
        }
        matcher = DomainSet(all)
    }

    /** ponytail: weekly refresh checked on start, unmetered networks only; WorkManager scheduling comes later. */
    private suspend fun refreshStale() {
        if (cm.isActiveNetworkMetered) return
        val stale = _states.value.filter { (_, s) ->
            s.enabled && System.currentTimeMillis() - s.updatedAt > TimeUnit.DAYS.toMillis(STALE_DAYS)
        }
        if (stale.isEmpty()) return
        stale.keys.forEach { download(it) }
        rebuild()
    }

    private const val TAG = "Blocklists"
    private const val TIMEOUT_MS = 30_000
    private const val STALE_DAYS = 7L
}
