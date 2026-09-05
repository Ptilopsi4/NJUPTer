package com.example.njupter.widget

import android.content.Context
import com.example.njupter.data.CourseSession
import com.example.njupter.data.LocalFileDataSource
import com.example.njupter.data.TimetableData
import com.example.njupter.data.TimetableMetadata
import com.example.njupter.domain.getDayOfWeek
import com.example.njupter.domain.getWeekIndexForDate
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import kotlin.math.max

data class WidgetCourseEntry(
    val name: String,
    val classroom: String,
    val teacher: String,
    val colorIndex: Int,
    val startSection: Int,
    val endSection: Int,
    val timeText: String
)

data class WidgetDisplayState(
    val entries: List<WidgetCourseEntry> = emptyList(),
    val dayOfWeek: Int = 1,
    val weekNumber: Int? = null,
    val isTomorrow: Boolean = false,
    val isDayComplete: Boolean = false,
    val nextRefreshAtMillis: Long? = null
)

object WidgetModels {
    fun computeWidgetDisplayState(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): WidgetDisplayState {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lastId = prefs.getString("last_selected_timetable_id", null)
            ?: return emptyState(nowMillis)
        val dataSource = LocalFileDataSource(context)

        return try {
            runBlocking {
                val metadata = dataSource.getAllTimetables().find { it.id == lastId }
                    ?: return@runBlocking emptyState(nowMillis)
                buildWidgetDisplayState(
                    metadata = metadata,
                    data = dataSource.loadTimetable(lastId),
                    nowMillis = nowMillis
                )
            }
        } catch (_: Exception) {
            emptyState(nowMillis)
        }
    }

    private fun emptyState(nowMillis: Long): WidgetDisplayState {
        return WidgetDisplayState(
            dayOfWeek = getDayOfWeek(nowMillis),
            nextRefreshAtMillis = nextMidnightRefresh(nowMillis)
        )
    }
}

internal fun buildWidgetDisplayState(
    metadata: TimetableMetadata,
    data: TimetableData,
    nowMillis: Long
): WidgetDisplayState {
    val todayWeek = getWeekIndexForDate(metadata.startDate, metadata.totalWeeks, nowMillis)
        ?.plus(1)
    val todayDay = getDayOfWeek(nowMillis)
    val todaySessions = activeSessions(data.sessions, todayDay, todayWeek)
    val latestEndMinute = todaySessions.mapNotNull { session ->
        sectionEndMinute(metadata.nonNullSessionTimes, session.endSection)
    }.maxOrNull()
    val forecastMinute = max(FORECAST_START_MINUTE, latestEndMinute ?: 0)
    val currentMinute = minuteOfDay(nowMillis)
    val shouldForecastTomorrow = currentMinute >= forecastMinute

    if (shouldForecastTomorrow) {
        val tomorrowMillis = addLocalDays(nowMillis, 1)
        val tomorrowWeek = getWeekIndexForDate(
            metadata.startDate,
            metadata.totalWeeks,
            tomorrowMillis
        )?.plus(1)
        val tomorrowDay = getDayOfWeek(tomorrowMillis)
        val tomorrowEntries = courseEntries(
            metadata = metadata,
            data = data,
            sessions = activeSessions(data.sessions, tomorrowDay, tomorrowWeek)
        )

        return WidgetDisplayState(
            entries = tomorrowEntries,
            dayOfWeek = tomorrowDay,
            weekNumber = tomorrowWeek,
            isTomorrow = tomorrowEntries.isNotEmpty(),
            isDayComplete = tomorrowEntries.isEmpty(),
            nextRefreshAtMillis = nextMidnightRefresh(nowMillis)
        )
    }

    return WidgetDisplayState(
        entries = courseEntries(metadata, data, todaySessions),
        dayOfWeek = todayDay,
        weekNumber = todayWeek,
        nextRefreshAtMillis = atMinuteOfLocalDay(nowMillis, forecastMinute)
    )
}

private fun activeSessions(
    sessions: List<CourseSession>,
    day: Int,
    week: Int?
): List<CourseSession> {
    if (week == null) return emptyList()
    return sessions
        .filter { it.day == day && it.weeks.contains(week) }
        .sortedBy { it.startSection }
}

private fun courseEntries(
    metadata: TimetableMetadata,
    data: TimetableData,
    sessions: List<CourseSession>
): List<WidgetCourseEntry> {
    val courseMap = data.courses.associateBy { it.id }
    val sessionTimes = metadata.nonNullSessionTimes
    return sessions.mapNotNull { session ->
        courseMap[session.courseId]?.let { course ->
            val startTime = sessionTimes.getOrNull(session.startSection - 1)
                ?.substringBefore("-")?.trim().orEmpty()
            val endTime = sessionTimes.getOrNull(session.endSection - 1)
                ?.substringAfter("-")?.trim().orEmpty()
            WidgetCourseEntry(
                name = course.name,
                classroom = session.classroom,
                teacher = course.teacher,
                colorIndex = course.colorIndex,
                startSection = session.startSection,
                endSection = session.endSection,
                timeText = if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
                    "$startTime-$endTime"
                } else {
                    "Sec ${session.startSection}-${session.endSection}"
                }
            )
        }
    }
}

private fun sectionEndMinute(sessionTimes: List<String>, section: Int): Int? {
    val endText = sessionTimes.getOrNull(section - 1)?.substringAfter("-", "")?.trim()
        .orEmpty()
    return parseMinuteOfDay(endText)
}

private fun parseMinuteOfDay(text: String): Int? {
    val parts = text.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun minuteOfDay(timeMillis: Long): Int {
    val calendar = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}

private fun atMinuteOfLocalDay(timeMillis: Long, minuteOfDay: Int): Long {
    return Calendar.getInstance().apply {
        this.timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun addLocalDays(timeMillis: Long, days: Int): Long {
    return Calendar.getInstance().apply {
        this.timeInMillis = timeMillis
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis
}

private fun nextMidnightRefresh(timeMillis: Long): Long {
    return Calendar.getInstance().apply {
        this.timeInMillis = timeMillis
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 5)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private const val FORECAST_START_MINUTE = 17 * 60
