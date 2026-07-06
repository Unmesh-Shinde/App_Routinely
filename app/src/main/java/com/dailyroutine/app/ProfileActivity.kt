package com.dailyroutine.app

import android.os.Bundle
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomPadding(findViewById(R.id.profileScroll))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val etName = findViewById<EditText>(R.id.etProfileName)
        val etAge = findViewById<EditText>(R.id.etProfileAge)
        val etHeight = findViewById<EditText>(R.id.etProfileHeight)
        val etWeight = findViewById<EditText>(R.id.etProfileWeight)
        val spinnerGender = findViewById<Spinner>(R.id.spinnerGender)
        val tvBmiValue = findViewById<TextView>(R.id.tvProfileBmiValue)
        val tvBmiCategory = findViewById<TextView>(R.id.tvProfileBmiCategory)
        val tvBmrValue = findViewById<TextView>(R.id.tvProfileBmrValue)
        val tvBmrCategory = findViewById<TextView>(R.id.tvProfileBmrCategory)
        val tvIdealWeight = findViewById<TextView>(R.id.tvProfileIdealWeight)
        val tvIdealCalories = findViewById<TextView>(R.id.tvProfileIdealCalories)
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
                tvBmiValue.text = "--"
                tvBmiCategory.text = "Add details"
                tvBmrValue.text = "--"
                tvBmrCategory.text = "kcal/day"
                tvIdealWeight.text = "Ideal weight updates after details"
                tvIdealCalories.text = "Ideal intake updates after details"
                return
            }
            tvBmiValue.text = "%.1f".format(metrics.bmi)
            tvBmiCategory.text = metrics.bmiCategory
            tvBmrValue.text = metrics.bmr.toString()
            tvBmrCategory.text = "kcal/day"
            tvIdealWeight.text = "Ideal weight: %.1f kg".format(metrics.idealWeightKg)
            tvIdealCalories.text = "Ideal intake: ${metrics.idealCalories} kcal/day"
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

        findViewById<View>(R.id.btnProfileSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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

            Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
