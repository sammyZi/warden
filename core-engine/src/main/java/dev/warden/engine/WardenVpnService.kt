package dev.warden.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.os.Process
import android.util.Log
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.DNSOpts
import com.celzero.firestack.backend.DNSSummary
import com.celzero.firestack.backend.DomainOpts
import com.celzero.firestack.backend.ServerSummary
import com.celzero.firestack.backend.Tab
import com.celzero.firestack.intra.Bridge
import com.celzero.firestack.intra.FlowSummary
import com.celzero.firestack.intra.Intra
import com.celzero.firestack.intra.Mark
import com.celzero.firestack.intra.PreMark
import com.celzero.firestack.intra.Tunnel
import com.celzero.firestack.settings.Settings
import dev.warden.rules.AppRules
import dev.warden.rules.Dns
import dev.warden.rules.Rules
import dev.warden.rules.Tor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

/**
 * Local VpnService driving firestack. firestack owns the TUN fd, the TCP/IP stack and DNS;
 * this class answers its per-flow and per-query callbacks (the [Bridge]).
 * Integration modelled on Rethink's BraveVPNService/GoVpnAdapter (Apache-2.0), not copied.
 */
class WardenVpnService : VpnService(), Bridge {

    @Volatile private var tunnel: Tunnel? = null
    private var counter: Job? = null
    private var resets: Job? = null
    private var restarts: Job? = null

    @Volatile private var dnsTransport = Backend.Default

    @Volatile private var systemTransport = Backend.Default

    @Volatile private var dohActive = false

    // Tor via Orbot's SOCKS proxy: set up only while at least one app is routed through it.
    @Volatile private var torReady = false

    @Volatile private var torDns: String? = null

    // IPv6 on the real network, as the tunnel was built and as last seen. A change rebuilds the tunnel.
    @Volatile private var tunnelHas6 = false

    @Volatile private var underlying6: Boolean? = null

    @Volatile private var cellular = false // per-app rules differ for Wi-Fi and mobile data
    private val ids = AtomicLong()
    private val mainHandler by lazy { Handler(mainLooper) }
    private val cm by lazy { getSystemService(ConnectivityManager::class.java) }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            // Some devices report our own tunnel here; its DNS is us, so taking it would loop.
            if (cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return
            useSystemDns(lp)
            // Moving between an IPv4-only and a dual-stack network: rebuild so v6 is routed (or dropped) to match.
            val has6 = hasIpv6(lp)
            underlying6 = has6
            if (tunnel != null && has6 != tunnelHas6) {
                Log.i(TAG, "ipv6 ${if (has6) "appeared" else "went away"}, restarting tunnel")
                mainHandler.post { reconnect() }
            }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
            val nowCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            if (nowCellular != cellular) {
                cellular = nowCellular
                // Wi-Fi vs mobile rules flip with the network: re-check apps that have one.
                val ruled = AppRules.rules.value.keys.joinToString(",")
                if (ruled.isNotEmpty()) runCatching { tunnel?.closeConns(ruled) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Rules.init(this) // always-on VPN can start us before any activity
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        if (tunnel == null) {
            // Foreground first: if connect() fails we can still stop cleanly instead of
            // tripping ForegroundServiceDidNotStartInTimeException.
            goForeground()
            try {
                connect()
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) { // firestack throws plain Exception
                Log.e(TAG, "tunnel start failed", e)
                shutdown()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun connect() {
        val lp = cm.getLinkProperties(cm.activeNetwork)
        // Only claim IPv6 when the real network has it; then route all global unicast v6 so none leaks.
        // On a rebuild the active network can briefly be our own VPN, so prefer what the callback last saw.
        cellular = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val has6 = underlying6 ?: hasIpv6(lp)
        tunnelHas6 = has6

        val builder = Builder()
            .setSession(getString(R.string.engine_session))
            .setMtu(MTU)
            .addAddress(GATEWAY4, 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(DNS4)
            .addDisallowedApplication(packageName)
        // Apps the user chose to skip; one that has since been uninstalled is simply ignored.
        AppRules.excluded.value.forEach { runCatching { builder.addDisallowedApplication(it) } }
        if (has6) {
            builder.addAddress(GATEWAY6, 120)
                .addRoute("2000::", 3)
                .addRoute("64:ff9b::", 96)
                .addDnsServer(DNS6)
        }
        val pfd = builder.establish() ?: error("VPN consent missing")

        // firestack dups the fd and closes its copy on disconnect; we close ours right away (as Rethink
        // does). Handing over the original instead leaked it, so the VPN (key icon) outlived Stop.
        Settings.dupTunFd(true)
        tunnel = pfd.use {
            Intra.connect(
                it.fd.toLong(), MTU.toLong(), MTU.toLong(),
                if (has6) "$GATEWAY4/24,$GATEWAY6/120" else "$GATEWAY4/24",
                "$DNS4:53,[$DNS6]:53",
                Intra.newBuiltinDefaultDNS(),
                this,
            )
        }
        useSystemDns(lp)
        useEncryptedDns()
        useTor()
        cm.registerDefaultNetworkCallback(networkCallback)
        Engine.setRunning(true)
        resets = CoroutineScope(Dispatchers.Default).launch {
            Rules.resets.collect { uids ->
                runCatching { tunnel?.closeConns(uids) }.onFailure { Log.w(TAG, "closeConns($uids)", it) }
            }
        }
        restarts = CoroutineScope(Dispatchers.Default).launch {
            Rules.restarts.collect { mainHandler.post { reconnect() } }
        }
        // The notification's blocked count: redrawn at most every 10s, and not at all while the screen is
        // off (nobody sees it then; each redraw costs System UI work).
        Engine.blocked.set(0)
        counter = CoroutineScope(Dispatchers.Default).launch {
            val power = getSystemService(PowerManager::class.java)
            var shown = 0
            while (true) {
                delay(NOTIFY_EVERY_MS)
                val now = Engine.blocked.get()
                if (now != shown && power.isInteractive) {
                    shown = now
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(now))
                }
            }
        }
    }

    private fun goForeground() {
        val n = notification(0)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    /** Forward allowed queries to the underlying network's resolvers, like the phone would without us. */
    private fun useSystemDns(lp: LinkProperties?) {
        val t = tunnel ?: return
        val csv = lp?.dnsServers.orEmpty().mapNotNull { it.hostAddress }.joinToString(",")
        Log.i(TAG, "upstream dns: ${csv.ifEmpty { "none, using builtin default" }}")
        systemTransport = if (csv.isNotEmpty() && runCatching { Intra.setSystemDNS(t, csv) }.isSuccess) {
            Backend.System
        } else {
            Backend.Default
        }
        dnsTransport = if (dohActive) Backend.Preferred else systemTransport
    }

    /** If Settings picked an encrypted upstream, send allowed lookups there over DNS-over-HTTPS instead. */
    private fun useEncryptedDns() {
        val t = tunnel ?: return
        val config = Dns.config.value
        val url = config.dohUrl
        dohActive = url != null && runCatching {
            Intra.addDoHTransport(t, Backend.Preferred, url, config.upstream.bootstrap)
        }.onFailure { Log.w(TAG, "doh upstream $url", it) }.isSuccess
        dnsTransport = if (dohActive) Backend.Preferred else systemTransport
        Log.i(TAG, "dns via ${if (dohActive) url else "phone"}")
    }

    /**
     * Point firestack at Orbot's local SOCKS port for apps routed through Tor, and resolve their lookups
     * through the same proxy so the names don't leak to the regular resolver.
     */
    private fun useTor() {
        torReady = false
        torDns = null
        if (Tor.apps.value.isEmpty()) return
        val t = tunnel ?: return
        val proxy = runCatching { t.proxies.addProxy(Backend.OrbotS5, "socks5://127.0.0.1:${Tor.socksPort}") }
            .onFailure { Log.w(TAG, "tor proxy", it) }.getOrNull() ?: return
        torReady = true
        // DNS over the proxy; firestack registers it under the proxy's id. Without it, Tor apps' lookups
        // fall back to the encrypted or system resolver (the connections themselves still go via Tor).
        torDns = runCatching { Intra.addProxyDNS(t, proxy) }.map { proxy.id() }
            .onFailure { Log.w(TAG, "tor dns", it) }.getOrNull()
    }

    /** Rebuild the tunnel in place, e.g. after the excluded-apps list changed. */
    private fun reconnect() {
        if (tunnel == null) return
        teardown()
        try {
            connect()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) { // firestack throws plain Exception
            Log.e(TAG, "tunnel restart failed", e)
            shutdown()
        }
    }

    private fun teardown() {
        listOfNotNull(counter, resets, restarts).forEach { it.cancel() }
        counter = null
        resets = null
        restarts = null
        runCatching { cm.unregisterNetworkCallback(networkCallback) }
        tunnel?.disconnect()
        tunnel = null
    }

    private fun shutdown() {
        teardown()
        Engine.setRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() = shutdown() // another VPN took over, or the user revoked consent

    override fun onDestroy() {
        if (tunnel != null) shutdown()
        super.onDestroy()
    }

    private fun notification(blocked: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.engine_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, javaClass).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle(getString(R.string.engine_notif_title))
            .setContentText(
                if (blocked == 0) getString(R.string.engine_notif_text)
                else resources.getQuantityString(R.plurals.engine_notif_blocked, blocked, blocked),
            )
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.engine_notif_stop), stop).build())
            .build()
    }

    // ---- firestack callbacks: run on Go threads, keep them short and never block ----

    override fun protect(who: String, fd: Long) {
        protect(fd.toInt())
    }

    // ponytail: protect only; Rethink also binds to a specific underlying network (multi-network routing).
    override fun bind4(who: String, addrport: String, fd: Long) {
        protect(fd.toInt())
    }

    override fun bind6(who: String, addrport: String, fd: Long) {
        protect(fd.toInt())
    }

    /** Called when firestack doesn't know the owner UID. Below Q it reads /proc/net itself. */
    override fun preflow(protocol: Int, uid: Int, src: String, dst: String): PreMark {
        val owner = if (uid >= 0 || Build.VERSION.SDK_INT < 29) {
            uid
        } else {
            runCatching { cm.getConnectionOwnerUid(protocol, socketAddress(src), socketAddress(dst)) }
                .getOrDefault(UNKNOWN_UID)
        }
        return PreMark().apply {
            this.uid = owner.toString()
            isUidSelf = owner == Process.myUid()
        }
    }

    override fun flow(
        protocol: Int,
        uid: Int,
        src: String,
        dst: String,
        origdsts: String,
        domains: String,
        probableDomains: String,
        blocklists: String,
        dstIsAlg: Boolean,
    ): Mark {
        val cid = ids.incrementAndGet().toString()
        val toOurDns = host(dst) == DNS4 || host(dst) == DNS6
        val names = (domains.split(',') + probableDomains.split(',')).map(::normalize).filter { it.isNotEmpty() }
        // An app talking to its own encrypted resolver would skip every rule below, so that's refused too.
        val bypass = !toOurDns && uid != Process.myUid() && Dns.config.value.stopBypass &&
            Dns.isBypass(names, host(dst), port(dst))
        // Tor carries TCP only, so a Tor app's UDP (QUIC, mostly) is refused; it retries over TCP.
        val tor = !toOurDns && Tor.uses(uid)
        val blocked = !toOurDns && (bypass || Rules.blocks(uid, names, cellular) || (tor && protocol == UDP))
        if (!toOurDns) {
            Engine.record(
                NetEvent(cid, System.currentTimeMillis(), uid, protocolName(protocol), names.firstOrNull(), dst, blocked),
            )
        }
        return Mark().apply {
            pidcsv = route(toOurDns, blocked, tor)
            this.cid = cid
            this.uid = uid.toString()
        }
    }

    /** Base = handled inside the tunnel (our fake DNS), Exit = dial out directly, Block = refuse. */
    private fun route(toOurDns: Boolean, blocked: Boolean, tor: Boolean): String = when {
        toOurDns -> Backend.Base
        blocked -> Backend.Block
        // Fail closed: with Tor chosen but not set up, the app gets nothing rather than going direct.
        tor -> if (torReady) Backend.OrbotS5 else Backend.Block
        else -> Backend.Exit
    }

    override fun inflow(protocol: Int, uid: Int, src: String, dst: String): Mark = Mark().apply {
        pidcsv = Backend.Exit
        cid = ids.incrementAndGet().toString()
        this.uid = uid.toString()
    }

    override fun flowing(m: Mark?) = Unit

    override fun postflow(s: FlowSummary?) {
        if (s == null) return
        // rtt is in ms; a non-empty msg means the connection ended with an error.
        Engine.closed(Closed(s.id, up = s.tx, down = s.rx, rttMs = s.rtt.toInt().takeIf { it > 0 }, failed = s.msg.isNotBlank()))
    }

    override fun onQuery(who: String, uid: String, domain: String, qtyp: Long): DNSOpts = DNSOpts().apply {
        this.uid = uid
        ipcsv = ""
        // BlockAll answers 0.0.0.0 / ::, which apps give up on at once (checked on device), unlike NXDOMAIN.
        tidcsv = when {
            queryBlocked(uid, normalize(domain)) -> Backend.BlockAll
            Tor.uses(uid.toIntOrNull() ?: UNKNOWN_UID) -> torDns ?: dnsTransport
            else -> dnsTransport
        }
    }

    /** Rules, plus looking up a public encrypted-DNS host (the first step of going around Warden). */
    private fun queryBlocked(uid: String, name: String): Boolean {
        val id = uid.toIntOrNull() ?: UNKNOWN_UID
        if (Rules.blocks(id, listOf(name), cellular)) return true
        return id != Process.myUid() && Dns.config.value.stopBypass && Dns.isResolverHost(name)
    }

    override fun onResponse(s: DNSSummary?) {
        val name = normalize(s?.qName ?: return)
        if (name.isEmpty()) return
        Engine.record(
            NetEvent(
                id = "d" + ids.incrementAndGet(),
                time = System.currentTimeMillis(),
                uid = s.uid.toIntOrNull() ?: UNKNOWN_UID,
                kind = "dns",
                domain = name,
                target = s.rData.ifEmpty { "no answer" },
                blocked = queryBlocked(s.uid, name),
                latencyMs = (s.latency * MS_PER_S).toInt().takeIf { it > 0 },
                failed = s.rData.isEmpty(),
            ),
        )
    }

    override fun onPrequery(who: String, uid: String, domain: String, qtyp: Long): DomainOpts? = null

    override fun onUpstreamAnswer(who: String, smm: DNSSummary?, forPref: DNSOpts?, unmodifiedipcsv: String) = forPref

    override fun svcRoute(sid: String, pid: String, network: String, sipport: String, dipport: String) = Tab()

    override fun onSvcComplete(p0: ServerSummary?) = Unit
    override fun onDNSAdded(id: String) = Unit
    override fun onDNSRemoved(id: String) = Unit
    override fun onDNSStopped() = Unit
    override fun onProxiesStopped() = Unit
    override fun onProxyAdded(id: String, handle: String) = Unit
    override fun onProxyRemoved(id: String, handle: String) = Unit
    override fun onProxyStopped(id: String, handle: String) = Unit
    override fun onProxyUpdated(id: String, handle: String) = Unit

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, WardenVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WardenVpnService::class.java).setAction(ACTION_STOP))
        }

        private const val TAG = "WardenVpn"
        private const val ACTION_STOP = "dev.warden.engine.STOP"
        private const val CHANNEL = "status"
        private const val NOTIFICATION_ID = 1
        private const val MTU = 1500
        private const val MS_PER_S = 1000
        private const val NOTIFY_EVERY_MS = 10_000L
        private const val UDP = 17
        private const val UNKNOWN_UID = -1

        // Private ranges, same shape as Rethink's defaults: .1 is us, .3 is the fake DNS server.
        private const val GATEWAY4 = "10.111.222.1"
        private const val DNS4 = "10.111.222.3"
        private const val GATEWAY6 = "fd66:f83a:c650::1"
        private const val DNS6 = "fd66:f83a:c650::3"

        private fun normalize(name: String) = name.trim().trimEnd('.').lowercase()

        private fun host(ipPort: String) = ipPort.substringBeforeLast(':').trim('[', ']')

        private fun port(ipPort: String) = ipPort.substringAfterLast(':').toIntOrNull() ?: 0

        private fun hasIpv6(lp: LinkProperties?) =
            lp?.routes.orEmpty().any { it.isDefaultRoute && it.destination.address is Inet6Address }

        private fun socketAddress(ipPort: String) =
            InetSocketAddress(InetAddress.getByName(host(ipPort)), ipPort.substringAfterLast(':').toInt())

        private fun protocolName(p: Int) = when (p) {
            6 -> "tcp"
            17 -> "udp"
            else -> "ip$p"
        }
    }
}
