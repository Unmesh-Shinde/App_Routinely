package com.dailyroutine.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private enum class FirstLaunchPermissionStep {
        NONE,
        NOTIFICATION,
        EXACT_ALARM,
        HEALTH_CONNECT,
        GOOGLE_FIT
    }

    private companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var mgr: ReminderManager
    private lateinit var planManager: PlanManager
    private lateinit var healthDataManager: HealthDataManager
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var googleFitHeartPointsManager: GoogleFitHeartPointsManager
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Set<String>>
    private var firstLaunchPermissionFlowActive = false
    private var firstLaunchPermissionStep = FirstLaunchPermissionStep.NONE
    private var waitingForExactAlarmSettings = false
    private val dataUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateDashboard()
        }
    }
    
    private val fileToneLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            if (ReminderToneHelper.isToneDurationAllowed(this, it)) {
                runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                ReminderDialogHelper.updateActiveTone(it.toString())
            } else {
                Toast.makeText(this, ReminderToneHelper.durationWarningText(), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!UserPreferencesStore.isSignedUp(this)) {
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        applyHomeInsets()

        mgr = ReminderManager(this)
        planManager = PlanManager(this)
        healthDataManager = HealthDataManager(this)
        healthConnectManager = HealthConnectManager(this)
        googleFitHeartPointsManager = GoogleFitHeartPointsManager(this)

        mgr.scheduleAllEnabled()
        ReminderToneHelper.preloadSystemNotificationTones(this)

        requestPermissionsLauncher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { _ ->
            fetchHealthData()
            if (firstLaunchPermissionFlowActive && firstLaunchPermissionStep == FirstLaunchPermissionStep.HEALTH_CONNECT) {
                findViewById<View>(android.R.id.content).postDelayed({
                    firstLaunchPermissionStep = FirstLaunchPermissionStep.GOOGLE_FIT
                    continueFirstLaunchPermissionFlow()
                }, 600)
            }
        }

        updateGreeting()
        updateDashboard()

        findViewById<TextView>(R.id.tvGreeting).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<View>(R.id.cardProfileAvatar).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<View>(R.id.cardWellnessScore).setOnClickListener {
            startActivity(Intent(this, WellnessScoreActivity::class.java))
        }

        findViewById<View>(R.id.cardDiet).setOnClickListener {
            startActivity(Intent(this, DietPlanActivity::class.java))
        }

        findViewById<View>(R.id.cardWorkout).setOnClickListener {
            startActivity(Intent(this, WorkoutPlanActivity::class.java))
        }

        findViewById<View>(R.id.cardWalking).setOnClickListener {
            startActivity(Intent(this, WalkingDataActivity::class.java))
        }

        findViewById<View>(R.id.cardSleep).setOnClickListener {
            startActivity(Intent(this, SleepTrackingActivity::class.java))
        }

        findViewById<View>(R.id.cardWeight).setOnClickListener {
            startActivity(Intent(this, WeightActivity::class.java))
        }

        findViewById<View>(R.id.cardCalories).setOnClickListener {
            startActivity(Intent(this, CaloriesActivity::class.java))
        }

        findViewById<View>(R.id.cardWater).setOnClickListener {
            showWaterAdjustDialog()
        }

        findViewById<View>(R.id.btnWaterMinus).setOnClickListener {
            adjustWaterToday(-0.25, "Removed 250 ml")
        }

        findViewById<View>(R.id.btnWaterPlus).setOnClickListener {
            adjustWaterToday(0.25, "Added 250 ml")
        }

        findViewById<MaterialButton>(R.id.btnHealthSync).setOnClickListener {
            startHealthAppScanning()
        }

        findViewById<MaterialButton>(R.id.btnReminders).setOnClickListener { view ->
            val popup = PopupMenu(this, view, Gravity.TOP)
            popup.menu.add("Set Reminders")
            popup.menu.add("View Reminders")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Set Reminders" -> {
                        ReminderDialogHelper.showDialog(
                            this, mgr, null, view,
                            onTonePickerRequested = { currentUri ->
                                showToneSourcePicker(currentUri)
                            }
                        ) {
                            updateDashboard()
                        }
                    }
                    "View Reminders" -> startActivity(Intent(this, RemindersActivity::class.java))
                }
                true
            }
            popup.show()
        }

        addDefaultsOnFirstRun()

        HealthSyncWorker.scheduleAutoSync(this)

        findViewById<View>(android.R.id.content).postDelayed({
            startFirstLaunchPermissionFlowIfNeeded()
        }, 300)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GoogleFitHeartPointsManager.HEART_POINTS_REQUEST_CODE) {
            when (val result = googleFitHeartPointsManager.handlePermissionResult(data)) {
                is GoogleFitHeartPointsManager.PermissionResult.Granted -> {
                    val email = result.email.orEmpty()
                    Toast.makeText(
                        this,
                        if (email.isNotEmpty()) "Google Fit Heart Points access enabled for $email" else "Google Fit Heart Points access enabled",
                        Toast.LENGTH_LONG
                    ).show()
                    fetchHealthData()
                    if (firstLaunchPermissionFlowActive && firstLaunchPermissionStep == FirstLaunchPermissionStep.GOOGLE_FIT) {
                        finishFirstLaunchPermissionFlow()
                    }
                }
                is GoogleFitHeartPointsManager.PermissionResult.MissingFitnessScope -> {
                    showGoogleFitHeartPointsAccessNotGrantedDialog(result.email, null)
                }
                is GoogleFitHeartPointsManager.PermissionResult.Failed -> {
                    showGoogleFitHeartPointsAccessNotGrantedDialog(null, "Status ${result.statusCode}: ${result.message ?: "Google sign-in failed"}")
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE && firstLaunchPermissionFlowActive) {
            firstLaunchPermissionStep = FirstLaunchPermissionStep.EXACT_ALARM
            continueFirstLaunchPermissionFlow()
        }
    }

    private fun startFirstLaunchPermissionFlowIfNeeded() {
        val prefs = getSharedPreferences("health_data_pref", MODE_PRIVATE)
        if (!prefs.getBoolean("needs_initial_permission_request", false) || firstLaunchPermissionFlowActive) {
            return
        }

        firstLaunchPermissionFlowActive = true
        firstLaunchPermissionStep = FirstLaunchPermissionStep.NOTIFICATION
        continueFirstLaunchPermissionFlow()
    }

    private fun continueFirstLaunchPermissionFlow() {
        if (!firstLaunchPermissionFlowActive || isFinishing || isDestroyed) return

        when (firstLaunchPermissionStep) {
            FirstLaunchPermissionStep.NOTIFICATION -> runNotificationPermissionStep()
            FirstLaunchPermissionStep.EXACT_ALARM -> runExactAlarmPermissionStep()
            FirstLaunchPermissionStep.HEALTH_CONNECT -> runHealthConnectPermissionStep()
            FirstLaunchPermissionStep.GOOGLE_FIT -> runGoogleFitPermissionStep()
            FirstLaunchPermissionStep.NONE -> finishFirstLaunchPermissionFlow()
        }
    }

    private fun runNotificationPermissionStep() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
        } else {
            firstLaunchPermissionStep = FirstLaunchPermissionStep.EXACT_ALARM
            continueFirstLaunchPermissionFlow()
        }
    }

    private fun runExactAlarmPermissionStep() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Allow reminder alarms")
                    .setMessage("Routinely uses exact alarms so reminders can ring at the time you set. Please allow alarm permission on the next screen, then return to Routinely to continue setup.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        waitingForExactAlarmSettings = true
                        startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        waitingForExactAlarmSettings = false
                        firstLaunchPermissionStep = FirstLaunchPermissionStep.HEALTH_CONNECT
                        continueFirstLaunchPermissionFlow()
                    }
                    .show()
                return
            }
        }

        firstLaunchPermissionStep = FirstLaunchPermissionStep.HEALTH_CONNECT
        continueFirstLaunchPermissionFlow()
    }

    private fun runHealthConnectPermissionStep() {
        val availability = HealthConnectClient.getSdkStatus(this)
        if (availability != HealthConnectClient.SDK_AVAILABLE) {
            Toast.makeText(this, "Health Connect is not available on this device.", Toast.LENGTH_SHORT).show()
            firstLaunchPermissionStep = FirstLaunchPermissionStep.GOOGLE_FIT
            continueFirstLaunchPermissionFlow()
            return
        }

        // Auto-select a data source so we can prompt for Health Connect access directly.
        if (healthDataManager.getConnectedAppName().isNullOrEmpty()) {
            val apps = HealthAppScanner.getInstalledFitnessApps(this)
            if (apps.isNotEmpty()) {
                val selectedApp = apps.first()
                healthDataManager.setConnectedAppName(selectedApp.name)
                healthDataManager.setConnectedAppPackage(selectedApp.packageName)
            }
        }

        lifecycleScope.launch {
            if (healthConnectManager.hasAnyPermission()) {
                fetchHealthData()
                firstLaunchPermissionStep = FirstLaunchPermissionStep.GOOGLE_FIT
                continueFirstLaunchPermissionFlow()
            } else {
                // Launch the Health Connect permission screen directly (compulsory prompt).
                requestPermissionsLauncher.launch(healthConnectManager.permissions)
            }
        }
    }

    private fun runGoogleFitPermissionStep() {
        val googleFitInstalled = HealthAppScanner.getInstalledFitnessApps(this)
            .any { it.packageName == GoogleFitHeartPointsManager.GOOGLE_FIT_PACKAGE }
        if (!googleFitInstalled || googleFitHeartPointsManager.hasReadPermission(this)) {
            finishFirstLaunchPermissionFlow()
            return
        }

        // Launch the Google account chooser / Heart Points consent directly (compulsory prompt).
        googleFitHeartPointsManager.requestReadPermission(this)
    }

    private fun finishFirstLaunchPermissionFlow() {
        getSharedPreferences("health_data_pref", MODE_PRIVATE)
            .edit()
            .putBoolean("needs_initial_permission_request", false)
            .apply()
        firstLaunchPermissionFlowActive = false
        firstLaunchPermissionStep = FirstLaunchPermissionStep.NONE
        waitingForExactAlarmSettings = false
    }

    private fun showGoogleFitHeartPointsUnavailableToast() {
        val email = googleFitHeartPointsManager.signedInEmail().orEmpty()
        val msg = if (email.isNotEmpty()) {
            "Google Fit Heart Points access is not enabled for $email. Tap Health Sync > Google Fit to retry."
        } else {
            "Google Fit Heart Points access is not enabled. Tap Health Sync > Google Fit to connect."
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun showGoogleFitHeartPointsPermissionDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Allow Google Fit Heart Points")
            .setMessage(
                "For Heart Points, Google first asks you to choose the Gmail account used in Google Fit. " +
                    "After choosing the account, Google grants this app the Google Fit activity read scope used for Heart Points. " +
                    "Please choose the same Gmail account that shows your Heart Points in Google Fit."
            )
            .setPositiveButton("Continue") { _, _ ->
                googleFitHeartPointsManager.requestReadPermission(this)
            }
            .setNegativeButton("Not Now") { _, _ ->
                checkHealthConnectPermissions()
            }
            .show()
    }

    private fun showGoogleFitHeartPointsAccessNotGrantedDialog(email: String? = null, errorDetails: String? = null) {
        val accountLine = if (!email.isNullOrBlank()) "\n\nSelected account: $email" else ""
        val errorLine = if (!errorDetails.isNullOrBlank()) "\n\nGoogle error: $errorDetails" else ""
        val status10Help = if (errorDetails?.contains("Status 10") == true) {
            "\n\nStatus 10 is Google Sign-In DEVELOPER_ERROR. It is not caused by Health Connect permissions. " +
                "It means Google does not recognize this installed APK as an authorized Android OAuth client for Google Fit." +
                "\n\nAdd this debug build to Google Cloud OAuth:" +
                "\nPackage: com.dailyroutine.app" +
                "\nSHA-1: F0:BD:00:C7:25:A3:C0:32:73:6E:4E:1C:78:FC:C2:2B:54:A7:A1:E0" +
                "\n\nAlso enable Google Fit API and add your Gmail as an OAuth test user if the consent screen is in Testing."
        } else {
            ""
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Google Fit access not granted")
            .setMessage(
                "Google did not grant this app the Google Fit fitness activity read scope, so Heart Points cannot be read yet." +
                    accountLine +
                    errorLine +
                    status10Help +
                    "\n\nYour Health Connect permissions are separate and can be fully granted while Google Fit OAuth still fails. " +
                    "Heart Points from Google Fit require this Google OAuth step because the data is stored under your Google account."
            )
            .setPositiveButton("Try Again") { _, _ ->
                googleFitHeartPointsManager.requestReadPermission(this)
            }
            .setNegativeButton("Later") { _, _ ->
                if (firstLaunchPermissionFlowActive && firstLaunchPermissionStep == FirstLaunchPermissionStep.GOOGLE_FIT) {
                    finishFirstLaunchPermissionFlow()
                } else {
                    checkHealthConnectPermissions()
                }
            }
            .show()
    }

    private fun addDefaultsOnFirstRun() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("is_first_run", true)) {
            // Ensure no legacy/mock health data exists
            getSharedPreferences("health_data_pref", MODE_PRIVATE)
                .edit()
                .clear()
                .putBoolean("needs_initial_permission_request", true)
                .apply()

            mgr.saveReminder(Reminder(title = "Drink Water", type = ReminderType.HYDRATION, isIntervalBased = true, intervalMinutes = 120))
            mgr.saveReminder(Reminder(title = "Healthy Meal", type = ReminderType.MEAL, hour = 13, minute = 0))
            mgr.saveReminder(Reminder(title = "Meditation", type = ReminderType.MEDITATION, hour = 8, minute = 0))
            prefs.edit().putBoolean("is_first_run", false).apply()
            updateDashboard()
        }
    }

    private fun applyHomeInsets() {
        val header = findViewById<View>(R.id.homeHeader)
        val headerBaseLeft = header.paddingLeft
        val headerBaseTop = header.paddingTop
        val headerBaseRight = header.paddingRight
        val headerBaseBottom = header.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(header) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.setPadding(
                headerBaseLeft + maxOf(systemBars.left, cutout.left),
                headerBaseTop + maxOf(systemBars.top, cutout.top),
                headerBaseRight + maxOf(systemBars.right, cutout.right),
                headerBaseBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(header)

        val scroll = findViewById<View>(R.id.homeScroll)
        val baseLeft = scroll.paddingLeft
        val baseTop = scroll.paddingTop
        val baseRight = scroll.paddingRight
        val baseBottom = scroll.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.setPadding(
                baseLeft + maxOf(systemBars.left, cutout.left),
                baseTop,
                baseRight + maxOf(systemBars.right, cutout.right),
                baseBottom + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(scroll)

        // Apply insets to header to prevent overlap with system status bar
        val rootView = findViewById<View>(android.R.id.content).parent as View
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            // Let the system handle the default behavior
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun startHealthAppScanning() {
        val apps = HealthAppScanner.getInstalledFitnessApps(this)
        if (apps.isEmpty()) {
            Toast.makeText(this, "No fitness apps found! Please install Google Fit, Samsung Health, etc.", Toast.LENGTH_LONG).show()
            return
        }

        val appNames = apps.map { it.name }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Select Health App Source")
            .setItems(appNames) { _, which ->
                val selectedApp = apps[which]
                healthDataManager.setConnectedAppName(selectedApp.name)
                healthDataManager.setConnectedAppPackage(selectedApp.packageName)
                if (selectedApp.packageName == GoogleFitHeartPointsManager.GOOGLE_FIT_PACKAGE &&
                    !googleFitHeartPointsManager.hasReadPermission(this)
                ) {
                    showGoogleFitHeartPointsPermissionDialog()
                } else {
                    checkHealthConnectPermissions()
                }
            }
            .setNeutralButton("Test Background Sync") { _, _ ->
                val request = OneTimeWorkRequestBuilder<HealthSyncWorker>().build()
                WorkManager.getInstance(this).enqueue(request)
                Toast.makeText(this, "Forcing Background Worker... Check notifications! 🛠️", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkHealthConnectPermissions() {
        val availability = HealthConnectClient.getSdkStatus(this)
        if (availability != HealthConnectClient.SDK_AVAILABLE) {
            Toast.makeText(this, "Health Connect SDK not available on this device.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            if (healthConnectManager.hasAnyPermission()) {
                fetchHealthData()
            } else {
                val appName = healthDataManager.getConnectedAppName()
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Link with $appName? ⌚")
                    .setMessage("To automatically fetch your Steps, Sleep, and Calories, we use Android's Health Connect system. \n\nIMPORTANT: Please ensure Google Fit is linked to Health Connect in its settings first.")
                    .setPositiveButton("Grant Permissions") { _, _ ->
                        requestPermissionsLauncher.launch(healthConnectManager.permissions)
                    }
                    .setNeutralButton("Settings") { _, _ ->
                        val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                        startActivity(intent)
                    }
                    .setNegativeButton("Not Now", null)
                    .show()
            }
        }
    }

    private fun fetchHealthData() {
        val appName = healthDataManager.getConnectedAppName()
        val appPkg = healthDataManager.getConnectedAppPackage()
        
        // Safety Check: If no app is connected and we aren't in a "Syncing" context, skip.
        if (appName == "None" || appPkg == null) {
            Log.d("HealthSync", "No app connected. Skipping background sync.")
            return
        }

        lifecycleScope.launch {
            val now = Instant.now()
            val zoneId = java.time.ZoneId.systemDefault()
            val todayDate = java.time.LocalDate.now(zoneId)
            val startOfToday = todayDate.atStartOfDay(zoneId).toInstant()

            val prefs = getSharedPreferences("health_data_pref", MODE_PRIVATE)
            val editor = prefs.edit().putBoolean("is_fitness_connected", true)
            val granted = healthConnectManager.getGrantedPermissions()

            var stepsToday = 0L
            var moveMinsToday = 0
            var sleepToday = ""
            var caloriesToday = ""
            var weightToday = 0.0
            var heartPointsToday = 0.0
            var heartPointsByDate = emptyMap<String, Double>()
            val canReadGoogleFitHeartPoints = appPkg == GoogleFitHeartPointsManager.GOOGLE_FIT_PACKAGE &&
                googleFitHeartPointsManager.hasReadPermission(this@MainActivity)
            val shouldSyncFullHistory = !healthDataManager.isInitialHistorySyncDone()

            if (granted.contains(HealthPermission.getReadPermission(StepsRecord::class))) {
                stepsToday = healthConnectManager.readSteps(startOfToday, now, appPkg)
                editor.putString("steps_count", "%,d".format(stepsToday))
            }
            if (granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))) {
                val distanceToday = healthConnectManager.readDistanceMeters(startOfToday, now, appPkg) / 1000.0
                editor.putString("distance_val", "%.2f km".format(distanceToday))
            }
            if (granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class))) {
                moveMinsToday = healthConnectManager.readMoveMinutes(startOfToday, now, appPkg)
                healthDataManager.setMoveMinutes(moveMinsToday)
            }
            if (granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
                val sleepSessions = healthConnectManager.readSleepSessions(startOfToday, now, appPkg)
                if (sleepSessions.isNotEmpty()) {
                    val totalDurationMin = sleepSessions.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                    sleepToday = "${totalDurationMin / 60}h ${totalDurationMin % 60}m"
                    editor.putString("sleep_hours", sleepToday)
                }
            }
            if (granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))) {
                val burnedCals = healthConnectManager.readTotalCalories(startOfToday, now, appPkg)
                caloriesToday = "%.0f".format(burnedCals)
                editor.putString("calories_burnt", caloriesToday)
            }
            if (granted.contains(HealthPermission.getReadPermission(WeightRecord::class))) {
                weightToday = healthConnectManager.readWeightKg(startOfToday, now, appPkg)
                if (weightToday > 0) {
                    editor.putString("current_weight", "%.1f kg".format(weightToday))
                }
            }
            if (canReadGoogleFitHeartPoints) {
                heartPointsToday = if (shouldSyncFullHistory) {
                    val oldestDate = todayDate.minusDays((HealthDataManager.SYNC_HISTORY_DAYS - 1).toLong())
                    heartPointsByDate = googleFitHeartPointsManager.readDailyHeartPoints(oldestDate, now, zoneId)
                    heartPointsByDate[todayDate.toString()] ?: googleFitHeartPointsManager.readHeartPoints(startOfToday, now)
                } else {
                    googleFitHeartPointsManager.readHeartPoints(startOfToday, now)
                }
                healthDataManager.setHeartPoints(heartPointsToday.toInt())
                Log.d("MainActivity", "Google Fit Heart Points synced: $heartPointsToday")
                Toast.makeText(this@MainActivity, "✓ Google Fit Heart Points: ${heartPointsToday.toInt()}", Toast.LENGTH_LONG).show()
            } else if (appPkg == GoogleFitHeartPointsManager.GOOGLE_FIT_PACKAGE) {
                Log.w("MainActivity", "Google Fit Heart Points permission not granted; not auto-requesting to avoid account picker loop")
                healthDataManager.setHeartPoints(0)
                showGoogleFitHeartPointsUnavailableToast()
            } else {
                healthDataManager.setHeartPoints(0)
            }

            // Data Validation: If we connected an app but got no critical data (Steps and Sleep), notify the user.
            if (appName != "None" && stepsToday == 0L && sleepToday.isEmpty()) {
                Toast.makeText(this@MainActivity, "Cannot sync health data from $appName: App not supported or data missing.", Toast.LENGTH_LONG).show()
            }

            if (shouldSyncFullHistory) {
                for (i in 0 until HealthDataManager.SYNC_HISTORY_DAYS) {
                    val date = todayDate.minusDays(i.toLong())
                    val dayStart = date.atStartOfDay(zoneId).toInstant()
                    val dayEnd = if (i == 0) now else date.plusDays(1).atStartOfDay(zoneId).toInstant()
                    val dateStr = date.toString()

                    if (granted.contains(HealthPermission.getReadPermission(StepsRecord::class))) {
                        healthDataManager.saveHistoricalSteps(dateStr, healthConnectManager.readSteps(dayStart, dayEnd, appPkg))
                    }
                    if (granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
                        val sessions = healthConnectManager.readSleepSessions(dayStart, dayEnd, appPkg)
                        val mins = sessions.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                        healthDataManager.saveHistoricalSleep(dateStr, mins / 60.0)
                    }
                    if (granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))) {
                        healthDataManager.saveHistoricalCalories(dateStr, healthConnectManager.readTotalCalories(dayStart, dayEnd, appPkg))
                    }
                    if (granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))) {
                        val distKm = healthConnectManager.readDistanceMeters(dayStart, dayEnd, appPkg) / 1000.0
                        prefs.edit().putString("hist_dist_$dateStr", "%.2f km".format(distKm)).apply()
                    }
                    if (granted.contains(HealthPermission.getReadPermission(WeightRecord::class))) {
                        val weightKg = healthConnectManager.readWeightKg(dayStart, dayEnd, appPkg)
                        if (weightKg > 0) {
                            healthDataManager.saveWeight(dateStr, weightKg)
                        }
                    }
                    if (canReadGoogleFitHeartPoints) {
                        healthDataManager.saveHistoricalHeartPoints(dateStr, heartPointsByDate[dateStr] ?: 0.0)
                    } else {
                        healthDataManager.saveHistoricalHeartPoints(dateStr, 0.0)
                    }
                }
                healthDataManager.setInitialHistorySyncDone(true)
            }

            val timestamp = java.text.SimpleDateFormat("hh:mm a, dd MMM", java.util.Locale.US).format(java.util.Date())
            healthDataManager.setLastSyncTime(timestamp)
            
            editor.apply()
            updateDashboard()
            
            val summary = StringBuilder("Sync complete from $appName!")
            if (stepsToday > 0) summary.append("\nSteps Today: %,d".format(stepsToday))
            if (moveMinsToday > 0) summary.append("\nMove: $moveMinsToday min")
            if (heartPointsToday > 0) summary.append("\nHeart Points: %.1f".format(heartPointsToday))
            if (caloriesToday.isNotEmpty() && caloriesToday != "0") summary.append("\nBurned Today: $caloriesToday kcal")
            if (weightToday > 0) summary.append("\nWeight: %.1f kg".format(weightToday))
            
            Toast.makeText(this@MainActivity, summary.toString(), Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            dataUpdatedReceiver,
            IntentFilter(WellnessWidget.ACTION_DATA_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // 🔴 Day-Rollover Protection: Check if we need to finalize "Yesterday"
        val prefs = getSharedPreferences("health_data_pref", MODE_PRIVATE)
        val todayStr = java.time.LocalDate.now().toString()
        val lastFinalized = prefs.getString("last_finalized_day", "")
        
        if (lastFinalized != todayStr) {
            // New day detected! Finalize yesterday's data before starting today
            fetchHealthData() // This will backfill yesterday correctly
            prefs.edit().putString("last_finalized_day", todayStr).apply()
        }

        updateDashboard()

        if (firstLaunchPermissionFlowActive && waitingForExactAlarmSettings) {
            waitingForExactAlarmSettings = false
            firstLaunchPermissionStep = FirstLaunchPermissionStep.HEALTH_CONNECT
            findViewById<View>(android.R.id.content).postDelayed({
                continueFirstLaunchPermissionFlow()
            }, 500)
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(dataUpdatedReceiver) }
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val name = UserPreferencesStore.getUserName(this)
        val greeting = when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }
        findViewById<TextView>(R.id.tvGreetingLabel).text = greeting
        findViewById<TextView>(R.id.tvGreeting).text = name

        val dateFormat = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault())
        findViewById<TextView>(R.id.tvHeaderDate).text = dateFormat.format(java.util.Date())

        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        findViewById<TextView>(R.id.tvProfileInitial).text = initial
    }


    private fun showWaterQuickAdd() {
        val options = arrayOf("+250 ml", "+500 ml", "+1 Liter")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Add Water Intake")
            .setItems(options) { _, which ->
                val amount = when(which) {
                    0 -> 0.25
                    1 -> 0.5
                    else -> 1.0
                }
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                healthDataManager.addWaterIntake(today, amount)
                updateDashboard()
                Toast.makeText(this, "Added $amount L! 💧", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showWaterAdjustDialog() {
        val options = arrayOf("+250 ml", "+500 ml", "+1 Liter", "-250 ml", "-500 ml")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Adjust Water Intake")
            .setItems(options) { _, which ->
                val amount = when (which) {
                    0 -> 0.25
                    1 -> 0.5
                    2 -> 1.0
                    3 -> -0.25
                    else -> -0.5
                }
                val message = if (amount > 0) {
                    "Added %.0f ml".format(amount * 1000)
                } else {
                    "Removed %.0f ml".format(kotlin.math.abs(amount) * 1000)
                }
                adjustWaterToday(amount, message)
            }
            .show()
    }

    private fun adjustWaterToday(amount: Double, message: String) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val updated = healthDataManager.adjustWaterIntake(today, amount)
        updateDashboard()
        Toast.makeText(this, "$message - %.1f L today".format(updated), Toast.LENGTH_SHORT).show()
    }

    private fun updateDashboard() {
        val allReminders = mgr.getAllReminders().filter { it.isEnabled }

        val calendar = Calendar.getInstance()
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(calendar.time)
        
        val mealsList = planManager.getMealsForDate(todayStr)
        val workoutList = planManager.getExercisesForDate(todayStr)

        val stepsStr = healthDataManager.getSteps().replace(",", "")
        val stepsCount = stepsStr.toIntOrNull() ?: 0
        
        val sleepValue = healthDataManager.getSleep()
        val sleepHours = try {
            val parts = sleepValue.split(" ")
            var h = 0.0
            var m = 0.0
            parts.forEach { 
                if (it.contains("h")) h = it.replace("h", "").toDoubleOrNull() ?: 0.0
                if (it.contains("m")) m = it.replace("m", "").toDoubleOrNull() ?: 0.0
            }
            h + (m / 60.0)
        } catch(e: Exception) { 0.0 }

        // 🟢 ASYNC INTAKE CALCULATION (Using Master Wellness Engine)
        WellnessEngine.calculateIntakeForDate(this, todayStr) { intakeTotal ->
            runOnUiThread {
                val weightValForBurn = healthDataManager.getWeight(todayStr).let { if (it > 0) it else 70.0 }
                val burnedTotal = WellnessEngine.calculateActiveBurn(this, stepsCount, weightValForBurn)
                val netBalance = intakeTotal - burnedTotal.toInt()
                
                Log.d("BurnEngine", "Intake: $intakeTotal | Steps: $stepsCount | Total Burned: $burnedTotal | Net: $netBalance")

                findViewById<TextView>(R.id.tvValSteps).text = if (healthDataManager.isConnected() && stepsCount > 0) healthDataManager.getSteps() else "0"
                findViewById<TextView>(R.id.tvValSleep).text = if (healthDataManager.isConnected() && sleepHours > 0) healthDataManager.getSleep() else "0h"
                findViewById<TextView>(R.id.tvValCalories).text = netBalance.toString()
            }
        }
        
        val weightVal = healthDataManager.getWeight(todayStr)
        findViewById<TextView>(R.id.tvValWeight).text = if (weightVal > 0) "$weightVal kg" else "Not Logged"
        
        val waterValManual = healthDataManager.getWaterIntake(todayStr)
        val doneIds = RoutineProgressStore.getDoneIds(this)
        val waterFromReminders = allReminders
            .filter { it.type == ReminderType.HYDRATION && it.id.toString() in doneIds }
            .size * 0.25

        findViewById<TextView>(R.id.tvValWater).text = "%.1f Liters".format(waterValManual + waterFromReminders)

        findViewById<TextView>(R.id.tvLastSync).text = "Last Auto-Sync: ${healthDataManager.getLastSyncTime()}"

        val doneIdsForScore = RoutineProgressStore.getDoneIds(this, todayStr)

        val nutritionDone = mealsList.count { it.id.toString() in doneIdsForScore }
        val nutritionTotal = mealsList.size
        val workoutDone = workoutList.count { it.id.toString() in doneIdsForScore }
        val workoutTotal = workoutList.size

        val score = WellnessScoreManager.calculateDailyScore(
            this, stepsCount, sleepHours,
            workoutDone, workoutTotal,
            nutritionDone, nutritionTotal
        )
        WellnessScoreManager.saveDailyScore(this, todayStr, score)
        findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.progressWellness).progress = score
        findViewById<TextView>(R.id.tvWellnessScore).text = score.toString()
        findViewById<TextView>(R.id.tvWellnessMsg).text = when {
            score >= 90 -> "Excellent! You're a wellness pro! 🏆"
            score >= 70 -> "Great job! Keep up the momentum! ✨"
            score >= 40 -> "Good start! You're making progress. 👍"
            else -> "Keep moving to reach your goals! 💪"
        }

        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val fixedReminders = allReminders.filter { it.isEnabled && !it.isIntervalBased }
        var upcoming = fixedReminders.filter { (it.hour * 60 + it.minute) > currentMinutes }.minByOrNull { it.hour * 60 + it.minute }
        if (upcoming == null) {
            upcoming = fixedReminders.minByOrNull { it.hour * 60 + it.minute }
        }

        if (upcoming != null) {
            findViewById<View>(R.id.cardUpcoming).visibility = View.VISIBLE
            val timePrefix = if ((upcoming.hour * 60 + upcoming.minute) <= currentMinutes) "Tomorrow at " else ""
            findViewById<TextView>(R.id.tvUpcomingText).text = "${upcoming.title.replace("Meal: ", "").replace("Exercise: ", "")} - $timePrefix${upcoming.formatTime()}"
        } else {
            findViewById<View>(R.id.cardUpcoming).visibility = View.GONE
        }
        
        WellnessWidget.refresh(this)
    }

    private fun showToneSourcePicker(currentUri: String?) {
        val options = arrayOf("System notification tones", "Audio file from device")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Pick Notification Tone")
            .setItems(options) { _, which ->
                if (which == 0) {
                    showSystemNotificationTonePicker(currentUri)
                } else {
                    fileToneLauncher.launch(arrayOf("audio/*"))
                }
            }
            .show()
    }

    private fun showSystemNotificationTonePicker(currentUri: String?) {
        ReminderToneHelper.cachedSystemNotificationTones()?.let {
            showSystemNotificationToneList(it, currentUri)
            return
        }

        val loadingDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Loading notification tones")
            .setMessage("Preparing available short tones...")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            val tones = withContext(Dispatchers.IO) {
                ReminderToneHelper.eligibleSystemNotificationTones(this@MainActivity)
            }
            if (!isFinishing && !isDestroyed) {
                loadingDialog.dismiss()
                showSystemNotificationToneList(tones, currentUri)
            }
        }
    }

    private fun showSystemNotificationToneList(tones: List<ReminderToneHelper.ToneOption>, currentUri: String?) {
        if (tones.isEmpty()) {
            Toast.makeText(this, "No notification tones up to 6 seconds were found.", Toast.LENGTH_LONG).show()
            return
        }

        val currentIndex = tones.indexOfFirst { it.uri?.toString() == currentUri }.coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Select Notification Tone")
            .setSingleChoiceItems(tones.map { it.title }.toTypedArray(), currentIndex) { dialog, which ->
                ReminderDialogHelper.updateActiveTone(tones[which].uri?.toString())
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
