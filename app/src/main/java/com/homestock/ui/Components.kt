package com.homestock.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String = "删除",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$label $date")
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(LocalDate.ofEpochDay(it / 86_400_000L)) }
                    open = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun EmptyHint(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(text)
        if (action != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(action) }
        }
    }
}

@Composable
fun ActionRow(onEdit: (() -> Unit)? = null, onToggle: (() -> Unit)? = null, toggleLabel: String = "", onDelete: () -> Unit) {
    Row {
        if (onEdit != null) TextButton(onClick = onEdit) { Text("编辑") }
        if (onToggle != null) TextButton(onClick = onToggle) { Text(toggleLabel) }
        TextButton(onClick = onDelete) { Text("删除") }
    }
}
