package ar.com.midepartamento.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FinanceTest {
    @Test fun moneyAcceptsArgentineDecimalsWithoutRoundingInput() {
        assertEquals(125050L, parseMoney("1250,50"))
        assertEquals(46000000L, parseMoney("460000"))
        assertNull(parseMoney("1.250,50")); assertNull(parseMoney("-1"))
        assertNull(parseMoney("0")); assertNull(parseMoney("1.001"))
        assertNull(parseMoney("NaN")); assertNull(parseMoney("100000000000000000000000"))
    }
    @Test fun conversionUsesCentsAndRoundsHalfUp() {
        assertEquals(135000000L, toArs(100000, "USD", 135000))
        assertEquals(2L, toArs(1, "USD", 150))
        assertEquals(99L, toArs(99, "ARS", 0))
    }
    @Test fun recurrenceRespectsStartMonthAndYearBoundary() {
        assertTrue(occurs("2026-11", "2027-01", 2))
        assertFalse(occurs("2026-11", "2026-12", 2))
        assertFalse(occurs("2026-11", "2026-10", 1))
        assertTrue(occurs("2026-11", "2027-11", 12))
    }
    @Test fun dueTodayIsPendingAndPaymentWinsOverExpiry() {
        val today = LocalDate.of(2026, 11, 1)
        assertEquals("Pendiente", billStatus(null, null, today))
        assertEquals("Pendiente", billStatus(null, "2026-11-01", today))
        assertEquals("Vencida", billStatus(null, "2026-10-31", today))
        assertEquals("Pagada", billStatus("2026-10-30", "2026-10-31", today))
    }
    @Test fun payingBillNeverDoublesForecastAndUsesRealAmount() {
        val before = totals(listOf(100000L to "ARS"), listOf(Triple(50000L, null, false)), listOf(5000L), 0)
        val after = totals(listOf(100000L to "ARS"), listOf(Triple(50000L, 45000L, true)), listOf(5000L), 0)
        assertEquals(55000L, before.forecast); assertEquals(5000L, before.actual)
        assertEquals(50000L, after.forecast); assertEquals(50000L, after.actual)
        assertEquals(50000L, after.available)
    }
    @Test fun pendingRealBillIsIncludedOnlyInForecast() {
        val t = totals(emptyList(), listOf(Triple(50000L, 45000L, false)), emptyList(), 0)
        assertEquals(45000L, t.forecast); assertEquals(0L, t.actual)
    }
    @Test fun missingRateKeepsArsIncomeAndExcludesUsd() {
        assertEquals(5000L, totals(listOf(100000L to "USD", 5000L to "ARS"), emptyList(), emptyList(), 0).income)
    }
}
