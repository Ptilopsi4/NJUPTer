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
            weeks = listOf(1, 2),
            classroom = "教3－300"
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

    @Test
    fun matchAndConvert_sameSlotDifferentRoomsStaysSplitByWeeks() {
        // 同名同节次的单双周课换教室：教室各自跟着周次，不能并成一条丢教室
        val result = matcher.matchAndConvert(
            remoteCourses = listOf(
                remote(name = "大学英语", weeks = (1..17).filter { it % 2 == 1 }, classroom = "教1-101"),
                remote(name = "大学英语", weeks = (2..18).filter { it % 2 == 0 }, classroom = "教2-202")
            ),
            existingCourses = emptyList(),
            existingSessions = emptyList()
        )

        assertEquals(1, result.newCourses.size)
        assertEquals(2, result.newSessions.size)
        val odd = result.newSessions.single { it.weeks.first() == 1 }
        val even = result.newSessions.single { it.weeks.first() == 2 }
        assertEquals("教1-101", odd.classroom)
        assertEquals("教2-202", even.classroom)
        assertEquals((1..17).filter { it % 2 == 1 }, odd.weeks)
        assertEquals((2..18).filter { it % 2 == 0 }, even.weeks)
    }

    @Test
    fun matchAndConvert_sameSlotSameRoomStillMergesWeeks() {
        val result = matcher.matchAndConvert(
            remoteCourses = listOf(
                remote(name = "高等数学", weeks = listOf(1, 3, 5)),
                remote(name = "高等数学", weeks = listOf(2, 4))
            ),
            existingCourses = emptyList(),
            existingSessions = emptyList()
        )

        assertEquals(1, result.newSessions.size)
        assertEquals(listOf(1, 2, 3, 4, 5), result.newSessions.single().weeks)
        assertEquals("教3－300", result.newSessions.single().classroom)
    }

    @Test
    fun matchAndConvert_repeatedImportDedupesWeeksPerClassroom() {
        // 已有一条"单周@教1-101"时再导单双周两段：只有"双周@教2-202"是缺失的；
        // 旧键不含教室会把两条已有 session 的周次 union，导致双周段被误判已存在
        val existingCourse = CourseInfo(id = "c1", name = "大学英语", teacher = "孙老师")
        val existingOdd = CourseSession(
            courseId = "c1",
            day = 1,
            startSection = 1,
            endSection = 2,
            weeks = (1..17).filter { it % 2 == 1 },
            classroom = "教1-101"
        )

        val result = matcher.matchAndConvert(
            remoteCourses = listOf(
                remote(name = "大学英语", weeks = (1..17).filter { it % 2 == 1 }, classroom = "教1-101"),
                remote(name = "大学英语", weeks = (2..18).filter { it % 2 == 0 }, classroom = "教2-202")
            ),
            existingCourses = listOf(existingCourse),
            existingSessions = listOf(existingOdd)
        )

        assertTrue(result.newCourses.isEmpty())
        assertEquals(1, result.newSessions.size)
        assertEquals("教2-202", result.newSessions.single().classroom)
        assertEquals((2..18).filter { it % 2 == 0 }, result.newSessions.single().weeks)
    }

    @Test
    fun matchAndConvert_blankClassroomsStillMerge() {
        val result = matcher.matchAndConvert(
            remoteCourses = listOf(
                remote(name = "体育", weeks = listOf(1, 2), classroom = ""),
                remote(name = "体育", weeks = listOf(3, 4), classroom = "")
            ),
            existingCourses = emptyList(),
            existingSessions = emptyList()
        )

        assertEquals(1, result.newSessions.size)
        assertEquals(listOf(1, 2, 3, 4), result.newSessions.single().weeks)
        assertEquals("", result.newSessions.single().classroom)
    }

    @Test
    fun matchAndConvert_countsRemotesWithoutParseableWeeks() {
        val result = matcher.matchAndConvert(
            remoteCourses = listOf(
                remote(name = "大学英语", weeks = listOf(1, 2)),
                remote(name = "体育", weeks = emptyList())
            ),
            existingCourses = emptyList(),
            existingSessions = emptyList()
        )

        assertEquals(1, result.newCourses.size)
        assertEquals(1, result.skippedRecords)
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
