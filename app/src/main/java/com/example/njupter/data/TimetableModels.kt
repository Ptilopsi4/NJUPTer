package com.example.njupter.data

/**
 * 存放数据模型和 JSON 解析用的 DTO
 */

val defaultSessionTimes = listOf(
    "08:00-08:45",
    "08:50-09:35",
    "09:50-10:35",
    "10:40-11:25",
    "11:30-12:15",
    "13:45-14:30",
    "14:35-15:20",
    "15:35-16:20",
    "16:25-17:10",
    "18:30-19:15",
    "19:25-20:10",
    "20:20-21:05"
)

data class TimetableMetadata(
    val id: String,
    val name: String,
    val lastModified: Long,
    val startDate: Long = System.currentTimeMillis(),   // TODO: 需要在创建/编辑界面提供选择，后续版本考虑从校历自动计算
    val totalWeeks: Int = 20,
    val sessionTimes: List<String>? = null,
    val showWeekends: Boolean = true
) {
    val nonNullSessionTimes: List<String>   // sessionTimes 为 null 或不足 12 个时自动用默认时间补全。
        get() = sessionTimes?.let { times ->
            if (times.size < 12) {
                times + defaultSessionTimes.drop(times.size)
            } else {
                times
            }
        } ?: defaultSessionTimes
}

data class TimetableData(
    val courses: List<CourseInfo>,
    val sessions: List<CourseSession>
)

// --- DTO 用于 JSON 解析 ---
internal data class TimetableJsonRoot(
    val courses: List<CourseInfoJson>,
    val sessions: List<CourseSessionJson>
)

internal data class CourseInfoJson(
    val id: String,
    val name: String,
    val teacher: String,
    val colorIndex: Int = -1,
    val credit: String? = null,
    val courseNature: String? = null,
    val note: String? = null,
    val attendanceType: String? = null,
    val reminderEnabled: Boolean? = null
)

internal data class CourseSessionJson(
    val courseId: String,
    val dayOfWeek: Int,
    val startNode: Int,
    val length: Int,
    val weeks: List<Int>? = null,
    val classroom: String? = null
)

internal data class TimetableIndex(
    val timetables: List<TimetableMetadata>
)
