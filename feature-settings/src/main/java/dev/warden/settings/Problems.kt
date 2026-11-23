package dev.warden.settings

import android.content.Context
import dev.warden.rules.InvalidInput
import dev.warden.rules.Problem

/** A translated reason for input the user can fix; anything else falls back to [fallback]. */
internal fun problemText(context: Context, e: Throwable, fallback: String): String {
    val p = e as? InvalidInput ?: return fallback
    return when (p.problem) {
        Problem.NotWireGuard -> context.getString(R.string.err_not_wireguard)
        Problem.NoPeer -> context.getString(R.string.err_no_peer)
        Problem.NoAddress -> context.getString(R.string.err_no_address)
        Problem.BadKey -> context.getString(R.string.err_bad_key, p.detail)
        Problem.NoEndpoint -> context.getString(R.string.err_no_endpoint)
        Problem.NoPort -> context.getString(R.string.err_no_port)
        Problem.Ipv6NeedsBrackets -> context.getString(R.string.err_ipv6)
        Problem.NotHttps -> context.getString(R.string.err_not_https)
        Problem.AlreadyAdded -> context.getString(R.string.err_already_added)
    }
}
