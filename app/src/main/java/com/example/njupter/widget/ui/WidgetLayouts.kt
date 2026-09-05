package com.example.njupter.widget.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.njupter.R
import com.example.njupter.widget.WidgetCourseEntry

@Composable
fun CoursesWidgetContent(
    entries: List<WidgetCourseEntry>,
    backgroundImagePath: String?,
    transparency: Int,
    colors: ColorProviders,
    headerTitle: String,
    weekLabel: String,
    emptyText: String,
    sectionLabel: (Int, Int) -> String
) {
    val size = LocalSize.current
    val courseColors = WidgetLightColors
    val maxCourses = when {
        size.height < 150.dp -> 1
        size.height < 210.dp -> 2
        size.height < 270.dp -> 3
        else -> 4
    }
    val overlayAlpha = transparency.coerceIn(0, 255)
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    // 解码目标像素尺寸按 widget 实际大小估算，避免每次渲染全尺寸解码大图
    val targetPx = (maxOf(size.width.value, size.height.value) * density).toInt().coerceAtLeast(1)
    val backgroundBitmap = backgroundImagePath?.let { path ->
        decodeSampled(path, targetPx)
    }

    WidgetTheme(colors = colors) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
        ) {
            if (backgroundBitmap != null) {
                Image(
                    provider = ImageProvider(backgroundBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(
                            Color(
                                red = 0,
                                green = 0,
                                blue = 0,
                                alpha = overlayAlpha
                            )
                        )
                ) {}
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                WidgetHeader(headerTitle = headerTitle, weekLabel = weekLabel)
                Spacer(modifier = GlanceModifier.height(8.dp))

                if (entries.isEmpty()) {
                    EmptyWidgetContent(emptyText)
                } else {
                    entries.take(maxCourses).forEachIndexed { index, entry ->
                        CourseRow(
                            entry = entry,
                            courseColor = getColorForIndex(
                                entry.name,
                                entry.colorIndex,
                                courseColors
                            ),
                            sectionText = sectionLabel(entry.startSection, entry.endSection)
                        )
                        if (index != minOf(entries.lastIndex, maxCourses - 1)) {
                            Spacer(modifier = GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 采样解码背景图，使最长边落在 [targetPx] 的 1–2 倍内。
 * OOM 是 Error 不是 Exception，必须捕 Throwable 才能避免崩掉 widget 更新。
 */
private fun decodeSampled(path: String, targetPx: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= targetPx ||
            bounds.outHeight / (sample * 2) >= targetPx
        ) {
            sample *= 2
        }
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (t: Throwable) {
        null
    }
}

@Composable
private fun WidgetHeader(
    headerTitle: String,
    weekLabel: String
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(22.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = GlanceModifier.size(22.dp)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = headerTitle,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
        if (weekLabel.isNotEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = weekLabel,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EmptyWidgetContent(text: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun CourseRow(
    entry: WidgetCourseEntry,
    courseColor: Color,
    sectionText: String
) {
    val (startTime, endTime) = splitTimes(entry.timeText)
    val metadata = buildList {
        add(sectionText)
        if (entry.classroom.isNotBlank()) add(entry.classroom)
        if (entry.teacher.isNotBlank()) add(entry.teacher)
    }.joinToString(" | ")

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(52.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(18.dp)
            .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = GlanceModifier.width(50.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (startTime.isNotEmpty()) {
                Text(
                    text = startTime,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = endTime,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            } else {
                Text(
                    text = sectionText,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = GlanceModifier.width(8.dp))
        Box(
            modifier = GlanceModifier
                .width(5.dp)
                .height(38.dp)
                .background(courseColor)
                .cornerRadius(3.dp)
        ) {}
        Spacer(modifier = GlanceModifier.width(10.dp))

        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Text(
                text = metadata,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

private fun splitTimes(timeText: String): Pair<String, String> {
    val parts = timeText.split("-", limit = 2)
    return if (parts.size == 2 && parts.all { ':' in it }) {
        parts[0] to parts[1]
    } else {
        "" to ""
    }
}
