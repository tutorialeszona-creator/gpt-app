package ar.com.midepartamento.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ar.com.midepartamento.domain.PurchaseItem
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationTest {
    @Test fun upgradeFromRealV1SchemaPreservesExpensesAndSupportsProducts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val schema = javaClass.classLoader!!.getResourceAsStream("ar.com.midepartamento.data.FinanceDatabase/1.json")!!.bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase("upgrade-test.db", Context.MODE_PRIVATE, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val name = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", name))
                val indexes = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", name))
            }
            old.execSQL("INSERT INTO expenses (id,date,category,description,cents) VALUES ('legacy','2026-11-01','Supermercado','Compra anterior',250000)")
            old.version = 1
        }
        val upgraded = Room.databaseBuilder(context, FinanceDatabase::class.java, "upgrade-test.db").addMigrations(MIGRATION_1_2).build()
        val oldExpense = upgraded.dao().allExpenses().single()
        assertEquals("Compra anterior", oldExpense.description)
        assertEquals(250_000L, oldExpense.cents)
        assertEquals("", oldExpense.merchant)
        assertTrue(oldExpense.items.isEmpty()); assertNull(oldExpense.receipt)
        upgraded.dao().expense(oldExpense.copy(items = listOf(PurchaseItem("Pan", 100_000)), merchant = "Mi comercio"))
        upgraded.close()
        val reopened = Room.databaseBuilder(context, FinanceDatabase::class.java, "upgrade-test.db").addMigrations(MIGRATION_1_2).build()
        assertEquals("Mi comercio", reopened.dao().allExpenses().single().merchant)
        assertEquals(listOf(PurchaseItem("Pan", 100_000)), reopened.dao().allExpenses().single().items)
        reopened.close()
    }
}
