package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiResourceContractsTest {

    @Test
    fun walkingDashboard_hasWeeklyAndMonthlyLegendsForStepsAndHeartPoints() {
        val layout = readResourceText("layout/activity_walking_data.xml")

        assertEquals(2, countOccurrences(layout, "@string/legend_steps"))
        assertEquals(2, countOccurrences(layout, "@string/legend_heart_points"))
        assertEquals(2, countOccurrences(layout, "@color/graphSteps"))
        assertTrue(layout.contains("@color/healthCalories"))
        assertTrue(layout.indexOf("@string/legend_steps") < layout.indexOf("@+id/rvWeeklyStepsGraph"))
        assertTrue(layout.lastIndexOf("@string/legend_steps") < layout.indexOf("@+id/rvMonthlySteps"))
    }

    @Test
    fun calorieDashboard_keepsIntakeAndBurnedLegendsForBothGraphTabs() {
        val layout = readResourceText("layout/activity_calories.xml")

        assertEquals(2, countOccurrences(layout, "@color/graphCaloriesIntake"))
        assertEquals(2, countOccurrences(layout, "@color/graphCaloriesBurned"))
        assertEquals(2, countOccurrences(layout, "@color/graphCaloriesMaintenance"))
        assertTrue(countOccurrences(layout, "@string/legend_intake") >= 2)
        assertTrue(countOccurrences(layout, "@string/legend_active_burn") >= 2)
        assertTrue(countOccurrences(layout, "@string/legend_maintenance") >= 2)
    }

    @Test
    fun homeScreen_hasHighContrastStrokesOnMainCards() {
        val layout = readResourceText("layout/activity_main.xml")

        assertTrue(layout.contains("app:strokeColor=\"@color/strokeWellness\""))
        assertTrue(layout.contains("app:strokeColor=\"@color/m3_premium_stroke\""))
    }

    @Test
    fun homeScreenCalorieCard_hasIntakeAndBurnedLabelsAndCenteredNetCalorieNumber() {
        val layout = readResourceText("layout/activity_main.xml")

        assertTrue(layout.contains("@string/legend_intake"))
        assertTrue(layout.contains("@string/legend_burned"))
        assertTrue(layout.contains("android:id=\"@+id/tvValCalories\""))
        assertTrue(layout.contains("android:textAlignment=\"center\""))
    }

    @Test
    fun singleBarGraphDrawables_useRoundedTopGradientFinishes() {
        val expected = mapOf(
            "drawable/bg_calorie_bar.xml" to listOf("@color/graphCaloriesIntake", "@color/graphCaloriesIntakeEnd"),
            "drawable/bg_calorie_burned_bar.xml" to listOf("@color/graphCaloriesBurned", "@color/graphCaloriesBurnedEnd"),
            "drawable/bg_step_bar.xml" to listOf("@color/graphSteps", "@color/graphStepsEnd"),
            "drawable/bg_weight_bar.xml" to listOf("@color/graphWeight", "@color/graphWeightEnd"),
            "drawable/bg_wellness_bar.xml" to listOf("@color/graphWellness", "@color/graphWellnessEnd")
        )

        expected.forEach { (path, colors) ->
            val drawable = readResourceText(path)
            assertTrue("$path should use a vertical gradient", drawable.contains("<gradient") && drawable.contains("android:angle=\"90\""))
            assertTrue("$path should round the top-left corner", drawable.contains("android:topLeftRadius=\"4dp\""))
            assertTrue("$path should round the top-right corner", drawable.contains("android:topRightRadius=\"4dp\""))
            colors.forEach { colorRef -> assertTrue("$path should contain $colorRef", drawable.contains(colorRef)) }
        }
    }

    @Test
    fun graphPalettes_includeGradientEndColorsInLightAndDarkModes() {
        val light = readColorMap("values/colors.xml")
        val dark = readColorMap("values-night/colors.xml")
        val requiredGraphTokens = listOf(
            "graphCaloriesIntake", "graphCaloriesIntakeEnd",
            "graphCaloriesBurned", "graphCaloriesBurnedEnd",
            "graphSteps", "graphStepsEnd",
            "graphSleep", "graphSleepGoalLine", "graphSleepGoalText",
            "graphWeight", "graphWeightEnd",
            "graphWellness", "graphWellnessEnd"
        )

        requiredGraphTokens.forEach { token ->
            assertNotNull("Light palette missing $token", light[token])
            assertNotNull("Dark palette missing $token", dark[token])
        }
    }

    @Test
    fun wellnessDashboard_usesEmeraldPaletteForChromeControlsAndGraph() {
        val layout = readResourceText("layout/activity_wellness_score.xml")
        val activity = readSourceText("WellnessScoreActivity.kt")

        assertTrue(layout.contains("android:background=\"@color/bgPage\""))
        assertTrue(layout.contains("app:tabTextColor=\"@color/textSecondary\""))
        assertTrue(layout.contains("app:tabIndicatorColor=\"@color/primary\""))
        assertTrue(layout.contains("app:thumbColor=\"@color/healthWellness\""))
        assertTrue(layout.contains("app:trackColorActive=\"@color/healthWellness\""))
        assertTrue(activity.contains("R.drawable.bg_wellness_bar"))
        assertTrue(activity.contains("R.color.graphWellness"))
        assertTrue(activity.contains("R.color.graphWellnessEnd"))
    }

    @Test
    fun sleepGraphGoalLineResources_supportReadableDarkAndLightModes() {
        val light = readColorMap("values/colors.xml")
        val dark = readColorMap("values-night/colors.xml")
        val overlay = readSourceText("GoalLineOverlayView.kt")

        assertEquals("#7E57C2", light["graphSleepGoalLine"])
        assertEquals("#4527A0", light["graphSleepGoalText"])
        assertEquals("#D1C4E9", dark["graphSleepGoalLine"])
        assertEquals("#F3EFFF", dark["graphSleepGoalText"])
        assertTrue(overlay.contains("linePaint.alpha = 230"))
        assertTrue(overlay.contains("DashPathEffect(floatArrayOf(10f, 8f), 0f)"))
    }

    private fun readColorMap(relativeResPath: String): Map<String, String> {
        val colorPattern = Regex("<color\\s+name=\"([^\"]+)\"[^>]*>(#[0-9A-Fa-f]{6,8})</color>")
        return readResourceText(relativeResPath)
            .let(colorPattern::findAll)
            .associate { match -> match.groupValues[1] to match.groupValues[2].uppercase() }
    }

    private fun readResourceText(relativeResPath: String): String {
        val file = sequenceOf(
            File("src/main/res/$relativeResPath"),
            File("app/src/main/res/$relativeResPath")
        ).firstOrNull { it.exists() } ?: error("Could not find resource $relativeResPath")
        return file.readText()
    }

    private fun readSourceText(fileName: String): String {
        val file = sequenceOf(
            File("src/main/java/com/dailyroutine/app/$fileName"),
            File("app/src/main/java/com/dailyroutine/app/$fileName")
        ).firstOrNull { it.exists() } ?: error("Could not find source $fileName")
        return file.readText()
    }

    private fun countOccurrences(text: String, needle: String): Int =
        Regex(Regex.escape(needle)).findAll(text).count()
}


