package com.dailyroutine.app

import android.Manifest
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var mgr: ReminderManager
    private lateinit var planManager: PlanManager
    private lateinit var healthDataManager: HealthDataManager
    private lateinit var healthConnectManager: IHealthConnectManager
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Set<String>>
    private lateinit var healthConnectSyncCoordinator: HealthConnectSyncCoordinator
    private lateinit var firstLaunchPermissionFlow: FirstLaunchPermissionFlow
    private var headerBackground: AnimatedHeaderBackgroundDrawable? = null
    private var entranceAnimationPlayed = false
    private var homeSavedInstanceState: Bundle? = null
    private val dataUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateDashboard()
        }
    }
    
    private val fileToneLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            if (ReminderToneHelper.isToneDurationAllowed(this, it)) {
                runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                ReminderDialogHelper.updateActiveTone(it.toString())
            } else {
                Toast.makeText(this, ReminderToneHelper.durationWarningText(), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        homeSavedInstanceState = savedInstanceState
        
        if (!UserPreferencesStore.isSignedUp(this)) {
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        applyHomeInsets()
        setupAnimatedHomeHeader()

        mgr = ReminderManager(this)
        planManager = PlanManager(this)
        healthDataManager = HealthDataManager(this)
        healthConnectManager = HealthConnectManager(this)
        healthDataManager.pruneHistoricalData()

        mgr.scheduleAllEnabled()
        ReminderToneHelper.preloadSystemNotificationTones(this)

        requestPermissionsLauncher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { _ ->
            firstLaunchPermissionFlow.onHealthConnectPermissionResult()
        }
        healthConnectSyncCoordinator = HealthConnectSyncCoordinator(
            this,
            healthConnectManager,
            healthDataManager,
            requestPermissionsLauncher
        )
        firstLaunchPermissionFlow = FirstLaunchPermissionFlow(
            this,
            healthConnectManager,
            requestPermissionsLauncher,
            healthConnectSyncCoordinator::activateAndSync
        )

        updateGreeting()
        updateDashboard()

        findViewById<TextView>(R.id.tvGreeting).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<View>(R.id.cardProfileAvatar).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<View>(R.id.cardWellnessScore).setOnClickListener {
            startActivity(Intent(this, WellnessScoreActivity::class.java))
        }

        findViewById<View>(R.id.cardDiet).setOnClickListener {
            startActivity(Intent(this, DietPlanActivity::class.java))
        }

        findViewById<View>(R.id.cardWorkout).setOnClickListener {
            startActivity(Intent(this, WorkoutPlanActivity::class.java))
        }

        findViewById<View>(R.id.cardWalking).setOnClickListener {
            startActivity(Intent(this, WalkingDataActivity::class.java))
        }

        findViewById<View>(R.id.cardSleep).setOnClickListener {
            startActivity(Intent(this, SleepTrackingActivity::class.java))
        }

        findViewById<View>(R.id.cardWeight).setOnClickListener {
            startActivity(Intent(this, WeightActivity::class.java))
        }

        findViewById<View>(R.id.cardCalories).setOnClickListener {
            startActivity(Intent(this, CaloriesActivity::class.java))
        }

        findViewById<View>(R.id.cardWater).setOnClickListener {
            showWaterAdjustDialog()
        }

        findViewById<View>(R.id.btnWaterMinus).setOnClickListener {
            adjustWaterToday(-0.25, showToast = false)
        }

        findViewById<View>(R.id.btnWaterPlus).setOnClickListener {
            adjustWaterToday(0.25, showToast = false)
        }

        findViewById<MaterialButton>(R.id.btnHealthSync).setOnClickListener {
            healthConnectSyncCoordinator.startManualSync()
        }

        findViewById<MaterialButton>(R.id.btnReminders).setOnClickListener { view ->
            val popup = PopupMenu(this, view, Gravity.TOP)
            popup.menu.add("Set Reminders")
            popup.menu.add("View Reminders")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Set Reminders" -> {
                        ReminderDialogHelper.showDialog(
                            this, mgr, null, view,
                            onTonePickerRequested = { currentUri ->
                                showToneSourcePicker(currentUri)
                            }
                        ) {
                            updateDashboard()
                        }
                    }
                    "View Reminders" -> startActivity(Intent(this, RemindersActivity::class.java))
                }
                true
            }
            popup.show()
        }

        findViewById<MaterialButton>(R.id.btnSyncLogs).setOnClickListener {
            showSyncLogDialog()
        }

        initializeFirstRunState()

        HealthSyncWorker.scheduleAutoSync(this)

        findViewById<View>(android.R.id.content).postDelayed({
            firstLaunchPermissionFlow.startIfNeeded()
        }, 300)

        findViewById<View>(android.R.id.content).postDelayed({
            showDeveloperOptionsWarningIfNeeded()
        }, 900)
    }

    private fun showDeveloperOptionsWarningIfNeeded() {
        if (isFinishing || isDestroyed || !DeveloperOptionsWarning.areEnabled(this)) return
        if (firstLaunchPermissionFlow.isActive) {
            findViewById<View>(android.R.id.content).postDelayed({
                showDeveloperOptionsWarningIfNeeded()
            }, 1000)
            return
        }
        showDeveloperOptionsWarning()
    }

    private fun showDeveloperOptionsWarning() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Security Notice: Developer Options Enabled")
            .setMessage(
                "Developer options are enabled on this device. Some options, including USB debugging and mock locations, can reduce device security or expose personal data when used with an untrusted computer or application.\n\n" +
                    "If you do not currently need these features, turn off USB debugging and other development settings you enabled, then disable Developer options in Android Settings. Routinely will remain available; this notice is informational."
            )
            .setPositiveButton("Understood", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        firstLaunchPermissionFlow.onNotificationPermissionResult(requestCode)
    }

    private fun initializeFirstRunState() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("is_first_run", true)) {
            // Ensure no legacy/mock health data exists
            getSharedPreferences("health_data_pref", MODE_PRIVATE)
                .edit()
                .clear()
                .putBoolean("needs_initial_permission_request", true)
                .apply()

            prefs.edit().putBoolean("is_first_run", false).apply()
        }
    }

    private fun applyHomeInsets() {
        val header = findViewById<View>(R.id.homeHeader)
        val headerBaseLeft = header.paddingLeft
        val headerBaseTop = header.paddingTop
        val headerBaseRight = header.paddingRight
        val headerBaseBottom = header.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(header) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.setPadding(
                headerBaseLeft + maxOf(systemBars.left, cutout.left),
                headerBaseTop + maxOf(systemBars.top, cutout.top),
                headerBaseRight + maxOf(systemBars.right, cutout.right),
                headerBaseBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(header)

        val scroll = findViewById<View>(R.id.homeScroll)
        val baseLeft = scroll.paddingLeft
        val baseTop = scroll.paddingTop
        val baseRight = scroll.paddingRight
        val baseBottom = scroll.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.setPadding(
                baseLeft + maxOf(systemBars.left, cutout.left),
                baseTop,
                baseRight + maxOf(systemBars.right, cutout.right),
                baseBottom + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(scroll)

        // Apply insets to header to prevent overlap with system status bar
        val rootView = findViewById<View>(android.R.id.content).parent as View
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            // Let the system handle the default behavior
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun setupAnimatedHomeHeader() {
        val header = findViewById<View>(R.id.homeHeader)
        headerBackground = AnimatedHeaderBackgroundDrawable.forTimeOfDay(this).also { drawable ->
            header.background = drawable
        }
    }

    private fun startHomeHeaderAnimationIfNeeded(savedInstanceState: Bundle?) {
        if (!HeaderAnimationStartupGate.shouldPlayOnHomeCreate(savedInstanceState)) {
            return
        }

        if (!ValueAnimator.areAnimatorsEnabled()) {
            findViewById<TextView>(R.id.tvGreetingLabel).alpha = 1f
            findViewById<TextView>(R.id.tvGreeting).alpha = 1f
            findViewById<TextView>(R.id.tvHeaderDate).alpha = 1f
            findViewById<View>(R.id.cardProfileAvatar).alpha = 1f
            return
        }
        
        // Hide views instantly to prepare for entrance sequence
        findViewById<TextView>(R.id.tvGreetingLabel).alpha = 0f
        findViewById<TextView>(R.id.tvGreeting).alpha = 0f
        findViewById<TextView>(R.id.tvHeaderDate).alpha = 0f
        findViewById<View>(R.id.cardProfileAvatar).alpha = 0f

        findViewById<View>(R.id.homeHeader).post {
            if (ValueAnimator.areAnimatorsEnabled()) {
                headerBackground?.start()
            }
            runPremiumEntranceAnimation()
        }
    }

    private fun runPremiumEntranceAnimation() {
        val tvGreetingLabel = findViewById<TextView>(R.id.tvGreetingLabel)
        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvHeaderDate = findViewById<TextView>(R.id.tvHeaderDate)
        val cardAvatar = findViewById<View>(R.id.cardProfileAvatar)

        // Convert DP to pixels for reliable motion
        val density = resources.displayMetrics.density
        val shiftPx = 24 * density

        // Reset states for animation
        tvGreetingLabel.translationY = -shiftPx * 0.35f
        tvGreeting.translationY = shiftPx * 0.35f
        tvHeaderDate.translationY = shiftPx * 0.25f
        cardAvatar.scaleX = 0.94f
        cardAvatar.scaleY = 0.94f

        val entranceDuration = 650L
        val entranceInterpolator = android.view.animation.DecelerateInterpolator(1.6f)

        tvGreetingLabel.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(entranceDuration)
            .setInterpolator(entranceInterpolator)
            .start()

        tvGreeting.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(100)
            .setDuration(entranceDuration)
            .setInterpolator(entranceInterpolator)
            .start()

        tvHeaderDate.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(180)
            .setDuration(entranceDuration)
            .setInterpolator(entranceInterpolator)
            .start()

        cardAvatar.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(120)
            .setDuration(entranceDuration)
            .setInterpolator(entranceInterpolator)
            .start()
    }

    private fun showSyncLogDialog() {
        val logs = SyncLogManager.getLogs(this)
        if (logs.isEmpty()) {
            Toast.makeText(this, "No sync logs recorded for today yet.", Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        logs.forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 16, 0, 16)
            }
            val timeTv = TextView(this@MainActivity).apply {
                text = entry.formatTime()
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.textSecondary))
            }
            val msgTv = TextView(this@MainActivity).apply {
                text = "${entry.source}: ${entry.message}"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@MainActivity, if (entry.isSuccess) R.color.textPrimary else android.R.color.holo_red_dark))
            }
            row.addView(timeTv)
            row.addView(msgTv)
            container.addView(row)
            
            // Divider
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                    setMargins(0, 8, 0, 8)
                }
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.m3_premium_stroke))
            }
            container.addView(divider)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Daily Sync History")
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            dataUpdatedReceiver,
            IntentFilter(WellnessWidget.ACTION_DATA_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Ensure the next background sync is scheduled without starting a foreground sync.
        HealthSyncWorker.scheduleAutoSync(this)

        updateGreeting()
        updateDashboard()

        firstLaunchPermissionFlow.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !entranceAnimationPlayed) {
            entranceAnimationPlayed = true
            startHomeHeaderAnimationIfNeeded(homeSavedInstanceState)
        } else if (hasFocus && ValueAnimator.areAnimatorsEnabled()) {
            headerBackground?.takeUnless { it.isRunning }?.start()
        }
    }

    override fun onPause() {
        headerBackground?.stop()
        super.onPause()
        runCatching { unregisterReceiver(dataUpdatedReceiver) }
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val name = UserPreferencesStore.getUserName(this)
        val greeting = when (hour) {
            in 0..11 -> "Good Morning!"
            in 12..16 -> "Good Afternoon!"
            in 17..20 -> "Good Evening!"
            else -> "Good Night!"
        }
        findViewById<TextView>(R.id.tvGreetingLabel).text = greeting
        findViewById<TextView>(R.id.tvGreeting).text = name

        val dateFormat = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault())
        findViewById<TextView>(R.id.tvHeaderDate).text = dateFormat.format(java.util.Date())

        val avatarId = UserPreferencesStore.getUserAvatarId(this)
        AvatarHelper.applyAvatar(findViewById(R.id.ivHeaderAvatar), avatarId)
    }


    private fun showWaterQuickAdd() {
        val amountsMl = (250..1000 step 250).toList()
        val options = amountsMl.map { "+$it ml" }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Add Water Intake")
            .setItems(options) { _, which ->
                val amount = amountsMl[which] / 1000.0
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                healthDataManager.addWaterIntake(today, amount)
                updateDashboard()
                Toast.makeText(this, "Added $amount L! 💧", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showWaterAdjustDialog() {
        val amountsMl = WaterIntakeSummary.adjustmentAmountsMl
        val options = amountsMl.map { amount ->
            val action = if (amount > 0) "Add" else "Remove"
            "$action ${kotlin.math.abs(amount)} ml"
        }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Adjust Water Intake")
            .setItems(options) { _, which ->
                val amount = amountsMl[which] / 1000.0
                val message = if (amount > 0) {
                    "Added %.0f ml".format(amount * 1000)
                } else {
                    "Removed %.0f ml".format(kotlin.math.abs(amount) * 1000)
                }
                adjustWaterToday(amount, showToast = true, message = message)
            }
            .show()
    }

    private var accumulatedWaterDeltaLiters = 0.0
    private val waterDeltaHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val waterDeltaResetRunnable = Runnable {
        val badge = findViewById<TextView>(R.id.tvWaterDeltaBadge) ?: return@Runnable
        badge.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                badge.visibility = View.GONE
                badge.alpha = 1f
                accumulatedWaterDeltaLiters = 0.0
            }
            .start()
    }

    private fun adjustWaterToday(amount: Double, showToast: Boolean = false, message: String = "") {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val updated = healthDataManager.adjustWaterIntake(today, amount)
        HapticHelper.triggerThump(this)
        updateDashboard()

        if (showToast) {
            val msg = if (message.isNotBlank()) message else if (amount >= 0) "Added ${formatWaterLiters(amount)} L" else "Removed ${formatWaterLiters(kotlin.math.abs(amount))} L"
            Toast.makeText(this, "$msg - ${formatWaterLiters(updated)} L today", Toast.LENGTH_SHORT).show()
        } else {
            accumulatedWaterDeltaLiters += amount
            waterDeltaHandler.removeCallbacks(waterDeltaResetRunnable)

            val badge = findViewById<TextView>(R.id.tvWaterDeltaBadge)
            if (badge != null) {
                val mlVal = (accumulatedWaterDeltaLiters * 1000).toInt()
                val prefix = if (mlVal > 0) "+" else ""
                badge.text = "$prefix$mlVal ml"
                badge.setTextColor(if (mlVal >= 0) 0xFF0288D1.toInt() else 0xFFE53935.toInt())
                badge.alpha = 1f
                badge.visibility = View.VISIBLE
            }

            waterDeltaHandler.postDelayed(waterDeltaResetRunnable, 1500)
        }
    }

    private fun formatWaterLiters(amount: Double): String =
        String.format(java.util.Locale.US, "%.2f", amount).trimEnd('0').trimEnd('.')

    private fun updateDashboard() {
        HomeDashboardUpdater(this, mgr, planManager, healthDataManager).update()
    }

    private fun showToneSourcePicker(currentUri: String?) {
        val options = arrayOf("System notification tones", "Audio file from device")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Pick Notification Tone")
            .setItems(options) { _, which ->
                if (which == 0) {
                    showSystemNotificationTonePicker(currentUri)
                } else {
                    fileToneLauncher.launch(arrayOf("audio/*"))
                }
            }
            .show()
    }

    private fun showSystemNotificationTonePicker(currentUri: String?) {
        ReminderToneHelper.cachedSystemNotificationTones()?.let {
            showSystemNotificationToneList(it, currentUri)
            return
        }

        val loadingDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Loading notification tones")
            .setMessage("Preparing available short tones...")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            val tones = withContext(Dispatchers.IO) {
                ReminderToneHelper.eligibleSystemNotificationTones(this@MainActivity)
            }
            if (!isFinishing && !isDestroyed) {
                loadingDialog.dismiss()
                showSystemNotificationToneList(tones, currentUri)
            }
        }
    }

    private fun showSystemNotificationToneList(tones: List<ReminderToneHelper.ToneOption>, currentUri: String?) {
        if (tones.isEmpty()) {
            Toast.makeText(this, "No notification tones up to 6 seconds were found.", Toast.LENGTH_LONG).show()
            return
        }

        val currentIndex = tones.indexOfFirst { it.uri?.toString() == currentUri }.coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Select Notification Tone")
            .setSingleChoiceItems(tones.map { it.title }.toTypedArray(), currentIndex) { dialog, which ->
                ReminderDialogHelper.updateActiveTone(tones[which].uri?.toString())
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
