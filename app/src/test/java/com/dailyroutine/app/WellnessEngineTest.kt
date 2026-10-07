package com.dailyroutine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WellnessEngineTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("plans_pref", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("routine_progress_pref", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun shouldEnrichWorkoutMet_isFalseForRecognizedOrExplicitMetExercises() {
        assertFalse(WellnessEngine.shouldEnrichWorkoutMet(Exercise(name = "Push-ups", estimatedMet = 0.0)))
        assertFalse(WellnessEngine.shouldEnrichWorkoutMet(Exercise(name = "Pull ups", estimatedMet = 0.0)))
        assertFalse(WellnessEngine.shouldEnrichWorkoutMet(Exercise(name = "High knees", estimatedMet = 0.0)))
        assertFalse(WellnessEngine.shouldEnrichWorkoutMet(Exercise(name = "Custom workout", estimatedMet = 6.2)))
    }

    @Test
    fun shouldEnrichWorkoutMet_isTrueForUnknownOrAmbiguousExerciseWithoutMet() {
        assertTrue(WellnessEngine.shouldEnrichWorkoutMet(Exercise(name = "Animal flow", targetArea = "Mobility", estimatedMet = 0.0)))
        assertTrue(WellnessEngine.shouldEnrichWorkoutMet(Exercise(name = "Push press", targetArea = "Shoulders", estimatedMet = 0.0)))
        assertFalse(WellnessEngine.shouldEnrichWorkoutMet(Exercise(name = "Workout", estimatedMet = 0.0)))
    }

    @Test
    fun getLocalWorkoutMet_returnsSpecificMetForKnownExercisesAndIntensityFallback() {
        assertEquals(3.8, WellnessEngine.getLocalWorkoutMet(Exercise(name = "Push-ups")), 0.001)
        assertEquals(3.8, WellnessEngine.getLocalWorkoutMet(Exercise(name = "Pull ups")), 0.001)
        assertEquals(8.0, WellnessEngine.getLocalWorkoutMet(Exercise(name = "Mountain climbers")), 0.001)
        assertEquals(5.0, WellnessEngine.getLocalWorkoutMet(Exercise(name = "Squats")), 0.001)
        assertEquals(6.0, WellnessEngine.getLocalWorkoutMet(Exercise(name = "Unknown lift", intensity = 80)), 0.001)
        assertEquals(3.0, WellnessEngine.getLocalWorkoutMet(Exercise(name = "Unknown lift", intensity = 40)), 0.001)
    }

    @Test
    fun isValidEnrichedWorkoutMet_rejectsImplausibleTypeSpecificValues() {
        assertTrue(WellnessEngine.isValidEnrichedWorkoutMet(Exercise(name = "Burpees"), 10.0))
        assertFalse(WellnessEngine.isValidEnrichedWorkoutMet(Exercise(name = "Stretching"), 11.0))
        assertFalse(WellnessEngine.isValidEnrichedWorkoutMet(Exercise(name = "Jumping jacks"), 16.0))
    }

    @Test
    fun calculateActiveBurnForDate_prioritizesSyncedActiveCalories() {
        val date = "2026-07-09"
        HealthDataManager(context).saveHistoricalActiveCalories(date, 500.0)
        
        // Even with steps, it should return 500.0
        val burn = WellnessEngine.calculateActiveBurnForDate(context, date, steps = 10000, weight = 70.0)
        
        assertEquals(500.0, burn, 0.001)
    }

    @Test
    fun calculateActiveBurnForDate_fallsBackToMetWhenNoSyncedActiveCalories() {
        val date = "2026-07-09"
        // Ensure no synced active calories
        context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE).edit().remove("hist_active_cals_$date").commit()
        
        // With 10000 steps (~7.6km) over ~100 min, burn should be significant
        val burn = WellnessEngine.calculateActiveBurnForDate(context, date, steps = 10000, weight = 70.0)
        
        assertTrue(burn > 200.0)
    }

    @Test
    fun calculateActiveBurnForDate_usesSyncedDistanceOverStepEstimate() {
        val date = "2026-07-09"
        val hdm = HealthDataManager(context)
        // Sync 5.0km instead of the step-based estimate (~0.7km)
        hdm.saveHistoricalDistance(date, 5.0)
        
        val burn = WellnessEngine.calculateActiveBurnForDate(context, date, steps = 10000, weight = 70.0)
        
        // With 5km synced and ~100 min movement, burn should be significant (>200 kcal)
        assertTrue(burn > 200.0) 
    }

    @Test
    fun calculateActiveBurnForDate_usesContinuousMetScaling() {
        val date1 = "2026-07-09"
        val date2 = "2026-07-10"
        val hdm = HealthDataManager(context)
        UserPreferencesStore.setUserWeight(context, 70.0)

        // Case 1: 5.0 km in 60 min (5.0 km/h)
        hdm.saveHistoricalDistance(date1, 5.0)
        // calculateDurationMin(10000 steps) = 100 min. Let's use steps to set duration.
        val burn5kmh = WellnessEngine.calculateActiveBurnForDate(context, date1, steps = 6000, weight = 70.0)
        
        // Case 2: 6.0 km in 60 min (6.0 km/h)
        hdm.saveHistoricalDistance(date2, 6.0)
        val burn6kmh = WellnessEngine.calculateActiveBurnForDate(context, date2, steps = 6000, weight = 70.0)
        
        assertTrue(burn6kmh > burn5kmh)
        // Ratio should be linear based on MET change (not fixed tiers)
        // MET at 5kmh ~3.38. MET at 6kmh ~3.85. Ratio ~1.14
        assertTrue(burn6kmh / burn5kmh in 1.1..1.2)
    }

    @Test
    fun calculateActiveBurnForDate_includesHistoricalStepsAndHeartPoints() {
        HealthDataManager(context).saveHistoricalHeartPoints("2026-07-09", 10.0)

        val burn = WellnessEngine.calculateActiveBurnForDate(context, "2026-07-09", steps = 1000, weight = 70.0)

        assertTrue(burn > 40.0)
    }

    @Test
    fun calculateActiveBurnForDate_treatsCardioMinutesAsTotalDuration() {
        val date = "2026-07-09"
        val exercise = Exercise(id = 42, name = "Jumping Jacks", sets = 3, reps = "10 min", intensity = 50)
        PlanManager(context).saveExerciseForDate(date, exercise)
        RoutineProgressStore.setDoneStatus(context, date, exercise.id, true)

        val burn = WellnessEngine.calculateActiveBurnForDate(context, date, steps = 0, weight = 70.0)

        assertTrue(burn in 95.0..100.0)
    }

    @Test
    fun estimateWorkoutBurnForExercise_usesStructuredCardioDuration() {
        val burn = WellnessEngine.estimateWorkoutBurnForExercise(
            Exercise(name = "Cardio", exerciseType = "Cardio", durationSeconds = 600, intensity = 50),
            weightKg = 70.0
        )

        assertEquals(85.75, burn, 0.01)
    }

    @Test
    fun estimateWorkoutBurnForExercise_usesStructuredHiitRoundsWorkAndRest() {
        val burn = WellnessEngine.estimateWorkoutBurnForExercise(
            Exercise(
                name = "Custom intervals",
                exerciseType = "HIIT / Circuit",
                rounds = 4,
                workSeconds = 40,
                restSeconds = 20,
                intensity = 50
            ),
            weightKg = 70.0
        )

        assertTrue(burn in 28.0..29.5)
    }

    @Test
    fun estimateWorkoutBurnForExercise_usesWeightedStrengthTypeAndAddedLoad() {
        val burn = WellnessEngine.estimateWorkoutBurnForExercise(
            Exercise(
                name = "Goblet Squat",
                exerciseType = "Strength / Weighted",
                sets = 3,
                reps = "10",
                restSeconds = 90,
                addedWeightKg = 16.0,
                intensity = 75
            ),
            weightKg = 70.0
        )

        assertTrue(burn in 17.0..19.0)
    }

    @Test
    fun estimateWorkoutBurnForExercise_usesCardioDistanceToRefineRunningMet() {
        val burnWithoutDistance = WellnessEngine.estimateWorkoutBurnForExercise(
            Exercise(name = "Running", exerciseType = "Cardio", durationSeconds = 1800, intensity = 50),
            weightKg = 70.0
        )
        val burnWithDistance = WellnessEngine.estimateWorkoutBurnForExercise(
            Exercise(name = "Running", exerciseType = "Cardio", durationSeconds = 1800, distanceKm = 5.0, intensity = 50),
            weightKg = 70.0
        )

        assertTrue(burnWithDistance > burnWithoutDistance)
        assertTrue(burnWithDistance in 380.0..390.0)
    }

    @Test
    fun calculateBMR_returnsCorrectValueForMaleAndFemale() {
        UserPreferencesStore.setUserAge(context, 25)
        UserPreferencesStore.setUserHeight(context, 175.0)
        UserPreferencesStore.setUserWeight(context, 70.0)

        UserPreferencesStore.setUserGender(context, "Male")
        // (10 * 70) + (6.25 * 175) - (5 * 25) + 5 = 700 + 1093.75 - 125 + 5 = 1673.75
        assertEquals(1673.75, WellnessEngine.calculateBMR(context), 0.01)

        UserPreferencesStore.setUserGender(context, "Female")
        // (10 * 70) + (6.25 * 175) - (5 * 25) - 161 = 700 + 1093.75 - 125 - 161 = 1507.75
        assertEquals(1507.75, WellnessEngine.calculateBMR(context), 0.01)
    }

    @Test
    fun calculateTEF_returnsTenPercentOfIntake() {
        assertEquals(200.0, WellnessEngine.calculateTEF(2000), 0.01)
        assertEquals(15.5, WellnessEngine.calculateTEF(155), 0.01)
        assertEquals(0.0, WellnessEngine.calculateTEF(0), 0.01)
    }

    @Test
    fun getAdaptiveCalorieInsight_returnsNullWhenInsufficientData() = runBlocking {
        val hdm = HealthDataManager(context)
        hdm.saveWeight("2026-07-24", 80.0)
        hdm.saveWeight("2026-07-23", 80.0)

        val insight = WellnessEngine.getAdaptiveCalorieInsightRoom(context)
        assertTrue(insight == null)
    }

    @Test
    fun getAdaptiveCalorieInsight_triggersSuggestionWhenWeightStableForLoseGoal() = runBlocking {
        val hdm = HealthDataManager(context)
        UserPreferencesStore.setUserGoal(context, "Lose Weight")
        
        // Mock 21 days of stable weight
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        repeat(21) {
            hdm.saveWeight(sdf.format(cal.time), 80.0)
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }

        val insight = WellnessEngine.getAdaptiveCalorieInsightRoom(context)
        assertTrue(insight != null)
        assertTrue(insight?.message?.contains("consider reducing") == true)
    }

    @Test
    fun getAdaptiveCalorieInsight_triggersEncouragementWhenWeightDroppingForLoseGoal() = runBlocking {
        val hdm = HealthDataManager(context)
        UserPreferencesStore.setUserGoal(context, "Lose Weight")
        
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        
        // Recent weight: 78kg (avg 78)
        repeat(7) {
            hdm.saveWeight(sdf.format(cal.time), 78.0)
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        // Middle week: skip
        repeat(7) { cal.add(java.util.Calendar.DAY_OF_YEAR, -1) }
        // Oldest weight: 80kg (avg 80)
        repeat(7) {
            hdm.saveWeight(sdf.format(cal.time), 80.0)
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }

        val insight = WellnessEngine.getAdaptiveCalorieInsightRoom(context)
        assertTrue(insight != null)
        assertTrue(insight?.message?.contains("Great job") == true)
        assertEquals("positive", insight?.type)
    }
}

