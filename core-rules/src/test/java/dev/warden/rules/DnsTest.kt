package dev.warden.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsTest {
    @Test fun bypassMatchesResolversNotOrdinarySites() {
        assertTrue(Dns.isBypass(listOf("dns.google"), "8.8.8.8", 443))
        assertTrue(Dns.isBypass(emptyList(), "1.1.1.1", 443)) // hard-coded IP, no name
        assertTrue(Dns.isBypass(emptyList(), "203.0.113.9", 853)) // any DNS-over-TLS
        assertTrue(Dns.isBypass(emptyList(), "8.8.8.8", 53)) // hard-coded plain DNS
        assertTrue(Dns.isResolverHost("chrome.cloudflare-dns.com"))
        assertFalse(Dns.isBypass(listOf("www.google.com"), "142.250.1.1", 443))
        assertFalse(Dns.isBypass(emptyList(), "1.1.1.1", 80)) // plain web on a resolver IP
        assertFalse(Dns.isResolverHost("notcloudflare-dns.com"))
    }

    @Test fun customUrlMustBeHttps() {
        assertTrue(isValidDohUrl("https://dns.example.org/dns-query"))
        assertFalse(isValidDohUrl("http://dns.example.org/dns-query"))
        assertFalse(isValidDohUrl("https://"))
        assertNull(DnsConfig(Upstream.Custom, customUrl = "nope").dohUrl)
        assertEquals(Upstream.Quad9.url, DnsConfig(Upstream.Quad9).dohUrl)
    }
}
