package dev.warden.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import dev.warden.engine.Closed
import kotlinx.coroutines.flow.Flow

/** One DNS answer or connection. Metadata only, never payload. */
@Entity(tableName = "connections", indices = [Index("time"), Index("uid"), Index(value = ["cid"], unique = true)])
data class ConnectionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Engine id, prefixed with the process start so ids stay unique across restarts. */
    val cid: String,
    val time: Long,
    val uid: Int,
    val kind: String,
    val domain: String?,
    val target: String,
    val blocked: Boolean,
    val bytesUp: Long = 0,
    val bytesDown: Long = 0,
    val latencyMs: Int? = null,
    val failed: Boolean = false,
    /** Whether the screen was on when this happened (approximate to the 1.5s batch). */
    val screenOn: Boolean = true,
)

data class RangeStats(val blocked: Int, val connections: Int, val apps: Int, val domains: Int, val bytes: Long)

data class Bucket(val bucket: Long, val count: Int)

data class ByteBucket(val bucket: Long, val bytes: Long)

data class AppTotals(
    val uid: Int,
    val connections: Int,
    val blocked: Int,
    val domains: Int,
    val bytesUp: Long,
    val bytesDown: Long,
)

data class DomainCount(val domain: String, val count: Int, val blocked: Int)

data class AppQuality(
    val uid: Int,
    val dnsMs: Double?,
    val rttMs: Double?,
    val lookups: Int,
    val failedLookups: Int,
    val connections: Int,
    val failedConnections: Int,
)

@Dao
interface ConnectionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rows: List<ConnectionRow>)

    @Query(
        "UPDATE connections SET bytesUp = :up, bytesDown = :down, latencyMs = COALESCE(:rtt, latencyMs), failed = :failed WHERE cid = :cid",
    )
    suspend fun close(cid: String, up: Long, down: Long, rtt: Int?, failed: Boolean)

    @Transaction
    suspend fun write(rows: List<ConnectionRow>, closed: List<Closed>, prefix: String) {
        if (rows.isNotEmpty()) insert(rows)
        closed.forEach { close(prefix + it.id, it.up, it.down, it.rttMs, it.failed) }
    }

    @Query("SELECT * FROM connections ORDER BY time DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ConnectionRow>>

    @Query(
        """SELECT COALESCE(SUM(blocked), 0) AS blocked, COALESCE(SUM(kind != 'dns'), 0) AS connections,
           COUNT(DISTINCT uid) AS apps, COUNT(DISTINCT domain) AS domains,
           COALESCE(SUM(bytesUp + bytesDown), 0) AS bytes FROM connections WHERE time >= :since""",
    )
    fun stats(since: Long): Flow<RangeStats>

    @Query("SELECT (time - :since) / :bucketMs AS bucket, COUNT(*) AS count FROM connections WHERE time >= :since GROUP BY bucket")
    fun activity(since: Long, bucketMs: Long): Flow<List<Bucket>>

    @Query(
        """SELECT (time - :since) / :bucketMs AS bucket, SUM(bytesUp + bytesDown) AS bytes FROM connections
           WHERE time >= :since GROUP BY bucket""",
    )
    fun traffic(since: Long, bucketMs: Long): Flow<List<ByteBucket>>

    @Query(
        """SELECT uid, SUM(kind != 'dns') AS connections, SUM(blocked) AS blocked, COUNT(DISTINCT domain) AS domains,
           SUM(bytesUp) AS bytesUp, SUM(bytesDown) AS bytesDown FROM connections
           WHERE time >= :since AND (:screenOffOnly = 0 OR screenOn = 0)
           GROUP BY uid ORDER BY connections + blocked DESC LIMIT :limit""",
    )
    fun apps(since: Long, screenOffOnly: Boolean, limit: Int): Flow<List<AppTotals>>

    @Query(
        """SELECT domain, COUNT(*) AS count, SUM(blocked) AS blocked FROM connections
           WHERE uid = :uid AND time >= :since AND domain IS NOT NULL GROUP BY domain ORDER BY count DESC""",
    )
    fun appDomains(uid: Int, since: Long): Flow<List<DomainCount>>

    @Query(
        """SELECT uid,
           AVG(CASE WHEN kind = 'dns' THEN latencyMs END) AS dnsMs,
           AVG(CASE WHEN kind != 'dns' THEN latencyMs END) AS rttMs,
           SUM(kind = 'dns') AS lookups, SUM(kind = 'dns' AND failed AND NOT blocked) AS failedLookups,
           SUM(kind != 'dns') AS connections, SUM(kind != 'dns' AND failed AND NOT blocked) AS failedConnections
           FROM connections WHERE time >= :since GROUP BY uid""",
    )
    fun quality(since: Long): Flow<List<AppQuality>>

    @Query("SELECT DISTINCT domain FROM connections WHERE time >= :since AND domain IS NOT NULL")
    fun domains(since: Long): Flow<List<String>>

    @Query("DELETE FROM connections WHERE time < :before")
    suspend fun deleteBefore(before: Long)

    @Query("DELETE FROM connections WHERE id IN (SELECT id FROM connections ORDER BY time DESC LIMIT -1 OFFSET :keep)")
    suspend fun keepNewest(keep: Int)

    @Query("DELETE FROM connections")
    suspend fun clear()

    @Query("SELECT * FROM connections WHERE time >= :since AND kind != 'dns' ORDER BY time DESC LIMIT :limit")
    fun connectionsSince(since: Long, limit: Int): Flow<List<ConnectionRow>>
}

@Database(entities = [ConnectionRow::class], version = 1, exportSchema = false)
abstract class WardenDb : RoomDatabase() {
    abstract fun connections(): ConnectionDao
}
