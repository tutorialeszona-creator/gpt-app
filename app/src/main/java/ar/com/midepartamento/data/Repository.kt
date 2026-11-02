package ar.com.midepartamento.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import ar.com.midepartamento.domain.occurs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
class Repository(private val context: Context) {
    private val db = FinanceDatabase.get(context)
    val dao = db.dao()
    suspend fun ensureMonth(month: String) = db.withTransaction {
        if (dao.getSettings() == null) {
            dao.settings(Settings())
            listOf(
                Triple("Alquiler", 460_000L, false), Triple("Expensas", 80_000L, false),
                Triple("Internet", 26_000L, false), Triple("Electricidad", 50_000L, true),
                Triple("Gas", 7_500L, true), Triple("Agua", 32_500L, true), Triple("ABL", 12_000L, false)
            ).forEach { (name, pesos, estimated) -> dao.recurring(Recurring(newId(), name, pesos * 100, estimated, startMonth = month)) }
        }
        if (dao.monthExists(month) == 0) {
            dao.month(GeneratedMonth(month))
            dao.allRecurring().filter { it.active && occurs(it.startMonth, month, it.frequency) }.forEach {
                dao.bill(Bill(newId(), it.id, month, it.name, it.cents, estimated = it.estimated))
            }
            val salary = dao.getSettings()!!.salaryCents
            if (salary > 0) dao.income(Income(newId(), month, "Ingreso mensual", salary, "USD"))
        }
    }
    suspend fun saveRecurring(value: Recurring, month: String, isNew: Boolean) = db.withTransaction {
        dao.recurring(value)
        if (isNew && occurs(value.startMonth, month, value.frequency))
            dao.bill(Bill(newId(), value.id, month, value.name, value.cents, estimated = value.estimated))
    }
    suspend fun saveBill(value: Bill) = db.withTransaction {
        val old = dao.allBills().find { it.id == value.id }
        dao.bill(value)
        if (old?.paidDate != value.paidDate || (value.paidDate != null && old?.amount != value.amount)) {
            dao.event(PaymentEvent(newId(), value.id, Instant.now().toString(), if (value.paidDate == null) "Pago anulado" else "Pago registrado / corregido", value.amount, value.paidDate))
        }
    }
    suspend fun deleteBill(value: Bill) = db.withTransaction { dao.deleteEvents(value.id); dao.deleteBill(value.id) }
    suspend fun attach(bill: Bill, uri: Uri) = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: error("No se reconoce el tipo de archivo")
        require(mime == "application/pdf" || mime in listOf("image/jpeg", "image/png", "image/webp")) { "Elegí un PDF o una imagen JPG, PNG o WebP" }
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val ext = when (mime) { "application/pdf" -> "pdf"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val target = File(dir, "${newId()}.$ext")
        try {
            context.contentResolver.openInputStream(uri).use { source ->
                requireNotNull(source) { "No se pudo abrir el comprobante" }
                target.outputStream().use { out ->
                    val buffer = ByteArray(8192); var total = 0L
                    while (true) {
                        val n = source.read(buffer); if (n == -1) break
                        total += n; require(total <= 20 * 1024 * 1024) { "El archivo supera los 20 MB" }; out.write(buffer, 0, n)
                    }
                    require(total > 0) { "El archivo está vacío" }
                }
            }
            // Read current record so an attachment cannot revert edits made while picking it.
            db.withTransaction { dao.allBills().find { it.id == bill.id }?.let { dao.bill(it.copy(attachment = target.name)) } }
        } catch (e: Exception) { target.delete(); throw e }
    }
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = db.withTransaction {
            val result = JSONObject().put("format", "mi-departamento").put("version", 1).put("exportedAt", Instant.now().toString())
            val settings = dao.getSettings() ?: Settings()
            result.put("settings", JSONObject().put("rateCents", settings.rateCents).put("salaryCents", settings.salaryCents))
            fun array(items: List<JSONObject>) = JSONArray().apply { items.forEach { put(it) } }
            result.put("months", JSONArray(dao.allMonths().map { it.month }))
            result.put("recurring", array(dao.allRecurring().map { JSONObject().put("id", it.id).put("name", it.name).put("cents", it.cents).put("estimated", it.estimated).put("frequency", it.frequency).put("startMonth", it.startMonth).put("active", it.active) }))
            result.put("incomes", array(dao.allIncomes().map { JSONObject().put("id", it.id).put("month", it.month).put("name", it.name).put("cents", it.cents).put("currency", it.currency) }))
            result.put("expenses", array(dao.allExpenses().map { JSONObject().put("id", it.id).put("date", it.date).put("category", it.category).put("description", it.description).put("cents", it.cents) }))
            result.put("bills", array(dao.allBills().map { JSONObject().put("id", it.id).put("recurringId", it.recurringId ?: JSONObject.NULL).put("month", it.month).put("name", it.name).put("planned", it.planned).put("actual", it.actual ?: JSONObject.NULL).put("estimated", it.estimated).put("dueDate", it.dueDate ?: JSONObject.NULL).put("paidDate", it.paidDate ?: JSONObject.NULL).put("notes", it.notes).put("attachment", it.attachment ?: JSONObject.NULL) }))
            result.put("paymentEvents", array(dao.allEvents().map { JSONObject().put("id", it.id).put("billId", it.billId).put("timestamp", it.timestamp).put("action", it.action).put("cents", it.cents).put("paidDate", it.paidDate ?: JSONObject.NULL) }))
            result.put("attachments", JSONObject().apply {
                dao.allBills().mapNotNull { it.attachment }.distinct().forEach { name ->
                    val f = File(context.filesDir, "receipts/$name")
                    require(f.isFile) { "Falta un comprobante: $name. Volvé a adjuntarlo antes de exportar." }
                    put(name, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                }
            })
            result
        }
        context.contentResolver.openOutputStream(uri, "wt").use { requireNotNull(it).write(backup.toString(2).toByteArray(Charsets.UTF_8)) }
    }
}
