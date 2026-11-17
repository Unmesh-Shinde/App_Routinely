package com.dailyroutine.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.lifecycleScope
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import kotlinx.coroutines.launch

/** Owns the ordered first-run notification, exact-alarm, and Health Connect permission flow. */
internal class FirstLaunchPermissionFlow(
    private val activity: AppCompatActivity,
    private val healthConnectManager: IHealthConnectManager,
    private val permissionsLauncher: ActivityResultLauncher<Set<String>>,
    private val activateHealthConnect: suspend () -> Unit
) {
    private enum class Step { NONE, NOTIFICATION, EXACT_ALARM, HEALTH_CONNECT }

    private var active = false
    private var step = Step.NONE
    private var waitingForExactAlarmSettings = false

    val isActive: Boolean get() = active

    fun onNotificationPermissionResult(requestCode: Int) {
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE && active) {
            step = Step.EXACT_ALARM
            continueFlow()
        }
    }

    fun onHealthConnectPermissionResult() {
        activity.lifecycleScope.launch {
            activateHealthConnect()
            if (active && step == Step.HEALTH_CONNECT) finish()
        }
    }

    fun startIfNeeded() {
        val prefs = activity.getSharedPreferences("health_data_pref", AppCompatActivity.MODE_PRIVATE)
        if (!prefs.getBoolean("needs_initial_permission_request", false) || active) return
        active = true
        step = Step.NOTIFICATION
        continueFlow()
    }

    fun onResume() {
        if (active && waitingForExactAlarmSettings) {
            waitingForExactAlarmSettings = false
            step = Step.HEALTH_CONNECT
            activity.findViewById<android.view.View>(android.R.id.content).postDelayed({ continueFlow() }, 500)
        }
    }

    private fun continueFlow() {
        if (!active || activity.isFinishing || activity.isDestroyed) return
        when (step) {
            Step.NOTIFICATION -> requestNotificationPermission()
            Step.EXACT_ALARM -> requestExactAlarmPermission()
            Step.HEALTH_CONNECT -> requestHealthConnectPermission()
            Step.NONE -> finish()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
        } else {
            step = Step.EXACT_ALARM
            continueFlow()
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = activity.getSystemService(android.app.AlarmManager::class.java)
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                    .setTitle("Allow reminder alarms")
                    .setMessage("Routinely uses exact alarms so reminders can ring at the time you set. Please allow alarm permission on the next screen, then return to Routinely to continue setup.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        waitingForExactAlarmSettings = true
                        activity.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        waitingForExactAlarmSettings = false
                        step = Step.HEALTH_CONNECT
                        continueFlow()
                    }
                    .show()
                return
            }
        }
        step = Step.HEALTH_CONNECT
        continueFlow()
    }

    private fun requestHealthConnectPermission() {
        if (HealthConnectClient.getSdkStatus(activity) != HealthConnectClient.SDK_AVAILABLE) {
            Toast.makeText(activity, "Health Connect is not available on this device.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        activity.lifecycleScope.launch {
            if (healthConnectManager.getGrantedPermissionsOrNull() == null) {
                Toast.makeText(activity, "Could not check Health Connect permissions. Please try syncing again.", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val missingPermissions = healthConnectManager.getMissingPermissions()
            if (missingPermissions.isEmpty()) {
                activateHealthConnect()
                finish()
            } else {
                permissionsLauncher.launch(missingPermissions)
            }
        }
    }

    private fun finish() {
        activity.getSharedPreferences("health_data_pref", AppCompatActivity.MODE_PRIVATE)
            .edit()
            .putBoolean("needs_initial_permission_request", false)
            .apply()
        active = false
        step = Step.NONE
        waitingForExactAlarmSettings = false
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 100
    }
}
