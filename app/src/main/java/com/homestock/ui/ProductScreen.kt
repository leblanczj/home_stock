package com.homestock.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.homestock.domain.BatchSource
import com.homestock.domain.Depletion
import com.homestock.domain.PricePoint
import com.homestock.domain.formatDepletion
import com.homestock.domain.formatMissingUsage
import com.homestock.domain.formatQuantity
import com.homestock.domain.formatSpeed
import com.homestock.domain.formatYuanFromFen
import com.homestock.domain.residentNames
import com.homestock.domain.validateWarnDays
import java.io.File

@Composable
fun ProductScreen(
    state: UiState,
    productId: Long,
    viewModel: InventoryViewModel,
    onPurchase: () -> Unit,
    onCount: () -> Unit,
    onConsume: () -> Unit,
    onDiscard: () -> Unit,
    onEditPurchase: (Long) -> Unit,
    onEditCount: (Long) -> Unit,
    onEditConsumption: (Long) -> Unit,
    onEditDiscard: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    val product = state.input.products.find { it.id == productId }
    if (product == null) {
        Text("找不到这个用品。", modifier = Modifier.padding(16.dp))
        return
    }
    val math = state.projection.math(productId)
    val residence = state.input.residences.find { it.id == product.residenceId }
    var low by remember(product.lowStockDays) { mutableStateOf(product.lowStockDays.toString()) }
    var expiryDays by remember(product.expiryWarnDays) { mutableStateOf(product.expiryWarnDays.toString()) }
    var editingNames by remember { mutableStateOf(false) }
    var brand by remember(product.brand) { mutableStateOf(product.brand) }
    var spec by remember(product.spec) { mutableStateOf(product.spec) }
    var unit by remember(product.unit) { mutableStateOf(product.unit) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var photosFor by remember { mutableStateOf<Long?>(null) }
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmText by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${product.brand} ${product.spec}" + if (product.abolished) " 已废除" else "", style = MaterialTheme.typography.headlineSmall)
        Text("${residence?.name ?: ""} · ${product.unit}")
        if (math != null && math.active) {
            Text("在手 ${math.onHand.formatQuantity()} ${product.unit}")
            Text("速度 ${formatSpeed(math.forecast.speed, product.unit)}")
            Text("用完 ${formatDepletion(math.forecast.depletion, state.today)}")
            if (math.forecast.depletion is Depletion.Unknown) {
                val names = math.forecast.missing?.let { residentNames(it, state.input.residents) }.orEmpty()
                Text(formatMissingUsage(names))
            }
        }
        LabeledField("快用完天数", low) { low = it }
        LabeledField("快过期天数", expiryDays) { expiryDays = it }
        Text("0 表示只在已经用完，或到期日就是今天及更早时提醒。")
        TextButton(onClick = {
            val lowDays = low.toIntOrNull()
            val expiry = expiryDays.toIntOrNull()
            if (lowDays == null || expiry == null || validateWarnDays(lowDays) != null || validateWarnDays(expiry) != null) {
                return@TextButton
            }
            viewModel.updateProduct(product.copy(lowStockDays = lowDays, expiryWarnDays = expiry))
        }) { Text("保存提醒天数") }
        TextButton(onClick = { editingNames = true }) { Text("改品牌、规格、单位") }
        TextButton(onClick = { viewModel.setProductAbolished(product.id, !product.abolished) }) {
            Text(if (product.abolished) "撤销废除" else "废除")
        }
        TextButton(onClick = {
            confirmText = "将删除这个用品的记账和截图，且不能恢复。"
            confirm = { viewModel.deleteProduct(product.id) { ok -> if (ok) onDeleted() } }
        }) { Text("删除用品") }

        SectionTitle("价格")
        val points = math?.pricePoints.orEmpty().sortedWith(compareBy({ it.date }, { it.purchaseId }))
        PriceChart(points, selected) { id ->
            selected = if (id in selected) selected - id else selected + id
        }
        points.filter { it.purchaseId in selected }.forEach { point ->
            Text("数量 ${point.quantity.formatQuantity()} ${product.unit}")
            Text("优惠 ${formatYuanFromFen(point.discountFen)} 元")
            Text("购买价格 ${formatYuanFromFen(point.priceFen)} 元")
            Text("实付单价 ${formatYuanFromFen(point.unitPriceFen)} 元")
            Text("渠道 ${point.channel}")
            TextButton(onClick = { photosFor = point.purchaseId }) { Text("查看截图") }
        }

        SectionTitle("批次")
        val batches = math?.batches.orEmpty()
        if (batches.isEmpty()) Text("还没有批次。")
        batches.forEach { batch ->
            val source = if (batch.source == BatchSource.PURCHASE) "新购" else "盈余"
            val expiry = batch.expiry?.toString() ?: "无到期日"
            val channel = batch.channel ?: "无渠道"
            Text(
                "$source ${batch.date} 剩余 ${batch.remaining.formatQuantity()} / ${batch.original.formatQuantity()} ${product.unit} · $expiry · $channel",
            )
        }

        SectionTitle("记账")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPurchase) { Text("新购") }
            Button(onClick = onCount) { Text("点数") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConsume) { Text("消耗") }
            Button(onClick = onDiscard) { Text("废弃") }
        }
        state.productPurchases(productId).sortedBy { it.date }.forEach { purchase ->
            val mark = if (purchase.abolished) " 已废除" else ""
            Text("新购 ${purchase.date} ${purchase.quantity.formatQuantity()} ${product.unit}$mark")
            if (math?.counts != null && !purchase.abolished) {
                math.pricePoints.find { it.purchaseId == purchase.id }?.let {
                    Text("实付单价 ${formatYuanFromFen(it.unitPriceFen)} 元")
                }
            }
            TextButton(onClick = { photosFor = purchase.id }) { Text("查看截图") }
            ActionRow(
                onEdit = { onEditPurchase(purchase.id) },
                onToggle = { viewModel.setPurchaseAbolished(purchase.id, !purchase.abolished) },
                toggleLabel = if (purchase.abolished) "撤销废除" else "废除",
                onDelete = {
                    confirmText = "删除后这笔新购和它的截图都会消失。"
                    confirm = { viewModel.deletePurchase(purchase.id) }
                },
            )
        }
        state.productCounts(productId).sortedBy { it.date }.forEach { count ->
            val detail = math?.counts?.get(count.id)
            val mark = if (count.abolished) " 已废除" else ""
            Text("点数 ${count.date} 实盘 ${count.actual.formatQuantity()} ${product.unit}$mark")
            if (detail != null && !count.abolished) {
                Text("账面 ${detail.book.formatQuantity()}，推算消耗 ${detail.inferred.formatQuantity()}，盈余 ${detail.surplus.formatQuantity()}")
            }
            ActionRow(
                onEdit = { onEditCount(count.id) },
                onToggle = { viewModel.setCountAbolished(count.id, !count.abolished) },
                toggleLabel = if (count.abolished) "撤销废除" else "废除",
                onDelete = {
                    confirmText = "删除这次点数后，会按仍生效的记录重算在手。"
                    confirm = { viewModel.deleteCount(count.id) }
                },
            )
        }
        state.productConsumptions(productId).sortedBy { it.date }.forEach { entry ->
            Text("消耗 ${entry.date} ${entry.quantity.formatQuantity()} ${product.unit}" + if (entry.abolished) " 已废除" else "")
            ActionRow(
                onEdit = { onEditConsumption(entry.id) },
                onToggle = { viewModel.setConsumptionAbolished(entry.id, !entry.abolished) },
                toggleLabel = if (entry.abolished) "撤销废除" else "废除",
                onDelete = {
                    confirmText = "删除这笔消耗？"
                    confirm = { viewModel.deleteConsumption(entry.id) }
                },
            )
        }
        state.productDiscards(productId).sortedBy { it.date }.forEach { entry ->
            val reason = entry.reason?.let { " $it" }.orEmpty()
            Text("废弃 ${entry.date} ${entry.quantity.formatQuantity()} ${product.unit}$reason" + if (entry.abolished) " 已废除" else "")
            ActionRow(
                onEdit = { onEditDiscard(entry.id) },
                onToggle = { viewModel.setDiscardAbolished(entry.id, !entry.abolished) },
                toggleLabel = if (entry.abolished) "撤销废除" else "废除",
                onDelete = {
                    confirmText = "删除这笔废弃？"
                    confirm = { viewModel.deleteDiscard(entry.id) }
                },
            )
        }
    }

    if (editingNames) {
        AlertDialog(
            onDismissRequest = { editingNames = false },
            title = { Text("改用品") },
            text = {
                Column {
                    LabeledField("品牌", brand) { brand = it }
                    LabeledField("规格", spec) { spec = it }
                    LabeledField("单位", unit) { unit = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateProduct(product.copy(brand = brand, spec = spec, unit = unit))
                    editingNames = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingNames = false }) { Text("取消") } },
        )
    }
    photosFor?.let { id ->
        val paths = state.photos.filter { it.purchaseId == id }.map { it.relativePath }
        AlertDialog(
            onDismissRequest = { photosFor = null },
            title = { Text("截图") },
            text = {
                Column {
                    if (paths.isEmpty()) {
                        Text("这笔记账没有截图")
                    } else {
                        paths.forEach { path -> PhotoImage(viewModel.photoFile(path)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { photosFor = null }) { Text("关闭") } },
        )
    }
    if (confirm != null) {
        ConfirmDialog(
            title = "确认删除",
            body = confirmText,
            onConfirm = {
                confirm?.invoke()
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun PhotoImage(file: File) {
    if (!file.isFile) {
        Text("截图文件丢失")
        return
    }
    val bitmap = remember(file.path) { BitmapFactory.decodeFile(file.absolutePath) }
    if (bitmap == null) {
        Text("截图文件丢失")
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "购买截图",
            modifier = Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp),
        )
    }
}

@Composable
private fun PriceChart(points: List<PricePoint>, selected: Set<Long>, onToggle: (Long) -> Unit) {
    if (points.isEmpty()) {
        Text("还没有购买记录")
        return
    }
    val color = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val index = nearestPoint(offset, points.size, size.width.toFloat(), size.height.toFloat())
                    onToggle(points[index].purchaseId)
                }
            },
    ) {
        val values = points.map { it.unitPriceFen.num.toFloat() / it.unitPriceFen.den.toFloat() }
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        val span = (max - min).takeIf { it > 0f } ?: 1f
        values.forEachIndexed { index, value ->
            val x = if (points.size == 1) size.width / 2f else size.width * index / (points.size - 1)
            val y = size.height - size.height * ((value - min) / span)
            val chosen = points[index].purchaseId in selected
            drawCircle(
                color = if (chosen) selectedColor else color,
                radius = if (chosen) 14f else 8f,
                center = Offset(x, y),
            )
        }
    }
}

private fun nearestPoint(offset: Offset, count: Int, width: Float, height: Float): Int {
    if (count <= 1) return 0
    var best = 0
    var bestDistance = Float.MAX_VALUE
    for (index in 0 until count) {
        val x = width * index / (count - 1)
        val distance = kotlin.math.abs(offset.x - x)
        if (distance < bestDistance) {
            best = index
            bestDistance = distance
        }
    }
    return best
}
