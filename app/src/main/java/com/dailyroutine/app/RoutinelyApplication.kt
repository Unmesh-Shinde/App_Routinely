package com.dailyroutine.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RoutinelyApplication : Application() {
	private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	override fun onCreate() {
		super.onCreate()
		initializeFirebaseSecurity()
		UserSettingsStore.applySavedTheme(this)
		registerActivityLifecycleCallbacks(AppLockCoordinator)
		applicationScope.launch {
			HealthMetricsBackfillManager(this@RoutinelyApplication).backfillIfNeeded()
		}
	}

	private fun initializeFirebaseSecurity() {
		FirebaseApp.initializeApp(this)

		val appCheck = FirebaseAppCheck.getInstance()
		if (BuildConfig.DEBUG) {
			appCheck.installAppCheckProviderFactory(
				DebugAppCheckProviderFactory.getInstance(),
			)
		} else {
			appCheck.installAppCheckProviderFactory(
				PlayIntegrityAppCheckProviderFactory.getInstance(),
			)
		}

		FirebaseAuth.getInstance().signInAnonymously()
			.addOnFailureListener { error ->
				Log.e("RoutinelyFirebase", "Anonymous sign-in failed", error)
			}
	}
}
