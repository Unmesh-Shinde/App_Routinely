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
