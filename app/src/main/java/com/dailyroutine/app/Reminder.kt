package com.dailyroutine.app

import java.io.Serializable

enum class ReminderType(
    val label: String,
    val defaultTitle: String,
    val emoji: String,
    val defaultMessage: String
) {
    HYDRATION(
        "Hydration", "Drink Water", "💧",
        "Stay hydrated! Time to drink a glass of water."
    ),
    MEDITATION(
        "Meditation", "Meditation Time", "🧘",
        "Take a mindful break. A few minutes of meditation will help you focus."
    ),
    DAILY_TODOS(
        "Daily To Do's", "To-Do List", "📝",
        "Check your tasks for today. Stay productive!"
    ),
    WEEKEND_TASKS(
        "Weekend Tasks", "Weekend Focus", "🏡",
        "Don't forget your weekend goals and chores."
    ),
    MONTHLY_TASKS(
        "Monthly Tasks", "Monthly Review", "📅",
        "Time to look at your monthly goals and progress."
    ),
    CUSTOM(
        "Custom", "My Reminder", "⏰",
        "It's time for your scheduled activity!"
    )
}

data class Reminder(
    val id: Int = System.currentTimeMillis().toInt(),
    val title: String = "",
    val type: ReminderType = ReminderType.CUSTOM,
    val hour: Int = 8,
    val minute: Int = 0,
    val isIntervalBased: Boolean = false,
    val intervalMinutes: Int = 60,
    val repeatDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val isEnabled: Boolean = true,
    val dishType: String = "",
    val ingredients: String = "",
    val isHidden: Boolean = false
) : Serializable
