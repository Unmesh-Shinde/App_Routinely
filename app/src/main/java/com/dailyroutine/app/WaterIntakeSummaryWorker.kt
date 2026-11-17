package com.dailyroutine.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Saves the day's water total as a non-estimated entry in the nutrition meal list. */
class WaterIntakeSummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = Calendar.getInstance()
        val date = inputData.getString(KEY_DATE)
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        val liters = WaterIntakeSummary.totalLiters(applicationContext, date)
        PlanManager(applicationContext).saveMealForDate(date, WaterIntakeSummary.createMeal(date, liters))
        scheduleNext(applicationContext)
        WellnessWidget.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "daily_water_intake_summary"
        private const val KEY_DATE = "summary_date"

        fun scheduleNext(context: Context) {
            val now = Calendar.getInstance()
            val target = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 50)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (!after(now)) {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
                    val alreadySaved = PlanManager(context).getMealsForDate(date).any { it.isWaterIntakeSummary }
                    if (!alreadySaved) {
                        timeInMillis = now.timeInMillis + 1_000L
                    } else {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
            }
            val delay = (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<WaterIntakeSummaryWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_DATE to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(target.time)))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
