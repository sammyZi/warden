package dev.warden.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Runs against the real bundled list, so a malformed line fails the build. */
class TrackersTest {
    init {
        Trackers.load(File("src/main/assets/trackers.tsv").inputStream().use(Trackers::parse))
    }

    @Test fun knownTrackersAndSubdomains() {
        assertEquals(Tracker("Google", "Ads"), Trackers.lookup("securepubads.g.doubleclick.net"))
        assertEquals("AppsFlyer", Trackers.lookup("launches.appsflyersdk.com")?.company)
        assertNull(Trackers.lookup("api.weather.com"))
        assertNull(Trackers.lookup("notdoubleclick.net"))
    }

    @Test fun summaryCountsTrackersAndCompanies() {
        val s =
            summarizeTrackers(listOf("app-measurement.com", "doubleclick.net", "graph.facebook.com", "example.org", null))
        assertEquals(3, s.trackers)
        assertEquals(2, s.companies)
        assertEquals("Google", s.byCompany.first().first)
        assertEquals(listOf("Analytics", "Ads"), s.byCompany.first().second)
    }
}
