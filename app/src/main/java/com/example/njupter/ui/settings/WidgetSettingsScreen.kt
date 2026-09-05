package com.example.njupter.ui.settings

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.njupter.R
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.njupter.widget.CourseWidget
import com.example.njupter.widget.CourseWidgetReceiver
import com.example.njupter.widget.WidgetCourseEntry
import com.example.njupter.widget.WidgetPinResultReceiver
import com.example.njupter.widget.WidgetDataManager
import com.example.njupter.widget.WidgetDisplayState
import com.example.njupter.widget.WidgetSettingsManager
import com.example.njupter.widget.ui.WidgetDarkColors
import com.example.njupter.widget.ui.WidgetLightColors
import com.example.njupter.widget.ui.getColorForIndex
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "WidgetSettings"
private const val PIN_RESULT_REQUEST_CODE = 2003
private const val MAX_BG_DIMENSION = 1600
// 手势取景烘焙：裁剪窗口目标长边/输出上限
private const val BAKE_TARGET_EDGE = 1080
private const val BAKE_MAX_EDGE = 1600

// 纯色模式预设色板：亮/暗主题下都可读的中性色 + 课程常用色系
private val SOLID_SWATCHES: List<Int> = listOf(
    0xFFFFFFFF, 0xFFECEFF1, 0xFFF5FBF0, 0xFF101418, 0xFF1E2B23, 0xFF37474F
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 背景图/透明度按 widget 实例区分；多实例时顶部出选择器，单实例直接编辑，
    // 桌面没放实例时（编辑全局兜底配置）selectedWidgetId 保持 null
    var widgetIds by remember { mutableStateOf<List<Int>>(emptyList()) }
    var selectedWidgetId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        // getGlanceIds 依赖系统 binder，异常不应炸掉设置页；退回空列表即编辑全局兜底配置
        try {
            val manager = GlanceAppWidgetManager(context)
            widgetIds = manager.getGlanceIds(CourseWidget::class.java)
                .map { manager.getAppWidgetId(it) }
                .sorted()
        } catch (e: Exception) {
            Log.w(TAG, "getGlanceIds failed", e)
            widgetIds = emptyList()
        }
        selectedWidgetId = widgetIds.singleOrNull()
    }
    var backgroundPath by remember(selectedWidgetId) {
        mutableStateOf(WidgetSettingsManager.getBackgroundImagePath(context, selectedWidgetId))
    }
    var transparency by remember(selectedWidgetId) {
        mutableFloatStateOf(
            WidgetSettingsManager.getBackgroundTransparency(context, selectedWidgetId) / 255f
        )
    }
    var mode by remember(selectedWidgetId) {
        mutableStateOf(WidgetSettingsManager.getBackgroundMode(context, selectedWidgetId))
    }
    var solidColorArgb by remember(selectedWidgetId) {
        mutableStateOf(WidgetSettingsManager.getSolidColor(context, selectedWidgetId))
    }
    var solidAlpha by remember(selectedWidgetId) {
        mutableIntStateOf(WidgetSettingsManager.getSolidAlpha(context, selectedWidgetId))
    }
    val canPin = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.getSystemService(AppWidgetManager::class.java)
                ?.isRequestPinAppWidgetSupported == true
    }
    // 背景图取景（编辑帧像素坐标）：拖拽/双指缩放后 debounce 烘焙进输出图
    var bgScale by remember(selectedWidgetId, backgroundPath) {
        mutableFloatStateOf(WidgetSettingsManager.getBackgroundScale(context, selectedWidgetId))
    }
    var bgOffset by remember(selectedWidgetId, backgroundPath) {
        mutableStateOf(
            Offset(
                WidgetSettingsManager.getBackgroundOffsetX(context, selectedWidgetId),
                WidgetSettingsManager.getBackgroundOffsetY(context, selectedWidgetId)
            )
        )
    }
    var bgFramePx by remember { mutableStateOf(IntSize.Zero) }
    var bgTransformDirty by remember(selectedWidgetId) { mutableStateOf(false) }
    // 预览用烘焙产物；烘焙完成后 bump 让预览重新解码
    var bakeEpoch by remember { mutableIntStateOf(0) }
    var showPinGuide by remember { mutableStateOf(false) }

    // 预览缩略图：优先展示烘焙后的取景产物（与桌面渲染一致），无产物时回落原图
    val previewBitmap = remember(backgroundPath, selectedWidgetId, bakeEpoch) {
        val bakedFile = WidgetSettingsManager.backgroundBakedFile(context, selectedWidgetId)
        (if (bakedFile.exists()) bakedFile.absolutePath else backgroundPath)
            ?.let { decodeSampled(it, 1080) }
    }
    // 编辑器用原图（src），保证拖拽/缩放有完整画幅

    // 编辑器用原图（src），保证拖拽/缩放有完整画幅
    val bgBitmap = remember(backgroundPath) {
        backgroundPath?.let { decodeSampled(it, 1080) }
    }

    // 取景变更后 debounce 烘焙：连续拖拽只落盘最后一次取景
    LaunchedEffect(bgScale, bgOffset, bgFramePx, backgroundPath) {
        if (!bgTransformDirty || bgBitmap == null || bgFramePx == IntSize.Zero || backgroundPath == null) {
            return@LaunchedEffect
        }
        delay(350)
        val baked = withContext(Dispatchers.IO) {
            bakeBackgroundImage(context, selectedWidgetId, bgFramePx, bgScale, bgOffset)
        }
        if (baked) bakeEpoch++
        WidgetSettingsManager.setBackgroundTransform(
            context, bgScale, bgOffset.x, bgOffset.y, selectedWidgetId
        )
        WidgetDataManager.refreshWidget(context)
        bgTransformDirty = false
    }

    // 真实课表数据驱动的预览，与桌面小组件渲染逻辑同源
    val widgetState = remember { WidgetDataManager.loadWidgetState(context) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val savedPath = saveBackgroundImage(context, uri, selectedWidgetId)
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
            if (widgetIds.size > 1) {
                Text(
                    text = stringResource(R.string.widget_instances),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    widgetIds.forEachIndexed { index, id ->
                        FilterChip(
                            selected = id == selectedWidgetId,
                            onClick = { selectedWidgetId = id },
                            label = { Text("${index + 1}") }
                        )
                    }
                }
            }

            // 引导添加：先发起系统 pin 请求（launcher 决定是否弹确认窗），
            // 部分桌面（如 vivo OriginOS）会静默忽略，所以无论结果都弹手动引导兑底
            if (canPin) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                                    CourseWidgetReceiver::class.java,
                                    CourseWidget(),
                                    null,
                                    PendingIntent.getBroadcast(
                                        context,
                                        PIN_RESULT_REQUEST_CODE,
                                        Intent(context, WidgetPinResultReceiver::class.java),
                                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                    )
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "requestPinGlanceAppWidget failed", e)
                            }
                        }
                        showPinGuide = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.widget_pin_to_home))
                }
            }

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
                    showImage = mode == WidgetSettingsManager.MODE_IMAGE,
                    bgBitmap = previewBitmap,
                    transparency = transparency,
                    solidColorArgb = solidColorArgb,
                    solidAlpha = solidAlpha / 255f,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Background section
            Text(
                text = stringResource(R.string.widget_background),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = mode == WidgetSettingsManager.MODE_SOLID,
                    onClick = {
                        mode = WidgetSettingsManager.MODE_SOLID
                        WidgetSettingsManager.setBackgroundMode(
                            context, WidgetSettingsManager.MODE_SOLID, selectedWidgetId
                        )
                        scope.launch { WidgetDataManager.refreshWidget(context) }
                    },
                    label = { Text(stringResource(R.string.widget_mode_solid)) }
                )
                FilterChip(
                    selected = mode == WidgetSettingsManager.MODE_IMAGE,
                    onClick = {
                        mode = WidgetSettingsManager.MODE_IMAGE
                        WidgetSettingsManager.setBackgroundMode(
                            context, WidgetSettingsManager.MODE_IMAGE, selectedWidgetId
                        )
                        scope.launch { WidgetDataManager.refreshWidget(context) }
                    },
                    label = { Text(stringResource(R.string.widget_mode_image)) }
                )
            }

            if (mode == WidgetSettingsManager.MODE_SOLID) {
                Text(
                    text = stringResource(R.string.widget_solid_color),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                FilterChip(
                    selected = solidColorArgb == null,
                    onClick = {
                        solidColorArgb = null
                        WidgetSettingsManager.setSolidColor(context, null, selectedWidgetId)
                        scope.launch { WidgetDataManager.refreshWidget(context) }
                    },
                    label = { Text(stringResource(R.string.widget_solid_follow_theme)) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SOLID_SWATCHES.forEach { swatch ->
                        val selected = swatch == solidColorArgb
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(swatch))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable {
                                    solidColorArgb = swatch
                                    WidgetSettingsManager.setSolidColor(context, swatch, selectedWidgetId)
                                    scope.launch { WidgetDataManager.refreshWidget(context) }
                                }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.widget_transparency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${(solidAlpha / 255f * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                Slider(
                    value = solidAlpha / 255f,
                    onValueChange = { newValue ->
                        solidAlpha = (newValue * 255).toInt().coerceIn(0, 255)
                        WidgetSettingsManager.setSolidAlpha(context, solidAlpha, selectedWidgetId)
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
            } else {
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
                                deleteBackgroundImage(context, selectedWidgetId)
                                backgroundPath = null
                                WidgetSettingsManager.setBackgroundImagePath(context, null, selectedWidgetId)
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
                    // 取景编辑器：cover 基准 + 围绕帧中心缩放平移，与烘焙几何模型一致
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .onSizeChanged { bgFramePx = it }
                            .pointerInput(bgBitmap, selectedWidgetId) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val frame = bgFramePx
                                    val bmp = bgBitmap
                                    if (frame != IntSize.Zero && bmp != null) {
                                        val base = maxOf(
                                            frame.width.toFloat() / bmp.width,
                                            frame.height.toFloat() / bmp.height
                                        )
                                        val newScale = (bgScale * zoom).coerceIn(1f, 5f)
                                        // 取景必须始终盖满帧：平移范围由缩放后的溢出量决定
                                        val maxX =
                                            ((bmp.width * base * newScale - frame.width) / 2f)
                                                .coerceAtLeast(0f)
                                        val maxY =
                                            ((bmp.height * base * newScale - frame.height) / 2f)
                                                .coerceAtLeast(0f)
                                        bgOffset = Offset(
                                            (bgOffset.x + pan.x).coerceIn(-maxX, maxX),
                                            (bgOffset.y + pan.y).coerceIn(-maxY, maxY)
                                        )
                                        bgScale = newScale
                                        bgTransformDirty = true
                                    }
                                }
                            }
                    ) {
                        Image(
                            bitmap = bgBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = bgScale
                                    scaleY = bgScale
                                    translationX = bgOffset.x
                                    translationY = bgOffset.y
                                },
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            text = stringResource(R.string.widget_bg_edit_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.widget_background_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                        WidgetSettingsManager.setBackgroundTransparency(context, alphaInt, selectedWidgetId)
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
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // 兑底引导：launcher 静默忽略 pin 请求时（如 OriginOS），指引用户手动添加
    if (showPinGuide) {
        AlertDialog(
            onDismissRequest = { showPinGuide = false },
            title = { Text(stringResource(R.string.widget_pin_to_home)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.widget_pin_guide_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.widget_pin_guide_steps),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPinGuide = false }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}

@Composable
private fun WidgetPreview(
    widgetState: WidgetDisplayState,
    showImage: Boolean,
    bgBitmap: Bitmap?,
    transparency: Float,
    solidColorArgb: Int?,
    solidAlpha: Float,
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

    val solidBg = solidColorArgb?.let {
        Color(
            red = (it shr 16) and 0xFF,
            green = (it shr 8) and 0xFF,
            blue = it and 0xFF,
            alpha = (solidAlpha * 255).toInt().coerceIn(0, 255)
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(solidBg ?: MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (showImage && bgBitmap != null) {
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
                if (widgetState.entries.size > 2) {
                    Text(
                        text = stringResource(
                            R.string.widget_more_courses,
                            widgetState.entries.size - 2
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
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
private fun saveBackgroundImage(
    context: android.content.Context,
    uri: Uri,
    appWidgetId: Int?
): String? {
    val tempFile = File(context.cacheDir, "widget_bg_pick_tmp")
    try {
        val resolver = context.contentResolver
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: run {
            Log.w(TAG, "openInputStream returned null: $uri")
            return null
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tempFile.absolutePath, bounds)
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
        val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, options)
            ?: run {
                Log.w(TAG, "sampled decode failed: ${bounds.outWidth}x${bounds.outHeight} sample=$sample")
                return null
            }

        // KEY_BG_PATH 存原图（src），baked 先与 src 一致（初始取景=居中 cover），
        // 后续拖拽/缩放再重烘焙 baked
        val srcFile = WidgetSettingsManager.backgroundSrcFile(context, appWidgetId)
        FileOutputStream(srcFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        }
        val bakedFile = WidgetSettingsManager.backgroundBakedFile(context, appWidgetId)
        FileOutputStream(bakedFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        }
        WidgetSettingsManager.setBackgroundTransform(context, 1f, 0f, 0f, appWidgetId)
        bitmap.recycle()
        return srcFile.absolutePath
    } catch (e: Exception) {
        Log.w(TAG, "saveBackgroundImage failed", e)
        return null
    } finally {
        tempFile.delete()
    }
}

private fun deleteBackgroundImage(context: android.content.Context, appWidgetId: Int?) {
    try {
        WidgetSettingsManager.backgroundSrcFile(context, appWidgetId).delete()
        WidgetSettingsManager.backgroundBakedFile(context, appWidgetId).delete()
        WidgetSettingsManager.clearBackgroundTransform(context, appWidgetId)
    } catch (_: Exception) {
    }
}

/**
 * 把编辑器取景（缩放/位移，帧像素坐标）烘焙进 baked 输出图：
 * RemoteViews 无法对 ImageView 做矩阵变换，只能落盘裁剪。
 * 几何模型与编辑器一致：源图以 cover 基准充满编辑帧，围绕帧中心缩放平移。
 */
private fun bakeBackgroundImage(
    context: android.content.Context,
    appWidgetId: Int?,
    framePx: IntSize,
    scale: Float,
    offset: Offset
): Boolean {
    return try {
        val src = WidgetSettingsManager.backgroundSrcFile(context, appWidgetId)
        val out = WidgetSettingsManager.backgroundBakedFile(context, appWidgetId)
        // 老用户只有旧输出图没有 src：把它迁移为 src，保证后续可重新取景
        if (!src.exists()) {
            val stored = WidgetSettingsManager.getBackgroundImagePath(context, appWidgetId)
                ?.let { File(it) }
            if (stored?.exists() == true) {
                stored.copyTo(src, overwrite = true)
            } else {
                return false
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        val w0 = bounds.outWidth.toFloat()
        val h0 = bounds.outHeight.toFloat()
        if (w0 <= 0f || h0 <= 0f || framePx == IntSize.Zero) return false
        val eff = maxOf(framePx.width / w0, framePx.height / h0) * scale
        val cropW = (framePx.width / eff).coerceAtMost(w0)
        val cropH = (framePx.height / eff).coerceAtMost(h0)
        val cropX = (w0 / 2f - (framePx.width / 2f + offset.x) / eff).coerceIn(0f, w0 - cropW)
        val cropY = (h0 / 2f - (framePx.height / 2f + offset.y) / eff).coerceIn(0f, h0 - cropH)

        var sample = 1
        while (maxOf(cropW, cropH) / (sample * 2f) >= BAKE_TARGET_EDGE) sample *= 2
        val full = BitmapFactory.decodeFile(
            src.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return false
        val inv = 1f / sample
        val x = (cropX * inv).toInt().coerceIn(0, full.width - 1)
        val y = (cropY * inv).toInt().coerceIn(0, full.height - 1)
        val cw = minOf(full.width - x, (cropW * inv).toInt().coerceAtLeast(1))
        val ch = minOf(full.height - y, (cropH * inv).toInt().coerceAtLeast(1))
        val cropped = Bitmap.createBitmap(full, x, y, cw, ch)
        val result = if (maxOf(cropped.width, cropped.height) > BAKE_MAX_EDGE) {
            val ratio = BAKE_MAX_EDGE.toFloat() / maxOf(cropped.width, cropped.height)
            Bitmap.createScaledBitmap(
                cropped,
                (cropped.width * ratio).toInt().coerceAtLeast(1),
                (cropped.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            cropped
        }
        if (result !== cropped) cropped.recycle()
        full.recycle()
        FileOutputStream(out).use { output ->
            result.compress(Bitmap.CompressFormat.JPEG, 85, output)
        }
        result.recycle()
        true
    } catch (t: Throwable) {
        Log.w(TAG, "bakeBackgroundImage failed", t)
        false
    }
}
