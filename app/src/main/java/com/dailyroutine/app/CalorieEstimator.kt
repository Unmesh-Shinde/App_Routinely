package com.dailyroutine.app

import android.content.Context
import com.google.gson.Gson
import java.util.Locale
import kotlin.math.max

fun MealNutritionInfo.hasDetailedNutrition(): Boolean {
    return calories > 0 && carbsG > 0 && proteinG > 0 && fatG > 0
}

object CalorieEstimator {

    private const val PREFS_LEARNED_MEALS = "learned_meal_nutrition_store"
    private const val SIMILARITY_THRESHOLD = 0.95
    private const val LEARNED_KEY_PREFIX = "learned|v2|t="

    fun clearSmartCache(context: Context) {
        context.getSharedPreferences(PREFS_LEARNED_MEALS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun saveLearnedMeal(context: Context, title: String, description: String, nutrition: MealNutritionInfo) {
        if (nutrition.calories <= 0) return
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
                    if (parsed != null && parsed.hasDetailedNutrition()) {
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
        if (info.calories <= 0) return info
        val calories = info.calories
        val hasMacros = info.carbsG > 0 || info.proteinG > 0 || info.fatG > 0

        val carbs = if (hasMacros) info.carbsG else Math.round((calories * 0.50 / 4.0) * 10.0) / 10.0
        val protein = if (hasMacros) info.proteinG else Math.round((calories * 0.22 / 4.0) * 10.0) / 10.0
        val fat = if (hasMacros) info.fatG else Math.round((calories * 0.28 / 9.0) * 10.0) / 10.0
        val fiber = if (hasMacros) info.fiberG else Math.round((carbs * 0.12) * 10.0) / 10.0

        val netCarbsG = Math.round((carbs - fiber).coerceAtLeast(0.0) * 10.0) / 10.0
        val sugarsG = Math.round((carbs * 0.2) * 10.0) / 10.0
        val satFatG = Math.round((fat * 0.3) * 10.0) / 10.0
        val mufaG = Math.round((fat * 0.4) * 10.0) / 10.0
        val pufaG = Math.round((fat * 0.3) * 10.0) / 10.0

        val fatBreakdown = if (info.fatBreakdown.isEmpty()) {
            mapOf(
                "Saturated Fat" to "$satFatG g",
                "Monounsaturated Fat" to "$mufaG g",
                "Polyunsaturated Fat" to "$pufaG g",
                "Trans Fat" to "0 g",
                "Omega-6" to "${Math.round(pufaG * 0.8 * 10) / 10.0} g"
            )
        } else info.fatBreakdown

        val carbBreakdown = if (info.carbBreakdown.isEmpty()) {
            mapOf(
                "Net Carbs" to "$netCarbsG g",
                "Total Sugars" to "$sugarsG g",
                "Added Sugars" to "0 g",
                "Soluble Fiber" to "${Math.round(fiber * 0.4 * 10) / 10.0} g",
                "Insoluble Fiber" to "${Math.round(fiber * 0.6 * 10) / 10.0} g"
            )
        } else info.carbBreakdown

        // Micronutrients depend on the actual ingredients; never synthesize values from calories.
        val vitamins = info.vitamins
        val minerals = info.minerals

        val aminoAcids = if (info.aminoAcids.isEmpty()) {
            mapOf(
                "Leucine" to "${Math.round(protein * 0.08 * 10) / 10.0} g",
                "Isoleucine" to "${Math.round(protein * 0.04 * 10) / 10.0} g",
                "Valine" to "${Math.round(protein * 0.05 * 10) / 10.0} g",
                "Lysine" to "${Math.round(protein * 0.06 * 10) / 10.0} g",
                "Methionine" to "${Math.round(protein * 0.025 * 10) / 10.0} g",
                "Phenylalanine" to "${Math.round(protein * 0.045 * 10) / 10.0} g",
                "Threonine" to "${Math.round(protein * 0.035 * 10) / 10.0} g",
                "Tryptophan" to "${Math.round(protein * 0.012 * 10) / 10.0} g",
                "Histidine" to "${Math.round(protein * 0.025 * 10) / 10.0} g"
            )
        } else {
            val base = mapOf(
                "Leucine" to "${Math.round(protein * 0.08 * 10) / 10.0} g",
                "Isoleucine" to "${Math.round(protein * 0.04 * 10) / 10.0} g",
                "Valine" to "${Math.round(protein * 0.05 * 10) / 10.0} g",
                "Lysine" to "${Math.round(protein * 0.06 * 10) / 10.0} g",
                "Methionine" to "${Math.round(protein * 0.025 * 10) / 10.0} g",
                "Phenylalanine" to "${Math.round(protein * 0.045 * 10) / 10.0} g",
                "Threonine" to "${Math.round(protein * 0.035 * 10) / 10.0} g",
                "Tryptophan" to "${Math.round(protein * 0.012 * 10) / 10.0} g",
                "Histidine" to "${Math.round(protein * 0.025 * 10) / 10.0} g"
            )
            base + info.aminoAcids
        }

        val antioxidants = if (info.antioxidants.isEmpty()) {
            mapOf("Polyphenols" to "${Math.round(calories * 0.25).coerceIn(20, 500)} mg")
        } else info.antioxidants

        val otherNutrients = if (info.otherNutrients.isEmpty()) {
            mapOf(
                "Saturated Fat" to "$satFatG g",
                "Cholesterol" to "${Math.round(protein * 1.5)} mg"
            )
        } else info.otherNutrients

        return info.copy(
            carbsG = carbs,
            proteinG = protein,
            fatG = fat,
            fiberG = fiber,
            fatBreakdown = fatBreakdown,
            carbBreakdown = carbBreakdown,
            vitamins = vitamins,
            minerals = minerals,
            aminoAcids = aminoAcids,
            antioxidants = antioxidants,
            otherNutrients = otherNutrients
        )
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
