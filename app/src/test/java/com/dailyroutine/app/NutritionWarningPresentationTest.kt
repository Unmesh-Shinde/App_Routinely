package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionWarningPresentationTest {
    @Test
    fun payloadTooLarge_usesAmberShorteningGuidanceOnCardAndDashboard() {
        val warning = NutritionWarningPresentation.forMeal("ai_payload_too_large")

        assertEquals("Warning: shorten meal details", warning.cardText)
        assertEquals("Meal details exceed the estimate input limit. Shorten the title or description.", warning.dashboardDetail)
        assertEquals(NutritionWarningTone.AMBER, warning.tone)
    }

    @Test
    fun blockedMealText_usesRedEditGuidanceOnCardAndDashboard() {
        val warning = NutritionWarningPresentation.forMeal("ai_content_blocked")

        assertEquals("Warning: edit title or description", warning.cardText)
        assertEquals("Meal text was blocked by the nutrition service. Edit the title or description.", warning.dashboardDetail)
        assertEquals(NutritionWarningTone.RED, warning.tone)
    }

    @Test
    fun exhaustedAutomaticRetries_showAmberCardGuidanceWithoutTextIssuePopupEntry() {
        val warning = NutritionWarningPresentation.forMeal(errorCode = null, autoRetriesExhausted = true)

        assertEquals("Estimate unavailable • edit meal", warning.cardText)
        assertNull(warning.dashboardDetail)
        assertEquals(NutritionWarningTone.AMBER, warning.tone)
    }

    @Test
    fun ordinaryPendingOrSuccessfulMeal_hasNoWarningPresentation() {
        val warning = NutritionWarningPresentation.forMeal(errorCode = null)

        assertNull(warning.cardText)
        assertNull(warning.dashboardDetail)
        assertEquals(NutritionWarningTone.NONE, warning.tone)
    }
}
