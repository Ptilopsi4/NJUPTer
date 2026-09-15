package com.example.njupter.ui.theme

import androidx.compose.ui.graphics.Color

// 课程卡片浅色 - 三档对比度（Material Design 调色板 100/200/300 级，色相顺序一致）
// 索引顺序：Red, Purple, Indigo, Blue, Teal, LightGreen, Orange, DeepOrange,
//           Amber, Lime, Green, Cyan, LightBlue, DeepPurple, Pink, BlueGrey
internal val SoftCourseColors = listOf(
    Color(0xFFFFCDD2), Color(0xFFE1BEE7), Color(0xFFC5CAE9), Color(0xFFBBDEFB),
    Color(0xFFB2DFDB), Color(0xFFDCEDC8), Color(0xFFFFE0B2), Color(0xFFFFCCBC),
    Color(0xFFFFECB3), Color(0xFFF0F4C3), Color(0xFFC8E6C9), Color(0xFFB2EBF2),
    Color(0xFFB3E5FC), Color(0xFFD1C4E9), Color(0xFFF8BBD0), Color(0xFFCFD8DC)
)

internal val StandardCourseColors = listOf(
    Color(0xFFEF9A9A), Color(0xFFCE93D8), Color(0xFF9FA8DA), Color(0xFF90CAF9),
    Color(0xFF80CBC4), Color(0xFFC5E1A5), Color(0xFFFFCC80), Color(0xFFFFAB91),
    Color(0xFFFFE082), Color(0xFFE6EE9C), Color(0xFFA5D6A7), Color(0xFF80DEEA),
    Color(0xFF81D4FA), Color(0xFFB39DDB), Color(0xFFF48FB1), Color(0xFFB0BEC5)
)

internal val VividCourseColors = listOf(
    Color(0xFFE57373), Color(0xFFBA68C8), Color(0xFF7986CB), Color(0xFF64B5F6),
    Color(0xFF4DB6AC), Color(0xFFAED581), Color(0xFFFFB74D), Color(0xFFFF8A65),
    Color(0xFFFFD54F), Color(0xFFDCE775), Color(0xFF81C784), Color(0xFF4DD0E1),
    Color(0xFF4FC3F7), Color(0xFF9575CD), Color(0xFFF06292), Color(0xFF90A4AE)
)

// 深色模式课程卡片颜色（与浅色同色相的低饱和暗色）
internal val DarkCourseColors = listOf(
    Color(0xFF5C2B29), Color(0xFF4A335C), Color(0xFF33375C), Color(0xFF264057),
    Color(0xFF1E4E56), Color(0xFF2D4B33), Color(0xFF5D4018), Color(0xFF5C263B),
    Color(0xFF5D4C19), Color(0xFF556020), Color(0xFF265928), Color(0xFF1D4D53),
    Color(0xFF234C5C), Color(0xFF3C2B5A), Color(0xFF5D2839), Color(0xFF363E45)
)