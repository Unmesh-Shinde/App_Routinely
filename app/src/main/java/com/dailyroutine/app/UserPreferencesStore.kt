package com.dailyroutine.app

import android.content.Context
import android.content.SharedPreferences

object UserPreferencesStore {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_AGE = "user_age"
    private const val KEY_USER_HEIGHT = "user_height"
    private const val KEY_USER_WEIGHT = "user_weight"
    private const val KEY_USER_GENDER = "user_gender"
    private const val KEY_IS_SIGNED_UP = "is_signed_up"

    // Wellness Score Weights
    private const val KEY_WEIGHT_SLEEP = "weight_sleep"
    private const val KEY_WEIGHT_WORKOUT = "weight_workout"
    private const val KEY_WEIGHT_NUTRITION = "weight_nutrition"
    private const val KEY_WEIGHT_STEPS = "weight_steps"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isSignedUp(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_SIGNED_UP, false)
    }

    fun setSignedUp(context: Context, signedUp: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_SIGNED_UP, signedUp).apply()
    }

    fun getUserName(context: Context): String {
        return getPrefs(context).getString(KEY_USER_NAME, "User") ?: "User"
    }

    fun setUserName(context: Context, name: String) {
        getPrefs(context).edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserAge(context: Context): Int = getPrefs(context).getInt(KEY_USER_AGE, 25)
    fun setUserAge(context: Context, age: Int) = getPrefs(context).edit().putInt(KEY_USER_AGE, age).apply()

    fun getUserHeight(context: Context): Int = getPrefs(context).getInt(KEY_USER_HEIGHT, 170)
    fun setUserHeight(context: Context, height: Int) = getPrefs(context).edit().putInt(KEY_USER_HEIGHT, height).apply()

    fun getUserWeight(context: Context): Double = getPrefs(context).getFloat(KEY_USER_WEIGHT, 70.0f).toDouble()
    fun setUserWeight(context: Context, weight: Double) = getPrefs(context).edit().putFloat(KEY_USER_WEIGHT, weight.toFloat()).apply()

    fun getUserGender(context: Context): String = getPrefs(context).getString(KEY_USER_GENDER, "Male") ?: "Male"
    fun setUserGender(context: Context, gender: String) = getPrefs(context).edit().putString(KEY_USER_GENDER, gender).apply()


    // Wellness Score Weight Accessors
    fun getSleepWeight(context: Context): Int = getPrefs(context).getInt(KEY_WEIGHT_SLEEP, 35)
    fun setSleepWeight(context: Context, weight: Int) = getPrefs(context).edit().putInt(KEY_WEIGHT_SLEEP, weight).apply()

    fun getWorkoutWeight(context: Context): Int = getPrefs(context).getInt(KEY_WEIGHT_WORKOUT, 25)
    fun setWorkoutWeight(context: Context, weight: Int) = getPrefs(context).edit().putInt(KEY_WEIGHT_WORKOUT, weight).apply()

    fun getNutritionWeight(context: Context): Int = getPrefs(context).getInt(KEY_WEIGHT_NUTRITION, 25)
    fun setNutritionWeight(context: Context, weight: Int) = getPrefs(context).edit().putInt(KEY_WEIGHT_NUTRITION, weight).apply()

    fun getStepsWeight(context: Context): Int = getPrefs(context).getInt(KEY_WEIGHT_STEPS, 15)
    fun setStepsWeight(context: Context, weight: Int) = getPrefs(context).edit().putInt(KEY_WEIGHT_STEPS, weight).apply()
}
