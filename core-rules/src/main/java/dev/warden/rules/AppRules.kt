package dev.warden.rules

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

/** Per-app network access. Both true = the app is fully offline. */
data class AppRule(val blockWifi: Boolean = false, val blockCellular: Boolean = false)

/**
 * Per-app Wi-Fi / mobile data blocking, read by the VPN on every new flow and DNS query.
 * ponytail: SharedPreferences keyed by UID (reinstalling an app resets it); Room arrives with logging.
 */
object AppRules {
    private val _rules = MutableStateFlow<Map<Int, AppRule>>(emptyMap())
    val rules: StateFlow<Map<Int, AppRule>> = _rules.asStateFlow()

    private val _excluded = MutableStateFlow<Set<String>>(emptySet())

    /** Packages that skip Warden entirely (not routed through the VPN, so neither filtered nor logged). */
    val excluded: StateFlow<Set<String>> = _excluded.asStateFlow()

    private val _lockdown = MutableStateFlow(false)

    /** Lockdown: every app is offline unless allowed below, including apps installed later. */
    val lockdown: StateFlow<Boolean> = _lockdown.asStateFlow()

    private val _allowed = MutableStateFlow<Set<Int>>(emptySet())

    /** UIDs that keep their connection while lockdown is on. */
    val allowed: StateFlow<Set<Int>> = _allowed.asStateFlow()

    private val started = AtomicBoolean()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        prefs = context.applicationContext.getSharedPreferences("app_rules", Context.MODE_PRIVATE)
        _rules.value = prefs.all.mapNotNull { (key, value) ->
            val uid = key.toIntOrNull() ?: return@mapNotNull null
            val bits = value as? Int ?: return@mapNotNull null
            uid to AppRule(blockWifi = bits and WIFI != 0, blockCellular = bits and CELLULAR != 0)
        }.toMap()
        _excluded.value = prefs.getStringSet(EXCLUDED, emptySet()).orEmpty()
        _lockdown.value = prefs.getBoolean(LOCKDOWN, false)
        _allowed.value = prefs.getStringSet(ALLOWED, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
    }

    /** Does [uid] get blocked on the current network? Safe from any thread. */
    fun blocks(uid: Int, cellular: Boolean): Boolean {
        if (_lockdown.value && !isCore(uid) && uid !in _allowed.value) return true
        val rule = _rules.value[uid] ?: return false
        return if (cellular) rule.blockCellular else rule.blockWifi
    }

    fun set(uid: Int, rule: AppRule) {
        val bits = (if (rule.blockWifi) WIFI else 0) or (if (rule.blockCellular) CELLULAR else 0)
        prefs.edit().apply { if (bits == 0) remove(uid.toString()) else putInt(uid.toString(), bits) }.apply()
        _rules.update { if (bits == 0) it - uid else it + (uid to rule) }
        Rules.reset(uid.toString())
    }

    /** Android applies exclusions when the VPN starts, so a change restarts the tunnel. */
    fun setExcluded(packages: Collection<String>, excluded: Boolean) {
        val next = if (excluded) _excluded.value + packages else _excluded.value - packages.toSet()
        prefs.edit().putStringSet(EXCLUDED, next).apply()
        _excluded.value = next
        Rules.restart()
    }

    fun setLockdown(on: Boolean) {
        prefs.edit().putBoolean(LOCKDOWN, on).apply()
        _lockdown.value = on
        Rules.reset("") // every app's open connections are re-checked
    }

    fun setAllowed(uid: Int, allowed: Boolean) {
        val next = if (allowed) _allowed.value + uid else _allowed.value - uid
        prefs.edit().putStringSet(ALLOWED, next.map { it.toString() }.toSet()).apply()
        _allowed.value = next
        Rules.reset(uid.toString())
    }

    /**
     * Android itself (UIDs below 10000 in any user profile) and unknown owners stay online in lockdown:
     * blocking them breaks network checks, time sync and DNS for everyone.
     * ponytail: all-or-nothing for core UIDs; add a "lock Android system too" switch if people ask.
     */
    internal fun isCore(uid: Int) = uid < 0 || uid % PER_USER_RANGE < FIRST_APP_UID

    private const val LOCKDOWN = "lockdown"
    private const val ALLOWED = "lockdown_allowed"
    private const val PER_USER_RANGE = 100_000
    private const val FIRST_APP_UID = 10_000
    private const val EXCLUDED = "excluded"
    private const val WIFI = 1
    private const val CELLULAR = 2
}
