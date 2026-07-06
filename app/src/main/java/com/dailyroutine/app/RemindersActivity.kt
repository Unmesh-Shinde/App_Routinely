package com.dailyroutine.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.util.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts

class RemindersActivity : AppCompatActivity(), ReminderAdapter.OnReminderListener {

    private lateinit var mgr: ReminderManager
    private lateinit var adapter: ReminderAdapter
    private lateinit var rv: RecyclerView
    private lateinit var emptyGroup: View
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var banner: TextView
    private lateinit var progressText: TextView

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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminders)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        mgr = ReminderManager(this)
        adapter = ReminderAdapter(this)

        rv = findViewById(R.id.recyclerView)
        emptyGroup = findViewById(R.id.emptyGroup)
        fab = findViewById(R.id.fab)
        banner = findViewById(R.id.tvBannerLeft)
        progressText = findViewById(R.id.tvProgress)

        rv.adapter = adapter
        InsetHelper.applyBottomPadding(rv)
        InsetHelper.applyBottomPadding(emptyGroup)
        InsetHelper.applyBottomMargin(fab)

        fab.setOnClickListener { showDialog(null) }

        requestNotificationPermission()
        requestExactAlarmPermission()
        addDefaultsOnFirstRun()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onToggle(reminder: Reminder) {
        mgr.toggleReminder(reminder)
        refresh()
    }

    override fun onEdit(reminder: Reminder) {
        showDialog(reminder)
    }

    override fun onDelete(reminder: Reminder) {
        AlertDialog.Builder(this)
            .setTitle("Delete Reminder?")
            .setMessage("Are you sure you want to remove '${reminder.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                mgr.deleteReminder(reminder)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refresh() {
        val list = mgr.getAllReminders()
        adapter.setReminders(list)
        emptyGroup.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        updateBanner(list)
    }

    private fun updateBanner(list: List<Reminder>) {
        if (list.isEmpty()) {
            banner.text = "Keep your routine on track"
            progressText.text = "No reminders set"
            return
        }
        val doneCount = RoutineProgressStore.getDoneCount(this)
        val total = list.size
        banner.text = when {
            doneCount == 0 -> "Let's get started!"
            doneCount < total -> "Great job! Keep going!"
            else -> "Perfect day! All done!"
        }
        progressText.text = "Today's progress: $doneCount/$total completed"
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), RC_NOTIF)
            }
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }

    private fun addDefaultsOnFirstRun() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("is_first_run", true)) {
            mgr.saveReminder(Reminder(title = "Drink Water", type = ReminderType.HYDRATION, isIntervalBased = true, intervalMinutes = 120))
            mgr.saveReminder(Reminder(title = "Meditation", type = ReminderType.MEDITATION, hour = 8, minute = 0))
            prefs.edit().putBoolean("is_first_run", false).apply()
            refresh()
        }
    }

    private fun showDialog(existing: Reminder?) {
        ReminderDialogHelper.showDialog(
            this, mgr, existing, rv,
            onTonePickerRequested = { currentUri ->
                showToneSourcePicker(currentUri)
            }
        ) {
            refresh()
        }
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
        val tones = ReminderToneHelper.eligibleSystemNotificationTones(this)
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

    companion object {
        private const val RC_NOTIF = 100
    }
}
