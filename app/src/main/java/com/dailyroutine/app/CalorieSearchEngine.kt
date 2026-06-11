package com.dailyroutine.app

import java.util.regex.Pattern

object CalorieSearchEngine {
    // DB: cals per 100g/ml or per 1 standard piece.
    private val foodDatabase = mapOf(
        "whole wheat bread" to 75, "white bread" to 80, "brown bread" to 75,
        "roti" to 85, "chapati" to 85, "phulka" to 70, "paratha" to 250, "naan" to 280, "sandwich" to 250,
        "rice" to 130, "biryani" to 200, "dal" to 100, "paneer butter masala" to 240,
        "chicken curry" to 180, "egg curry" to 150, "salad" to 30, "vegetable salad" to 35,
        "apple" to 52, "banana" to 89, "orange" to 47, "mango" to 60,
        "tomato" to 18, "onion" to 40, "cabbage" to 25, "cucumber" to 15,
        "milk" to 60, "buttermilk" to 30, "curd" to 65, "yogurt" to 65, "lassi" to 80,
        "butter" to 717, "ghee" to 900, "oil" to 884, "olive oil" to 884, "cheese slice" to 70,
        "cheese" to 400, "mayonnaise" to 680, "mayo" to 680, "tomato sauce" to 100,
        "mustard dressing" to 450, "mustard" to 60, "almond" to 7, "cashew" to 9, 
        "pistachio" to 4, "peanut" to 6, "roasted peanuts" to 160 // per handful
    )

    // Ingredients that should use a tiny default portion (15g/ml) if no quantity is found
    private val smallIngredients = listOf(
        "butter", "ghee", "oil", "mayo", "sauce", "ketchup", "mustard", "honey", "cream", 
        "onion", "tomato", "cucumber", "dressing", "dressing", "cheese"
    )

    private val unitMultipliers = mapOf(
        "teaspoon" to 0.05, "tsp" to 0.05,
        "tablespoon" to 0.15, "tbsp" to 0.15,
        "handful" to 0.3, "bowl" to 1.5,
        "plate" to 2.0, "glass" to 2.5, "cup" to 1.5, "slice" to 1.0,
        "gm" to 0.01, "gram" to 0.01, "ml" to 0.01
    )

    fun getCalories(text: String): Int {
        val normalized = text.lowercase().replace("probably around", "").replace("fillings of", "").trim()
        if (normalized.isEmpty()) return 0

        // Handle common typos or slang
        val sanitized = normalized.replace("raosted", "roasted").replace("pieces", "").replace("portion", "")

        val segments = sanitized.split(
            " and ", " with ", " including ", " plus ", " along with ", 
            ",", ";", "&", "+", " side of "
        ).filter { it.isNotBlank() }

        var total = 0.0
        var globalMultiplier = 1.0

        for (segment in segments) {
            val res = analyzeSegment(segment)
            
            // Quantity Context
            if (segment.contains(" each")) {
                total += (res.calories * globalMultiplier)
            } else {
                total += res.calories
                if (res.isPrimary && res.quantity > 1) {
                    globalMultiplier = res.quantity
                }
            }
        }

        // Realistic Cap for a single meal
        return total.toInt().coerceAtMost(2500)
    }

    data class ParseResult(val calories: Double, val quantity: Double, val isPrimary: Boolean)

    private fun analyzeSegment(segment: String): ParseResult {
        var foundBase = 0.0
        var isPrimary = false
        
        // Match Food
        var foodKey = ""
        val sortedKeys = foodDatabase.keys.sortedByDescending { it.length }
        for (key in sortedKeys) {
            if (segment.contains(key)) {
                foundBase = foodDatabase[key]?.toDouble() ?: 0.0
                foodKey = key
                if (key.contains("sandwich") || key.contains("bread") || key.contains("roti") || 
                    key.contains("dosa") || key.contains("burger") || key.contains("biryani")) {
                    isPrimary = true
                }
                break
            }
        }

        if (foodKey.isEmpty()) return ParseResult(0.0, 1.0, false)

        // Extract Number
        val numPattern = Pattern.compile("(\\d+(\\.\\d+)?)")
        val matcher = numPattern.matcher(segment)
        var foundNumber: Double? = null
        if (matcher.find()) {
            foundNumber = matcher.group(1)?.toDoubleOrNull()
        }

        // Extract Unit
        var unitMult = 1.0
        var foundUnit = false
        for ((unit, mult) in unitMultipliers) {
            if (segment.contains(unit)) {
                unitMult = mult
                foundUnit = true
                // Priority: if user specified ml or gram, ignore container multipliers (glass/bowl)
                if (unit == "ml" || unit == "gram" || unit == "gm") break 
            }
        }

        // Logic: How to calculate the multiplier?
        var finalMultiplier = 1.0
        
        if (foundNumber != null && foundUnit) {
            // e.g. "300 ml" -> multiplier = 300 * 0.01 = 3.0
            finalMultiplier = foundNumber * unitMult
        } else if (foundNumber != null) {
            // e.g. "2 roti" -> multiplier = 2.0
            finalMultiplier = foundNumber
        } else if (foundUnit) {
            // e.g. "bowl of dal" -> multiplier = unit default (1.5)
            finalMultiplier = unitMult
        } else {
            // NO QUANTITY AND NO UNIT FOUND
            // Check if it's a small ingredient (should be a tiny serving)
            val isSmall = smallIngredients.any { foodKey.contains(it) }
            if (isSmall) {
                finalMultiplier = 0.15 // Default to 15g/ml serving
            } else {
                finalMultiplier = 1.0 // Default to 100g or 1 piece
            }
        }

        return ParseResult(foundBase * finalMultiplier, foundNumber ?: 1.0, isPrimary)
    }
}
