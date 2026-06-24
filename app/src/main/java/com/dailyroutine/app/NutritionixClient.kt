package com.dailyroutine.app

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object NutritionixClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // 🔑 FREE API KEYS (Developer Sandbox)
    private const val APP_ID = "6190be99"
    private const val APP_KEY = "f7c9e0d9b4b7c6c4c0b4f8d4d9b4b7c6" // Note: This is a placeholder for implementation

    fun getCaloriesForMeal(query: String, callback: (Int) -> Unit) {
        val url = "https://trackapi.nutritionix.com/v2/natural/nutrients"
        
        val json = JSONObject().apply {
            put("query", query)
        }
        
        val request = Request.Builder()
            .url(url)
            .addHeader("x-app-id", APP_ID)
            .addHeader("x-app-key", APP_KEY)
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Nutritionix", "Network Fail", e)
                callback(0)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val jsonResponse = JSONObject(body)
                        val foods = jsonResponse.getJSONArray("foods")
                        var totalCals = 0.0
                        for (i in 0 until foods.length()) {
                            totalCals += foods.getJSONObject(i).getDouble("nf_calories")
                        }
                        callback(totalCals.toInt())
                    } catch (e: Exception) {
                        Log.e("Nutritionix", "Parse Fail", e)
                        callback(0)
                    }
                } else {
                    Log.e("Nutritionix", "API Error: ${response.code}")
                    callback(0)
                }
            }
        })
    }
}
