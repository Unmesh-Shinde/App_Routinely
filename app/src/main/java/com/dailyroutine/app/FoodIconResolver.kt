package com.dailyroutine.app

object FoodIconResolver {

    /**
     * Resolves up to 3 icon drawable resource IDs for multi-item meals (2-in-1 or 3-in-1 combo).
     * If a single predefined combo dish or dominant item (>= 60% portion weight) exists,
     * returns a single-item list.
     */
    fun getIconsForMeal(title: String, description: String = "", mealType: String = ""): List<Int> {
        val fullText = "${title.trim()} ${description.trim()}".trim()
        if (fullText.isEmpty()) return listOf(RoutineIconMapper.iconForMealType(mealType))

        val singleIcon = getIconForMeal(title, description, mealType)
        val q = fullText.lowercase()

        // Check if singleIcon matched a predefined combo dish or single dish without conjunctions
        val isPredefinedCombo = containsAny(q,
            "dal rice", "varan bhat", "dal chawal", "puri bhaji", "poori bhaji",
            "chole bhature", "idli sambar", "vada sambar", "medu vada", "appam stew",
            "rajma chawal", "fafda jalebi", "sabudana khichdi", "sabudana vada",
            "kadhi pakora", "wada pav", "vada pav", "pav bhaji", "misal pav"
        )
        if (isPredefinedCombo) return listOf(singleIcon)

        // Split by logical conjunctions / clause delimiters
        val delimiters = Regex("[,+&;]|\\b(and|with|w/|along with|served with)\\b|\\n", RegexOption.IGNORE_CASE)
        val rawClauses = fullText.split(delimiters)
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 1 }

        if (rawClauses.size <= 1) {
            return listOf(singleIcon)
        }

        // Score each clause by quantity / portion signals
        val scoredIcons = mutableMapOf<Int, Double>()
        val defaultSlotIcon = RoutineIconMapper.iconForMealType(mealType)

        for (clause in rawClauses) {
            val clauseLower = clause.lowercase()
            val icon = getIconForMeal(clause, "", mealType)
            if (icon == defaultSlotIcon && rawClauses.size > 1) {
                // Ignore empty generic fallback match when other specific items exist
                continue
            }

            var weight = 100.0

            // Grams / Milliliters parsing (e.g. 500g, 250ml)
            val gMlMatch = Regex("(\\d+)\\s*(g|gm|gram|grams|ml|liter|l)\\b", RegexOption.IGNORE_CASE).find(clauseLower)
            if (gMlMatch != null) {
                val valNum = gMlMatch.groupValues[1].toDoubleOrNull() ?: 100.0
                weight = valNum.coerceIn(20.0, 1000.0)
            } else {
                // Quantity multiplier parsing (e.g. 2, 3, 4 rotis)
                val countMatch = Regex("\\b(\\d+)\\b").find(clauseLower)
                if (countMatch != null) {
                    val count = countMatch.groupValues[1].toDoubleOrNull() ?: 1.0
                    weight = (count * 100.0).coerceIn(50.0, 600.0)
                }
            }

            // Keyword prominence adjustments
            if (containsAny(clauseLower, "large", "extra", "main", "full", "double", "triple")) {
                weight *= 1.5
            }
            if (containsAny(clauseLower, "small", "side", "spoon", "tbsp", "tsp", "pinch", "chutney", "pickle", "raita")) {
                weight *= 0.2
            }

            scoredIcons[icon] = (scoredIcons[icon] ?: 0.0) + weight
        }

        if (scoredIcons.isEmpty()) {
            return listOf(singleIcon)
        }

        val totalWeight = scoredIcons.values.sum()
        val sortedList = scoredIcons.entries.sortedByDescending { it.value }

        // 60% Dominance threshold check
        val topEntry = sortedList.first()
        if (topEntry.value / totalWeight >= 0.60) {
            return listOf(topEntry.key)
        }

        // Return top 2 or top 3 icons
        val maxCount = 3
        return sortedList.take(maxCount).map { it.key }
    }

    /**
     * Resolves an icon drawable resource ID using a 3-tier fallback strategy:
     * 1. Tier 1: Exact / Specific Individual Dish Match (105+ icons)
     * 2. Tier 2: Culinary Category Fallback Match (30+ category icons)
     * 3. Tier 3: Time-of-Day / Meal Slot Default (Breakfast/Lunch/Dinner/Snack)
     */
    fun getIconForMeal(title: String, description: String = "", mealType: String = ""): Int {
        val q = "${title.lowercase()} ${description.lowercase()}".trim()
        if (q.isEmpty()) return RoutineIconMapper.iconForMealType(mealType)

        // ====================================================================
        // TIER 1: Individual Dish Specific Matches
        // ====================================================================

        // Biryani & Rice Specialties
        if (containsAny(q, "veg biryani", "vegetable biryani")) return R.drawable.ic_food_veg_biryani
        if (containsAny(q, "chicken biryani", "mutton biryani", "prawn biryani", "prawns biryani", "egg biryani", "biryani")) return R.drawable.ic_food_nonveg_biryani
        if (containsAny(q, "veg fried rice", "vegetable fried rice")) return R.drawable.ic_food_veg_fried_rice
        if (containsAny(q, "chicken fried rice", "prawn fried rice", "egg fried rice", "non veg fried rice", "fried rice")) return R.drawable.ic_food_nonveg_fried_rice
        if (containsAny(q, "sabudana khichdi")) return R.drawable.ic_food_sabudana_khichdi
        if (containsAny(q, "sabudana vada", "sabudana wada")) return R.drawable.ic_food_sabudana_vada
        if (containsAny(q, "dal khichdi", "khichdi", "khichuri")) return R.drawable.ic_food_dal_khichdi
        if (containsAny(q, "dal rice", "varan bhat", "dal chawal")) return R.drawable.ic_food_dal_rice
        if (containsAny(q, "curd rice", "tayir sadam", "dahi chawal")) return R.drawable.ic_food_curd_rice
        if (containsAny(q, "jeera rice")) return R.drawable.ic_food_jeera_rice
        if (containsAny(q, "lemon rice")) return R.drawable.ic_food_lemon_rice
        if (containsAny(q, "rajma chawal", "rajma rice", "rajma")) return R.drawable.ic_food_rajma_chawal

        // Dairy, Ghee & Oils
        if (containsAny(q, "ghee", "desi ghee", "cow ghee", "buffalo ghee", "clarified butter")) return R.drawable.ic_food_ghee

        // Flatbreads, Dosas & Tandoori Breads
        if (containsAny(q, "butter naan", "garlic naan", "naan", "tandoori roti")) return R.drawable.ic_food_butter_naan
        if (containsAny(q, "paratha", "aloo paratha", "paneer paratha", "gobi paratha", "laccha paratha")) return R.drawable.ic_food_paratha
        if (containsAny(q, "dosa", "masala dosa", "plain dosa", "rava dosa", "paper dosa")) return R.drawable.ic_food_dosa
        if (containsAny(q, "idli", "idli sambar", "button idli")) return R.drawable.ic_food_idli_sambar
        if (containsAny(q, "medu wada", "vada sambar", "medu vada")) return R.drawable.ic_food_medu_wada
        if (containsAny(q, "uttapam", "onion uttapam")) return R.drawable.ic_food_uttapam
        if (containsAny(q, "appam", "appam stew")) return R.drawable.ic_food_appam_stew
        if (containsAny(q, "puri bhaji", "poori bhaji", "poori masalu")) return R.drawable.ic_food_puri_bhaji
        if (containsAny(q, "chole bhature", "bhatura", "bhature")) return R.drawable.ic_food_chole_bhature
        if (containsAny(q, "kathi roll", "frankie", "egg roll", "chicken roll", "paneer roll")) return R.drawable.ic_food_kathi_roll
        if (containsAny(q, "dal chapati", "roti bhaji", "chapati", "chapatis", "phulka", "fulka", "bhakri", "roti", "rotis")) return R.drawable.ic_food_dal_chapati

        // Street Food, Chaat & Fast Food
        if (containsAny(q, "wada pav", "vada pav", "batata vada")) return R.drawable.ic_food_wada_pav
        if (containsAny(q, "pav bhaji", "pavbhaji")) return R.drawable.ic_food_pav_bhaji
        if (containsAny(q, "misal pav", "misal")) return R.drawable.ic_food_misal_pav
        if (containsAny(q, "sev puri", "sev poori")) return R.drawable.ic_food_sev_puri
        if (containsAny(q, "dahi puri", "dahi poori", "dahi batata puri")) return R.drawable.ic_food_dahi_puri
        if (containsAny(q, "pani puri", "golgappa", "puchka")) return R.drawable.ic_food_pani_puri
        if (containsAny(q, "bhel", "bhel puri", "sukha bhel")) return R.drawable.ic_food_bhel
        if (containsAny(q, "samosa", "samosa chaat")) return R.drawable.ic_food_samosa
        if (containsAny(q, "kachori", "pyaz kachori")) return R.drawable.ic_food_kachori
        if (containsAny(q, "dhokla", "khaman")) return R.drawable.ic_food_dhokla
        if (containsAny(q, "fafda", "fafda jalebi")) return R.drawable.ic_food_fafda
        if (containsAny(q, "momo", "momos", "dimsum")) return R.drawable.ic_food_momos
        if (containsAny(q, "shawarma")) return R.drawable.ic_food_shawarma
        if (containsAny(q, "burger", "cheeseburger")) return R.drawable.ic_food_burger
        if (containsAny(q, "sandwich", "bread toast", "grilled sandwich")) return R.drawable.ic_food_bread_sandwich
        if (containsAny(q, "pizza")) return R.drawable.ic_food_pizza
        if (containsAny(q, "veg taco", "vegetable taco")) return R.drawable.ic_food_veg_taco
        if (containsAny(q, "chicken taco", "taco")) return R.drawable.ic_food_chicken_taco
        if (containsAny(q, "french fries", "fries", "potato fries")) return R.drawable.ic_food_french_fries
        if (containsAny(q, "veg puff", "chicken puff", "egg puff", "puff", "patties")) return R.drawable.ic_food_veg_puff
        if (containsAny(q, "garlic bread")) return R.drawable.ic_food_garlic_bread
        if (containsAny(q, "waffle", "waffles", "pancake", "pancakes")) return R.drawable.ic_food_waffles_pancakes

        // Chinese & Noodles
        if (containsAny(q, "chowmein", "chow mein", "hakka noodles", "noodle", "noodles")) return R.drawable.ic_food_chowmein
        if (containsAny(q, "manchurian", "veg manchurian", "chicken manchurian")) return R.drawable.ic_food_manchurian
        if (containsAny(q, "ramen")) return R.drawable.ic_food_noodle_ramen

        // Non-Veg Starters, Fry & Tandoori
        if (containsAny(q, "tandoori chicken", "afghani chicken")) return R.drawable.ic_food_tandoori_chicken
        if (containsAny(q, "chicken tikka", "reshmi tikka", "malai tikka")) return R.drawable.ic_food_chicken_tikka
        if (containsAny(q, "chicken fry", "fried chicken", "kfc chicken")) return R.drawable.ic_food_chicken_fry
        if (containsAny(q, "chicken curry", "chicken gravy", "chicken rassa")) return R.drawable.ic_food_chicken_curry
        if (containsAny(q, "mutton curry", "mutton gravy", "mutton rassa", "mutton kolhapuri")) return R.drawable.ic_food_mutton_curry
        if (containsAny(q, "mutton roast", "mutton sukka")) return R.drawable.ic_food_mutton_roast
        if (containsAny(q, "fish fry", "fried fish", "surmai fry", "pomfret fry")) return R.drawable.ic_food_fish_fry
        if (containsAny(q, "fish curry", "fish gravy", "fish rassa")) return R.drawable.ic_food_fish_curry
        if (containsAny(q, "tandoori prawns", "prawns fry")) return R.drawable.ic_food_tandoori_prawns
        if (containsAny(q, "prawns curry", "prawns gravy", "prawn curry")) return R.drawable.ic_food_prawns_curry
        if (containsAny(q, "seekh kebab", "mutton seekh", "chicken seekh")) return R.drawable.ic_food_seekh_kebab
        if (containsAny(q, "shami kebab", "shami kabab")) return R.drawable.ic_food_shami_kebab
        if (containsAny(q, "grilled chicken", "roast chicken")) return R.drawable.ic_food_grilled_chicken

        // Regional Curries & Sabji Specialties
        if (containsAny(q, "paneer tikka")) return R.drawable.ic_food_paneer_tikka
        if (containsAny(q, "kadhai paneer", "kadai paneer")) return R.drawable.ic_food_kadhai_paneer
        if (containsAny(q, "sarson saag", "sarson ka saag", "saag")) return R.drawable.ic_food_sarson_saag
        if (containsAny(q, "kadhi pakora", "kadhi pakoda", "punjabi kadhi")) return R.drawable.ic_food_kadhi_pakora
        if (containsAny(q, "baingan bharta", "eggplant bharta")) return R.drawable.ic_food_baingan_bharta

        // Fasting / Vrat / Gym & Health Foods
        if (containsAny(q, "sabudana khichdi")) return R.drawable.ic_food_sabudana_khichdi
        if (containsAny(q, "sabudana vada", "sabudana wada")) return R.drawable.ic_food_sabudana_vada
        if (containsAny(q, "makhana", "fox nuts")) return R.drawable.ic_food_makhana
        if (containsAny(q, "protein shake", "whey protein", "shaker")) return R.drawable.ic_food_protein_shake
        if (containsAny(q, "sprouts salad", "moong salad")) return R.drawable.ic_food_sprouts_salad
        if (containsAny(q, "oatmeal", "oats bowl", "oats")) return R.drawable.ic_food_oatmeal_bowl
        if (containsAny(q, "poha", "kanda poha")) return R.drawable.ic_food_poha
        if (containsAny(q, "upma", "rava upma")) return R.drawable.ic_food_upma

        // Egg Meals
        if (containsAny(q, "egg omelette", "omelette", "omlet")) return R.drawable.ic_food_egg_omelette
        if (containsAny(q, "boiled egg", "boiled eggs")) return R.drawable.ic_food_boiled_egg
        if (containsAny(q, "egg bhurji", "scrambled egg", "bhurji")) return R.drawable.ic_food_egg_bhurji

        // Crunchy Snacks & Savories
        if (containsAny(q, "bhaje", "pakora", "pakoda", "onion bhaji", "mirchi bhaji")) return R.drawable.ic_food_bhaje
        if (containsAny(q, "sev", "nylon sev")) return R.drawable.ic_food_sev
        if (containsAny(q, "chakli", "murukku")) return R.drawable.ic_food_chakli
        if (containsAny(q, "murmure", "kurmura", "puffed rice")) return R.drawable.ic_food_murmure
        if (containsAny(q, "popcorn")) return R.drawable.ic_food_popcorn
        if (containsAny(q, "potato chips", "chips", "wafers")) return R.drawable.ic_food_chips
        if (containsAny(q, "kurkure")) return R.drawable.ic_food_kurkure

        // Sweets, Mithai & Desserts
        if (containsAny(q, "gulab jamun")) return R.drawable.ic_food_gulab_jamun
        if (containsAny(q, "jalebi")) return R.drawable.ic_food_jalebi
        if (containsAny(q, "rasgulla", "rasmalai")) return R.drawable.ic_food_rasgulla
        if (containsAny(q, "kheer", "payasam")) return R.drawable.ic_food_kheer
        if (containsAny(q, "kaju katli", "kaju barfi")) return R.drawable.ic_food_kaju_katli
        if (containsAny(q, "gajar halwa", "gajar ka halwa")) return R.drawable.ic_food_gajar_halwa
        if (containsAny(q, "laddoo", "ladoo", "motichoor")) return R.drawable.ic_food_laddoo
        if (containsAny(q, "kulfi", "matka kulfi")) return R.drawable.ic_food_kulfi
        if (containsAny(q, "shrikhand", "amrakhand")) return R.drawable.ic_food_shrikhand
        if (containsAny(q, "mishti doi", "sandesh")) return R.drawable.ic_food_mishti_doi
        if (containsAny(q, "ghevar", "imarti")) return R.drawable.ic_food_ghevar
        if (containsAny(q, "peda", "barfi")) return R.drawable.ic_food_peda
        if (containsAny(q, "ice cream", "icecream")) return R.drawable.ic_food_ice_cream
        if (containsAny(q, "chocolate bar", "chocolate", "cadbury")) return R.drawable.ic_food_chocolate_bar

        // Beverages, Dairy & Alcohol
        if (containsAny(q, "falooda")) return R.drawable.ic_food_falooda
        if (containsAny(q, "badam milk", "almond milk")) return R.drawable.ic_food_badam_milk
        if (containsAny(q, "jaljeera", "masala soda")) return R.drawable.ic_food_jaljeera
        if (containsAny(q, "thandai")) return R.drawable.ic_food_thandai
        if (containsAny(q, "sugarcane juice", "ganne ka ras")) return R.drawable.ic_food_sugarcane_juice
        if (containsAny(q, "sol kadi", "solkadi")) return R.drawable.ic_food_sol_kadi
        if (containsAny(q, "lassi", "sweet lassi", "mango lassi")) return R.drawable.ic_food_lassi
        if (containsAny(q, "buttermilk", "taak", "chaas")) return R.drawable.ic_food_buttermilk
        if (containsAny(q, "limbu pani", "lemonade", "nimbu pani")) return R.drawable.ic_food_limbu_pani
        if (containsAny(q, "black coffee", "black tea")) return R.drawable.ic_food_black_coffee
        if (containsAny(q, "green tea", "green coffee")) return R.drawable.ic_food_green_tea
        if (containsAny(q, "coffee", "cappuccino", "espresso", "latte")) return R.drawable.ic_food_coffee
        if (containsAny(q, "tea", "chai", "masala chai")) return R.drawable.ic_food_tea
        if (containsAny(q, "milk", "warm milk")) return R.drawable.ic_food_milk
        if (containsAny(q, "cold drink", "coca cola", "pepsi", "sprite", "soda")) return R.drawable.ic_food_cold_drink
        if (containsAny(q, "whiskey", "whisky", "bourbon")) return R.drawable.ic_food_whiskey
        if (containsAny(q, "vodka")) return R.drawable.ic_food_vodka
        if (containsAny(q, "red rum", "dark rum", "rum")) return R.drawable.ic_food_red_rum
        if (containsAny(q, "white rum", "mojito")) return R.drawable.ic_food_white_rum
        if (containsAny(q, "beer", "draught beer")) return R.drawable.ic_food_beer
        if (containsAny(q, "red wine", "wine")) return R.drawable.ic_food_red_wine
        if (containsAny(q, "champagne", "white wine")) return R.drawable.ic_food_champagne
        if (containsAny(q, "curd", "dahi", "yogurt")) return R.drawable.ic_food_curd
        if (containsAny(q, "ghee", "desi ghee", "cow ghee", "buffalo ghee", "clarified butter")) return R.drawable.ic_food_ghee

        // Dry Fruits & Nuts
        if (containsAny(q, "almond", "almonds", "badam")) return R.drawable.ic_food_almonds
        if (containsAny(q, "walnut", "walnuts", "akhrot")) return R.drawable.ic_food_walnuts
        if (containsAny(q, "cashew", "cashews", "kaju")) return R.drawable.ic_food_cashews
        if (containsAny(q, "pistachio", "pistachios", "pista")) return R.drawable.ic_food_pistachios
        if (containsAny(q, "anjeer", "fig", "figs")) return R.drawable.ic_food_anjeer
        if (containsAny(q, "raisin", "raisins", "kishmish")) return R.drawable.ic_food_raisins
        if (containsAny(q, "seeds", "pumpkin seeds", "watermelon seeds")) return R.drawable.ic_food_seeds
        if (containsAny(q, "dry fruit", "dry fruits", "meewa")) return R.drawable.ic_food_dry_fruits

        // Fruits & Juices
        if (containsAny(q, "juice", "juices", "fruit juice")) return R.drawable.ic_food_juices
        if (containsAny(q, "mango", "aam")) return R.drawable.ic_food_mango
        if (containsAny(q, "orange", "santra")) return R.drawable.ic_food_orange
        if (containsAny(q, "guava", "amrood")) return R.drawable.ic_food_guava
        if (containsAny(q, "banana", "kela")) return R.drawable.ic_food_banana
        if (containsAny(q, "sweet lime", "mosambi")) return R.drawable.ic_food_sweet_lime
        if (containsAny(q, "apple", "seb")) return R.drawable.ic_food_apple
        if (containsAny(q, "pomegranate", "anar")) return R.drawable.ic_food_pomegranate
        if (containsAny(q, "pineapple", "ananas")) return R.drawable.ic_food_pineapple
        if (containsAny(q, "kiwi")) return R.drawable.ic_food_kiwi
        if (containsAny(q, "watermelon", "tarbooz")) return R.drawable.ic_food_watermelon
        if (containsAny(q, "muskmelon", "cantaloupe", "kharbuja")) return R.drawable.ic_food_muskmelon
        if (containsAny(q, "black grapes", "kala angoor")) return R.drawable.ic_food_black_grapes
        if (containsAny(q, "grapes", "angoor")) return R.drawable.ic_food_grapes
        if (containsAny(q, "peach", "adoo")) return R.drawable.ic_food_peach
        if (containsAny(q, "dragonfruit", "dragon fruit")) return R.drawable.ic_food_dragonfruit
        if (containsAny(q, "coconut", "nariyal", "tender coconut")) return R.drawable.ic_food_coconut
        if (containsAny(q, "fruit bowl", "fruit salad", "fruits")) return R.drawable.ic_food_fruit_bowl
        if (containsAny(q, "salad", "green salad", "cucumber salad")) return R.drawable.ic_food_salad
        if (containsAny(q, "pasta", "macaroni", "spaghetti")) return R.drawable.ic_food_pasta
        if (containsAny(q, "peanut butter toast")) return R.drawable.ic_food_peanut_butter_toast

        // ====================================================================
        // TIER 2: Category & Preparation Fallback Matches
        // ====================================================================

        // Non-Veg Categories
        if (containsAny(q, "crab", "khekda", "crab curry", "crab soup")) return R.drawable.ic_cat_crab
        if (containsAny(q, "pork", "ham", "bacon", "pork chop")) return R.drawable.ic_cat_pork
        if (containsAny(q, "chicken 65", "chicken chilly", "chicken crispy", "dragon chicken")) return R.drawable.ic_cat_chinese_nonveg
        if (containsAny(q, "chicken soup", "mutton soup", "paya soup", "bone broth")) return R.drawable.ic_cat_nonveg_soup
        if (containsAny(q, "chicken")) return R.drawable.ic_cat_chicken
        if (containsAny(q, "mutton", "goat", "lamb")) return R.drawable.ic_cat_mutton
        if (containsAny(q, "fish", "prawn", "seafood")) return R.drawable.ic_cat_fish
        if (containsAny(q, "egg")) return R.drawable.ic_food_egg_omelette

        // Chinese Veg Categories
        if (containsAny(q, "gobi manchurian", "veg crispy", "schezwan", "chinese")) return R.drawable.ic_cat_chinese_veg

        // Veg Soups
        if (containsAny(q, "soup", "tomato soup", "sweet corn soup", "manchow soup")) return R.drawable.ic_cat_veg_soup

        // Paneer & Tofu / Mushroom Categories
        if (containsAny(q, "paneer", "tofu")) return R.drawable.ic_cat_paneer_tofu
        if (containsAny(q, "mushroom", "mushrooms")) return R.drawable.ic_cat_mushroom

        // Sabji / Bhaji (Gravy vs Dry)
        if (containsAny(q, "gravy", "rassa", "curry", "masala", "korma", "makhani", "tikka masala")) return R.drawable.ic_cat_sabji_gravy
        if (containsAny(q, "sabji", "bhaji", "methi", "palak", "shepu", "bhendi", "louki", "kaddu", "aloo", "gobi", "matar", "fry")) return R.drawable.ic_cat_sabji_dry

        // Dals, Pulses, Sprouts & Legumes
        if (containsAny(q, "sprout", "sprouts")) return R.drawable.ic_cat_sprouts
        if (containsAny(q, "dal", "tur", "urad", "chana", "masoor", "pulse", "pulses", "lentil", "lentils")) return R.drawable.ic_cat_dal_lentils

        // Grains & Millets
        if (containsAny(q, "millet", "millets", "bajra", "jowar", "ragi", "nachni", "rajgira", "grain", "grains")) return R.drawable.ic_cat_millets

        // Sweets & Farsan
        if (containsAny(q, "farsan", "namkeen", "gathiya")) return R.drawable.ic_cat_farsan
        if (containsAny(q, "sweet", "sweets", "mithai", "halwa")) return R.drawable.ic_food_gulab_jamun

        // ====================================================================
        // TIER 3: Time-of-Day / Meal Slot Fallback
        // ====================================================================
        return RoutineIconMapper.iconForMealType(mealType)
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        for (kw in keywords) {
            if (text.contains(kw)) return true
        }
        return false
    }
}
