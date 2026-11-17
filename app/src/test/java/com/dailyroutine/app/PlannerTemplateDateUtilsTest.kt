package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PlannerTemplateDateUtilsTest {
    @Test
    fun daysBetweenInclusive_handlesLeapDayAndInvalidDates() {
        assertEquals(3, PlannerTemplateDateUtils.daysBetweenInclusive("2024-02-28", "2024-03-01"))
        assertEquals(1, PlannerTemplateDateUtils.daysBetweenInclusive("2024-02-29", "2024-02-29"))
        assertEquals(0, PlannerTemplateDateUtils.daysBetweenInclusive("2024-03-02", "2024-03-01"))
        assertEquals(0, PlannerTemplateDateUtils.daysBetweenInclusive("not-a-date", "2024-03-01"))
    }

    @Test
    fun buildDateRangePreview_usesFullShortRangesAndCompactLongRanges() {
        assertEquals("2026-01-01", PlannerTemplateDateUtils.buildDateRangePreview("2026-01-01", 1))
        assertEquals(
            "2026-01-01, 2026-01-02, 2026-01-03, 2026-01-04, 2026-01-05",
            PlannerTemplateDateUtils.buildDateRangePreview("2026-01-01", 5)
        )
        assertEquals(
            "2026-01-01, 2026-01-02, ... 2026-01-07",
            PlannerTemplateDateUtils.buildDateRangePreview("2026-01-01", 7)
        )
        assertEquals("2026-01-01", PlannerTemplateDateUtils.buildDateRangePreview("2026-01-01", 0))
    }
}
