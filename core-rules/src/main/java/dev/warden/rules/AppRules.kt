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
    }

    /** Does [uid] get blocked on the current network? Safe from any thread. */
    fun blocks(uid: Int, cellular: Boolean): Boolean {
        val rule = _rules.value[uid] ?: return false
        return if (cellular) rule.blockCellular else rule.blockWifi
    }

    fun set(uid: Int, rule: AppRule) {
        val bits = (if (rule.blockWifi) WIFI else 0) or (if (rule.blockCellular) CELLULAR else 0)
        prefs.edit().apply { if (bits == 0) remove(uid.toString()) else putInt(uid.toString(), bits) }.apply()
        _rules.update { if (bits == 0) it - uid else it + (uid to rule) }
    }

    private const val WIFI = 1
    private const val CELLULAR = 2
}
