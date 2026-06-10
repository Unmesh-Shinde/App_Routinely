package com.dailyroutine.app

import java.util.regex.Pattern

object CalorieSearchEngine {
    // Database values are per 100g for mass-based items, or per standard serving (1 piece) for others.
    private val foodDatabase = mapOf(
        // --- Indian Breads (Per Piece) ---
        "roti" to 85, "chapati" to 85, "phulka" to 70, "paratha" to 250, "naan" to 280,
        "butter naan" to 350, "garlic naan" to 330, "kulcha" to 210, "missi roti" to 160, 
        "poori" to 125, "bhatura" to 230, "rumali roti" to 130, "thepla" to 110,
        "jowar roti" to 90, "bajra roti" to 110, "makki roti" to 120, "lacha paratha" to 300,

        // --- Rice & Biryanis (Per 100g cooked) ---
        "rice" to 130, "cooked rice" to 130, "white rice" to 130, "brown rice" to 110,
        "pulao" to 150, "veg pulao" to 160, "biryani" to 200, "veg biryani" to 180,
        "chicken biryani" to 220, "mutton biryani" to 240, "egg biryani" to 190,
        "jeera rice" to 140, "khichdi" to 120, "lemon rice" to 150, "fried rice" to 180, 
        "curd rice" to 110, "basmati rice" to 130, "schezwan rice" to 190,

        // --- Dals & Pulses (Per 100g cooked) ---
        "dal" to 100, "dal tadka" to 120, "dal makhani" to 160, "chole" to 140,
        "rajma" to 140, "sambhar" to 80, "rasam" to 40, "moong dal" to 100,
        "lobia" to 110, "kadhi" to 90, "moong chilka" to 100, "masoor dal" to 100,
        "arhar dal" to 110, "urad dal" to 110, "chana dal" to 120,

        // --- Paneer & Veg Main Course (Per 100g) ---
        "paneer butter masala" to 240, "palak paneer" to 160, "matar paneer" to 180,
        "shahi paneer" to 220, "kadhai paneer" to 200, "paneer tikka" to 180,
        "aloo gobhi" to 120, "bhindi" to 100, "baingan bharta" to 110,
        "mixed veg" to 120, "malai kofta" to 250, "dum aloo" to 150, "gajar halwa" to 250,
        "aloo mattar" to 130, "jeera aloo" to 110, "veg korma" to 180, "mix veg curry" to 140,

        // --- Non-Veg Main Course (Per 100g) ---
        "chicken curry" to 180, "butter chicken" to 240, "chicken tikka masala" to 220,
        "fish curry" to 160, "fish fry" to 250, "mutton curry" to 250, "egg curry" to 150,
        "egg bhurji" to 180, "omelette" to 160, "boiled egg" to 75, "tandoori chicken" to 180,
        "chicken fry" to 280, "mutton rogan josh" to 280, "prawn curry" to 160,

        // --- Breakfast & Snacks (Per piece or 100g) ---
        "samosa" to 210, "pakora" to 160, "vada pav" to 310, "pav bhaji" to 150,
        "pani puri" to 40, "bhel puri" to 150, "aloo tikki" to 180, "dhokla" to 160,
        "idli" to 55, "dosa" to 130, "masala dosa" to 260, "medu vada" to 160,
        "poha" to 180, "upma" to 200, "maggi" to 310, "momos" to 40, "bread butter" to 200,
        "sandwich" to 250, "veg sandwich" to 230, "cheese sandwich" to 320, "burger" to 250, "chicken burger" to 300,

        // --- Fruits & Salads ---
        "apple" to 52, "banana" to 89, "orange" to 47, "mango" to 60,
        "papaya" to 43, "watermelon" to 30, "salad" to 30, "sprouts" to 125,
        "guava" to 68, "grapes" to 67, "pomegranate" to 83, "cucumber" to 15,

        // --- Beverages & Dairy ---
        "milk" to 60, "curd" to 65, "yogurt" to 65, "lassi" to 80,
        "chaas" to 30, "tea" to 30, "coffee" to 40, "juice" to 50,
        "buttermilk" to 35, "green tea" to 2, "coconut water" to 20,

        // --- Ingredients & Toppings ---
        "butter" to 717, "ghee" to 900, "oil" to 884, "cheese" to 400, "nuts" to 600,
        "sugar" to 387, "honey" to 304, "cream" to 340, "mayonnaise" to 680
    )

    private val portionMultipliers = mapOf(
        "bowl" to 1.5,
        "small bowl" to 0.8,
        "large bowl" to 2.2,
        "plate" to 2.0,
        "full plate" to 2.5,
        "half plate" to 1.0,
        "cup" to 1.0,
        "small cup" to 0.6,
        "large cup" to 1.5,
        "glass" to 1.2,
        "spoon" to 0.2,
        "tablespoon" to 0.3
    )

    fun getCalories(text: String): Int {
        val normalized = text.lowercase().trim()
        if (normalized.isEmpty()) return 0

        // Split into distinct meal components
        val sections = normalized.split(" and ", " with ", "&", ",", "+").filter { it.isNotBlank() }
        var totalSum = 0

        for (section in sections) {
            totalSum += analyzeSection(section)
        }

        // Final Sanity Check: If calculation results in impossible values for a single meal, cap it.
        // A single meal entry shouldn't realistically exceed 3500 kcal (a full day's worth).
        return totalSum.coerceAtMost(3500)
    }

    private fun analyzeSection(section: String): Int {
        var baseCals = 0
        var foundFoodKey = ""
        
        // Match Food Database - Try longest matches first
        val sortedKeys = foodDatabase.keys.sortedByDescending { it.length }
        for (food in sortedKeys) {
            if (section.contains(food)) {
                baseCals = foodDatabase[food] ?: 0
                foundFoodKey = food
                break
            }
        }

        if (foundFoodKey.isEmpty()) return 250 // Default fallback

        // Detection logic: Grams vs. Quantity
        val gramPattern = Pattern.compile("(\\d+)\\s*(gram|g|gm)")
        val gramMatcher = gramPattern.matcher(section)
        
        if (gramMatcher.find()) {
            // Logic for Mass (Grams): calories = (grams / 100) * baseCals (since DB is per 100g)
            val grams = gramMatcher.group(1)?.toDoubleOrNull() ?: 100.0
            // If the item is naturally piece-based (roti, apple) but user entered grams, we treat DB value as per 100g too.
            return (baseCals * (grams / 100.0)).toInt()
        }

        // Logic for Quantity (1, 2, 3...): calories = quantity * baseCals
        val numPattern = Pattern.compile("(\\d+)")
        val numMatcher = numPattern.matcher(section)
        var multiplier = 1.0
        if (numMatcher.find()) {
            multiplier = numMatcher.group(1)?.toDoubleOrNull() ?: 1.0
        }

        // Apply Portion Multipliers if any
        for ((portion, mult) in portionMultipliers) {
            if (section.contains(portion)) {
                multiplier *= mult
                break
            }
        }

        return (baseCals * multiplier).toInt()
    }
}
