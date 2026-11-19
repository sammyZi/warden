package dev.warden.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingTest {
    private val key = "yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=" // 32 bytes
    private val conf = """
        [Interface]
        PrivateKey = $key
        Address = 10.64.0.2/32, fc00::2/128
        DNS = 10.64.0.1
        # a comment
        [Peer]
        PublicKey = $key
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = [2001:db8::1]:51820
        PersistentKeepalive = 25
    """.trimIndent()

    @Test fun wgQuickBecomesFirestackUserspace() {
        val wg = WireGuard.parse(conf)
        val hex = "c809f3e5317e9575c9b5ed78b638b7ce530dabe85ddab614220241801ddf0669"
        assertTrue(wg.userspace.startsWith("private_key=$hex\naddress=10.64.0.2/32,fc00::2/128\ndns=10.64.0.1\nmtu=-1\n"))
        assertTrue("public_key=$hex\nallowed_ip=0.0.0.0/0\nallowed_ip=::/0\n" in wg.userspace)
        assertTrue("persistent_keepalive_interval=25\n" in wg.userspace)
        assertEquals("2001:db8::1", wg.endpoint)
    }

    @Test fun brokenConfigsSayWhy() {
        assertThrows(IllegalArgumentException::class.java) { WireGuard.parse("hello") }
        assertThrows(IllegalArgumentException::class.java) { WireGuard.parse(conf.replace(key + "\nAddress", "abc\nAddress")) }
        assertThrows(IllegalArgumentException::class.java) { WireGuard.parse(conf.substringBefore("[Peer]")) }
    }

    @Test fun typedInFieldsMakeTheSameConfig() {
        val typed = WireGuard.parse(WireGuard.conf(key, "10.64.0.2/32", "", " $key ", "198.51.100.7:51820"))
        assertTrue("allowed_ip=0.0.0.0/0\nallowed_ip=::/0\n" in typed.userspace)
        assertTrue("dns=1.1.1.1\n" in typed.userspace) // blank DNS gets a default
        assertEquals("198.51.100.7", typed.endpoint)
        assertThrows(IllegalArgumentException::class.java) {
            WireGuard.parse(WireGuard.conf(key, "10.64.0.2/32", "", key, "198.51.100.7"))
        }
    }

    @Test fun lockdownLeavesAndroidItselfOnline() {
        assertTrue(AppRules.isCore(-1))
        assertTrue(AppRules.isCore(1000))
        assertTrue(AppRules.isCore(1_001_000)) // system in a work profile
        assertFalse(AppRules.isCore(10_123))
        assertFalse(AppRules.isCore(1_010_123))
    }
}
