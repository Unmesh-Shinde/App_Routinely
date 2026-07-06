package com.dailyroutine.app

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class AppLockActivity : AppCompatActivity() {
	private var unlocked = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_app_lock)

		val tvHint = findViewById<TextView>(R.id.tvLockHint)
		val pinGroup = findViewById<View>(R.id.pinUnlockGroup)
		val etPin = findViewById<EditText>(R.id.etUnlockPin)
		val btnUnlock = findViewById<MaterialButton>(R.id.btnUnlockWithPin)
		val btnBiometric = findViewById<MaterialButton>(R.id.btnUnlockWithBiometric)

		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				finishAffinity()
			}
		})

		etPin.filters = arrayOf(InputFilter.LengthFilter(6))
		etPin.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD

		if (UserSettingsStore.getLockMethod(this) == UserSettingsStore.LOCK_METHOD_BIOMETRIC) {
			pinGroup.visibility = View.GONE
			btnBiometric.visibility = View.VISIBLE
			tvHint.text = "Use your phone's fingerprint or face unlock to continue."
			btnBiometric.setOnClickListener { showBiometricPrompt() }
			showBiometricPrompt()
		} else {
			pinGroup.visibility = View.VISIBLE
			btnBiometric.visibility = View.GONE
			tvHint.text = "Enter your 6-digit Routinely PIN to unlock."
			btnUnlock.setOnClickListener {
				val pin = etPin.text.toString()
				if (UserSettingsStore.verifyManualPin(this, pin)) {
					completeUnlock()
				} else {
					etPin.error = "Incorrect 6-digit PIN"
					Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
				}
			}
		}
	}

	private fun showBiometricPrompt() {
		if (!AppLockCoordinator.isBiometricAvailable(this)) {
			Toast.makeText(this, "Biometric unlock is not available on this device right now.", Toast.LENGTH_LONG).show()
			return
		}

		val prompt = BiometricPrompt(
			this,
			ContextCompat.getMainExecutor(this),
			object : BiometricPrompt.AuthenticationCallback() {
				override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
					super.onAuthenticationSucceeded(result)
					completeUnlock()
				}

				override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
					super.onAuthenticationError(errorCode, errString)
					if (!unlocked) {
						Toast.makeText(this@AppLockActivity, errString, Toast.LENGTH_SHORT).show()
					}
				}
			}
		)

		val promptInfo = BiometricPrompt.PromptInfo.Builder()
			.setTitle("Unlock Routinely")
			.setSubtitle("Confirm it is you to continue")
			.setNegativeButtonText("Cancel")
			.setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
			.build()

		prompt.authenticate(promptInfo)
	}

	private fun completeUnlock() {
		unlocked = true
		AppLockCoordinator.markUnlocked()
		finish()
	}

	override fun onDestroy() {
		if (!unlocked) AppLockCoordinator.markLockScreenClosed()
		super.onDestroy()
	}
}