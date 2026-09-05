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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderLeadDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var input by remember { mutableStateOf(initialMinutes.toString()) }
    val parsed = input.toIntOrNull()
    val valid = parsed != null &&
        parsed in SettingsRepository.MIN_REMINDER_LEAD_MINUTES..SettingsRepository.MAX_REMINDER_LEAD_MINUTES

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
                            selected = parsed == minutes,
                            onClick = { input = minutes.toString() },
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
                    isError = !valid,
                    supportingText = {
                        Text(stringResource(R.string.reminder_lead_range_hint))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(parsed!!) },
                enabled = valid
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private val presetMinutes = listOf(5, 10, 15, 20, 30)

@Preview(showBackground = true)
@Composable
private fun ReminderLeadDialogPreview() {
    NJUPTerTheme {
        ReminderLeadDialog(
            initialMinutes = 10,
            onDismiss = {},
            onConfirm = {}
        )
    }
}