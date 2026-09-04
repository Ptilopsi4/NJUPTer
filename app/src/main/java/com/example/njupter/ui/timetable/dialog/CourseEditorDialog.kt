package com.example.njupter.ui.timetable.dialog

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.njupter.ui.animation.pressScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.njupter.data.CourseInfo
import com.example.njupter.data.CourseSession
import com.example.njupter.domain.validation.CourseValidator
import com.example.njupter.domain.validation.ValidationError
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import java.util.UUID
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.example.njupter.R
import android.content.Context
import com.example.njupter.ui.theme.getCourseColors

private fun ValidationError.toLocalizedString(context: Context): String {
    return when (this) {
        is ValidationError.StartAfterEnd -> context.getString(R.string.error_start_after_end, start, end)
        is ValidationError.NoWeekSelected -> context.getString(R.string.error_no_week)
        is ValidationError.TimeConflict -> context.getString(R.string.error_time_conflict, day, startSection, endSection)
        is ValidationError.CourseDuplicate -> context.getString(R.string.error_course_duplicate, name, teacher)
    }
}

@Composable
fun CourseEditorDialog(
    initialSession: CourseSession?,
    initialCourse: CourseInfo?,
    existingCourses: List<CourseInfo>,
    existingSessions: List<CourseSession>,
    colorsList: List<Color>,
    isDarkTheme: Boolean,
    totalWeeks: Int,
    maxSection: Int = 12,
    initialDay: Int = 1,
    initialStartSection: Int = 1,
    initialEndSection: Int = 2,
    initialWeeks: Set<Int> = (1..totalWeeks).toSet(),
    onDismiss: () -> Unit,
    onSave: (CourseInfo, CourseSession, Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    // Info
    var courseId by remember { mutableStateOf(initialCourse?.id ?: UUID.randomUUID().toString()) }
    var courseName by remember { mutableStateOf(initialCourse?.name ?: "") }
    var teacher by remember { mutableStateOf(initialCourse?.teacher ?: "") }
    var classroom by remember(initialSession) { mutableStateOf(initialSession?.classroom ?: "") }
    var note by remember { mutableStateOf(initialCourse?.note ?: "") }
    var attendanceType by remember { mutableStateOf(initialCourse?.attendanceType ?: "") }
    var selectedColorIndex by remember { mutableStateOf(initialCourse?.colorIndex ?: -1) }

    // Session
    var day by remember(initialSession, initialDay) {
        mutableStateOf(initialSession?.day ?: initialDay)
    }
    var startSection by remember(initialSession, initialStartSection) {
        mutableStateOf((initialSession?.startSection ?: initialStartSection).toString())
    }
    var endSection by remember(initialSession, initialEndSection) {
        mutableStateOf((initialSession?.endSection ?: initialEndSection).toString())
    }

    val unavailableWeeks = remember(
        day,
        startSection,
        endSection,
        initialSession,
        existingSessions
    ) {
        val start = startSection.toIntOrNull()
        val end = endSection.toIntOrNull()
        if (start == null || end == null || start > end) {
            emptySet()
        } else {
            CourseValidator.unavailableWeeksForSession(
                day = day,
                start = start,
                end = end,
                editingSession = initialSession,
                allSessions = existingSessions
            ).filterTo(mutableSetOf()) { it in 1..totalWeeks }
        }
    }

    // Weeks
    var selectedWeeks by remember(initialSession, initialWeeks, totalWeeks) {
        val requestedWeeks = initialSession?.weeks?.toSet() ?: initialWeeks
        mutableStateOf<Set<Int>>(
            requestedWeeks.filterTo(mutableSetOf()) { it in 1..totalWeeks }
        )
    }
    var automaticallyRemovedWeeks by remember(initialSession, initialWeeks, totalWeeks) {
        mutableStateOf<Set<Int>>(emptySet())
    }
    var showCustomWeekDialog by remember { mutableStateOf(false) }

    LaunchedEffect(unavailableWeeks) {
        val newlyUnavailable = selectedWeeks intersect unavailableWeeks
        val newlyAvailable = automaticallyRemovedWeeks - unavailableWeeks
        selectedWeeks = (selectedWeeks - unavailableWeeks) + newlyAvailable
        automaticallyRemovedWeeks =
            (automaticallyRemovedWeeks + newlyUnavailable) intersect unavailableWeeks
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 描边颜色
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryColor = MaterialTheme.colorScheme.primary

    if (showCustomWeekDialog) {
        CustomWeekPickerDialog(
            totalWeeks = totalWeeks,
            initialWeeks = selectedWeeks,
            disabledWeeks = unavailableWeeks,
            onDismiss = { showCustomWeekDialog = false },
            onConfirm = {
                selectedWeeks = it
                // Confirming the picker is an explicit user choice. Do not restore weeks that
                // were hidden by a previous, temporary time conflict after this point.
                automaticallyRemovedWeeks = emptySet()
                showCustomWeekDialog = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSession == null) stringResource(R.string.add_course) else stringResource(R.string.edit_course)) },
        text = {
            CourseEditorForm(
                courseName = courseName,
                onCourseNameChange = { courseName = it; errorMessage = null },
                teacher = teacher,
                onTeacherChange = { teacher = it },
                classroom = classroom,
                onClassroomChange = { classroom = it },
                attendanceType = attendanceType,
                onAttendanceTypeChange = { attendanceType = it },
                note = note,
                onNoteChange = { note = it },
                selectedColorIndex = selectedColorIndex,
                onColorSelect = { selectedColorIndex = it },
                colorsList = colorsList,
                isDarkTheme = isDarkTheme,
                maxSection = maxSection,
                day = day,
                onDayChange = { day = it },
                startSection = startSection,
                onStartSectionChange = { if (it.all { c -> c.isDigit() }) startSection = it },
                endSection = endSection,
                onEndSectionChange = { if (it.all { c -> c.isDigit() }) endSection = it },
                selectedWeeks = selectedWeeks,
                showCustomWeekDialog = showCustomWeekDialog,
                onCustomWeekClick = { showCustomWeekDialog = true },
                errorMessage = errorMessage
            )
        },
        confirmButton = {
            Button(onClick = {
                val d = day
                val s = startSection.toIntOrNull() ?: 1
                val e = endSection.toIntOrNull() ?: s
                val weeksList = selectedWeeks.sorted().toList()

                // 1. Time Conflict + Basic Logic (start <= end, no empty weeks)
                // Use the new Validator
                val session = CourseSession(
                    courseId = courseId, // Temporary for check, or null if strictly checking by value
                    day = d,
                    startSection = s,
                    endSection = e,
                    weeks = weeksList
                )
                
                // We pass 'initialSession' as the editing session to ignore self-conflicts
                val timeError = CourseValidator.validateSessionInput(
                    day = d,
                    start = s,
                    end = e,
                    weeks = weeksList,
                    editingSession = initialSession, 
                    allSessions = existingSessions
                )

                if (timeError != null) {
                    errorMessage = timeError.toLocalizedString(context)
                    return@Button
                }

                // 2. Course Duplication Check
                val duplicationError = CourseValidator.validateCourseDuplication(
                    currentId = courseId,
                    name = courseName,
                    teacher = teacher,
                    existingCourses = existingCourses
                )

                if (duplicationError != null) {
                    errorMessage = duplicationError.toLocalizedString(context)
                    return@Button
                }

                // 3. Construct and Callback
                val info = CourseInfo(
                    id = courseId,
                    name = courseName,
                    teacher = teacher,
                    colorIndex = selectedColorIndex,
                    credit = initialCourse?.credit.orEmpty(),
                    courseNature = initialCourse?.courseNature.orEmpty(),
                    note = note.trim(),
                    attendanceType = attendanceType.trim()
                )
                // Re-create session with final values
                val finalSession = CourseSession(
                    courseId = courseId,
                    day = d,
                    startSection = s,
                    endSection = e,
                    weeks = weeksList,
                    classroom = classroom.trim()
                )
                
                onSave(info, finalSession, initialCourse == null)
            }) { Text(stringResource(R.string.save_btn)) }
        },
        dismissButton = {
            if (initialSession != null) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseEditorForm(
    courseName: String,
    onCourseNameChange: (String) -> Unit,
    teacher: String,
    onTeacherChange: (String) -> Unit,
    classroom: String,
    onClassroomChange: (String) -> Unit,
    attendanceType: String,
    onAttendanceTypeChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    selectedColorIndex: Int,
    onColorSelect: (Int) -> Unit,
    colorsList: List<Color>,
    isDarkTheme: Boolean,
    day: Int,
    maxSection: Int,
    onDayChange: (Int) -> Unit,
    startSection: String,
    onStartSectionChange: (String) -> Unit,
    endSection: String,
    onEndSectionChange: (String) -> Unit,
    selectedWeeks: Set<Int>,
    showCustomWeekDialog: Boolean,
    onCustomWeekClick: () -> Unit,
    errorMessage: String?
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryColor = MaterialTheme.colorScheme.primary
    val scrollState = rememberScrollState()

    // 出错时滚动到底部，让用户看到错误信息
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(16)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(scrollState)
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    )
    // Details
    {
        Text(
            stringResource(R.string.course_details),
            style = MaterialTheme.typography.titleSmall,
            color = primaryColor
        )
        OutlinedTextField(
            value = courseName,
            onValueChange = { onCourseNameChange(it) },
            label = { Text(stringResource(R.string.course_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = classroom,
                onValueChange = { onClassroomChange(it) },
                label = { Text(stringResource(R.string.classroom)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            OutlinedTextField(
                value = teacher,
                onValueChange = { onTeacherChange(it) },
                label = { Text(stringResource(R.string.teacher)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        OutlinedTextField(
            value = attendanceType,
            onValueChange = { onAttendanceTypeChange(it) },
            label = { Text(stringResource(R.string.attendance_type)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = note,
            onValueChange = { onNoteChange(it) },
            label = { Text(stringResource(R.string.note)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
            maxLines = 4
        )

        HorizontalDivider(Modifier.padding(10.dp, vertical = 10.dp))

        // --- Time & Week Settings ---
        Text(
            stringResource(R.string.time_settings),
            style = MaterialTheme.typography.titleSmall,
            color = primaryColor
        )

        // 星期
        Text(stringResource(R.string.day_of_week), style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val days = listOf("M", "T", "W", "T", "F", "S", "S")
            days.forEachIndexed { index, label ->
                val dayNum = index + 1
                val isSelected = (day == dayNum)
                val dayInteractionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(if (isSelected) primaryColor else Color.Transparent)
                        .pressScale(dayInteractionSource)
                        .border(
                            1.dp,
                            if (isSelected) primaryColor else outlineColor,
                            CircleShape
                        )
                        .clickable(
                            interactionSource = dayInteractionSource,
                            onClick = { onDayChange(dayNum) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 节次
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.start_sec), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.section_range, startSection, endSection),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
            Text(stringResource(R.string.end_sec), style = MaterialTheme.typography.bodySmall)
        }
        val startSecNum = (startSection.toIntOrNull() ?: 1).coerceIn(1, maxSection)
        val endSecNum = (endSection.toIntOrNull() ?: startSecNum).coerceIn(startSecNum, maxSection)
        val currentSectionRange = startSecNum.toFloat()..endSecNum.toFloat()
        RangeSlider(
            value = currentSectionRange,
            onValueChange = { range: ClosedFloatingPointRange<Float> ->
                onStartSectionChange(range.start.roundToInt().toString())
                onEndSectionChange(range.endInclusive.roundToInt().toString())
            },
            valueRange = 1f..maxSection.toFloat(),
            steps = (maxSection - 2).coerceAtLeast(0)
        )

        // 周次选择
        Text(
            stringResource(R.string.weeks, if (selectedWeeks.isEmpty()) stringResource(R.string.weeks_none) else stringResource(R.string.weeks_selected, selectedWeeks.size)),
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SuggestionChip(
                onClick = { onCustomWeekClick() },
                label = { Text(stringResource(R.string.customize)) },
                icon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = if (showCustomWeekDialog) SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) else SuggestionChipDefaults.suggestionChipColors()
            )
        }

        HorizontalDivider(Modifier.padding(10.dp, vertical = 1.dp))

        // Card Color
        Text(
            stringResource(R.string.card_color),
            style = MaterialTheme.typography.titleSmall,
            color = primaryColor
        )

        Column {
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth()
            ) {
                val isAutoSelected = (selectedColorIndex == -1)
                val autoBorderColor = if (isAutoSelected) primaryColor else outlineColor
                val autoBorderWidth = if (isAutoSelected) 2.dp else 1.dp
                val autoColorInteractionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .pressScale(autoColorInteractionSource)
                        .border(autoBorderWidth, autoBorderColor, CircleShape)
                        .clickable(
                            interactionSource = autoColorInteractionSource,
                            onClick = { onColorSelect(-1) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.auto),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color.Black
                    )
                }
                colorsList.forEachIndexed { index, color ->
                    val isSelected = (selectedColorIndex == index)
                    val borderWidth = if (isSelected) 2.dp else 1.dp
                    val borderColor = if (isSelected) primaryColor else outlineColor
                    val colorInteractionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color)
                            .pressScale(colorInteractionSource)
                            .border(borderWidth, borderColor, CircleShape)
                            .clickable(
                                interactionSource = colorInteractionSource,
                                onClick = { onColorSelect(index) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.cd_selected),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomWeekPickerDialog(
    totalWeeks: Int,
    initialWeeks: Set<Int>,
    disabledWeeks: Set<Int> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (Set<Int>) -> Unit
) {
    val availableWeeks = remember(totalWeeks, disabledWeeks) {
        (1..totalWeeks).filterNotTo(mutableSetOf()) { it in disabledWeeks }
    }
    var tempWeeks by remember(initialWeeks, disabledWeeks) {
        mutableStateOf(initialWeeks intersect availableWeeks)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_weeks)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tempWeeks.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text(   //TODO:M3强调效果
                            text = stringResource(R.string.at_least_one_week),
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                WeekGrid(
                    totalWeeks = totalWeeks,
                    selectedWeeks = tempWeeks,
                    disabledWeeks = disabledWeeks,
                    onWeekToggle = { weekNum ->
                        if (weekNum in disabledWeeks) return@WeekGrid
                        tempWeeks = if (tempWeeks.contains(weekNum)) {
                            tempWeeks - weekNum
                        } else {
                            tempWeeks + weekNum
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 0.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val buttonModifier = Modifier.weight(1f)
                        val buttonPadding = PaddingValues(horizontal = 2.dp)

                        TextButton(
                            onClick = { tempWeeks = availableWeeks },
                            modifier = buttonModifier,
                            contentPadding = buttonPadding
                        ) {
                            Text(stringResource(R.string.select_all), maxLines = 1)
                        }
                        TextButton(
                            onClick = {
                                tempWeeks = availableWeeks.filterTo(mutableSetOf()) { it % 2 == 1 }
                            },
                            modifier = buttonModifier,
                            contentPadding = buttonPadding
                        ) {
                            Text(stringResource(R.string.odd_week), maxLines = 1)
                        }
                        TextButton(
                            onClick = {
                                tempWeeks = availableWeeks.filterTo(mutableSetOf()) { it % 2 == 0 }
                            },
                            modifier = buttonModifier,
                            contentPadding = buttonPadding
                        ) {
                            Text(stringResource(R.string.even_week), maxLines = 1)
                        }
                    }
                    TextButton(
                        onClick = { tempWeeks = emptySet() },
                        modifier = Modifier.fillMaxWidth(1f / 3)

                    ) {
                        Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(tempWeeks) },
                enabled = tempWeeks.isNotEmpty()
            ) {
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

@Composable
fun WeekGrid(
    totalWeeks: Int,
    selectedWeeks: Set<Int>,
    disabledWeeks: Set<Int> = emptySet(),
    onWeekToggle: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 48.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(totalWeeks) { index ->
            val weekNum = index + 1
            val isSelected = selectedWeeks.contains(weekNum)
            val isEnabled = weekNum !in disabledWeeks

            val backgroundColor = when {
                !isEnabled -> MaterialTheme.colorScheme.surfaceContainerLow
                isSelected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = when {
                !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                isSelected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val gridInteractionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .pressScale(gridInteractionSource)
                    .clickable(
                        interactionSource = gridInteractionSource,
                        enabled = isEnabled,
                        onClick = { onWeekToggle(weekNum) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = weekNum.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CourseEditorFormPreview() {
    MaterialTheme {
        CourseEditorForm(
            courseName = "高等数学",
            onCourseNameChange = {},
            teacher = "张老师",
            onTeacherChange = {},
            classroom = "教2-101",
            onClassroomChange = {},
            attendanceType = "点名",
            onAttendanceTypeChange = {},
            note = "",
            onNoteChange = {},
            selectedColorIndex = 0,
            onColorSelect = {},
            colorsList = getCourseColors(),
            isDarkTheme = false,
            maxSection = 12,
            day = 1,
            onDayChange = {},
            startSection = "1",
            onStartSectionChange = {},
            endSection = "2",
            onEndSectionChange = {},
            selectedWeeks = (1..20).toSet(),
            showCustomWeekDialog = false,
            onCustomWeekClick = {},
            errorMessage = null
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CustomWeekPickerDialogPreview() {
    MaterialTheme {
        CustomWeekPickerDialog(
            totalWeeks = 20,
            initialWeeks = setOf(1, 2, 3, 4, 5),
            onDismiss = {},
            onConfirm = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WeekGridPreview() {
    MaterialTheme {
        WeekGrid(
            totalWeeks = 20,
            selectedWeeks = setOf(1, 3, 5, 7),
            onWeekToggle = {}
        )
    }
}
