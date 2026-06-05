package com.dailyroutine.app

import java.io.Serializable

enum class ReminderType(
    val label: String,
    val defaultTitle: String,
    val emoji: String,
    val defaultMessage: String
) {
    MEAL(
        "Meal Time", "Meal Reminder", "🍽️",
        "Time for your meal. Eat healthy and mindfully!"
    ),
    EXERCISE(
        "Exercise", "Workout Time", "💪",
        "Time to get moving! Your body will thank you."
    ),
    STAND_UP(
        "Stand Up", "Time to Stretch!", "🪑",
        "You've been sitting too long. Stand up and stretch for a few minutes!"
    ),
    WATER(
        "Hydration", "Drink Water", "💧",
        "Stay hydrated! Drink a glass of water now."
    ),
    SLEEP(
        "Sleep", "Bedtime", "😴",
        "Time to wind down. Get some restful sleep tonight!"
    ),
    MEDITATION(
        "Meditation", "Meditation Time", "🧘",
        "Take a mindful break. A few minutes of meditation will help you focus."
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
