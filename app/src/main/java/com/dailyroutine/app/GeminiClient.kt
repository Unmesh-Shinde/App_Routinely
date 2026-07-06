package com.dailyroutine.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    // Gemini API key. If this key is invalid or restricted, the local estimator is used.
    private const val API_KEY = "AQ.Ab8RN6LJjFAWqVv8-aNwNe5ozESBGbVNxIrPIxKNZ0c-5Ckicg"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getCaloriesForMeal(query: String, context: Context? = null): Int = withContext(Dispatchers.IO) {
        getCaloriesForMeal(title = query, description = "", context = context)
    }

    suspend fun getCaloriesForMeal(title: String, description: String, context: Context? = null): Int = withContext(Dispatchers.IO) {
        val configs = listOf(
            "v1beta" to "gemini-2.0-flash",
            "v1beta" to "gemini-flash-latest",
            "v1beta" to "gemini-2.5-flash"
        )

        for ((apiVersion, modelName) in configs) {
            Log.d("GeminiClient", "Trying $apiVersion/$modelName")
            val aiCalories = tryCallApi(apiVersion, modelName, title, description)
            if (aiCalories > 0) return@withContext aiCalories
        }

        val localEstimate = if (context != null) {
            CalorieEstimator.estimateMealCalories(context, title, description)
        } else {
            CalorieEstimator.estimateMealCalories(listOf(title, description).joinToString(" "))
        }
        Log.w("GeminiClient", "Gemini unavailable. Using local estimate: $localEstimate kcal")
        localEstimate
    }

    suspend fun getMetForWorkout(exercise: Exercise): Double = withContext(Dispatchers.IO) {
        val configs = listOf(
            "v1beta" to "gemini-2.0-flash",
            "v1beta" to "gemini-flash-latest",
            "v1beta" to "gemini-2.5-flash"
        )

        for ((apiVersion, modelName) in configs) {
            val met = tryCallWorkoutMetApi(apiVersion, modelName, exercise)
            if (met in 1.0..15.0) return@withContext met
        }

        0.0
    }

    private fun tryCallApi(apiVersion: String, modelName: String, title: String, description: String): Int {
        val url = "https://generativelanguage.googleapis.com/$apiVersion/models/$modelName:generateContent"
        val prompt = "Estimate total calories for exactly one meal from these fields:\n" +
            "meal_title: \"$title\"\n" +
            "portion_details: \"$description\"\n\n" +
            "Rules:\n" +
            "- Treat meal_title and portion_details as describing the same meal, not two separate meals.\n" +
            "- If portion_details lists quantities, use those quantities and do not count repeated title words again.\n" +
            "- Use meal_title only to identify ambiguous foods in portion_details, or to add foods that are not mentioned in portion_details.\n" +
            "- If portion_details is empty or vague, use meal_title with normal serving sizes.\n" +
            "- For Indian food, estimate realistic homemade portions unless restaurant/fried/large is stated.\n" +
            "Return only JSON in this exact shape: {\"total_calories\": integer}."

        val jsonRequest = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
            })
        }

        return try {
            val body = jsonRequest.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", API_KEY)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.w("GeminiClient", "HTTP ${response.code} from $modelName: $rawBody")
                    return 0
                }

                val root = JSONObject(rawBody)
                val candidates = root.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    Log.w("GeminiClient", "No candidates returned: $rawBody")
                    return 0
                }

                val text = candidates
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                val cleanJson = text.replace("```json", "").replace("```", "").trim()
                val calories = parseCalories(cleanJson)
                if (calories > 0) Log.d("GeminiClient", "Success: $calories kcal")
                calories
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Exception: ${e.message}")
            0
        }
    }

    private fun parseCalories(text: String): Int {
        return try {
            JSONObject(text).optInt("total_calories", 0)
        } catch (_: Exception) {
            Regex("""\d{2,5}""").find(text)?.value?.toIntOrNull() ?: 0
        }
    }

    private fun tryCallWorkoutMetApi(apiVersion: String, modelName: String, exercise: Exercise): Double {
        val url = "https://generativelanguage.googleapis.com/$apiVersion/models/$modelName:generateContent"
        val prompt = "Classify this workout and estimate a base MET value for calorie-burn calculation:\n" +
            "exercise_name: \"${exercise.name}\"\n" +
            "target_area: \"${exercise.targetArea}\"\n" +
            "sets: ${exercise.sets}\n" +
            "reps_or_duration: \"${exercise.reps}\"\n" +
            "user_intensity_percent: ${exercise.intensity}\n\n" +
            "Rules:\n" +
            "- Return the base MET for the exercise type at normal/moderate effort.\n" +
            "- Do not multiply by sets, reps, duration, body weight, or intensity.\n" +
            "- Use accepted Compendium-style MET ranges when possible.\n" +
            "- Keep MET between 1.0 and 15.0.\n" +
            "Return only JSON in this exact shape: {\"exercise_family\": string, \"base_met\": number}."

        val jsonRequest = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
            })
        }

        return try {
            val body = jsonRequest.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", API_KEY)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.w("GeminiClient", "Workout MET HTTP ${response.code} from $modelName: $rawBody")
                    return 0.0
                }

                val root = JSONObject(rawBody)
                val candidates = root.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) return 0.0

                val text = candidates
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                parseWorkoutMet(text.replace("```json", "").replace("```", "").trim())
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Workout MET exception: ${e.message}")
            0.0
        }
    }

    private fun parseWorkoutMet(text: String): Double {
        return try {
            JSONObject(text).optDouble("base_met", 0.0)
        } catch (_: Exception) {
            Regex("""\d+(?:\.\d+)?""").find(text)?.value?.toDoubleOrNull() ?: 0.0
        }
    }
}
