package dev.warden.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Runs against the real bundled assets, so it also checks tools/geo/build_geo.py output. */
class GeoTest {
    private val geo = Geo.load { File("src/main/assets/$it").inputStream() }

    @Test fun ipv4() {
        assertEquals("US", geo.lookup("8.8.8.8")?.iso)
        assertEquals("AU", geo.lookup("1.0.0.1")?.iso)
    }

    @Test fun ipv6AndNat64() {
        // DB-IP places Google's anycast v6 DNS range in Canada: country-level and anycast-blind.
        assertEquals("CA", geo.lookup("2001:4860:4860::8888")?.iso)
        assertEquals("US", geo.lookup("64:ff9b::808:808")?.iso) // NAT64 form of 8.8.8.8
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
