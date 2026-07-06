package com.dailyroutine.app

import android.app.Application

class RoutinelyApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		UserSettingsStore.applySavedTheme(this)
		registerActivityLifecycleCallbacks(AppLockCoordinator)
	}
}