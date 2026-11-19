package dev.warden.settings

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import dev.warden.design.R as DesignR

/**
 * On by default (the user can switch it off in Settings): once a day, ask GitHub for Warden's latest release and post a notification
 * when it's newer than this install. Only a version check (one request to api.github.com, which sees
 * your IP); nothing is downloaded or installed. Pre-releases are skipped (GitHub's "latest" excludes them).
 */
object UpdateCheck {
    fun enabled(context: Context) = prefs(context).getBoolean(ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(ENABLED, on).apply()
        val jobs = context.getSystemService(JobScheduler::class.java)
        if (!on) {
            jobs.cancel(JOB_ID)
            return
        }
        schedule(context)
        // Check right away too, so a pending update shows now rather than up to a day later.
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch { runCatching { check(app) } }
    }

    /** Called on every app start: a periodic job doesn't survive a reboot unless re-added. */
    fun ensureScheduled(context: Context) {
        if (enabled(context) && context.getSystemService(JobScheduler::class.java).getPendingJob(JOB_ID) == null) {
            schedule(context)
        }
    }

    private fun schedule(context: Context) {
        context.getSystemService(JobScheduler::class.java).schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJob::class.java))
                .setPeriodic(DAY_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build(),
        )
    }

    /** Checks now; returns the newer tag it notified about, or null. Blocking: call off the main thread. */
    internal fun check(context: Context): String? {
        val conn = URL(LATEST).openConnection() as HttpURLConnection
        val json = try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
        val tag = json.optString("tag_name")
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        val p = prefs(context)
        if (!isNewer(tag, installed) || p.getString(NOTIFIED, null) == tag) return null
        notify(context, tag.removePrefix("v"), json.optString("html_url", PAGE))
        p.edit().putString(NOTIFIED, tag).apply() // once per version
        return tag
    }

    /** "v0.3.0" vs "0.2.1": compares the numbers, so 0.10 is newer than 0.9. */
    internal fun isNewer(tag: String, installed: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(tag)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun notify(context: Context, version: String, url: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(Intent.ACTION_VIEW, Uri.parse(url)), PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(DesignR.drawable.ph_bell_simple_light)
                .setContentTitle(context.getString(R.string.update_title, version))
                .setContentText(context.getString(R.string.update_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    private const val ENABLED = "enabled"
    private const val NOTIFIED = "notified_tag"
    private const val JOB_ID = 4201
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val TIMEOUT_MS = 15_000
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 2 // the VPN status notification is 1
    private const val PAGE = "https://github.com/sammyZi/warden/releases/latest"
    private const val LATEST = "https://api.github.com/repos/sammyZi/warden/releases/latest"
}

/** The daily job. Network errors just wait for the next day. */
class UpdateJob : JobService() {
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = CoroutineScope(Dispatchers.IO).launch {
            runCatching { UpdateCheck.check(applicationContext) }.onFailure { Log.w("WardenUpdate", "check failed", it) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return false
    }
}
