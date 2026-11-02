package ar.com.midepartamento.domain

import ar.com.midepartamento.data.Expense
import ar.com.midepartamento.ui.Records
import ar.com.midepartamento.ui.UiState
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class PurchasesTest {
    @Test fun receiptReadsArgentinePricesDateProductsAndExplicitTotal() {
        val receipt = ReceiptParser.parse("SUPERMERCADO DEL BARRIO\nFECHA 01/11/2026\nLECHE 1.250,50\nPAN 749,50\nTOTAL A PAGAR $ 2.000,00\nEFECTIVO 5.000,00\nVUELTO 3.000,00")
        assertEquals("SUPERMERCADO DEL BARRIO", receipt.merchant)
        assertEquals("2026-11-01", receipt.date)
        assertEquals(200_000L, receipt.total)
        assertEquals(listOf(PurchaseItem("LECHE", 125_050), PurchaseItem("PAN", 74_950)), receipt.items)
    }
    @Test fun ignoresSubtotalTaxesDiscountsAndPaymentAmounts() {
        val receipt = ReceiptParser.parse("CARNICERIA LA ESQUINA\nCARNE 10.000,00\nSUBTOTAL 10.000,00\nDESCUENTO -500,00\nIVA 1.735,54\nTOTAL 9.500,00\nTARJETA 9.500,00")
        assertEquals("Carnicería", receipt.category)
        assertEquals(950_000L, receipt.total)
        assertEquals(1, receipt.items.size)
    }
    @Test fun doesNotInventTotalOrDateWhenMissingOrInvalid() {
        val receipt = ReceiptParser.parse("VERDULERIA\n31/02/2026\nTOMATE 2.000,00")
        assertEquals("Verdulería", receipt.category)
        assertNull(receipt.total); assertNull(receipt.date)
        assertEquals(200_000L, receipt.items.single().cents)
    }
    @Test fun acceptsDecimalDotAndTotalWholePesos() {
        assertEquals(250_050L, ReceiptParser.parse("LOCAL\nTOTAL 2500.50").total)
        assertEquals(250_000L, ReceiptParser.parse("LOCAL\nTOTAL $ 2500").total)
        assertEquals(123_456L, ReceiptParser.parse("LOCAL\nTOTAL $ 1,234.56").total)
    }
    @Test fun doesNotTreatTotalSavingsOrNumberOfItemsAsMoneySpent() {
        val receipt = ReceiptParser.parse("LOCAL\nTOTAL AHORRO 2.000,00\nTOTAL ARTICULOS 12\nSUBTOTAL 10.000,00")
        assertNull(receipt.total)
        assertTrue(receipt.items.isEmpty())
    }
    @Test fun monthlyFoodTotalsUsePurchaseTotalOnceAndExcludeOtherMonthsAndNonFood() {
        val expenses = listOf(
            Expense("a", "2026-11-01", "Supermercado", "", 20_000, items = listOf(PurchaseItem("Pan", 10_000), PurchaseItem("Leche", 10_000))),
            Expense("b", "2026-11-01", "Verdulería", "", 5_000),
            Expense("c", "2026-11-01", "Carnicería", "", 10_000),
            Expense("d", "2026-10-31", "Carnicería", "", 99_999),
            Expense("e", "2026-11-01", "Transporte", "", 2_000)
        )
        val state = UiState(YearMonth.of(2026, 11), Records(emptyList(), emptyList(), emptyList(), expenses, null))
        val summary = foodSummary(state.expenses.map { it.category to it.cents })
        assertEquals(35_000L, summary.total)
        assertEquals(20_000L, summary.byCategory["Supermercado"])
        assertEquals(37_000L, state.totals.actual)
        val edited = expenses.map { if (it.id == "b") it.copy(cents = 7_000) else it }.filter { it.id != "c" }
        assertEquals(27_000L, foodSummary(edited.filter { it.date.startsWith("2026-11") }.map { it.category to it.cents }).total)
    }
}
