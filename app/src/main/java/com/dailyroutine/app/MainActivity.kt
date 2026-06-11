package com.dailyroutine.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var mgr: ReminderManager
    private lateinit var planManager: PlanManager
    private lateinit var healthDataManager: HealthDataManager

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

        updateGreeting()
        updateDashboard()

        findViewById<View>(R.id.cardProgress).setOnClickListener {
            startActivity(Intent(this, HabitProgressActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardDiet).setOnClickListener {
            startActivity(Intent(this, DietPlanActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardWorkout).setOnClickListener {
            startActivity(Intent(this, WorkoutPlanActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardWalking).setOnClickListener {
            startActivity(Intent(this, WalkingDataActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardSleep).setOnClickListener {
            startActivity(Intent(this, SleepTrackingActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardWeight).setOnClickListener {
            startActivity(Intent(this, WeightActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardCalories).setOnClickListener {
            startActivity(Intent(this, CaloriesActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnHealthSync).setOnClickListener {
            // Simulate fetching data from a fitness band
            val prefs = getSharedPreferences("health_data_pref", MODE_PRIVATE)
            prefs.edit()
                .putBoolean("is_fitness_connected", true)
                .putString("steps_count", "7,250")
                .putString("sleep_hours", "7h 15m")
                .putString("calories_burnt", "1,420")
                .putString("current_weight", "74.2 kg")
                .apply()
            
            updateDashboard()
            Toast.makeText(this, "Health Data Synced from Band! ⌚", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnReminders).setOnClickListener { view ->
            val popup = PopupMenu(this, view, Gravity.TOP)
            popup.menu.add("Set Reminders")
            popup.menu.add("View Reminders")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Set Reminders" -> ReminderDialogHelper.showDialog(this, mgr, null, view) {
                        updateDashboard()
                    }
                    "View Reminders" -> startActivity(Intent(this, RemindersActivity::class.java))
                }
                true
            }
            popup.show()
        }

        requestNotificationPermission()
        requestExactAlarmPermission()
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val name = UserPreferencesStore.getUserName(this)
        val greeting = when (hour) {
            in 0..11 -> "Good Morning, $name 👋"
            in 12..16 -> "Good Afternoon, $name 👋"
            in 17..20 -> "Good Evening, $name 👋"
            else -> "Good Night, $name 👋"
        }
        findViewById<TextView>(R.id.tvGreeting).text = greeting
    }

    private fun updateDashboard() {
        val allReminders = mgr.getAllReminders().filter { it.isEnabled }
        val activeRemindersCount = allReminders.filter { !it.isHidden }.size
        
        // Count today's plan items
        val calendar = Calendar.getInstance()
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(calendar.time)
        
        val todayMeals = planManager.getMealsForDate(todayStr).size
        val todayWorkout = planManager.getExercisesForDate(todayStr).size
        
        val totalHabits = activeRemindersCount + todayMeals + todayWorkout
        val doneHabits = RoutineProgressStore.getDoneCount(this)
        
        // Progress Card
        findViewById<TextView>(R.id.tvHabitProgress).text = 
            "Today's Progress: $doneHabits/$totalHabits habits completed"
        val progress = if (totalHabits > 0) (doneHabits * 100 / totalHabits) else 0
        findViewById<LinearProgressIndicator>(R.id.progressHabits).progress = progress

        // Wellness Tracking (Dynamic)
        val stepsStr = healthDataManager.getSteps().replace(",", "")
        val stepsCount = stepsStr.toIntOrNull() ?: 0
        val sleepStr = healthDataManager.getSleep().replace("h", "").replace("m", "").trim()
        // Simple parse for "7h 15m" -> approx 7.25
        val sleepHours = try {
            val parts = healthDataManager.getSleep().split(" ")
            var h = 0.0
            var m = 0.0
            parts.forEach { 
                if (it.contains("h")) h = it.replace("h", "").toDoubleOrNull() ?: 0.0
                if (it.contains("m")) m = it.replace("m", "").toDoubleOrNull() ?: 0.0
            }
            h + (m / 60.0)
        } catch(e: Exception) { 0.0 }

        findViewById<TextView>(R.id.tvValSteps).text = if (healthDataManager.isConnected()) healthDataManager.getSteps() else "0"
        findViewById<TextView>(R.id.tvValSleep).text = if (healthDataManager.isConnected()) healthDataManager.getSleep() else "0h"
        findViewById<TextView>(R.id.tvValCalories).text = if (healthDataManager.isConnected()) healthDataManager.getCalories() else "0"
        findViewById<TextView>(R.id.tvValWeight).text = if (healthDataManager.isConnected()) healthDataManager.getWeight() else "0 kg"

        // Calculate and Update Wellness Score
        val score = WellnessScoreManager.calculateDailyScore(this, stepsCount, sleepHours, doneHabits, totalHabits)
        findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.progressWellness).progress = score
        findViewById<TextView>(R.id.tvWellnessScore).text = score.toString()
        findViewById<TextView>(R.id.tvWellnessMsg).text = when {
            score >= 90 -> "Excellent! You're a wellness pro! 🏆"
            score >= 70 -> "Great job! Keep up the momentum! ✨"
            score >= 40 -> "Good start! You're making progress. 👍"
            else -> "Keep moving to reach your goals! 💪"
        }

        // Upcoming Reminder
        val now = Calendar.getInstance()
        val upcoming = allReminders
            .filter { it.isEnabled && !it.isIntervalBased }
            .filter { (it.hour * 60 + it.minute) > (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)) }
            .minByOrNull { it.hour * 60 + it.minute }

        if (upcoming != null) {
            findViewById<View>(R.id.cardUpcoming).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvUpcomingText).text = 
                "${upcoming.type.emoji} ${upcoming.title.replace("Meal: ", "").replace("Exercise: ", "")} - ${upcoming.formatTime()}"
        } else {
            findViewById<View>(R.id.cardUpcoming).visibility = View.GONE
        }
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
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }
}
