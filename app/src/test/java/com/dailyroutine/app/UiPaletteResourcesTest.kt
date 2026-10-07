package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiPaletteResourcesTest {

    @Test
    fun categoryPalette_matchesApprovedLightModeHomeScreenScheme() {
        val colors = readColorMap("values/colors.xml")

        assertEquals("#4CAF50", colors["healthNutrition"])
        assertEquals("#FB8C00", colors["healthWorkout"])
        assertEquals("#2196F3", colors["healthWalking"])
        assertEquals("#F44336", colors["healthCalories"])
        assertEquals("#FB8C00", colors["calorieStatusOrange"])
        assertEquals("#4CAF50", colors["calorieStatusGreen"])
        assertEquals("#F44336", colors["calorieStatusRed"])
        assertEquals("#5E35B1", colors["healthSleep"])
        assertEquals("#009688", colors["healthWeight"])
        assertEquals("#E91E63", colors["healthSync"])
        assertEquals("#FFC107", colors["healthReminder"])
        assertEquals("#546E7A", colors["healthProfile"])
        assertEquals("#00C853", colors["healthWellness"])
        assertEquals("#00351C", colors["textOnWellness"])
    }

    @Test
    fun dashboardPalettes_stayAlignedWithNutritionReminderAndProfileIdentities() {
        val colors = readColorMap("values/colors.xml")

        assertEquals("#4CAF50", colors["nutritionHeaderBg"])
        assertEquals("#E8F5E9", colors["nutritionLegendBg"])
        assertEquals("#FFC107", colors["healthReminder"])
        assertEquals("#546E7A", colors["healthProfile"])
        assertEquals("#F0F5F7", colors["surfaceProfile"])
        assertEquals("#00897B", colors["profileMetricPrimary"])
        assertEquals("#EC407A", colors["profileMetricSecondary"])
    }

    @Test
    fun headerAnimationDuration_isLongEnoughForAVisibleAppStartEffect() {
        val durationMillis = AnimatedHeaderBackgroundDrawable.ENTRANCE_DURATION_MILLIS
        assertTrue(durationMillis >= 1000L)
    }

    @Test
    fun graphPalette_supportsReadableDashboardGraphs() {
        val colors = readColorMap("values/colors.xml")

        assertEquals("#4CAF50", colors["graphCaloriesIntake"])
        assertEquals("#F44336", colors["graphCaloriesBurned"])
        assertEquals("#2196F3", colors["graphSteps"])
        assertEquals("#5E35B1", colors["graphSleep"])
        assertEquals("#7E57C2", colors["graphSleepGoalLine"])
        assertEquals("#4527A0", colors["graphSleepGoalText"])
        assertEquals("#009688", colors["graphWeight"])
        assertEquals("#00C853", colors["graphWellness"])
    }

    @Test
    fun highContrastStrokePalette_containsDarkerVariantsForCardsAndButtons() {
        val colors = readColorMap("values/colors.xml")

        assertEquals("#2E7D32", colors["strokeNutrition"])
        assertEquals("#BF360C", colors["strokeWorkout"])
        assertEquals("#1565C0", colors["strokeWalking"])
        assertEquals("#B71C1C", colors["strokeCalories"])
        assertEquals("#4527A0", colors["strokeSleep"])
        assertEquals("#00695C", colors["strokeWeight"])
        assertEquals("#880E4F", colors["strokeSync"])
        assertEquals("#FF8F00", colors["strokeReminder"])
        assertEquals("#1B5E20", colors["strokeWellness"])
        assertEquals("#0277BD", colors["strokeHydration"])
    }

    @Test
    fun backgroundPalette_usesSoftNeutralTonesToReduceEyeStrain() {
        val colors = readColorMap("values/colors.xml")

        assertEquals("#DFE1E5", colors["bgPage"])
        assertEquals("#E8EAED", colors["cardBg"])
    }

    @Test
    fun widgetPalette_matchesAtmosphereGrayTheme() {
        val colors = readColorMap("values/colors.xml")
        
        // Widget should follow the soft gray baseline
        assertEquals("#DFE1E5", colors["bgPage"])
        assertEquals("#E8EAED", colors["cardBg"])
    }

    private fun readColorMap(relativeResPath: String): Map<String, String> {
        val file = sequenceOf(
            File("src/main/res/$relativeResPath"),
            File("app/src/main/res/$relativeResPath")
        ).firstOrNull { it.exists() } ?: error("Could not find $relativeResPath")

        val colorPattern = Regex("<color\\s+name=\"([^\"]+)\"[^>]*>(#[0-9A-Fa-f]{6,8})</color>")
        return colorPattern.findAll(file.readText())
            .associate { match -> match.groupValues[1] to match.groupValues[2].uppercase() }
    }
}



