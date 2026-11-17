package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GeminiClientRetryMetadataTest {
    @Test
    fun automaticRetry_includesCategoryWithoutClientControlledDayBoundary() {
        val body = GeminiClient.createMealNutritionRequestBody(
            title = "Dal rice",
            description = "One bowl",
            automaticRetryCategory = "Lunch"
        )

        assertEquals("Dal rice", body.getString("title"))
        assertEquals("One bowl", body.getString("description"))
        assertEquals("automatic_retry", body.getString("request_kind"))
        assertEquals("Lunch", body.getString("meal_category"))
        assertFalse(body.has("client_timezone_offset_minutes"))
    }

    @Test
    fun userInitiatedEstimate_doesNotClaimAutomaticRetryBudget() {
        val body = GeminiClient.createMealNutritionRequestBody("Oats", "With milk")

        assertFalse(body.has("request_kind"))
        assertFalse(body.has("meal_category"))
        assertFalse(body.has("client_timezone_offset_minutes"))
    }

    @Test
    fun budgetError_preservesServerResetTimestampForWorkerDeferral() {
        val parsed = GeminiClient.parseBackendError(
            """{"errorCode":"ai_retry_budget_exhausted","scope":"user_budget","retryable":true,"retryAt":1794960000000}""",
            429
        )

        assertEquals("ai_retry_budget_exhausted", parsed.errorCode)
        assertEquals(1794960000000L, parsed.retryAtMillis)
        assertEquals("user_budget", parsed.scope)
        assertEquals(true, parsed.retryable)
    }
}
