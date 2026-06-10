package com.dailyroutine.app

import android.content.Context
import android.content.pm.PackageManager

data class FitnessApp(val name: String, val packageName: String, val iconRes: Int?)

object HealthAppScanner {
    private val KNOWN_APPS = listOf(
        FitnessApp("Google Fit", "com.google.android.apps.fitness", null),
        FitnessApp("Samsung Health", "com.sec.android.app.shealth", null),
        FitnessApp("Xiaomi Zepp Life", "com.xiaomi.hm.health", null),
        FitnessApp("Zepp (Amazfit)", "com.huami.watch.hmwatchmanager", null),
        FitnessApp("Huawei Health", "com.huawei.health", null),
        FitnessApp("Realme Link", "com.realme.link", null),
        FitnessApp("Health Connect", "com.google.android.apps.healthdata", null)
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
