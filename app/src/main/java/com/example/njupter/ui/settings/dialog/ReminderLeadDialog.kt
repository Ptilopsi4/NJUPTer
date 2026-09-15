package com.example.njupter.ui.settings.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.njupter.R
import com.example.njupter.data.SettingsRepository
import com.example.njupter.ui.theme.NJUPTerTheme

/**
 * 多段提前量：预设 chip 多选（可同时选"60 分钟"+"10 分钟"两类提醒），
 * 输入框补充 0–120 内的任意自定义值；全部取消选择即关闭提醒。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderLeadDialog(
    initialMinutes: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit
) {
    var selected by remember { mutableStateOf(initialMinutes.toSet()) }
    var input by remember { mutableStateOf("") }
    val parsed = input.toIntOrNull()
    val outOfRange = parsed != null &&
        (parsed < SettingsRepository.MIN_REMINDER_LEAD_MINUTES ||
            parsed > SettingsRepository.MAX_REMINDER_LEAD_MINUTES)
    val addable = parsed != null && !outOfRange && parsed !in selected

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_lead_time)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.reminder_lead_time_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetMinutes.forEach { minutes ->
                        FilterChip(
                            selected = minutes in selected,
                            onClick = {
                                selected = if (minutes in selected) selected - minutes else selected + minutes
                            },
                            label = { Text(stringResource(R.string.reminder_lead_min_value, minutes)) }
                        )
                    }
                    // 自定义值不在预设内：以选中态列出，点击移除
                    selected.filterNot { it in presetMinutes }.sorted().forEach { minutes ->
                        FilterChip(
                            selected = true,
                            onClick = { selected = selected - minutes },
                            label = { Text(stringResource(R.string.reminder_lead_min_value, minutes)) }
                        )
                    }
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { newValue ->
                        if (newValue.length <= 3 && newValue.all { it.isDigit() }) input = newValue
                    },
                    label = { Text(stringResource(R.string.reminder_lead_minutes_label)) },
                    suffix = { Text(stringResource(R.string.minutes_unit)) },
                    singleLine = true,
                    isError = outOfRange,
                    supportingText = {
                        Text(
                            if (selected.isEmpty()) stringResource(R.string.reminder_lead_empty_hint)
                            else stringResource(R.string.reminder_lead_range_hint)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (addable) {
                    TextButton(onClick = {
                        selected = selected + (parsed ?: 0)
                        input = ""
                    }) { Text(stringResource(R.string.reminder_lead_add)) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected.toList()) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private val presetMinutes = listOf(0, 5, 10, 15, 30, 60)

@Preview(showBackground = true)
@Composable
private fun ReminderLeadDialogPreview() {
    NJUPTerTheme {
        ReminderLeadDialog(
            initialMinutes = listOf(10, 60),
            onDismiss = {},
            onConfirm = {}
        )
    }
}
