package com.dailyroutine.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var mgr: ReminderManager
    private lateinit var planManager: PlanManager
    private lateinit var healthDataManager: HealthDataManager
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Set<String>>
    
    private val systemToneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.getParcelableExtra<android.net.Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ReminderDialogHelper.updateActiveTone(uri?.toString())
        }
    }

    private val fileToneLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ReminderDialogHelper.updateActiveTone(it.toString())
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

        mgr = ReminderManager(this)
        planManager = PlanManager(this)
        healthDataManager = HealthDataManager(this)
        healthConnectManager = HealthConnectManager(this)
        
        mgr.scheduleAllEnabled()

        requestPermissionsLauncher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            fetchHealthData()
        }

        updateGreeting()
        updateDashboard()
        updateStreak()

        findViewById<TextView>(R.id.tvGreeting).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<View>(R.id.cardProgress).setOnClickListener {
            startActivity(Intent(this, HabitProgressActivity::class.java))
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
            showWaterQuickAdd()
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

        requestNotificationPermission()
        requestExactAlarmPermission()
        
        addDefaultsOnFirstRun()

        val healthPrefs = getSharedPreferences("health_data_pref", MODE_PRIVATE)
        if (healthPrefs.getBoolean("needs_initial_permission_request", true)) {
            startHealthAppScanning()
            healthPrefs.edit().putBoolean("needs_initial_permission_request", false).apply()
        }
        
        HealthSyncWorker.scheduleAutoSync(this)
    }

    private fun addDefaultsOnFirstRun() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("is_first_run", true)) {
            mgr.saveReminder(Reminder(title = "Drink Water", type = ReminderType.HYDRATION, isIntervalBased = true, intervalMinutes = 120))
            mgr.saveReminder(Reminder(title = "Healthy Meal", type = ReminderType.MEAL, hour = 13, minute = 0))
            mgr.saveReminder(Reminder(title = "Meditation", type = ReminderType.MEDITATION, hour = 8, minute = 0))
            prefs.edit().putBoolean("is_first_run", false).apply()
            updateDashboard()
        }
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
                checkHealthConnectPermissions()
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
        android.util.Log.d("DailyRoutineHealth", "Attempting to fetch data for: $appName ($appPkg)")

        lifecycleScope.launch {
            val now = Instant.now()
            val startOfToday = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            
            val prefs = getSharedPreferences("health_data_pref", MODE_PRIVATE)
            val editor = prefs.edit().putBoolean("is_fitness_connected", true)
            val granted = healthConnectManager.getGrantedPermissions()

            var stepsToday = 0L
            var moveMinsToday = 0
            var sleepToday = ""
            var caloriesToday = ""

            // 1. Fetch Today's Data
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

            // 6. Basal Metabolic Rate (BMR)
            if (granted.contains(HealthPermission.getReadPermission(BasalMetabolicRateRecord::class))) {
                // We could fetch actual BMR from system if available
            }

            // 2. Historical Backfill (Extended to 60 Days)
            for (i in 1..60) {
                val dayStart = java.time.LocalDate.now().minusDays(i.toLong()).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                val dayEnd = java.time.LocalDate.now().minusDays(i.toLong() - 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                val dateStr = java.time.LocalDate.now().minusDays(i.toLong()).toString()

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
            }

            val timestamp = java.text.SimpleDateFormat("hh:mm a, dd MMM", java.util.Locale.US).format(java.util.Date())
            healthDataManager.setLastSyncTime(timestamp)
            
            editor.apply()
            updateDashboard()
            
            val summary = StringBuilder("Sync complete from $appName!")
            if (stepsToday > 0) summary.append("\nSteps Today: %,d".format(stepsToday))
            if (moveMinsToday > 0) summary.append("\nMove: $moveMinsToday min")
            if (caloriesToday.isNotEmpty() && caloriesToday != "0") summary.append("\nBurned Today: $caloriesToday kcal")
            
            Toast.makeText(this@MainActivity, summary.toString(), Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // 🔴 Day-Rollover Protection: Check if we need to finalize "Yesterday"
        val prefs = getSharedPreferences("health_data_pref", MODE_PRIVATE)
        val todayStr = java.time.LocalDate.now().toString()
        val lastFinalized = prefs.getString("last_finalized_day", "")
        
        if (lastFinalized != "" && lastFinalized != todayStr) {
            // New day detected! Finalize yesterday's data before starting today
            fetchHealthData() // This will backfill yesterday correctly
            prefs.edit().putString("last_finalized_day", todayStr).apply()
        }

        updateDashboard()
        updateStreak()
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
        findViewById<TextView>(R.id.tvGreeting).text = "$greeting, $name!"
    }

    private fun updateStreak() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val lastUpdate = UserPreferencesStore.getLastStreakUpdate(this)
        
        if (lastUpdate != today) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterday = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
            
            var currentStreak = UserPreferencesStore.getStreakCount(this)
            
            if (lastUpdate == yesterday) {
                currentStreak++
            } else if (lastUpdate != "") {
                currentStreak = 0 
            } else {
                currentStreak = 1 
            }
            
            UserPreferencesStore.setStreakCount(this, currentStreak)
            UserPreferencesStore.setLastStreakUpdate(this, today)
        }

        val streak = UserPreferencesStore.getStreakCount(this)
        findViewById<TextView>(R.id.tvStreak).text = "🔥 $streak Days"
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

    private fun updateDashboard() {
        val allReminders = mgr.getAllReminders().filter { it.isEnabled }
        val activeRemindersCount = allReminders.filter { !it.isHidden }.size
        
        val calendar = Calendar.getInstance()
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(calendar.time)
        
        val todayMeals = planManager.getMealsForDate(todayStr).size
        val todayWorkout = planManager.getExercisesForDate(todayStr).size
        
        val totalHabits = activeRemindersCount + todayMeals + todayWorkout
        val doneHabits = RoutineProgressStore.getDoneCount(this)
        
        findViewById<TextView>(R.id.tvHabitProgress).text = 
            "Today's Progress: $doneHabits/$totalHabits habits completed"
        val progress = if (totalHabits > 0) (doneHabits * 100 / totalHabits) else 0
        findViewById<LinearProgressIndicator>(R.id.progressHabits).progress = progress

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

        val weightValForBurn = healthDataManager.getWeight(todayStr).let { if (it > 0) it else 70.0 }
        val doneIds = RoutineProgressStore.getDoneIds(this)
        val doneExercises = planManager.getExercisesForDate(todayStr).filter { it.id.toString() in doneIds }
        
        // 🟢 Use Master Wellness Engine for final counts
        val intakeTotal = WellnessEngine.calculateIntake(this)
        val burnedTotal = WellnessEngine.calculateActiveBurn(this, stepsCount, weightValForBurn)
        val netBalance = intakeTotal - burnedTotal.toInt()

        findViewById<TextView>(R.id.tvValSteps).text = if (healthDataManager.isConnected() && stepsCount > 0) healthDataManager.getSteps() else "0"
        findViewById<TextView>(R.id.tvValSleep).text = if (healthDataManager.isConnected() && sleepHours > 0) healthDataManager.getSleep() else "0h"
        findViewById<TextView>(R.id.tvValCalories).text = netBalance.toString()
        
        val weightVal = healthDataManager.getWeight(todayStr)
        findViewById<TextView>(R.id.tvValWeight).text = if (weightVal > 0) "$weightVal kg" else "Not Logged"
        
        val waterValManual = healthDataManager.getWaterIntake(todayStr)
        val waterFromReminders = allReminders
            .filter { it.type == ReminderType.HYDRATION && it.id.toString() in doneIds }
            .size * 0.25 
        
        findViewById<TextView>(R.id.tvValWater).text = "%.1f Liters".format(waterValManual + waterFromReminders)

        findViewById<TextView>(R.id.tvLastSync).text = "Last Auto-Sync: ${healthDataManager.getLastSyncTime()}"
        updateWellnessIntelligence()

        val score = WellnessScoreManager.calculateDailyScore(this, stepsCount, sleepHours, doneHabits, totalHabits)
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
            findViewById<TextView>(R.id.tvUpcomingText).text = "${upcoming.type.emoji} ${upcoming.title.replace("Meal: ", "").replace("Exercise: ", "")} - $timePrefix${upcoming.formatTime()}"
        } else {
            findViewById<View>(R.id.cardUpcoming).visibility = View.GONE
        }
        
        WellnessWidget.refresh(this)
    }

    private fun updateWellnessIntelligence() {
        WellnessEngine.checkMilestones(this)
        val insight = WellnessEngine.getTrendInsight(this)
        findViewById<TextView>(R.id.tvTrendInsight).text = insight
        
        val container = findViewById<LinearLayout>(R.id.llTrophyStrip)
        container.removeAllViews()
        
        val unlockedIds = UserPreferencesStore.getUnlockedBadges(this)
        val achieved = WellnessEngine.milestones.filter { it.id in unlockedIds }
        
        if (achieved.isEmpty()) {
            val emptyMsg = TextView(this).apply {
                text = "Keep going to earn your first trophy! 🏃‍♂️"
                textSize = 12f
                setPadding(24, 0, 24, 0)
                setTextColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.textSecondary))
            }
            container.addView(emptyMsg)
        } else {
            achieved.forEach { trophy ->
                val tv = TextView(this).apply {
                    text = "${trophy.emoji} ${trophy.title}"
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(24, 12, 24, 12)
                    setBackgroundResource(R.drawable.bg_chip)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.primaryLight))
                    setTextColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.primary))
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(0, 0, 16, 0)
                    layoutParams = lp
                }
                container.addView(tv)
            }
        }
    }

    private fun showToneSourcePicker(currentUri: String?) {
        val options = arrayOf("System Ringtones", "File Manager (MP3/Audio)")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Pick Notification Tone")
            .setItems(options) { _, which ->
                if (which == 0) {
                    val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Tone")
                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri?.let { android.net.Uri.parse(it) })
                    }
                    systemToneLauncher.launch(intent)
                } else {
                    fileToneLauncher.launch(arrayOf("audio/*"))
                }
            }
            .show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }
}
