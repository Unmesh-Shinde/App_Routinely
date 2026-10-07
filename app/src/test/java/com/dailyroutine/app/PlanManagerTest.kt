package com.dailyroutine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlanManagerTest {
    private lateinit var context: Context
    private lateinit var manager: PlanManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("plans_pref", Context.MODE_PRIVATE).edit().clear().commit()
        manager = PlanManager(context)
    }

    @Test
    fun mealTemplateApply_writesAllDaysOnceAndClonesMealIds() {
        val breakfast = Meal(id = 101, name = "Oats", mealType = "Breakfast", calories = 320)
        val lunch = Meal(id = 102, name = "Dal Rice", mealType = "Lunch", calories = 650)
        manager.saveMealForDate("2026-07-01", breakfast)
        manager.saveMealForDate("2026-07-02", lunch)

        assertTrue(manager.createMealTemplateFromRange("Two day diet", "2026-07-01", "2026-07-02", allowEmpty = false))
        val template = manager.listMealTemplates().single()

        val result = manager.applyMealTemplateToRange(template.id, "2026-07-10", "2026-07-11")

        assertTrue(result.applied)
        assertEquals("2026-07-11", result.appliedEndDate)
        val dayOneMeals = manager.getMealsForDate("2026-07-10")
        val dayTwoMeals = manager.getMealsForDate("2026-07-11")
        assertEquals(listOf("Oats"), dayOneMeals.map { it.name })
        assertEquals(listOf("Dal Rice"), dayTwoMeals.map { it.name })
        assertNotEquals(101, dayOneMeals.single().id)
        assertNotEquals(102, dayTwoMeals.single().id)
    }

    @Test
    fun mealTemplateApply_rejectsOverlapsAndTooLongRanges() {
        manager.saveMealForDate("2026-07-01", Meal(id = 201, name = "Paneer"))
        assertTrue(manager.createMealTemplateFromRange("One day diet", "2026-07-01", "2026-07-01", allowEmpty = false))
        val template = manager.listMealTemplates().single()

        assertTrue(manager.applyMealTemplateToRange(template.id, "2026-07-10", "2026-07-10").applied)

        val overlap = manager.applyMealTemplateToRange(template.id, "2026-07-10", "2026-07-10")
        assertFalse(overlap.applied)
        assertNotNull(overlap.conflictRange)

        val tooLong = manager.applyMealTemplateToRange(template.id, "2026-07-12", "2026-07-13")
        assertFalse(tooLong.applied)
        assertTrue(tooLong.failureReason.orEmpty().contains("1-day template"))
    }

    @Test
    fun workoutTemplateApply_writesAllDaysOnceAndClonesExerciseIds() {
        val pushups = Exercise(id = 301, name = "Push-ups", sets = 3, reps = "12")
        val squats = Exercise(id = 302, name = "Squats", sets = 4, reps = "15")
        manager.saveExerciseForDate("2026-07-01", pushups)
        manager.saveExerciseForDate("2026-07-02", squats)

        assertTrue(manager.createWorkoutTemplateFromRange("Two day workout", "2026-07-01", "2026-07-02", allowEmpty = false))
        val template = manager.listWorkoutTemplates().single()

        val result = manager.applyWorkoutTemplateToRange(template.id, "2026-07-20", "2026-07-21")

        assertTrue(result.applied)
        val dayOneExercises = manager.getExercisesForDate("2026-07-20")
        val dayTwoExercises = manager.getExercisesForDate("2026-07-21")
        assertEquals(listOf("Push-ups"), dayOneExercises.map { it.name })
        assertEquals(listOf("Squats"), dayTwoExercises.map { it.name })
        assertNotEquals(301, dayOneExercises.single().id)
        assertNotEquals(302, dayTwoExercises.single().id)
    }

    @Test
    fun workoutTemplateApply_rejectsOverlapsAndTooLongRanges() {
        manager.saveExerciseForDate("2026-07-01", Exercise(id = 401, name = "Plank"))
        assertTrue(manager.createWorkoutTemplateFromRange("One day workout", "2026-07-01", "2026-07-01", allowEmpty = false))
        val template = manager.listWorkoutTemplates().single()

        assertTrue(manager.applyWorkoutTemplateToRange(template.id, "2026-07-20", "2026-07-20").applied)

        val overlap = manager.applyWorkoutTemplateToRange(template.id, "2026-07-20", "2026-07-20")
        assertFalse(overlap.applied)
        assertNotNull(overlap.conflictRange)

        val tooLong = manager.applyWorkoutTemplateToRange(template.id, "2026-07-22", "2026-07-23")
        assertFalse(tooLong.applied)
        assertTrue(tooLong.failureReason.orEmpty().contains("1-day template"))
    }
}

