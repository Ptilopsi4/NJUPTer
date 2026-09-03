// 根据星期和节次查找对应课程
package com.example.njupter.domain

import com.example.njupter.data.CourseInfo
import com.example.njupter.data.CourseSession
import java.util.Calendar
import java.util.TimeZone

// TODO:给定(day, section)，找到对应的课程信息
fun getCourseAt(
    sessions: List<CourseSession>,
    courseMap: Map<String, CourseInfo>,
    day: Int,
    section: Int
): CourseInfo? {

    val session = sessions.find {
        it.day == day && section in it.startSection..it.endSection
    }

    return session?.let { courseMap[it.courseId] }
}

private val dateFormatLocal = ThreadLocal.withInitial {
    java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
}

fun getDateForWeekDay(startDate: Long, week: Int, day: Int): String {
    val targetDate = normalizedWeekStart(startDate).apply {
        add(Calendar.DAY_OF_YEAR, (week - 1) * 7 + (day - 1))
    }
    return dateFormatLocal.get()!!.format(targetDate.time)
}

fun getTodayWeekIndex(startDate: Long, totalWeeks: Int): Int? {
    return getWeekIndexForDate(startDate, totalWeeks, System.currentTimeMillis())
}

fun getWeekIndexForDate(startDate: Long, totalWeeks: Int, dateMillis: Long): Int? {
    val startCalendar = normalizedWeekStart(startDate)
    val targetCalendar = startOfLocalDay(dateMillis)

    val diffDays = localEpochDay(targetCalendar) - localEpochDay(startCalendar)
    if (diffDays < 0) return null

    val weekIndex = (diffDays / 7).toInt()

    return weekIndex.takeIf { it in 0 until totalWeeks }
}

fun getTodayDayOfWeek(): Int {
    return getDayOfWeek(System.currentTimeMillis())
}

fun getDayOfWeek(dateMillis: Long): Int {
    return when (Calendar.getInstance().apply { timeInMillis = dateMillis }.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }
}

fun getMillisForWeekDay(
    startDate: Long,
    week: Int,
    day: Int,
    minuteOfDay: Int
): Long {
    return normalizedWeekStart(startDate).apply {
        add(Calendar.DAY_OF_YEAR, (week - 1) * 7 + (day - 1))
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/**
 * Material 3's DatePicker represents a calendar date as midnight UTC. App timetable dates,
 * however, are interpreted in the device's local time zone. Preserve the local year/month/day
 * when crossing that boundary so early-morning timestamps do not select the previous day.
 */
fun localDateMillisToDatePickerMillis(localDateMillis: Long): Long {
    val localDate = Calendar.getInstance().apply { timeInMillis = localDateMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            localDate.get(Calendar.YEAR),
            localDate.get(Calendar.MONTH),
            localDate.get(Calendar.DAY_OF_MONTH)
        )
    }.timeInMillis
}

/** Converts a DatePicker UTC date back to local midnight for timetable storage and calculations. */
fun datePickerMillisToLocalDateMillis(datePickerMillis: Long): Long {
    val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = datePickerMillis
    }
    return Calendar.getInstance().apply {
        clear()
        set(
            utcDate.get(Calendar.YEAR),
            utcDate.get(Calendar.MONTH),
            utcDate.get(Calendar.DAY_OF_MONTH)
        )
    }.timeInMillis
}

private fun normalizedWeekStart(startDate: Long): Calendar {
    return startOfLocalDay(startDate).apply {
        // Some school calendars store the semester opening Sunday as the week
        // start. The timetable itself is Monday-first, so that Sunday must not
        // be rendered or scheduled as Monday.
        if (get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}

private fun startOfLocalDay(timeMillis: Long): Calendar {
    return Calendar.getInstance().apply {
        this.timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

private fun localEpochDay(calendar: Calendar): Long {
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }.timeInMillis / (24 * 60 * 60 * 1000L)
}
