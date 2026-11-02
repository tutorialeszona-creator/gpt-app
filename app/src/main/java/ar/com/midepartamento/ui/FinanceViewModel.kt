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
import java.time.YearMonth

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
    fun expense(value: Expense) = run { repo.dao.expense(value) }
    fun recurring(value: Recurring, isNew: Boolean) = run { repo.saveRecurring(value, month.value.toString(), isNew) }
    fun settings(value: Settings) = run { repo.dao.settings(value) }
    fun delete(value: Any) = run { when(value) {
        is Bill -> repo.deleteBill(value)
        is Income -> repo.dao.deleteIncome(value.id)
        is Expense -> repo.dao.deleteExpense(value.id)
        is Recurring -> repo.dao.recurring(value.copy(active = false))
        else -> Unit
    } }
    fun attach(bill: Bill, uri: Uri) = run { repo.attach(bill, uri) }
    fun export(uri: Uri) = run { repo.export(uri) }
}
