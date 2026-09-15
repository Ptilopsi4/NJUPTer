package com.example.njupter.data

data class CourseInfo(
    val id: String,     // 课程唯一标识
    val name: String,
    val teacher: String,
    val colorIndex: Int = -1,    // “-1” 默认Auto
    val credit: String = "",
    val courseNature: String = "",
    val note: String = "",
    val attendanceType: String = "",
    val reminderEnabled: Boolean = false    // 是否为该课安排上课提醒（默认关闭）
)
