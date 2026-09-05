package com.example.njupter.widget

import android.content.Context
import android.content.res.Configuration
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.example.njupter.R
import com.example.njupter.widget.ui.CoursesWidgetContent
import com.example.njupter.widget.ui.widgetColorProviders

class CourseWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = WidgetDataManager.ensureWidgetState(context)
        // 背景图/透明度按 widget 实例区分，多实例可各自定制
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val bgPath = WidgetSettingsManager.getRenderableBackgroundPath(context, appWidgetId)
        val transparency = WidgetSettingsManager.getBackgroundTransparency(context, appWidgetId)
        val mode = WidgetSettingsManager.getBackgroundMode(context, appWidgetId)
        val showBackgroundImage = mode == WidgetSettingsManager.MODE_IMAGE && bgPath != null
        val solidColorArgb = WidgetSettingsManager.getSolidColor(context, appWidgetId)
        val solidAlpha = WidgetSettingsManager.getSolidAlpha(context, appWidgetId)
        val dayName = context.getString(
            when (state.dayOfWeek) {
                1 -> R.string.day_mon
                2 -> R.string.day_tue
                3 -> R.string.day_wed
                4 -> R.string.day_thu
                5 -> R.string.day_fri
                6 -> R.string.day_sat
                else -> R.string.day_sun
            }
        )
        val isDark = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val colors = widgetColorProviders(context, isDark)

        provideContent {
            CoursesWidgetContent(
                entries = state.entries,
                showBackgroundImage = showBackgroundImage,
                backgroundImagePath = bgPath,
                transparency = transparency,
                solidColorArgb = solidColorArgb,
                solidAlpha = solidAlpha,
                colors = colors,
                headerTitle = context.getString(
                    if (state.isTomorrow) R.string.widget_tomorrow_format else R.string.widget_today_format,
                    dayName
                ),
                weekLabel = state.weekNumber?.let { context.getString(R.string.week, it) }.orEmpty(),
                emptyText = context.getString(
                    if (state.isDayComplete) R.string.widget_courses_completed else R.string.widget_no_courses_today
                ),
                sectionLabel = { start, end ->
                    context.getString(R.string.widget_section_range, start, end)
                }
            )
        }
    }
}

class CourseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CourseWidget()
}
