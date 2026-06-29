package com.dailyroutine.app

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
    // 🔑 Your Gemini API Key (AQ. format)
    private const val API_KEY = "AQ.Ab8RN6Lv8YtdcHcdGzW7oWoyRt4ubKM2dxTW5kGzhHyuO9tJkA"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * UNIVERSAL AI CONNECTOR
     * Tries multiple endpoints and models to guarantee a result with AQ. keys.
     */
    suspend fun getCaloriesForMeal(query: String): Int = withContext(Dispatchers.IO) {
        val configs = listOf(
            Pair("v1", "gemini-1.5-flash"),
            Pair("v1beta", "gemini-1.5-flash"),
            Pair("v1", "gemini-pro"),
            Pair("v1beta", "gemini-pro")
        )

        for (cfg in configs) {
            Log.d("GeminiClient", "🚀 Trying: ${cfg.first} / ${cfg.second}")
            val cals = tryCallApi(cfg.first, cfg.second, query)
            if (cals > 0) return@withContext cals
        }

        Log.e("GeminiClient", "❌ ALL MODELS FAILED. Ensure 'Generative Language API' is enabled in Cloud Console.")
        0
    }

    private fun tryCallApi(apiVersion: String, modelName: String, query: String): Int {
        val url = "https://generativelanguage.googleapis.com/$apiVersion/models/$modelName:generateContent"
        
        val prompt = "You are a nutrition expert. Estimate total calories for: '$query'. " +
                     "Return ONLY a JSON: {\"total_calories\": integer}. No units or other text."

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
            // Added explicit safety settings to prevent false-positive blocks on food names
            put("safetySettings", JSONArray().apply {
                listOf("HATE_SPEECH", "HARASSMENT", "SEXUALLY_EXPLICIT", "DANGEROUS_CONTENT").forEach { cat ->
                    put(JSONObject().apply {
                        put("category", cat)
                        put("threshold", "BLOCK_NONE")
                    })
                }
            })
        }

        return try {
            val body = jsonRequest.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", API_KEY)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val rawBody = response.body?.string() ?: ""
            
            if (!response.isSuccessful) {
                Log.w("GeminiClient", "Fail: $modelName ($apiVersion) | HTTP ${response.code} | Error: $rawBody")
                return 0
            }

            val root = JSONObject(rawBody)
            val text = root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text").trim()

            val cleanJson = text.replace("```json", "").replace("```", "").trim()
            val cals = JSONObject(cleanJson).optInt("total_calories", 0)
            if (cals > 0) Log.d("GeminiClient", "✅ Success! $cals kcal")
            cals
        } catch (e: Exception) {
            Log.e("GeminiClient", "Exception: ${e.message}")
            0
        }
    }
}
