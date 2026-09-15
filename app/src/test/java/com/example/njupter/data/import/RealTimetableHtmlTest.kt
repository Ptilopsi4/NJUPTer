package com.example.njupter.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 用真实导出的课表页面验证解析完整性：
 * 同一格的多门课程（单双周、1-8/9-18 分段）必须全部解析出来。
 */
class RealTimetableHtmlTest {
    private val parser = JwxtParser()

    private fun loadSampleHtml(): String? {
        val candidates = listOf(
            File("tmp/timetable_sample.html"),
            File("D:/Repos/NJUPTer/tmp/timetable_sample.html")
        )
        return candidates.firstOrNull { it.exists() }?.readText()
    }

    @Test
    fun pairedSlotCourses_areAllParsed() {
        val html = loadSampleHtml() ?: return  // 样本文件不存在时跳过

        val courses = parser.parseHtml(html)

        // 星期四 3-4 节：单周与双周两门课必须在同一节次槽位各解析出一条
        val slot34 = courses.filter { it.dayOfWeek == 4 && it.startSection == 3 && it.endSection == 4 }
        assertEquals(2, slot34.size)
        assertEquals(setOf("单", "双"), slot34.mapNotNull { c ->
            when {
                c.weeks.all { it % 2 == 1 } -> "单"
                c.weeks.all { it % 2 == 0 } -> "双"
                else -> null
            }
        }.toSet())
        assertEquals((1..17).filter { it % 2 == 1 }, slot34.first { it.weeks.first() == 1 }.weeks)
        assertEquals((2..18).filter { it % 2 == 0 }, slot34.first { it.weeks.first() == 2 }.weeks)

        // 星期二 6-7 节：1-8周 与 9-18周 分段两门课
        val slot67 = courses.filter { it.dayOfWeek == 2 && it.startSection == 6 && it.endSection == 7 }
        assertEquals(2, slot67.size)
        assertTrue(slot67.any { it.weeks == (1..8).toList() })
        assertTrue(slot67.any { it.weeks == (9..18).toList() })

        // 星期五 8-9 节：同样为 1-8周 / 9-18周 两门
        val slot89 = courses.filter { it.dayOfWeek == 5 && it.startSection == 8 && it.endSection == 9 }
        assertEquals(2, slot89.size)
    }
}
