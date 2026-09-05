package com.example.njupter.notification

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.njupter.data.CourseInfo
import com.example.njupter.data.CourseSession
import com.example.njupter.data.SettingsRepository
import com.example.njupter.domain.getMillisForWeekDay

class CourseReminderScheduler(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun scheduleUpcomingReminders(
        courseInfos: List<CourseInfo>,
        sessions: List<CourseSession>,
        currentTimetableId: String?,
        startDate: Long,
        totalWeeks: Int,
        sessionTimes: List<String>
    ) {
        clearAllScheduledReminders()

        if (currentTimetableId.isNullOrBlank()) return
        if (totalWeeks <= 0 || sessionTimes.isEmpty()) return
        if (sessions.isEmpty() || courseInfos.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val courseMap = courseInfos.associateBy { it.id }
        val now = System.currentTimeMillis()
        val horizon = now + SCHEDULE_WINDOW_MILLIS
        val requestCodes = mutableSetOf<Int>()
        val leadMillis = settingsRepository.peekReminderLeadMinutes() * 60_000L

        for (week in 1..totalWeeks) {
            sessions.forEach { session ->
                if (!session.weeks.contains(week)) return@forEach

                val startMinute = getSectionStartMinute(sessionTimes, session.startSection) ?: return@forEach
                val classStartMillis = getMillisForWeekDay(
                    startDate = startDate,
                    week = week,
                    day = session.day,
                    minuteOfDay = startMinute
                )
                val reminderMillis = classStartMillis - leadMillis

                if (reminderMillis <= now || reminderMillis > horizon) return@forEach

                val course = courseMap[session.courseId] ?: return@forEach
                if (!course.reminderEnabled) return@forEach    // 单课提醒开关

                val timeText = buildSessionTimeText(sessionTimes, session.startSection, session.endSection)
                val requestCode = buildRequestCode(currentTimetableId, session.courseId, week, classStartMillis)

                val reminderIntent = Intent(context, CourseReminderReceiver::class.java).apply {
                    putExtra(CourseReminderContract.EXTRA_NOTIFICATION_ID, requestCode)
                    putExtra(CourseReminderContract.EXTRA_COURSE_NAME, course.name)
                    putExtra(CourseReminderContract.EXTRA_TIME_TEXT, timeText)
                    putExtra(CourseReminderContract.EXTRA_CLASSROOM, session.classroom)
                    putExtra(CourseReminderContract.EXTRA_TEACHER, course.teacher)
                    putExtra(CourseReminderContract.EXTRA_LEAD_MINUTES, (leadMillis / 60_000L).toInt())
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    reminderIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                scheduleExactSafely(reminderMillis, pendingIntent)

                requestCodes.add(requestCode)
            }
        }

        prefs.edit {
            putStringSet(KEY_REQUEST_CODES, requestCodes.map { it.toString() }.toSet())
        }
    }

    private fun clearAllScheduledReminders() {
        val requestCodes = prefs.getStringSet(KEY_REQUEST_CODES, emptySet()).orEmpty()
            .mapNotNull { it.toIntOrNull() }

        requestCodes.forEach { requestCode ->
            val intent = Intent(context, CourseReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        prefs.edit { remove(KEY_REQUEST_CODES) }
    }

    private fun getSectionStartMinute(sessionTimes: List<String>, section: Int): Int? {
        if (section !in 1..sessionTimes.size) return null
        val parts = sessionTimes[section - 1].split("-")
        val start = parts.firstOrNull()?.trim().orEmpty()
        return parseMinuteOfDay(start)
    }

    private fun buildSessionTimeText(sessionTimes: List<String>, startSection: Int, endSection: Int): String {
        val startText = getSectionStartText(sessionTimes, startSection)
        val endText = getSectionEndText(sessionTimes, endSection)
        return if (startText != null && endText != null) "$startText-$endText" else ""
    }

    private fun getSectionStartText(sessionTimes: List<String>, section: Int): String? {
        if (section !in 1..sessionTimes.size) return null
        val parts = sessionTimes[section - 1].split("-")
        return parts.firstOrNull()?.trim()
    }

    private fun getSectionEndText(sessionTimes: List<String>, section: Int): String? {
        if (section !in 1..sessionTimes.size) return null
        val parts = sessionTimes[section - 1].split("-")
        return parts.getOrNull(1)?.trim()
    }

    private fun parseMinuteOfDay(timeText: String): Int? {
        val parts = timeText.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    private fun buildRequestCode(
        timetableId: String,
        courseId: String,
        week: Int,
        classStartMillis: Long
    ): Int {
        return ("$timetableId|$courseId|$week|$classStartMillis".hashCode() and 0x7fffffff)
    }

    private fun scheduleExactSafely(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "course_reminder_scheduler"
        private const val KEY_REQUEST_CODES = "request_codes"
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val SCHEDULE_WINDOW_MILLIS = 21 * DAY_MILLIS
    }
}
