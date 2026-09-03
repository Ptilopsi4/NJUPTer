package com.example.njupter.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimetableUtilsTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `sunday semester anchor starts timetable on following monday`() {
        val sunday = millis(2026, Calendar.AUGUST, 30, 12, 0)
        val monday = millis(2026, Calendar.AUGUST, 31, 7, 0)

        assertEquals("08/31", getDateForWeekDay(sunday, week = 1, day = 1))
        assertNull(getWeekIndexForDate(sunday, totalWeeks = 20, dateMillis = sunday))
        assertEquals(0, getWeekIndexForDate(sunday, totalWeeks = 20, dateMillis = monday))
    }

    @Test
    fun `notification time uses the same normalized monday anchor`() {
        val sunday = millis(2026, Calendar.AUGUST, 30, 12, 0)
        val classStart = getMillisForWeekDay(
            startDate = sunday,
            week = 1,
            day = 1,
            minuteOfDay = 8 * 60
        )
        val calendar = Calendar.getInstance().apply { timeInMillis = classStart }

        assertEquals(Calendar.MONDAY, calendar.get(Calendar.DAY_OF_WEEK))
        assertEquals(31, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun `date picker keeps the local date during early morning`() {
        val localEarlyMorning = millis(2026, Calendar.SEPTEMBER, 2, 1, 30)

        val pickerMillis = localDateMillisToDatePickerMillis(localEarlyMorning)
        val pickerUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = pickerMillis
        }
        assertEquals(2026, pickerUtc.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, pickerUtc.get(Calendar.MONTH))
        assertEquals(2, pickerUtc.get(Calendar.DAY_OF_MONTH))

        val storedMillis = datePickerMillisToLocalDateMillis(pickerMillis)
        val storedLocal = Calendar.getInstance().apply { timeInMillis = storedMillis }
        assertEquals(2026, storedLocal.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, storedLocal.get(Calendar.MONTH))
        assertEquals(2, storedLocal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, storedLocal.get(Calendar.HOUR_OF_DAY))
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
    }
}
