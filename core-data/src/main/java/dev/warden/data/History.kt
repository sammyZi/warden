package dev.warden.data

import android.content.Context
import android.os.PowerManager
import androidx.room.Room
import dev.warden.engine.Engine
import dev.warden.engine.NetEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/** How far back a view looks. */
enum class Range(val buckets: Int) { Today(24), Week(28), Month(30) }

/**
 * Persistent history: drains the engine every [FLUSH_MS] into Room in one transaction (spec: batch
 * inserts every 1-2s), prunes by age and size, and serves aggregates to the UI. Started once per
 * process from the Application, so it also runs when only the VPN service is alive.
 */
object History {
    private val started = AtomicBoolean()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefix = "${System.currentTimeMillis()}-"
    private lateinit var dao: ConnectionDao

    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        dao = Room.databaseBuilder(app, WardenDb::class.java, "warden.db").fallbackToDestructiveMigration(true).build().connections()
        val power = app.getSystemService(PowerManager::class.java)
        scope.launch {
            var sincePrune = Long.MAX_VALUE / 2
            while (true) {
                // Screen off: nobody is watching, so write in bigger, rarer batches (fewer disk wakeups).
                delay(
                    when {
                        !Engine.running.value -> FLUSH_IDLE_MS // protection off: nothing new to write
                        power.isInteractive -> FLUSH_MS
                        else -> FLUSH_SCREEN_OFF_MS
                    },
                )
                // ponytail: screen state is taken at write time, so a batch spanning a screen change is labelled by its end.
                val screenOn = power.isInteractive
                val new = Engine.drainNew().map { it.toRow(screenOn) }
                val closed = Engine.drainClosed()
                if (new.isNotEmpty() || closed.isNotEmpty()) dao.write(new, closed, prefix)
                sincePrune += FLUSH_MS
                if (sincePrune > PRUNE_EVERY_MS) {
                    sincePrune = 0
                    dao.deleteBefore(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(KEEP_DAYS))
                    dao.keepNewest(KEEP_ROWS)
                }
            }
        }
    }

    private val visible = MutableStateFlow(false)
    private val shownScreens = AtomicInteger()

    /**
     * A Warden screen started or stopped. Counted, not a flag: when a second copy opens on top, the new
     * one starts before the old one stops, and a flag left the app marked hidden (stats stopped updating).
     */
    fun screenStarted() {
        visible.value = shownScreens.incrementAndGet() > 0
    }

    fun screenStopped() {
        visible.value = shownScreens.decrementAndGet() > 0
    }

    /**
     * Room re-runs every observed query after each write. Left running in the background, that was most
     * of Warden's CPU (measured: ~70% on the Room threads), so UI queries pause while no screen shows.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> Flow<T>.whileVisible(): Flow<T> = visible.flatMapLatest { if (it) this else emptyFlow() }

    /** Most recent events, newest first: what the live screens (apps, map, log) show. */
    fun recent(limit: Int = RECENT_ROWS): Flow<List<NetEvent>> =
        dao.recent(limit).whileVisible().map { rows -> rows.map { it.toEvent() } }

    /** When this process started: the "this session" range. */
    val sessionStart = System.currentTimeMillis()

    /** Delete all saved history (and the engine's in-memory list). */
    fun clear() {
        Engine.clear()
        scope.launch { dao.clear() }
    }

    /** Connections (no DNS answers) since [since], newest first, capped at [limit]. */
    fun connectionsSince(since: Long, limit: Int = MAP_ROWS): Flow<List<NetEvent>> =
        dao.connectionsSince(since, limit).whileVisible().map { rows -> rows.map { it.toEvent() } }

    fun stats(range: Range): Flow<RangeStats> = dao.stats(start(range)).whileVisible()

    /** Event counts per bucket across [range], oldest first, zero-filled. */
    fun activity(range: Range): Flow<List<Int>> {
        val since = start(range)
        val bucketMs = (System.currentTimeMillis() - since) / range.buckets + 1
        return dao.activity(since, bucketMs).whileVisible().map { list ->
            val out = IntArray(range.buckets)
            list.forEach { if (it.bucket in 0 until range.buckets) out[it.bucket.toInt()] = it.count }
            out.toList()
        }
    }

    /** Bytes moved per bucket across [range], oldest first, zero-filled. */
    fun traffic(range: Range): Flow<List<Long>> {
        val since = start(range)
        val bucketMs = (System.currentTimeMillis() - since) / range.buckets + 1
        return dao.traffic(since, bucketMs).whileVisible().map { list ->
            val out = LongArray(range.buckets)
            list.forEach { if (it.bucket in 0 until range.buckets) out[it.bucket.toInt()] = it.bytes }
            out.toList()
        }
    }

    fun apps(range: Range, screenOffOnly: Boolean = false, limit: Int = 50): Flow<List<AppTotals>> =
        dao.apps(start(range), screenOffOnly, limit).whileVisible()

    fun appDomains(uid: Int, range: Range): Flow<List<DomainCount>> = dao.appDomains(uid, start(range)).whileVisible()

    /** Every distinct domain contacted in [range] (for tracker summaries). */
    fun domains(range: Range): Flow<List<String>> = dao.domains(start(range)).whileVisible()

    /** Start of [range]: midnight today, or N days back. */
    fun start(range: Range): Long = when (range) {
        Range.Today -> Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        Range.Week -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(WEEK_DAYS)
        Range.Month -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(KEEP_DAYS)
    }

    private fun NetEvent.toRow(screenOn: Boolean) = ConnectionRow(
        cid = prefix + id, time = time, uid = uid, kind = kind, domain = domain, target = target, blocked = blocked,
        bytesUp = bytesUp, bytesDown = bytesDown, latencyMs = latencyMs, failed = failed, screenOn = screenOn,
    )

    private fun ConnectionRow.toEvent() = NetEvent(
        id = cid, time = time, uid = uid, kind = kind, domain = domain, target = target, blocked = blocked,
        bytesUp = bytesUp, bytesDown = bytesDown, latencyMs = latencyMs, failed = failed,
    )

    private const val FLUSH_MS = 1_500L
    private const val FLUSH_SCREEN_OFF_MS = 30_000L
    private const val FLUSH_IDLE_MS = 10_000L
    private const val PRUNE_EVERY_MS = 3_600_000L
    private const val KEEP_DAYS = 30L
    private const val WEEK_DAYS = 7L
    private const val KEEP_ROWS = 200_000
    private const val RECENT_ROWS = 1_000
    private const val MAP_ROWS = 5_000
}
