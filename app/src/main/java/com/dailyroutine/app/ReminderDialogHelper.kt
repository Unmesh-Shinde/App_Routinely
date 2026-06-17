package com.dailyroutine.app

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.snackbar.Snackbar

object ReminderDialogHelper {

    private var activeToneUpdater: ((String?) -> Unit)? = null

    fun updateActiveTone(uri: String?) {
        activeToneUpdater?.invoke(uri)
    }

    fun showDialog(
        context: Context,
        mgr: ReminderManager,
        existing: Reminder?,
        anchorView: View? = null,
        onTonePickerRequested: ((currentUri: String?) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val v = LayoutInflater.from(context).inflate(R.layout.dialog_add_reminder, null)

        val spinnerType       = v.findViewById<Spinner>(R.id.spinnerType)
        val etTitle           = v.findViewById<EditText>(R.id.etTitle)
        val rgMode            = v.findViewById<RadioGroup>(R.id.rgMode)
        val rbFixed           = v.findViewById<RadioButton>(R.id.rbFixedTime)
        val rbInterval        = v.findViewById<RadioButton>(R.id.rbInterval)
        val llFixed           = v.findViewById<View>(R.id.llFixed)
        val llInterval        = v.findViewById<View>(R.id.llInterval)
        val tvTime            = v.findViewById<TextView>(R.id.tvTime)
        val btnPickTime       = v.findViewById<Button>(R.id.btnPickTime)
        val etIntervalMin     = v.findViewById<EditText>(R.id.etIntervalMin)

        val llMealDetails     = v.findViewById<View>(R.id.llMealDetails)
        val etDishType        = v.findViewById<EditText>(R.id.etDishType)
        val etIngredients     = v.findViewById<EditText>(R.id.etIngredients)

        val spinnerFrequency  = v.findViewById<Spinner>(R.id.spinnerFrequency)
        val llRepeatDays      = v.findViewById<View>(R.id.llRepeatDays)
        val llMonthlyDay      = v.findViewById<View>(R.id.llMonthlyDay)
        val etDayOfMonth      = v.findViewById<EditText>(R.id.etDayOfMonth)

        val tvToneName        = v.findViewById<TextView>(R.id.tvToneName)
        val btnPickTone       = v.findViewById<View>(R.id.btnPickTone)

        var selectedToneUri: String? = existing?.soundUri

        val cbDays = listOf(
            v.findViewById<SwitchCompat>(R.id.swMon),
            v.findViewById<SwitchCompat>(R.id.swTue),
            v.findViewById<SwitchCompat>(R.id.swWed),
            v.findViewById<SwitchCompat>(R.id.swThu),
            v.findViewById<SwitchCompat>(R.id.swFri),
            v.findViewById<SwitchCompat>(R.id.swSat),
            v.findViewById<SwitchCompat>(R.id.swSun)
        )

        // Frequency Setup
        val frequencies = arrayOf("Daily (Specific Days)", "Monthly (Same Date)")
        spinnerFrequency.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, frequencies)
        spinnerFrequency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, vw: View?, pos: Int, id: Long) {
                llRepeatDays.visibility = if (pos == 0) View.VISIBLE else View.GONE
                llMonthlyDay.visibility = if (pos == 1) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val types = ReminderType.values().filter { it != ReminderType.MEAL && it != ReminderType.EXERCISE }
        val adapter = ArrayAdapter(
            context, R.layout.spinner_item,
            types.map { "${it.emoji}  ${it.label}" }.toTypedArray()
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = adapter

        var selHour = existing?.hour ?: 8
        var selMin  = existing?.minute ?: 0

        fun refreshTimeLabel() {
            val h = if (selHour == 0 || selHour == 12) 12 else selHour % 12
            val amPm = if (selHour < 12) "AM" else "PM"
            tvTime.text = "%02d:%02d %s".format(h, selMin, amPm)
        }
        refreshTimeLabel()

        fun updateToneLabel(uri: String?) {
            selectedToneUri = uri
            if (uri == null) {
                tvToneName.text = "Default Notification Tone"
            } else {
                try {
                    val rUri = android.net.Uri.parse(uri)
                    val ringtone = android.media.RingtoneManager.getRingtone(context, rUri)
                    tvToneName.text = ringtone?.getTitle(context) ?: "Custom Audio File"
                } catch (e: Exception) {
                    tvToneName.text = "Custom Audio File"
                }
            }
        }
        updateToneLabel(selectedToneUri)
        activeToneUpdater = ::updateToneLabel

        btnPickTone.setOnClickListener {
            onTonePickerRequested?.invoke(selectedToneUri)
        }

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
            llMealDetails.visibility = if (r.type == ReminderType.MEAL) View.VISIBLE else View.GONE
            
            spinnerFrequency.setSelection(if (r.isMonthly) 1 else 0)
            etDayOfMonth.setText(r.dayOfMonth.toString())
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
            TimePickerDialog(context, { _, h, m ->
                selHour = h
                selMin = m
                refreshTimeLabel()
            }, selHour, selMin, false).show()
        }

        fun syncVisibility() {
            val isFixed = rbFixed.isChecked
            llFixed.visibility = if (isFixed) View.VISIBLE else View.GONE
            llInterval.visibility = if (!isFixed) View.VISIBLE else View.GONE
        }
        syncVisibility()
        rgMode.setOnCheckedChangeListener { _, _ -> syncVisibility() }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) "Add Reminder" else "Edit Reminder")
            .setView(v)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnDismissListener {
            activeToneUpdater = null
        }
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
                Toast.makeText(context, "Select at least one day", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isMonthly = spinnerFrequency.selectedItemPosition == 1
            val dayOfMonth = etDayOfMonth.text.toString().toIntOrNull()?.coerceIn(1, 31) ?: 1

            // ── DUPLICATE CHECK ──
            if (!isInterval) {
                val existingList = mgr.getAllReminders()
                val isDuplicate = existingList.any { r ->
                    r.id != (existing?.id ?: -1) &&
                    r.type == selectedType &&
                    r.hour == selHour &&
                    r.minute == selMin &&
                    if (isMonthly) {
                        r.isMonthly && r.dayOfMonth == dayOfMonth
                    } else {
                        !r.isMonthly && r.repeatDays.any { it in days }
                    }
                }

                if (isDuplicate) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                        .setTitle("Duplicate Reminder")
                        .setMessage("A reminder of this category is already set for this exact time and day. Do you want to add it anyway?")
                        .setPositiveButton("Add Anyway") { _, _ ->
                            saveAndFinish(mgr, existing, title, selectedType, selHour, selMin, isInterval, intervalMin, days, dishType, ingredients, selectedToneUri, isMonthly, dayOfMonth, onComplete, dialog, anchorView)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return@setOnClickListener
                }
            }

            saveAndFinish(mgr, existing, title, selectedType, selHour, selMin, isInterval, intervalMin, days, dishType, ingredients, selectedToneUri, isMonthly, dayOfMonth, onComplete, dialog, anchorView)
        }
    }

    private fun saveAndFinish(
        mgr: ReminderManager,
        existing: Reminder?,
        title: String,
        selectedType: ReminderType,
        selHour: Int,
        selMin: Int,
        isInterval: Boolean,
        intervalMin: Int,
        days: List<Int>,
        dishType: String,
        ingredients: String,
        selectedToneUri: String?,
        isMonthly: Boolean,
        dayOfMonth: Int,
        onComplete: (() -> Unit)?,
        dialog: android.content.DialogInterface,
        anchorView: View?
    ) {
        mgr.saveReminder(
            Reminder(
                id = existing?.id ?: System.currentTimeMillis().toInt(),
                title = title,
                type = selectedType,
                hour = selHour,
                minute = selMin,
                isIntervalBased = isInterval,
                intervalMinutes = intervalMin,
                repeatDays = if (isMonthly) emptyList() else days,
                isEnabled = existing?.isEnabled ?: true,
                dishType = if (selectedType == ReminderType.MEAL) dishType else "",
                ingredients = if (selectedType == ReminderType.MEAL) ingredients else "",
                soundUri = selectedToneUri,
                isMonthly = isMonthly,
                dayOfMonth = dayOfMonth
            )
        )

        onComplete?.invoke()
        dialog.dismiss()
        anchorView?.let {
            Snackbar.make(
                it,
                if (existing == null) "Reminder added 🎉" else "Reminder updated",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
}
