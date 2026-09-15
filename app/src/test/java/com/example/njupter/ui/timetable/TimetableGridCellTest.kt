package com.example.njupter.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 双击空白格新建课程依赖这条像素→格子的换算，边界取整错了就会开在相邻格。
 */
class TimetableGridCellTest {

    @Test
    fun `first cell center maps to index one`() {
        assertEquals(1, gridCellIndex(positionPx = 20f, extentPx = 200f, count = 5))
    }

    @Test
    fun `cell boundary belongs to the next cell`() {
        assertEquals(2, gridCellIndex(40f, 200f, 5))
        assertEquals(5, gridCellIndex(160f, 200f, 5))
    }

    @Test
    fun `last pixel inside the grid still maps to the last index`() {
        assertEquals(5, gridCellIndex(199.9f, 200f, 5))
    }

    @Test
    fun `positions outside the grid are rejected`() {
        assertEquals(0, gridCellIndex(-1f, 200f, 5))
        assertEquals(0, gridCellIndex(200f, 200f, 5))
    }

    @Test
    fun `degenerate geometry is rejected instead of dividing by zero`() {
        assertEquals(0, gridCellIndex(10f, 0f, 5))
        assertEquals(0, gridCellIndex(10f, 200f, 0))
    }

    @Test
    fun `real device widths keep seven columns and twelve rows aligned`() {
        // 1440px 宽屏去掉 50dp 侧栏后的内容区近似 1160px
        for (day in 1..7) {
            val center = (1160f / 7) * (day - 0.5f)
            assertEquals(day, gridCellIndex(center, 1160f, 7))
        }
        for (section in 1..12) {
            val center = (2100f / 12) * (section - 0.5f)
            assertEquals(section, gridCellIndex(center, 2100f, 12))
        }
    }
}
