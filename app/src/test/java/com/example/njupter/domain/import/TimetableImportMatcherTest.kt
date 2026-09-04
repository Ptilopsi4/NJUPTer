package com.example.njupter.domain.import

import com.example.njupter.data.CourseInfo
import com.example.njupter.data.CourseSession
import com.example.njupter.data.import.RemoteCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableImportMatcherTest {
    private val matcher = TimetableImportMatcher()

    @Test
    fun matchAndConvert_normalizesCourseIdentityAndMergesWeekFragments() {
        val remoteCourses = listOf(
            remote(name = "物理实验 （下）", weeks = listOf(1, 3, 5)),
            remote(name = "物理实验（下）", weeks = listOf(7, 9))
        )

        val result = matcher.matchAndConvert(remoteCourses, emptyList(), emptyList())

        assertEquals(1, result.newCourses.size)
        assertEquals("物理实验（下）", result.newCourses.single().name)
        assertEquals("2.5", result.newCourses.single().credit)
        assertEquals("限选", result.newCourses.single().courseNature)
        assertEquals(1, result.newSessions.size)
        assertEquals(listOf(1, 3, 5, 7, 9), result.newSessions.single().weeks)
    }

    @Test
    fun matchAndConvert_reusesExistingCourseAndImportsOnlyMissingWeeks() {
        val existingCourse = CourseInfo(
            id = "existing",
            name = "信号与系统",
            teacher = "孙老师"
        )
        val existingSession = CourseSession(
            courseId = existingCourse.id,
            day = 1,
            startSection = 1,
            endSection = 2,
            weeks = listOf(1, 2)
        )

        val result = matcher.matchAndConvert(
            remoteCourses = listOf(remote(name = " 信号与系统 ", weeks = listOf(1, 2, 3, 4))),
            existingCourses = listOf(existingCourse),
            existingSessions = listOf(existingSession)
        )

        assertTrue(result.newCourses.isEmpty())
        assertEquals(listOf(3, 4), result.newSessions.single().weeks)
        assertEquals(existingCourse.id, result.newSessions.single().courseId)
        assertEquals("教3－300", result.newSessions.single().classroom)
    }

    @Test
    fun matchAndConvert_mergesSameCourseAcrossDifferentRoomsIntoSessions() {
        // 同一门课每周两次、教室不同：应合并为一条课程记录，教室分属各 session
        val result = matcher.matchAndConvert(
            remoteCourses = listOf(
                remote(name = "数据库系统原理", weeks = (1..18).toList()),
                remote(
                    name = " 数据库系统原理 ",
                    weeks = (1..17).filter { it % 2 == 1 },
                    day = 4,
                    classroom = "教4－309"
                )
            ),
            existingCourses = emptyList(),
            existingSessions = emptyList()
        )

        assertEquals(1, result.newCourses.size)
        assertEquals(2, result.newSessions.size)
        assertEquals("教3－300", result.newSessions.first { it.day == 1 }.classroom)
        assertEquals("教4－309", result.newSessions.first { it.day == 4 }.classroom)
    }

    @Test
    fun matchAndConvert_keepsDistinctClassroomsPerSession() {
        val odd = RemoteCourse(
            name = "课程A",
            teacher = "张三",
            classroom = "教1-101",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            weeks = (1..16).filter { it % 2 == 1 }
        )
        val even = odd.copy(name = "课程B", classroom = "教2-202", weeks = (1..16).filter { it % 2 == 0 })

        val result = matcher.matchAndConvert(listOf(odd, even), emptyList(), emptyList())

        assertEquals(2, result.newCourses.size)
        assertEquals("教1-101", result.newSessions.first { it.weeks.first() == 1 }.classroom)
        assertEquals("教2-202", result.newSessions.first { it.weeks.first() == 2 }.classroom)
    }

    private fun remote(
        name: String,
        weeks: List<Int>,
        day: Int = 1,
        classroom: String = "教3－300"
    ) = RemoteCourse(
        name = name,
        teacher = "孙老师",
        classroom = classroom,
        credit = "2.5",
        courseNature = "限选",
        dayOfWeek = day,
        startSection = 1,
        endSection = 2,
        weeks = weeks
    )
}
