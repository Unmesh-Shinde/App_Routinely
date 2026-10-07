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
class UserPreferencesStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun weightGoal_persistsAndRetrievesCorrectly() {
        assertEquals("Maintain Weight", UserPreferencesStore.getUserGoal(context))

        UserPreferencesStore.setUserGoal(context, "Lose Weight")
        assertEquals("Lose Weight", UserPreferencesStore.getUserGoal(context))

        UserPreferencesStore.setUserGoal(context, "Gain Weight")
        assertEquals("Gain Weight", UserPreferencesStore.getUserGoal(context))
    }

    @Test
    fun userMetrics_persistAndRetrieveCorrectly() {
        UserPreferencesStore.setUserAge(context, 30)
        UserPreferencesStore.setUserHeight(context, 180.0)
        UserPreferencesStore.setUserWeight(context, 85.0)
        UserPreferencesStore.setUserGender(context, "Male")

        assertEquals(30, UserPreferencesStore.getUserAge(context))
        assertEquals(180.0, UserPreferencesStore.getUserHeight(context), 0.01)
        assertEquals(85.0, UserPreferencesStore.getUserWeight(context), 0.01)
        assertEquals("Male", UserPreferencesStore.getUserGender(context))
    }

    @Test
    fun avatarId_persistsAndRetrievesCorrectly() {
        assertEquals(1, UserPreferencesStore.getUserAvatarId(context))

        UserPreferencesStore.setUserAvatarId(context, 10)
        assertEquals(10, UserPreferencesStore.getUserAvatarId(context))
    }
}
