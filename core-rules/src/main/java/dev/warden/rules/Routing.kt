package dev.warden.rules

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** Where an app's allowed traffic goes. */
enum class Route { Direct, Tor, Vpn }

/** One imported WireGuard config, e.g. a provider's server in one country. */
class VpnServer(val id: String, val name: String, val config: WireGuard)

/**
 * Per-app routing: straight out, through Tor (built into Warden; see the engine's BuiltInTor), or
 * through the user's own WireGuard server. Both detours fail closed: if Tor or
 * the WireGuard tunnel isn't up, those apps get no connection rather than quietly going direct.
 * Several servers can be saved (one per country, say); every "Your VPN" app uses the selected one.
 * ponytail: one active server for all apps; a per-app server pick needs one WireGuard proxy each.
 */
object Routing {
    private val _routes = MutableStateFlow<Map<Int, Route>>(emptyMap())
    val routes: StateFlow<Map<Int, Route>> = _routes.asStateFlow()

    private val _servers = MutableStateFlow<List<VpnServer>>(emptyList())

    /** Saved WireGuard servers, oldest first. Private keys live in app-private storage (no backups). */
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _vpnForAll = MutableStateFlow(false)

    /** Every app set to Direct goes through the selected server instead (Tor apps stay on Tor). */
    val vpnForAll: StateFlow<Boolean> = _vpnForAll.asStateFlow()

    private val _wireGuard = MutableStateFlow<WireGuard?>(null)

    /** The selected server's config, or null. This is what "Your VPN" apps go through. */
    val wireGuard: StateFlow<WireGuard?> = _wireGuard.asStateFlow()

    private val started = AtomicBoolean()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        prefs = context.applicationContext.getSharedPreferences("routing", Context.MODE_PRIVATE)
        _routes.value = prefs.all.mapNotNull { (k, v) ->
            val uid = k.removePrefix(APP).takeIf { k.startsWith(APP) }?.toIntOrNull() ?: return@mapNotNull null
            uid to (runCatching { Route.valueOf(v as String) }.getOrNull() ?: return@mapNotNull null)
        }.toMap()
        // Before servers could be listed there was one config under "wireguard": keep it as the first.
        prefs.getString(LEGACY, null)?.let { conf ->
            prefs.edit().putString(CONF + "1", conf).putString(NAME + "1", "").putString(SELECTED, "1").remove(LEGACY).apply()
        }
        _servers.value = prefs.all.keys.filter { it.startsWith(CONF) }.map { it.removePrefix(CONF) }
            .sortedBy { it.toLongOrNull() ?: 0 }
            .mapNotNull { id ->
                val parsed = runCatching { WireGuard.parse(prefs.getString(CONF + id, null).orEmpty()) }.getOrNull()
                parsed?.let { VpnServer(id, prefs.getString(NAME + id, null).orEmpty().ifEmpty { it.endpoint }, it) }
            }
        select(prefs.getString(SELECTED, null))
        _vpnForAll.value = prefs.getBoolean(ALL, false)
    }

    /** Where [uid]'s traffic goes now: its own choice, or the VPN when that's on for all apps. */
    fun route(uid: Int): Route {
        val own = _routes.value[uid] ?: Route.Direct
        return if (own == Route.Direct && vpnForAllActive()) Route.Vpn else own
    }

    fun uses(route: Route) = route in _routes.value.values || (route == Route.Vpn && vpnForAllActive())

    /** Only with a server saved: "all apps" with none would take the whole phone offline. */
    private fun vpnForAllActive() = _vpnForAll.value && _wireGuard.value != null

    fun setVpnForAll(on: Boolean) {
        prefs.edit().putBoolean(ALL, on).apply()
        _vpnForAll.value = on
    }

    fun set(uid: Int, route: Route) {
        if (route == route(uid)) return
        prefs.edit().apply { if (route == Route.Direct) remove(APP + uid) else putString(APP + uid, route.name) }.apply()
        _routes.value = if (route == Route.Direct) _routes.value - uid else _routes.value + (uid to route)
        // The VPN watches [routes] and [wireGuard] and sets Tor/WireGuard up or down on the running tunnel.
    }

    /**
     * Saves a wg-quick .conf as a new server and switches to it. [name] is shown in the list (the file
     * name, say); blank means the server's address. Throws [IllegalArgumentException] if it can't be used.
     */
    fun addServer(conf: String, name: String) {
        val parsed = WireGuard.parse(conf)
        val id = System.currentTimeMillis().toString()
        val label = name.trim().removeSuffix(".conf")
        prefs.edit().putString(CONF + id, conf).putString(NAME + id, label).apply()
        _servers.value = _servers.value + VpnServer(id, label.ifEmpty { parsed.endpoint }, parsed)
        select(id)
    }

    /** Switches every "Your VPN" app to this server; open connections are moved over by the VPN. */
    fun select(id: String?) {
        val server = _servers.value.firstOrNull { it.id == id } ?: _servers.value.firstOrNull()
        prefs.edit().putString(SELECTED, server?.id).apply()
        _selected.value = server?.id
        _wireGuard.value = server?.config
    }

    fun removeServer(id: String) {
        prefs.edit().remove(CONF + id).remove(NAME + id).apply()
        _servers.value = _servers.value.filterNot { it.id == id }
        // Removing the one in use moves to the next saved server; with none left, those apps fail closed.
        if (_selected.value == id) select(null)
    }

    private const val APP = "app:"
    private const val CONF = "wg:"
    private const val NAME = "wg_name:"
    private const val SELECTED = "wg_selected"
    private const val LEGACY = "wireguard"
    private const val ALL = "vpn_for_all"
}
