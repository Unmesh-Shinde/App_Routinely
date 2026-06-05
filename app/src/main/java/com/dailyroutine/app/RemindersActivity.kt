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

class RemindersActivity : AppCompatActivity(), ReminderAdapter.OnReminderListener {

    private lateinit var mgr: ReminderManager
    private lateinit var adapter: ReminderAdapter
    private lateinit var rv: RecyclerView
    private lateinit var emptyGroup: View
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var banner: TextView
    private lateinit var progressText: TextView

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
        val list = mgr.getAllReminders().filter { !it.isHidden }
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
            mgr.saveReminder(Reminder(title = "Morning Stretch", type = ReminderType.EXERCISE, hour = 7, minute = 30))
            mgr.saveReminder(Reminder(title = "Healthy Breakfast", type = ReminderType.MEAL, hour = 8, minute = 0))
            mgr.saveReminder(Reminder(title = "Hydration Break", type = ReminderType.WATER, isIntervalBased = true, intervalMinutes = 120))
            prefs.edit().putBoolean("is_first_run", false).apply()
            refresh()
        }
    }

    private fun showDialog(existing: Reminder?) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_add_reminder, null)

        val spinnerType   = v.findViewById<Spinner>(R.id.spinnerType)
        val etTitle       = v.findViewById<EditText>(R.id.etTitle)
        val rgMode        = v.findViewById<RadioGroup>(R.id.rgMode)
        val rbFixed       = v.findViewById<RadioButton>(R.id.rbFixedTime)
        val rbInterval    = v.findViewById<RadioButton>(R.id.rbInterval)
        val llFixed       = v.findViewById<View>(R.id.llFixed)
        val llInterval    = v.findViewById<View>(R.id.llInterval)
        val tvTime        = v.findViewById<TextView>(R.id.tvTime)
        val btnPickTime   = v.findViewById<Button>(R.id.btnPickTime)
        val etIntervalMin = v.findViewById<EditText>(R.id.etIntervalMin)

        val llMealDetails = v.findViewById<View>(R.id.llMealDetails)
        val etDishType    = v.findViewById<EditText>(R.id.etDishType)
        val etIngredients = v.findViewById<EditText>(R.id.etIngredients)

        val cbDays = listOf(
            v.findViewById<SwitchCompat>(R.id.swMon),
            v.findViewById<SwitchCompat>(R.id.swTue),
            v.findViewById<SwitchCompat>(R.id.swWed),
            v.findViewById<SwitchCompat>(R.id.swThu),
            v.findViewById<SwitchCompat>(R.id.swFri),
            v.findViewById<SwitchCompat>(R.id.swSat),
            v.findViewById<SwitchCompat>(R.id.swSun)
        )

        val types = ReminderType.values()
        spinnerType.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            types.map { "${it.emoji}  ${it.label}" }.toTypedArray()
        )

        var selHour = existing?.hour ?: 8
        var selMin  = existing?.minute ?: 0

        fun refreshTimeLabel() { tvTime.text = "%02d:%02d".format(selHour, selMin) }
        refreshTimeLabel()

        existing?.let { r ->
            spinnerType.setSelection(types.indexOf(r.type).coerceAtLeast(0))
            etTitle.setText(r.title)
            if (r.isIntervalBased) {
                rbInterval.isChecked = true
                etIntervalMin.setText(r.intervalMinutes.toString())
            }
            etDishType.setText(r.dishType)
            etIngredients.setText(r.ingredients)
            cbDays.forEachIndexed { i, sw -> sw.isChecked = (i + 1) in r.repeatDays }
        } ?: cbDays.forEach { it.isChecked = true }

        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, vw: View?, pos: Int, id: Long) {
                val type = types[pos]
                if (existing == null && etTitle.text.isNullOrBlank()) {
                    etTitle.setText(type.defaultTitle)
                }
                llMealDetails.visibility = if (type == ReminderType.MEAL) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        btnPickTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                selHour = h
                selMin = m
                refreshTimeLabel()
            }, selHour, selMin, true).show()
        }

        fun syncVisibility() {
            val isFixed = rbFixed.isChecked
            llFixed.visibility = if (isFixed) View.VISIBLE else View.GONE
            llInterval.visibility = if (!isFixed) View.VISIBLE else View.GONE
        }
        syncVisibility()
        rgMode.setOnCheckedChangeListener { _, _ -> syncVisibility() }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add Reminder" else "Edit Reminder")
            .setView(v)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = etTitle.text.toString().trim()
            if (title.isEmpty()) {
                etTitle.error = "Required"
                return@setOnClickListener
            }

            val selectedType = types[spinnerType.selectedItemPosition]
            val isInterval = rbInterval.isChecked
            val intervalMin = etIntervalMin.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 60
            val days = cbDays.mapIndexedNotNull { i, sw -> if (sw.isChecked) i + 1 else null }
            val dishType = etDishType.text.toString().trim()
            val ingredients = etIngredients.text.toString().trim()

            if (!isInterval && days.isEmpty()) {
                Toast.makeText(this, "Select at least one day", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            mgr.saveReminder(
                Reminder(
                    id = existing?.id ?: System.currentTimeMillis().toInt(),
                    title = title,
                    type = selectedType,
                    hour = selHour,
                    minute = selMin,
                    isIntervalBased = isInterval,
                    intervalMinutes = intervalMin,
                    repeatDays = days,
                    isEnabled = existing?.isEnabled ?: true,
                    dishType = if (selectedType == ReminderType.MEAL) dishType else "",
                    ingredients = if (selectedType == ReminderType.MEAL) ingredients else ""
                )
            )

            refresh()
            dialog.dismiss()
            Snackbar.make(
                rv,
                if (existing == null) "Reminder added 🎉" else "Reminder updated",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        private const val RC_NOTIF = 100
    }
}
