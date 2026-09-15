package com.example.njupter.data

data class CourseSession(
    val courseId: String,   // 关联 CourseInfo
    val day: Int,           // 1=Mon, 2=Tue, ...
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int> = (1..30).toList(),
    val classroom: String = ""
)