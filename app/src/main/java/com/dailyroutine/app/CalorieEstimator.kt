package com.dailyroutine.app

import android.content.Context
import com.google.gson.Gson
import java.util.Locale
import kotlin.math.max

fun MealNutritionInfo.hasAnyNutrition(): Boolean {
    return calories != null || carbsG != null || proteinG != null || fatG != null || fiberG != null ||
        fatBreakdown.isNotEmpty() || carbBreakdown.isNotEmpty() || vitamins.isNotEmpty() ||
        minerals.isNotEmpty() || aminoAcids.isNotEmpty() || antioxidants.isNotEmpty() || otherNutrients.isNotEmpty()
}

object CalorieEstimator {

    private const val PREFS_LEARNED_MEALS = "learned_meal_nutrition_store"
    private const val SIMILARITY_THRESHOLD = 0.95
    private const val LEARNED_KEY_PREFIX = "learned|v3|t="

    fun clearSmartCache(context: Context) {
        context.getSharedPreferences(PREFS_LEARNED_MEALS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun saveLearnedMeal(context: Context, title: String, description: String, nutrition: MealNutritionInfo) {
        if (!nutrition.hasAnyNutrition()) return
        val prefs = context.getSharedPreferences(PREFS_LEARNED_MEALS, Context.MODE_PRIVATE)
        val jsonStr = Gson().toJson(nutrition)
        val fullKey = buildNormalizedKey(title, description)
        val titleKey = buildNormalizedKey(title, "")

        val editor = prefs.edit().putString(fullKey, jsonStr)
        if (titleKey.isNotBlank() && titleKey != fullKey) {
            editor.putString(titleKey, jsonStr)
        }
        editor.apply()
    }

    fun getLearnedMealNutrition(context: Context, title: String, description: String): MealNutritionInfo? {
        val normTitle = normalize(title)
        val normDesc = normalize(description)
        if (normTitle.isBlank() && normDesc.isBlank()) return null

        val prefs = context.getSharedPreferences(PREFS_LEARNED_MEALS, Context.MODE_PRIVATE)
        val allEntries = prefs.all ?: return null

        var bestMatch: MealNutritionInfo? = null
        var highestScore = 0.0

        val targetCombined = buildCombinedQuery(normTitle, normDesc)

        for ((key, rawValue) in allEntries) {
            if (rawValue !is String) continue
            val (storedTitle, storedDesc) = extractTitleAndDescFromKey(key)
            val storedCombined = buildCombinedQuery(storedTitle, storedDesc)

            val score = calculateSimilarity(targetCombined, storedCombined)
            if (score >= SIMILARITY_THRESHOLD && score > highestScore) {
                try {
                    val parsed = Gson().fromJson(rawValue, MealNutritionInfo::class.java)
                    if (parsed != null && parsed.hasAnyNutrition()) {
                        highestScore = score
                        bestMatch = enrichMealNutrition(parsed)
                    } else if (parsed != null) {
                        prefs.edit().remove(key).apply()
                    }
                } catch (_: Exception) {}
            }
        }

        return bestMatch
    }

    fun getLearnedMealCalories(context: Context, title: String, description: String): Int? {
        return getLearnedMealNutrition(context, title, description)?.calories
    }

    fun enrichMealNutrition(info: MealNutritionInfo): MealNutritionInfo {
        // Keep the model output intact. Missing fields stay unavailable instead of being
        // synthesized from calories or generic macro ratios.
        return info
    }

    fun calculateSimilarity(s1: String, s2: String): Double {
        val norm1 = normalize(s1)
        val norm2 = normalize(s2)
        if (norm1 == norm2) return 1.0
        if (norm1.isBlank() || norm2.isBlank()) return 0.0

        val dist = levenshteinDistance(norm1, norm2)
        val maxLen = max(norm1.length, norm2.length)
        if (maxLen == 0) return 1.0
        return 1.0 - (dist.toDouble() / maxLen)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = i - 1
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                prev = temp
            }
        }
        return dp[s2.length]
    }

    private fun buildCombinedQuery(title: String, description: String): String {
        return listOf(title, description).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun buildNormalizedKey(title: String, description: String): String {
        val normTitle = normalize(title)
        val normDesc = normalize(description)
        return "$LEARNED_KEY_PREFIX$normTitle|d=$normDesc"
    }

    private fun extractTitleAndDescFromKey(key: String): Pair<String, String> {
        val prefix = LEARNED_KEY_PREFIX
        if (!key.startsWith(prefix)) return Pair("", "")
        val content = key.substring(prefix.length)
        val parts = content.split("|d=")
        val title = parts.getOrNull(0).orEmpty()
        val desc = parts.getOrNull(1).orEmpty()
        return Pair(title, desc)
    }

    fun normalize(value: String): String {
        return value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9. /-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
