package com.example.njupter.ui.timetable.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.njupter.ui.animation.pressScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.njupter.data.CourseInfo
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun CourseCard(
    course: CourseInfo,
    colorsList: List<Color>,
    isActiveInCurrentWeek: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorIndex = remember(course, colorsList) {
        if (course.colorIndex in colorsList.indices) {
            course.colorIndex
        } else {
            if (colorsList.isNotEmpty()) (course.name.hashCode() and Int.MAX_VALUE) % colorsList.size else 0
        }
    }

    val fallbackColor = MaterialTheme.colorScheme.primaryContainer
    val activeBackgroundColor = remember(colorIndex, colorsList, fallbackColor) {
        if (colorsList.isNotEmpty()) colorsList[colorIndex] else fallbackColor
    }
    val backgroundColor = if (isActiveInCurrentWeek) {
        activeBackgroundColor
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isActiveInCurrentWeek) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    }

    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = modifier
            .padding(1.dp)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                onClick = onClick
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = contentColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (course.classroom.isNotEmpty()) {
                Text(
                    text = "@${course.classroom}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = contentColor,
                    // 教室信息不折叠：允许换行完整显示，避免被省略号截断
                    maxLines = 2
                )
            }
        }
    }
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
            onClick = {},
            modifier = Modifier.size(width = 96.dp, height = 120.dp)
        )
    }
}

