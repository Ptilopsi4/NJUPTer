package com.example.njupter.ui.settings.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.njupter.R
import com.example.njupter.data.CourseInfo
import com.example.njupter.data.CourseSession
import com.example.njupter.domain.import.TimetableImportMatcher
import com.example.njupter.ui.theme.NJUPTerTheme
import com.example.njupter.ui.timetable.dialog.StartDateDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ImportPreviewDialog(
    importResult: TimetableImportMatcher.ImportResult,
    onConfirm: (String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultName = stringResource(R.string.imported_timetable_name)
    var name by remember(defaultName) { mutableStateOf(defaultName) }
    var startDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        StartDateDialog(
            initialDateMillis = startDate,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                startDate = it
                showDatePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.import_preview))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = importResult.summary)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.new_timetable_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 开学日期决定周次计算，单双周课程的显示随它而定
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                OutlinedTextField(
                    value = dateFormat.format(Date(startDate)),
                    onValueChange = { },
                    label = { Text(stringResource(R.string.start_date)) },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    colors = TextFieldDefaults.colors(
                        disabledContainerColor = Color.Transparent,
                        disabledIndicatorColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(text = stringResource(R.string.import_desc))
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, startDate) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.create_and_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ImportPreviewDialogPreview() {
    NJUPTerTheme {
        ImportPreviewDialog(
            importResult = TimetableImportMatcher.ImportResult(
                newCourses = listOf(
                    CourseInfo(
                        id = "preview-course-1",
                        name = "Advanced Mathematics",
                        teacher = "Dr. Smith",
                        colorIndex = 0
                    )
                ),
                newSessions = listOf(
                    CourseSession(
                        courseId = "preview-course-1",
                        day = 1,
                        startSection = 1,
                        endSection = 2,
                        weeks = listOf(1, 2, 3),
                        classroom = "A101"
                    )
                ),
                summary = "Found 1 new course and 1 new session."
            ),
            onConfirm = { _, _ -> },
            onDismiss = {}
        )
    }
}
