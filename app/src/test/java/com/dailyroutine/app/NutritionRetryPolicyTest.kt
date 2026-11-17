package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class NutritionRetryPolicyTest {
    @Test
    fun retryableDayFailures_scheduleThirtyThenFortyFiveThenSixtyMinutes() {
        var now = localTime(10, 0)
        var meal = NutritionRetryPolicy.queueFirstRetry(
            Meal(isPendingOvernightRetry = true),
            "network_error",
            now
        )
        assertEquals(1, meal.nutritionDayRetriesUsed)
        assertEquals(now + TimeUnit.MINUTES.toMillis(30), meal.nextNutritionRetryAt)
        now = meal.nextNutritionRetryAt

        listOf(45L, 60L).forEachIndexed { index, minutes ->
            meal = NutritionRetryPolicy.afterRetryableFailure(meal, "network_error", now)
            assertEquals(index + 2, meal.nutritionDayRetriesUsed)
            assertEquals("day", meal.nutritionRetryStage)
            assertEquals(now + TimeUnit.MINUTES.toMillis(minutes), meal.nextNutritionRetryAt)
            assertTrue(meal.isPendingOvernightRetry)
            now = meal.nextNutritionRetryAt
        }

        meal = NutritionRetryPolicy.afterRetryableFailure(meal, "network_error", now)
        assertEquals("overnight", meal.nutritionRetryStage)
        assertEquals(localTime(0, 0, day = 18), meal.nextNutritionRetryAt)
    }

    @Test
    fun overnightSequence_usesMidnightThenTwoSixAndEightAmOnly() {
        val anchor = localTime(0, 0, day = 18)
        var meal = Meal(
            isPendingOvernightRetry = true,
            nutritionRetryStage = "overnight",
            nutritionOvernightWindowStartAt = anchor,
            nextNutritionRetryAt = anchor
        )

        val expectedHours = listOf(2, 6, 8)
        expectedHours.forEach { hour ->
            val failedAt = meal.nextNutritionRetryAt + TimeUnit.MINUTES.toMillis(1)
            meal = NutritionRetryPolicy.afterRetryableFailure(meal, "network_error", failedAt)
            val expected = localTime(hour, 0, day = 18)
            assertEquals(expected, meal.nextNutritionRetryAt)
            assertTrue(meal.isPendingOvernightRetry)
        }

        meal = NutritionRetryPolicy.afterRetryableFailure(
            meal,
            "network_error",
            meal.nextNutritionRetryAt + TimeUnit.MINUTES.toMillis(1)
        )
        assertFalse(meal.isPendingOvernightRetry)
        assertTrue(meal.nutritionAutoRetriesExhausted)
        assertEquals(0L, meal.nextNutritionRetryAt)
    }

    @Test
    fun lateNightFailure_startsAtNextMidnightAndPermanentFailureStopsQueue() {
        val lateFailure = localTime(23, 10)
        val retry = NutritionRetryPolicy.afterRetryableFailure(
            Meal(isPendingOvernightRetry = true, nutritionRetryStage = "day", nutritionDayRetriesUsed = 4),
            "network_error",
            lateFailure
        )
        assertEquals(localTime(0, 0, day = 18), retry.nextNutritionRetryAt)

        val permanent = NutritionRetryPolicy.afterPermanentFailure(retry, "ai_content_blocked")
        assertFalse(permanent.isPendingOvernightRetry)
        assertTrue(permanent.nutritionAutoRetriesExhausted)
        assertEquals("ai_content_blocked", permanent.nutritionErrorCode)
        assertNull(permanent.nutritionRetryStage)
    }

    @Test
    fun anchoredOvernightWindow_hasNoRetryAfterEightAm() {
        val midnight = localTime(0, 0, day = 18)
        assertNull(NutritionRetryPolicy.nextOvernightSlot(localTime(8, 1, day = 18), midnight))
    }

    @Test
    fun budgetDenial_defersSameRetryWithoutAdvancingCountersOrExhaustingMeal() {
        val current = Meal(
            isPendingOvernightRetry = true,
            nutritionRetryStage = "overnight",
            nutritionDayRetriesUsed = 3,
            nutritionOvernightRetriesUsed = 1,
            nextNutritionRetryAt = localTime(2, 0, day = 18),
            nutritionOvernightWindowStartAt = localTime(0, 0, day = 18)
        )
        val now = localTime(2, 0, day = 18)
        val resetAt = localTime(0, 0, day = 19)

        val deferred = NutritionRetryPolicy.deferForBudget(current, "ai_retry_budget_exhausted", resetAt, now)

        assertTrue(deferred.isPendingOvernightRetry)
        assertFalse(deferred.nutritionAutoRetriesExhausted)
        assertEquals(current.nutritionRetryStage, deferred.nutritionRetryStage)
        assertEquals(current.nutritionDayRetriesUsed, deferred.nutritionDayRetriesUsed)
        assertEquals(current.nutritionOvernightRetriesUsed, deferred.nutritionOvernightRetriesUsed)
        assertEquals(resetAt, deferred.nextNutritionRetryAt)
        assertEquals("ai_retry_budget_exhausted", deferred.nutritionErrorCode)
    }

    @Test
    fun missingOrExpiredBudgetReset_defersThirtyMinutesWithoutAdvancingAttempt() {
        val now = localTime(10, 0)
        val current = Meal(isPendingOvernightRetry = true, nutritionDayRetriesUsed = 2, nutritionRetryStage = "day")

        val deferred = NutritionRetryPolicy.deferForBudget(current, "ai_budget_unavailable", now, now)

        assertEquals(now + TimeUnit.MINUTES.toMillis(30), deferred.nextNutritionRetryAt)
        assertEquals(2, deferred.nutritionDayRetriesUsed)
        assertEquals("day", deferred.nutritionRetryStage)
        assertTrue(deferred.isPendingOvernightRetry)
    }

    private fun localTime(hour: Int, minute: Int, day: Int = 17): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
