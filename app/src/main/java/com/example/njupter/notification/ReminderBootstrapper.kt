package com.example.njupter.notification

import android.content.Context
import com.example.njupter.data.LocalFileDataSource
import com.example.njupter.data.SharedPreferencesSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ReminderBootstrapper {
    suspend fun rescheduleCurrentTimetable(context: Context) = withContext(Dispatchers.IO) {
        val settingsRepository = SharedPreferencesSettingsRepository(context)
        val dataSource = LocalFileDataSource(context)
        val timetables = dataSource.getAllTimetables()
        if (timetables.isEmpty()) return@withContext

        val selectedId = settingsRepository.peekLastSelectedTimetableId()
        val meta = timetables.find { it.id == selectedId } ?: timetables.firstOrNull() ?: return@withContext
        val data = dataSource.loadTimetable(meta.id)

        CourseReminderScheduler(
            context,
            settingsRepository
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
