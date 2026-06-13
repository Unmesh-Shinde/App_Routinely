package com.dailyroutine.app

import android.content.Context
import android.content.SharedPreferences

object UserPreferencesStore {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_USER_NAME = "user_name"
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
