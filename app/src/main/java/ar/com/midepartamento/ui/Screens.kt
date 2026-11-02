@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ar.com.midepartamento.ui

import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.midepartamento.data.*
import ar.com.midepartamento.domain.*
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

val Spanish = Locale.forLanguageTag("es-AR")
val MonthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Spanish)
val Categories = listOf("Supermercado", "Verdulería", "Carnicería", "Transporte", "Comida", "Limpieza", "Mascotas", "Entretenimiento", "Compras", "Otros")
data class Editor(val kind: String, val value: Any? = null)

@Composable fun DepartmentApp(vm: FinanceViewModel) = DepartmentTheme {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val scanBusy by vm.scanBusy.collectAsStateWithLifecycle()
    val scanDraft by vm.scanDraft.collectAsStateWithLifecycle()
    val savingPurchase by vm.savingPurchase.collectAsStateWithLifecycle()
    val purchaseError by vm.purchaseError.collectAsStateWithLifecycle()
    var scanSource by rememberSaveable { mutableStateOf(false) }
    var cameraName by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<Editor?>(null) }
    var deleting by remember { mutableStateOf<Any?>(null) }
    var monthDialog by remember { mutableStateOf(false) }
    var attachmentBill by remember { mutableStateOf<Bill?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val b = attachmentBill
        if (uri != null && b != null) vm.attach(b, uri)
        attachmentBill = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.scanReceipt(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        cameraName?.let { name ->
            val file = File(context.filesDir, "receipts/$name")
            if (success) vm.scanReceipt(FileProvider.getUriForFile(context, "${context.packageName}.files", file), name) else file.delete()
        }
        cameraName = null
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.export(uri) }
    LaunchedEffect(vm) { vm.messages.receiveAsFlow().collect { snackbar.showSnackbar(it) } }
    val labels = listOf("Inicio", "Boletas", "Movimientos", "Estadísticas", "Configuración")
    val icons = listOf(Icons.Outlined.Home, Icons.AutoMirrored.Outlined.ReceiptLong, Icons.Outlined.SwapHoriz, Icons.Outlined.BarChart, Icons.Outlined.Settings)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            labels.forEachIndexed { i, label -> NavigationBarItem(selected = i == tab, onClick = { tab = i }, icon = { Icon(icons[i], label) }, label = { Text(label, fontSize = 10.sp, maxLines = 1) }) }
        } },
        floatingActionButton = { if (tab in 1..2 && !busy && !scanBusy && scanDraft == null) FloatingActionButton(onClick = { editor = Editor(if (tab == 1) "bill" else "expense") }, containerColor = Mint) { Icon(Icons.Outlined.Add, "Agregar") } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("MI DEPARTAMENTO", color = Mint, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(labels[tab], style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Text(if (tab == 0) "Tu casa, tus cuentas claras." else "Todo en un lugar. Todo bajo tu control.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { vm.selectMonth(state.month.minusMonths(1)) }, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Mes anterior") }
                TextButton(onClick = { monthDialog = true }, enabled = !busy) { Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(state.month.format(MonthFormat).replaceFirstChar { it.titlecase(Spanish) }) }
                IconButton(onClick = { vm.selectMonth(state.month.plusMonths(1)) }, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Mes siguiente") }
            } }
            if (busy || state.records.settings == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Preparando registros mensuales…") }
            else {
                if (state.missingRate) item { Notice("Falta la cotización del dólar. Los ingresos en USD todavía no están incluidos en los totales en pesos.") { editor = Editor("settings", state.settings) } }
                if (scanBusy) item { SurfaceCard { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Leyendo ticket en este teléfono…"); Text("La compra se mostrará para revisar antes de guardarse.") } }
                when (tab) {
                    0 -> {
                        item { Hero(state) }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Metric("Ingresos", ars(state.totals.income), Modifier.weight(1f), Mint)
                            Metric("Gastos reales", ars(state.totals.actual), Modifier.weight(1f), Peach)
                        } }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Metric("Pendientes · ${state.bills.count { it.paidDate == null }}", ars(state.bills.filter { it.paidDate == null }.sumOf { it.amount }), Modifier.weight(1f), Peach)
                            Metric("Pagadas · ${state.bills.count { it.paidDate != null }}", ars(state.bills.filter { it.paidDate != null }.sumOf { it.amount }), Modifier.weight(1f), Mint)
                        } }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { editor = Editor("income") }, modifier = Modifier.weight(1f)) { Text("+ Ingreso") }
                            OutlinedButton(onClick = { editor = Editor("expense") }, modifier = Modifier.weight(1f)) { Text("+ Gasto") }
                        } }
                        item { FoodSummaryCard(state) }
                        item { OutlinedButton(onClick = { scanSource = true }, enabled = !scanBusy && scanDraft == null, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.DocumentScanner, null); Spacer(Modifier.width(8.dp)); Text("Escanear ticket") } }
                        item { Section("Próximos vencimientos") }
                        val upcoming = state.bills.filter { it.paidDate == null && it.dueDate != null }.sortedBy { it.dueDate }
                        if (upcoming.isEmpty()) item { Empty("No hay vencimientos cargados", "Agregá la fecha real desde cada boleta.") }
                        upcoming.take(5).forEach { b -> item(key = "up${b.id}") { BillCard(b, { editor = Editor("bill", b) }, {}, {}, showActions = false) } }
                        item { Distribution(state) }
                        item { Text("Previstos: todas las boletas del mes + gastos personales. Reales: boletas pagadas + gastos personales. Un pago nunca se suma dos veces.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    1 -> {
                        item { Section("Boletas del mes", "${state.bills.size} registros") }
                        item { Text("Los importes y vencimientos se editan en cada boleta. Los conceptos recurrentes se administran en Configuración.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (state.bills.isEmpty()) item { Empty("Todavía no hay boletas", "Usá + para agregar una.") }
                        state.bills.forEach { b -> item(key = b.id) {
                            BillCard(b, { editor = Editor("bill", b) }, { deleting = b }, { attachmentBill = b; picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")) }, onOpen = {
                                val f = File(context.filesDir, "receipts/${b.attachment}")
                                val result = runCatching {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                }
                                if (result.isFailure) { vm.messages.trySend("No hay una aplicación disponible para abrir este comprobante") }
                            })
                            val events = state.events.filter { it.billId == b.id }
                            if (events.isNotEmpty()) {
                                var expanded by remember { mutableStateOf(false) }
                                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar historial" else "Historial de pagos (${events.size})") }
                                if (expanded) events.forEach { e -> Text("${e.paidDate ?: "Sin fecha de pago"} · ${e.action} · ${ars(e.cents)}", style = MaterialTheme.typography.bodySmall) }
                            }
                        } }
                    }
                    2 -> {
                        item { FoodSummaryCard(state) }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { editor = Editor("expense") }, modifier = Modifier.weight(1f)) { Text("+ Compra / gasto") }
                            OutlinedButton(onClick = { scanSource = true }, enabled = !scanBusy && scanDraft == null, modifier = Modifier.weight(1f)) { Text("Escanear ticket") }
                        } }
                        item { Section("Ingresos"); Spacer(Modifier.height(8.dp)); Button(onClick = { editor = Editor("income") }) { Text("Agregar ingreso") } }
                        if (state.incomes.isEmpty()) item { Empty("Sin ingresos", "Registrá un ingreso en ARS o USD.") }
                        state.incomes.forEach { i -> item(key = i.id) { RecordCard(i.name, if (i.currency == "USD") "USD ${decimal(i.cents)} · ${if (state.settings.rateCents > 0) ars(toArs(i.cents, i.currency, state.settings.rateCents)) else "Sin cotización"}" else "Pesos argentinos", if (i.currency == "USD") "USD ${decimal(i.cents)}" else ars(i.cents), Mint, { editor = Editor("income", i) }, { deleting = i }) } }
                        item { Section("Gastos personales", ars(state.expenses.sumOf { it.cents })) }
                        if (state.expenses.isEmpty()) item { Empty("Sin gastos personales", "Supermercado, transporte, mascotas y más.") }
                        state.expenses.forEach { e -> item(key = e.id) { PurchaseCard(e, { editor = Editor("expense", e) }, { deleting = e }) } }
                    }
                    3 -> {
                        item { FoodSummaryCard(state) }
                        item { Distribution(state) }
                        item { Metric("Saldo actual del mes", ars(state.totals.balance), Modifier.fillMaxWidth(), Mint) }
                        item { Text("Saldo = ingresos − gastos reales. Hay ${state.bills.count { it.paidDate == null }} boletas sin pagar. El saldo no representa ahorro definitivo.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        item {
                            val housing = state.bills.sumOf { it.amount }
                            val share = if (state.totals.income > 0 && !state.missingRate) "%.1f %%".format(Spanish, housing.toDouble() * 100 / state.totals.income) else "Sin datos de ingresos"
                            Metric("Ingresos destinados a vivienda (previsto)", share, Modifier.fillMaxWidth(), Lavender)
                        }
                        item { Section("Comparación mensual", "Últimos 6 meses") }
                        (5 downTo 0).forEach { offset -> item {
                            val m = state.month.minusMonths(offset.toLong()).toString()
                            val bs = state.records.bills.filter { it.month == m }
                            val es = state.records.expenses.filter { it.date.startsWith(m) }
                            val ins = state.records.incomes.filter { it.month == m }
                            val t = totals(ins.map { it.cents to it.currency }, bs.map { Triple(it.planned, it.actual, it.paidDate != null) }, es.map { it.cents }, state.settings.rateCents)
                            SurfaceCard { Text(YearMonth.parse(m).format(MonthFormat), fontWeight = FontWeight.Bold)
                                Text(if (bs.isEmpty() && es.isEmpty() && ins.isEmpty()) "Sin registros" else "Previstos ${ars(t.forecast)}\nReales ${ars(t.actual)}") }
                        } }
                        item { Section("Luz, gas y agua", "Importes por período") }
                        state.records.bills.filter { it.name.lowercase(Spanish) in listOf("electricidad", "luz", "gas", "agua") && it.month <= state.month.toString() }.sortedWith(compareByDescending<Bill> { it.month }.thenBy { it.name }).take(18).forEach { b -> item { SurfaceCard { Text("${b.name} · ${b.month}"); Text("${ars(b.amount)}${if (b.estimated && b.actual == null) " · Estimado" else " · Real"}", color = Mint) } } }
                        item { Text("La comparación usa los registros cargados y la cotización actual. No genera meses ni inventa consumos anteriores.", style = MaterialTheme.typography.bodySmall) }
                    }
                    4 -> {
                        item { SurfaceCard { Text("Tu configuración", style = MaterialTheme.typography.titleLarge); Text("Moneda principal · ARS"); Text("1 USD = ${if (state.settings.rateCents > 0) ars(state.settings.rateCents) else "Sin definir"}"); Text("Ingreso base · USD ${decimal(state.settings.salaryCents)}"); Button(onClick = { editor = Editor("settings", state.settings) }) { Text("Editar cotización e ingreso base") } } }
                        item { Section("Gastos recurrentes"); Text("Los cambios se aplican a meses que todavía no se generaron. Las boletas existentes se corrigen desde Boletas.", color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick = { editor = Editor("recurring") }) { Text("+ Agregar concepto recurrente") } }
                        state.records.recurring.filter { it.active }.forEach { r -> item(key = r.id) { RecordCard(r.name, "Cada ${r.frequency} mes(es) · desde ${r.startMonth}${if (r.estimated) " · Estimado" else ""}", ars(r.cents), Lavender, { editor = Editor("recurring", r) }, { deleting = r }) } }
                        item { SurfaceCard {
                            Text("Tus datos se quedan con vos", style = MaterialTheme.typography.titleLarge)
                            Text("Tus finanzas y los tickets se guardan en este dispositivo. No hay cuentas, servidores ni conexión requerida.")
                            Button(onClick = { exporter.launch("mi-departamento-${LocalDate.now()}.json") }) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Exportar copia de seguridad") }
                            Text("JSON con todos los meses, movimientos, historial y comprobantes. Guardalo en un lugar privado. La importación desde la app se agregará en una próxima versión.", style = MaterialTheme.typography.bodySmall)
                        } }
                        item { Text("Mi Departamento · versión 0.2.0\nHecho para llevar tus cuentas con calma.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
    if (monthDialog) MonthDialog(state.month, { monthDialog = false }) { vm.selectMonth(it); monthDialog = false }
    if (editor == null) scanDraft?.let { draft -> key(draft.expense.id) {
        PurchaseDialog(draft.expense, state.month, draft.text, savingPurchase, purchaseError, { vm.discardScan() }, { vm.expense(it) })
    } }
    if (scanSource) AlertDialog(onDismissRequest = { scanSource = false }, title = { Text("Escanear ticket") }, text = { Text("Sacá una foto nítida del ticket completo o elegí una de la galería. La lectura funciona sin internet y te mostrará los datos antes de guardarlos.") }, confirmButton = {
        TextButton(onClick = {
            scanSource = false
            runCatching {
                val dir = File(context.filesDir, "receipts").apply { mkdirs() }
                val name = "camera-${newId()}.jpg"
                val file = File(dir, name).apply { createNewFile() }
                cameraName = name
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
            }.onFailure { cameraName?.let { File(context.filesDir, "receipts/$it").delete() }; cameraName = null; vm.messages.trySend("No se pudo abrir la cámara. Probá elegir una foto de la galería.") }
        }) { Text("Sacar foto") }
    }, dismissButton = { Column {
        TextButton(onClick = { scanSource = false; gallery.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }) { Text("Elegir de galería") }
        TextButton(onClick = { scanSource = false }) { Text("Cancelar") }
    } })
    editor?.let { e -> if (e.kind == "expense") {
        key((e.value as? Expense)?.id ?: "new-purchase") {
            PurchaseDialog(e.value as? Expense, state.month, saving = savingPurchase, serverError = purchaseError, onDismiss = { editor = null; vm.clearPurchaseError() }, onSave = { vm.expense(it) { editor = null } })
        }
    } else EditDialog(e, state, onDismiss = { editor = null }, onSave = { value ->
        when(value) { is Bill -> vm.bill(value); is Income -> vm.income(value); is Expense -> vm.expense(value); is Settings -> vm.settings(value); is Recurring -> vm.recurring(value, e.value == null) }
        editor = null
    }) }
    deleting?.let { value -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(if (value is Recurring) "¿Desactivar concepto?" else "¿Eliminar registro?") }, text = { Text(if (value is Recurring) "No se generarán nuevas boletas para este concepto. Las existentes se conservan." else "Se eliminará este registro y su historial de pagos. Esta acción no se puede deshacer.") }, confirmButton = { TextButton(onClick = { vm.delete(value); deleting = null }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }) }
}

@Composable fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}
@Composable fun Section(title: String, detail: String = "") { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); if (detail.isNotBlank()) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) } }
@Composable fun Empty(title: String, description: String) { SurfaceCard { Text(title, fontWeight = FontWeight.Medium); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable fun Notice(message: String, onClick: () -> Unit) { SurfaceCard { Text(message, color = Peach); TextButton(onClick = onClick) { Text("Configurar cotización") } } }
@Composable fun Metric(label: String, amount: String, modifier: Modifier = Modifier, color: Color = Mint) { Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(amount, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color) } } }
@Composable fun Hero(state: UiState) { Card(colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("DISPONIBLE PREVISTO${if (state.missingRate) " · PARCIAL" else ""}", letterSpacing = 1.sp, style = MaterialTheme.typography.labelMedium, color = Color(0xFF244A37))
    Text(ars(state.totals.available), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFF102D21))
    HorizontalDivider(color = Color(0x33305040))
    Text("Gastos previstos  ${ars(state.totals.forecast)}", color = Color(0xFF244A37))
    Text("Ingresos − boletas del mes − gastos personales", fontSize = 12.sp, color = Color(0xFF244A37))
} } }
@Composable fun RecordCard(title: String, detail: String, amount: String, color: Color, edit: () -> Unit, delete: () -> Unit) { SurfaceCard {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(amount, color = color) }
    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row { TextButton(onClick = edit) { Text("Editar") }; TextButton(onClick = delete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } }
} }
@Composable fun BillCard(b: Bill, edit: () -> Unit, delete: () -> Unit, attach: () -> Unit, showActions: Boolean = true, onOpen: () -> Unit = {}) { SurfaceCard {
    val status = billStatus(b.paidDate, b.dueDate)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(b.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(ars(b.amount), fontWeight = FontWeight.Bold) }
    Text("$status${if (b.estimated && b.actual == null) " · Estimado" else " · Real"}", color = if (status == "Pagada") Mint else Peach, style = MaterialTheme.typography.labelMedium)
    Text(if (b.dueDate == null) "Sin vencimiento cargado" else "Vence ${b.dueDate}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    if (b.paidDate != null) Text("Pagada el ${b.paidDate}", style = MaterialTheme.typography.bodySmall)
    if (b.notes.isNotBlank()) Text(b.notes, style = MaterialTheme.typography.bodySmall)
    if (showActions) {
        Row { TextButton(onClick = edit) { Text(if (b.paidDate == null) "Editar / pagar" else "Editar pago") }; TextButton(onClick = delete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } }
        Row { TextButton(onClick = attach) { Icon(Icons.Outlined.AttachFile, null, Modifier.size(16.dp)); Text(if (b.attachment == null) "Adjuntar comprobante" else "Reemplazar") }; if (b.attachment != null) TextButton(onClick = onOpen) { Text("Ver archivo") } }
    } else TextButton(onClick = edit) { Text("Ver boleta") }
} }
@Composable fun Distribution(state: UiState) {
    val entries = (state.bills.map { it.name to it.amount } + state.expenses.map { it.category to it.cents }).groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.toList().sortedByDescending { it.second }
    val total = entries.sumOf { it.second }
    SurfaceCard {
        Text("Así se distribuyen tus gastos", style = MaterialTheme.typography.titleMedium)
        Text("Importes previstos del mes", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (total == 0L) Text("Sin gastos para mostrar") else {
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(164.dp).padding(12.dp)) {
                    var start = -90f
                    entries.forEachIndexed { i, e -> val sweep = e.second.toFloat() / total * 360f; drawArc(ChartColors[i % ChartColors.size], start, sweep, false, style = Stroke(22.dp.toPx(), cap = StrokeCap.Butt)); start += sweep }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("TOTAL", style = MaterialTheme.typography.labelSmall); Text(ars(total), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
            entries.forEachIndexed { i,e -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = ChartColors[i % ChartColors.size], shape = RoundedCornerShape(3.dp), modifier = Modifier.size(8.dp)) {}
                Spacer(Modifier.width(8.dp)); Text(e.first, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("${ars(e.second)} · ${"%.0f".format(Spanish, e.second.toDouble() * 100 / total)}%", style = MaterialTheme.typography.bodySmall)
            } }
        }
    }
}
@Composable fun MonthDialog(month: YearMonth, dismiss: () -> Unit, save: (YearMonth) -> Unit) {
    var value by rememberSaveable { mutableStateOf(month.toString()) }; var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Elegir mes y año") }, text = { Column { OutlinedTextField(value, { value = it }, label = { Text("Mes (AAAA-MM)") }, isError = error); if (error) Text("Ingresá un mes válido entre 2000 y 2100") } }, confirmButton = { TextButton(onClick = { val m = runCatching { YearMonth.parse(value) }.getOrNull(); if (m == null || m.year !in 2000..2100) error = true else save(m) }) { Text("Ver mes") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
