package com.dailyroutine.app

import android.content.Context
import android.content.SharedPreferences

object UserPreferencesStore {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_AGE = "user_age"
    private const val KEY_USER_HEIGHT = "user_height"
    private const val KEY_USER_GENDER = "user_gender"
    private const val KEY_IS_SIGNED_UP = "is_signed_up"
    private const val KEY_STREAK_COUNT = "streak_count"
    private const val KEY_LAST_STREAK_UPDATE = "last_streak_update"

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

    fun getUserGender(context: Context): String = getPrefs(context).getString(KEY_USER_GENDER, "Male") ?: "Male"
    fun setUserGender(context: Context, gender: String) = getPrefs(context).edit().putString(KEY_USER_GENDER, gender).apply()

    fun getStreakCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_STREAK_COUNT, 0)
    }

    fun setStreakCount(context: Context, count: Int) {
        getPrefs(context).edit().putInt(KEY_STREAK_COUNT, count).apply()
    }

    fun getLastStreakUpdate(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_STREAK_UPDATE, "") ?: ""
    }

    fun setLastStreakUpdate(context: Context, date: String) {
        getPrefs(context).edit().putString(KEY_LAST_STREAK_UPDATE, date).apply()
    }
}
