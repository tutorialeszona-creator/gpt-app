package ar.com.midepartamento.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import ar.com.midepartamento.data.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun incomeFormRejectsBadAmountsAndSavesValidIncome() {
        val state = UiState(YearMonth.of(2026, 11))
        var saved: Any? = null
        compose.setContent { DepartmentTheme { EditDialog(Editor("income"), state, {}, { saved = it }) } }
        compose.onNodeWithText("Nombre").performTextInput("Trabajo extra")
        compose.onNodeWithText("Importe en ARS").performTextInput("-1")
        compose.onNodeWithText("Guardar").performClick()
        compose.onNodeWithText("Ingresá un importe positivo", substring = true).assertExists()
        compose.onNodeWithText("Importe en ARS").performTextClearance()
        compose.onNodeWithText("Importe en ARS").performTextInput("2500,50")
        compose.onNodeWithText("Guardar").performClick()
        compose.runOnIdle {
            val income = saved as Income
            org.junit.Assert.assertEquals("Trabajo extra", income.name)
            org.junit.Assert.assertEquals(250050L, income.cents)
            org.junit.Assert.assertEquals("2026-11", income.month)
        }
    }
}
