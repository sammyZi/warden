package dev.warden.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {
    @Test fun cellsAreQuotedOnlyWhenNeeded() {
        assertEquals("example.com", History.csvCell("example.com"))
        assertEquals("\"Maps, Google\"", History.csvCell("Maps, Google"))
        assertEquals("\"say \"\"hi\"\"\"", History.csvCell("say \"hi\""))
    }

    @Test fun formulasAreDefused() {
        assertEquals("'=HYPERLINK(1)", History.csvCell("=HYPERLINK(1)"))
        assertEquals("'+cmd", History.csvCell("+cmd"))
        assertEquals("-1", History.csvCell("-1")) // the unknown-owner uid stays a number
    }
}
