package com.example.njupter.notification

import com.example.njupter.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReminderLeadSettingsTest {

    @Test
    fun `normalizeLeadMinutes filters out-of-range values dedups and sorts`() {
        assertEquals(
            listOf(0, 10, 60),
            SettingsRepository.normalizeLeadMinutes(listOf(60, 10, 0, 10, 200, -5))
        )
        assertEquals(emptyList<Int>(), SettingsRepository.normalizeLeadMinutes(listOf(121, -1)))
    }

    @Test
    fun `reminder keys differ across lead minutes for the same class occurrence`() {
        val tenMinute = buildReminderKey("t", "c", 3, 1_000L, 10)
        val sixtyMinute = buildReminderKey("t", "c", 3, 1_000L, 60)

        assertNotEquals(tenMinute, sixtyMinute)

        val assigned = mutableMapOf<Int, String>()
        listOf(tenMinute, sixtyMinute).forEach { key ->
            assigned[resolveCollisionFreeRequestCode(assigned, key)] = key
        }
        assertEquals(2, assigned.size)
    }

    @Test
    fun `same occurrence and lead resolves to the same key for idempotent rescheduling`() {
        assertEquals(
            buildReminderKey("t", "c", 3, 1_000L, 10),
            buildReminderKey("t", "c", 3, 1_000L, 10)
        )
    }
}
