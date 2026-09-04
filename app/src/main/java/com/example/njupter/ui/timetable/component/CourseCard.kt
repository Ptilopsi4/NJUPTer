package com.example.njupter.ui.timetable.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.njupter.data.CourseInfo
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

/**
 * 课程格。文本走绘制层：卡片在 pager 里按周重复出现，同一课程的文本与约束跨页
 * 完全一致，共享的 TextMeasurer 能直接命中 layout 缓存，省掉每页 26 个 Text 节点
 * 的 StaticLayout 构建（探针实测：文本占滑动途中 p50 约 3ms，结构本身近零成本）。
 * 语义由 contentDescription 承担，命中测试仍由整卡 clickable 负责。
 */
@Composable
fun CourseCard(
    course: CourseInfo,
    colorsList: List<Color>,
    isActiveInCurrentWeek: Boolean = true,
    autoColorIndex: Int? = null,
    textMeasurer: TextMeasurer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorIndex = remember(course, colorsList, autoColorIndex) {
        if (course.colorIndex in colorsList.indices) {
            course.colorIndex
        } else if (autoColorIndex != null && autoColorIndex in colorsList.indices) {
            autoColorIndex
        } else {
            if (colorsList.isNotEmpty()) (course.name.hashCode() and Int.MAX_VALUE) % colorsList.size else 0
        }
    }

    val fallbackColor = MaterialTheme.colorScheme.primaryContainer
    val activeBackgroundColor = remember(colorIndex, colorsList, fallbackColor) {
        if (colorsList.isNotEmpty()) colorsList[colorIndex] else fallbackColor
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = when {
        !isActiveInCurrentWeek -> MaterialTheme.colorScheme.surfaceVariant
        isPressed -> activeBackgroundColor.copy(alpha = 0.72f)
        else -> activeBackgroundColor
    }
    val contentColor = if (isActiveInCurrentWeek) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    }

    // 组合期构建 style：drawWithCache 闭包里没有 CompositionLocal 可读
    val nameStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    val roomStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        textAlign = TextAlign.Center
    )

    val cardShape = RoundedCornerShape(8.dp)

    // 不用 Card：它附带 elevation 阴影与 Surface 节点；课表卡片是平面格。
    // 且反馈一律不做几何动画：pressScale / 波纹会在卡片重叠区逐帧改变面积，
    // 而卡片在 CourseDayColumn 里是比例定位、彼此可以压盖的。
    Box(
        modifier = modifier
            .padding(1.dp)
            .clip(cardShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics {
                if (course.classroom.isEmpty()) {
                    contentDescription = course.name
                } else {
                    contentDescription = "${course.name} ${course.classroom}"
                }
            }
            // 文本可用区：与原 Column(padding 4dp) 对齐，且让 DrawScope.size 即文本可用尺寸
            .padding(4.dp)
            .drawWithCache {
                val textSpace = size.width.roundToInt()
                val nameLayout = textMeasurer.measure(
                    text = AnnotatedString(course.name),
                    style = nameStyle,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = textSpace)
                )
                val roomLayout = if (course.classroom.isEmpty()) {
                    null
                } else {
                    textMeasurer.measure(
                        text = AnnotatedString("@${course.classroom}"),
                        style = roomStyle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        constraints = Constraints(maxWidth = textSpace)
                    )
                }
                val gap = 2.dp.toPx()
                val totalHeight = nameLayout.size.height +
                    (roomLayout?.size?.height?.plus(gap) ?: 0f)
                val startY = ((size.height - totalHeight) / 2f).coerceAtLeast(0f)
                val roomY = startY + nameLayout.size.height + gap

                onDrawBehind {
                    drawText(nameLayout, color = contentColor)
                    roomLayout?.let { drawText(it, color = contentColor, topLeft = Offset(0f, roomY)) }
                }
            }
    )
}

@Preview(showBackground = true)
@Composable
fun CourseCardPreview() {
    val sampleColors = listOf(
        Color(0xFFE3F2FD),
        Color(0xFFE8F5E9),
        Color(0xFFFFF3E0),
        Color(0xFFF3E5F5),
        Color(0xFFE0F7FA)
    )
    MaterialTheme {
        CourseCard(
            course = CourseInfo("1", "高等数学", "张老师", "教 1-101", 0),
            colorsList = sampleColors,
            textMeasurer = rememberTextMeasurer(),
            onClick = {},
            modifier = Modifier.size(width = 96.dp, height = 120.dp)
        )
    }
}
