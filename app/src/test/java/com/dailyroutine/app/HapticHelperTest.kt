package com.dailyroutine.app

import android.content.Context
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVibrator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HapticHelperTest {
    private lateinit var context: Context
    private lateinit var shadowVibrator: ShadowVibrator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        shadowVibrator = shadowOf(vibrator) as ShadowVibrator
    }

    @Test
    fun triggerThump_requestsVibration() {
        HapticHelper.triggerThump(context)
        
        assertTrue("Vibrator should have been triggered", shadowVibrator.isVibrating)
    }
}
