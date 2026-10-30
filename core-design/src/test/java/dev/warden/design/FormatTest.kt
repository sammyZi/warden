package dev.warden.design

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatTest {
    init {
        Locale.setDefault(Locale.US)
    }

    @Test fun bytesUseRealUnits() {
        assertEquals("812 B", formatBytes(812))
        assertEquals("4 KB", formatBytes(4096))
        assertEquals("1.7 GB", formatBytes(1_811_000_000))
        assertEquals("340 MB", formatBytes(340L * 1024 * 1024))
        assertEquals("2.5 TB", formatBytes((2.5 * 1024 * 1024 * 1024 * 1024).toLong()))
    }

    @Test fun countsFitATile() {
        assertEquals("950", formatCount(950))
        assertEquals("1.1K", formatCount(1_126))
        assertEquals("12K", formatCount(12_400))
        assertEquals("3.4M", formatCount(3_400_000))
    }
}
