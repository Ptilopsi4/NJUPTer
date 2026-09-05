package com.example.njupter.notification

import android.content.Context
import com.example.njupter.data.LocalFileDataSource
import com.example.njupter.data.SharedPreferencesSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ReminderBootstrapper {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_LAST_SELECTED_TIMETABLE_ID = "last_selected_timetable_id"

    suspend fun rescheduleCurrentTimetable(context: Context) = withContext(Dispatchers.IO) {
        val dataSource = LocalFileDataSource(context)
        val timetables = dataSource.getAllTimetables()
        if (timetables.isEmpty()) return@withContext

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedId = prefs.getString(KEY_LAST_SELECTED_TIMETABLE_ID, null)
        val meta = timetables.find { it.id == selectedId } ?: timetables.firstOrNull() ?: return@withContext
        val data = dataSource.loadTimetable(meta.id)

        CourseReminderScheduler(
            context,
            SharedPreferencesSettingsRepository(context)
        ).scheduleUpcomingReminders(
            courseInfos = data.courses,
            sessions = data.sessions,
            currentTimetableId = meta.id,
            startDate = meta.startDate,
            totalWeeks = meta.totalWeeks,
            sessionTimes = meta.nonNullSessionTimes
        )
    }
}
