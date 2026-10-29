package dev.warden.engine

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

/** What the UI observes. The engine behind it (firestack today) stays private to this module. */
object Engine {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _events = MutableStateFlow<List<NetEvent>>(emptyList())
    val events: StateFlow<List<NetEvent>> = _events.asStateFlow()

    internal fun setRunning(on: Boolean) = _running.update { on }

    // ponytail: in-memory ring of 300 for the spike; Room with 1-2s batch inserts lands in M4.
    internal fun record(e: NetEvent) = _events.update { (listOf(e) + it).take(MAX_EVENTS) }

    internal fun closed(id: String, up: Long, down: Long) = _events.update { list ->
        list.map { if (it.id == id) it.copy(bytesUp = up, bytesDown = down) else it }
    }

    private const val MAX_EVENTS = 300
}
