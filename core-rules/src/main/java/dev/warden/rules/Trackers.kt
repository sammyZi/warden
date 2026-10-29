package dev.warden.rules

import android.content.Context
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Who runs a tracker domain and what it's for. */
data class Tracker(val company: String, val category: String)

/**
 * Domain-to-company map from Warden's own list (assets/trackers.tsv, GPL-3.0). Third-party lists
 * (Disconnect, Ghostery TrackerDB, Tracker Radar) are CC BY-NC-SA and Exodus states no data license,
 * so none of them are used. Matching covers subdomains.
 */
object Trackers {
    @Volatile private var map: Map<String, Tracker> = emptyMap()
    private val started = AtomicBoolean()

    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        map = context.applicationContext.assets.open("trackers.tsv").use(::parse)
    }

    /** The tracker behind [domain] (or a parent domain of it), if it's a known one. */
    fun lookup(domain: String?): Tracker? {
        var d = domain ?: return null
        while (true) {
            map[d]?.let { return it }
            val dot = d.indexOf('.')
            if (dot < 0) return null
            d = d.substring(dot + 1)
        }
    }

    internal fun parse(input: InputStream): Map<String, Tracker> = input.bufferedReader().readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line -> line.split('\t').takeIf { it.size == 3 }?.let { (d, company, category) -> d to Tracker(company, category) } }
        .toMap()

    internal fun load(map: Map<String, Tracker>) {
        this.map = map
    }
}

/** Summary of trackers among a set of domains: "14 trackers from 6 companies". */
data class TrackerSummary(val trackers: Int, val companies: Int, val byCompany: List<Pair<String, List<String>>>)

/** Groups the known trackers among [domains] by company, biggest first. Each company lists its categories. */
fun summarizeTrackers(domains: Collection<String?>): TrackerSummary {
    val hits = domains.filterNotNull().distinct().mapNotNull { d -> Trackers.lookup(d)?.let { d to it } }
    val byCompany = hits.groupBy { it.second.company }
        .map { (company, list) -> company to list.map { it.second.category }.distinct() }
        .sortedByDescending { (company, _) -> hits.count { it.second.company == company } }
    return TrackerSummary(hits.size, byCompany.size, byCompany)
}
