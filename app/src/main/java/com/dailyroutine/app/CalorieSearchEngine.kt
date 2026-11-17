package com.dailyroutine.app

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class NutritionResult {
    data class Success(val info: MealNutritionInfo, val fromCache: Boolean = false) : NutritionResult()
    data class Error(
        val code: String,
        val message: String,
        val scope: String = "unknown",
        val httpStatus: Int? = null,
        val upstreamStatus: Int? = null,
        val retryable: Boolean = false,
        val retryAtMillis: Long? = null
    ) : NutritionResult()
}

data class AiRequestBudget(
    val usageDate: String,
    val mealType: String,
    val reserve: () -> Boolean,
    val release: () -> Unit
) {
    val inFlightKey: String
        get() = "${usageDate}|${mealType.lowercase().trim()}"
}

object CalorieSearchEngine {
    
    // New namespace invalidates prior entries containing inferred values or zero-as-missing data.
    private const val PREFS_CACHE = "calorie_cache_pref_v7"
    private val scope = CoroutineScope(Dispatchers.Main)
    private val inFlightRequests = InFlightRequestRegistry<String, NutritionResult>()

    fun clearCache(context: Context) {
        context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE).edit().clear().apply()
        CalorieEstimator.clearSmartCache(context)
    }

    fun getCalories(context: Context, text: String, onResult: (Int) -> Unit) {
        getMealNutrition(context, text, "") { info ->
            onResult(info.calories ?: 0)
        }
    }

    fun getCalories(context: Context, title: String, description: String, onResult: (Int) -> Unit) {
        getMealNutrition(context, title, description) { info ->
            onResult(info.calories ?: 0)
        }
    }

    fun getMealNutrition(context: Context, title: String, description: String, onResult: (MealNutritionInfo) -> Unit) {
        getMealNutritionResult(context, title, description) { result ->
            when (result) {
                is NutritionResult.Success -> onResult(result.info)
                is NutritionResult.Error -> onResult(MealNutritionInfo())
            }
        }
    }

    fun getMealNutritionResult(
        context: Context,
        title: String,
        description: String,
        budget: AiRequestBudget? = null,
        onResult: (NutritionResult) -> Unit
    ) {
        val normalizedTitle = title.lowercase().trim()
        val normalizedDescription = description.lowercase().trim()
        if (normalizedTitle.isEmpty() && normalizedDescription.isEmpty()) {
            onResult(NutritionResult.Success(MealNutritionInfo(), fromCache = true))
            return
        }

        val learnedLocally = CalorieEstimator.getLearnedMealNutrition(context, title, description)
        if (learnedLocally != null && learnedLocally.hasAnyNutrition()) {
            onResult(NutritionResult.Success(learnedLocally, fromCache = true))
            return
        }

        val cacheKey = "v7_nutrition|title=$normalizedTitle|desc=$normalizedDescription"
        val cache = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
        val cachedJson = cache.getString(cacheKey, null)
        if (cachedJson != null) {
            try {
                val cachedObj = Gson().fromJson(cachedJson, MealNutritionInfo::class.java)
                if (cachedObj != null && cachedObj.hasAnyNutrition()) {
                    cache.edit().putString(cacheKey, Gson().toJson(cachedObj)).apply()
                    CalorieEstimator.saveLearnedMeal(context, title, description, cachedObj)
                    onResult(NutritionResult.Success(cachedObj, fromCache = true))
                    return
                } else {
                    cache.edit().remove(cacheKey).apply()
                }
            } catch (_: Exception) {}
        }

        val requestKey = if (budget == null) cacheKey else "$cacheKey|budget=${budget.inFlightKey}"
        if (!inFlightRequests.addOrJoin(requestKey, onResult)) return

        if (budget != null) {
            val reserved = try {
                budget.reserve()
            } catch (_: Exception) {
                false
            }
            if (!reserved) {
                inFlightRequests.complete(
                    requestKey,
                    NutritionResult.Error(
                        "ai_daily_limit_reached",
                        "Daily AI calculation limit reached for ${budget.mealType}. Try again tomorrow."
                    )
                )
                return
            }
        }

        scope.launch {
            val result = try {
                when (val geminiResult = GeminiClient.getNutritionResultForMeal(
                    title = normalizedTitle,
                    description = normalizedDescription,
                    context = context.applicationContext
                )) {
                    is GeminiResult.Success -> {
                        val info = CalorieEstimator.enrichMealNutrition(geminiResult.data)
                        if (!info.hasAnyNutrition()) {
                            NutritionResult.Error(
                                "ai_empty_response",
                                "AI returned no usable nutrition values. The meal can be retried.",
                                scope = "provider",
                                retryable = true
                            )
                        } else {
                            val jsonStr = Gson().toJson(info)
                            cache.edit().putString(cacheKey, jsonStr).apply()
                            CalorieEstimator.saveLearnedMeal(context, title, description, info)
                            NutritionResult.Success(info, fromCache = false)
                        }
                    }
                    is GeminiResult.Error -> NutritionResult.Error(
                        code = geminiResult.code,
                        message = geminiResult.message,
                        scope = geminiResult.scope,
                        httpStatus = geminiResult.httpStatus,
                        upstreamStatus = geminiResult.upstreamStatus,
                        retryable = geminiResult.retryable,
                        retryAtMillis = geminiResult.retryAtMillis
                    )
                }
            } catch (exception: Exception) {
                NutritionResult.Error(
                    "nutrition_lookup_failed",
                    exception.message ?: "Nutrition lookup failed. Please try again.",
                    scope = "network",
                    retryable = true
                )
            }
            if (budget != null && result is NutritionResult.Error && result.code in setOf(
                    "ai_provider_auth_error",
                    "unauthorized",
                    "ai_provider_not_configured"
                )
            ) {
                runCatching { budget.release() }
            }
            inFlightRequests.complete(requestKey, result)
        }
    }

    fun getMealNutritionWithRetries(
        context: Context,
        title: String,
        description: String,
        maxImmediateRetries: Int = 1,
        budget: AiRequestBudget? = null,
        onResult: (NutritionResult) -> Unit
    ) {
        var attemptsMade = 0
        var lastAttemptFailure: NutritionResult.Error? = null
        fun attemptFetch() {
            attemptsMade++
            getMealNutritionResult(context, title, description, budget) { result ->
                when (result) {
                    is NutritionResult.Success -> onResult(result)
                    is NutritionResult.Error -> {
                        if (result.code == "ai_daily_limit_reached" && attemptsMade > 1) {
                            // Earlier network attempts already used today's allowance. Preserve
                            // the real failure so callers can save the meal in the retry queue.
                            onResult(lastAttemptFailure ?: result)
                            return@getMealNutritionResult
                        }
                        if (!result.retryable || attemptsMade >= maxImmediateRetries) {
                            onResult(result)
                        } else {
                            lastAttemptFailure = result
                            scope.launch {
                                kotlinx.coroutines.delay(800L * attemptsMade)
                                attemptFetch()
                            }
                        }
                    }
                }
            }
        }
        attemptFetch()
    }

    suspend fun fetchMealNutritionDirect(
        context: Context,
        title: String,
        description: String,
        budget: AiRequestBudget? = null,
        automaticRetryCategory: String? = null
    ): NutritionResult {
        val normalizedTitle = title.lowercase().trim()
        val normalizedDescription = description.lowercase().trim()
        if (normalizedTitle.isEmpty() && normalizedDescription.isEmpty()) {
            return NutritionResult.Success(MealNutritionInfo(), fromCache = true)
        }

        val learnedLocally = CalorieEstimator.getLearnedMealNutrition(context, title, description)
        if (learnedLocally != null && learnedLocally.hasAnyNutrition()) {
            return NutritionResult.Success(learnedLocally, fromCache = true)
        }

        if (budget != null) {
            val reserved = try {
                budget.reserve()
            } catch (_: Exception) {
                false
            }
            if (!reserved) {
                return NutritionResult.Error(
                    "ai_daily_limit_reached",
                    "Daily AI calculation limit reached for ${budget.mealType}. The meal remains queued for a later retry."
                )
            }
        }

        val result = when (val geminiResult = GeminiClient.getNutritionResultForMeal(
            title = normalizedTitle,
            description = normalizedDescription,
            context = context.applicationContext,
            automaticRetryCategory = automaticRetryCategory
        )) {
            is GeminiResult.Success -> {
                val info = CalorieEstimator.enrichMealNutrition(geminiResult.data)
                if (!info.hasAnyNutrition()) {
                    NutritionResult.Error(
                        "ai_empty_response",
                        "AI returned no usable nutrition values. The meal can be retried.",
                        scope = "provider",
                        retryable = true
                    )
                } else {
                    val cacheKey = "v7_nutrition|title=$normalizedTitle|desc=$normalizedDescription"
                    val cache = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
                    cache.edit().putString(cacheKey, Gson().toJson(info)).apply()
                    CalorieEstimator.saveLearnedMeal(context, title, description, info)
                    NutritionResult.Success(info, fromCache = false)
                }
            }
            is GeminiResult.Error -> NutritionResult.Error(
                code = geminiResult.code,
                message = geminiResult.message,
                scope = geminiResult.scope,
                httpStatus = geminiResult.httpStatus,
                upstreamStatus = geminiResult.upstreamStatus,
                retryable = geminiResult.retryable,
                retryAtMillis = geminiResult.retryAtMillis
            )
        }
        if (budget != null && result is NutritionResult.Error && result.code in setOf(
                "ai_provider_auth_error",
                "unauthorized",
                "ai_provider_not_configured"
            )
        ) {
            runCatching { budget.release() }
        }
        return result
    }

    fun calculateActiveBurn(context: Context, steps: Int, weight: Double): Double {
        return WellnessEngine.calculateActiveBurn(context, steps, weight)
    }
}
