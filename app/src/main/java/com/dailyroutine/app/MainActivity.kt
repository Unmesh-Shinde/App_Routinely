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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mgr = ReminderManager(this)
        planManager = PlanManager(this)

        updateGreeting()
        updateDashboard()

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

        findViewById<MaterialButton>(R.id.btnHealthSync).setOnClickListener {
            Toast.makeText(this, "Health Syncing...", Toast.LENGTH_SHORT).show()
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
        val greeting = when (hour) {
            in 0..11 -> "Good Morning, John 👋"
            in 12..16 -> "Good Afternoon, John 👋"
            in 17..20 -> "Good Evening, John 👋"
            else -> "Good Night, John 👋"
        }
        findViewById<TextView>(R.id.tvGreeting).text = greeting
    }

    private fun updateDashboard() {
        val allReminders = mgr.getAllReminders()
        val totalHabits = allReminders.size
        val doneHabits = RoutineProgressStore.getDoneCount(this)
        
        // Progress Card
        findViewById<TextView>(R.id.tvHabitProgress).text = 
            "Today's Progress: $doneHabits/$totalHabits habits completed"
        val progress = if (totalHabits > 0) (doneHabits * 100 / totalHabits) else 0
        findViewById<LinearProgressIndicator>(R.id.progressHabits).progress = progress

        // Today's Score
        val dietPlan = planManager.getDietPlan(PlanDuration.WEEKLY)
        val totalMeals = dietPlan.dailyMeals.values.sumOf { it.size }
        // Simple mock for summary logic as full tracking isn't in place for all yet
        findViewById<TextView>(R.id.tvScoreMeals).text = "3/5"
        findViewById<TextView>(R.id.tvScoreWorkout).text = "Done ✅"
        findViewById<TextView>(R.id.tvScoreWater).text = "2.1L"
        findViewById<TextView>(R.id.tvScoreSleep).text = "7h 20m"
        findViewById<TextView>(R.id.tvScoreOverall).text = "Overall Score: 78%"

        // Module Status
        findViewById<TextView>(R.id.tvStatusDiet).text = "Meal schedule tracking active"
        findViewById<TextView>(R.id.tvStatusWorkout).text = "15-day challenge in progress"

        // Upcoming Reminder
        val now = Calendar.getInstance()
        val upcoming = allReminders
            .filter { it.isEnabled && !it.isIntervalBased }
            .filter { (it.hour * 60 + it.minute) > (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)) }
            .minByOrNull { it.hour * 60 + it.minute }

        if (upcoming != null) {
            findViewById<View>(R.id.cardUpcoming).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvUpcomingText).text = 
                "${upcoming.type.emoji} ${upcoming.title} - ${upcoming.formatTime()}"
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
