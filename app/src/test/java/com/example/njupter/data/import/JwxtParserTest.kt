package com.example.njupter.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JwxtParserTest {
    private val parser = JwxtParser()

    @Test
    fun parseHtml_readsNewListTableAndFiltersPendingCourses() {
        val courses = parser.parseHtml(NEW_JWGLXT_HTML)

        assertEquals(3, courses.size)
        assertEquals(
            RemoteCourse(
                name = "大学物理（下）",
                teacher = "闫巍",
                classroom = "教3－520",
                credit = "3",
                courseNature = "必修",
                dayOfWeek = 1,
                startSection = 1,
                endSection = 2,
                weeks = (2..18).filter { it % 2 == 0 }
            ),
            courses[0]
        )
        assertEquals("物理实验（下）", courses[1].name)
        assertEquals("", courses[1].credit)
        assertEquals("", courses[1].courseNature)
        assertEquals((1..17).filter { it % 2 == 1 }, courses[1].weeks)
        assertEquals(listOf(1, 3, 5, 8, 9, 10), courses[2].weeks)
    }

    @Test
    fun parseHtml_readsPairedSlotCoursesThatShareRowspanedFestivalCell() {
        // 真实页面中同格两门课共用 rowspan 的节次单元格：第二门课所在行
        // 没有 span.festival，必须沿用上一行的节次，不能整行跳过。
        val html = """
            <table id="kblist_table">
              <tr><td id="xq_rowspan_4" rowspan="2"><span class="week">星期四</span></td></tr>
              <tr>
                <td id="jc_4-3-4" rowspan="2"><span class="festival">3-4</span></td>
                <td>
                  <div class="timetable_con text-left">
                    <span class="title"><font color="blue">单周课程</font></span>
                    <p><font color="blue">周数：1-17周(单)</font><font color="blue">上课地点：教4－309</font></p>
                  </div>
                </td>
              </tr>
              <tr>
                <td>
                  <div class="timetable_con text-left">
                    <span class="title"><font color="blue">双周课程</font></span>
                    <p><font color="blue">周数：2-18周(双)</font><font color="blue">上课地点：教4－406</font></p>
                  </div>
                </td>
              </tr>
            </table>
        """.trimIndent()

        val courses = parser.parseHtml(html)

        assertEquals(2, courses.size)
        val odd = courses.first { it.name == "单周课程" }
        val even = courses.first { it.name == "双周课程" }
        assertEquals((1..17).filter { it % 2 == 1 }, odd.weeks)
        assertEquals((2..18).filter { it % 2 == 0 }, even.weeks)
        assertEquals(4, odd.dayOfWeek)
        assertEquals(4, even.dayOfWeek)
        assertEquals(3, odd.startSection)
        assertEquals(3, even.startSection)
        assertEquals(4, odd.endSection)
        assertEquals(4, even.endSection)
    }

    @Test
    fun parseHtml_reportsAuthenticationPageInsteadOfReturningEmptyData() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parseHtml("<html><body><form id='login'>统一认证</form></body></html>")
        }
    }

    @Test
    fun parseHtmlDetailed_countsBlocksInRowsWithoutResolvableSlot() {
        // 节次 rowspan 只覆盖第一行，第二行的课程块无处安放：
        // 必须计数暴露，而不是静默丢弃
        fun block(name: String) =
            """<td><div class="timetable_con"><span class="title"><font color="blue">$name</font></span><p><font color="blue">周数：1-2周</font></p></div></td>"""
        val html = """
            <table id="kblist_table"><tbody>
            <tr><td><span class="week">星期四</span></td><td><span class="festival">3-4</span></td>${block("first")}</tr>
            <tr>${block("orphan")}</tr>
            </tbody></table>
        """.trimIndent()

        val result = parser.parseHtmlDetailed(html)

        assertEquals(1, result.courses.size)
        assertEquals("first", result.courses.single().name)
        assertEquals(1, result.skippedRecords)
    }

    private companion object {
        val NEW_JWGLXT_HTML = """
            <table id="kblist_table">
              <tr><td>课表标题</td></tr>
              <tr class="tbody_head"><td>星期</td><td>节次</td><td>课表信息</td></tr>
              <tr><td rowspan="3"><span class="week">星期一</span></td></tr>
              <tr>
                <td><span class="festival">1-2</span></td>
                <td><div class="timetable_con text-left">
                  <span class="title"><font color="blue">大学物理（下）</font></span>
                  <p><font color="blue">周数：2-18周(双)</font><font color="blue">校区:仙林 上课地点：教3－520</font><font color="blue">教师 ：闫巍</font><font color="blue">学分：3</font><font color="blue">课程性质：必修</font></p>
                </div></td>
              </tr>
              <tr>
                <td><span class="festival">3-5</span></td>
                <td><div class="timetable_con text-left">
                  <span class="title"><font color="blue">物理实验 （下）</font></span>
                  <p><font color="blue">周数：1－17周（单）</font><font color="blue">校区:仙林 上课地点：未排地点</font><font color="blue">教师 ：王老师</font></p>
                </div></td>
              </tr>
              <tr><td rowspan="2"><span class="week">星期二</span></td></tr>
              <tr>
                <td><span class="festival">6-7</span></td>
                <td>
                  <div class="timetable_con text-left">
                    <span class="title"><font color="blue">信号与系统</font></span>
                    <p><font color="blue">周数：1,3,5,8-10周</font><font color="blue">校区:仙林 上课地点：教3-300</font><font color="blue">教师 ：孙老师</font></p>
                  </div>
                  <div class="timetable_con text-left">
                    <span class="title"><font color="red"><i>待筛选课程</i></font></span>
                    <p><font color="red">周数：1-18周</font><font color="red">上课地点：教1-101</font><font color="red">教师 ：待定</font></p>
                  </div>
                </td>
              </tr>
            </table>
        """.trimIndent()
    }
}
