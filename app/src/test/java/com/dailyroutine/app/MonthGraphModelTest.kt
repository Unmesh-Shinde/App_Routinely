package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthGraphModelTest {

    @Test
    fun barData_defaultsRepresentSingleBarGraph() {
        val bar = BarData(
            label = "Week 1",
            date = "1-7 Jul",
            valueDisplay = "72",
            heightPx = 120,
            color = 0xFF009688.toInt()
        )

        assertFalse(bar.isDoubleBar)
        assertNull(bar.backgroundRes)
        assertNull(bar.secondaryValueDisplay)
        assertEquals(0, bar.secondaryHeightPx)
        assertNull(bar.secondaryColor)
        assertNull(bar.secondaryBackgroundRes)
    }

    @Test
    fun barData_supportsDualBarGraphWithIndependentSecondaryStyling() {
        val bar = BarData(
            label = "Week 2",
            date = "8-14 Jul",
            valueDisplay = "10k",
            heightPx = 180,
            color = 0xFF2196F3.toInt(),
            backgroundRes = R.drawable.bg_step_bar,
            isDoubleBar = true,
            secondaryValueDisplay = "45",
            secondaryHeightPx = 96,
            secondaryColor = 0xFFF44336.toInt(),
            secondaryBackgroundRes = R.drawable.bg_calorie_bar
        )

        assertTrue(bar.isDoubleBar)
        assertEquals(R.drawable.bg_step_bar, bar.backgroundRes)
        assertEquals("45", bar.secondaryValueDisplay)
        assertEquals(96, bar.secondaryHeightPx)
        assertEquals(0xFFF44336.toInt(), bar.secondaryColor)
        assertEquals(R.drawable.bg_calorie_bar, bar.secondaryBackgroundRes)
    }

    @Test
    fun monthData_keepsGoalLinesWithBarItemsForMonthlyGraphPages() {
        val goalLine = GoalLineSpec(value = 8.0, maxValue = 12.0, label = "8h goal", color = 0xFF7E57C2.toInt())
        val month = MonthData(
            monthName = "July",
            year = "2026",
            barValues = listOf(
                BarItem(
                    BarData(
                        label = "Week 1",
                        date = "1-7 Jul",
                        valueDisplay = "7.5h",
                        heightPx = 140,
                        color = 0xFF5E35B1.toInt()
                    )
                )
            ),
            goalLines = listOf(goalLine)
        )

        assertEquals("July", month.monthName)
        assertEquals("2026", month.year)
        assertEquals(1, month.barValues.size)
        assertEquals(goalLine, month.goalLines.single())
    }
}

