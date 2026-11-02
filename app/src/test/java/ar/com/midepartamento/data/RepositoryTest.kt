package ar.com.midepartamento.data

import android.content.Context
import android.net.Uri
import java.io.File
import org.json.JSONObject
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RepositoryTest {
    @After fun releaseDatabase() {
        FinanceDatabase.get(ApplicationProvider.getApplicationContext<Context>()).close()
    }
    @Test fun monthlyRecordsPaymentsCorrectionsAndPersistence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = Repository(context)
        coroutineScope { (1..4).map { async { repo.ensureMonth("2026-11") } }.awaitAll() }
        val initial = repo.dao.allBills()
        assertEquals(7, initial.size)
        assertEquals(66_800_000L, initial.sumOf { it.planned })
        assertTrue(initial.all { it.paidDate == null && it.dueDate == null })
        assertEquals(setOf("Electricidad", "Gas", "Agua"), initial.filter { it.estimated }.map { it.name }.toSet())
        assertEquals(100_000L, repo.dao.allIncomes().single().cents)
        val light = initial.first { it.name == "Electricidad" }
        repo.saveBill(light.copy(actual = 4_500_000, paidDate = "2026-11-03"))
        repo.saveBill(repo.dao.allBills().first { it.id == light.id })
        assertEquals(1, repo.dao.allEvents().size)
        repo.saveBill(repo.dao.allBills().first { it.id == light.id }.copy(paidDate = null))
        assertEquals(2, repo.dao.allEvents().size)
        repo.deleteBill(initial.first { it.name == "ABL" })
        repo.ensureMonth("2026-11")
        assertEquals(6, repo.dao.allBills().size) // intentional deletion is not resurrected
        val water = repo.dao.allRecurring().first { it.name == "Agua" }
        repo.saveRecurring(water.copy(frequency = 2, cents = 3_600_000), "2026-11", false)
        repo.ensureMonth("2026-12")
        assertEquals(6, repo.dao.allBills().count { it.month == "2026-12" })
        repo.ensureMonth("2027-01")
        assertEquals(7, repo.dao.allBills().count { it.month == "2027-01" })
        assertEquals(3_600_000L, repo.dao.allBills().first { it.name == "Agua" && it.month == "2027-01" }.planned)
        assertEquals(3_250_000L, repo.dao.allBills().first { it.name == "Agua" && it.month == "2026-11" }.planned)
        repo.dao.expense(Expense("test-expense", "2026-11-04", "Supermercado", "Compra semanal", 45_000))
        repo.dao.income(Income("test-income", "2026-11", "Extra", 50_000, "ARS"))
        repo.dao.settings(Settings(rateCents = 135_000))
        // A second database instance verifies persisted disk records, not only an in-memory list.
        val reopened = Room.databaseBuilder(context, FinanceDatabase::class.java, "mi-departamento.db").build()
        assertEquals(45_000L, reopened.dao().allExpenses().single().cents)
        assertEquals(135_000L, reopened.dao().getSettings()!!.rateCents)
        assertEquals(2, reopened.dao().allIncomes().count { it.month == "2026-11" })
        assertEquals(2, reopened.dao().allEvents().size)
        reopened.close()
        val receipt = File(context.filesDir, "receipts/test.pdf").apply { parentFile!!.mkdirs(); writeText("%PDF-test") }
        repo.saveBill(repo.dao.allBills().first { it.id == light.id }.copy(attachment = receipt.name))
        val backupFile = File(context.cacheDir, "backup.json")
        repo.export(Uri.fromFile(backupFile))
        val backup = JSONObject(backupFile.readText())
        assertEquals(1, backup.getInt("version"))
        assertEquals(19, backup.getJSONArray("bills").length())
        assertEquals("JVBERi10ZXN0", backup.getJSONObject("attachments").getString("test.pdf"))
        assertEquals(2, backup.getJSONArray("paymentEvents").length())
        repo.dao.deleteExpense("test-expense")
        assertTrue(repo.dao.allExpenses().isEmpty())
    }
}
