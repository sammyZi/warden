package dev.warden.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Runs against the real bundled assets, so it also checks tools/geo/build_geo.py output. */
class GeoTest {
    private val geo = Geo.load { File("src/main/assets/$it").inputStream() }

    @Test fun ipv4() {
        assertEquals("AU", geo.lookup("1.0.4.1")?.iso)
        assertEquals("IN", geo.lookup("117.200.0.1")?.iso)
    }

    @Test fun anycastNetworksAreEdgesNotCountries() {
        assertEquals("~CF", geo.lookup("104.16.0.1")?.iso) // Cloudflare (Supabase, many APIs)
        assertEquals("~CF", geo.lookup("172.64.0.1")?.iso) // 1.1.1.1 isn't in Cloudflare's CDN list
        assertEquals("~GO", geo.lookup("8.8.8.8")?.iso) // Google
        assertEquals("~CF", geo.lookup("2606:4700::1")?.iso) // Cloudflare v6
        assertEquals("~CF", geo.lookup("64:ff9b::6810:1")?.iso) // NAT64 of 104.16.0.1
    }

    @Test fun ipv6AndNat64() {
        // DB-IP alone puts Google's anycast v6 DNS in Canada; the edge list catches it first.
        assertEquals("~GO", geo.lookup("2001:4860:4860::8888")?.iso)
        assertEquals("IN", geo.lookup("2409:4000::1")?.iso) // Jio, a plain country range
        assertEquals("~GO", geo.lookup("64:ff9b::808:808")?.iso) // NAT64 form of 8.8.8.8, a Google edge
    }

    @Test fun privateAndHostnamesAreUnknown() {
        assertNull(geo.lookup("10.0.0.1"))
        assertNull(geo.lookup("dead.beef")) // must not trigger a DNS lookup
    }

    @Test fun landDotsAreUnitVectors() {
        assertEquals(0, geo.land.size % 3)
        val (x, y, z) = Triple(geo.land[0], geo.land[1], geo.land[2])
        assertEquals(1.0, Math.sqrt((x * x + y * y + z * z).toDouble()), 1e-4)
    }
}
