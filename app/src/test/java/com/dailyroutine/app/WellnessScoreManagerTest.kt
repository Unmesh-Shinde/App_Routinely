package com.dailyroutine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WellnessScoreManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("wellness_score_history_pref", Context.MODE_PRIVATE).edit().clear().commit()
        HealthDataManager(context).setDailyStepGoal(10_000)
    }

    @Test
    fun calculateDailyScore_returnsZeroWhenNoInputsAreLogged() {
        val score = WellnessScoreManager.calculateDailyScore(
            context = context,
            steps = 0,
            sleepHours = 0.0,
            workoutDone = 0,
            workoutTotal = 0,
            nutritionDone = 0,
            nutritionTotal = 0
        )

        assertEquals(0, score)
    }

    @Test
    fun calculateDailyScore_returnsHundredForCompleteActiveFactors() {
        val score = WellnessScoreManager.calculateDailyScore(
            context = context,
            steps = 12_000,
            sleepHours = 8.5,
            workoutDone = 3,
            workoutTotal = 3,
            nutritionDone = 4,
            nutritionTotal = 4
        )

        assertEquals(10, score)
    }

    @Test
    fun calculateDailyScore_rescalesOnlyLoggedFactors() {
        val onlyHalfSteps = WellnessScoreManager.calculateDailyScore(
            context = context,
            steps = 5_000,
            sleepHours = 0.0,
            workoutDone = 0,
            workoutTotal = 0,
            nutritionDone = 0,
            nutritionTotal = 0
        )

        assertEquals(5, onlyHalfSteps)
    }

    @Test
    fun calculateDailyScore_appliesDefaultWeightsForPartialDay() {
        val score = WellnessScoreManager.calculateDailyScore(
            context = context,
            steps = 5_000,
            sleepHours = 4.0,
            workoutDone = 1,
            workoutTotal = 2,
            nutritionDone = 1,
            nutritionTotal = 4
        )

        assertEquals(4, score)
    }

    @Test
    fun saveDailyScore_clampsAndReadsScore() {
        assertNull(WellnessScoreManager.getSavedDailyScore(context, "2026-07-09"))

        WellnessScoreManager.saveDailyScore(context, "2026-07-09", 150)
        WellnessScoreManager.saveDailyScore(context, "2026-07-10", -10)

        assertEquals(10, WellnessScoreManager.getSavedDailyScore(context, "2026-07-09"))
        assertEquals(0, WellnessScoreManager.getSavedDailyScore(context, "2026-07-10"))
    }
}

