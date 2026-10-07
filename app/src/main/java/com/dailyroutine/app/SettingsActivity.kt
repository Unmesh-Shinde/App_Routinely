package com.dailyroutine.app

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
	private lateinit var switchAppLock: SwitchMaterial
	private lateinit var tvLockSummary: TextView
	private lateinit var radioMethodPin: RadioButton
	private lateinit var radioMethodBiometric: RadioButton
	private lateinit var btnChangePin: MaterialButton
	private lateinit var btnClearLoggedLists: MaterialButton
	private lateinit var btnClearDataAndCache: MaterialButton

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_settings)
		InsetHelper.applyTopPadding(findViewById(R.id.appBar))
		InsetHelper.applyBottomPadding(findViewById(R.id.settingsScroll))

		val toolbar = findViewById<Toolbar>(R.id.toolbar)
		setSupportActionBar(toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
		toolbar.setNavigationOnClickListener { finish() }

		switchAppLock = findViewById(R.id.switchAppLock)
		tvLockSummary = findViewById(R.id.tvLockSummary)
		radioMethodPin = findViewById(R.id.radioUnlockPin)
		radioMethodBiometric = findViewById(R.id.radioUnlockBiometric)
		btnChangePin = findViewById(R.id.btnChangePin)
		btnClearLoggedLists = findViewById(R.id.btnClearLoggedLists)
		btnClearDataAndCache = findViewById(R.id.btnClearDataAndCache)

		setupThemeSettings()
		setupLockSettings()
		setupClearData()
		updateLockSummary()
	}

	private fun setupClearData() {
		val options = arrayOf(
			"Nutrition Data (Meals & Templates)",
			"Workout Data (Exercises & Templates)",
			"Health Logs & Progress",
			"Reminders & Alarms"
		)

		btnClearLoggedLists.setOnClickListener {
			val checkedItems = booleanArrayOf(false, false, false, false)
			AlertDialog.Builder(this)
				.setTitle("Clear Logged Lists")
				.setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
					checkedItems[which] = isChecked
				}
				.setPositiveButton("Clear Selected") { _, _ ->
					if (checkedItems.none { it }) {
						Toast.makeText(this, "No data selected to clear.", Toast.LENGTH_SHORT).show()
						return@setPositiveButton
					}
					
					AlertDialog.Builder(this)
						.setTitle("Clear Logged Lists?")
						.setMessage("Selected logged entries will be permanently deleted. Your AI calorie and workout estimation cache will be preserved.")
						.setPositiveButton("Delete") { _, _ ->
							clearSelectedData(checkedItems, includeCache = false)
						}
						.setNegativeButton("Cancel", null)
						.show()
				}
				.setNegativeButton("Cancel", null)
				.show()
		}

		btnClearDataAndCache.setOnClickListener {
			val checkedItems = booleanArrayOf(false, false, false, false)
			AlertDialog.Builder(this)
				.setTitle("Clear Lists & AI Cache")
				.setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
					checkedItems[which] = isChecked
				}
				.setPositiveButton("Clear Selected") { _, _ ->
					if (checkedItems.none { it }) {
						Toast.makeText(this, "No data selected to clear.", Toast.LENGTH_SHORT).show()
						return@setPositiveButton
					}
					
					val warningMessageHtml = "<font color='#D32F2F'><b>WARNING:</b></font> This action will <font color='#D32F2F'><b>PERMANENTLY ERASE</b></font> all your logged meals, custom nutrition records, and <font color='#D32F2F'><b>AI</b></font> smart cache. This data <font color='#D32F2F'><b>CANNOT BE RECOVERED</b></font> once deleted. Are you absolutely sure you want to proceed?"
					val formattedMessage = Html.fromHtml(warningMessageHtml, Html.FROM_HTML_MODE_LEGACY)

					AlertDialog.Builder(this)
						.setTitle("Permanent Data Deletion Warning")
						.setMessage(formattedMessage)
						.setPositiveButton("Delete Everything Selected") { _, _ ->
							clearSelectedData(checkedItems, includeCache = true)
						}
						.setNegativeButton("Cancel", null)
						.show()
				}
				.setNegativeButton("Cancel", null)
				.show()
		}
	}

	private fun clearSelectedData(checkedItems: BooleanArray, includeCache: Boolean) {
		val planManager = PlanManager(this)
		val clearNutrition = checkedItems[0]
		val clearWorkouts = checkedItems[1]
		val clearHealth = checkedItems[2]
		val clearReminders = checkedItems[3]

		if (clearNutrition) {
			planManager.clearDietData()
			if (includeCache) {
				CalorieSearchEngine.clearCache(this)
			}
		}

		if (clearWorkouts) {
			planManager.clearWorkoutData()
		}

		if (clearReminders) {
			val reminderManager = ReminderManager(this)
			val reminders = reminderManager.getAllReminders()
			for (reminder in reminders) {
				reminderManager.deleteReminder(reminder)
			}
			getSharedPreferences("reminders_pref", MODE_PRIVATE).edit().clear().apply()
		}

		if (includeCache) {
			getSharedPreferences("calorie_cache_pref", MODE_PRIVATE).edit().clear().apply()
			getSharedPreferences("workout_met_cache_pref", MODE_PRIVATE).edit().clear().apply()
		}

		if (clearHealth) {
			getSharedPreferences("health_data_pref", MODE_PRIVATE).edit().clear().apply()
			getSharedPreferences("routine_progress_pref", MODE_PRIVATE).edit().clear().apply()
			getSharedPreferences("wellness_score_pref", MODE_PRIVATE).edit().clear().apply()
			getSharedPreferences("HealthBackfillPrefs", MODE_PRIVATE).edit().clear().apply()

			CoroutineScope(Dispatchers.IO).launch {
				RoutinelyDatabase.getInstance(this@SettingsActivity).clearAllTables()
				withContext(Dispatchers.Main) {
					val message = if (includeCache) "Selected data & AI cache cleared successfully." else "Selected logged lists cleared successfully."
					Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
				}
			}
		} else {
			val message = if (includeCache) "Selected data & AI cache cleared successfully." else "Selected logged lists cleared successfully."
			Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
		}
	}

	private fun setupThemeSettings() {
		val themeGroup = findViewById<RadioGroup>(R.id.radioGroupTheme)
		when (UserSettingsStore.getThemeMode(this)) {
			UserSettingsStore.THEME_LIGHT -> themeGroup.check(R.id.radioThemeLight)
			UserSettingsStore.THEME_DARK -> themeGroup.check(R.id.radioThemeDark)
			else -> themeGroup.check(R.id.radioThemeSystem)
		}

		themeGroup.setOnCheckedChangeListener { _, checkedId ->
			val mode = when (checkedId) {
				R.id.radioThemeLight -> UserSettingsStore.THEME_LIGHT
				R.id.radioThemeDark -> UserSettingsStore.THEME_DARK
				else -> UserSettingsStore.THEME_SYSTEM
			}
			UserSettingsStore.setThemeMode(this, mode)
		}
	}

	private fun setupLockSettings() {
		switchAppLock.isChecked = UserSettingsStore.isAppLockEnabled(this)

		val timeoutGroup = findViewById<RadioGroup>(R.id.radioGroupLockTimeout)
		timeoutGroup.check(
			when (UserSettingsStore.getLockTimeoutMs(this)) {
				UserSettingsStore.LOCK_TIMEOUT_10_MIN -> R.id.radioLock10
				UserSettingsStore.LOCK_TIMEOUT_30_MIN -> R.id.radioLock30
				UserSettingsStore.LOCK_TIMEOUT_60_MIN -> R.id.radioLock60
				else -> R.id.radioLockExit
			}
		)
		timeoutGroup.setOnCheckedChangeListener { _, checkedId ->
			UserSettingsStore.setLockTimeoutMs(
				this,
				when (checkedId) {
					R.id.radioLock10 -> UserSettingsStore.LOCK_TIMEOUT_10_MIN
					R.id.radioLock30 -> UserSettingsStore.LOCK_TIMEOUT_30_MIN
					R.id.radioLock60 -> UserSettingsStore.LOCK_TIMEOUT_60_MIN
					else -> UserSettingsStore.LOCK_TIMEOUT_IMMEDIATE
				}
			)
			updateLockSummary()
		}

		val methodGroup = findViewById<RadioGroup>(R.id.radioGroupUnlockMethod)
		methodGroup.check(
			if (UserSettingsStore.getLockMethod(this) == UserSettingsStore.LOCK_METHOD_BIOMETRIC) {
				R.id.radioUnlockBiometric
			} else {
				R.id.radioUnlockPin
			}
		)
		methodGroup.setOnCheckedChangeListener { group, checkedId ->
			when (checkedId) {
				R.id.radioUnlockPin -> {
					if (UserSettingsStore.hasManualPin(this)) {
						UserSettingsStore.setLockMethod(this, UserSettingsStore.LOCK_METHOD_PIN)
						AppLockCoordinator.markLockSettingsChanged(UserSettingsStore.isAppLockEnabled(this))
						updateLockSummary()
					} else {
						showSetPinDialog(
							onSaved = {
								UserSettingsStore.setLockMethod(this, UserSettingsStore.LOCK_METHOD_PIN)
								AppLockCoordinator.markLockSettingsChanged(UserSettingsStore.isAppLockEnabled(this))
								group.check(R.id.radioUnlockPin)
								updateLockSummary()
							},
							onCancelled = { restoreUnlockMethodSelection(group) }
						)
					}
				}
				R.id.radioUnlockBiometric -> {
					if (AppLockCoordinator.isBiometricAvailable(this)) {
						UserSettingsStore.setLockMethod(this, UserSettingsStore.LOCK_METHOD_BIOMETRIC)
						AppLockCoordinator.markLockSettingsChanged(UserSettingsStore.isAppLockEnabled(this))
						updateLockSummary()
					} else {
						Toast.makeText(this, "Fingerprint or face unlock is not available/enrolled on this phone.", Toast.LENGTH_LONG).show()
						restoreUnlockMethodSelection(group)
					}
				}
			}
		}

		switchAppLock.setOnCheckedChangeListener { button, enabled ->
			if (!button.isPressed) return@setOnCheckedChangeListener

			if (enabled) {
				enableLockWithSelectedMethod()
			} else {
				UserSettingsStore.setAppLockEnabled(this, false)
				AppLockCoordinator.markLockSettingsChanged(false)
				updateLockSummary()
			}
		}

		btnChangePin.setOnClickListener {
			showSetPinDialog(
				onSaved = {
					UserSettingsStore.setLockMethod(this, UserSettingsStore.LOCK_METHOD_PIN)
					methodGroup.check(R.id.radioUnlockPin)
					AppLockCoordinator.markLockSettingsChanged(UserSettingsStore.isAppLockEnabled(this))
					updateLockSummary()
					Toast.makeText(this, "PIN saved", Toast.LENGTH_SHORT).show()
				}
			)
		}
	}

	private fun enableLockWithSelectedMethod() {
		when {
			radioMethodBiometric.isChecked -> {
				if (AppLockCoordinator.isBiometricAvailable(this)) {
					UserSettingsStore.setLockMethod(this, UserSettingsStore.LOCK_METHOD_BIOMETRIC)
					UserSettingsStore.setAppLockEnabled(this, true)
					AppLockCoordinator.markLockSettingsChanged(true)
					updateLockSummary()
				} else {
					switchAppLock.isChecked = false
					Toast.makeText(this, "Set up fingerprint or face unlock on your phone first.", Toast.LENGTH_LONG).show()
				}
			}
			UserSettingsStore.hasManualPin(this) -> {
				UserSettingsStore.setLockMethod(this, UserSettingsStore.LOCK_METHOD_PIN)
				UserSettingsStore.setAppLockEnabled(this, true)
				AppLockCoordinator.markLockSettingsChanged(true)
				updateLockSummary()
			}
			else -> {
				switchAppLock.isChecked = false
				showSetPinDialog(
					onSaved = {
						UserSettingsStore.setLockMethod(this, UserSettingsStore.LOCK_METHOD_PIN)
						radioMethodPin.isChecked = true
						UserSettingsStore.setAppLockEnabled(this, true)
						switchAppLock.isChecked = true
						AppLockCoordinator.markLockSettingsChanged(true)
						updateLockSummary()
					}
				)
			}
		}
	}

	private fun restoreUnlockMethodSelection(group: RadioGroup) {
		group.setOnCheckedChangeListener(null)
		group.check(
			if (UserSettingsStore.getLockMethod(this) == UserSettingsStore.LOCK_METHOD_BIOMETRIC) {
				R.id.radioUnlockBiometric
			} else {
				R.id.radioUnlockPin
			}
		)
		setupLockSettings()
	}

	private fun updateLockSummary() {
		val timeout = when (UserSettingsStore.getLockTimeoutMs(this)) {
			UserSettingsStore.LOCK_TIMEOUT_10_MIN -> "after 10 minutes away"
			UserSettingsStore.LOCK_TIMEOUT_30_MIN -> "after 30 minutes away"
			UserSettingsStore.LOCK_TIMEOUT_60_MIN -> "after 60 minutes away"
			else -> "as soon as you exit the app"
		}
		val method = if (UserSettingsStore.getLockMethod(this) == UserSettingsStore.LOCK_METHOD_BIOMETRIC) {
			"fingerprint or face unlock"
		} else {
			"six digit PIN"
		}
		tvLockSummary.text = if (UserSettingsStore.isAppLockEnabled(this)) {
			"Enabled. Routinely locks $timeout and unlocks with $method."
		} else {
			"Off. Select an unlock method and lock timing, then turn on App Lock."
		}
		btnChangePin.text = if (UserSettingsStore.hasManualPin(this)) "Change PIN" else "Create PIN"
	}

	private fun showSetPinDialog(onSaved: () -> Unit, onCancelled: () -> Unit = {}) {
		val hasExistingPin = UserSettingsStore.hasManualPin(this)
		val container = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(48, 16, 48, 0)
		}
		val oldPin = if (hasExistingPin) createPinEditText("Enter your old PIN") else null
		val pin = createPinEditText(if (hasExistingPin) "Enter new PIN" else "Enter New 6-digit PIN")
		val confirm = createPinEditText("Confirm PIN")
		val hint = createHintEditText("Add a Hint").apply {
			setText(UserSettingsStore.getManualPinHint(this@SettingsActivity))
		}
		oldPin?.let { container.addView(it) }
		container.addView(pin)
		container.addView(confirm)
		container.addView(hint)

		val dialog = AlertDialog.Builder(this)
			.setTitle(if (hasExistingPin) "Change PIN" else "Create PIN")
			.setMessage("Use exactly six numbers. Avoid obvious PINs such as 000000 or 123456.")
			.setView(container)
			.setPositiveButton(if (hasExistingPin) "Change PIN" else "Create PIN", null)
			.setNegativeButton("Cancel") { _, _ -> onCancelled() }
			.create()

		dialog.setOnShowListener {
			dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
				val previous = oldPin?.text?.toString().orEmpty()
				val first = pin.text.toString()
				val second = confirm.text.toString()
				when {
					hasExistingPin && !UserSettingsStore.verifyManualPin(this, previous) -> oldPin?.error = "Enter the correct current PIN"
					!first.matches(Regex("\\d{6}")) -> pin.error = "Enter exactly 6 digits"
					hasExistingPin && UserSettingsStore.verifyManualPin(this, first) -> pin.error = "Choose a new PIN that is different from your current PIN"
					first != second -> confirm.error = "PINs do not match"
					first == "000000" || first == "123456" -> pin.error = "Choose a less obvious PIN"
					UserSettingsStore.setManualPin(this, first, hint.text.toString()) -> {
						dialog.dismiss()
						onSaved()
					}
				}
			}
		}
		dialog.setOnCancelListener { onCancelled() }
		dialog.show()
	}

	private fun createPinEditText(hintText: String): EditText {
		return EditText(this).apply {
			hint = hintText
			inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
			filters = arrayOf(InputFilter.LengthFilter(6))
			setSingleLine(true)
		}
	}

	private fun createHintEditText(hintText: String): EditText {
		return EditText(this).apply {
			hint = hintText
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
			filters = arrayOf(InputFilter.LengthFilter(80))
			setSingleLine(true)
		}
	}
}
