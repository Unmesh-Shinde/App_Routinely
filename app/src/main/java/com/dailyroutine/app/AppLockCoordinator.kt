package com.dailyroutine.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK

object AppLockCoordinator : Application.ActivityLifecycleCallbacks {
	private var startedActivities = 0
	private var lastBackgroundAtMs = 0L
	private var authenticatedForSession = false
	private var lockLaunchInProgress = false

	fun markUnlocked() {
		authenticatedForSession = true
		lockLaunchInProgress = false
		lastBackgroundAtMs = 0L
	}

	fun markLockSettingsChanged(enabled: Boolean) {
		authenticatedForSession = enabled
		lockLaunchInProgress = false
		lastBackgroundAtMs = 0L
	}

	fun markLockScreenClosed() {
		lockLaunchInProgress = false
	}

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

	override fun onActivityStarted(activity: Activity) {
		val wasBackgrounded = startedActivities == 0
		startedActivities += 1

		if (wasBackgrounded && UserPreferencesStore.isSignedUp(activity) && UserSettingsStore.isAppLockEnabled(activity)) {
			val backgroundDuration = if (lastBackgroundAtMs > 0L) {
				SystemClock.elapsedRealtime() - lastBackgroundAtMs
			} else {
				Long.MAX_VALUE
			}

			if (!authenticatedForSession || UserSettingsStore.shouldLockAfterBackground(activity, backgroundDuration)) {
				authenticatedForSession = false
			}
		}
	}

	override fun onActivityResumed(activity: Activity) {
		if (shouldShowLock(activity)) {
			lockLaunchInProgress = true
			activity.startActivity(Intent(activity, AppLockActivity::class.java))
		}
	}

	override fun onActivityPaused(activity: Activity) = Unit

	override fun onActivityStopped(activity: Activity) {
		startedActivities = (startedActivities - 1).coerceAtLeast(0)
		if (startedActivities == 0) {
			lastBackgroundAtMs = SystemClock.elapsedRealtime()
		}
	}

	override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
	override fun onActivityDestroyed(activity: Activity) = Unit

	private fun shouldShowLock(activity: Activity): Boolean {
		if (activity is AppLockActivity || activity is SignupActivity) return false
		if (lockLaunchInProgress || authenticatedForSession) return false
		if (!UserPreferencesStore.isSignedUp(activity)) return false
		if (!UserSettingsStore.isAppLockEnabled(activity)) return false

		return when (UserSettingsStore.getLockMethod(activity)) {
			UserSettingsStore.LOCK_METHOD_PIN -> UserSettingsStore.hasManualPin(activity)
			UserSettingsStore.LOCK_METHOD_BIOMETRIC -> isBiometricAvailable(activity)
			else -> false
		}
	}

	fun isBiometricAvailable(activity: Activity): Boolean {
		return BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
	}
}