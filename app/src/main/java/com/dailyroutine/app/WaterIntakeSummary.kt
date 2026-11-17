package com.dailyroutine.app

import android.content.Context

/** Pure water-summary formatting and adjustment rules shared by the worker and UI. */
object WaterIntakeSummary {
    val adjustmentAmountsMl: List<Int> = listOf(250, 500, 750, 1000, -250, -500, -750, -1000)

    fun adjustedLiters(currentLiters: Double, adjustmentMl: Int): Double =
        (currentLiters + adjustmentMl / 1000.0).coerceAtLeast(0.0)

    fun formatLiters(liters: Double): String =
        String.format(java.util.Locale.US, "%.2f", liters.coerceAtLeast(0.0))
            .trimEnd('0')
            .trimEnd('.')

    fun amountLabel(meal: Meal): String {
        val value = Regex("""(\d+(?:\.\d+)?)""").find(meal.description)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: return meal.description
        val liters = if (meal.description.contains("ml", ignoreCase = true)) value / 1000.0 else value
        return "${formatLiters(liters)} L"
    }

    fun createMeal(date: String, liters: Double): Meal = Meal(
        id = "water-intake-summary-$date".hashCode(),
        name = "Total water intake",
        description = "${formatLiters(liters)} L",
        hour = 23,
        minute = 50,
        isReminderEnabled = false,
        mealType = "Hydration",
        calories = null,
        nutritionInfo = null,
        isWaterIntakeSummary = true
    )

    fun mealsWithSummaryLast(meals: List<Meal>): List<Meal> = meals.sortedWith(
        compareBy<Meal> { it.isWaterIntakeSummary }
            .thenBy { it.hour * 60 + it.minute }
    )

    fun allowsDelete(meal: Meal): Boolean = !meal.isWaterIntakeSummary

    fun totalLiters(context: Context, date: String): Double {
        val doneReminderIds = RoutineProgressStore.getDoneIds(context, date)
        val reminderWater = ReminderManager(context).getAllReminders()
            .filter { it.isEnabled && it.type == ReminderType.HYDRATION && it.id.toString() in doneReminderIds }
            .size * 0.25
        return HealthDataManager(context).getWaterIntake(date) + reminderWater
    }

    fun adjustDate(context: Context, date: String, adjustmentMl: Int): Double {
        val healthDataManager = HealthDataManager(context)
        val currentManual = healthDataManager.getWaterIntake(date)
        val currentTotal = totalLiters(context, date)
        val targetTotal = adjustedLiters(currentTotal, adjustmentMl)
        val reminderTotal = (currentTotal - currentManual).coerceAtLeast(0.0)
        val targetManual = (targetTotal - reminderTotal).coerceAtLeast(0.0)
        healthDataManager.adjustWaterIntake(date, targetManual - currentManual)

        val updatedTotal = totalLiters(context, date)
        PlanManager(context).saveMealForDate(date, createMeal(date, updatedTotal))
        WellnessWidget.refresh(context)
        return updatedTotal
    }
}
