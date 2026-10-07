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

sealed class GeminiResult<out T> {
    data class Success<out T>(val data: T) : GeminiResult<T>()
    data class Error(val code: String, val message: String) : GeminiResult<Nothing>()
}

object GeminiClient {
    private const val LOG_TAG = "GeminiClient"
    private const val MAX_BACKEND_ATTEMPTS = 3
    private val RETRY_DELAYS_MS = longArrayOf(300L, 900L)

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
    ): Int = getNutritionForMeal(title, description, context).calories

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
    ): GeminiResult<MealNutritionInfo> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("title", title)
            put("description", description)
        }
        val response = postToBackendWithDetails("/v1/ai/meal-calories", body)
        if (response.json != null) {
            val responseJson = response.json
            val totalCalories = optNumericDouble(responseJson, "total_calories", "calories", "totalCalories").toInt()
            if (totalCalories > 0) {
                val carbs = optNumericDouble(responseJson, "carbs_g", "carbs", "carbohydrates", "carbsG")
                val protein = optNumericDouble(responseJson, "protein_g", "protein", "proteinG")
                val fat = optNumericDouble(responseJson, "fat_g", "fat", "fatG")
                val fiber = optNumericDouble(responseJson, "fiber_g", "fiber", "fiberG")

                val fatBreakdownMap = parseFlexibleJsonMap(responseJson, "fat_breakdown")
                val carbBreakdownMap = parseFlexibleJsonMap(responseJson, "carb_breakdown")
                val vitaminsMap = parseFlexibleJsonMap(responseJson, "vitamins")
                val mineralsMap = parseFlexibleJsonMap(responseJson, "minerals")
                val aminoAcidsMap = parseFlexibleJsonMap(responseJson, "amino_acids")
                val antioxidantsMap = parseFlexibleJsonMap(responseJson, "antioxidants")
                val otherMap = parseFlexibleJsonMap(responseJson, "other_nutrients")

                val info = MealNutritionInfo(
                    calories = totalCalories,
                    carbsG = carbs,
                    proteinG = protein,
                    fatG = fat,
                    fiberG = fiber,
                    fatBreakdown = fatBreakdownMap,
                    carbBreakdown = carbBreakdownMap,
                    vitamins = vitaminsMap,
                    minerals = mineralsMap,
                    aminoAcids = aminoAcidsMap,
                    antioxidants = antioxidantsMap,
                    otherNutrients = otherMap
                )
                if (info.hasDetailedNutrition()) {
                    val enrichedInfo = CalorieEstimator.enrichMealNutrition(info)
                    Log.d(LOG_TAG, "Meal nutrition received: $enrichedInfo")
                    return@withContext GeminiResult.Success(enrichedInfo)
                } else {
                    Log.w(LOG_TAG, "AI returned incomplete macros for calories=$totalCalories (carbs=$carbs, protein=$protein, fat=$fat), rejecting with error.")
                    return@withContext GeminiResult.Error("ai_empty_response", "AI returned incomplete nutritional details. Please try again.")
                }
            }
        }

        val code = response.errorCode.ifBlank { "ai_provider_error" }
        val userMessage = when (code) {
            "ai_provider_quota" -> "AI service busy due to high traffic. Please wait a minute and try again."
            "ai_empty_response" -> "AI returned incomplete nutrition details. Please try again."
            "ai_provider_auth_error", "unauthorized" -> "AI service authorization error. Please check server status."
            "network_error" -> "Unable to connect to AI server. Please check your internet connection."
            else -> "AI service temporary error. Please try again shortly."
        }
        Log.w(LOG_TAG, "AI backend error ($code): $userMessage")
        GeminiResult.Error(code, userMessage)
    }

    private fun optNumericDouble(json: JSONObject, vararg keys: String): Double {
        for (key in keys) {
            if (!json.has(key) || json.isNull(key)) continue
            val opt = json.opt(key)
            if (opt is Number) return opt.toDouble()
            if (opt is String) {
                val match = Regex("""(\d+(?:\.\d+)?)""").find(opt)
                if (match != null) {
                    val parsed = match.groupValues[1].toDoubleOrNull()
                    if (parsed != null) return parsed
                }
            }
        }
        return 0.0
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
        val errorCode: String = ""
    )

    private suspend fun postToBackendWithDetails(path: String, body: JSONObject): BackendResponse {
        val backendUrl = BuildConfig.AI_BACKEND_URL.trim().trimEnd('/')
        if (backendUrl.isBlank()) {
            Log.w(LOG_TAG, "AI_BACKEND_URL is blank. Configure it in local.properties.")
            return BackendResponse(null, 0, "ai_provider_not_configured")
        }

        var forceRefreshTokens = false
        var lastErrorCode = "network_error"
        var lastStatusCode = 0

        repeat(MAX_BACKEND_ATTEMPTS) { attemptNumber ->
            try {
                val auth = FirebaseAuth.getInstance()
                val user = auth.currentUser ?: Tasks.await(auth.signInAnonymously()).user
                val idToken = user?.let { Tasks.await(it.getIdToken(forceRefreshTokens)).token }
                val appCheckToken = Tasks.await(
                    FirebaseAppCheck.getInstance().getAppCheckToken(forceRefreshTokens),
                ).token

                if (idToken.isNullOrBlank() || appCheckToken.isNullOrBlank()) {
                    Log.w(LOG_TAG, "Firebase security tokens are unavailable")
                    return BackendResponse(null, 401, "unauthorized")
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
                    if (response.code == 401) {
                        forceRefreshTokens = true
                        lastErrorCode = "unauthorized"
                    } else if (response.code == 429 || rawBody.contains("ai_provider_quota")) {
                        lastErrorCode = "ai_provider_quota"
                        return BackendResponse(null, 429, "ai_provider_quota")
                    } else {
                        lastErrorCode = "ai_provider_busy"
                    }

                    if (!isRetryableBackendStatus(response.code) || attemptNumber == MAX_BACKEND_ATTEMPTS - 1) {
                        return BackendResponse(null, response.code, lastErrorCode)
                    }
                }
            } catch (error: Exception) {
                Log.e(LOG_TAG, "AI backend request failed: ${error.message}")
                lastErrorCode = "network_error"
                if (attemptNumber == MAX_BACKEND_ATTEMPTS - 1 || error !is IOException) {
                    return BackendResponse(null, lastStatusCode, "network_error")
                }
            }

            delay(RETRY_DELAYS_MS[attemptNumber] ?: RETRY_DELAYS_MS.last())
        }

        return BackendResponse(null, lastStatusCode, lastErrorCode)
    }

    private fun isRetryableBackendStatus(status: Int): Boolean {
        return status == 401 || status == 408 || status == 429 || status >= 500
    }
}
