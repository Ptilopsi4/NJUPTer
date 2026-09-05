package com.example.njupter.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class SemesterRangeParserTest {

    @Test
    fun `parses homepage calendar payload`() {
        val raw = "2026-2027学年第1学期(2026-08-31至2027-01-15)\n1,1,2,3,4,5,5,6,7,8,9,9,10,11,12,13,14,14,15,16,17,18,18,19,20"

        val range = SemesterRangeParser.parse(raw)

        assertEquals("2026-2027-1", range?.semesterCode)
        assertEquals(millis(2026, Calendar.AUGUST, 31), range?.startMillis)
        assertEquals(millis(2027, Calendar.JANUARY, 15), range?.endMillis)
        // 月份交界的重复周号取最大值
        assertEquals(20, range?.totalWeeks)
    }

    @Test
    fun `parses live dom text without the ordinal marker`() {
        // 真机 textContent 里没有“第”字（浏览器另存样本才有）
        val raw = "2026-2027学年1学期(2026-08-31至2027-01-15)\n1,1,2,3,4,5,5,6,7,8,9,9,10,11,12,13,14,14,15,16,17,18,18,19,20"

        val range = SemesterRangeParser.parse(raw)

        assertEquals("2026-2027-1", range?.semesterCode)
        assertEquals(millis(2026, Calendar.AUGUST, 31), range?.startMillis)
        assertEquals(20, range?.totalWeeks)
    }

    @Test
    fun `parsed start date is the monday of week one`() {
        val range = SemesterRangeParser.parse(
            "2026-2027学年第1学期(2026-08-31至2027-01-15)\n1,2,3"
        )
        val calendar = Calendar.getInstance().apply { timeInMillis = range!!.startMillis }

        assertEquals(Calendar.MONDAY, calendar.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `falls back to day count when week headers are missing`() {
        // 2026-08-31 至 2027-01-15 共 138 天，向上取整为 20 周
        val range = SemesterRangeParser.fromTitleAndWeeks(
            "2026-2027学年第1学期(2026-08-31至2027-01-15)",
            emptyList()
        )

        assertEquals(20, range?.totalWeeks)
    }

    @Test
    fun `rejects malformed payloads`() {
        assertNull(SemesterRangeParser.parse(null))
        assertNull(SemesterRangeParser.parse(""))
        assertNull(SemesterRangeParser.parse("no separator"))
        assertNull(SemesterRangeParser.parse("\n1,2,3"))
        assertNull(SemesterRangeParser.fromTitleAndWeeks("garbage title", listOf(1, 2)))
        assertNull(SemesterRangeParser.fromTitleAndWeeks("2026-2027学年第1学期(2027-01-15至2026-08-31)", listOf(1)))
    }

    private fun millis(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day)
        }.timeInMillis
    }
}
