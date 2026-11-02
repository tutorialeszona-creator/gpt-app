package ar.com.midepartamento.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import ar.com.midepartamento.data.Expense
import ar.com.midepartamento.domain.*

@Composable fun FoodSummaryCard(state: UiState) {
    val summary = foodSummary(state.expenses.map { it.category to it.cents })
    SurfaceCard {
        Text("Comida del mes", style = MaterialTheme.typography.titleLarge)
        Text(state.month.format(MonthFormat), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(ars(summary.total), style = MaterialTheme.typography.headlineMedium, color = Mint, fontWeight = FontWeight.Bold)
        summary.byCategory.forEach { (name, amount) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(name); Text(ars(amount)) }
        }
        Text("Acumulado de las compras registradas en estos rubros durante el mes seleccionado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun PurchaseCard(value: Expense, edit: () -> Unit, delete: () -> Unit) {
    var details by remember(value.id) { mutableStateOf(false) }
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(value.merchant.ifBlank { value.description.ifBlank { value.category } }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(ars(value.cents), color = Peach)
        }
        Text("${value.category} · ${value.date}", style = MaterialTheme.typography.bodySmall)
        if (value.merchant.isNotBlank() && value.description.isNotBlank()) Text(value.description, style = MaterialTheme.typography.bodySmall)
        if (value.items.isNotEmpty()) {
            TextButton(onClick = { details = !details }) { Text(if (details) "Ocultar productos" else "Ver productos (${value.items.size})") }
            if (details) value.items.forEach { item -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(item.cents?.let(::ars) ?: "Sin importe", style = MaterialTheme.typography.bodySmall)
            } }
        }
        if (value.receipt != null) Text("Foto del ticket guardada · abrila desde Editar", color = Mint, style = MaterialTheme.typography.bodySmall)
        Row { TextButton(onClick = edit) { Text("Editar") }; TextButton(onClick = delete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } }
    }
}
