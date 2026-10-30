package dev.warden.data

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One app's bytes over a period, split by network. */
data class AppUsage(val uid: Int, val wifiUp: Long, val wifiDown: Long, val mobileUp: Long, val mobileDown: Long) {
    val up get() = wifiUp + mobileUp
    val down get() = wifiDown + mobileDown
    val wifi get() = wifiUp + wifiDown
    val mobile get() = mobileUp + mobileDown
}

/**
 * Android's own per-app data counters (NetworkStatsManager), the same numbers as Settings > Data usage.
 * They're kept by the system whether or not Warden is running. Reading them needs Usage access,
 * which the user grants in system settings. Nothing leaves the phone.
 */
object SystemUsage {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Bytes per app between [start] and [end]. System UIDs (below 10000) are merged into one "Android
     * system" row; removed apps, tethering and Warden itself (its tunnel overhead) are left out.
     */
    suspend fun apps(context: Context, start: Long, end: Long): List<AppUsage> = withContext(Dispatchers.IO) {
        val nsm = context.getSystemService(NetworkStatsManager::class.java)
        val sums = HashMap<Int, LongArray>()
        NETWORKS.forEachIndexed { i, type ->
            // ponytail: a null subscriber covers mobile on Android 10+; older phones may report no mobile data.
            val stats = runCatching { nsm.querySummary(type, null, start, end) }.getOrNull() ?: return@forEachIndexed
            try {
                val b = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(b)
                    if (b.uid < 0 || b.uid == Process.myUid()) continue
                    val uid = if (b.uid < Process.FIRST_APPLICATION_UID) Process.SYSTEM_UID else b.uid
                    val a = sums.getOrPut(uid) { LongArray(4) }
                    a[i * 2] += b.txBytes
                    a[i * 2 + 1] += b.rxBytes
                }
            } finally {
                stats.close()
            }
        }
        sums.map { (uid, a) -> AppUsage(uid, a[0], a[1], a[2], a[3]) }.filter { it.up + it.down > 0 }
    }

    /** Whole-device bytes in [buckets] equal slices from [start] to now, oldest first. */
    suspend fun traffic(context: Context, start: Long, buckets: Int): List<Long> = withContext(Dispatchers.IO) {
        val nsm = context.getSystemService(NetworkStatsManager::class.java)
        val step = (System.currentTimeMillis() - start) / buckets + 1
        // ponytail: the system keeps ~2h buckets for recent data, so hourly columns come out lumpy; fine for a shape.
        List(buckets) { i ->
            val from = start + i * step
            NETWORKS.sumOf { type ->
                runCatching { nsm.querySummaryForDevice(type, null, from, from + step).let { it.txBytes + it.rxBytes } }.getOrDefault(0L)
            }
        }
    }

    @Suppress("DEPRECATION") // TYPE_* is still what NetworkStatsManager takes.
    private val NETWORKS = listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)
}
