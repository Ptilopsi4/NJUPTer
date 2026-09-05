package com.example.njupter.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.njupter.MainActivity
import com.example.njupter.R

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(CourseReminderContract.EXTRA_NOTIFICATION_ID, 0)
        val courseName = intent.getStringExtra(CourseReminderContract.EXTRA_COURSE_NAME).orEmpty()
        val timeText = intent.getStringExtra(CourseReminderContract.EXTRA_TIME_TEXT).orEmpty()
        val classroom = intent.getStringExtra(CourseReminderContract.EXTRA_CLASSROOM).orEmpty()
        val teacher = intent.getStringExtra(CourseReminderContract.EXTRA_TEACHER).orEmpty()
        val leadMinutes = intent.getIntExtra(CourseReminderContract.EXTRA_LEAD_MINUTES, 10)

        ensureChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = context.getString(
            R.string.course_reminder_content,
            courseName,
            timeText,
            classroom,
            teacher
        )

        val notification = NotificationCompat.Builder(context, CourseReminderContract.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(
                context.getString(R.string.course_reminder_title, leadMinutes)
            )
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(launchPendingIntent)
            .build()

        if (!canPostNotifications(context)) return

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission state may change between check and notify.
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CourseReminderContract.CHANNEL_ID,
            context.getString(R.string.course_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.course_reminder_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
