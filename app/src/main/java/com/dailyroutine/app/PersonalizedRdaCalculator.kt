package com.dailyroutine.app

import android.content.Context
import kotlin.math.roundToInt

data class PersonalizedRda(
    val idealCalories: Int,
    val carbsG: Double,
    val proteinG: Double,
    val fatG: Double,
    val fiberG: Double,
    val vitamins: Map<String, Double>,
    val minerals: Map<String, Double>,
    val aminoAcids: Map<String, Double>,
    val otherNutrients: Map<String, Double>
) {
    fun getTargetForNutrient(nutrientName: String): Double? {
        val key = nutrientName.lowercase().trim()
        val directMacro = when (key) {
            "carbohydrates", "carbs" -> carbsG
            "protein" -> proteinG
            "fat" -> fatG
            "dietary fiber", "fiber" -> fiberG
            else -> null
        }
        if (directMacro != null) return directMacro

        return vitamins[key] ?: minerals[key] ?: aminoAcids[key] ?: otherNutrients[key]
    }

    fun calculateRdaPercentage(nutrientName: String, valueStr: String): Pair<Int, String?> {
        val target = getTargetForNutrient(nutrientName) ?: return Pair(0, null)
        if (target <= 0.0) return Pair(0, null)

        val match = Regex("""(\d+(?:\.\d+)?)""").find(valueStr)
        val num = match?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return Pair(0, null)

        if (num <= 0.0) {
            return Pair(0, "0% Goal")
        }

        val pct = ((num / target) * 100).roundToInt().coerceIn(1, 100)
        val label = "$pct% Goal"
        return Pair(pct, label)
    }
}

object PersonalizedRdaCalculator {

    fun calculate(context: Context): PersonalizedRda {
        val metrics = ProfileHealthMetricsCalculator.calculate(context)
        val age = UserPreferencesStore.getUserAge(context)
        val weight = UserPreferencesStore.getUserWeight(context).let { if (it > 0) it else 70.0 }
        val gender = UserPreferencesStore.getUserGender(context).lowercase()

        val tdee = metrics?.idealCalories ?: 2000
        val isFemaleUnder50 = gender == "female" && (age in 1..49)
        val isAge50Plus = age >= 50

        val carbsG = Math.round((tdee * 0.50 / 4.0) * 10.0) / 10.0
        val proteinG = Math.round((weight * 1.2).coerceIn(45.0, 180.0) * 10.0) / 10.0
        val fatG = Math.round((tdee * 0.28 / 9.0) * 10.0) / 10.0
        val fiberG = Math.round(((tdee / 1000.0) * 14.0) * 10.0) / 10.0

        val vitamins = mapOf(
            "vitamin a" to 900.0,
            "vitamin c" to 90.0,
            "vitamin d" to (if (isAge50Plus) 20.0 else 15.0),
            "vitamin b12" to 2.4,
            "vitamin b6" to (if (isAge50Plus) 1.7 else 1.3),
            "folate" to 400.0,
            "folic acid" to 400.0,
            "choline" to (if (gender == "female") 425.0 else 550.0),
            "vitamin k2" to 120.0
        )

        val minerals = mapOf(
            "calcium" to (if (isAge50Plus) 1200.0 else 1000.0),
            "iron" to (if (isFemaleUnder50) 29.0 else 19.0),
            "potassium" to (tdee * 1.6),
            "sodium" to 2000.0,
            "zinc" to (if (gender == "male") 12.0 else 9.0),
            "magnesium" to (if (gender == "male") 340.0 else 310.0),
            "selenium" to 55.0,
            "phosphorus" to 700.0
        )

        val aminoAcids = mapOf(
            "leucine" to Math.round((proteinG * 0.08) * 10.0) / 10.0,
            "isoleucine" to Math.round((proteinG * 0.04) * 10.0) / 10.0,
            "valine" to Math.round((proteinG * 0.05) * 10.0) / 10.0,
            "lysine" to Math.round((proteinG * 0.06) * 10.0) / 10.0,
            "methionine" to Math.round((proteinG * 0.025) * 10.0) / 10.0,
            "phenylalanine" to Math.round((proteinG * 0.045) * 10.0) / 10.0,
            "threonine" to Math.round((proteinG * 0.035) * 10.0) / 10.0,
            "tryptophan" to Math.round((proteinG * 0.012) * 10.0) / 10.0,
            "histidine" to Math.round((proteinG * 0.025) * 10.0) / 10.0
        )

        val otherNutrients = mapOf(
            "saturated fat" to Math.round((fatG * 0.3) * 10.0) / 10.0,
            "monounsaturated fat" to Math.round((fatG * 0.4) * 10.0) / 10.0,
            "polyunsaturated fat" to Math.round((fatG * 0.3) * 10.0) / 10.0,
            "net carbs" to Math.round((carbsG - fiberG).coerceAtLeast(0.0) * 10.0) / 10.0,
            "total sugars" to 30.0,
            "added sugars" to 25.0,
            "cholesterol" to 300.0,
            "omega-3" to 1.6,
            "omega-6" to 14.0,
            "polyphenols" to 500.0,
            "lycopene" to 10000.0,
            "lutein & zeaxanthin" to 10000.0,
            "beta-carotene" to 3000.0,
            "sulforaphane" to 15.0
        )

        return PersonalizedRda(
            idealCalories = tdee,
            carbsG = carbsG,
            proteinG = proteinG,
            fatG = fatG,
            fiberG = fiberG,
            vitamins = vitamins,
            minerals = minerals,
            aminoAcids = aminoAcids,
            otherNutrients = otherNutrients
        )
    }
}
