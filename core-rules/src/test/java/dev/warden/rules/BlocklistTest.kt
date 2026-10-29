package dev.warden.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistTest {

    @Test fun parsesEveryFormat() {
        assertEquals("ads.example.com", BlocklistParser.domainOf("0.0.0.0 ads.example.com"))
        assertEquals("t.co.uk", BlocklistParser.domainOf("127.0.0.1\tT.co.uk  # tracker"))
        assertEquals("doubleclick.net", BlocklistParser.domainOf("||doubleclick.net^"))
        assertEquals("metrics.app", BlocklistParser.domainOf("*.metrics.app"))
        assertEquals("plain.io", BlocklistParser.domainOf("plain.io"))
    }

    @Test fun skipsCommentsHostsBoilerplateAndCosmeticRules() {
        listOf(
            "# comment", "! adblock comment", "[Adblock Plus 2.0]", "", "127.0.0.1 localhost",
            "127.0.0.1 localhost.localdomain", "@@||allowed.com^", "||ads.com^\$third-party", "||x.com/path^",
            "192.168.1.1 router.lan.home", "not a domain", "nodots",
        ).forEach { assertNull(it, BlocklistParser.domainOf(it)) }
    }

    @Test fun matchesSubdomainsButNotLookalikes() {
        val set = DomainSet(setOf("ads.com", "tracker.net"))
        assertTrue(set.matches("ads.com"))
        assertTrue(set.matches("eu.cdn.ads.com"))
        assertFalse(set.matches("notads.com"))
        assertFalse(set.matches("com"))
        assertFalse(set.matches("tracker.network"))
    }

    @Test fun countsParsedLines() {
        val into = HashSet<String>()
        val n = BlocklistParser.parse(sequenceOf("# hdr", "0.0.0.0 a.com", "b.com", "b.com"), into)
        assertEquals(3, n)
        assertEquals(setOf("a.com", "b.com"), into)
    }
}
