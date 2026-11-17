package com.dailyroutine.app

import android.widget.Toast
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.launch

/** Keeps Health Connect permission checks and manual history sync scheduling out of the activity. */
internal class HealthConnectSyncCoordinator(
    private val activity: AppCompatActivity,
    private val healthConnectManager: IHealthConnectManager,
    private val healthDataManager: HealthDataManager,
    private val permissionsLauncher: ActivityResultLauncher<Set<String>>
) {
    fun startManualSync() {
        if (HealthConnectClient.getSdkStatus(activity) != HealthConnectClient.SDK_AVAILABLE) {
            Toast.makeText(activity, "Health Connect SDK not available on this device.", Toast.LENGTH_SHORT).show()
            return
        }

        activity.lifecycleScope.launch {
            if (healthConnectManager.getGrantedPermissionsOrNull() == null) {
                Toast.makeText(activity, "Could not check Health Connect permissions. Please try syncing again.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val missingPermissions = healthConnectManager.getMissingPermissions()
            if (missingPermissions.isEmpty()) {
                activateAndSync()
            } else {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                    .setTitle("Connect Health Connect")
                    .setMessage("Routinely reads Steps, Sleep, and Calories through Android Health Connect. Make sure your device or provider has written the data you want to share to Health Connect.")
                    .setPositiveButton("Grant Permissions") { _, _ -> permissionsLauncher.launch(missingPermissions) }
                    .setNeutralButton("Settings") { _, _ -> activity.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
                    .setNegativeButton("Not Now", null)
                    .show()
            }
        }
    }

    suspend fun activateAndSync() {
        val granted = healthConnectManager.getGrantedPermissionsOrNull()
        if (granted == null) {
            Toast.makeText(activity, "Could not check Health Connect permissions. Please try syncing again.", Toast.LENGTH_LONG).show()
            return
        }
        val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
        if (stepsPermission !in granted) {
            Toast.makeText(activity, "Allow Steps access in Health Connect to sync your activity.", Toast.LENGTH_LONG).show()
            return
        }

        healthDataManager.setConnected(true)
        scheduleHistorySync()
    }

    private fun scheduleHistorySync() {
        val request = OneTimeWorkRequestBuilder<HealthSyncWorker>()
            .setInputData(workDataOf(HealthSyncWorker.KEY_SYNC_MODE to HealthSyncWorker.SYNC_MODE_HISTORY))
            .addTag(HealthSyncWorker.TAG_HISTORY_SYNC)
            .build()
        WorkManager.getInstance(activity.applicationContext).enqueueUniqueWork(
            "HealthHistoryFullSync_v3",
            androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        Toast.makeText(activity, "Health Connect sync started. It will continue in the background.", Toast.LENGTH_LONG).show()
    }
}
