package ar.com.midepartamento.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ar.com.midepartamento.data.*
import ar.com.midepartamento.domain.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ar.com.midepartamento.receipts.ReceiptScanner
import java.time.YearMonth

data class ScannedPurchase(val expense: Expense, val text: String)

data class Records(val recurring: List<Recurring>, val bills: List<Bill>, val incomes: List<Income>, val expenses: List<Expense>, val settings: Settings?)
data class UiState(val month: YearMonth = YearMonth.now(), val records: Records = Records(emptyList(), emptyList(), emptyList(), emptyList(), null), val events: List<PaymentEvent> = emptyList()) {
    val bills get() = records.bills.filter { it.month == month.toString() }
    val incomes get() = records.incomes.filter { it.month == month.toString() }
    val expenses get() = records.expenses.filter { it.date.startsWith(month.toString()) }
    val settings get() = records.settings ?: Settings()
    val missingRate get() = settings.rateCents == 0L && incomes.any { it.currency == "USD" }
    val totals get() = totals(incomes.map { it.cents to it.currency }, bills.map { Triple(it.planned, it.actual, it.paidDate != null) }, expenses.map { it.cents }, settings.rateCents)
}
class FinanceViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val month = MutableStateFlow(YearMonth.now())
    private val _scanBusy = MutableStateFlow(false)
    val scanBusy = _scanBusy.asStateFlow()
    private val _scanDraft = MutableStateFlow<ScannedPurchase?>(null)
    val scanDraft = _scanDraft.asStateFlow()
    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError = _purchaseError.asStateFlow()
    fun clearPurchaseError() { _purchaseError.value = null }
    private val _savingPurchase = MutableStateFlow(false)
    val savingPurchase = _savingPurchase.asStateFlow()
    private val receiptFiles = ReceiptFiles(app)
    private val _busy = MutableStateFlow(true)
    val busy = _busy.asStateFlow()
    val messages = Channel<String>(Channel.BUFFERED)
    private val records = combine(repo.dao.recurring(), repo.dao.bills(), repo.dao.incomes(), repo.dao.expenses(), repo.dao.settings()) { a,b,c,d,e -> Records(a,b,c,d,e) }
    val state = combine(month, records, repo.dao.events()) { m,r,e -> UiState(m,r,e) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())
    init { selectMonth(YearMonth.now()) }
    fun selectMonth(value: YearMonth) {
        viewModelScope.launch {
            _busy.value = true
            try { repo.ensureMonth(value.toString()); month.value = value }
            catch (e: Exception) { messages.send("No se pudo preparar el mes: ${e.localizedMessage}") }
            finally { _busy.value = false }
        }
    }
    private fun run(block: suspend () -> Unit) { viewModelScope.launch {
        try { block(); messages.send("Guardado") } catch (e: Exception) { messages.send("No se pudo guardar: ${e.localizedMessage}") }
    } }
    fun bill(value: Bill) = run { repo.saveBill(value) }
    fun income(value: Income) = run { repo.dao.income(value) }
    fun expense(value: Expense, onSaved: () -> Unit = {}) {
        if (_savingPurchase.value) return
        _savingPurchase.value = true
        _purchaseError.value = null
        viewModelScope.launch {
            try {
                repo.saveExpense(value)
                month.value = YearMonth.parse(value.date.take(7))
                if (_scanDraft.value?.expense?.id == value.id) _scanDraft.value = null
                onSaved()
                messages.send("Compra guardada")
            } catch (e: Exception) { _purchaseError.value = e.localizedMessage ?: "No se pudo guardar la compra" }
            finally { _savingPurchase.value = false }
        }
    }
    fun scanReceipt(uri: Uri, cameraFile: String? = null) {
        if (_scanBusy.value || _scanDraft.value != null) return
        _scanBusy.value = true
        _purchaseError.value = null
        viewModelScope.launch {
            var copied: java.io.File? = null
            try {
                copied = receiptFiles.copy(uri)
                val result = ReceiptScanner().scan(copied)
                _scanDraft.value = ScannedPurchase(
                    Expense(newId(), result.date.orEmpty(), result.category, "", result.total ?: 0L, result.merchant, result.items, copied.name), result.rawText
                )
            } catch (e: CancellationException) { withContext(NonCancellable) { copied?.let { repo.discardReceipt(it.name) } }; throw e }
            catch (e: Exception) { copied?.let { repo.discardReceipt(it.name) }; messages.send("No se pudo leer el ticket: ${e.localizedMessage}. Podés cargar la compra manualmente.") }
            finally {
                cameraFile?.let { java.io.File(getApplication<Application>().filesDir, "receipts/$it").delete() }
                _scanBusy.value = false
            }
        }
    }
    fun discardScan() {
        if (_savingPurchase.value) return
        _purchaseError.value = null
        val old = _scanDraft.value
        _scanDraft.value = null
        old?.expense?.receipt?.let { viewModelScope.launch { repo.discardReceipt(it) } }
    }
    fun recurring(value: Recurring, isNew: Boolean) = run { repo.saveRecurring(value, month.value.toString(), isNew) }
    fun settings(value: Settings) = run { repo.dao.settings(value) }
    fun delete(value: Any) = run { when(value) {
        is Bill -> repo.deleteBill(value)
        is Income -> repo.dao.deleteIncome(value.id)
        is Expense -> repo.deleteExpense(value)
        is Recurring -> repo.dao.recurring(value.copy(active = false))
        else -> Unit
    } }
    fun attach(bill: Bill, uri: Uri) = run { repo.attach(bill, uri) }
    fun export(uri: Uri) = run { repo.export(uri) }
}
