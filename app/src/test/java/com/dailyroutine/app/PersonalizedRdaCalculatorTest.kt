package com.dailyroutine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonalizedRdaCalculatorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun calculate_generatesPersonalizedMacroAndMicroRdaTargets() {
        UserPreferencesStore.setUserAge(context, 28)
        UserPreferencesStore.setUserHeight(context, 175.0)
        UserPreferencesStore.setUserWeight(context, 75.0)
        UserPreferencesStore.setUserGender(context, "Male")

        val rda = PersonalizedRdaCalculator.calculate(context)

        assertNotNull(rda)
        assertTrue(rda.idealCalories > 1500)
        assertEquals(90.0, rda.proteinG, 0.1) // 75kg * 1.2 = 90g
        assertTrue(rda.carbsG > 150.0)
        assertTrue(rda.fatG > 40.0)

        // Test % Daily Goal calculations
        val (pct, label) = rda.calculateRdaPercentage("Protein", "45 g")
        assertEquals(50, pct) // 45g / 90g = 50%
        assertEquals("50% Goal", label)
    }

    @Test
    fun calculate_adjustsIronTargetForFemalesUnder50() {
        UserPreferencesStore.setUserAge(context, 26)
        UserPreferencesStore.setUserHeight(context, 160.0)
        UserPreferencesStore.setUserWeight(context, 55.0)
        UserPreferencesStore.setUserGender(context, "Female")

        val rda = PersonalizedRdaCalculator.calculate(context)

        assertEquals(29.0, rda.minerals["iron"])

        val (pct, label) = rda.calculateRdaPercentage("Iron", "14.5 mg")
        assertEquals(50, pct)
        assertEquals("50% Goal", label)
    }

    @Test
    fun calculateRdaPercentage_returnsZeroPercentForZeroValue() {
        val rda = PersonalizedRdaCalculator.calculate(context)

        val (pct, label) = rda.calculateRdaPercentage("Protein", "0 g")
        assertEquals(0, pct)
        assertEquals("0% Goal", label)
    }
}
