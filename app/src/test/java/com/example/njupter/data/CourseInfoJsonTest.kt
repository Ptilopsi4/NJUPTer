package com.example.njupter.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseInfoJsonTest {
    private val gson = Gson()

    @Test
    fun `new academic fields survive json round trip`() {
        val source = CourseInfoJson(
            id = "course",
            name = "大学物理",
            teacher = "闫巍",
            credit = "3",
            courseNature = "必修"
        )

        val restored = gson.fromJson(gson.toJson(source), CourseInfoJson::class.java)

        assertEquals("3", restored.credit)
        assertEquals("必修", restored.courseNature)
    }

    @Test
    fun `legacy json with course level room remains readable`() {
        val restored = gson.fromJson(
            """{"id":"course","name":"大学物理","teacher":"闫巍","room":"教3-520"}""",
            CourseInfoJson::class.java
        )

        assertNull(restored.credit)
        assertNull(restored.courseNature)
    }

    @Test
    fun `session classroom survives json round trip`() {
        val source = CourseSessionJson(
            courseId = "course",
            dayOfWeek = 1,
            startNode = 1,
            length = 2,
            weeks = listOf(1, 3, 5),
            classroom = "教3-520"
        )

        val restored = gson.fromJson(gson.toJson(source), CourseSessionJson::class.java)

        assertEquals("教3-520", restored.classroom)
    }

    @Test
    fun `legacy session json without classroom remains readable`() {
        val restored = gson.fromJson(
            """{"courseId":"course","dayOfWeek":1,"startNode":1,"length":2}""",
            CourseSessionJson::class.java
        )

        assertNull(restored.classroom)
        assertNull(restored.weeks)
    }
}
