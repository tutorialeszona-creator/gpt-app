package ar.com.midepartamento.data

import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import org.robolectric.shadows.ShadowContentResolver
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import ar.com.midepartamento.domain.PurchaseItem
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import java.util.Base64
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PurchaseRepositoryTest {
    @After fun closeDatabase() { FinanceDatabase.get(ApplicationProvider.getApplicationContext<Context>()).close() }
    @Test fun purchaseProductsPhotoDuplicateProtectionAndExportArePersisted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = Repository(context)
        val receipt = File(context.filesDir, "receipts/ticket-test.jpg").apply { parentFile!!.mkdirs(); writeText("photo-bytes") }
        val expense = Expense("purchase", "2026-01-01", "Carnicería", "Asado", 850_000, "Carnicería del barrio", listOf(PurchaseItem("Carne", 900_000)), receipt.name)
        repo.saveExpense(expense)
        repo.saveExpense(expense.copy(cents = 800_000)) // same confirmation id is an update, not a second purchase
        assertEquals(1, repo.dao.allExpenses().size)
        assertEquals(800_000L, repo.dao.allExpenses().single().cents)
        assertEquals(expense.items, repo.dao.allExpenses().single().items)
        var duplicateRejected = false
        try { repo.saveExpense(expense.copy(id = "second-scan")) } catch (e: IllegalArgumentException) { duplicateRejected = true }
        assertTrue(duplicateRejected)
        assertEquals(1, repo.dao.allExpenses().size)
        repo.discardReceipt(receipt.name)
        assertTrue(receipt.exists()) // canceling a scan cannot delete a photo already attached to a saved purchase
        val file = File(context.cacheDir, "purchases-backup.json")
        repo.export(Uri.fromFile(file))
        val json = JSONObject(file.readText())
        assertEquals(2, json.getInt("version"))
        assertEquals("Carnicería del barrio", json.getJSONArray("expenses").getJSONObject(0).getString("merchant"))
        assertEquals("Carne", json.getJSONArray("expenses").getJSONObject(0).getJSONArray("items").getJSONObject(0).getString("name"))
        assertTrue(json.getJSONObject("attachments").has(receipt.name))
        repo.dao.deleteExpense(expense.id)
        assertTrue(repo.dao.allExpenses().isEmpty())
        repo.discardReceipt(receipt.name)
        assertFalse(receipt.exists())
    }
    @Test fun galleryCopyUsesContentHashAndKeepsSamePhotoOnlyOnce() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getProviderInfo(ComponentName(context, FileProvider::class.java), PackageManager.GET_META_DATA)
        val provider = Robolectric.buildContentProvider(FileProvider::class.java).create(info).get()
        ShadowContentResolver.registerProviderInternal(info.authority, provider)
        val image = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJz0AAAAASUVORK5CYII=")
        val source = File(context.filesDir, "receipts/camera-test.png").apply { parentFile!!.mkdirs(); writeBytes(image) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", source)
        val store = ReceiptFiles(context)
        val first = store.copy(uri)
        val second = store.copy(uri)
        assertEquals(first.name, second.name)
        assertArrayEquals(image, first.readBytes())
        assertTrue(first.name.startsWith("ticket-"))
        assertTrue(source.exists())
    }
}
