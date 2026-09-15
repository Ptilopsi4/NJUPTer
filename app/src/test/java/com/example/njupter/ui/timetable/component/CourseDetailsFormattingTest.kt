package com.example.njupter.ui.timetable.component

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseDetailsFormattingTest {
    @Test
    fun formatWeekRanges_compactsConsecutiveWeeks() {
        assertEquals("1–3, 5, 7–8", formatWeekRanges(listOf(8, 1, 2, 3, 5, 7, 3)))
    }

    @Test
    fun formatWeekRanges_handlesEmptyInput() {
        assertEquals("", formatWeekRanges(emptyList()))
    }
}
