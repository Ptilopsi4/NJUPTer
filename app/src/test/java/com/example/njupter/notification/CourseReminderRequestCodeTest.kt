package com.example.njupter.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CourseReminderRequestCodeTest {

    @Test
    fun `same key returns same code`() {
        val key = "a|b|1|1000"
        val base = key.hashCode() and 0x7fffffff
        val assigned = mapOf(base to key)
        assertEquals(base, resolveCollisionFreeRequestCode(assigned, key))
    }

    @Test
    fun `collision probes to next free slot`() {
        val key = "a|b|1|1000"
        val base = key.hashCode() and 0x7fffffff
        val assigned = mapOf(base to "other|key")
        val resolved = resolveCollisionFreeRequestCode(assigned, key)
        assertNotEquals(base, resolved)
        assertEquals((base + 1) and 0x7fffffff, resolved)
    }

    @Test
    fun `multi-slot collision probes until free`() {
        val key = "a|b|1|1000"
        val base = key.hashCode() and 0x7fffffff
        val assigned = mapOf(
            base to "x",
            ((base + 1) and 0x7fffffff) to "y",
            ((base + 2) and 0x7fffffff) to "z"
        )
        assertEquals((base + 3) and 0x7fffffff, resolveCollisionFreeRequestCode(assigned, key))
    }

    @Test
    fun `repeated resolution keeps all codes unique per distinct key`() {
        val assigned = mutableMapOf<Int, String>()
        val keys = (1..200).map { "tt|$it|3|$it" }
        keys.forEach { key ->
            val code = resolveCollisionFreeRequestCode(assigned, key)
            assigned[code] = key
        }
        // 每个键都拿到唯一 slot，且重复解析幂等
        keys.forEach { key ->
            assertEquals(assigned.entries.first { it.value == key }.key,
                resolveCollisionFreeRequestCode(assigned, key))
        }
        assertEquals(keys.size, assigned.size)
    }

    @Test
    fun `free code returned as is`() {
        val key = "a|b|1|1000"
        val base = key.hashCode() and 0x7fffffff
        assertEquals(base, resolveCollisionFreeRequestCode(emptyMap(), key))
    }
}
