package dev.warden

import android.app.Application
import android.os.Build
import dev.warden.data.CrashLog
import dev.warden.data.History
import dev.warden.rules.Rules
import dev.warden.settings.UpdateCheck
import java.io.File

/** Process-wide startup: rules and the history writer run whether the UI or only the VPN is alive. */
class WardenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Built-in Tor runs in its own ":tor" process; it needs none of this (and two history writers would clash).
        if (processName().endsWith(":tor")) return
        CrashLog.install(this)
        Rules.init(this)
        History.init(this)
        UpdateCheck.ensureScheduled(this)
    }

    private fun processName(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        getProcessName()
    } else {
        runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrDefault("")
    }
}
