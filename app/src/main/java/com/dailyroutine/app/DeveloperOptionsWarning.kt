package com.dailyroutine.app

import android.content.Context
import android.provider.Settings

internal object DeveloperOptionsWarning {
    fun areEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) != 0
    }.getOrDefault(false)
}
