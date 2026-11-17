package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiResourceContractsTest {

    @Test
    fun walkingDashboard_hasOnlyStepsLegendAndNoHeartPointsUi() {
        val layout = readResourceText("layout/activity_walking_data.xml")

        assertEquals(2, countOccurrences(layout, "@string/legend_steps"))
        assertTrue(!layout.contains("Heart Points", ignoreCase = true))
        assertTrue(!layout.contains("cardHeartPoints"))
        assertTrue(!layout.contains("tvHeartPoints"))
        assertEquals(2, countOccurrences(layout, "@color/graphSteps"))
        assertTrue(layout.indexOf("@string/legend_steps") < layout.indexOf("@+id/rvWeeklyStepsGraph"))
        assertTrue(layout.lastIndexOf("@string/legend_steps") < layout.indexOf("@+id/rvMonthlySteps"))

        val distanceLayout = readResourceText("layout/activity_distance.xml")
        assertTrue(!distanceLayout.contains("Heart Points", ignoreCase = true))
        assertTrue(!distanceLayout.contains("tvHeartPoints"))
        val dualMetricLayout = readResourceText("layout/item_dual_metric_bar.xml")
        assertTrue(!dualMetricLayout.contains("Heart Points", ignoreCase = true))
    }

    @Test
    fun healthSync_usesHealthConnectOnlyAndReadsAllAvailableOrigins() {
        val manifest = File("app/src/main/AndroidManifest.xml").readText()
        val mainActivity = readSourceText("MainActivity.kt")
        val sleepActivity = readSourceText("SleepTrackingActivity.kt")
        val healthConnectManager = readSourceText("HealthConnectManager.kt")
        val healthConnectInterface = readSourceText("IHealthConnectManager.kt")
        val syncCoordinator = readSourceText("HealthConnectSyncCoordinator.kt")
        val gradle = File("app/build.gradle").readText()

        assertTrue("Manual sync must no longer scan or present fitness app choices", !mainActivity.contains("HealthAppScanner"))
        assertTrue(!mainActivity.contains("FitnessApp"))
        assertTrue(mainActivity.contains("healthConnectSyncCoordinator.startManualSync()"))
        assertTrue(syncCoordinator.contains("SYNC_MODE_HISTORY"))
        assertTrue(syncCoordinator.contains("Health Connect"))
        assertTrue(!manifest.contains("com.google.android.apps.fitness"))
        assertTrue(!manifest.contains("com.fitbit.FitbitMobile"))
        assertTrue(sleepActivity.contains("Connect Health Connect"))
        assertTrue(!sleepActivity.contains("HealthAppScanner"))

        assertTrue("Health Connect reads must not filter by provider package", !healthConnectManager.contains("dataOriginFilter"))
        assertTrue(!healthConnectManager.contains("DataOrigin("))
        assertTrue(!healthConnectInterface.contains("filterPackage"))
        assertTrue("Heart Points permission must not be declared", !manifest.contains("READ_HEART_RATE"))
        assertTrue("Google Fit Heart Points integration must not be linked", !gradle.contains("play-services-fitness"))
        assertTrue("Health Connect must not request a heart-rate record permission", !healthConnectManager.contains("HeartRateRecord::class"))
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
    fun homeScreen_dailyWellnessScoreHasNoDistinctStroke() {
        val layout = readResourceText("layout/activity_main.xml")

        val wellnessCard = layout.substringAfter("android:id=\"@+id/cardWellnessScore\"").substringBefore("</com.google.android.material.card.MaterialCardView>")
        assertTrue(!wellnessCard.contains("app:strokeColor="))
        assertTrue(wellnessCard.contains("android:text=\"Daily Wellness Score\""))
        assertTrue(wellnessCard.contains("android:layout_height=\"82dp\""))
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
    fun homeScreenHealthSyncButton_usesCompactTwoWordLabel() {
        val layout = readResourceText("layout/activity_main.xml")
        val syncButton = layout.substringAfter("android:id=\"@+id/btnHealthSync\"").substringBefore("/>" )

        assertTrue(syncButton.contains("android:text=\"Sync Now\""))
        assertTrue(!syncButton.contains("Sync Health Connect"))
        assertTrue(layout.contains("android:id=\"@+id/btnSyncLogs\""))
        assertTrue(layout.contains("android:text=\"Sync Logs\""))
        val syncLogsButton = layout.substringAfter("android:id=\"@+id/btnSyncLogs\"").substringBefore("/>" )
        assertTrue(syncLogsButton.contains("@drawable/ic_sync_logs"))
        assertTrue(syncLogsButton.contains("@color/cardBg"))
        assertTrue(syncButton.contains("@drawable/ic_health_sync"))
        assertTrue(!layout.contains("@+id/cardUpcoming"))
        assertTrue(!layout.contains("@+id/tvUpcomingText"))

        val wellness = layout.indexOf("android:id=\"@+id/cardWellnessScore\"")
        val nutrition = layout.indexOf("android:id=\"@+id/cardDiet\"")
        val workout = layout.indexOf("android:id=\"@+id/cardWorkout\"")
        val hydration = layout.indexOf("android:id=\"@+id/cardWater\"")
        val reminders = layout.indexOf("android:id=\"@+id/btnReminders\"")
        val tracking = layout.indexOf("Wellness Tracking")
        val syncNow = layout.indexOf("android:id=\"@+id/btnHealthSync\"")
        val syncLogs = layout.indexOf("android:id=\"@+id/btnSyncLogs\"")
        assertTrue(wellness < nutrition && nutrition < workout && workout < tracking)
        assertTrue(tracking < hydration && hydration < reminders && reminders < syncNow && syncNow < syncLogs)

        val homeContent = layout.substringAfter("android:id=\"@+id/homeScroll\"").substringBefore("<!-- Daily Wellness Score Card -->")
        assertTrue(homeContent.contains("android:layout_height=\"match_parent\""))
        assertEquals(5, countOccurrences(layout, "<Space android:layout_width=\"match_parent\" android:layout_height=\"0dp\" android:layout_weight=\"1\"/>"))

        val hydrationControl = layout.substringAfter("android:id=\"@+id/cardWater\"").substringBefore("</com.google.android.material.card.MaterialCardView>")
        assertTrue(hydrationControl.contains("@drawable/ic_water"))
        assertTrue(hydrationControl.contains("@+id/tvValWater"))
        assertTrue(hydrationControl.contains("@+id/btnWaterMinus"))
        assertTrue(hydrationControl.contains("@+id/btnWaterPlus"))
        assertTrue(hydrationControl.contains("android:gravity=\"center\""))
        assertTrue(hydrationControl.contains("android:layout_marginHorizontal=\"8dp\""))

        val syncRow = layout.substringBefore("android:id=\"@+id/btnHealthSync\"").substringAfterLast("<LinearLayout")
        assertTrue(!syncRow.contains("android:layout_marginTop="))

        val waterIcon = readResourceText("drawable/ic_water.xml")
        assertTrue(waterIcon.contains("Z\" />"))
        assertTrue(!waterIcon.contains("M12,5.4"))
    }

    @Test
    fun wellnessScoreFactors_allowHalfPointIncrements() {
        val layout = readResourceText("layout/activity_wellness_score.xml")
        val activity = readSourceText("WellnessScoreActivity.kt")

        assertEquals(4, countOccurrences(layout, "android:stepSize=\"0.5\""))
        assertTrue(activity.contains("formatFactorWeight"))
        assertTrue(activity.contains("/ 10f"))
    }

    @Test
    fun weightAndCalorieDashboards_useProfileBasedTargetSuggestionWording() {
        val main = readSourceText("MainActivity.kt")
        val dashboard = readSourceText("HomeDashboardUpdater.kt")
        val weight = readSourceText("WeightActivity.kt")
        val calories = readSourceText("CaloriesActivity.kt")
        val expectedSuggestion = "According to the vital details added to your profile"

        assertTrue(main.contains("HomeDashboardUpdater(this, mgr, planManager, healthDataManager).update()"))
        assertTrue(dashboard.contains("Log Today's Weight"))
        assertTrue(dashboard.contains("hasUserWeight(activity)"))
        assertTrue(weight.contains("No weight data logged for today"))
        assertTrue(weight.contains(".setTitle(\"Today's Weight\")"))
        assertTrue(weight.contains(expectedSuggestion))
        assertTrue(weight.contains("metrics.idealWeightKg"))
        assertTrue(calories.contains(expectedSuggestion))
        assertTrue(calories.contains("metrics.idealCalories"))
    }

    @Test
    fun dashboardIcons_keepSemanticMappingsAndSharedScreenReferences() {
        val mapper = readSourceText("RoutineIconMapper.kt")
        val home = readResourceText("layout/activity_main.xml")

        listOf(
            "ReminderType.MEAL -> R.drawable.ic_meal",
            "ReminderType.EXERCISE -> R.drawable.ic_workout",
            "ReminderType.SLEEP -> R.drawable.ic_sleep",
            "\"Breakfast\" -> R.drawable.ic_breakfast",
            "\"Lunch\" -> R.drawable.ic_lunch",
            "\"Dinner\" -> R.drawable.ic_dinner",
            "else -> R.drawable.ic_snack"
        ).forEach { mapping -> assertTrue("Missing icon mapping: $mapping", mapper.contains(mapping)) }

        listOf("ic_meal", "ic_workout", "ic_steps", "ic_sleep", "ic_calories", "ic_weight").forEach { icon ->
            assertTrue("Home dashboard should reference $icon", home.contains("@drawable/$icon"))
        }
        assertTrue(readResourceText("layout/activity_weight.xml").contains("@drawable/ic_weight"))
        assertTrue(readResourceText("layout/activity_distance.xml").contains("@drawable/ic_steps"))
        assertTrue(readResourceText("layout/item_meal.xml").contains("@drawable/ic_meal"))
        assertTrue(readResourceText("layout/item_exercise.xml").contains("@drawable/ic_workout"))
    }

    @Test
    fun appLock_unlockMethodChangesRequireCurrentPinAndAllPinFieldsUseVisibilityControl() {
        val settings = readSourceText("SettingsActivity.kt")
        val pinStore = readSourceText("UserSettingsStore.kt")

        assertTrue(settings.contains("showVerifyPinDialog(\"Confirm PIN to use PIN unlock\")"))
        assertTrue(settings.contains("showVerifyPinDialog(\"Confirm PIN to use biometric unlock\")"))
        assertTrue(settings.contains("UserSettingsStore.verifyManualPin(this, input.editText.text.toString())"))
		assertEquals(5, countOccurrences(settings, "createPinInput("))
		assertTrue(settings.contains("inputType = InputType.TYPE_CLASS_NUMBER"))
		assertTrue(settings.contains("transformationMethod = PinMaskTransformationMethod()"))
		assertTrue(settings.contains("setSingleLine(true)\n\t\t\ttransformationMethod = PinMaskTransformationMethod()"))
		assertTrue(settings.contains("var isRevealed = false"))
		assertTrue(settings.contains("isRevealed = !isRevealed"))
		assertTrue(settings.contains("editText.transformationMethod = if (isRevealed) null else PinMaskTransformationMethod()"))
		assertTrue(settings.contains("setImageResource(R.drawable.ic_visibility)"))
		assertTrue(settings.contains("setImageResource(if (isRevealed) R.drawable.ic_visibility_off else R.drawable.ic_visibility)"))
		assertTrue(settings.contains("contentDescription = if (isRevealed) \"Hide PIN\" else \"Show PIN\""))
        assertTrue(pinStore.contains("fun isManualPinPreviouslyUsed(context: Context, pin: String)"))
        assertTrue(pinStore.contains("KEY_PREVIOUS_PIN_HASH"))
    }

    @Test
    fun dashboardIconPack_usesTintableTwentyFourDpVectorDrawables() {
        val iconResources = listOf(
            "meal", "breakfast", "lunch", "dinner", "snack", "workout", "steps", "sleep", "calories", "weight"
        )

        iconResources.forEach { name ->
            val vector = readResourceText("drawable/ic_$name.xml")
            assertTrue("ic_$name should be a vector drawable", vector.contains("<vector "))
            assertTrue("ic_$name should remain 24dp wide", vector.contains("android:width=\"24dp\""))
            assertTrue("ic_$name should remain 24dp high", vector.contains("android:height=\"24dp\""))
            assertTrue("ic_$name should have vector path geometry", Regex("android:pathData=\"[^\"]+\"").containsMatchIn(vector))
            assertTrue("ic_$name should retain a tintable color resource", Regex("android:fillColor=\"@color/icon[A-Za-z]+\"").containsMatchIn(vector))
        }
    }

    @Test
    fun profilePhotoCropDialog_exposesSaveOriginalAndCancelActions() {
        val layout = readResourceText("layout/dialog_profile_photo_crop.xml")

        assertTrue(layout.contains("@+id/profilePhotoCropFrame"))
        assertTrue(layout.contains("@+id/btnSaveCroppedPhoto"))
        assertTrue(layout.contains("android:text=\"Save cropped photo\""))
        assertTrue(layout.contains("@+id/btnUseOriginalPhoto"))
        assertTrue(layout.contains("@+id/btnCancelPhotoCrop"))
    }

    @Test
    fun profileReportControls_includeDateRangeAndParameterSelection() {
        val layout = readResourceText("layout/activity_profile.xml")
        val activity = readSourceText("ProfileActivity.kt")

        assertTrue(layout.contains("android:text=\"Export Wellness Report\""))
        assertTrue(layout.contains("@+id/spinnerReportRange"))
        assertTrue(layout.contains("@+id/tvReportExportHint"))
        assertTrue(layout.contains("Choose a date range to enable report export."))
        assertTrue(layout.contains("@+id/btnReportParameters"))
        assertTrue(layout.contains("@+id/btnExportReport"))
        assertTrue(layout.contains("android:enabled=\"false\""))
        assertTrue(activity.contains("reportExportButton.isEnabled = hasDateRange && hasParameters"))
        assertTrue(activity.contains("Choose a date range before exporting your wellness report."))
    }

    @Test
    fun profileReportWarning_isBoldReadableAndThemeAware() {
        val layout = readResourceText("layout/activity_profile.xml")
        val warningStart = layout.indexOf("android:id=\"@+id/tvReportExportHint\"")
        assertTrue("Report warning should exist", warningStart >= 0)
        val warningEnd = layout.indexOf(" />", warningStart)
        assertTrue("Report warning should be a self-closing view", warningEnd > warningStart)
        val warning = layout.substring(warningStart, warningEnd)

        assertTrue(warning.contains("@drawable/bg_report_export_hint"))
        assertTrue(warning.contains("@color/reportExportHintText"))
        assertTrue(warning.contains("android:textStyle=\"bold\""))
        assertTrue(warning.contains("android:textSize=\"13sp\""))

        val hintBackground = readResourceText("drawable/bg_report_export_hint.xml")
        assertTrue(hintBackground.contains("@color/badgeReminder"))
        assertTrue(hintBackground.contains("android:radius=\"10dp\""))

        val lightColors = readColorMap("values/colors.xml")
        val darkColors = readColorMap("values-night/colors.xml")
        assertEquals("#6A4B00", lightColors["reportExportHintText"])
        assertEquals("#FFE08A", darkColors["reportExportHintText"])
    }

    @Test
    fun profileSaveButton_appearsBeforeWellnessExportSection() {
        val layout = readResourceText("layout/activity_profile.xml")

        assertEquals(1, countOccurrences(layout, "@+id/btnSaveProfile"))
        assertTrue(layout.indexOf("@+id/btnSaveProfile") < layout.indexOf("Export Wellness Report"))
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

    @Test
    fun foodIconResolver_integratedInCaloriesAndDietPlanActivities() {
        val caloriesActivity = readSourceText("CaloriesActivity.kt")
        val dietPlanActivity = readSourceText("DietPlanActivity.kt")
        val routineIconMapper = readSourceText("RoutineIconMapper.kt")

        assertTrue(caloriesActivity.contains("FoodIconResolver.getIconsForMeal") || caloriesActivity.contains("FoodIconResolver.getIconForMeal"))
        assertTrue(dietPlanActivity.contains("FoodIconResolver.getIconsForMeal") || dietPlanActivity.contains("FoodIconResolver.getIconForMeal"))
        assertTrue(routineIconMapper.contains("FoodIconResolver.getIconForMeal"))
    }

    @Test
    fun nutritionIssuePopup_repeatsUntilResolvedAndNavigatesToTheSelectedMeal() {
        val dashboard = readSourceText("DietPlanActivity.kt")
        val planManager = readSourceText("PlanManager.kt")

        assertTrue(dashboard.contains("window.decorView.postDelayed({ showNutritionTextErrorPopupIfNeeded()"))
        assertTrue(dashboard.contains("!UserSettingsStore.hasAcknowledgedNutritionPolicy(this)) return"))
        assertTrue(dashboard.contains("planManager.getMealsWithNutritionTextErrors()"))
        assertTrue(dashboard.contains("Meals Need Attention"))
        assertTrue(dashboard.contains("Added \$whenAdded · Meal date \$date"))
        assertTrue(dashboard.contains("calendar.time = dateFormatter.parse(date)"))
        assertTrue(dashboard.contains("visibleMeals.indexOfFirst { it.id == meal.id }"))
        assertTrue(dashboard.contains("mealList.scrollToPosition(index)"))
        assertTrue(planManager.contains("it.nutritionErrorCode in setOf(\"ai_payload_too_large\", \"ai_content_blocked\")"))
        assertTrue(planManager.contains("it.second.addedTimestamp.takeIf"))
    }

    @Test
    fun calorieDashboardCard_usesIssueSpecificWarningToneAndOpensDetailsWithoutRetryAction() {
        val calories = readSourceText("CaloriesActivity.kt")
        val dietPlan = readSourceText("DietPlanActivity.kt")
        val warning = readSourceText("NutritionWarningPresentation.kt")

        assertTrue(calories.contains("NutritionWarningPresentation.forMeal("))
        assertTrue(calories.contains("NutritionWarningTone.AMBER -> Color.rgb(183, 103, 0)"))
        assertTrue(calories.contains("NutritionWarningTone.RED -> Color.rgb(183, 28, 28)"))
        assertTrue(calories.contains("cardWarning != null"))
        assertTrue(calories.contains("showMealNutritionPopup(m)"))
        assertTrue(dietPlan.contains("NutritionRetryPolicy.queueFirstRetry(meal ?: Meal(), result.code, nowMs)"))
        assertTrue(dietPlan.contains("rescheduleNutritionRetries()"))
        assertTrue(warning.contains("Warning: shorten meal details"))
        assertTrue(warning.contains("Warning: edit title or description"))
        assertTrue(warning.contains("Meal details exceed the estimate input limit"))
        assertTrue(warning.contains("Meal text was blocked by the nutrition service"))
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


