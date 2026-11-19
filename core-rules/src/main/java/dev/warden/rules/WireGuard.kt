package dev.warden.rules

import java.util.Base64

/**
 * A WireGuard config, as exported by providers (Mullvad, Proton, your own server): the wg-quick
 * `.conf` format. [userspace] is the same config in the key=value form firestack takes, with hex keys
 * and the address/dns/mtu lines it requires (format as in Rethink's WgInterface/Peer, Apache-2.0).
 * ponytail: plain WireGuard only; AmneziaWG's extra keys (Jc, S1, H1…) are ignored.
 */
class WireGuard private constructor(val userspace: String, val endpoint: String) {
    companion object {
        fun parse(conf: String): WireGuard {
            val iface = mutableMapOf<String, String>()
            val peers = mutableListOf<MutableMap<String, String>>()
            var section: MutableMap<String, String>? = null
            conf.lineSequence().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.forEach { line ->
                when (line.lowercase()) {
                    "[interface]" -> section = iface
                    "[peer]" -> section = mutableMapOf<String, String>().also(peers::add)
                    else -> {
                        val key = line.substringBefore('=', "").trim().lowercase()
                        val target = section
                        require(key.isNotEmpty() && target != null) { "Not a WireGuard config" }
                        target[key] = line.substringAfter('=').trim()
                    }
                }
            }
            require(peers.isNotEmpty()) { "No [Peer] section" }
            val out = StringBuilder()
            out.line("private_key", hexKey(iface["privatekey"], "PrivateKey"))
            out.line("address", list(iface["address"]).ifEmpty { throw IllegalArgumentException("Missing Address") })
            out.line("dns", list(iface["dns"]).ifEmpty { DEFAULT_DNS })
            out.line("mtu", iface["mtu"]?.toIntOrNull()?.toString() ?: "-1") // -1: firestack picks
            out.line("replace_peers", "true")
            peers.forEach { p ->
                out.line("public_key", hexKey(p["publickey"], "PublicKey")) // starts a new peer: keep first
                p["presharedkey"]?.let { out.line("preshared_key", hexKey(it, "PresharedKey")) }
                list(p["allowedips"]).ifEmpty { "0.0.0.0/0,::/0" }.split(',').forEach { out.line("allowed_ip", it) }
                p["endpoint"]?.let { out.line("endpoint", it) }
                p["persistentkeepalive"]?.toIntOrNull()?.let { out.line("persistent_keepalive_interval", it.toString()) }
            }
            val endpoint = peers.firstNotNullOfOrNull { it["endpoint"] }
            require(endpoint != null) { "No Endpoint in [Peer]" }
            require(endpoint.substringAfterLast(':', "").toIntOrNull() in 1..MAX_PORT) {
                "Endpoint needs a port, like vpn.example.com:51820"
            }
            return WireGuard(out.toString(), endpoint.substringBeforeLast(':').trim('[', ']'))
        }

        /**
         * A wg-quick config from the fields a provider's web page shows, for people without a file.
         * Blank optional fields are left out; Allowed IPs default to everything (a full tunnel).
         */
        @Suppress("LongParameterList")
        fun conf(
            privateKey: String,
            address: String,
            dns: String,
            publicKey: String,
            endpoint: String,
            presharedKey: String = "",
        ): String = buildString {
            append("[Interface]\nPrivateKey = ").append(privateKey.trim()).append('\n')
            append("Address = ").append(address.trim()).append('\n')
            if (dns.isNotBlank()) append("DNS = ").append(dns.trim()).append('\n')
            append("[Peer]\nPublicKey = ").append(publicKey.trim()).append('\n')
            if (presharedKey.isNotBlank()) append("PresharedKey = ").append(presharedKey.trim()).append('\n')
            append("AllowedIPs = 0.0.0.0/0, ::/0\n")
            append("Endpoint = ").append(endpoint.trim()).append('\n')
            append("PersistentKeepalive = 25\n") // keeps mobile networks' NAT from dropping an idle tunnel
        }

        private fun StringBuilder.line(key: String, value: String) = append(key).append('=').append(value).append('\n')

        private fun list(v: String?) = v.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")

        private fun hexKey(b64: String?, name: String): String {
            val bytes = runCatching { Base64.getDecoder().decode(b64.orEmpty().trim()) }.getOrNull()
            require(bytes != null && bytes.size == KEY_BYTES) { "Missing or invalid $name" }
            return bytes.joinToString("") { "%02x".format(it) }
        }

        private const val KEY_BYTES = 32
        private const val MAX_PORT = 65535
        private const val DEFAULT_DNS = "1.1.1.1"
    }
}
