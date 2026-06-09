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

    fun showDialog(
        context: Context,
        mgr: ReminderManager,
        existing: Reminder?,
        anchorView: View? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val v = LayoutInflater.from(context).inflate(R.layout.dialog_add_reminder, null)

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
            context, android.R.layout.simple_spinner_dropdown_item,
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
            cbDays.forEachIndexed { i, sw -> sw.isChecked = (i + 1) in r.repeatDays }
        } ?: cbDays.forEach { it.isChecked = true }

        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, vw: View?, pos: Int, id: Long) {
                val type = types[pos]
                if (existing == null && etTitle.text.isNullOrBlank()) {
                    etTitle.setText(type.defaultTitle)
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        btnPickTime.setOnClickListener {
            TimePickerDialog(context, { _, h, m ->
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

        val dialog = AlertDialog.Builder(context)
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

            if (!isInterval && days.isEmpty()) {
                Toast.makeText(context, "Select at least one day", Toast.LENGTH_SHORT).show()
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
                    isEnabled = existing?.isEnabled ?: true
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
}
