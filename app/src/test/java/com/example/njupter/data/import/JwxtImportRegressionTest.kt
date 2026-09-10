package com.example.njupter.data.import

import com.example.njupter.domain.import.TimetableImportMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtImportRegressionTest {
    private val parser = JwxtParser()
    private val html = requireNotNull(javaClass.getResource("/jwxt/thursday-rowspan-adjustment.html"))
        .readText()

    @Test
    fun rowspanRetainsBothEnglishRoomsAndWeekSets() {
        val english = parser.parseHtml(html).filter { it.name == "大学英语IV" }
        assertEquals(2, english.size)
        assertEquals(listOf("教3－105", "语音8室(教3-603)"), english.map { it.classroom })
        assertEquals((1..17 step 2).toList(), english[0].weeks)
        assertEquals((2..18 step 2).toList(), english[1].weeks)
        assertTrue(english.all { it.dayOfWeek == 4 && it.startSection == 3 && it.endSection == 4 })
    }

    @Test
    fun adjustedTitlesKeepSeparateDurationsAndMixedWeeks() {
        val adjusted = parser.parseHtml(html).filter { it.name.startsWith("【调】") }
        assertEquals(2, adjusted.size)
        assertEquals(listOf(6 to 7, 6 to 8), adjusted.map { it.startSection to it.endSection })
        assertTrue(adjusted.all { it.dayOfWeek == 4 })
        assertEquals(listOf(2), adjusted[0].weeks)
        assertEquals(listOf(1, 3) + (4..18), adjusted[1].weeks)
    }

    @Test
    fun parsedSessionsSurviveMatchingAndRepeatedImport() {
        val remote = parser.parseHtml(html)
        val matcher = TimetableImportMatcher()
        val result = matcher.matchAndConvert(remote, emptyList(), emptyList())
        // 本地模型教室挂在 CourseSession 上，同名课程不按教室拆成多个 CourseInfo；
        // 同名同节次的单双周两段会并入同一条 session（沿用先到教室），这是本地既有语义
        assertEquals(2, result.newCourses.size)
        assertEquals(3, result.newSessions.size)
        for (week in 1..18) {
            val sessions = result.newSessions.filter { week in it.weeks }
            assertEquals(2, sessions.size)
            assertEquals(if (week == 2) 7 else 8, sessions.single { it.startSection == 6 }.endSection)
        }
        val repeated = matcher.matchAndConvert(remote, result.newCourses, result.newSessions)
        assertTrue(repeated.newCourses.isEmpty())
        assertTrue(repeated.newSessions.isEmpty())
    }

    @Test
    fun sectionInheritanceExpiresAndResetsAtDayAndRowGroupBoundaries() {
        fun block(name: String) = """<td><div class="timetable_con"><span class="title">$name</span><p><font>周数：1-2周</font></p></div></td>"""
        val courses = parser.parseHtml("""
            <table id="kblist_table"><tbody>
            <tr><td><span class="week">星期四</span></td><td rowspan="2"><span class="festival">3-4</span></td>${block("first")}</tr>
            <tr>${block("inherited")}</tr>
            <tr>${block("expired")}</tr>
            <tr><td rowspan="5"><span class="festival">6-8</span></td>${block("afternoon")}</tr>
            <tr><td><span class="week">星期五</span></td>${block("wrong-day")}</tr>
            <tr><td rowspan="5"><span class="festival">1-2</span></td>${block("friday")}</tr>
            </tbody><tbody><tr>${block("wrong-group")}</tr></tbody></table>
        """.trimIndent())
        assertEquals(listOf("first", "inherited", "afternoon", "friday"), courses.map { it.name })
        assertEquals(listOf(4, 4, 4, 5), courses.map { it.dayOfWeek })
        assertEquals(listOf(3, 3, 6, 1), courses.map { it.startSection })
    }

    @Test
    fun zeroRowspanCoversRemainingRowsOnlyWithinItsGroup() {
        val courses = parser.parseHtml(html.replace("rowspan=\"2\"", "rowspan=\"0\""))
        assertEquals(4, courses.size)
        assertEquals(listOf(3, 3, 6, 6), courses.map { it.startSection })
    }

    @Test
    fun pendingUnderlinedTitlesAreStillExcluded() {
        val pending = html.replace("<u class=\"title showJxbtkjl\" style=\"cursor: pointer;\"><font color=\"blue\">",
            "<u class=\"title showJxbtkjl\"><font color=\"red\"><i>")
            .replace("</font></u>", "</i></font></u>")
        assertEquals(listOf("大学英语IV", "大学英语IV"), parser.parseHtml(pending).map { it.name })
    }

    @Test
    fun parityIsAppliedPerSegmentIncludingMixedOddAndEvenSegments() {
        val mixed = html.replace("1-3周(单),4-18周", "1-7周（单），2-8周（双）；10-12周、12周")
        val course = parser.parseHtml(mixed).single { it.startSection == 6 && it.endSection == 8 }
        assertEquals((1..8).toList() + (10..12), course.weeks)
    }
}
