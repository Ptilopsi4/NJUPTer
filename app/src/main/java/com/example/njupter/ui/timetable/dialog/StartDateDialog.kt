package com.example.njupter.ui.timetable.dialog

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.njupter.R
import com.example.njupter.domain.datePickerMillisToLocalDateMillis
import com.example.njupter.domain.localDateMillisToDatePickerMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartDateDialog(
    initialDateMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = localDateMillisToDatePickerMillis(initialDateMillis)
    )
    
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let {
                    onConfirm(datePickerMillisToLocalDateMillis(it))
                }
            }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Preview(showBackground = true)
@Composable
fun StartDateDialogPreview() {
    MaterialTheme {
        StartDateDialog(
            initialDateMillis = System.currentTimeMillis(),
            onDismiss = {},
            onConfirm = {}
        )
    }
}

