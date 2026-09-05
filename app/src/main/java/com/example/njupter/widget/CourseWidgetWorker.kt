package com.example.njupter.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.njupter.notification.ReminderBootstrapper

class CourseWidgetWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            WidgetDataManager.refreshWidget(context)
            // 每日续排：提醒闹钟只排 21 天窗口，靠这条每日链路滚动补排，
            // 否则用户长期不打开 app 后窗口滑出，提醒会静默消失
            ReminderBootstrapper.rescheduleCurrentTimetable(context)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "course_widget_midnight_refresh"
    }
}
