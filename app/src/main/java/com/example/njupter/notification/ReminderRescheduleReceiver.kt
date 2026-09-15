package com.example.njupter.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.njupter.widget.WidgetDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val action = intent.action
        // 开机和包替换必须立即执行（开机闹钟已清空/包替换会清空全部闹钟）；
        // TIME_SET/TIMEZONE_CHANGED 在自动校时下可能短时间连发多次，每次全量重排纯属浪费，节流掉
        val force = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!force && isThrottled(appContext)) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderBootstrapper.rescheduleCurrentTimetable(appContext)
                WidgetDataManager.refreshWidget(appContext)
            } catch (_: Exception) {
                // 系统广播里未捕获异常会直接 crash 进程（开机首启尤其致命）；
                // 单次重排失败可接受，后续触发点（启动/开机/改时间/每日续排）会再补
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isThrottled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_RESCHEDULE, 0L) < THROTTLE_MILLIS) return true
        prefs.edit().putLong(KEY_LAST_RESCHEDULE, now).apply()
        return false
    }

    private companion object {
        const val PREFS_NAME = "reminder_reschedule_throttle"
        const val KEY_LAST_RESCHEDULE = "last_reschedule_millis"
        const val THROTTLE_MILLIS = 60_000L
    }
}
