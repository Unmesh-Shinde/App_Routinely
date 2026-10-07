package com.dailyroutine.app

import android.content.Context
import android.util.Base64
import androidx.appcompat.app.AppCompatDelegate
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object UserSettingsStore {
	const val THEME_SYSTEM = "system"
	const val THEME_LIGHT = "light"
	const val THEME_DARK = "dark"

	const val LOCK_METHOD_PIN = "pin"
	const val LOCK_METHOD_BIOMETRIC = "biometric"

	const val LOCK_TIMEOUT_IMMEDIATE = 0L
	const val LOCK_TIMEOUT_10_MIN = 10L * 60L * 1000L
	const val LOCK_TIMEOUT_30_MIN = 30L * 60L * 1000L
	const val LOCK_TIMEOUT_60_MIN = 60L * 60L * 1000L

	private const val PREFS = "user_settings"
	private const val KEY_THEME_MODE = "theme_mode"
	private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
	private const val KEY_LOCK_METHOD = "lock_method"
	private const val KEY_LOCK_TIMEOUT_MS = "lock_timeout_ms"
	private const val KEY_PIN_SALT = "pin_salt"
	private const val KEY_PIN_HASH = "pin_hash"
	private const val KEY_PIN_HINT = "pin_hint"
	private const val KEY_PIN_ALGORITHM = "pin_algorithm"
	private const val PIN_ALGORITHM_PBKDF2 = "pbkdf2_sha256_v1"
	private const val PBKDF2_ITERATIONS = 180_000
	private const val PBKDF2_KEY_LENGTH_BITS = 256

	private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	fun getThemeMode(context: Context): String = prefs(context).getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM

	fun setThemeMode(context: Context, mode: String) {
		prefs(context).edit().putString(KEY_THEME_MODE, mode).apply()
		applyTheme(mode)
	}

	fun applySavedTheme(context: Context) = applyTheme(getThemeMode(context))

	fun applyTheme(mode: String) {
		AppCompatDelegate.setDefaultNightMode(
			when (mode) {
				THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
				THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
				else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
			}
		)
	}

	fun isAppLockEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_APP_LOCK_ENABLED, false)
	fun setAppLockEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()

	fun getLockMethod(context: Context): String = prefs(context).getString(KEY_LOCK_METHOD, LOCK_METHOD_PIN) ?: LOCK_METHOD_PIN
	fun setLockMethod(context: Context, method: String) = prefs(context).edit().putString(KEY_LOCK_METHOD, method).apply()

	fun getLockTimeoutMs(context: Context): Long = prefs(context).getLong(KEY_LOCK_TIMEOUT_MS, LOCK_TIMEOUT_IMMEDIATE)
	fun setLockTimeoutMs(context: Context, timeoutMs: Long) = prefs(context).edit().putLong(KEY_LOCK_TIMEOUT_MS, timeoutMs).apply()

	fun shouldLockAfterBackground(context: Context, backgroundDurationMs: Long): Boolean {
		val timeoutMs = getLockTimeoutMs(context)
		return timeoutMs == LOCK_TIMEOUT_IMMEDIATE || backgroundDurationMs >= timeoutMs
	}

	fun hasManualPin(context: Context): Boolean = !prefs(context).getString(KEY_PIN_HASH, null).isNullOrBlank()

	fun setManualPin(context: Context, pin: String, hint: String = ""): Boolean {
		if (!pin.matches(Regex("\\d{6}"))) return false

		val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
		val saltText = Base64.encodeToString(salt, Base64.NO_WRAP)
		val hash = hashPinPbkdf2(pin, saltText)
		prefs(context).edit()
			.putString(KEY_PIN_SALT, saltText)
			.putString(KEY_PIN_HASH, hash)
			.putString(KEY_PIN_HINT, hint.trim())
			.putString(KEY_PIN_ALGORITHM, PIN_ALGORITHM_PBKDF2)
			.apply()
		return true
	}

	fun getManualPinHint(context: Context): String = prefs(context).getString(KEY_PIN_HINT, "")?.trim().orEmpty()

	fun verifyManualPin(context: Context, pin: String): Boolean {
		if (!pin.matches(Regex("\\d{6}"))) return false
		val settings = prefs(context)
		val savedSalt = settings.getString(KEY_PIN_SALT, null) ?: return false
		val savedHash = settings.getString(KEY_PIN_HASH, null) ?: return false
		val algorithm = settings.getString(KEY_PIN_ALGORITHM, null)

		return if (algorithm == PIN_ALGORITHM_PBKDF2) {
			constantTimeBase64Equals(hashPinPbkdf2(pin, savedSalt), savedHash)
		} else {
			val verifiedLegacyPin = constantTimeBase64Equals(hashPinLegacySha256(pin, savedSalt), savedHash)
			if (verifiedLegacyPin) {
				// Migrate existing SHA-256 PINs after a successful unlock so users are not locked out.
				setManualPin(context, pin, getManualPinHint(context))
			}
			verifiedLegacyPin
		}
	}

	private fun hashPinPbkdf2(pin: String, salt: String): String {
		val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
		val spec = PBEKeySpec(pin.toCharArray(), saltBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
		return try {
			val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
			Base64.encodeToString(bytes, Base64.NO_WRAP)
		} finally {
			spec.clearPassword()
		}
	}

	private fun hashPinLegacySha256(pin: String, salt: String): String {
		val digest = MessageDigest.getInstance("SHA-256")
		val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
		return Base64.encodeToString(bytes, Base64.NO_WRAP)
	}

	private fun constantTimeBase64Equals(left: String, right: String): Boolean {
		return try {
			MessageDigest.isEqual(
				Base64.decode(left, Base64.NO_WRAP),
				Base64.decode(right, Base64.NO_WRAP)
			)
		} catch (_: IllegalArgumentException) {
			false
		}
	}
}