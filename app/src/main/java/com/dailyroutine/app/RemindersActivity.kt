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

    private val systemToneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.getParcelableExtra<android.net.Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ReminderDialogHelper.updateActiveTone(uri?.toString())
        }
    }

    private val fileToneLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ReminderDialogHelper.updateActiveTone(it.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminders)

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
            banner.text = "Keep your routine on track 💪"
            progressText.text = "No reminders set"
            return
        }
        val doneCount = RoutineProgressStore.getDoneCount(this)
        val total = list.size
        banner.text = when {
            doneCount == 0 -> "Let's get started! 🚀"
            doneCount < total -> "Great job! Keep going! ✨"
            else -> "Perfect day! All done! 🎉"
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
        val options = arrayOf("System Ringtones", "File Manager (MP3/Audio)")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Pick Notification Tone")
            .setItems(options) { _, which ->
                if (which == 0) {
                    val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Tone")
                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri?.let { android.net.Uri.parse(it) })
                    }
                    systemToneLauncher.launch(intent)
                } else {
                    fileToneLauncher.launch(arrayOf("audio/*"))
                }
            }
            .show()
    }

    companion object {
        private const val RC_NOTIF = 100
    }
}
