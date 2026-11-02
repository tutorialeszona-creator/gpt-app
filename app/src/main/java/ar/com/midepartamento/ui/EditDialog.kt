@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ar.com.midepartamento.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.com.midepartamento.data.*
import ar.com.midepartamento.domain.*
import java.time.LocalDate
import java.time.YearMonth

@Composable fun EditDialog(editor: Editor, state: UiState, onDismiss: () -> Unit, onSave: (Any) -> Unit) {
    val bill = editor.value as? Bill; val income = editor.value as? Income
    val expense = editor.value as? Expense; val recurring = editor.value as? Recurring
    val settings = editor.value as? Settings
    var name by rememberSaveable { mutableStateOf(bill?.name ?: income?.name ?: recurring?.name ?: expense?.description ?: "") }
    var amount by rememberSaveable { mutableStateOf((bill?.planned ?: income?.cents ?: expense?.cents ?: recurring?.cents)?.let(::decimal) ?: "") }
    var actual by rememberSaveable { mutableStateOf(bill?.actual?.let(::decimal) ?: "") }
    var currency by rememberSaveable { mutableStateOf(income?.currency ?: "ARS") }
    var category by rememberSaveable { mutableStateOf(expense?.category ?: Categories.first()) }
    var estimated by rememberSaveable { mutableStateOf(bill?.estimated ?: recurring?.estimated ?: false) }
    var due by rememberSaveable { mutableStateOf(bill?.dueDate ?: "") }
    var paid by rememberSaveable { mutableStateOf(bill?.paidDate != null) }
    var paidDate by rememberSaveable { mutableStateOf(bill?.paidDate ?: "") }
    var date by rememberSaveable { mutableStateOf(expense?.date ?: if (state.month == YearMonth.now()) LocalDate.now().toString() else "") }
    var month by rememberSaveable { mutableStateOf(bill?.month ?: income?.month ?: recurring?.startMonth ?: state.month.toString()) }
    var notes by rememberSaveable { mutableStateOf(bill?.notes ?: "") }
    var frequency by rememberSaveable { mutableIntStateOf(recurring?.frequency ?: 1) }
    var rate by rememberSaveable { mutableStateOf(settings?.rateCents?.takeIf { it > 0 }?.let(::decimal) ?: "") }
    var salary by rememberSaveable { mutableStateOf(settings?.salaryCents?.let(::decimal) ?: "1000.00") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val title = when(editor.kind) { "bill" -> "Boleta"; "income" -> "Ingreso"; "expense" -> "Gasto personal"; "recurring" -> "Gasto recurrente"; else -> "Cotización e ingreso base" }
    fun validDate(text: String): String { require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(text)) { "Usá el formato AAAA-MM-DD" }; return LocalDate.parse(text).toString() }
    fun validMonth(text: String): String { val m = YearMonth.parse(text); require(m.year in 2000..2100) { "El año debe estar entre 2000 y 2100" }; return m.toString() }
    fun money(text: String): Long = requireNotNull(parseMoney(text)) { "Ingresá un importe positivo, sin separadores de miles y con hasta 2 decimales" }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (editor.value == null) "Agregar $title" else "Editar $title") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (editor.kind == "settings") {
                Text("Cotización manual: pesos argentinos por cada dólar. Se usa para todos los ingresos en USD, incluidos meses anteriores.")
                MoneyField(rate, { rate = it }, "1 USD en ARS (vacío = sin definir)")
                MoneyField(salary, { salary = it }, "Ingreso mensual base en USD")
                Text("El ingreso base se crea una sola vez al abrir un mes nuevo. Usá 0 para desactivarlo. Para modificar un mes ya generado, editá su ingreso en Movimientos.", style = MaterialTheme.typography.bodySmall)
            } else {
                OutlinedTextField(name, { name = it }, label = { Text(if (editor.kind == "expense") "Descripción (opcional)" else "Nombre") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                MoneyField(amount, { amount = it }, if (editor.kind == "bill") "Importe previsto en ARS" else "Importe en ${if (editor.kind == "income") currency else "ARS"}")
                Text("Sin separadores de miles. Ejemplo: 460000 o 1250,50.", style = MaterialTheme.typography.bodySmall)
                if (editor.kind == "income") {
                    Choice("Moneda", currency, listOf("ARS", "USD")) { currency = it }
                    OutlinedTextField(month, { month = it }, label = { Text("Mes (AAAA-MM)") }, modifier = Modifier.fillMaxWidth())
                }
                if (editor.kind == "expense") {
                    Choice("Categoría", category, Categories) { category = it }
                    DateField(date, { date = it }, "Fecha (AAAA-MM-DD)")
                }
                if (editor.kind == "bill" || editor.kind == "recurring") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Importe estimado", Modifier.weight(1f)); Switch(estimated, { estimated = it }) }
                    OutlinedTextField(month, { month = it }, label = { Text(if (editor.kind == "recurring") "Desde el mes (AAAA-MM)" else "Mes correspondiente (AAAA-MM)") }, modifier = Modifier.fillMaxWidth())
                }
                if (editor.kind == "bill") {
                    MoneyField(actual, { actual = it }, "Importe real en ARS (opcional)")
                    DateField(due, { due = it }, "Vencimiento (opcional)")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Boleta pagada", Modifier.weight(1f)); Switch(paid, { paid = it }) }
                    if (paid) {
                        Text("Confirmá el importe real y la fecha efectiva del pago.", color = Mint)
                        DateField(paidDate, { paidDate = it }, "Fecha de pago (AAAA-MM-DD)")
                    }
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Text("No hace falta registrar este pago como gasto personal. Ya se contabiliza desde la boleta.", style = MaterialTheme.typography.bodySmall)
                }
                if (editor.kind == "recurring") {
                    Choice("Frecuencia", "$frequency", listOf("1", "2", "3", "6", "12"), { "Cada $it mes(es)" }) { frequency = it.toInt() }
                    Text("Se genera una boleta por período al abrir un mes nuevo. Editar o desactivar este concepto conserva las boletas existentes.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        try {
            val value: Any = if (editor.kind == "settings") {
                val base = if (salary.trim() in listOf("0", "0.00", "0,00")) 0L else money(salary)
                val exchange = if (rate.isBlank()) 0L else money(rate)
                require(exchange <= 100_000_000L) { "La cotización máxima es ARS 1.000.000 por USD" }
                (settings ?: Settings()).copy(rateCents = exchange, salaryCents = base)
            } else {
                val cents = money(amount)
                require(name.isNotBlank() || editor.kind == "expense") { "Ingresá un nombre" }
                require(name.length <= 120 && notes.length <= 2000) { "El nombre admite 120 caracteres y las notas 2000" }
                when(editor.kind) {
                    "income" -> Income(income?.id ?: newId(), validMonth(month), name.trim(), cents, currency)
                    "expense" -> Expense(expense?.id ?: newId(), validDate(date).also { require(it.startsWith(state.month.toString())) { "La fecha debe corresponder al mes seleccionado (${state.month})" } }, category, name.trim(), cents)
                    "recurring" -> Recurring(recurring?.id ?: newId(), name.trim(), cents, estimated, frequency, validMonth(month))
                    else -> {
                        val real = if (actual.isBlank()) null else money(actual)
                        require(!paid || real != null) { "Para pagar, ingresá el importe real" }
                        val payment = if (paid) validDate(paidDate) else null
                        require(payment == null || !LocalDate.parse(payment).isAfter(LocalDate.now())) { "La fecha de pago no puede ser futura" }
                        val period = validMonth(month)
                        Bill(bill?.id ?: newId(), if (bill?.month == period) bill.recurringId else null, period, name.trim(), cents, real, estimated, if (due.isBlank()) null else validDate(due), payment, notes.trim(), bill?.attachment)
                    }
                }
            }
            onSave(value)
        } catch (e: Exception) { error = if (e is java.time.format.DateTimeParseException) "Revisá la fecha o el mes: usá AAAA-MM-DD o AAAA-MM" else e.message ?: "Revisá los datos" }
    }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
@Composable private fun MoneyField(value: String, change: (String) -> Unit, label: String) { OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
@Composable private fun Choice(label: String, value: String, options: List<String>, format: (String) -> String = { it }, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${format(value)}") }; DropdownMenu(expanded, { expanded = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(format(option)) }, onClick = { change(option); expanded = false }) } } }
}

@Composable internal fun DateField(value: String, change: (String) -> Unit, label: String) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Outlined.CalendarMonth, "Elegir fecha") } })
    if (open) {
        val initial = runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { change(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; open = false }, enabled = picker.selectedDateMillis != null) { Text("Aceptar") }
        }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }) { DatePicker(picker) }
    }
}
