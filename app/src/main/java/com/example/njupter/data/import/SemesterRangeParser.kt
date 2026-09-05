package com.example.njupter.data.import

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class SemesterRange(
    val semesterCode: String,
    val startMillis: Long,
    val endMillis: Long,
    val totalWeeks: Int
)

/**
 * 解析教务主页日历 widget 的数据。WebView 端脚本返回 "标题\n周次数字列表" 两行文本，
 * 标题形如 "2026-2027学年第1学期(2026-08-31至2027-01-15)"；周次数字在月份交界处重复，
 * 取最大值即为总周数。
 */
object SemesterRangeParser {

    fun parse(raw: String?): SemesterRange? {
        if (raw.isNullOrBlank()) return null
        val separator = raw.indexOf('\n')
        if (separator <= 0) return null

        val title = raw.substring(0, separator).trim()
        val weekNumbers = raw.substring(separator + 1)
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }

        return fromTitleAndWeeks(title, weekNumbers)
    }

    fun fromTitleAndWeeks(title: String, weekNumbers: List<Int>): SemesterRange? {
        val match = titleRegex.find(title) ?: return null
        val (year, termNumber, startText, endText) = match.destructured
        val start = parseLocalDate(startText) ?: return null
        val end = parseLocalDate(endText) ?: return null
        if (end.timeInMillis < start.timeInMillis) return null

        val totalWeeks = weekNumbers.maxOrNull() ?: daysToWeeks(start, end)
        return SemesterRange("$year-$termNumber", start.timeInMillis, end.timeInMillis, totalWeeks)
    }

    private fun daysToWeeks(start: Calendar, end: Calendar): Int {
        val days = ((end.timeInMillis - start.timeInMillis) / DAY_MS).toInt() + 1
        return ((days + 6) / 7).coerceIn(1, 30)
    }

    private fun parseLocalDate(text: String): Calendar? {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.isLenient = false
        val date = try {
            format.parse(text)
        } catch (e: ParseException) {
            null
        } ?: return null
        return Calendar.getInstance().apply {
            clear()
            time = date
        }
    }

    // 线上 DOM 的 textContent 为“2026-2027学年1学期”，浏览器另存的样本则多一个“第”字，两者都兼容
    private val titleRegex =
        Regex("""(\d{4}-\d{4})学年(?:第)?(\d+)学期\((\d{4}-\d{2}-\d{2})至(\d{4}-\d{2}-\d{2})\)""")

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}
