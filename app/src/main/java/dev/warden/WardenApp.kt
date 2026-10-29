package dev.warden

import android.app.Application
import dev.warden.data.History
import dev.warden.rules.Rules

/** Process-wide startup: rules and the history writer run whether the UI or only the VPN is alive. */
class WardenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Rules.init(this)
        History.init(this)
    }
}
