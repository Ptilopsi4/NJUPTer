package com.example.njupter.ui.timetable.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.njupter.R
import com.example.njupter.data.CourseInfo
import com.example.njupter.data.CourseSession
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailsBottomSheet(
    course: CourseInfo,
    session: CourseSession,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.course_details),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = course.name.ifBlank { stringResource(R.string.unnamed_course) },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            CourseDetailRow(
                label = stringResource(R.string.teacher),
                value = course.teacher.ifBlank { stringResource(R.string.not_set) }
            )
            CourseDetailRow(
                label = stringResource(R.string.classroom),
                value = session.classroom.ifBlank { stringResource(R.string.not_set) }
            )
            CourseDetailRow(
                label = stringResource(R.string.course_credit),
                value = course.credit.ifBlank { stringResource(R.string.not_set) }
            )
            CourseDetailRow(
                label = stringResource(R.string.course_nature),
                value = course.courseNature.ifBlank { stringResource(R.string.not_set) }
            )
            CourseDetailRow(
                label = stringResource(R.string.attendance_type),
                value = course.attendanceType.ifBlank { stringResource(R.string.not_set) }
            )
            if (course.note.isNotBlank()) {
                CourseDetailRow(
                    label = stringResource(R.string.note),
                    value = course.note
                )
            }
            CourseDetailRow(
                label = stringResource(R.string.weeks),
                value = formatWeekRanges(session.weeks)
                    .ifBlank { stringResource(R.string.weeks_none) }
            )

            Spacer(Modifier.height(2.dp))
            Button(
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) onEdit()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Text(
                    text = stringResource(R.string.edit),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun CourseDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.34f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(0.66f)
        )
    }
}

internal fun formatWeekRanges(weeks: List<Int>): String {
    val sorted = weeks.distinct().sorted()
    if (sorted.isEmpty()) return ""

    val ranges = mutableListOf<String>()
    var start = sorted.first()
    var end = start
    sorted.drop(1).forEach { week ->
        if (week == end + 1) {
            end = week
        } else {
            ranges += if (start == end) "$start" else "$start–$end"
            start = week
            end = week
        }
    }
    ranges += if (start == end) "$start" else "$start–$end"
    return ranges.joinToString(", ")
}
