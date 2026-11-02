package ar.com.midepartamento.ui

import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ar.com.midepartamento.data.*
import ar.com.midepartamento.domain.*
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

private data class ProductInput(val id: String, val name: String, val amount: String)
private val ProductsSaver = listSaver<SnapshotStateList<ProductInput>, String>(
    save = { rows -> rows.flatMap { listOf(it.id, it.name, it.amount) } },
    restore = { saved -> saved.chunked(3).map { ProductInput(it[0], it[1], it[2]) }.toMutableStateList() }
)
@Composable fun PurchaseDialog(value: Expense?, month: YearMonth, rawText: String? = null, saving: Boolean = false, serverError: String? = null, onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    val context = LocalContext.current
    val id = rememberSaveable { value?.id ?: newId() }
    var merchant by rememberSaveable { mutableStateOf(value?.merchant.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(value?.category ?: "Supermercado") }
    var description by rememberSaveable { mutableStateOf(value?.description.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(value?.date ?: if (month == YearMonth.now()) LocalDate.now().toString() else "") }
    var total by rememberSaveable { mutableStateOf(value?.cents?.takeIf { it > 0 }?.let(::decimal).orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showText by rememberSaveable { mutableStateOf(false) }
    val rows = rememberSaveable(saver = ProductsSaver) { (value?.items ?: emptyList()).map { ProductInput(newId(), it.name, it.cents?.let(::decimal).orEmpty()) }.toMutableStateList() }
    val detailSum = rows.mapNotNull { parseMoney(it.amount) }.sum()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(if (rawText != null) "Revisar ticket" else if (value == null) "Agregar compra / gasto" else "Editar compra / gasto") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rawText != null) {
                Text("Revisá comercio, fecha, productos y total. La lectura puede equivocarse. Nada se guarda hasta que confirmes.", color = Peach)
                TextButton(onClick = { showText = !showText }) { Text(if (showText) "Ocultar texto leído" else "Ver texto leído del ticket") }
                if (showText) Text(rawText.ifBlank { "No se detectó texto. Podés completar los datos manualmente." }, style = MaterialTheme.typography.bodySmall)
            }
            PurchaseChoice(category) { category = it }
            OutlinedTextField(merchant, { merchant = it }, label = { Text("Comercio (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DateField(date, { date = it }, "Fecha de compra (AAAA-MM-DD)")
            if (date.take(7) != month.toString() && runCatching { LocalDate.parse(date) }.isSuccess) Text("Se guardará en el mes ${date.take(7)}", color = Mint)
            OutlinedTextField(description, { description = it }, label = { Text("Descripción / notas (opcional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Text("¿Qué compraste?", style = MaterialTheme.typography.titleSmall)
            rows.forEachIndexed { index, row -> key(row.id) {
                OutlinedTextField(row.name, { rows[index] = row.copy(name = it) }, label = { Text("Producto ${index + 1}") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(row.amount, { rows[index] = row.copy(amount = it) }, label = { Text("Importe del renglón (opcional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { rows.removeAt(index) }) { Text("Quitar producto ${index + 1}") }
            } }
            OutlinedButton(onClick = { rows.add(ProductInput(newId(), "", "")) }, enabled = rows.size < 100 && !saving) { Text("+ Agregar producto") }
            if (rows.isNotEmpty()) {
                Text("Suma del detalle: ${ars(detailSum)}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { total = decimal(detailSum) }, enabled = detailSum > 0 && rows.all { it.name.isNotBlank() && parseMoney(it.amount) != null } && !saving) { Text("Usar suma de productos como total") }
                if (parseMoney(total) != null && detailSum != parseMoney(total)) Text("El detalle no coincide con el total. Revisá descuentos, productos o precios. Se contará únicamente el total de la compra.", color = Peach, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(total, { total = it }, label = { Text("Total de la compra en ARS") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Sin separadores de miles: 2500 o 2500,50. El total se suma una sola vez; los productos son su detalle.", style = MaterialTheme.typography.bodySmall)
            if (value?.receipt != null) TextButton(onClick = {
                runCatching {
                    val file = File(context.filesDir, "receipts/${value.receipt}")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }.onFailure { error = "No se pudo abrir la foto con una aplicación del teléfono" }
            }) { Text("Ver foto del ticket") }
            if (serverError != null) Text(serverError, color = MaterialTheme.colorScheme.error)
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(enabled = !saving, onClick = {
        try {
            val actualDate = LocalDate.parse(date)
            require(actualDate.year in 2000..2100 && !actualDate.isAfter(LocalDate.now())) { "La fecha de compra no puede ser futura" }
            val cents = requireNotNull(parseMoney(total)) { "Ingresá el total positivo, sin separadores de miles y con hasta 2 decimales" }
            require(merchant.length <= 120 && description.length <= 2000) { "El comercio admite 120 caracteres y las notas 2000" }
            val items = rows.map { row ->
                require(row.name.isNotBlank() && row.name.length <= 120) { "Completá el nombre de cada producto (hasta 120 caracteres)" }
                PurchaseItem(row.name.trim(), if (row.amount.isBlank()) null else requireNotNull(parseMoney(row.amount)) { "Revisá el importe del producto ${row.name}" })
            }
            onSave(Expense(id, actualDate.toString(), category, description.trim(), cents, merchant.trim(), items, value?.receipt))
        } catch (e: Exception) { error = if (e is java.time.format.DateTimeParseException) "Completá una fecha válida en formato AAAA-MM-DD" else e.message ?: "Revisá los datos" }
    }) { Text(if (saving) "Guardando…" else "Guardar compra") } }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } })
}
@Composable private fun PurchaseChoice(value: String, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Rubro: $value") }
        DropdownMenu(expanded, { expanded = false }) { Categories.forEach { category -> DropdownMenuItem(text = { Text(category) }, onClick = { change(category); expanded = false }) } }
    }
}
