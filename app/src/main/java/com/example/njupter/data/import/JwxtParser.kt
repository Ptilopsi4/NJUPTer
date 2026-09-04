package com.example.njupter.data.import

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 远程课程数据模型，临时存放解析来的字段
 */
data class RemoteCourse(
    val name: String,
    val teacher: String,
    val classroom: String,
    val credit: String = "",
    val courseNature: String = "",
    val dayOfWeek: Int, // 1~7
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>
)

/**
 * 新版正方教务课表 HTML 解析层。
 */
class JwxtParser {

    fun parseHtml(html: String): List<RemoteCourse> {
        val document = Jsoup.parse(html)
        val courses = mutableListOf<RemoteCourse>()

        // 新系统同时输出网格与列表。列表没有网格中的 rowspan 列偏移问题，
        // 并且字段带有稳定标签，更适合做数据导入。
        val table = document.getElementById("kblist_table")
            ?: throw IllegalArgumentException("未找到课表数据，请确认统一认证已经完成")

        var currentDay = -1
        var currentSlot: Pair<Int, Int>? = null
        for (row in table.select("tr")) {
            row.selectFirst("span.week")?.text()?.let { dayText ->
                currentDay = parseDay(dayText)
            }

            // 同一格的第二门课（单双周、1-8/9-18 分段）与第一门共用 rowspan 的
            // 节次单元格，其所在行没有 span.festival；此时沿用上一行的槽位，
            // 否则第二门课会被整行跳过。
            row.selectFirst("span.festival")?.text()?.let { sectionText ->
                val (startSection, endSection) = parseSections(sectionText)
                if (startSection != -1) currentSlot = startSection to endSection
            }
            val slot = currentSlot ?: continue
            if (currentDay == -1) continue

            for (block in row.select("div.timetable_con")) {
                // 红色斜体是“待筛选”课程，不属于已经选上的课表。
                val titleColor = block.selectFirst("span.title font")
                    ?.attr("color")
                    ?.trim()
                    ?.lowercase()
                if (titleColor == "red") continue

                parseCourseBlock(block, currentDay, slot.first, slot.second)
                    ?.let(courses::add)
            }
        }

        return courses
    }

    private fun parseCourseBlock(
        block: Element,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int
    ): RemoteCourse? {
        val name = block.selectFirst("span.title")
            ?.text()
            ?.cleanDisplayText()
            .orEmpty()
        if (name.isBlank()) return null

        val details = block.select("p font").joinToString(" ") { it.text() }
        val weeks = parseWeeks(extractLabeledValue(details, "周数"))
        if (weeks.isEmpty()) return null

        return RemoteCourse(
            name = name,
            teacher = extractLabeledValue(details, "教师").cleanDisplayText(),
            classroom = extractLabeledValue(details, "上课地点").cleanDisplayText(),
            credit = extractLabeledValue(details, "学分").cleanDisplayText(),
            courseNature = extractLabeledValue(details, "课程性质").cleanDisplayText(),
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            endSection = endSection,
            weeks = weeks
        )
    }

    private fun extractLabeledValue(text: String, label: String): String {
        val labels = listOf("周数", "校区", "上课地点", "教师", "学分", "课程性质")
        val followingLabels = labels.joinToString("|") { Regex.escape(it) }
        val regex = Regex(
            "${Regex.escape(label)}\\s*[:：]\\s*(.*?)(?=\\s*(?:$followingLabels)\\s*[:：]|$)"
        )
        return regex.find(text)?.groupValues?.get(1).orEmpty().trim()
    }

    private fun parseDay(dayStr: String): Int {
        return when (dayStr.trim()) {
            "星期一", "周一" -> 1
            "星期二", "周二" -> 2
            "星期三", "周三" -> 3
            "星期四", "周四" -> 4
            "星期五", "周五" -> 5
            "星期六", "周六" -> 6
            "星期日", "星期天", "周日", "周天" -> 7
            else -> -1
        }
    }

    private fun parseSections(sectionText: String): Pair<Int, Int> {
        val sections = Regex("\\d+")
            .findAll(sectionText)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
        return if (sections.isEmpty()) {
            -1 to -1
        } else {
            sections.minOrNull()!! to sections.maxOrNull()!!
        }
    }

    private fun parseWeeks(weekText: String): List<Int> {
        if (weekText.isBlank()) return emptyList()

        val normalized = weekText
            .replace('（', '(')
            .replace('）', ')')
            .replace(Regex("[—–－~～至]"), "-")
            .replace("第", "")
            .replace("周", "")

        val allWeeks = linkedSetOf<Int>()
        normalized
            .replace(Regex("[()]"), "")
            .split(Regex("[,，、;；]"))
            .forEach { part ->
                val bounds = Regex("\\d+")
                    .findAll(part)
                    .mapNotNull { it.value.toIntOrNull() }
                    .toList()
                when {
                    bounds.size >= 2 -> {
                        val start = bounds.first()
                        val end = bounds.last()
                        if (start in 1..MAX_WEEK && end in start..MAX_WEEK) {
                            allWeeks.addAll(start..end)
                        }
                    }
                    bounds.size == 1 && bounds.first() in 1..MAX_WEEK -> {
                        allWeeks.add(bounds.first())
                    }
                }
            }

        return when {
            normalized.contains("单") && !normalized.contains("双") -> allWeeks.filter { it % 2 == 1 }
            normalized.contains("双") && !normalized.contains("单") -> allWeeks.filter { it % 2 == 0 }
            else -> allWeeks.toList()
        }
    }

    private fun String.cleanDisplayText(): String =
        trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([（(])"), "$1")

    private companion object {
        const val MAX_WEEK = 60
    }
}
