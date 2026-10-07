package com.dailyroutine.app

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UserSettingsStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("user_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun setManualPin_storesPbkdf2VerifierAndVerifiesPin() {
        assertTrue(UserSettingsStore.setManualPin(context, "123456", "safe hint"))

        val prefs = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
        assertEquals("pbkdf2_sha256_v1", prefs.getString("pin_algorithm", null))
        assertTrue((prefs.getString("pin_salt", null) ?: "").isNotBlank())
        assertTrue((prefs.getString("pin_hash", null) ?: "").isNotBlank())
        assertEquals("safe hint", UserSettingsStore.getManualPinHint(context))

        assertTrue(UserSettingsStore.verifyManualPin(context, "123456"))
        assertFalse(UserSettingsStore.verifyManualPin(context, "654321"))
        assertFalse(UserSettingsStore.verifyManualPin(context, "12345"))
    }

    @Test
    fun verifyManualPin_migratesLegacySha256PinAfterSuccessfulUnlock() {
        val pin = "246810"
        val salt = Base64.encodeToString(byteArrayOf(1, 3, 5, 7, 9, 11, 13, 15), Base64.NO_WRAP)
        val legacyHash = legacySha256(pin, salt)
        val prefs = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("pin_salt", salt)
            .putString("pin_hash", legacyHash)
            .putString("pin_hint", "legacy")
            .commit()

        assertTrue(UserSettingsStore.verifyManualPin(context, pin))

        assertEquals("pbkdf2_sha256_v1", prefs.getString("pin_algorithm", null))
        assertNotEquals(legacyHash, prefs.getString("pin_hash", null))
        assertEquals("legacy", UserSettingsStore.getManualPinHint(context))
        assertTrue(UserSettingsStore.verifyManualPin(context, pin))
        assertFalse(UserSettingsStore.verifyManualPin(context, "000000"))
    }

    @Test
    fun verifyManualPin_doesNotMigrateLegacySha256PinWhenPinIsWrong() {
        val salt = Base64.encodeToString(byteArrayOf(2, 4, 6, 8), Base64.NO_WRAP)
        val legacyHash = legacySha256("111111", salt)
        val prefs = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("pin_salt", salt)
            .putString("pin_hash", legacyHash)
            .commit()

        assertFalse(UserSettingsStore.verifyManualPin(context, "222222"))
        assertFalse(prefs.contains("pin_algorithm"))
        assertEquals(legacyHash, prefs.getString("pin_hash", null))
    }

    private fun legacySha256(pin: String, salt: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

