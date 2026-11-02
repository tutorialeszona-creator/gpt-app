package ar.com.midepartamento.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import ar.com.midepartamento.MainActivity
import org.junit.Rule
import org.junit.After
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import ar.com.midepartamento.data.FinanceDatabase
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @After fun releaseDatabase() {
        compose.activityRule.scenario.close()
        FinanceDatabase.get(ApplicationProvider.getApplicationContext<Context>()).close()
    }
    @Test fun appStartsLoadsDatabaseAndNavigatesToBillsAndSettings() {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Preparando registros mensuales…").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Tu casa, tus cuentas claras.").assertIsDisplayed()
        compose.onNodeWithText("Boletas").performClick()
        // Scroll through the real LazyColumn; on small devices a rate notice can push the heading below the fold.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Boletas del mes"))
        compose.onNodeWithText("Boletas del mes").assertIsDisplayed()
        compose.onNodeWithText("Configuración").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Tu configuración"))
        compose.onNodeWithText("Tu configuración").assertIsDisplayed()
    }
}
