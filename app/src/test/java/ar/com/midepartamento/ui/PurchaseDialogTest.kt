package ar.com.midepartamento.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import ar.com.midepartamento.data.Expense
import ar.com.midepartamento.domain.PurchaseItem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PurchaseDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun scannedPurchaseRequiresConfirmationAndKeepsCorrectedTotalAndProducts() {
        var saved: Expense? = null
        val draft = Expense("scan-id", "2026-01-01", "Verdulería", "", 20_000, "VERDULERIA", listOf(PurchaseItem("Tomate", 20_000)), "ticket.jpg")
        compose.setContent { DepartmentTheme { PurchaseDialog(draft, YearMonth.of(2026, 1), rawText = "TOMATE 200,00", onDismiss = {}, onSave = { saved = it }) } }
        compose.onNodeWithText("Revisar ticket").assertExists()
        assertNull(saved)
        compose.onNodeWithText("Total de la compra en ARS").performScrollTo().performTextReplacement("180,50")
        compose.onNodeWithText("Guardar compra").performClick()
        compose.runOnIdle {
            assertEquals(18_050L, saved!!.cents)
            assertEquals(listOf(PurchaseItem("Tomate", 20_000)), saved!!.items)
            assertEquals("ticket.jpg", saved!!.receipt)
            assertEquals("scan-id", saved!!.id)
        }
    }
    @Test fun missingDateAndTotalAreNotInventedForUnreadableReceipt() {
        var saved: Expense? = null
        val draft = Expense("empty-scan", "", "Supermercado", "", 0)
        compose.setContent { DepartmentTheme { PurchaseDialog(draft, YearMonth.now(), rawText = "", onDismiss = {}, onSave = { saved = it }) } }
        compose.onNodeWithText("Guardar compra").performClick()
        compose.onNodeWithText("Completá una fecha válida", substring = true).assertExists()
        assertNull(saved)
    }
}
