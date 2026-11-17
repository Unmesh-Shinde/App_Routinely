package com.dailyroutine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NutritionPdfExporterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("user_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun generatePdf_runsSafelyWithoutUncaughtExceptions() {
        val pdfUnack = NutritionPdfExporter.generatePdf(context)
        if (pdfUnack != null) {
            assertEquals("Routinely_Nutrition_Fair_Use_Guide.pdf", pdfUnack.name)
        }

        UserSettingsStore.setNutritionPolicyAcknowledged(context, "2026-10-26")
        val pdfAck = NutritionPdfExporter.generatePdf(context)
        if (pdfAck != null) {
            assertEquals("Routinely_Nutrition_Fair_Use_Guide.pdf", pdfAck.name)
        }
    }
}
