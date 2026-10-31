package dev.warden.engine

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Engine-agnostic record of one DNS answer or one connection. Metadata only, never payload. */
data class NetEvent(
    val id: String,
    val time: Long,
    val uid: Int,
    val kind: String, // "dns", "tcp", "udp"
    val domain: String?,
    val target: String,
    val blocked: Boolean,
    val bytesUp: Long = 0,
    val bytesDown: Long = 0,
    /** DNS answer time, or the connection's round-trip time once it closes. */
    val latencyMs: Int? = null,
    /** DNS lookup with no answer, or a connection that closed with an error. */
    val failed: Boolean = false,
)

/** A connection's final numbers, reported when it closes. */
data class Closed(val id: String, val up: Long, val down: Long, val rttMs: Int?, val failed: Boolean)

/** Event counts per [bucketMs] slice over the last [buckets] slices, oldest first. */
fun List<NetEvent>.activity(buckets: Int = 20, bucketMs: Long = 15_000L, now: Long = System.currentTimeMillis()): List<Int> {
    val counts = IntArray(buckets)
    forEach {
        val age = ((now - it.time) / bucketMs).toInt()
        if (age in 0 until buckets) counts[buckets - 1 - age]++
    }
    return counts.toList()
}

/** What the UI observes. The engine behind it (firestack today) stays private to this module. */
object Engine {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    internal fun setRunning(on: Boolean) = _running.update { on }

    private val _dnsEncrypted = MutableStateFlow(false)

    /** True while allowed lookups really go to an encrypted (DoH) server, not just when one is chosen. */
    val dnsEncrypted: StateFlow<Boolean> = _dnsEncrypted.asStateFlow()

    internal fun setDnsEncrypted(on: Boolean) {
        _dnsEncrypted.value = on
    }

    /** Blocked flows and lookups since the tunnel started, for the notification. */
    internal val blocked = AtomicInteger()

    // Persistence queues, drained in batches by the history writer (bounded if nobody drains).
    private val pendingNew = ArrayList<NetEvent>()
    private val pendingClosed = ArrayList<Closed>()

    /** Events recorded since the last call, oldest first. */
    fun drainNew(): List<NetEvent> = synchronized(pendingNew) { pendingNew.toList().also { pendingNew.clear() } }

    /** Connection closes since the last call. */
    fun drainClosed(): List<Closed> = synchronized(pendingClosed) { pendingClosed.toList().also { pendingClosed.clear() } }

    /** Forget everything in memory (the user cleared history). */
    fun clear() {
        synchronized(pendingNew) { pendingNew.clear() }
        synchronized(pendingClosed) { pendingClosed.clear() }
    }

    internal fun record(e: NetEvent) {
        if (e.blocked) blocked.incrementAndGet()
        synchronized(pendingNew) { if (pendingNew.size < MAX_PENDING) pendingNew.add(e) }
    }

    internal fun closed(c: Closed) {
        synchronized(pendingClosed) { if (pendingClosed.size < MAX_PENDING) pendingClosed.add(c) }
    }

    private const val MAX_PENDING = 20_000
}
