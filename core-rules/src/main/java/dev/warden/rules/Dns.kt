package dev.warden.rules

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** Where allowed lookups go. Encrypted options use DNS-over-HTTPS, so the network can't read or change them. */
enum class Upstream(val url: String?, val bootstrap: String) {
    Phone(null, ""),
    Cloudflare("https://cloudflare-dns.com/dns-query", "1.1.1.1,1.0.0.1,2606:4700:4700::1111,2606:4700:4700::1001"),
    Quad9("https://dns.quad9.net/dns-query", "9.9.9.9,149.112.112.112,2620:fe::fe,2620:fe::9"),
    Custom(null, ""),
}

/** DNS choices from Settings, read by the VPN. An upstream change rebuilds the tunnel; the bypass switch applies live. */
data class DnsConfig(
    val upstream: Upstream = Upstream.Phone,
    val customUrl: String = "",
    val stopBypass: Boolean = true,
) {
    /** The DoH URL in use, or null for the phone's own DNS. */
    val dohUrl: String? get() = if (upstream == Upstream.Custom) customUrl.takeIf { isValidDohUrl(it) } else upstream.url
}

fun isValidDohUrl(url: String): Boolean =
    url.startsWith("https://") && url.length > "https://a.b".length && ' ' !in url

object Dns {
    private val _config = MutableStateFlow(DnsConfig())
    val config: StateFlow<DnsConfig> = _config.asStateFlow()

    private val started = AtomicBoolean()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!started.compareAndSet(false, true)) return
        prefs = context.applicationContext.getSharedPreferences("dns", Context.MODE_PRIVATE)
        _config.value = DnsConfig(
            upstream = runCatching { Upstream.valueOf(prefs.getString(UPSTREAM, null)!!) }.getOrDefault(Upstream.Phone),
            customUrl = prefs.getString(CUSTOM, "").orEmpty(),
            stopBypass = prefs.getBoolean(BYPASS, true),
        )
    }

    fun setUpstream(upstream: Upstream, customUrl: String = _config.value.customUrl) {
        prefs.edit().putString(UPSTREAM, upstream.name).putString(CUSTOM, customUrl).apply()
        _config.value = _config.value.copy(upstream = upstream, customUrl = customUrl)
        Rules.restart()
    }

    fun setStopBypass(on: Boolean) {
        prefs.edit().putBoolean(BYPASS, on).apply()
        _config.value = _config.value.copy(stopBypass = on)
        Rules.reset("") // drop open connections to resolvers so the change applies now
    }

    /**
     * Is this a resolver an app could use to go around Warden's filtering? Matches plain DNS to any
     * server other than Warden's (port 53), DNS-over-TLS (853), well-known DoH hostnames (with subdomains)
     * and resolver addresses on 443. Warden's own upstream never passes through here: it's outside the tunnel.
     */
    fun isBypass(names: List<String>, ip: String, port: Int): Boolean {
        if (port == DNS_PORT || port == DOT_PORT) return true
        if (port == HTTPS_PORT && ip in RESOLVER_IPS) return true
        return names.any { isResolverHost(it) }
    }

    fun isResolverHost(name: String): Boolean {
        var d = name
        while (true) {
            if (d in RESOLVER_HOSTS) return true
            val dot = d.indexOf('.')
            if (dot < 0) return false
            d = d.substring(dot + 1)
        }
    }

    // Public DoH services. Facts about who runs what, written for Warden; not a downloaded list.
    private val RESOLVER_HOSTS = setOf(
        "dns.google", "dns.google.com", "8888.google", "cloudflare-dns.com", "one.one.one.one", "1dot1dot1dot1.cloudflare-dns.com",
        "dns.quad9.net", "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net", "doh.opendns.com", "doh.familyshield.opendns.com",
        "dns.nextdns.io", "dns.adguard.com", "dns.adguard-dns.com", "doh.cleanbrowsing.org", "doh.dns.sb", "dns.mullvad.net",
        "doh.mullvad.net", "freedns.controld.com", "dns.controld.com", "dns0.eu", "doh.libredns.gr", "dns.alidns.com", "doh.pub",
        "doh.360.cn", "dns.twnic.tw", "private.canadianshield.cira.ca", "doh.xfinity.com", "dns.switch.ch", "doh.applied-privacy.net",
        "mozilla.cloudflare-dns.com", "chrome.cloudflare-dns.com",
    )

    private val RESOLVER_IPS = setOf(
        "1.1.1.1", "1.0.0.1", "1.1.1.2", "1.0.0.2", "1.1.1.3", "1.0.0.3", "8.8.8.8", "8.8.4.4", "9.9.9.9", "149.112.112.112",
        "9.9.9.10", "9.9.9.11", "208.67.222.222", "208.67.220.220", "94.140.14.14", "94.140.15.15", "76.76.2.0", "76.76.10.0",
        "185.228.168.168", "185.228.169.168", "194.242.2.2", "45.90.28.0", "45.90.30.0", "193.110.81.0", "185.253.5.0",
        "223.5.5.5", "223.6.6.6", "2606:4700:4700::1111", "2606:4700:4700::1001", "2001:4860:4860::8888", "2001:4860:4860::8844",
        "2620:fe::fe", "2620:fe::9", "2620:119:35::35", "2620:119:53::53", "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff",
    )

    private const val DNS_PORT = 53
    private const val HTTPS_PORT = 443
    private const val DOT_PORT = 853
    private const val UPSTREAM = "upstream"
    private const val CUSTOM = "custom_url"
    private const val BYPASS = "stop_bypass"
}
