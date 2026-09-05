package com.example.njupter.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetDataManager {
    private const val PREFS_NAME = "widget_courses"
    private const val KEY_DATA = "today_courses_json"
    private const val KEY_DISPLAY_STATE = "display_state_json"
    private val gson = Gson()

    fun saveWidgetState(context: Context, state: WidgetDisplayState) {
        val json = gson.toJson(state)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISPLAY_STATE, json)
            .apply()
    }

    fun loadWidgetState(context: Context): WidgetDisplayState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_DISPLAY_STATE, null)?.let { json ->
            try {
                return gson.fromJson(json, WidgetDisplayState::class.java)
            } catch (_: Exception) {
            }
        }

        // Preserve the previous cached entry while upgrading from the old
        // list-only state format.
        val legacyJson = prefs.getString(KEY_DATA, null) ?: return WidgetDisplayState()
        return try {
            val type = object : TypeToken<List<WidgetCourseEntry>>() {}.type
            WidgetDisplayState(entries = gson.fromJson(legacyJson, type))
        } catch (_: Exception) {
            WidgetDisplayState()
        }
    }

    /**
     * 首次添加小组件但 app 尚未启动过时，缓存为空，provideGlance 只能拿到默认状态。
     * 这里在缓存缺失时现算一次并续排闹钟，避免表头错星期、且无人调度刷新。
     */
    suspend fun ensureWidgetState(context: Context): WidgetDisplayState = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_DISPLAY_STATE)) {
            loadWidgetState(context)
        } else {
            val state = WidgetModels.computeWidgetDisplayState(context)
            saveWidgetState(context, state)
            state.nextRefreshAtMillis?.let {
                WidgetUpdateScheduler.scheduleRefresh(context, it)
            }
            state
        }
    }

    /**
     * 重算 widget 状态并按需刷新。
     * 计算/读盘部分下沉到 IO 线程（调用方可能在主线程）；
     * 可视内容（课程/星期/周数等）未变时只补写 nextRefresh 时间戳，不重组 Glance。
     * 可见性比较必须先抹掉 nextRefreshAtMillis，否则时间戳差异会被误判为内容变化。
     */
    suspend fun refreshWidget(context: Context) = withContext(Dispatchers.IO) {
        val newState = WidgetModels.computeWidgetDisplayState(context)
        val previous = loadWidgetState(context)
        try {
            val visibleChanged =
                previous.copy(nextRefreshAtMillis = null) != newState.copy(nextRefreshAtMillis = null)
            when {
                visibleChanged -> {
                    saveWidgetState(context, newState)
                    CourseWidget().updateAll(context)
                }
                previous.nextRefreshAtMillis != newState.nextRefreshAtMillis ->
                    saveWidgetState(context, newState)
            }
        } catch (_: Exception) {
        }
        newState.nextRefreshAtMillis?.let { triggerAtMillis ->
            WidgetUpdateScheduler.scheduleRefresh(context, triggerAtMillis)
        }
    }
}
