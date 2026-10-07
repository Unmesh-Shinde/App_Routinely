package com.dailyroutine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HeaderMoodLogicTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun forHour_returnsDrawableForEachTimePhase() {
        // Morning (8 AM)
        assertNotNull(AnimatedHeaderBackgroundDrawable.forHour(context, 8))
        
        // Afternoon (2 PM)
        assertNotNull(AnimatedHeaderBackgroundDrawable.forHour(context, 14))
        
        // Evening (6 PM)
        assertNotNull(AnimatedHeaderBackgroundDrawable.forHour(context, 18))
        
        // Night (10 PM)
        assertNotNull(AnimatedHeaderBackgroundDrawable.forHour(context, 22))
    }
}
