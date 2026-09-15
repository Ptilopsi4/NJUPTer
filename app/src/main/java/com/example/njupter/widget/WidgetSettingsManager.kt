package com.example.njupter.widget

import android.content.Context
import java.io.File

object WidgetSettingsManager {
    private const val PREFS_NAME = "widget_settings"
    private const val KEY_BG_PATH = "background_image_path"
    private const val KEY_TRANSPARENCY = "background_transparency"
    private const val KEY_MODE = "background_mode"
    private const val KEY_SOLID_COLOR = "solid_color"
    private const val KEY_SOLID_ALPHA = "solid_alpha"
    private const val KEY_BG_SCALE = "background_scale"
    private const val KEY_BG_OFFSET_X = "background_offset_x"
    private const val KEY_BG_OFFSET_Y = "background_offset_y"
    // 显式清除的哨兵值：null 表示“未设置（回落到全局）”，"" 表示“该实例已主动清除”
    private const val CLEARED = ""

    const val MODE_SOLID = "solid"
    const val MODE_IMAGE = "image"

    private fun key(base: String, appWidgetId: Int?): String =
        if (appWidgetId == null) base else "${base}_$appWidgetId"

    fun getBackgroundImagePath(context: Context, appWidgetId: Int? = null): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appWidgetId?.let { id ->
            prefs.getString(key(KEY_BG_PATH, id), null)?.let { return it.ifEmpty { null } }
        }
        return prefs.getString(KEY_BG_PATH, null)?.ifEmpty { null }
    }

    /**
     * widget 渲染实际应使用的背景图：KEY_BG_PATH 存的是用户原图（src），
     * 桌面显示的是按拖拽/缩放取景烘焙后的产物（baked）；
     * baked 不存在（老用户/首次保存前）时回落到原图。
     */
    fun getRenderableBackgroundPath(context: Context, appWidgetId: Int? = null): String? {
        val src = getBackgroundImagePath(context, appWidgetId) ?: return null
        val baked = backgroundBakedFile(context, appWidgetId)
        return if (baked.exists()) baked.absolutePath else src
    }

    /** 用户选择的原图（未变换）。 */
    fun backgroundSrcFile(context: Context, appWidgetId: Int?): File =
        File(
            context.filesDir,
            if (appWidgetId == null) "widget_background_src.jpg" else "widget_bg_${appWidgetId}_src.jpg"
        )

    /** 烘焙产物（widget 实际渲染用）。 */
    fun backgroundBakedFile(context: Context, appWidgetId: Int?): File =
        File(
            context.filesDir,
            if (appWidgetId == null) "widget_background.jpg" else "widget_bg_${appWidgetId}.jpg"
        )

    fun getBackgroundScale(context: Context, appWidgetId: Int? = null): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appWidgetId?.let { id ->
            if (prefs.contains(key(KEY_BG_SCALE, id))) {
                return prefs.getFloat(key(KEY_BG_SCALE, id), 1f)
            }
        }
        return prefs.getFloat(KEY_BG_SCALE, 1f)
    }

    fun getBackgroundOffsetX(context: Context, appWidgetId: Int? = null): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appWidgetId?.let { id ->
            if (prefs.contains(key(KEY_BG_OFFSET_X, id))) {
                return prefs.getFloat(key(KEY_BG_OFFSET_X, id), 0f)
            }
        }
        return prefs.getFloat(KEY_BG_OFFSET_X, 0f)
    }

    fun getBackgroundOffsetY(context: Context, appWidgetId: Int? = null): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appWidgetId?.let { id ->
            if (prefs.contains(key(KEY_BG_OFFSET_Y, id))) {
                return prefs.getFloat(key(KEY_BG_OFFSET_Y, id), 0f)
            }
        }
        return prefs.getFloat(KEY_BG_OFFSET_Y, 0f)
    }

    fun setBackgroundTransform(
        context: Context,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        appWidgetId: Int? = null
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val k = { base: String -> key(base, appWidgetId) }
        prefs.edit()
            .putFloat(k(KEY_BG_SCALE), scale)
            .putFloat(k(KEY_BG_OFFSET_X), offsetX)
            .putFloat(k(KEY_BG_OFFSET_Y), offsetY)
            .apply()
    }

    fun clearBackgroundTransform(context: Context, appWidgetId: Int? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val k = { base: String -> key(base, appWidgetId) }
        prefs.edit()
            .remove(k(KEY_BG_SCALE))
            .remove(k(KEY_BG_OFFSET_X))
            .remove(k(KEY_BG_OFFSET_Y))
            .apply()
    }

    fun setBackgroundImagePath(context: Context, path: String?, appWidgetId: Int? = null) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(KEY_BG_PATH, appWidgetId), path ?: CLEARED)
            .apply()
    }

    fun getBackgroundTransparency(context: Context, appWidgetId: Int? = null): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appWidgetId?.let { id ->
            if (prefs.contains(key(KEY_TRANSPARENCY, id))) {
                return prefs.getInt(key(KEY_TRANSPARENCY, id), 128)
            }
        }
        return prefs.getInt(KEY_TRANSPARENCY, 128)
    }

    fun setBackgroundTransparency(context: Context, transparency: Int, appWidgetId: Int? = null) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(key(KEY_TRANSPARENCY, appWidgetId), transparency.coerceIn(0, 255))
            .apply()
    }

    /**
     * 背景模式：纯色（跟随主题或自选色）/ 图片。
     * 历史用户没写过 mode 但选过背景图，默认落回 image 保持原观感。
     */
    fun getBackgroundMode(context: Context, appWidgetId: Int? = null): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appWidgetId?.let { id ->
            prefs.getString(key(KEY_MODE, id), null)?.let { return it }
        }
        val legacy = prefs.getString(KEY_MODE, null)
        if (legacy != null) return legacy
        return if (prefs.getString(KEY_BG_PATH, null) != null) MODE_IMAGE else MODE_SOLID
    }

    fun setBackgroundMode(context: Context, mode: String, appWidgetId: Int? = null) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(KEY_MODE, appWidgetId), mode)
            .apply()
    }

    /** 自选纯色（RGB，不含 alpha）；null = 跟随主题 widgetBackground。 */
    fun getSolidColor(context: Context, appWidgetId: Int? = null): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appWidgetId?.let { id ->
            prefs.getString(key(KEY_SOLID_COLOR, id), null)?.let { return it.toIntOrNull() }
        }
        return prefs.getString(KEY_SOLID_COLOR, null)?.toIntOrNull()
    }

    fun setSolidColor(context: Context, color: Int?, appWidgetId: Int? = null) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(KEY_SOLID_COLOR, appWidgetId), color?.toString() ?: CLEARED)
            .apply()
    }

    fun getSolidAlpha(context: Context, appWidgetId: Int? = null): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appWidgetId?.let { id ->
            if (prefs.contains(key(KEY_SOLID_ALPHA, id))) {
                return prefs.getInt(key(KEY_SOLID_ALPHA, id), 255)
            }
        }
        return prefs.getInt(KEY_SOLID_ALPHA, 255)
    }

    fun setSolidAlpha(context: Context, alpha: Int, appWidgetId: Int? = null) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(key(KEY_SOLID_ALPHA, appWidgetId), alpha.coerceIn(0, 255))
            .apply()
    }
}
