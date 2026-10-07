package com.dailyroutine.app

import android.Manifest
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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
import androidx.work.await
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
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
    private lateinit var healthConnectManager: IHealthConnectManager
    private lateinit var googleFitHeartPointsManager: IGoogleFitHeartPointsManager
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Set<String>>
    private var firstLaunchPermissionFlowActive = false
    private var firstLaunchPermissionStep = FirstLaunchPermissionStep.NONE
    private var waitingForExactAlarmSettings = false
    private var headerBackground: AnimatedHeaderBackgroundDrawable? = null
    private var entranceAnimationPlayed = false
    private var homeSavedInstanceState: Bundle? = null
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        homeSavedInstanceState = savedInstanceState
        
        if (!UserPreferencesStore.isSignedUp(this)) {
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        applyHomeInsets()
        setupAnimatedHomeHeader()

        mgr = ReminderManager(this)
        planManager = PlanManager(this)
        healthDataManager = HealthDataManager(this)
        healthConnectManager = HealthConnectManager(this)
        googleFitHeartPointsManager = GoogleFitHeartPointsManager(this)
        healthDataManager.pruneHistoricalData()

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

        findViewById<TextView>(R.id.tvLastSync).setOnClickListener {
            showSyncLogDialog()
        }

        initializeFirstRunState()

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
            val missingPermissions = healthConnectManager.getMissingPermissions()
            if (missingPermissions.isEmpty()) {
                fetchHealthData()
                firstLaunchPermissionStep = FirstLaunchPermissionStep.GOOGLE_FIT
                continueFirstLaunchPermissionFlow()
            } else {
                // Launch the Health Connect permission screen directly (compulsory prompt).
                requestPermissionsLauncher.launch(missingPermissions)
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

    private fun initializeFirstRunState() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("is_first_run", true)) {
            // Ensure no legacy/mock health data exists
            getSharedPreferences("health_data_pref", MODE_PRIVATE)
                .edit()
                .clear()
                .putBoolean("needs_initial_permission_request", true)
                .apply()

            prefs.edit().putBoolean("is_first_run", false).apply()
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

    private fun setupAnimatedHomeHeader() {
        val header = findViewById<View>(R.id.homeHeader)
        headerBackground = AnimatedHeaderBackgroundDrawable.forTimeOfDay(this).also { drawable ->
            header.background = drawable
        }
    }

    private fun startHomeHeaderAnimationIfNeeded(savedInstanceState: Bundle?) {
        if (!HeaderAnimationStartupGate.shouldPlayOnHomeCreate(savedInstanceState)) {
            return
        }

        if (!ValueAnimator.areAnimatorsEnabled()) {
            findViewById<TextView>(R.id.tvGreetingLabel).alpha = 1f
            findViewById<TextView>(R.id.tvGreeting).alpha = 1f
            findViewById<TextView>(R.id.tvHeaderDate).alpha = 1f
            findViewById<View>(R.id.cardProfileAvatar).alpha = 1f
            return
        }
        
        // Hide views instantly to prepare for entrance sequence
        findViewById<TextView>(R.id.tvGreetingLabel).alpha = 0f
        findViewById<TextView>(R.id.tvGreeting).alpha = 0f
        findViewById<TextView>(R.id.tvHeaderDate).alpha = 0f
        findViewById<View>(R.id.cardProfileAvatar).alpha = 0f

        findViewById<View>(R.id.homeHeader).post {
            if (ValueAnimator.areAnimatorsEnabled()) {
                headerBackground?.start()
            }
            runPremiumEntranceAnimation()
        }
    }

    private fun runPremiumEntranceAnimation() {
        val tvGreetingLabel = findViewById<TextView>(R.id.tvGreetingLabel)
        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvHeaderDate = findViewById<TextView>(R.id.tvHeaderDate)
        val cardAvatar = findViewById<View>(R.id.cardProfileAvatar)

        // Convert DP to pixels for reliable motion
        val density = resources.displayMetrics.density
        val shiftPx = 24 * density

        // Reset states for animation
        tvGreetingLabel.translationY = -shiftPx
        tvGreeting.translationY = shiftPx
        cardAvatar.scaleX = 0.5f
        cardAvatar.scaleY = 0.5f

        // Sequence (Extra slow as requested)
        tvGreetingLabel.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(1500)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        tvGreeting.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(400)
            .setDuration(1600)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        tvHeaderDate.animate()
            .alpha(1f)
            .setStartDelay(1000)
            .setDuration(1500)
            .start()

        cardAvatar.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(800)
            .setDuration(1500)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.4f))
            .start()
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
            val missingPermissions = healthConnectManager.getMissingPermissions()
            if (missingPermissions.isEmpty()) {
                fetchHealthData()
            } else {
                val appName = healthDataManager.getConnectedAppName()
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Link with $appName? ⌚")
                    .setMessage("To automatically fetch your Steps, Sleep, and Calories, we use Android's Health Connect system. \n\nIMPORTANT: Please ensure Google Fit is linked to Health Connect in its settings first.")
                    .setPositiveButton("Grant Permissions") { _, _ ->
                        requestPermissionsLauncher.launch(missingPermissions)
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
            val todayKey = todayDate.toString()
            val startOfToday = todayDate.atStartOfDay(zoneId).toInstant()

            val prefs = getSharedPreferences("health_data_pref", MODE_PRIVATE)
            val editor = prefs.edit().putBoolean("is_fitness_connected", true)
            val roomWriter = HealthMetricsRoomWriter(this@MainActivity)
            val granted = healthConnectManager.getGrantedPermissions()

            var stepsToday = 0L
            var moveMinsToday = 0
            var sleepToday = ""
            var caloriesToday = ""
            var weightToday = 0.0
            var heartPointsToday = 0.0
            var heartPointsByDate = emptyMap<String, Double>()
            val canReadGoogleFitHeartPoints = googleFitHeartPointsManager.hasReadPermission(this@MainActivity)
            val shouldSyncStepHistory = !healthDataManager.isInitialHistorySyncDone() ||
                !healthDataManager.isStepHistoryComplete()
            val shouldSyncHeartPointsHistory = canReadGoogleFitHeartPoints &&
                (!healthDataManager.isInitialHeartPointsHistorySyncDone() || !healthDataManager.isHeartPointsHistoryComplete())
            val shouldSyncFullHistory = shouldSyncStepHistory || shouldSyncHeartPointsHistory
            var stepHistoryHadReadFailures = false

            if (granted.contains(HealthPermission.getReadPermission(StepsRecord::class))) {
                stepsToday = healthConnectManager.readStepsWithFallback(startOfToday, now, appPkg) ?: 0L
                editor.putString("steps_count", "%,d".format(stepsToday))
                roomWriter.upsertMetric(date = todayKey, steps = stepsToday, stepsSynced = true)
            }
            if (granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))) {
                val distanceToday = healthConnectManager.readDistanceMeters(startOfToday, now, appPkg) / 1000.0
                editor.putString("distance_val", "%.2f km".format(distanceToday))
                healthDataManager.saveHistoricalDistance(todayKey, distanceToday)
                roomWriter.upsertMetric(date = todayKey, distanceKm = distanceToday)
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
                    roomWriter.upsertMetric(date = todayKey, sleepHours = totalDurationMin / 60.0)
                }
            }
            
            var activeSynced = false
            if (granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))) {
                val activeBurned = healthConnectManager.readActiveCalories(startOfToday, now, appPkg)
                if (activeBurned > 0) {
                    caloriesToday = "%.0f".format(activeBurned)
                    healthDataManager.saveHistoricalActiveCalories(todayKey, activeBurned)
                    roomWriter.upsertMetric(date = todayKey, activeCalories = activeBurned)
                    activeSynced = true
                }
            }
            if (!activeSynced && granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))) {
                val burnedCals = healthConnectManager.readTotalCalories(startOfToday, now, appPkg)
                if (burnedCals > 0) {
                    caloriesToday = "%.0f".format(burnedCals)
                    editor.putString("calories_burnt", caloriesToday)
                    roomWriter.upsertMetric(date = todayKey, totalCalories = burnedCals)
                }
            }
            if (granted.contains(HealthPermission.getReadPermission(WeightRecord::class))) {
                weightToday = healthConnectManager.readWeightKg(startOfToday, now, appPkg)
                if (weightToday > 0) {
                    editor.putString("current_weight", "%.1f kg".format(weightToday))
                    roomWriter.upsertMetric(date = todayKey, weightKg = weightToday)
                }
            }
            if (canReadGoogleFitHeartPoints) {
                heartPointsToday = if (shouldSyncHeartPointsHistory) {
                    val oldestDate = todayDate.minusDays((HealthDataManager.SYNC_HISTORY_DAYS - 1).toLong())
                    heartPointsByDate = googleFitHeartPointsManager.readDailyHeartPoints(oldestDate, now, zoneId)
                    heartPointsByDate[todayDate.toString()] ?: googleFitHeartPointsManager.readHeartPoints(startOfToday, now)
                } else {
                    googleFitHeartPointsManager.readHeartPoints(startOfToday, now)
                }
                if (heartPointsByDate.isNotEmpty() || heartPointsToday > 0.0) {
                    healthDataManager.setHeartPoints(heartPointsToday.toInt())
                    roomWriter.upsertMetric(date = todayKey, heartPoints = heartPointsToday)
                    Log.d("MainActivity", "Google Fit Heart Points synced: $heartPointsToday")
                    Toast.makeText(this@MainActivity, "✓ Google Fit Heart Points: ${heartPointsToday.toInt()}", Toast.LENGTH_LONG).show()
                } else {
                    Log.w("MainActivity", "Google Fit returned no Heart Points; preserving cached today value")
                }
            } else if (appPkg == GoogleFitHeartPointsManager.GOOGLE_FIT_PACKAGE) {
                Log.w("MainActivity", "Google Fit Heart Points permission not granted; not auto-requesting to avoid account picker loop")
                showGoogleFitHeartPointsUnavailableToast()
            } else {
                Log.w("MainActivity", "Google Fit Heart Points permission unavailable; preserving cached Heart Points")
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
                        val historicalSteps = healthConnectManager.readStepsWithFallback(dayStart, dayEnd, appPkg)
                        if (historicalSteps != null) {
                            healthDataManager.saveHistoricalSteps(dateStr, historicalSteps)
                            roomWriter.upsertMetric(date = dateStr, steps = historicalSteps, stepsSynced = true)
                        } else {
                            stepHistoryHadReadFailures = true
                            Log.w("MainActivity", "Step history read failed for $dateStr; leaving day pending for retry")
                        }
                        
                        // Distance History
                        if (granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))) {
                            val distKm = healthConnectManager.readDistanceMeters(dayStart, dayEnd, appPkg) / 1000.0
                            if (distKm > 0) {
                                healthDataManager.saveHistoricalDistance(dateStr, distKm)
                                roomWriter.upsertMetric(date = dateStr, distanceKm = distKm)
                            }
                        }

                        // Calorie History
                        var histActiveSynced = false
                        if (granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))) {
                            val activeBurned = healthConnectManager.readActiveCalories(dayStart, dayEnd, appPkg)
                            if (activeBurned > 0) {
                                healthDataManager.saveHistoricalActiveCalories(dateStr, activeBurned)
                                roomWriter.upsertMetric(date = dateStr, activeCalories = activeBurned)
                                histActiveSynced = true
                            }
                        }
                        if (!histActiveSynced && granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))) {
                            val historicalCalories = healthConnectManager.readTotalCalories(dayStart, dayEnd, appPkg)
                            if (historicalCalories > 0) {
                                healthDataManager.saveHistoricalCalories(dateStr, historicalCalories)
                                roomWriter.upsertMetric(date = dateStr, totalCalories = historicalCalories)
                            }
                        }
                    }
                    if (granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
                        val sessions = healthConnectManager.readSleepSessions(dayStart, dayEnd, appPkg)
                        val mins = sessions.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                        healthDataManager.saveHistoricalSleep(dateStr, mins / 60.0)
                        roomWriter.upsertMetric(date = dateStr, sleepHours = mins / 60.0)
                    }
                    if (granted.contains(HealthPermission.getReadPermission(WeightRecord::class))) {
                        val weightKg = healthConnectManager.readWeightKg(dayStart, dayEnd, appPkg)
                        if (weightKg > 0) {
                            healthDataManager.saveWeight(dateStr, weightKg)
                            roomWriter.upsertMetric(date = dateStr, weightKg = weightKg)
                        }
                    }
                    if (canReadGoogleFitHeartPoints && heartPointsByDate.containsKey(dateStr)) {
                        val historicalHeartPoints = heartPointsByDate[dateStr] ?: 0.0
                        healthDataManager.saveHistoricalHeartPoints(dateStr, historicalHeartPoints)
                        roomWriter.upsertMetric(date = dateStr, heartPoints = historicalHeartPoints)
                    }
                }
                if (shouldSyncStepHistory) {
                    val stepPermissionGranted = granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
                    val historyComplete = stepPermissionGranted && !stepHistoryHadReadFailures && healthDataManager.isStepHistoryComplete()
                    healthDataManager.setInitialHistorySyncDone(historyComplete)
                    Log.d(
                        "MainActivity",
                        "180-day step history complete: $historyComplete (${healthDataManager.countHistoricalStepSyncedDays()}/${HealthDataManager.SYNC_HISTORY_DAYS})"
                    )
                }
                if (shouldSyncHeartPointsHistory) {
                    val heartPointsHistoryComplete = heartPointsByDate.isNotEmpty() && healthDataManager.isHeartPointsHistoryComplete()
                    healthDataManager.setInitialHeartPointsHistorySyncDone(heartPointsHistoryComplete)
                    Log.d(
                        "MainActivity",
                        "180-day Heart Points history complete: $heartPointsHistoryComplete (${healthDataManager.countHistoricalHeartPointsSyncedDays()}/${HealthDataManager.SYNC_HISTORY_DAYS})"
                    )
                }
            }

            val timestamp = java.text.SimpleDateFormat("hh:mm a, dd MMM", java.util.Locale.US).format(java.util.Date())
            healthDataManager.setLastSyncTime(timestamp)
            
            editor.apply()
            healthDataManager.pruneHistoricalData()
            roomWriter.pruneToRetentionWindow()
            updateDashboard()
            
            val summary = StringBuilder("Sync complete from $appName!")
            if (stepsToday > 0) summary.append("\nSteps Today: %,d".format(stepsToday))
            if (moveMinsToday > 0) summary.append("\nMove: $moveMinsToday min")
            if (heartPointsToday > 0) summary.append("\nHeart Points: %.1f".format(heartPointsToday))
            if (caloriesToday.isNotEmpty() && caloriesToday != "0") summary.append("\nBurned Today: $caloriesToday kcal")
            if (weightToday > 0) summary.append("\nWeight: %.1f kg".format(weightToday))
            
            SyncLogManager.addLog(this@MainActivity, appName, "Manual Sync: Steps, Sleep, Heart Points", true)
            Toast.makeText(this@MainActivity, summary.toString(), Toast.LENGTH_LONG).show()
        }
    }

    private fun showSyncLogDialog() {
        val logs = SyncLogManager.getLogs(this)
        if (logs.isEmpty()) {
            Toast.makeText(this, "No sync logs recorded for today yet.", Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        logs.forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 16, 0, 16)
            }
            val timeTv = TextView(this@MainActivity).apply {
                text = entry.formatTime()
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.textSecondary))
            }
            val msgTv = TextView(this@MainActivity).apply {
                text = "${entry.source}: ${entry.message}"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@MainActivity, if (entry.isSuccess) R.color.textPrimary else android.R.color.holo_red_dark))
            }
            row.addView(timeTv)
            row.addView(msgTv)
            container.addView(row)
            
            // Divider
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                    setMargins(0, 8, 0, 8)
                }
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.m3_premium_stroke))
            }
            container.addView(divider)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Daily Sync History")
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            dataUpdatedReceiver,
            IntentFilter(WellnessWidget.ACTION_DATA_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Ensure the next background sync is scheduled without starting a foreground sync.
        HealthSyncWorker.scheduleAutoSync(this)

        updateGreeting()
        updateDashboard()

        if (firstLaunchPermissionFlowActive && waitingForExactAlarmSettings) {
            waitingForExactAlarmSettings = false
            firstLaunchPermissionStep = FirstLaunchPermissionStep.HEALTH_CONNECT
            findViewById<View>(android.R.id.content).postDelayed({
                continueFirstLaunchPermissionFlow()
            }, 500)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !entranceAnimationPlayed) {
            entranceAnimationPlayed = true
            startHomeHeaderAnimationIfNeeded(homeSavedInstanceState)
        } else if (hasFocus && ValueAnimator.areAnimatorsEnabled()) {
            headerBackground?.takeUnless { it.isRunning }?.start()
        }
    }

    override fun onPause() {
        headerBackground?.stop()
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

        val avatarId = UserPreferencesStore.getUserAvatarId(this)
        AvatarHelper.applyAvatar(findViewById(R.id.ivHeaderAvatar), avatarId)
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
        HapticHelper.triggerThump(this)
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
                val bmr = WellnessEngine.calculateBMRForDate(this, todayStr).toInt()
                val activeBurn = WellnessEngine.calculateActiveBurn(this, stepsCount, weightValForBurn).toInt()
                val tef = WellnessEngine.calculateTEF(intakeTotal).toInt()
                val totalBurned = bmr + activeBurn + tef
                val netBalance = intakeTotal - totalBurned
                
                Log.d("BurnEngine", "Intake: $intakeTotal | BMR: $bmr | Active: $activeBurn | TEF: $tef | Total Burned: $totalBurned | Net: $netBalance")

                findViewById<TextView>(R.id.tvValSteps).text = if (healthDataManager.isConnected() && stepsCount > 0) healthDataManager.getSteps() else "0"
                findViewById<TextView>(R.id.tvValSleep).text = if (healthDataManager.isConnected() && sleepHours > 0) healthDataManager.getSleep() else "0h"
                
                val goal = healthDataManager.getDailyCalorieGoal().let { if (it > 0) it else 2000 }
                val margin = 50
                val minGreen = goal - margin
                val maxGreen = goal + margin

                val (calorieColorRes, calorieText) = when {
                    intakeTotal < minGreen -> {
                        R.color.calorieStatusOrange to netBalance.toString()
                    }
                    intakeTotal in minGreen..maxGreen -> {
                        R.color.calorieStatusGreen to netBalance.toString()
                    }
                    else -> { // intakeTotal > maxGreen
                        val text = if (netBalance > 0) "+$netBalance" else netBalance.toString()
                        R.color.calorieStatusRed to text
                    }
                }

                val tvValCalories = findViewById<TextView>(R.id.tvValCalories)
                tvValCalories.text = calorieText
                tvValCalories.setTextColor(ContextCompat.getColor(this@MainActivity, calorieColorRes))

                findViewById<TextView>(R.id.tvCalorieIn).text = intakeTotal.toString()
                findViewById<TextView>(R.id.tvCalorieOut).text = totalBurned.toString()
                
                val progress = findViewById<LinearProgressIndicator>(R.id.progressCalories)
                progress.max = goal
                progress.progress = intakeTotal.coerceAtMost(goal)
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
        findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.progressWellness).max = 10
        findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.progressWellness).progress = score
        findViewById<TextView>(R.id.tvWellnessScore).text = score.toString()
        findViewById<TextView>(R.id.tvWellnessMsg).text = when {
            score >= 9 -> "Excellent! You're a wellness pro! 🏆"
            score >= 7 -> "Great job! Keep up the momentum! ✨"
            score >= 4 -> "Good start! You're making progress. 👍"
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
