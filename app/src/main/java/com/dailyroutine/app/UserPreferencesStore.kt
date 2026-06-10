package com.dailyroutine.app

import android.content.Context
import android.content.SharedPreferences

object UserPreferencesStore {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_IS_SIGNED_UP = "is_signed_up"

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
}
