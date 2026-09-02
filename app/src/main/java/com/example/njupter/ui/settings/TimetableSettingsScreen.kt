package com.example.njupter.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.njupter.R
import com.example.njupter.ui.settings.component.SettingsSectionCard
import com.example.njupter.ui.settings.model.SettingsItem
import com.example.njupter.ui.settings.model.SettingsIcon
import com.example.njupter.ui.settings.model.SettingsSection
import com.example.njupter.ui.timetable.dialog.SessionTimeEditorDialog
import com.example.njupter.ui.timetable.dialog.StartDateDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableSettingsScreen(
    currentTimetableName: String,
    currentStartDate: Long,
    currentTotalWeeks: Int,
    currentShowWeekends: Boolean,
    currentShowNonCurrentWeekCourses: Boolean,
    currentSessionTimes: List<String>,
    onBack: () -> Unit,
    onSave: (String, Long, Int, Boolean, Boolean, List<String>) -> Unit
) {
    var name by remember(currentTimetableName) { mutableStateOf(currentTimetableName) }
    var startDate by remember(currentStartDate) { mutableStateOf(currentStartDate) }
    var totalWeeks by remember(currentTotalWeeks) { mutableFloatStateOf(currentTotalWeeks.toFloat()) }
    var showWeekends by remember(currentShowWeekends) { mutableStateOf(currentShowWeekends) }
    var showNonCurrentWeekCourses by remember(currentShowNonCurrentWeekCourses) {
        mutableStateOf(currentShowNonCurrentWeekCourses)
    }
    var sessionTimes by remember(currentSessionTimes) { mutableStateOf(currentSessionTimes) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showSessionTimeEditor by remember { mutableStateOf(false) }

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

    if (showSessionTimeEditor) {
        SessionTimeEditorDialog(
            initialTimes = sessionTimes,
            onDismiss = { showSessionTimeEditor = false },
            onConfirm = {
                sessionTimes = it
                showSessionTimeEditor = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.cur_timetable_settings)) },
            navigationIcon = {
                TextButton(onClick = onBack) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
            },
            actions = {
                TextButton(
                    onClick = {
                        onSave(
                            name.trim(),
                            startDate,
                            totalWeeks.toInt(),
                            showWeekends,
                            showNonCurrentWeekCourses,
                            sessionTimes
                        )
                        onBack()
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text(stringResource(R.string.save_btn))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = TopAppBarDefaults.topAppBarColors().containerColor
            )
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(200))
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsSectionCard(
                    section = SettingsSection(
                        title = stringResource(R.string.current_timetable_settings),
                        items = listOf(
                            SettingsItem.Navigation(
                                icon = SettingsIcon.Vector(Icons.Default.CalendarMonth),
                                title = stringResource(R.string.start_date),
                                value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(startDate)),
                                onClick = { showDatePicker = true }
                            ),
                            SettingsItem.Navigation(
                                icon = SettingsIcon.Vector(Icons.Default.Schedule),
                                title = stringResource(R.string.session_times_label),
                                value = stringResource(R.string.edit),
                                onClick = { showSessionTimeEditor = true }
                            ),
                            SettingsItem.Toggle(
                                icon = SettingsIcon.Vector(Icons.Default.TableRows),
                                title = stringResource(R.string.show_weekends),
                                checked = showWeekends,
                                onToggle = { showWeekends = !showWeekends }
                            ),
                            SettingsItem.Toggle(
                                icon = SettingsIcon.Vector(Icons.Default.Visibility),
                                title = stringResource(R.string.show_non_current_week_courses),
                                description = stringResource(R.string.show_non_current_week_courses_summary),
                                checked = showNonCurrentWeekCourses,
                                onToggle = {
                                    showNonCurrentWeekCourses = !showNonCurrentWeekCourses
                                }
                            )
                        )
                    )
                )
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = stringResource(R.string.timetable_name),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = tween(200))
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(text = stringResource(R.string.total_weeks))
                    Slider(
                        value = totalWeeks,
                        onValueChange = { totalWeeks = it },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = totalWeeks.toInt().toString(),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .animateContentSize(animationSpec = tween(200))
                    )
                }
            }
        }
    }
}

