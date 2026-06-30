package com.dailyroutine.app

import android.content.Context
import com.google.gson.Gson
import java.util.Locale
import kotlin.math.roundToInt

object CalorieEstimator {

    private const val ASSET_NAME = "indian_food_calories.json"

    private data class FoodDatabase(
        val version: Int = 1,
        val source: String = "",
        val entries: List<IndianFoodEntry> = emptyList()
    )

    private data class IndianFoodEntry(
        val name: String = "",
        val aliases: List<String> = emptyList(),
        val category: String = "",
        val subCategory: String = "",
        val dietaryType: String = "",
        val origin: String = "",
        val cookingMethod: String = "",
        val description: String = "",
        val serving: String = "1 serving",
        val servingGrams: Int = 0,
        val calories: Int = 0,
        val confidence: String = "estimated"
    )

    private data class FoodRule(
        val keywords: List<String>,
        val caloriesPerServing: Int,
        val gramsPerServing: Int = 100
    )

    private var cachedDatabase: FoodDatabase? = null

    private val basicFoods = listOf(
        FoodRule(listOf("roti", "chapati", "phulka"), 105, 45),
        FoodRule(listOf("naan"), 260, 100),
        FoodRule(listOf("paratha"), 260, 90),
        FoodRule(listOf("rice", "chawal"), 205, 160),
        FoodRule(listOf("biriyani", "biryani"), 420, 300),
        FoodRule(listOf("dal", "daal", "lentil"), 180, 200),
        FoodRule(listOf("sabzi", "sabji", "vegetable curry", "veg curry"), 160, 180),
        FoodRule(listOf("bhaji", "leafy vegetable", "saag", "amaranth bhaji"), 130, 150),
        FoodRule(listOf("paneer"), 265, 100),
        FoodRule(listOf("chicken curry"), 320, 250),
        FoodRule(listOf("chicken"), 240, 150),
        FoodRule(listOf("egg", "anda"), 78, 50),
        FoodRule(listOf("milk"), 150, 250),
        FoodRule(listOf("curd", "yogurt", "yoghurt", "dahi"), 120, 200),
        FoodRule(listOf("tea", "chai"), 90, 180)
    )

    fun estimateMealCalories(context: Context, text: String): Int {
        val normalized = normalize(text)
        if (normalized.isBlank()) return 0

        val databaseEstimate = estimateFromIndianDatabase(context, normalized)
        if (databaseEstimate > 0) return databaseEstimate

        return estimateMealCalories(text)
    }

    fun estimateMealCalories(context: Context, title: String, description: String): Int {
        val normalizedTitle = normalize(title)
        val normalizedDescription = normalize(description)
        if (normalizedTitle.isBlank() && normalizedDescription.isBlank()) return 0

        if (normalizedDescription.isNotBlank()) {
            val descriptionEstimate = estimateFromIndianDatabase(context, normalizedDescription)
            if (descriptionEstimate > 0) return descriptionEstimate

            val simpleDescriptionEstimate = estimateWithBasicFoods(normalizedDescription)
            if (simpleDescriptionEstimate > 0) return simpleDescriptionEstimate.roundToInt().coerceIn(40, 2500)
        }

        val combined = listOf(normalizedTitle, normalizedDescription)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return estimateMealCalories(context, combined)
    }

    fun estimateMealCalories(text: String): Int {
        val normalized = normalize(text)
        if (normalized.isBlank()) return 0

        val basicEstimate = estimateWithBasicFoods(normalized)
        if (basicEstimate > 0) return basicEstimate.roundToInt().coerceIn(40, 2500)

        return estimateGenericMeal(normalized).roundToInt().coerceIn(40, 2500)
    }

    private fun estimateWithBasicFoods(normalized: String): Double {
        var total = 0.0
        basicFoods.forEach { food ->
            val matchedKeyword = food.keywords.firstOrNull { normalized.containsWholePhrase(it) }
            if (matchedKeyword != null) {
                total += estimateFoodCalories(normalized, matchedKeyword, food)
            }
        }

        return total
    }

    private fun estimateFromIndianDatabase(context: Context, normalizedText: String): Int {
        val entries = loadDatabase(context).entries
        if (entries.isEmpty()) return 0

        val consumedRanges = mutableListOf<IntRange>()
        var total = 0.0

        entries
            .flatMap { entry ->
                (entry.aliases + entry.name)
                    .filter { it.length >= 3 }
                    .distinct()
                    .map { alias -> entry to normalize(alias) }
            }
            .sortedByDescending { it.second.length }
            .forEach { (entry, alias) ->
                val match = findWholePhraseMatch(normalizedText, alias) ?: return@forEach
                if (consumedRanges.any { it.overlaps(match.range) }) return@forEach

                total += entry.calories * quantityMultiplier(normalizedText, alias, match.range.first)
                consumedRanges.add(match.range)
            }

        return total.roundToInt().coerceIn(0, 3500)
    }

    private fun loadDatabase(context: Context): FoodDatabase {
        cachedDatabase?.let { return it }

        val loaded = try {
            context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                Gson().fromJson(reader, FoodDatabase::class.java) ?: FoodDatabase()
            }
        } catch (_: Exception) {
            FoodDatabase()
        }

        cachedDatabase = loaded
        return loaded
    }

    private fun quantityMultiplier(text: String, alias: String, aliasStart: Int): Double {
        val before = text.substring(0, aliasStart).takeLast(24)
        val after = text.substring((aliasStart + alias.length).coerceAtMost(text.length)).take(24)

        val pieceCountBefore = Regex("""(\d+(?:\.\d+)?)\s*$""").find(before)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (pieceCountBefore != null) return pieceCountBefore

        val pieceCountAfter = Regex("""^\s*(\d+(?:\.\d+)?)\b""").find(after)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (pieceCountAfter != null) return pieceCountAfter

        val gramsBefore = Regex("""(\d+(?:\.\d+)?)\s*(g|gram|grams)\s*$""").find(before)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (gramsBefore != null) return (gramsBefore / 100.0).coerceAtLeast(0.2)

        val halfWords = listOf("half", "1/2")
        if (halfWords.any { before.trim().endsWith(it) }) return 0.5

        return 1.0
    }

    private fun estimateFoodCalories(text: String, keyword: String, food: FoodRule): Double {
        val grams = findQuantityBeforeKeyword(text, keyword, "(g|gram|grams)") ?: findQuantityAfterKeyword(text, keyword, "(g|gram|grams)")
        if (grams != null) return food.caloriesPerServing * (grams / food.gramsPerServing)

        val cups = findQuantityBeforeKeyword(text, keyword, "(cup|cups|bowl|bowls)") ?: findQuantityAfterKeyword(text, keyword, "(cup|cups|bowl|bowls)")
        if (cups != null) return food.caloriesPerServing * cups

        val pieces = findPlainCountBeforeKeyword(text, keyword) ?: findPlainCountAfterKeyword(text, keyword)
        return food.caloriesPerServing * (pieces ?: 1.0)
    }

    private fun estimateGenericMeal(text: String): Double {
        val explicitCalories = Regex("""(\d{2,4})\s*(kcal|calories|cal)\b""").find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
        if (explicitCalories != null) return explicitCalories

        val heavyWords = listOf("fried", "burger", "pizza", "thali", "combo", "large")
        val lightWords = listOf("fruit", "salad", "soup", "plain", "small")
        return when {
            heavyWords.any { text.contains(it) } -> 650.0
            lightWords.any { text.contains(it) } -> 180.0
            else -> 350.0
        }
    }

    private fun normalize(value: String): String {
        return value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9. /-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.containsWholePhrase(phrase: String): Boolean {
        return findWholePhraseMatch(this, phrase) != null
    }

    private fun findWholePhraseMatch(text: String, phrase: String): MatchResult? {
        return Regex("""(^|\s)${Regex.escape(phrase)}(\s|$)""").find(text)
    }

    private fun IntRange.overlaps(other: IntRange): Boolean {
        return first <= other.last && other.first <= last
    }

    private fun findQuantityBeforeKeyword(text: String, keyword: String, unitPattern: String): Double? {
        val pattern = Regex("""(\d+(?:\.\d+)?)\s*$unitPattern\s+${Regex.escape(keyword)}""")
        return pattern.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun findQuantityAfterKeyword(text: String, keyword: String, unitPattern: String): Double? {
        val pattern = Regex("""${Regex.escape(keyword)}\s+(\d+(?:\.\d+)?)\s*$unitPattern""")
        return pattern.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun findPlainCountBeforeKeyword(text: String, keyword: String): Double? {
        val pattern = Regex("""(\d+(?:\.\d+)?)\s+${Regex.escape(keyword)}""")
        return pattern.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun findPlainCountAfterKeyword(text: String, keyword: String): Double? {
        val pattern = Regex("""${Regex.escape(keyword)}\s+(\d+(?:\.\d+)?)""")
        return pattern.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }
}
