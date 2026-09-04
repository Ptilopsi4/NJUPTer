package com.example.njupter.domain.import

import com.example.njupter.data.CourseInfo
import com.example.njupter.data.CourseSession
import com.example.njupter.data.import.RemoteCourse
import java.util.UUID

/**
 * 将从远程爬取的数据映射到本地域领域模型，并处理重名/匹配。
 */
class TimetableImportMatcher {

    data class ImportResult(
        val newCourses: List<CourseInfo>,
        val newSessions: List<CourseSession>,
        val summary: String
    )

    fun matchAndConvert(
        remoteCourses: List<RemoteCourse>,
        existingCourses: List<CourseInfo>,
        existingSessions: List<CourseSession>,
        summaryTemplate: (Int, Int) -> String = { courses, sessions ->
            "Found $courses new courses, $sessions new sessions."
        }
    ): ImportResult {
        val newInfos = mutableListOf<CourseInfo>()
        val pendingSessions = linkedMapOf<SessionKey, CourseSession>()

        // 同一门课每周多次、教室可能不同，所以教室归属 session 而不是课程；
        // 课程同名同教师视为同一门。展示时仍保留教务系统返回的可读文本。
        val courseMap = mutableMapOf<String, CourseInfo>()
        existingCourses.forEach {
            courseMap[courseKey(it.name, it.teacher)] = it
        }

        val existingWeeksBySession = existingSessions
            .groupBy { SessionKey(it.courseId, it.day, it.startSection, it.endSection) }
            .mapValues { (_, sessions) -> sessions.flatMap { it.weeks }.toSet() }

        remoteCourses.forEach { remote ->
            if (remote.weeks.isEmpty()) return@forEach

            val courseInfo = courseMap.getOrPut(
                courseKey(remote.name, remote.teacher)
            ) {
                val newCourse = CourseInfo(
                    id = UUID.randomUUID().toString(),
                    name = cleanDisplayText(remote.name),
                    teacher = cleanDisplayText(remote.teacher),
                    colorIndex = -1,
                    credit = cleanDisplayText(remote.credit),
                    courseNature = cleanDisplayText(remote.courseNature)
                )
                newInfos.add(newCourse)
                newCourse
            }

            val sessionKey = SessionKey(
                courseId = courseInfo.id,
                day = remote.dayOfWeek,
                startSection = remote.startSection,
                endSection = remote.endSection
            )
            val missingWeeks = remote.weeks
                .asSequence()
                .filter { it > 0 }
                .filterNot { it in existingWeeksBySession[sessionKey].orEmpty() }
                .toSet()
            if (missingWeeks.isEmpty()) return@forEach

            val previous = pendingSessions[sessionKey]
            pendingSessions[sessionKey] = CourseSession(
                courseId = courseInfo.id,
                day = remote.dayOfWeek,
                startSection = remote.startSection,
                endSection = remote.endSection,
                weeks = (previous?.weeks.orEmpty() + missingWeeks).distinct().sorted(),
                classroom = cleanDisplayText(remote.classroom)
            )
        }

        val newSessions = pendingSessions.values.toList()
        return ImportResult(
            newCourses = newInfos,
            newSessions = newSessions,
            summary = summaryTemplate(newInfos.size, newSessions.size)
        )
    }

    private fun courseKey(name: String, teacher: String): String =
        listOf(name, teacher).joinToString("|") { canonicalize(it) }

    private fun canonicalize(value: String): String =
        value
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), "")
            .replace('（', '(')
            .replace('）', ')')
            .replace(Regex("[—–－]"), "-")

    private fun cleanDisplayText(value: String): String =
        value
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([（(])"), "$1")

    private data class SessionKey(
        val courseId: String,
        val day: Int,
        val startSection: Int,
        val endSection: Int
    )
}
