package com.dailyroutine.app

object RoutineIconMapper {
    fun iconForReminder(type: ReminderType): Int = when (type) {
        ReminderType.MEAL -> R.drawable.ic_meal
        ReminderType.EXERCISE -> R.drawable.ic_workout
        ReminderType.HYDRATION -> R.drawable.ic_water
        ReminderType.SLEEP -> R.drawable.ic_sleep
        ReminderType.MEDITATION -> R.drawable.ic_meditation
        ReminderType.DAILY_TODOS -> R.drawable.ic_task
        ReminderType.WEEKEND_TASKS -> R.drawable.ic_weekend_tasks
        ReminderType.MONTHLY_TASKS -> R.drawable.ic_monthly_review
        ReminderType.CUSTOM -> R.drawable.ic_custom_reminder
    }

    fun badgeForReminder(type: ReminderType): Int = when (type) {
        ReminderType.MEAL -> R.drawable.bg_circle_diet
        ReminderType.EXERCISE -> R.drawable.bg_circle_workout
        ReminderType.HYDRATION -> R.drawable.bg_circle_water
        ReminderType.SLEEP -> R.drawable.bg_circle_sleep
        ReminderType.MEDITATION -> R.drawable.bg_circle_walking
        ReminderType.DAILY_TODOS -> R.drawable.bg_circle_reminders
        ReminderType.WEEKEND_TASKS -> R.drawable.bg_circle_reminders
        ReminderType.MONTHLY_TASKS -> R.drawable.bg_circle_reminders
        ReminderType.CUSTOM -> R.drawable.bg_circle_reminders
    }

    fun iconForMealType(mealType: String): Int = when (mealType) {
        "Breakfast" -> R.drawable.ic_breakfast
        "Lunch" -> R.drawable.ic_lunch
        "Dinner" -> R.drawable.ic_dinner
        else -> R.drawable.ic_snack
    }

    fun badgeForMealType(mealType: String): Int = when (mealType) {
        "Breakfast" -> R.drawable.bg_circle_breakfast
        "Lunch" -> R.drawable.bg_circle_diet
        "Dinner" -> R.drawable.bg_circle_dinner
        else -> R.drawable.bg_circle_snack
    }
}
