package com.dailyroutine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

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

    @Test
    fun categoryLimitCheck_enforcesMaxTwoMealsPerCategoryPerDay() {
        val date = LocalDate.now().toString()
        assertFalse(manager.isCategoryLimitReached(date, "Breakfast"))

        val meal1 = Meal(id = 501, name = "Oatmeal", mealType = "Breakfast")
        manager.saveMealForDate(date, meal1)
        assertFalse(manager.isCategoryLimitReached(date, "Breakfast"))

        val meal2 = Meal(id = 502, name = "Eggs", mealType = "Breakfast")
        manager.saveMealForDate(date, meal2)
        assertTrue(manager.isCategoryLimitReached(date, "Breakfast"))

        // Exclude meal2 when editing meal2 -> should allow save
        assertFalse(manager.isCategoryLimitReached(date, "Breakfast", excludeMealId = 502))
    }

    @Test
    fun mealTemplateApply_rejectsCategoryCapExceededWithoutPartialWrites() {
        val sourceDate = "2026-07-01"
        val targetDate = "2026-07-10"
        manager.saveMealForDate(sourceDate, Meal(id = 511, name = "Oats", mealType = "Breakfast"))
        manager.saveMealForDate(sourceDate, Meal(id = 512, name = "Eggs", mealType = "Breakfast"))
        manager.saveMealForDate(targetDate, Meal(id = 513, name = "Fruit", mealType = "Breakfast"))
        assertTrue(manager.createMealTemplateFromRange("Breakfast template", sourceDate, sourceDate, allowEmpty = false))
        val template = manager.listMealTemplates().single()

        val result = manager.applyMealTemplateToRange(template.id, targetDate, targetDate)

        assertFalse(result.applied)
        assertTrue(result.failureReason.orEmpty().contains("limit of $MAX_MEALS_PER_CATEGORY_PER_DAY"))
        assertEquals(listOf("Fruit"), manager.getMealsForDate(targetDate).map { it.name })
    }

    @Test
    fun nutritionTextErrors_areFilteredSortedByOriginalAddTimeAndDisappearAfterResolution() {
        val earlier = Meal(
            id = 650,
            name = "Long breakfast description",
            mealType = "Breakfast",
            addedTimestamp = 1_000L,
            nutritionErrorCode = "ai_payload_too_large"
        )
        val later = Meal(
            id = 651,
            name = "Blocked lunch text",
            mealType = "Lunch",
            addedTimestamp = 2_000L,
            nutritionErrorCode = "ai_content_blocked"
        )
        manager.saveMealForDate("2026-07-02", later)
        manager.saveMealForDate("2026-07-01", earlier)
        manager.saveMealForDate("2026-07-03", Meal(id = 652, name = "Transient", nutritionErrorCode = "network_error"))
        manager.saveMealForDate("2026-07-04", Meal(id = 653, name = "Water", isWaterIntakeSummary = true, nutritionErrorCode = "ai_content_blocked"))

        val issues = manager.getMealsWithNutritionTextErrors()
        assertEquals(listOf(earlier.id, later.id), issues.map { it.second.id })
        assertEquals(listOf("2026-07-01", "2026-07-02"), issues.map { it.first })

        assertTrue(
            manager.updateMealNutrition(
                date = "2026-07-01",
                mealId = earlier.id,
                info = MealNutritionInfo(calories = 240),
                markPendingFalse = false
            )
        )
        assertEquals(listOf(later.id), manager.getMealsWithNutritionTextErrors().map { it.second.id })
        assertNull(manager.getMealsForDate("2026-07-01").single().nutritionErrorCode)
    }

    @Test
    fun pendingOvernightMeals_returnsSortedFIFOAndUpdatesSuccessfully() {
        val date1 = "2026-07-01"
        val date2 = "2026-07-02"

        val mealLater = Meal(id = 601, name = "Salad", mealType = "Lunch", isPendingOvernightRetry = true, addedTimestamp = 2000L)
        val mealEarlier = Meal(id = 602, name = "Fruit Bowl", mealType = "Breakfast", isPendingOvernightRetry = true, addedTimestamp = 1000L)
        val mealNormal = Meal(id = 603, name = "Rice", mealType = "Dinner", isPendingOvernightRetry = false)

        manager.saveMealForDate(date1, mealLater)
        manager.saveMealForDate(date2, mealEarlier)
        manager.saveMealForDate(date1, mealNormal)

        val pending = manager.getPendingOvernightMeals()
        assertEquals(2, pending.size)
        assertEquals("Fruit Bowl", pending[0].second.name)
        assertEquals("Salad", pending[1].second.name)

        val updated = manager.updateMealNutrition(date2, 602, MealNutritionInfo(calories = 250), markPendingFalse = true)
        assertTrue(updated)

        val remainingPending = manager.getPendingOvernightMeals()
        assertEquals(1, remainingPending.size)
        assertEquals("Salad", remainingPending[0].second.name)
    }

    @Test
    fun strategyCTokenCapping_grantsReplacementCreditOnDelete() {
        val date = "2026-07-01"
        val category = "Breakfast"

        assertEquals(0, manager.getAiTokenCount(date, category))
        assertFalse(manager.isAiTokenLimitReached(date, category))

        manager.incrementAiTokenCount(date, category)
        manager.incrementAiTokenCount(date, category)
        assertEquals(2, manager.getAiTokenCount(date, category))
        assertTrue(manager.isAiTokenLimitReached(date, category))

        // Deleting a meal grants 1 replacement credit
        val meal = Meal(id = 701, name = "Oats", mealType = category)
        manager.saveMealForDate(date, meal)
        manager.deleteMealForDate(date, meal)

        assertEquals(1, manager.getReplacementCreditCount(date, category))
        assertFalse(manager.isAiTokenLimitReached(date, category))

        manager.incrementAiTokenCount(date, category)
        assertTrue(manager.isAiTokenLimitReached(date, category))
    }

    @Test
    fun primaryMealQualifyingRule_and_disciplineStreakSystem() {
        // Snack alone does NOT qualify as a primary meal
        manager.saveMealForDate("2026-07-01", Meal(id = 801, name = "Chips", mealType = "Snack"))
        assertFalse(manager.hasPrimaryMealLogged("2026-07-01"))

        // Breakfast qualifies as primary meal
        manager.saveMealForDate("2026-07-02", Meal(id = 802, name = "Eggs", mealType = "Breakfast"))
        assertTrue(manager.hasPrimaryMealLogged("2026-07-02"))

        // Build 3 qualifying primary days (July 2, July 3, July 4)
        manager.saveMealForDate("2026-07-03", Meal(id = 803, name = "Rice", mealType = "Lunch"))
        manager.saveMealForDate("2026-07-04", Meal(id = 804, name = "Soup", mealType = "Dinner"))

        // July 1 (snack only) does not advance streak.
        manager.evaluateStreaks("2026-07-03") // Evaluates July 2
        assertFalse(manager.isEditBonusActive("2026-07-03"))

        manager.evaluateStreaks("2026-07-04") // Evaluates July 3
        assertFalse(manager.isEditBonusActive("2026-07-04"))

        manager.evaluateStreaks("2026-07-05") // Evaluates July 4 (3rd qualifying day)
        assertTrue(manager.isEditBonusActive("2026-07-05"))
        assertTrue(manager.isDeleteBonusActive("2026-07-05"))
        assertEquals(3, manager.getBonusAllowedEdits("2026-07-05"))
        assertEquals(2, manager.getBonusAllowedReplacementCredits("2026-07-05"))
        assertEquals("both", manager.consumePendingStreakPopup())

        // Active for exactly three dates: July 5, July 6, and July 7.
        assertTrue(manager.isEditBonusActive("2026-07-06"))
        assertTrue(manager.isEditBonusActive("2026-07-07"))
        assertFalse(manager.isEditBonusActive("2026-07-08"))
        assertEquals(2, manager.getBonusAllowedEdits("2026-07-08"))
    }
}

