package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WellnessReportOptionsTest {

    @Test
    fun periods_coverEveryFifteenDayStepFromFifteenDaysThroughSixMonths() {
        val periods = WellnessReportPeriods.options

        assertEquals((15..180 step 15).toList(), periods.map { it.days })
        assertEquals("Last 15 days", periods.first().label)
        assertEquals("1 month", periods[1].label)
        assertEquals("1.5 months", periods[2].label)
        assertEquals("2.5 months", periods[4].label)
        assertEquals("6 months", periods.last().label)
    }

    @Test
    fun parameterSelection_selectAllAndIndividualChangesStayInSync() {
        val selection = ReportParameterSelection()
        val parameters = WellnessReportManager.Parameter.values().toSet()

        assertTrue(selection.isAllSelected)
        assertEquals(parameters, selection.selected)

        selection.selectAll(false)
        assertFalse(selection.isAllSelected)
        assertTrue(selection.selected.isEmpty())

        selection.setSelected(WellnessReportManager.Parameter.SLEEP, true)
        assertEquals(setOf(WellnessReportManager.Parameter.SLEEP), selection.selected)
        selection.setSelected(WellnessReportManager.Parameter.STEPS, true)
        assertEquals(
            setOf(WellnessReportManager.Parameter.SLEEP, WellnessReportManager.Parameter.STEPS),
            selection.selected
        )

        selection.selectAll(true)
        assertTrue(selection.isAllSelected)
        assertEquals(parameters, selection.selected)
    }

    @Test
    fun parameterSelection_returnsDefensiveCopiesAndCanClearSelection() {
        val selection = ReportParameterSelection(setOf(WellnessReportManager.Parameter.BMI))
        val selected = selection.selected.toMutableSet()
        selected.clear()

        assertEquals(setOf(WellnessReportManager.Parameter.BMI), selection.selected)
        selection.replaceSelection(emptySet())
        assertTrue(selection.selected.isEmpty())
    }

    @Test
    fun parameterSelection_exportsOnlySelectedDailySummaryAndProfileFields() {
        val selection = ReportParameterSelection(
            setOf(
                WellnessReportManager.Parameter.STEPS,
                WellnessReportManager.Parameter.SLEEP,
                WellnessReportManager.Parameter.TOTAL_STEPS,
                WellnessReportManager.Parameter.PROFILE_METRICS
            )
        )

        assertEquals(
            setOf(WellnessReportManager.Parameter.STEPS, WellnessReportManager.Parameter.SLEEP),
            selection.dailyParameters
        )
        assertEquals(setOf(WellnessReportManager.Parameter.TOTAL_STEPS), selection.summaryParameters)
        assertTrue(selection.includesProfileMetrics)
        assertFalse(
            ReportParameterSelection(setOf(WellnessReportManager.Parameter.STEPS)).includesProfileMetrics
        )
    }
}
