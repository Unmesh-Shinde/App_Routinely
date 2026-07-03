package com.dailyroutine.app

import android.content.Context
import kotlin.math.roundToInt

data class ProfileHealthMetrics(
    val bmi: Double,
    val bmiCategory: String,
    val bmr: Int,
    val idealWeightKg: Double,
    val idealCalories: Int
)

object ProfileHealthMetricsCalculator {
    fun calculate(context: Context): ProfileHealthMetrics? {
        return calculate(
            age = UserPreferencesStore.getUserAge(context),
            heightCm = UserPreferencesStore.getUserHeight(context),
            weightKg = UserPreferencesStore.getUserWeight(context),
            gender = UserPreferencesStore.getUserGender(context)
        )
    }

    fun calculate(age: Int, heightCm: Int, weightKg: Double, gender: String): ProfileHealthMetrics? {
        if (age <= 0 || heightCm <= 0 || weightKg <= 0.0) return null

        val heightM = heightCm / 100.0
        val bmi = weightKg / (heightM * heightM)
        val bmr = when (gender.lowercase()) {
            "male" -> (10 * weightKg) + (6.25 * heightCm) - (5 * age) + 5
            "female" -> (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161
            else -> {
                val male = (10 * weightKg) + (6.25 * heightCm) - (5 * age) + 5
                val female = (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161
                (male + female) / 2.0
            }
        }.roundToInt()

        val idealWeightKg = calculateIdealWeight(heightCm, gender)
        val idealCalories = roundToNearest50(bmr * 1.375)

        return ProfileHealthMetrics(
            bmi = bmi,
            bmiCategory = bmiCategory(bmi),
            bmr = bmr,
            idealWeightKg = idealWeightKg,
            idealCalories = idealCalories
        )
    }

    private fun calculateIdealWeight(heightCm: Int, gender: String): Double {
        val inches = heightCm / 2.54
        val inchesOverFiveFeet = (inches - 60.0).coerceAtLeast(0.0)
        val ideal = when (gender.lowercase()) {
            "male" -> 50.0 + (2.3 * inchesOverFiveFeet)
            "female" -> 45.5 + (2.3 * inchesOverFiveFeet)
            else -> 47.75 + (2.3 * inchesOverFiveFeet)
        }
        return (ideal * 10.0).roundToInt() / 10.0
    }

    private fun bmiCategory(bmi: Double): String {
        return when {
            bmi < 18.5 -> "Underweight"
            bmi < 25.0 -> "Healthy"
            bmi < 30.0 -> "Overweight"
            else -> "Obese"
        }
    }

    private fun roundToNearest50(value: Double): Int {
        return ((value / 50.0).roundToInt() * 50).coerceAtLeast(0)
    }
}
