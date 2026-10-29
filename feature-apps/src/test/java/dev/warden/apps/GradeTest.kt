package dev.warden.apps

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the documented formula (docs/report-card.md). */
class GradeTest {
    @Test fun cleanAppIsA() = assertEquals("A", grade(0, 0f, 0f).letter)

    @Test fun oneCompanyLightUseIsB() = assertEquals("B", grade(1, 0.1f, 0f).letter) // 100 - 9 - 3 = 88

    @Test fun manyCompaniesIsF() = assertEquals("F", grade(8, 0.5f, 0.5f).letter) // 100 - 54 - 15 - 7.5 = 23.5

    @Test fun companiesCapAtSix() = assertEquals(grade(6, 0f, 0f).score, grade(20, 0f, 0f).score)
}
