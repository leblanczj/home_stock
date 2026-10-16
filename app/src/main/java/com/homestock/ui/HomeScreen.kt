package com.homestock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homestock.domain.HomeRow
import com.homestock.domain.Lookback
import com.homestock.domain.Residence
import com.homestock.domain.formatDepletion
import com.homestock.domain.formatQuantity
import com.homestock.domain.formatSpeed
import com.homestock.domain.lowStockRows
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    residences: List<Residence>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = residences.find { it.id == selectedId }
    TopAppBar(
        title = {
            TextButton(onClick = { if (residences.isNotEmpty()) open = true }) {
                Text(current?.name ?: "还没有住所")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                residences.filter { !it.abolished }.forEach { residence ->
                    DropdownMenuItem(
                        text = { Text(residence.name) },
                        onClick = {
                            onSelect(residence.id)
                            open = false
                        },
                    )
                }
            }
        },
        actions = { TextButton(onClick = onSettings) { Text("统计范围") } },
    )
}

@Composable
fun LookbackDialog(current: Lookback, onSelect: (Lookback) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(Lookback.WEEK to "周", Lookback.MONTH to "月", Lookback.YEAR to "年")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("统计范围") },
        text = {
            Column {
                Text("只用这段时间的记账来估计用量。")
                options.forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = current == value, onClick = { onSelect(value) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
fun HomeScreen(
    state: UiState,
    onOpenProduct: (Long) -> Unit,
    onInventory: () -> Unit,
    onPurchase: () -> Unit,
    onCount: () -> Unit,
    onConsume: () -> Unit,
    onDiscard: () -> Unit,
    onPlaces: () -> Unit,
    contentPadding: PaddingValues,
) {
    val residenceId = state.residenceId
    val products = state.input.products.filter { it.residenceId == residenceId && !it.abolished }
    val rows = if (residenceId == null) emptyList() else state.projection.lowStockRows(residenceId)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (residenceId == null) {
            item {
                EmptyHint(
                    "先添加居住者，再写下住在哪。",
                    action = "去住所",
                    onAction = onPlaces,
                )
            }
        } else if (products.isEmpty()) {
            item { EmptyHint("记一笔新购，库存就会出现在这里。") }
        } else if (rows.isEmpty()) {
            item { Text("目前没有要补的。") }
        }
        items(rows, key = { it.productId }) { row ->
            HomeCard(row, state.today) { onOpenProduct(row.productId) }
        }
        if (residenceId != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onPurchase, modifier = Modifier.weight(1f)) { Text("新购") }
                    Button(onClick = onCount, modifier = Modifier.weight(1f)) { Text("点数") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onConsume, modifier = Modifier.weight(1f)) { Text("消耗") }
                    Button(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("废弃") }
                }
            }
            item { TextButton(onClick = onInventory) { Text("全部库存") } }
        }
    }
}

@Composable
private fun HomeCard(row: HomeRow, today: LocalDate, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${row.brand} ${row.spec}")
            Text("在手 ${row.onHand.formatQuantity()} ${row.unit}")
            Text("速度 ${formatSpeed(row.speed, row.unit)}")
            Text("用完 ${formatDepletion(row.depletion, today)}")
            Text("${row.lowStockDays} 天内提醒")
        }
    }
}
