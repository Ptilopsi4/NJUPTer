package com.example.njupter.domain.validation

import com.example.njupter.data.CourseSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseValidatorTest {
    @Test
    fun unavailableWeeks_mergesOnlyOverlappingDayAndSections() {
        val sessions = listOf(
            CourseSession("a", day = 2, startSection = 3, endSection = 4, weeks = listOf(1, 3)),
            CourseSession("b", day = 2, startSection = 4, endSection = 6, weeks = listOf(2, 4)),
            CourseSession("c", day = 2, startSection = 6, endSection = 7, weeks = listOf(5)),
            CourseSession("d", day = 3, startSection = 3, endSection = 4, weeks = listOf(6))
        )

        val unavailable = CourseValidator.unavailableWeeksForSession(
            day = 2,
            start = 3,
            end = 5,
            editingSession = null,
            allSessions = sessions
        )

        assertEquals(setOf(1, 2, 3, 4), unavailable)
    }

    @Test
    fun unavailableWeeks_keepsAdjacentSectionsAvailable() {
        val sessions = listOf(
            CourseSession("a", day = 1, startSection = 1, endSection = 2, weeks = listOf(1, 2))
        )

        val unavailable = CourseValidator.unavailableWeeksForSession(
            day = 1,
            start = 3,
            end = 3,
            editingSession = null,
            allSessions = sessions
        )

        assertTrue(unavailable.isEmpty())
    }

    @Test
    fun unavailableWeeks_excludesOnlyTheSessionBeingEdited() {
        val editing = CourseSession(
            "a",
            day = 1,
            startSection = 1,
            endSection = 2,
            weeks = listOf(1)
        )
        val identicalDuplicate = editing.copy()

        val unavailable = CourseValidator.unavailableWeeksForSession(
            day = 1,
            start = 1,
            end = 2,
            editingSession = editing,
            allSessions = listOf(editing, identicalDuplicate)
        )

        assertEquals(setOf(1), unavailable)
    }

    @Test
    fun validateSessionInput_doesNotIgnoreAnIdenticalDuplicate() {
        val editing = CourseSession(
            "a",
            day = 1,
            startSection = 1,
            endSection = 2,
            weeks = listOf(1)
        )

        val error = CourseValidator.validateSessionInput(
            day = 1,
            start = 1,
            end = 2,
            weeks = listOf(1),
            editingSession = editing,
            allSessions = listOf(editing, editing.copy())
        )

        assertTrue(error is ValidationError.TimeConflict)
    }
}
