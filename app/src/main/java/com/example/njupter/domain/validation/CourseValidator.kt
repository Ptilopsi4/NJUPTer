package com.example.njupter.domain.validation

import com.example.njupter.data.CourseInfo
import com.example.njupter.data.CourseSession
import kotlin.math.max
import kotlin.math.min

sealed class ValidationError {
    data class StartAfterEnd(val start: Int, val end: Int) : ValidationError()
    object NoWeekSelected : ValidationError()
    data class TimeConflict(val day: Int, val startSection: Int, val endSection: Int) : ValidationError()
    data class CourseDuplicate(val name: String, val teacher: String) : ValidationError()
}

object CourseValidator {

    /**
     * Returns the weeks that cannot be used for the proposed day/section range.
     *
     * The session currently being edited is excluded exactly once so its own weeks remain
     * selectable, while an otherwise identical duplicate session still counts as a conflict.
     */
    fun unavailableWeeksForSession(
        day: Int,
        start: Int,
        end: Int,
        editingSession: CourseSession?,
        allSessions: List<CourseSession>
    ): Set<Int> {
        if (start > end) return emptySet()

        val editingIndex = editingSessionIndex(editingSession, allSessions)

        return allSessions
            .asSequence()
            .filterIndexed { index, target ->
                index != editingIndex &&
                    target.day == day &&
                    max(target.startSection, start) <= min(target.endSection, end)
            }
            .flatMap { it.weeks.asSequence() }
            .toSet()
    }

    fun validateSessionInput(
        day: Int,
        start: Int,
        end: Int,
        weeks: List<Int>,
        editingSession: CourseSession?,
        allSessions: List<CourseSession>
    ): ValidationError? {
        if (start > end) {
            return ValidationError.StartAfterEnd(start, end)
        }

        if (weeks.isEmpty()) {
            return ValidationError.NoWeekSelected
        }

        val editingIndex = editingSessionIndex(editingSession, allSessions)
        val selectedWeeks = weeks.toSet()
        val conflict = allSessions.withIndex().find { (index, target) ->
            if (index == editingIndex || target.day != day) return@find false
            val sectionOverlap = max(target.startSection, start) <= min(target.endSection, end)
            val weekOverlap = target.weeks.any { it in selectedWeeks }
            sectionOverlap && weekOverlap
        }?.value

        if (conflict != null) {
            return ValidationError.TimeConflict(conflict.day, conflict.startSection, conflict.endSection)
        }

        return null
    }

    /**
     * Maps a day and week selection to the sections that are already occupied,
     * clipped to [maxSection] so the slider can highlight exactly the blocked range.
     */
    fun conflictingSections(
        day: Int,
        weeks: Set<Int>,
        editingSession: CourseSession?,
        allSessions: List<CourseSession>,
        maxSection: Int
    ): Set<Int> = (1..maxSection).filterTo(mutableSetOf()) { section ->
        val error = validateSessionInput(day, section, section, weeks.toList(), editingSession, allSessions)
        error is ValidationError.TimeConflict
    }

    private fun editingSessionIndex(
        editingSession: CourseSession?,
        allSessions: List<CourseSession>
    ): Int {
        if (editingSession == null) return -1
        return allSessions.indexOfFirst { it === editingSession }
            .takeIf { it >= 0 }
            ?: allSessions.indexOf(editingSession)
    }

    fun validateCourseDuplication(
        currentId: String,
        name: String,
        teacher: String,
        existingCourses: List<CourseInfo>
    ): ValidationError? {
        val duplicate = existingCourses.find {
            it.name == name &&
            it.teacher == teacher &&
            it.id != currentId
        }

        if (duplicate != null) {
            return ValidationError.CourseDuplicate(name, teacher)
        }

        return null
    }
}
