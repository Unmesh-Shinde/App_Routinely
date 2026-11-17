package com.dailyroutine.app

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

sealed class GeminiResult<out T> {
    data class Success<out T>(val data: T) : GeminiResult<T>()
    data class Error(
        val code: String,
        val message: String,
        val scope: String = "unknown",
        val httpStatus: Int? = null,
        val upstreamStatus: Int? = null,
        val retryable: Boolean = false,
        val retryAtMillis: Long? = null
    ) : GeminiResult<Nothing>()
}

object GeminiClient {
    private const val LOG_TAG = "GeminiClient"
    // One extra Worker request is reserved only for refreshing a rejected Firebase token.
    private const val MAX_BACKEND_ATTEMPTS = 2
    private val RETRY_DELAYS_MS = longArrayOf(300L)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getCaloriesForMeal(query: String, context: Context? = null): Int =
        withContext(Dispatchers.IO) {
            getCaloriesForMeal(title = query, description = "", context = context)
        }

    suspend fun getCaloriesForMeal(
        title: String,
        description: String,
        context: Context? = null,
    ): Int = getNutritionForMeal(title, description, context).calories ?: 0

    suspend fun getNutritionForMeal(
        title: String,
        description: String,
        context: Context? = null,
    ): MealNutritionInfo = withContext(Dispatchers.IO) {
        when (val result = getNutritionResultForMeal(title, description, context)) {
            is GeminiResult.Success -> result.data
            is GeminiResult.Error -> MealNutritionInfo()
        }
    }

    suspend fun getNutritionResultForMeal(
        title: String,
        description: String,
        context: Context? = null,
        automaticRetryCategory: String? = null,
    ): GeminiResult<MealNutritionInfo> = withContext(Dispatchers.IO) {
        val body = createMealNutritionRequestBody(title, description, automaticRetryCategory)
        val response = postToBackendWithDetails("/v1/ai/meal-calories", body)
        if (response.json != null) {
            val info = parseMealNutritionResponse(response.json)
            Log.d(LOG_TAG, "Meal nutrition received (partial values allowed): $info")
            return@withContext GeminiResult.Success(info)
        }

        val code = response.errorCode.ifBlank { "ai_provider_error" }
        val userMessage = when (code) {
            "ai_provider_quota" -> "AI service busy due to high traffic. Please wait a minute and try again."
            "ai_provider_rate_limited" -> "The nutrition service is handling too many requests right now. Your meal will be retried when capacity is available."
            "ai_provider_location" -> "The nutrition service is unavailable from the server's current location. Your meal is saved while service access is checked."
            "ai_payload_too_large" -> "The meal details are too long for a nutrition estimate. Shorten the description and try again."
            "ai_content_blocked" -> "The current meal title or description could not be used for an estimate. Edit it to be clearer or more neutral, then try again."
            "ai_model_unavailable" -> "The configured nutrition models are unavailable. Your meal is saved while the service is checked."
            "ai_provider_bad_request" -> "The nutrition request could not be processed. Your meal is saved while the request is checked."
            "ai_empty_response" -> "AI returned incomplete nutrition details. Please try again."
            "ai_provider_auth_error", "unauthorized" -> "AI service authorization error. Please check server status."
            "ai_retry_budget_exhausted" -> "The daily automatic estimate allowance for this meal category is used. The meal remains saved until the reset."
            "ai_provider_daily_budget_reached" -> "The shared daily nutrition service limit has been reached. The meal remains saved until the reset."
            "ai_budget_unavailable" -> "The nutrition service could not check its retry allowance. The meal remains saved for another attempt."
            "network_error" -> "Unable to connect to AI server. Please check your internet connection."
            "ai_invalid_response" -> "The nutrition service returned an unusable response. Your meal is saved for another attempt."
            else -> "AI service temporary error. Please try again shortly."
        }
        Log.w(LOG_TAG, "AI backend error ($code): $userMessage")
        GeminiResult.Error(
            code = code,
            message = userMessage,
            scope = response.scope,
            httpStatus = response.statusCode,
            upstreamStatus = response.upstreamStatus,
            retryable = response.retryable,
            retryAtMillis = response.retryAtMillis
        )
    }

    internal fun createMealNutritionRequestBody(
        title: String,
        description: String,
        automaticRetryCategory: String? = null
    ): JSONObject = JSONObject().apply {
        put("title", title)
        put("description", description)
        if (!automaticRetryCategory.isNullOrBlank()) {
            put("request_kind", "automatic_retry")
            put("meal_category", automaticRetryCategory)
        }
    }

    internal fun parseMealNutritionResponse(responseJson: JSONObject): MealNutritionInfo {
        val totalCalories = optNumericDoubleOrNull(responseJson, "total_calories", "calories", "totalCalories")
            ?.takeIf { it <= 20_000.0 }
            ?.roundToInt()
        return MealNutritionInfo(
            calories = totalCalories,
            carbsG = optNumericDoubleOrNull(responseJson, "carbs_g", "carbs", "carbohydrates", "carbsG"),
            proteinG = optNumericDoubleOrNull(responseJson, "protein_g", "protein", "proteinG"),
            fatG = optNumericDoubleOrNull(responseJson, "fat_g", "fat", "fatG"),
            fiberG = optNumericDoubleOrNull(responseJson, "fiber_g", "fiber", "fiberG"),
            fatBreakdown = parseFlexibleJsonMap(responseJson, "fat_breakdown"),
            carbBreakdown = parseFlexibleJsonMap(responseJson, "carb_breakdown"),
            vitamins = parseFlexibleJsonMap(responseJson, "vitamins"),
            minerals = parseFlexibleJsonMap(responseJson, "minerals"),
            aminoAcids = parseFlexibleJsonMap(responseJson, "amino_acids"),
            antioxidants = parseFlexibleJsonMap(responseJson, "antioxidants"),
            otherNutrients = parseFlexibleJsonMap(responseJson, "other_nutrients")
        )
    }

    private fun optNumericDoubleOrNull(json: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            if (!json.has(key) || json.isNull(key)) continue
            val opt = json.opt(key)
            val parsed = when (opt) {
                is Number -> opt.toDouble()
                is String -> {
                    val text = opt.trim()
                    text.toDoubleOrNull() ?: Regex("""[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?""")
                        .find(text)?.value?.toDoubleOrNull()
                }
                else -> null
            }
            if (parsed != null && parsed.isFinite() && parsed >= 0.0) return parsed
        }
        return null
    }

    private fun parseFlexibleJsonMap(json: JSONObject, key: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!json.has(key) || json.isNull(key)) return result

        val obj = json.optJSONObject(key)
        if (obj != null) {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k, "")
                if (v.isNotBlank()) {
                    result[k] = v
                }
            }
            return result
        }

        val array = json.optJSONArray(key)
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                if (item != null) {
                    val name = item.optString("name", item.optString("nutrient", ""))
                    val value = item.optString("value", item.optString("amount", ""))
                    if (name.isNotBlank() && value.isNotBlank()) {
                        result[name] = value
                    }
                }
            }
        }
        return result
    }

    suspend fun getMetForWorkout(exercise: Exercise): Double = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("exercise_name", exercise.name)
            put("target_area", exercise.targetArea)
            put("exercise_type", exercise.exerciseType.orEmpty())
            put("effort_label", exercise.effortLabel.orEmpty())
            put("sets", exercise.sets)
            put("reps", exercise.reps)
            put("duration_seconds", exercise.durationSeconds)
            put("rest_seconds", exercise.restSeconds)
            put("rounds", exercise.rounds)
            put("work_seconds", exercise.workSeconds)
            put("added_weight_kg", exercise.addedWeightKg)
            put("distance_km", exercise.distanceKm)
            put("intensity", exercise.intensity)
        }
        val result = postToBackendWithDetails("/v1/ai/workout-met", body).json?.optDouble("base_met", 0.0) ?: 0.0
        if (result in 1.0..15.0) {
            Log.d(LOG_TAG, "Workout MET received: $result")
            return@withContext result
        }

        Log.w(LOG_TAG, "AI backend unavailable for workout MET")
        0.0
    }

    private data class BackendResponse(
        val json: JSONObject?,
        val statusCode: Int = 0,
        val errorCode: String = "",
        val scope: String = "unknown",
        val upstreamStatus: Int? = null,
        val retryable: Boolean = false,
        val retryAtMillis: Long? = null,
        val errorMessage: String = ""
    )

    private suspend fun postToBackendWithDetails(path: String, body: JSONObject): BackendResponse {
        val backendUrl = BuildConfig.AI_BACKEND_URL.trim().trimEnd('/')
        if (backendUrl.isBlank()) {
            Log.w(LOG_TAG, "AI_BACKEND_URL is blank. Configure it in local.properties.")
            return BackendResponse(null, 0, "ai_provider_not_configured", "project")
        }

        var forceRefreshTokens = false
        var lastErrorCode = "network_error"
        var lastStatusCode = 0

        repeat(MAX_BACKEND_ATTEMPTS) { attemptNumber ->
            try {
                val auth = FirebaseAuth.getInstance()
                val user = auth.currentUser ?: try {
                    Tasks.await(auth.signInAnonymously()).user
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Anonymous auth failed: ${e.message}", e)
                    null
                }
                val idToken = try {
                    user?.let { Tasks.await(it.getIdToken(forceRefreshTokens)).token }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "getIdToken failed: ${e.message}", e)
                    null
                }
                val appCheckToken = try {
                    Tasks.await(
                        FirebaseAppCheck.getInstance().getAppCheckToken(forceRefreshTokens),
                    ).token
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "getAppCheckToken failed: ${e.message}", e)
                    null
                }

                if (idToken.isNullOrBlank() || appCheckToken.isNullOrBlank()) {
                    Log.w(LOG_TAG, "Firebase security tokens unavailable (idToken=${idToken != null}, appCheckToken=${appCheckToken != null})")
                    return BackendResponse(null, 401, "ai_provider_auth_error", "firebase")
                }

                val request = Request.Builder()
                    .url(backendUrl + path)
                    .addHeader("Authorization", "Bearer $idToken")
                    .addHeader("X-Firebase-AppCheck", appCheckToken)
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    lastStatusCode = response.code
                    if (response.isSuccessful) {
                        return BackendResponse(JSONObject(rawBody), response.code, "")
                    }

                    Log.w(LOG_TAG, "Backend HTTP ${response.code}: ${rawBody.take(300)}")
                    val parsedError = parseBackendError(rawBody, response.code)
                    if (response.code == 401 && parsedError.scope == "firebase") {
                        forceRefreshTokens = true
                        lastErrorCode = parsedError.errorCode
                        if (attemptNumber < MAX_BACKEND_ATTEMPTS - 1) return@use
                    }
                    return BackendResponse(
                        json = null,
                        statusCode = response.code,
                        errorCode = parsedError.errorCode,
                        scope = parsedError.scope,
                        upstreamStatus = parsedError.upstreamStatus,
                        retryable = parsedError.retryable,
                        retryAtMillis = parsedError.retryAtMillis,
                        errorMessage = parsedError.message
                    )
                }
            } catch (error: Exception) {
                Log.e(LOG_TAG, "AI backend request failed (attempt $attemptNumber): ${error.message}", error)
                if (error is IOException) {
                    lastErrorCode = "network_error"
                    return BackendResponse(null, lastStatusCode, "network_error", "network", retryable = true)
                } else {
                    return BackendResponse(null, 0, "ai_provider_error", "client", retryable = false)
                }
            }

            delay(RETRY_DELAYS_MS[attemptNumber] ?: RETRY_DELAYS_MS.last())
        }

        return BackendResponse(null, lastStatusCode, lastErrorCode, "network", retryable = lastErrorCode == "network_error")
    }

    internal data class ParsedBackendError(
        val errorCode: String,
        val scope: String,
        val upstreamStatus: Int?,
        val retryable: Boolean,
        val retryAtMillis: Long?,
        val message: String
    )

    internal fun parseBackendError(rawBody: String, httpStatus: Int): ParsedBackendError {
        val json = runCatching { JSONObject(rawBody) }.getOrNull()
        val errorCode = json?.optString("errorCode")?.takeIf { it.isNotBlank() }
            ?: json?.optString("error")?.takeIf { it.isNotBlank() }
            ?: when (httpStatus) {
                401, 403 -> "ai_provider_auth_error"
                413 -> "ai_payload_too_large"
                429 -> "ai_provider_rate_limited"
                408, 500, 502, 503, 504 -> "ai_provider_busy"
                else -> "ai_provider_error"
            }
        val defaultRetryable = errorCode in setOf(
            "network_error", "ai_provider_busy", "ai_provider_rate_limited", "ai_provider_quota", "ai_empty_response", "ai_invalid_response"
        ) || httpStatus == 408 || (httpStatus >= 500 && httpStatus != 501)
        return ParsedBackendError(
            errorCode = errorCode,
            scope = json?.optString("scope")?.takeIf { it.isNotBlank() } ?: when (httpStatus) {
                401, 403 -> "firebase"
                413 -> "meal_input"
                429 -> "key_model"
                else -> "unknown"
            },
            upstreamStatus = json?.takeIf { it.has("upstreamStatus") && !it.isNull("upstreamStatus") }
                ?.optInt("upstreamStatus"),
            retryable = json?.takeIf { it.has("retryable") }?.optBoolean("retryable") ?: defaultRetryable,
            retryAtMillis = json?.takeIf { it.has("retryAt") && !it.isNull("retryAt") }
                ?.optLong("retryAt")?.takeIf { it > 0L },
            message = json?.optString("message")?.takeIf { it.isNotBlank() }.orEmpty()
        )
    }
}
