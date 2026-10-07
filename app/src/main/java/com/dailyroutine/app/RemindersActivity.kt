package com.dailyroutine.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemindersActivity : AppCompatActivity(), ReminderAdapter.OnReminderListener {

    private lateinit var mgr: ReminderManager
    private lateinit var adapter: ReminderAdapter
    private lateinit var rv: RecyclerView
    private lateinit var emptyGroup: View
    private lateinit var fab: ExtendedFloatingActionButton

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
        setContentView(R.layout.activity_reminders)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
        toolbar.setNavigationOnClickListener { finish() }

        mgr = ReminderManager(this)
        adapter = ReminderAdapter(this)

        rv = findViewById(R.id.recyclerView)
        emptyGroup = findViewById(R.id.emptyGroup)
        fab = findViewById(R.id.fab)

        rv.adapter = adapter
        InsetHelper.applyBottomPadding(rv)
        InsetHelper.applyBottomPadding(emptyGroup)
        InsetHelper.applyBottomMargin(fab)
        ReminderToneHelper.preloadSystemNotificationTones(this)

        fab.setOnClickListener { showDialog(null) }

        requestNotificationPermission()
        requestExactAlarmPermission()
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

    override fun onSelectionChanged(count: Int) {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        if (count > 0) {
            toolbar.title = "$count selected"
            val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            toolbar.setBackgroundColor(if (isNightMode) Color.parseColor("#1A237E") else Color.parseColor("#E8EAF6"))

            val typedValue = TypedValue()
            theme.resolveAttribute(androidx.appcompat.R.attr.actionModeCloseDrawable, typedValue, true)
            if (typedValue.resourceId != 0) {
                toolbar.setNavigationIcon(typedValue.resourceId)
            }
            toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
            toolbar.setNavigationOnClickListener { adapter.clearSelection() }

            if (toolbar.menu.findItem(1) == null) {
                toolbar.menu.add(Menu.NONE, 1, Menu.NONE, "Delete")
                    .setIcon(android.R.drawable.ic_menu_delete)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                toolbar.setOnMenuItemClickListener {
                    if (it.itemId == 1) {
                        deleteSelectedReminders()
                        true
                    } else false
                }
            }
        } else {
            toolbar.title = "Reminders"
            toolbar.setBackgroundColor(Color.TRANSPARENT)

            val typedValue = TypedValue()
            theme.resolveAttribute(androidx.appcompat.R.attr.homeAsUpIndicator, typedValue, true)
            toolbar.setNavigationIcon(typedValue.resourceId)
            toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
            toolbar.setNavigationOnClickListener { finish() }

            toolbar.menu.clear()
        }
    }

    private fun deleteSelectedReminders() {
        val selectedIds = adapter.getSelectedIds()
        val reminders = mgr.getAllReminders().filter { selectedIds.contains(it.id) }

        AlertDialog.Builder(this)
            .setTitle("Delete Reminders?")
            .setMessage("Remove ${reminders.size} selected reminders?")
            .setPositiveButton("Delete") { _, _ ->
                reminders.forEach { reminder ->
                    mgr.deleteReminder(reminder)
                }
                adapter.clearSelection()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refresh() {
        val list = mgr.getAllReminders()
        adapter.setReminders(list)
        emptyGroup.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
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
                ReminderToneHelper.eligibleSystemNotificationTones(this@RemindersActivity)
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

    companion object {
        private const val RC_NOTIF = 100
    }
}
