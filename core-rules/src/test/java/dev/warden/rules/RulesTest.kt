package dev.warden.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RulesTest {
    private val rules = mapOf(
        "10:ads.example.com" to Verdict.Allow,
        "*:example.com" to Verdict.Block,
    )

    @Test fun perAppRuleBeatsGlobalAndMatchesSubdomains() {
        assertEquals(Verdict.Allow, Rules.lookup(rules, "10:", "eu.ads.example.com"))
        assertNull(Rules.lookup(rules, "11:", "eu.ads.example.com"))
        assertEquals(Verdict.Block, Rules.lookup(rules, "*:", "eu.ads.example.com"))
    }

    @Test fun noRuleMeansNoVerdict() {
        assertNull(Rules.lookup(rules, "*:", "example.org"))
        assertNull(Rules.lookup(rules, "*:", "notexample.com"))
    }
}
