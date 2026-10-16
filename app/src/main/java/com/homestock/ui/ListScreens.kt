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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homestock.domain.Residence
import com.homestock.domain.Resident
import com.homestock.domain.ResidentSet
import com.homestock.domain.Stay
import com.homestock.domain.expiryRows
import com.homestock.domain.formatDepletion
import com.homestock.domain.formatExpiryDistance
import com.homestock.domain.formatQuantity
import com.homestock.domain.planResidenceDeletion
import com.homestock.domain.residentNames

@Composable
fun ExpiryScreen(state: UiState, onOpenProduct: (Long) -> Unit, contentPadding: PaddingValues) {
    val residenceId = state.residenceId
    val rows = if (residenceId == null) emptyList() else state.projection.expiryRows(residenceId, state.today)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("这里只看到期，默认 3 天，每个用品可以改。") }
        if (residenceId == null) {
            item { Text("先添加住所。") }
        } else if (rows.isEmpty()) {
            item { Text("目前没有快过期的批次。") }
        }
        items(rows, key = { "${it.productId}-${it.expiry}-${it.remaining}" }) { row ->
            Card(Modifier.fillMaxWidth().clickable { onOpenProduct(row.productId) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${row.brand} ${row.spec}")
                    Text("剩余 ${row.remaining.formatQuantity()} ${row.unit}")
                    Text("到期 ${row.expiry} ${formatExpiryDistance(row.daysFromToday)}")
                    Text("渠道 ${row.channel ?: "—"}")
                    Text("${row.warnDays} 天内提醒")
                }
            }
        }
    }
}

@Composable
fun InventoryScreen(state: UiState, onOpenProduct: (Long) -> Unit, contentPadding: PaddingValues) {
    var query by remember { mutableStateOf("") }
    val residenceId = state.residenceId
    val products = state.input.products.filter { it.residenceId == residenceId }
    val shown = products.filter {
        val text = query.trim()
        text.isEmpty() || it.brand.contains(text) || it.spec.contains(text)
    }
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索品牌或规格") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (residenceId == null) {
            Text("先添加住所。", modifier = Modifier.padding(top = 12.dp))
            return
        }
        if (products.isEmpty()) {
            Text("这个住所还没有用品。记一笔新购。", modifier = Modifier.padding(top = 12.dp))
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            items(shown, key = { it.id }) { product ->
                val math = state.projection.math(product.id)
                Card(Modifier.fillMaxWidth().clickable { onOpenProduct(product.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${product.brand} ${product.spec}" + if (product.abolished || math?.active == false) " 已废除" else "")
                        if (math != null && math.active) {
                            Text("在手 ${math.onHand.formatQuantity()} ${product.unit}")
                            Text(formatDepletion(math.forecast.depletion, state.today))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceScreen(
    state: UiState,
    viewModel: InventoryViewModel,
    onEditStay: (Long) -> Unit,
    onAddStay: () -> Unit,
    contentPadding: PaddingValues,
) {
    var residenceName by remember { mutableStateOf("") }
    var residentName by remember { mutableStateOf("") }
    var confirmResidence by remember { mutableStateOf<Long?>(null) }
    var confirmResident by remember { mutableStateOf<Long?>(null) }
    var confirmStay by remember { mutableStateOf<Long?>(null) }
    var renameResidence by remember { mutableStateOf<Residence?>(null) }
    var renameResident by remember { mutableStateOf<Resident?>(null) }
    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("住所")
        if (state.input.residences.isEmpty()) Text("还没有住所。")
        state.input.residences.forEach { residence ->
            Text(residence.name + if (residence.abolished) " 已废除" else "")
            ActionRow(
                onEdit = { renameResidence = residence },
                onToggle = { viewModel.setResidenceAbolished(residence.id, !residence.abolished) },
                toggleLabel = if (residence.abolished) "撤销废除" else "废除",
                onDelete = { confirmResidence = residence.id },
            )
        }
        LabeledField("新住所", residenceName) { residenceName = it }
        TextButton(onClick = {
            viewModel.addResidence(residenceName)
            residenceName = ""
        }) { Text("添加住所") }

        SectionTitle("居住者")
        if (state.input.residents.isEmpty()) Text("还没有居住者。添加居住者后，才能写下谁住在这里。")
        state.input.residents.forEach { resident ->
            val mark = when {
                resident.deleted -> " 已删除"
                resident.abolished -> " 已废除"
                else -> ""
            }
            Text(resident.displayName() + mark)
            if (!resident.deleted) {
                ActionRow(
                    onEdit = { renameResident = resident },
                    onToggle = { viewModel.setResidentAbolished(resident.id, !resident.abolished) },
                    toggleLabel = if (resident.abolished) "撤销废除" else "废除",
                    onDelete = { confirmResident = resident.id },
                )
            }
        }
        LabeledField("新居住者", residentName) { residentName = it }
        TextButton(onClick = {
            viewModel.addResident(residentName)
            residentName = ""
        }) { Text("添加居住者") }

        SectionTitle("居住")
        Text("选完人之后显示人数，这个数字不能改。结束可以是日期，也可以是至今。")
        if (state.input.stays.isEmpty()) Text("还没有居住记录。写下谁在哪一段日子住在这里。")
        state.input.stays.forEach { stay ->
            StayLine(stay, state)
            ActionRow(
                onEdit = { onEditStay(stay.id) },
                onToggle = { viewModel.setStayAbolished(stay.id, !stay.abolished) },
                toggleLabel = if (stay.abolished) "撤销废除" else "废除",
                onDelete = { confirmStay = stay.id },
            )
        }
        TextButton(onClick = onAddStay) { Text("添加居住") }
    }

    confirmResidence?.let { id ->
        val photos = state.photos.count { photo ->
            val purchase = state.input.purchases.find { it.id == photo.purchaseId }
            val product = state.input.products.find { it.id == purchase?.productId }
            product?.residenceId == id
        }
        val plan = planResidenceDeletion(state.input, id, photos)
        if (plan != null) {
            ConfirmDialog(
                title = "删除住所",
                body = plan.message(),
                onConfirm = {
                    viewModel.deleteResidence(id)
                    confirmResidence = null
                },
                onDismiss = { confirmResidence = null },
            )
        }
    }
    confirmResident?.let { id ->
        val resident = state.input.residents.find { it.id == id }
        val used = state.input.stays.any { id in it.residents.ids }
        ConfirmDialog(
            title = "删除居住者",
            body = if (used) {
                "删除后不能再选入新的居住行。已经写下的居住行仍会显示「${resident?.displayName()}」，原来的人员组合保持不变。"
            } else {
                "删除「${resident?.displayName()}」？名单里不会再出现这个人。"
            },
            onConfirm = {
                viewModel.deleteResident(id)
                confirmResident = null
            },
            onDismiss = { confirmResident = null },
        )
    }
    confirmStay?.let { id ->
        ConfirmDialog(
            title = "删除居住记录",
            body = "删除后这条居住记录不再参与用量和用完时间。",
            onConfirm = {
                viewModel.deleteStay(id)
                confirmStay = null
            },
            onDismiss = { confirmStay = null },
        )
    }
    renameResidence?.let { residence ->
        NameDialog("改住所名", residence.name, { viewModel.renameResidence(residence.id, it) }) { renameResidence = null }
    }
    renameResident?.let { resident ->
        NameDialog("改居住者名", resident.name, { viewModel.renameResident(resident.id, it) }) { renameResident = null }
    }
}

@Composable
private fun StayLine(stay: Stay, state: UiState) {
    val home = state.input.residences.find { it.id == stay.residenceId }?.name ?: "住所"
    val people = residentNames(stay.residents, state.input.residents).joinToString("、")
    val end = stay.end?.toString() ?: "至今"
    val mark = if (stay.abolished) " 已废除" else ""
    Text("$home · ${stay.start} 至 $end · $people · ${stay.residents.ids.size} 人$mark")
}

@Composable
private fun NameDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { LabeledField("名字", value) { value = it } },
        confirmButton = {
            TextButton(onClick = {
                onSave(value)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun StayForm(state: UiState, stayId: Long, viewModel: InventoryViewModel, onDone: () -> Unit, contentPadding: PaddingValues) {
    val existing = state.input.stays.find { it.id == stayId }
    var residenceId by remember(stayId, state.residenceId) {
        mutableStateOf(existing?.residenceId ?: state.residenceId ?: state.input.residences.firstOrNull { !it.abolished }?.id)
    }
    var start by remember(stayId) { mutableStateOf(existing?.start ?: state.today) }
    var openEnded by remember(stayId) { mutableStateOf(existing?.end == null && existing != null) }
    var end by remember(stayId) { mutableStateOf(existing?.end ?: state.today) }
    var selected by remember(stayId) { mutableStateOf(existing?.residents?.ids?.toSet() ?: emptySet()) }
    val residences = state.input.residences.filter { !it.abolished || it.id == residenceId }
    val options = state.input.residents.filter { resident ->
        (!resident.deleted && !resident.abolished) || resident.id in (existing?.residents?.ids ?: emptyList())
    }
    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (existing == null) "添加居住" else "编辑居住")
        if (state.input.residents.none { !it.deleted && !it.abolished } && existing == null) {
            Text("先添加居住者。")
            return
        }
        if (residences.isEmpty()) {
            Text("先添加住所。")
            return
        }
        residences.forEach { residence ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.RadioButton(
                    selected = residenceId == residence.id,
                    onClick = { residenceId = residence.id },
                )
                Text(residence.name + if (residence.abolished) " 已废除" else "")
            }
        }
        DateField("开始", start) { start = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("至今")
            Switch(checked = openEnded, onCheckedChange = { openEnded = it })
        }
        if (!openEnded) DateField("结束", end) { end = it }
        Text("选择居住者")
        options.forEach { resident ->
            val lockedOut = (resident.deleted || resident.abolished) && resident.id !in selected && resident.id !in (existing?.residents?.ids ?: emptyList())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = resident.id in selected,
                    onCheckedChange = { checked ->
                        if (!lockedOut) {
                            selected = if (checked) selected + resident.id else selected - resident.id
                        }
                    },
                    enabled = !lockedOut || resident.id in selected,
                )
                Text(resident.displayName() + if (resident.deleted) "（已删除）" else if (resident.abolished) "（已废除）" else "")
            }
        }
        Text("${selected.size} 人")
        TextButton(onClick = {
            val homeId = residenceId ?: return@TextButton
            if (selected.isEmpty()) return@TextButton
            val stay = Stay(
                id = existing?.id ?: 0L,
                residenceId = homeId,
                start = start,
                end = if (openEnded) null else end,
                residents = ResidentSet.of(selected),
                abolished = existing?.abolished ?: false,
            )
            viewModel.saveStay(stay) { ok -> if (ok) onDone() }
        }) { Text("保存") }
        if (selected.isEmpty()) Text("请至少选择一名居住者。")
    }
}
