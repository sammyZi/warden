package dev.warden.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test fun comparesVersionNumbersNotText() {
        assertTrue(UpdateCheck.isNewer("v0.2.2", "0.2.1"))
        assertTrue(UpdateCheck.isNewer("v0.10.0", "0.9.3"))
        assertTrue(UpdateCheck.isNewer("v1.0", "0.9.9"))
        assertFalse(UpdateCheck.isNewer("v0.2.1", "0.2.1"))
        assertFalse(UpdateCheck.isNewer("v0.2.0", "0.2.1"))
        assertFalse(UpdateCheck.isNewer("", "0.2.1"))
    }
}
