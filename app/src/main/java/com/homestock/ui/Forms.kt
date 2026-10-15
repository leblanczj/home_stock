package com.homestock.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import com.homestock.data.PurchaseDraft
import com.homestock.domain.CountPreview
import com.homestock.domain.Rational
import com.homestock.domain.coveringStay
import com.homestock.domain.formatQuantity
import com.homestock.domain.formatYuanFromFen
import com.homestock.domain.parseQuantity
import com.homestock.domain.parseYuanToFen
import com.homestock.domain.previewCount
import com.homestock.domain.problemMessage
import java.time.LocalDate

@Composable
fun PurchaseForm(
    state: UiState,
    productId: Long?,
    entryId: Long?,
    viewModel: InventoryViewModel,
    onDone: () -> Unit,
) {
    val existing = entryId?.let { id -> state.input.purchases.find { it.id == id } }
    val locked = productId?.let { id -> state.input.products.find { it.id == id } }
        ?: existing?.let { purchase -> state.input.products.find { it.id == purchase.productId } }
    var residenceId by remember {
        mutableStateOf(locked?.residenceId ?: state.residenceId)
    }
    var brand by remember { mutableStateOf(locked?.brand ?: "") }
    var spec by remember { mutableStateOf(locked?.spec ?: "") }
    var unit by remember { mutableStateOf(locked?.unit ?: "") }
    var qty by remember { mutableStateOf(existing?.quantity?.formatQuantity().orEmpty()) }
    var price by remember { mutableStateOf(existing?.let { formatYuanFromFen(it.priceFen) }.orEmpty()) }
    var discount by remember { mutableStateOf(existing?.let { formatYuanFromFen(it.discountFen) }.orEmpty()) }
    var channel by remember { mutableStateOf(existing?.channel ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: state.today) }
    var hasExpiry by remember { mutableStateOf(existing?.expiry != null) }
    var expiry by remember { mutableStateOf(existing?.expiry ?: state.today) }
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var removed by remember { mutableStateOf(setOf<Long>()) }
    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris = it }
    val photos = state.photos.filter { it.purchaseId == existing?.id && it.id !in removed }
    val residences = state.input.residences.filter { !it.abolished || it.id == residenceId }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (existing == null) "新购" else "编辑新购")
        Text("新购不是消耗。同一住所里品牌和规格相同会并入已有用品，单位沿用原来的。")
        if (locked == null) {
            residences.forEach { residence ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = residenceId == residence.id, onClick = { residenceId = residence.id })
                    Text(residence.name)
                }
            }
            LabeledField("品牌", brand) { brand = it }
            LabeledField("规格", spec) { spec = it }
            LabeledField("单位", unit) { unit = it }
        } else {
            Text("${locked.brand} ${locked.spec}（${locked.unit}）")
        }
        LabeledField("数量", qty) { qty = it }
        LabeledField("购买价格（元，优惠前总价）", price) { price = it }
        LabeledField("优惠金额（元）", discount) { discount = it }
        LabeledField("渠道", channel) { channel = it }
        DateField("购买日期", date) { date = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("填写到期日")
            androidx.compose.material3.Switch(checked = hasExpiry, onCheckedChange = { hasExpiry = it })
        }
        if (hasExpiry) DateField("到期日", expiry) { expiry = it }
        val parsedQty = parseQuantity(qty)
        val parsedPrice = parseYuanToFen(price)
        val parsedDiscount = parseYuanToFen(discount)
        if (parsedQty != null && parsedPrice != null && parsedDiscount != null && parsedQty > Rational.ZERO && parsedDiscount <= parsedPrice) {
            Text("实付单价 ${formatYuanFromFen(Rational.of(parsedPrice - parsedDiscount) / parsedQty)} 元")
        }
        photos.forEach { photo ->
            Text(photo.relativePath)
            TextButton(onClick = { removed = removed + photo.id }) { Text("移除这张截图") }
        }
        Text("已选 ${uris.size} 张新截图")
        Button(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }) { Text("添加截图") }
        Button(onClick = {
            val home = residenceId ?: return@Button
            val quantity = parsedQty ?: return@Button
            val priceFen = parsedPrice ?: return@Button
            val discountFen = parsedDiscount ?: return@Button
            viewModel.savePurchase(
                PurchaseDraft(
                    id = existing?.id,
                    productId = locked?.id,
                    residenceId = home,
                    brand = locked?.brand ?: brand,
                    spec = locked?.spec ?: spec,
                    unit = locked?.unit ?: unit,
                    date = date,
                    quantity = quantity,
                    priceFen = priceFen,
                    discountFen = discountFen,
                    channel = channel,
                    expiry = if (hasExpiry) expiry else null,
                    newPhotos = emptyList(),
                    removePhotoIds = removed,
                ),
                uris,
            ) { ok -> if (ok) onDone() }
        }) { Text("保存") }
    }
}

@Composable
fun CountForm(
    state: UiState,
    productId: Long?,
    entryId: Long?,
    viewModel: InventoryViewModel,
    onPurchase: () -> Unit,
    onDone: () -> Unit,
) {
    QuantityEntryForm(
        state = state,
        title = "点数",
        productId = productId,
        entryId = entryId,
        initialQuantity = entryId?.let { id -> state.input.counts.find { it.id == id }?.actual?.formatQuantity() }.orEmpty(),
        initialDate = entryId?.let { id -> state.input.counts.find { it.id == id }?.date } ?: state.today,
        quantityLabel = "实盘数量",
        onPurchase = onPurchase,
        extra = { chosenId, date, quantity ->
            if (chosenId != null && quantity != null) {
                when (val preview = previewCount(state.input, chosenId, date, quantity, entryId)) {
                    is CountPreview.Ready -> {
                        val product = state.input.products.find { it.id == chosenId }
                        val unit = product?.unit ?: ""
                        Text("账面 ${preview.math.book.formatQuantity()} $unit")
                        when {
                            preview.math.inferred > Rational.ZERO ->
                                Text("将记入推算消耗 ${preview.math.inferred.formatQuantity()} $unit。这是实际用掉的数量，会计入用量。")
                            preview.math.surplus > Rational.ZERO ->
                                Text("将记入盈余 ${preview.math.surplus.formatQuantity()} $unit。这不是购买，也不是负的消耗，不会计入用量。")
                            else -> Text("账面和实盘相同，库存不变。")
                        }
                        Text("已经记过的消耗和废弃不会再算一次。")
                    }
                    is CountPreview.Rejected -> Text(problemMessage(preview.problem, state.input))
                }
            }
        },
        onSave = { chosenId, date, quantity, _ ->
            viewModel.saveCount(entryId, chosenId, date, quantity) { ok -> if (ok) onDone() }
        },
    )
}

@Composable
fun ConsumeForm(
    state: UiState,
    productId: Long?,
    entryId: Long?,
    viewModel: InventoryViewModel,
    onPurchase: () -> Unit,
    onDone: () -> Unit,
) {
    QuantityEntryForm(
        state = state,
        title = "消耗",
        productId = productId,
        entryId = entryId,
        initialQuantity = entryId?.let { id -> state.input.consumptions.find { it.id == id }?.quantity?.formatQuantity() }.orEmpty(),
        initialDate = entryId?.let { id -> state.input.consumptions.find { it.id == id }?.date } ?: state.today,
        quantityLabel = "数量",
        onPurchase = onPurchase,
        extra = { chosenId, date, _ ->
            val product = state.input.products.find { it.id == chosenId }
            if (product != null && coveringStay(product.residenceId, state.input, date) == null) {
                Text("这一天没有居住记录，库存会减少，但这笔不会进入用量估计。")
            }
        },
        onSave = { chosenId, date, quantity, _ ->
            viewModel.saveConsumption(entryId, chosenId, date, quantity) { ok -> if (ok) onDone() }
        },
    )
}

@Composable
fun DiscardForm(
    state: UiState,
    productId: Long?,
    entryId: Long?,
    viewModel: InventoryViewModel,
    onPurchase: () -> Unit,
    onDone: () -> Unit,
) {
    val existing = entryId?.let { id -> state.input.discards.find { it.id == id } }
    var reason by remember { mutableStateOf(existing?.reason.orEmpty()) }
    QuantityEntryForm(
        state = state,
        title = "废弃",
        productId = productId,
        entryId = entryId,
        initialQuantity = existing?.quantity?.formatQuantity().orEmpty(),
        initialDate = existing?.date ?: state.today,
        quantityLabel = "数量",
        onPurchase = onPurchase,
        extra = { _, _, _ ->
            LabeledField("原因（可空）", reason) { reason = it }
            Text("废弃会减少库存，但不会计入用量。")
        },
        onSave = { chosenId, date, quantity, _ ->
            viewModel.saveDiscard(entryId, chosenId, date, quantity, reason) { ok -> if (ok) onDone() }
        },
    )
}

@Composable
private fun QuantityEntryForm(
    state: UiState,
    title: String,
    productId: Long?,
    entryId: Long?,
    initialQuantity: String,
    initialDate: LocalDate,
    quantityLabel: String,
    onPurchase: () -> Unit,
    extra: @Composable (Long?, LocalDate, Rational?) -> Unit,
    onSave: (Long, LocalDate, Rational, String) -> Unit,
) {
    val residenceId = productId?.let { id -> state.input.products.find { it.id == id }?.residenceId } ?: state.residenceId
    val products = state.input.products.filter {
        it.residenceId == residenceId && (!it.abolished || it.id == productId)
    }
    var chosen by remember { mutableStateOf(productId ?: products.singleOrNull()?.id) }
    var quantityText by remember { mutableStateOf(initialQuantity) }
    var date by remember { mutableStateOf(initialDate) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title)
        if (products.isEmpty()) {
            EmptyHint("先记一笔新购。", "去新购", onPurchase)
            return
        }
        if (productId == null) {
            products.forEach { product ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = chosen == product.id, onClick = { chosen = product.id })
                    Text("${product.brand} ${product.spec}")
                }
            }
        } else {
            products.find { it.id == productId }?.let { Text("${it.brand} ${it.spec}") }
        }
        LabeledField(quantityLabel, quantityText) { quantityText = it }
        DateField("日期", date) { date = it }
        val quantity = parseQuantity(quantityText)
        extra(chosen, date, quantity)
        Button(onClick = {
            val id = chosen ?: return@Button
            val parsed = quantity ?: return@Button
            onSave(id, date, parsed, "")
        }) { Text(if (entryId == null) "保存" else "保存修改") }
        if (entryId != null) Text("已废除的记录改完之后仍然是废除，要等撤销才重新生效。")
    }
}
