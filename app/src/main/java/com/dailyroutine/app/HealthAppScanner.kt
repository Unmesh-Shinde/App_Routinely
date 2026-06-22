package com.dailyroutine.app

import android.content.Context
import android.content.pm.PackageManager

data class FitnessApp(val name: String, val packageName: String, val iconRes: Int?)

object HealthAppScanner {
    private val KNOWN_APPS = listOf(
        FitnessApp("Google Fit", "com.google.android.apps.fitness", null),
        FitnessApp("Samsung Health", "com.sec.android.app.shealth", null),
        FitnessApp("Fitbit", "com.fitbit.FitbitMobile", null),
        FitnessApp("Garmin Connect", "com.garmin.android.apps.connectmobile", null),
        FitnessApp("Xiaomi Mi Fitness", "com.xiaomi.wearable", null),
        FitnessApp("Xiaomi Zepp Life", "com.xiaomi.hm.health", null),
        FitnessApp("Zepp (Amazfit)", "com.huami.watch.hmwatchmanager", null),
        FitnessApp("NoiseFit", "com.noisefit", null),
        FitnessApp("boAt Crest", "com.coveiot.boat", null),
        FitnessApp("Huawei Health", "com.huawei.health", null),
        FitnessApp("Realme Link", "com.realme.link", null),
        FitnessApp("OnePlus Health", "com.oneplus.health.orbit", null),
        FitnessApp("HeyTap Health (Oppo)", "com.heytap.health", null),
        FitnessApp("Health Connect", "com.google.android.apps.healthdata", null),
        FitnessApp("Fossil", "com.fossil.wearables.fossil", null),
        FitnessApp("Withings Health Mate", "com.withings.wiscale2", null),
        FitnessApp("Suunto", "com.stt.android.suunto", null),
        FitnessApp("Polar Flow", "fi.polar.beat", null)
    )

    fun getInstalledFitnessApps(context: Context): List<FitnessApp> {
        val pm = context.packageManager
        return KNOWN_APPS.filter { app ->
            try {
                pm.getPackageInfo(app.packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
