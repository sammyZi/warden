package dev.warden.rules

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

enum class Verdict { Allow, Block }

/** What the user picked for one domain, as seen from one app. */
enum class DomainChoice { Default, AllowHere, BlockHere, BlockEverywhere }

/**
 * Custom domain rules plus the one place that decides. Precedence (spec):
 * per-app domain rule > per-app network rule > global domain rule > blocklists > allow.
 * Domain rules match subdomains too. ponytail: SharedPreferences; Room arrives with logging.
 */
object Rules {
    private val _domains = MutableStateFlow<Map<String, Verdict>>(emptyMap())
    val domains: StateFlow<Map<String, Verdict>> = _domains.asStateFlow()

    // Rules only gate new connections; apps like Chrome and WhatsApp keep long-lived ones open.
    // After a change the VPN closes the affected connections so the new rule bites immediately.
    private val _resets = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Connections to close after a rule change: a CSV of UIDs, or "" for every connection. */
    val resets: SharedFlow<String> = _resets.asSharedFlow()

    internal fun reset(uids: String) {
        _resets.tryEmit(uids)
    }

    private val _restarts = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The tunnel must be rebuilt (VPN exclusions only apply when it is established). */
    val restarts: SharedFlow<Unit> = _restarts.asSharedFlow()

    internal fun restart() {
        _restarts.tryEmit(Unit)
    }

    private val started = AtomicBoolean()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        Blocklists.init(context)
        AppRules.init(context)
        Trackers.init(context)
        if (!started.compareAndSet(false, true)) return
        prefs = context.applicationContext.getSharedPreferences("domain_rules", Context.MODE_PRIVATE)
        _domains.value = prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to Verdict.valueOf(it) } }.toMap()
    }

    /** Should a flow or query from [uid] to any of [names] be blocked? Safe from any thread. */
    fun blocks(uid: Int, names: List<String>, cellular: Boolean): Boolean {
        val rules = _domains.value
        names.firstNotNullOfOrNull { lookup(rules, "$uid:", it) }?.let { return it == Verdict.Block }
        if (AppRules.blocks(uid, cellular)) return true
        names.firstNotNullOfOrNull { lookup(rules, GLOBAL, it) }?.let { return it == Verdict.Block }
        return names.any(Blocklists::isBlocked)
    }

    fun choice(uid: Int, domain: String): DomainChoice {
        val rules = _domains.value
        return when (rules["$uid:$domain"]) {
            Verdict.Block -> DomainChoice.BlockHere
            Verdict.Allow -> DomainChoice.AllowHere
            null -> if (rules["$GLOBAL$domain"] == Verdict.Block) DomainChoice.BlockEverywhere else DomainChoice.Default
        }
    }

    fun choose(uid: Int, domain: String, choice: DomainChoice) {
        val app = "$uid:$domain"
        val global = "$GLOBAL$domain"
        val changes = when (choice) {
            DomainChoice.Default -> mapOf(app to null, global to null)
            DomainChoice.AllowHere -> mapOf(app to Verdict.Allow, global to null)
            DomainChoice.BlockHere -> mapOf(app to Verdict.Block, global to null)
            DomainChoice.BlockEverywhere -> mapOf(app to null, global to Verdict.Block)
        }
        val touchedGlobal = choice == DomainChoice.BlockEverywhere || global in _domains.value
        prefs.edit().apply { changes.forEach { (k, v) -> if (v == null) remove(k) else putString(k, v.name) } }.apply()
        _domains.update { current ->
            current.toMutableMap().apply { changes.forEach { (k, v) -> if (v == null) remove(k) else put(k, v) } }
        }
        // A global rule can touch any app; a per-app one only this app.
        reset(if (touchedGlobal) "" else uid.toString())
    }

    /** Delete one stored rule by its key ("<uid>:<domain>" or "*:<domain>"). */
    fun remove(key: String) {
        prefs.edit().remove(key).apply()
        _domains.update { it - key }
        reset(if (key.startsWith(GLOBAL)) "" else key.substringBefore(':'))
    }

    internal fun lookup(rules: Map<String, Verdict>, prefix: String, name: String): Verdict? {
        var d = name
        while (true) {
            rules[prefix + d]?.let { return it }
            val dot = d.indexOf('.')
            if (dot < 0) return null
            d = d.substring(dot + 1)
        }
    }

    private const val GLOBAL = "*:"
}
