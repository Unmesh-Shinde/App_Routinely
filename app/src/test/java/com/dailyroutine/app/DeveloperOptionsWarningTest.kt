package com.dailyroutine.app

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeveloperOptionsWarningTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun areEnabled_returnsTrueWhenSystemDeveloperOptionsAreEnabled() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)

        assertTrue(DeveloperOptionsWarning.areEnabled(context))
    }

    @Test
    fun areEnabled_returnsFalseWhenSystemDeveloperOptionsAreDisabled() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)

        assertFalse(DeveloperOptionsWarning.areEnabled(context))
    }
}
