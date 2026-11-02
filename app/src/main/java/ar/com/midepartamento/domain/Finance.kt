package ar.com.midepartamento.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.text.NumberFormat
import java.util.Locale

const val MAX_MONEY = 100_000_000_000L
fun parseMoney(text: String): Long? = runCatching {
    val clean = text.trim().replace(" ", "").replace(",", ".")
    if (!Regex("[0-9]+(\\.[0-9]{1,2})?").matches(clean)) return null
    BigDecimal(clean).movePointRight(2).longValueExact().takeIf { it in 1..MAX_MONEY }
}.getOrNull()
fun decimal(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
fun ars(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR")).format(BigDecimal.valueOf(cents, 2))
fun toArs(cents: Long, currency: String, rateCents: Long): Long =
    if (currency == "ARS") cents else BigDecimal.valueOf(cents).multiply(BigDecimal.valueOf(rateCents)).divide(BigDecimal(100), 0, RoundingMode.HALF_UP).longValueExact()
fun occurs(start: String, month: String, frequency: Int): Boolean {
    val a = YearMonth.parse(start); val b = YearMonth.parse(month)
    val distance = (b.year - a.year) * 12 + b.monthValue - a.monthValue
    return frequency > 0 && distance >= 0 && distance % frequency == 0
}
fun billStatus(paidDate: String?, dueDate: String?, today: LocalDate = LocalDate.now()): String = when {
    paidDate != null -> "Pagada"
    dueDate != null && LocalDate.parse(dueDate).isBefore(today) -> "Vencida"
    else -> "Pendiente"
}
data class Totals(val income: Long, val forecast: Long, val actual: Long) {
    val available get() = income - forecast
    val balance get() = income - actual
}
fun totals(incomes: List<Pair<Long, String>>, bills: List<Triple<Long, Long?, Boolean>>, expenses: List<Long>, rate: Long): Totals = Totals(
    incomes.sumOf { toArs(it.first, it.second, rate) },
    bills.sumOf { it.second ?: it.first } + expenses.sum(),
    bills.filter { it.third }.sumOf { it.second ?: it.first } + expenses.sum()
)
