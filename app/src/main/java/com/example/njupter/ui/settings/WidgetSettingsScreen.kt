package com.example.njupter.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.njupter.R
import com.example.njupter.widget.WidgetCourseEntry
import com.example.njupter.widget.WidgetDataManager
import com.example.njupter.widget.WidgetDisplayState
import com.example.njupter.widget.WidgetSettingsManager
import com.example.njupter.widget.ui.WidgetDarkColors
import com.example.njupter.widget.ui.WidgetLightColors
import com.example.njupter.widget.ui.getColorForIndex
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

private const val TAG = "WidgetSettings"
private const val WIDGET_BG_FILE = "widget_background.jpg"
private const val WIDGET_BG_SRC_FILE = "widget_bg_source"
private const val MAX_BG_DIMENSION = 1600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backgroundPath by remember { mutableStateOf(WidgetSettingsManager.getBackgroundImagePath(context)) }
    var transparency by remember { mutableFloatStateOf(WidgetSettingsManager.getBackgroundTransparency(context) / 255f) }

    // 预览缩略图共用一份采样解码结果，避免对同一张图重复全尺寸解码
    val bgBitmap = remember(backgroundPath) {
        backgroundPath?.let { decodeSampled(it, 1080) }
    }

    // 真实课表数据驱动的预览，与桌面小组件渲染逻辑同源
    val widgetState = remember { WidgetDataManager.loadWidgetState(context) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val savedPath = saveBackgroundImage(context, uri)
        if (savedPath == null) {
            Toast.makeText(context, R.string.widget_pick_image_failed, Toast.LENGTH_SHORT).show()
        } else {
            backgroundPath = savedPath
            WidgetSettingsManager.setBackgroundImagePath(context, savedPath)
            scope.launch { WidgetDataManager.refreshWidget(context) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preview section
            Text(
                text = stringResource(R.string.widget_preview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                WidgetPreview(
                    widgetState = widgetState,
                    bgBitmap = bgBitmap,
                    transparency = transparency,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Background image section
            Text(
                text = stringResource(R.string.widget_background),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.widget_pick_image))
                }

                if (backgroundPath != null) {
                    Button(
                        onClick = {
                            deleteBackgroundImage(context)
                            backgroundPath = null
                            WidgetSettingsManager.setBackgroundImagePath(context, null)
                            scope.launch { WidgetDataManager.refreshWidget(context) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.widget_remove_background))
                    }
                }
            }

            if (bgBitmap != null) {
                Image(
                    bitmap = bgBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = stringResource(R.string.widget_background_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Transparency section
            Text(
                text = stringResource(R.string.widget_transparency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${(transparency * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Slider(
                value = transparency,
                onValueChange = { newValue ->
                    transparency = newValue
                    val alphaInt = (newValue * 255).toInt().coerceIn(0, 255)
                    WidgetSettingsManager.setBackgroundTransparency(context, alphaInt)
                },
                onValueChangeFinished = {
                    scope.launch { WidgetDataManager.refreshWidget(context) }
                }
            )

            Text(
                text = stringResource(R.string.widget_transparency_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WidgetPreview(
    widgetState: WidgetDisplayState,
    bgBitmap: Bitmap?,
    transparency: Float,
    modifier: Modifier = Modifier
) {
    val dayName = stringResource(
        when (widgetState.dayOfWeek) {
            1 -> R.string.day_mon
            2 -> R.string.day_tue
            3 -> R.string.day_wed
            4 -> R.string.day_thu
            5 -> R.string.day_fri
            6 -> R.string.day_sat
            else -> R.string.day_sun
        }
    )
    val headerTitle = stringResource(
        if (widgetState.isTomorrow) R.string.widget_tomorrow_format else R.string.widget_today_format,
        dayName
    )
    val weekLabel = widgetState.weekNumber?.let { stringResource(R.string.week, it) }.orEmpty()
    val emptyText = when {
        widgetState.entries.isNotEmpty() -> null
        widgetState.weekNumber == null -> stringResource(R.string.widget_preview_no_data)
        widgetState.isDayComplete -> stringResource(R.string.widget_courses_completed)
        else -> stringResource(R.string.widget_no_courses_today)
    }
    val courseColors = if (isSystemInDarkTheme()) WidgetDarkColors else WidgetLightColors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (bgBitmap != null) {
            Image(
                bitmap = bgBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = transparency))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = headerTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (weekLabel.isNotEmpty()) {
                    Text(
                        text = weekLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            if (emptyText != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 与小组件一致：180dp 高度只显示前两条
                widgetState.entries.take(2).forEach { entry ->
                    PreviewCourseRow(
                        entry = entry,
                        color = getColorForIndex(entry.name, entry.colorIndex, courseColors),
                        sectionLabel = stringResource(
                            R.string.widget_section_range,
                            entry.startSection,
                            entry.endSection
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCourseRow(
    entry: WidgetCourseEntry,
    color: Color,
    sectionLabel: String
) {
    val metadata = buildList {
        add(sectionLabel)
        if (entry.classroom.isNotBlank()) add(entry.classroom)
        if (entry.teacher.isNotBlank()) add(entry.teacher)
    }.joinToString(" | ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (startTime, endTime) = splitTimes(entry.timeText)
        if (startTime.isNotEmpty()) {
            Column(modifier = Modifier.width(44.dp)) {
                Text(
                    text = startTime,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = endTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        } else {
            Column(modifier = Modifier.width(44.dp)) {
                Text(
                    text = sectionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(34.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = metadata,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * 解码一张采样后的位图，最长边限制在 [maxDimension] 内，避免大图直接解码导致 OOM。
 */
private fun decodeSampled(path: String, maxDimension: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= maxDimension ||
            bounds.outHeight / (sample * 2) >= maxDimension
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, options)
    } catch (_: Exception) {
        null
    }
}

/**
 * 将所选图片解码（带采样，限制最长边）后压缩为 JPEG 存储。
 * 失败返回 null，调用方负责提示且不改动现有设置。
 *
 * 先把 URI 流拷贝到临时文件再从文件解码：部分相册云图/一次性流 provider
 * 不支持二次 openInputStream，原实现读两次会静默失败。
 */
private fun saveBackgroundImage(context: android.content.Context, uri: Uri): String? {
    val srcFile = File(context.cacheDir, WIDGET_BG_SRC_FILE)
    try {
        val resolver = context.contentResolver
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(srcFile).use { output -> input.copyTo(output) }
        } ?: run {
            Log.w(TAG, "openInputStream returned null: $uri")
            return null
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(srcFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "unsupported image format: $uri mime=${resolver.getType(uri)}")
            return null
        }

        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= MAX_BG_DIMENSION ||
            bounds.outHeight / (sample * 2) >= MAX_BG_DIMENSION
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(srcFile.absolutePath, options)
            ?: run {
                Log.w(TAG, "sampled decode failed: ${bounds.outWidth}x${bounds.outHeight} sample=$sample")
                return null
            }

        val file = File(context.filesDir, WIDGET_BG_FILE)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        }
        bitmap.recycle()
        return file.absolutePath
    } catch (e: Exception) {
        Log.w(TAG, "saveBackgroundImage failed", e)
        return null
    } finally {
        srcFile.delete()
    }
}

private fun deleteBackgroundImage(context: android.content.Context) {
    try {
        File(context.filesDir, WIDGET_BG_FILE).delete()
    } catch (_: Exception) {
    }
}
