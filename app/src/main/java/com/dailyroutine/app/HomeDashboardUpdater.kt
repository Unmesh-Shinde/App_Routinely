package com.dailyroutine.app

import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Refreshes the home dashboard from the current plan, reminder, and health data. */
internal class HomeDashboardUpdater(
    private val activity: MainActivity,
    private val reminderManager: ReminderManager,
    private val planManager: PlanManager,
    private val healthDataManager: HealthDataManager
) {
    fun update() {
        val allReminders = reminderManager.getAllReminders().filter { it.isEnabled }

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
        WellnessEngine.calculateIntakeForDate(activity, todayStr) { intakeTotal ->
            activity.runOnUiThread {
                val weightValForBurn = healthDataManager.getWeight(todayStr).let { if (it > 0) it else 70.0 }
                val bmr = WellnessEngine.calculateBMRForDate(activity, todayStr).toInt()
                val activeBurn = WellnessEngine.calculateActiveBurn(activity, stepsCount, weightValForBurn).toInt()
                val tef = WellnessEngine.calculateTEF(intakeTotal).toInt()
                val totalBurned = bmr + activeBurn + tef
                val netBalance = intakeTotal - totalBurned
                
                Log.d("BurnEngine", "Intake: $intakeTotal | BMR: $bmr | Active: $activeBurn | TEF: $tef | Total Burned: $totalBurned | Net: $netBalance")

                activity.findViewById<TextView>(R.id.tvValSteps).text = if (healthDataManager.isConnected() && stepsCount > 0) healthDataManager.getSteps() else "0"
                activity.findViewById<TextView>(R.id.tvValSleep).text = if (healthDataManager.isConnected() && sleepHours > 0) healthDataManager.getSleep() else "0h"
                
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

                val tvValCalories = activity.findViewById<TextView>(R.id.tvValCalories)
                tvValCalories.text = calorieText
                tvValCalories.setTextColor(ContextCompat.getColor(activity, calorieColorRes))

                activity.findViewById<TextView>(R.id.tvCalorieIn).text = intakeTotal.toString()
                activity.findViewById<TextView>(R.id.tvCalorieOut).text = totalBurned.toString()
                
                val progress = activity.findViewById<LinearProgressIndicator>(R.id.progressCalories)
                progress.max = goal
                progress.progress = intakeTotal.coerceAtMost(goal)
            }
        }
        
        val weightVal = healthDataManager.getWeight(todayStr)
        val profileWeight = UserPreferencesStore.getUserWeight(activity)
        activity.findViewById<TextView>(R.id.tvValWeight).text = when {
            weightVal > 0 -> "$weightVal kg"
            UserPreferencesStore.hasUserWeight(activity) && profileWeight > 0 -> "$profileWeight kg"
            else -> "Log Today's Weight"
        }
        
        val waterValManual = healthDataManager.getWaterIntake(todayStr)
        val doneIds = RoutineProgressStore.getDoneIds(activity)
        val waterFromReminders = allReminders
            .filter { it.type == ReminderType.HYDRATION && it.id.toString() in doneIds }
            .size * 0.25

        val waterTotal = waterValManual + waterFromReminders
        activity.findViewById<TextView>(R.id.tvValWater).text = "${formatWaterLiters(waterTotal)} L"
        activity.findViewById<View>(R.id.ivWaterDrop).contentDescription =
            "${activity.getString(R.string.hydration_label)}: ${formatWaterLiters(waterTotal)} Liters"

        val latestSyncLog = SyncLogManager.getLogs(activity).firstOrNull()
        val syncLogsButton = activity.findViewById<MaterialButton>(R.id.btnSyncLogs)
        syncLogsButton.text = "Sync Logs"
        syncLogsButton.contentDescription = "Sync Logs. Last sync: ${latestSyncLog?.formatTime() ?: "Never"}"

        val doneIdsForScore = RoutineProgressStore.getDoneIds(activity, todayStr)

        val nutritionDone = mealsList.count { it.id.toString() in doneIdsForScore }
        val nutritionTotal = mealsList.size
        val workoutDone = workoutList.count { it.id.toString() in doneIdsForScore }
        val workoutTotal = workoutList.size

        val score = WellnessScoreManager.calculateDailyScore(
            activity, stepsCount, sleepHours,
            workoutDone, workoutTotal,
            nutritionDone, nutritionTotal
        )
        WellnessScoreManager.saveDailyScore(activity, todayStr, score)
        activity.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.progressWellness).max = 10
        activity.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.progressWellness).progress = score
        activity.findViewById<TextView>(R.id.tvWellnessScore).text = score.toString()
        activity.findViewById<TextView>(R.id.tvWellnessMsg).text = when {
            score >= 9 -> "Excellent! You're a wellness pro! 🏆"
            score >= 7 -> "Great job! Keep up the momentum! ✨"
            score >= 4 -> "Good start! You're making progress. 👍"
            else -> "Keep moving to reach your goals!"
        }

        WellnessWidget.refresh(activity)
    }

    private fun formatWaterLiters(amount: Double): String =
        String.format(Locale.US, "%.2f", amount).trimEnd('0').trimEnd('.')
}
