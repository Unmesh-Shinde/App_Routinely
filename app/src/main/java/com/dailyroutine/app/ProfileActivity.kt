package com.dailyroutine.app

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val etName = findViewById<EditText>(R.id.etProfileName)
        val etAge = findViewById<EditText>(R.id.etProfileAge)
        val etHeight = findViewById<EditText>(R.id.etProfileHeight)
        val etWeight = findViewById<EditText>(R.id.etProfileWeight)
        val spinnerGender = findViewById<Spinner>(R.id.spinnerGender)
        val btnBmi = findViewById<MaterialButton>(R.id.btnProfileBmi)
        val btnBmr = findViewById<MaterialButton>(R.id.btnProfileBmr)
        val tvMetricsHint = findViewById<TextView>(R.id.tvProfileMetricsHint)
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)
        val btnExport = findViewById<Button>(R.id.btnExportReport)

        val genders = arrayOf("Male", "Female", "Other")
        spinnerGender.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genders)

        // Load existing data
        etName.setText(UserPreferencesStore.getUserName(this))
        etAge.setText(UserPreferencesStore.getUserAge(this).toString())
        etHeight.setText(UserPreferencesStore.getUserHeight(this).toString())
        etWeight.setText("%.1f".format(UserPreferencesStore.getUserWeight(this)))
        spinnerGender.setSelection(genders.indexOf(UserPreferencesStore.getUserGender(this)).coerceAtLeast(0))

        fun updateLiveMetrics() {
            val age = etAge.text.toString().toIntOrNull() ?: 0
            val height = etHeight.text.toString().toIntOrNull() ?: 0
            val weight = etWeight.text.toString().toDoubleOrNull() ?: 0.0
            val gender = spinnerGender.selectedItem?.toString() ?: "Male"
            val metrics = ProfileHealthMetricsCalculator.calculate(age, height, weight, gender)
            if (metrics == null) {
                btnBmi.text = "BMI --"
                btnBmr.text = "BMR --"
                tvMetricsHint.text = "Add valid age, height, weight, and gender to calculate BMI/BMR."
                return
            }
            btnBmi.text = "BMI\n%.1f • ${metrics.bmiCategory}".format(metrics.bmi)
            btnBmr.text = "BMR\n${metrics.bmr} kcal/day"
            tvMetricsHint.text = "Ideal weight %.1f kg • Ideal intake ${metrics.idealCalories} kcal/day".format(metrics.idealWeightKg)
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateLiveMetrics()
            override fun afterTextChanged(s: Editable?) {}
        }
        etAge.addTextChangedListener(watcher)
        etHeight.addTextChangedListener(watcher)
        etWeight.addTextChangedListener(watcher)
        spinnerGender.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateLiveMetrics()
            override fun onNothingSelected(parent: AdapterView<*>?) = updateLiveMetrics()
        }
        updateLiveMetrics()

        btnExport.setOnClickListener {
            WellnessReportManager.generateAndShareReport(this)
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val age = etAge.text.toString().toIntOrNull() ?: 25
            val height = etHeight.text.toString().toIntOrNull() ?: 170
            val weight = etWeight.text.toString().toDoubleOrNull() ?: UserPreferencesStore.getUserWeight(this)
            val gender = spinnerGender.selectedItem.toString()

            if (name.isEmpty()) {
                etName.error = "Name is required"
                return@setOnClickListener
            }

            UserPreferencesStore.setUserName(this, name)
            UserPreferencesStore.setUserAge(this, age)
            UserPreferencesStore.setUserHeight(this, height)
            UserPreferencesStore.setUserWeight(this, weight)
            UserPreferencesStore.setUserGender(this, gender)

            if (weight > 0.0) {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                HealthDataManager(this).saveWeight(today, weight)
            }

            Toast.makeText(this, "Profile updated successfully! ✅", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
