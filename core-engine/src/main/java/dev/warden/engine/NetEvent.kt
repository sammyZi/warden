package dev.warden.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
)

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

    private val _events = MutableStateFlow<List<NetEvent>>(emptyList())
    val events: StateFlow<List<NetEvent>> = _events.asStateFlow()

    internal fun setRunning(on: Boolean) = _running.update { on }

    // Events land here from firestack's threads at any rate; the UI sees a snapshot at most every
    // PUBLISH_MS. Publishing per event recomposed every screen dozens of times a second (jank).
    // ponytail: in-memory ring of 300; Room with 1-2s batch inserts lands in M4.
    private val buffer = ArrayDeque<NetEvent>()
    private val kick = Channel<Unit>(Channel.CONFLATED)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            for (@Suppress("UnusedPrivateProperty") signal in kick) {
                delay(PUBLISH_MS)
                _events.value = synchronized(buffer) { buffer.toList() }
            }
        }
    }

    internal fun record(e: NetEvent) {
        synchronized(buffer) {
            buffer.addFirst(e)
            if (buffer.size > MAX_EVENTS) buffer.removeLast()
        }
        kick.trySend(Unit)
    }

    internal fun closed(id: String, up: Long, down: Long) {
        synchronized(buffer) {
            val i = buffer.indexOfFirst { it.id == id }
            if (i >= 0) buffer[i] = buffer[i].copy(bytesUp = up, bytesDown = down)
        }
        kick.trySend(Unit)
    }

    private const val MAX_EVENTS = 300
    private const val PUBLISH_MS = 500L
}
