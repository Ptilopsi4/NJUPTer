package com.example.njupter.widget

import com.example.njupter.data.CourseInfo
import com.example.njupter.data.CourseSession
import com.example.njupter.data.TimetableData
import com.example.njupter.data.TimetableMetadata
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WidgetModelsTest {
    private lateinit var originalTimeZone: TimeZone
    private val courses = listOf(
        CourseInfo("m1", "Morning one", "Teacher A", 0),
        CourseInfo("m2", "Morning two", "Teacher B", 1),
        CourseInfo("t1", "Tomorrow one", "Teacher C", 2),
        CourseInfo("late", "Late course", "Teacher D", 3)
    )

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `widget keeps all courses for layout to display as space allows`() {
        val state = stateAt(
            hour = 10,
            minute = 0,
            sessions = listOf(
                CourseSession("m1", day = 1, startSection = 1, endSection = 2, weeks = listOf(1)),
                CourseSession("m2", day = 1, startSection = 3, endSection = 4, weeks = listOf(1))
            )
        )

        assertEquals(listOf("Morning one", "Morning two"), state.entries.map { it.name })
        assertFalse(state.isTomorrow)
    }

    @Test
    fun `after 17 widget previews tomorrows first course`() {
        val state = stateAt(
            hour = 17,
            minute = 0,
            sessions = listOf(
                CourseSession("m1", day = 1, startSection = 1, endSection = 2, weeks = listOf(1)),
                CourseSession("t1", day = 2, startSection = 3, endSection = 4, weeks = listOf(1))
            )
        )

        assertEquals(listOf("Tomorrow one"), state.entries.map { it.name })
        assertEquals(2, state.dayOfWeek)
        assertTrue(state.isTomorrow)
        assertFalse(state.isDayComplete)
    }

    @Test
    fun `after 17 without tomorrow courses shows completed state`() {
        val state = stateAt(
            hour = 17,
            minute = 0,
            sessions = listOf(
                CourseSession("m1", day = 1, startSection = 1, endSection = 2, weeks = listOf(1))
            )
        )

        assertTrue(state.entries.isEmpty())
        assertFalse(state.isTomorrow)
        assertTrue(state.isDayComplete)
    }

    @Test
    fun `course ending after 17 delays tomorrow preview until it is over`() {
        val sessions = listOf(
            CourseSession("late", day = 1, startSection = 9, endSection = 9, weeks = listOf(1)),
            CourseSession("t1", day = 2, startSection = 1, endSection = 2, weeks = listOf(1))
        )

        assertEquals("Late course", stateAt(17, 5, sessions).entries.single().name)
        assertFalse(stateAt(17, 5, sessions).isTomorrow)
        assertEquals("Tomorrow one", stateAt(17, 11, sessions).entries.single().name)
        assertTrue(stateAt(17, 11, sessions).isTomorrow)
    }

    private fun stateAt(
        hour: Int,
        minute: Int,
        sessions: List<CourseSession>
    ): WidgetDisplayState {
        val sundayStart = millis(2026, Calendar.AUGUST, 30, 12, 0)
        return buildWidgetDisplayState(
            metadata = TimetableMetadata(
                id = "test",
                name = "Test",
                lastModified = 0,
                startDate = sundayStart,
                totalWeeks = 20
            ),
            data = TimetableData(courses = courses, sessions = sessions),
            nowMillis = millis(2026, Calendar.AUGUST, 31, hour, minute)
        )
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
    }
}
