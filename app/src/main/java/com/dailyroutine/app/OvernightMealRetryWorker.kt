package com.dailyroutine.app

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

/** Processes only meals whose persisted retry timestamp is due. */
class OvernightMealRetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = PlanManager(applicationContext)
        manager.initializeLegacyNutritionRetries()
        val now = System.currentTimeMillis()
        val dueMeals = manager.getPendingOvernightMeals()
            .filter { it.second.nextNutritionRetryAt <= now }
            .sortedWith(compareBy<Pair<String, Meal>> { it.second.nextNutritionRetryAt }.thenBy { it.second.addedTimestamp })
            .take(MAX_MEALS_PER_RUN)

        if (dueMeals.isEmpty()) {
            NutritionRetryScheduler.schedule(applicationContext)
            return Result.success()
        }

        Log.i(TAG, "Processing ${dueMeals.size} due automatic meal estimate retries.")
        for ((date, meal) in dueMeals) {
            val result = CalorieSearchEngine.fetchMealNutritionDirect(
                context = applicationContext,
                title = meal.name,
                description = meal.description,
                automaticRetryCategory = meal.mealType
            )

            when (result) {
                is NutritionResult.Success -> {
                    manager.updateMealNutrition(
                        date = date,
                        mealId = meal.id,
                        info = result.info,
                        markPendingFalse = true,
                        expectedName = meal.name,
                        expectedDescription = meal.description
                    )
                    Log.i(TAG, "Automatic retry succeeded for meal '${meal.name}' ($date).")
                }
                is NutritionResult.Error -> {
                    if (result.code in BUDGET_DEFERRED_CODES) {
                        manager.updateMealRetryState(date, meal.id, meal.name, meal.description) { current ->
                            NutritionRetryPolicy.deferForBudget(
                                current = current,
                                errorCode = result.code,
                                retryAt = result.retryAtMillis ?: 0L,
                                now = now
                            )
                        }
                        Log.w(TAG, "Automatic retry for '${meal.name}' deferred until budget reset; attempt counters unchanged.")
                    } else {
                        Log.w(TAG, "Automatic retry failed for '${meal.name}' ($date): ${result.code}")
                        recordFailure(manager, date, meal, result)
                    }
                }
            }
            delay(1_500L)
        }

        NutritionRetryScheduler.schedule(applicationContext)
        return Result.success()
    }

    private fun recordFailure(manager: PlanManager, date: String, meal: Meal, result: NutritionResult.Error) {
        val now = System.currentTimeMillis()
        val retryable = result.retryable || result.code in RETRYABLE_CODES
        manager.updateMealRetryState(date, meal.id, meal.name, meal.description) { current ->
            if (retryable) NutritionRetryPolicy.afterRetryableFailure(current, result.code, now)
            else NutritionRetryPolicy.afterPermanentFailure(current, result.code)
        }
    }

    companion object {
        private const val TAG = "OvernightMealRetry"
        const val UNIQUE_WORK_NAME = "overnight_meal_nutrition_retry"
        private const val MAX_MEALS_PER_RUN = 4
        private val BUDGET_DEFERRED_CODES = setOf(
            "ai_retry_budget_exhausted", "ai_provider_daily_budget_reached", "ai_budget_unavailable"
        )
        private val RETRYABLE_CODES = setOf(
            "network_error", "ai_provider_busy", "ai_provider_rate_limited", "ai_provider_quota",
            "ai_empty_response", "ai_invalid_response", "ai_daily_limit_reached"
        )

        fun schedule(context: Context) = NutritionRetryScheduler.schedule(context)

        internal fun enqueueDueWork(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = OneTimeWorkRequestBuilder<OvernightMealRetryWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
