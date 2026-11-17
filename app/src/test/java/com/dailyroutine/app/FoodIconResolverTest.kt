package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodIconResolverTest {

    @Test
    fun tier1_individualDishes_broadSpectrum() {
        // Rice & Biryani
        assertEquals(R.drawable.ic_food_veg_biryani, FoodIconResolver.getIconForMeal("Veg Biryani"))
        assertEquals(R.drawable.ic_food_nonveg_biryani, FoodIconResolver.getIconForMeal("Chicken Biryani"))
        assertEquals(R.drawable.ic_food_dal_khichdi, FoodIconResolver.getIconForMeal("Dal Khichdi"))
        assertEquals(R.drawable.ic_food_dal_rice, FoodIconResolver.getIconForMeal("Varan Bhat"))
        assertEquals(R.drawable.ic_food_curd_rice, FoodIconResolver.getIconForMeal("Tayir Sadam"))
        assertEquals(R.drawable.ic_food_jeera_rice, FoodIconResolver.getIconForMeal("Jeera Rice"))
        assertEquals(R.drawable.ic_food_lemon_rice, FoodIconResolver.getIconForMeal("Lemon Rice"))
        assertEquals(R.drawable.ic_food_rajma_chawal, FoodIconResolver.getIconForMeal("Rajma Chawal"))

        // Flatbreads & Tiffin
        assertEquals(R.drawable.ic_food_butter_naan, FoodIconResolver.getIconForMeal("Garlic Butter Naan"))
        assertEquals(R.drawable.ic_food_paratha, FoodIconResolver.getIconForMeal("Aloo Paratha"))
        assertEquals(R.drawable.ic_food_dosa, FoodIconResolver.getIconForMeal("Masala Dosa"))
        assertEquals(R.drawable.ic_food_idli_sambar, FoodIconResolver.getIconForMeal("Idli Sambar"))
        assertEquals(R.drawable.ic_food_medu_wada, FoodIconResolver.getIconForMeal("Medu Wada"))
        assertEquals(R.drawable.ic_food_uttapam, FoodIconResolver.getIconForMeal("Onion Uttapam"))
        assertEquals(R.drawable.ic_food_appam_stew, FoodIconResolver.getIconForMeal("Appam Stew"))
        assertEquals(R.drawable.ic_food_puri_bhaji, FoodIconResolver.getIconForMeal("Puri Bhaji"))
        assertEquals(R.drawable.ic_food_chole_bhature, FoodIconResolver.getIconForMeal("Chole Bhature"))
        assertEquals(R.drawable.ic_food_kathi_roll, FoodIconResolver.getIconForMeal("Chicken Frankie"))

        // Fast Food, Chaat & Snacks
        assertEquals(R.drawable.ic_food_wada_pav, FoodIconResolver.getIconForMeal("Special Wada Pav"))
        assertEquals(R.drawable.ic_food_pav_bhaji, FoodIconResolver.getIconForMeal("Pav Bhaji"))
        assertEquals(R.drawable.ic_food_misal_pav, FoodIconResolver.getIconForMeal("Misal Pav"))
        assertEquals(R.drawable.ic_food_sev_puri, FoodIconResolver.getIconForMeal("Sev Puri"))
        assertEquals(R.drawable.ic_food_dahi_puri, FoodIconResolver.getIconForMeal("Dahi Puri"))
        assertEquals(R.drawable.ic_food_pani_puri, FoodIconResolver.getIconForMeal("Golgappa"))
        assertEquals(R.drawable.ic_food_bhel, FoodIconResolver.getIconForMeal("Bhel Puri"))
        assertEquals(R.drawable.ic_food_samosa, FoodIconResolver.getIconForMeal("Samosa Chaat"))
        assertEquals(R.drawable.ic_food_kachori, FoodIconResolver.getIconForMeal("Pyaz Kachori"))
        assertEquals(R.drawable.ic_food_dhokla, FoodIconResolver.getIconForMeal("Dhokla"))
        assertEquals(R.drawable.ic_food_fafda, FoodIconResolver.getIconForMeal("Fafda Jalebi"))
        assertEquals(R.drawable.ic_food_momos, FoodIconResolver.getIconForMeal("Steamed Momos"))
        assertEquals(R.drawable.ic_food_shawarma, FoodIconResolver.getIconForMeal("Chicken Shawarma"))
        assertEquals(R.drawable.ic_food_burger, FoodIconResolver.getIconForMeal("Cheeseburger"))
        assertEquals(R.drawable.ic_food_bread_sandwich, FoodIconResolver.getIconForMeal("Grilled Sandwich"))
        assertEquals(R.drawable.ic_food_pizza, FoodIconResolver.getIconForMeal("Cheese Pizza"))
        assertEquals(R.drawable.ic_food_french_fries, FoodIconResolver.getIconForMeal("Potato Fries"))
        assertEquals(R.drawable.ic_food_veg_puff, FoodIconResolver.getIconForMeal("Veg Puff Patties"))
        assertEquals(R.drawable.ic_food_garlic_bread, FoodIconResolver.getIconForMeal("Garlic Bread"))
        assertEquals(R.drawable.ic_food_waffles_pancakes, FoodIconResolver.getIconForMeal("Belgian Waffle"))

        // Chinese & Noodles
        assertEquals(R.drawable.ic_food_chowmein, FoodIconResolver.getIconForMeal("Hakka Noodles"))
        assertEquals(R.drawable.ic_food_manchurian, FoodIconResolver.getIconForMeal("Veg Manchurian"))
        assertEquals(R.drawable.ic_food_noodle_ramen, FoodIconResolver.getIconForMeal("Ramen Bowl"))

        // Meat & Seafood Starters / Curries
        assertEquals(R.drawable.ic_food_tandoori_chicken, FoodIconResolver.getIconForMeal("Afghani Tandoori Chicken"))
        assertEquals(R.drawable.ic_food_chicken_tikka, FoodIconResolver.getIconForMeal("Reshmi Chicken Tikka"))
        assertEquals(R.drawable.ic_food_chicken_fry, FoodIconResolver.getIconForMeal("Crispy Chicken Fry"))
        assertEquals(R.drawable.ic_food_chicken_curry, FoodIconResolver.getIconForMeal("Chicken Rassa Curry"))
        assertEquals(R.drawable.ic_food_mutton_curry, FoodIconResolver.getIconForMeal("Mutton Kolhapuri Curry"))
        assertEquals(R.drawable.ic_food_mutton_roast, FoodIconResolver.getIconForMeal("Mutton Sukka Roast"))
        assertEquals(R.drawable.ic_food_fish_fry, FoodIconResolver.getIconForMeal("Surmai Fish Fry"))
        assertEquals(R.drawable.ic_food_fish_curry, FoodIconResolver.getIconForMeal("Fish Curry"))
        assertEquals(R.drawable.ic_food_tandoori_prawns, FoodIconResolver.getIconForMeal("Tandoori Prawns"))
        assertEquals(R.drawable.ic_food_prawns_curry, FoodIconResolver.getIconForMeal("Prawns Curry"))
        assertEquals(R.drawable.ic_food_seekh_kebab, FoodIconResolver.getIconForMeal("Mutton Seekh Kebab"))
        assertEquals(R.drawable.ic_food_shami_kebab, FoodIconResolver.getIconForMeal("Shami Kabab"))
        assertEquals(R.drawable.ic_food_grilled_chicken, FoodIconResolver.getIconForMeal("Grilled Chicken Breast"))

        // Fasting / Vrat / Gym Foods
        assertEquals(R.drawable.ic_food_sabudana_khichdi, FoodIconResolver.getIconForMeal("Upvas Sabudana Khichdi"))
        assertEquals(R.drawable.ic_food_sabudana_vada, FoodIconResolver.getIconForMeal("Sabudana Wada"))
        assertEquals(R.drawable.ic_food_makhana, FoodIconResolver.getIconForMeal("Roasted Makhana"))
        assertEquals(R.drawable.ic_food_protein_shake, FoodIconResolver.getIconForMeal("Whey Protein Shake"))
        assertEquals(R.drawable.ic_food_sprouts_salad, FoodIconResolver.getIconForMeal("Sprouted Moong Salad"))

        // Egg Meals
        assertEquals(R.drawable.ic_food_egg_omelette, FoodIconResolver.getIconForMeal("Egg Omelette"))
        assertEquals(R.drawable.ic_food_boiled_egg, FoodIconResolver.getIconForMeal("Hard Boiled Eggs"))
        assertEquals(R.drawable.ic_food_egg_bhurji, FoodIconResolver.getIconForMeal("Egg Bhurji"))

        // Mithai & Sweets
        assertEquals(R.drawable.ic_food_gulab_jamun, FoodIconResolver.getIconForMeal("Gulab Jamun"))
        assertEquals(R.drawable.ic_food_jalebi, FoodIconResolver.getIconForMeal("Hot Jalebi"))
        assertEquals(R.drawable.ic_food_rasgulla, FoodIconResolver.getIconForMeal("Rasgulla"))
        assertEquals(R.drawable.ic_food_kheer, FoodIconResolver.getIconForMeal("Rice Kheer"))
        assertEquals(R.drawable.ic_food_kaju_katli, FoodIconResolver.getIconForMeal("Kaju Katli Barfi"))
        assertEquals(R.drawable.ic_food_gajar_halwa, FoodIconResolver.getIconForMeal("Gajar Ka Halwa"))
        assertEquals(R.drawable.ic_food_laddoo, FoodIconResolver.getIconForMeal("Motichoor Ladoo"))
        assertEquals(R.drawable.ic_food_kulfi, FoodIconResolver.getIconForMeal("Matka Kulfi"))
        assertEquals(R.drawable.ic_food_shrikhand, FoodIconResolver.getIconForMeal("Amrakhand Shrikhand"))
        assertEquals(R.drawable.ic_food_mishti_doi, FoodIconResolver.getIconForMeal("Mishti Doi"))
        assertEquals(R.drawable.ic_food_ghevar, FoodIconResolver.getIconForMeal("Rajasthani Ghevar"))
        assertEquals(R.drawable.ic_food_peda, FoodIconResolver.getIconForMeal("Mathura Peda"))

        // Beverages & Alcohol
        assertEquals(R.drawable.ic_food_falooda, FoodIconResolver.getIconForMeal("Rose Falooda"))
        assertEquals(R.drawable.ic_food_badam_milk, FoodIconResolver.getIconForMeal("Chilled Badam Milk"))
        assertEquals(R.drawable.ic_food_jaljeera, FoodIconResolver.getIconForMeal("Masala Jaljeera Soda"))
        assertEquals(R.drawable.ic_food_thandai, FoodIconResolver.getIconForMeal("Kesar Thandai"))
        assertEquals(R.drawable.ic_food_sugarcane_juice, FoodIconResolver.getIconForMeal("Ganne Ka Ras"))
        assertEquals(R.drawable.ic_food_sol_kadi, FoodIconResolver.getIconForMeal("Kokum Sol Kadi"))
        assertEquals(R.drawable.ic_food_lassi, FoodIconResolver.getIconForMeal("Mango Lassi"))
        assertEquals(R.drawable.ic_food_buttermilk, FoodIconResolver.getIconForMeal("Spiced Chaas"))
        assertEquals(R.drawable.ic_food_whiskey, FoodIconResolver.getIconForMeal("Bourbon Whiskey"))
        assertEquals(R.drawable.ic_food_vodka, FoodIconResolver.getIconForMeal("Vodka Shot"))
        assertEquals(R.drawable.ic_food_beer, FoodIconResolver.getIconForMeal("Draught Beer"))
        assertEquals(R.drawable.ic_food_ghee, FoodIconResolver.getIconForMeal("Desi Cow Ghee"))
    }

    @Test
    fun tier1_descriptionFieldMatching() {
        // Description field keyword matching when title is generic
        assertEquals(R.drawable.ic_food_samosa, FoodIconResolver.getIconForMeal("Evening Snack", description = "Crispy Punjabi Samosa"))
        assertEquals(R.drawable.ic_food_protein_shake, FoodIconResolver.getIconForMeal("Post Workout Drink", description = "Vanilla Whey Protein Shake"))
        assertEquals(R.drawable.ic_food_almonds, FoodIconResolver.getIconForMeal("Morning Nuts", description = "Soaked Almonds Badam"))
    }

    @Test
    fun tier2_allCategoryFallbackMatching() {
        // Main Proteins
        assertEquals(R.drawable.ic_cat_chicken, FoodIconResolver.getIconForMeal("Unlisted Chicken Dish"))
        assertEquals(R.drawable.ic_cat_mutton, FoodIconResolver.getIconForMeal("Unlisted Lamb Feast"))
        assertEquals(R.drawable.ic_cat_fish, FoodIconResolver.getIconForMeal("Unlisted Seafood Delight"))
        assertEquals(R.drawable.ic_cat_crab, FoodIconResolver.getIconForMeal("Khekda Crab Masala"))
        assertEquals(R.drawable.ic_cat_pork, FoodIconResolver.getIconForMeal("Grilled Pork Chop"))

        // Chinese Non-Veg & Veg Categories
        assertEquals(R.drawable.ic_cat_chinese_nonveg, FoodIconResolver.getIconForMeal("Spicy Chicken 65"))
        assertEquals(R.drawable.ic_cat_chinese_veg, FoodIconResolver.getIconForMeal("Veg Schezwan Dry"))

        // Soups
        assertEquals(R.drawable.ic_cat_veg_soup, FoodIconResolver.getIconForMeal("Tomato Soup"))
        assertEquals(R.drawable.ic_cat_nonveg_soup, FoodIconResolver.getIconForMeal("Chicken Bone Broth Soup"))

        // Paneer, Mushroom, Sabji Dry vs Gravy
        assertEquals(R.drawable.ic_cat_paneer_tofu, FoodIconResolver.getIconForMeal("Tofu Saute"))
        assertEquals(R.drawable.ic_cat_mushroom, FoodIconResolver.getIconForMeal("Sauteed Mushrooms"))
        assertEquals(R.drawable.ic_cat_sabji_dry, FoodIconResolver.getIconForMeal("Fresh Methi Sabji"))
        assertEquals(R.drawable.ic_cat_sabji_gravy, FoodIconResolver.getIconForMeal("Rich Veg Korma Gravy"))

        // Dals, Sprouts, Millets & Farsan
        assertEquals(R.drawable.ic_cat_sprouts, FoodIconResolver.getIconForMeal("Sprouted Moong Sprouts"))
        assertEquals(R.drawable.ic_cat_dal_lentils, FoodIconResolver.getIconForMeal("Tur Dal Tadka"))
        assertEquals(R.drawable.ic_cat_millets, FoodIconResolver.getIconForMeal("Bajra Porridge"))
        assertEquals(R.drawable.ic_cat_farsan, FoodIconResolver.getIconForMeal("Gujarati Namkeen Farsan"))
    }

    @Test
    fun tier3_slotDefaultFallbackMatching() {
        assertEquals(R.drawable.ic_breakfast, FoodIconResolver.getIconForMeal("Unknown Item Alpha", mealType = "Breakfast"))
        assertEquals(R.drawable.ic_lunch, FoodIconResolver.getIconForMeal("Unknown Item Beta", mealType = "Lunch"))
        assertEquals(R.drawable.ic_dinner, FoodIconResolver.getIconForMeal("Unknown Item Gamma", mealType = "Dinner"))
        assertEquals(R.drawable.ic_snack, FoodIconResolver.getIconForMeal("Unknown Item Delta", mealType = "Snack"))
    }

    @Test
    fun routineIconMapper_delegatesToFoodIconResolver() {
        assertEquals(R.drawable.ic_food_wada_pav, RoutineIconMapper.iconForMeal("Wada Pav"))
        assertEquals(R.drawable.ic_cat_sabji_dry, RoutineIconMapper.iconForMeal("Methi Bhaji"))
        assertEquals(R.drawable.ic_breakfast, RoutineIconMapper.iconForMeal("Custom Dish", mealType = "Breakfast"))
    }

    @Test
    fun getIconsForMeal_multiItemAndDominanceResolution() {
        // Predefined combo dishes return single element list
        assertEquals(listOf(R.drawable.ic_food_dal_rice), FoodIconResolver.getIconsForMeal("Dal Chawal"))
        assertEquals(listOf(R.drawable.ic_food_puri_bhaji), FoodIconResolver.getIconsForMeal("Puri Bhaji"))

        // Single dish items return single element list
        assertEquals(listOf(R.drawable.ic_food_veg_biryani), FoodIconResolver.getIconsForMeal("Veg Biryani"))

        // 2-in-1 Combo resolution
        val combo2 = FoodIconResolver.getIconsForMeal("Aloo Paratha and Mango Lassi")
        assertEquals(2, combo2.size)
        assertEquals(R.drawable.ic_food_paratha, combo2[0])
        assertEquals(R.drawable.ic_food_lassi, combo2[1])

        // 3-in-1 Combo resolution (capped at 3)
        val combo3 = FoodIconResolver.getIconsForMeal("2 Rotis, 1 bowl Dal Fry, and 1 glass Mango Lassi")
        assertEquals(3, combo3.size)
        assertEquals(R.drawable.ic_food_dal_chapati, combo3[0])

        // 60% Dominance threshold suppresses secondary minor items
        val dominantBiryani = FoodIconResolver.getIconsForMeal("Lunch", description = "500g Chicken Biryani with a spoon of raita")
        assertEquals(1, dominantBiryani.size)
        assertEquals(R.drawable.ic_food_nonveg_biryani, dominantBiryani[0])
    }
}
