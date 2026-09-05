package com.example.njupter.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimetableTimeIndicatorTest {
    private val times = listOf(
        "08:00-08:45",
        "08:50-09:35",
        "09:50-10:35"
    )

    @Test
    fun `before classes indicator is hidden`() {
        assertNull(findCurrentSectionPosition(times, 7 * 60))
    }

    @Test
    fun `at day start indicator rests on first start line`() {
        assertEquals(0 to 0f, findCurrentSectionPosition(times, 8 * 60))
    }

    @Test
    fun `between classes indicator rests on next start line`() {
        assertEquals(2 to 0f, findCurrentSectionPosition(times, 9 * 60 + 40))
    }

    @Test
    fun `during class indicator follows progress`() {
        assertEquals(0 to (30f / 45f), findCurrentSectionPosition(times, 8 * 60 + 30))
    }

    @Test
    fun `after all classes indicator is hidden`() {
        assertNull(findCurrentSectionPosition(times, 12 * 60))
    }

    @Test
    fun `without valid times indicator remains unavailable`() {
        assertNull(findCurrentSectionPosition(listOf("invalid"), 7 * 60))
    }
}
