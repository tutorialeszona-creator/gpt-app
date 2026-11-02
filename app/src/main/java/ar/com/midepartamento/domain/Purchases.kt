package ar.com.midepartamento.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

data class PurchaseItem(val name: String, val cents: Long? = null)
val FoodCategories = listOf("Supermercado", "Verdulería", "Carnicería", "Comida")
data class FoodSummary(val byCategory: Map<String, Long>) { val total get() = byCategory.values.sum() }
fun foodSummary(expenses: List<Pair<String, Long>>): FoodSummary = FoodSummary(
    FoodCategories.associateWith { category -> expenses.filter { it.first == category }.sumOf { it.second } }
)
data class ReceiptDraft(val merchant: String, val date: String?, val total: Long?, val items: List<PurchaseItem>, val rawText: String) {
    val category: String get() = when {
        merchant.uppercase(Locale.ROOT).contains("CARNI") -> "Carnicería"
        merchant.uppercase(Locale.ROOT).let { it.contains("VERDU") || it.contains("FRUTER") } -> "Verdulería"
        else -> "Supermercado"
    }
}
/** Conservative suggestions only. The user must verify every field before saving. */
object ReceiptParser {
    private val decimalPrice = Regex("(?<![\\d.,-])(?:\\d{1,3}(?:[.,]\\d{3})+|\\d+)[.,]\\d{2}(?![\\d.,])")
    private val totalLabel = Regex("^(?:(?:IMPORTE|MONTO)\\s+)?TOTAL(?:\\s+(?:A\\s+)?(?:PAGAR|ABONAR))?\\s*[:$=\\s]|^A\\s+PAGAR\\b")
    private val ignored = Regex("SUBTOTAL|IVA|CUIT|CA[EI]|VUELTO|CAMBIO|EFECTIVO|TARJETA|AHORRO|DESCUENTO|GRAVADO|IMPUEST|ARTICULOS|CANTIDAD|CUOTAS|PAGO|TOTAL|FECHA|HORA|TICKET|COMPROBANTE|TELEFONO|DOMICILIO|BIENVENID")
    fun parse(text: String): ReceiptDraft {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        var total: Long? = null
        var date: String? = null
        val products = mutableListOf<PurchaseItem>()
        for (line in lines) {
            val upper = line.uppercase(Locale.ROOT)
            if (date == null) {
                val dateMatch = Regex("(?<!\\d)(\\d{2})[/-](\\d{2})[/-](\\d{4})(?!\\d)").find(line)
                if (dateMatch != null) date = runCatching {
                    LocalDate.parse(dateMatch.value.replace('-', '/'), DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)).toString()
                }.getOrNull()
            }
            if (totalLabel.containsMatchIn(upper) && !Regex("SUBTOTAL|AHORRO|DESCUENTO|GRAVADO|IVA|ARTICULOS|CUOTAS").containsMatchIn(upper)) {
                val match = decimalPrice.findAll(line).lastOrNull()
                val candidate = if (match != null) price(match.value) else Regex("[0-9]+\\s*(?:ARS|\\$)?$").find(line)?.value?.replace(Regex("[^0-9]"), "")?.toLongOrNull()?.takeIf { it <= MAX_MONEY / 100 }?.times(100)
                if (candidate != null && candidate > 0) total = candidate
                continue
            }
            if (ignored.containsMatchIn(upper) || line.contains(Regex("-\\s*\\$?\\s*\\d"))) continue
            val amount = decimalPrice.findAll(line).lastOrNull() ?: continue
            if (line.substring(amount.range.last + 1).trim().removePrefix("ARS").trim().isNotEmpty()) continue
            val name = line.substring(0, amount.range.first).trim().trimEnd('$', ':').trim()
            if (name.count { it.isLetter() } < 3 || name.length > 120) continue
            val cents = price(amount.value) ?: continue
            products.add(PurchaseItem(name, cents))
        }
        val merchant = lines.take(5).firstOrNull { line ->
            line.count { it.isLetter() } >= 3 && !ignored.containsMatchIn(line.uppercase(Locale.ROOT)) && !decimalPrice.containsMatchIn(line) && !Regex("\\d{2}[/-]\\d{2}[/-]\\d{4}").containsMatchIn(line)
        }.orEmpty().take(120)
        return ReceiptDraft(merchant, date, total, products.take(100), text)
    }
    private fun price(text: String): Long? = runCatching {
        val split = maxOf(text.lastIndexOf(','), text.lastIndexOf('.'))
        val normalized = text.substring(0, split).replace(",", "").replace(".", "") + "." + text.substring(split + 1)
        BigDecimal(normalized).movePointRight(2).longValueExact().takeIf { it in 1..MAX_MONEY }
    }.getOrNull()
}
