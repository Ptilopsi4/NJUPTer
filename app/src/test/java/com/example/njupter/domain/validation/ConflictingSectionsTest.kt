package com.example.njupter.domain.validation

import com.example.njupter.data.CourseSession
import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictingSectionsTest {
    @Test fun `marks every occupied section only on overlapping days and weeks`() {
        val sessions = listOf(
            CourseSession("a", 1, 2, 4, listOf(1, 3)),
            CourseSession("b", 1, 7, 8, listOf(2)),
            CourseSession("c", 2, 9, 10, listOf(1))
        )
        assertEquals(setOf(2, 3, 4), CourseValidator.conflictingSections(1, setOf(1), null, sessions, 12))
        assertEquals(setOf(7, 8), CourseValidator.conflictingSections(1, setOf(2), null, sessions, 12))
        assertEquals(emptySet<Int>(), CourseValidator.conflictingSections(1, emptySet(), null, sessions, 12))
    }

    @Test fun `excludes edited session once but retains duplicate conflicts`() {
        val editing = CourseSession("a", 1, 1, 2, listOf(1))
        assertEquals(emptySet<Int>(), CourseValidator.conflictingSections(1, setOf(1), editing, listOf(editing), 12))
        assertEquals(setOf(1, 2), CourseValidator.conflictingSections(1, setOf(1), editing, listOf(editing, editing.copy()), 12))
    }

    @Test fun `clips highlights to visible sections without changing requested weeks`() {
        val weeks = setOf(1, 2)
        val sessions = listOf(CourseSession("a", 1, 11, 14, listOf(2)))
        assertEquals(setOf(11, 12), CourseValidator.conflictingSections(1, weeks, null, sessions, 12))
        assertEquals(setOf(1, 2), weeks)
        assertEquals(ValidationError.TimeConflict(1, 11, 14),
            CourseValidator.validateSessionInput(1, 10, 12, weeks.toList(), null, sessions))
    }
}
