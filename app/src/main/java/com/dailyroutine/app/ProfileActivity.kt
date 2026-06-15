package com.dailyroutine.app

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var healthDataManager: HealthDataManager
    private var calculatedBmr: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        healthDataManager = HealthDataManager(this)

        val etName = findViewById<EditText>(R.id.etProfileName)
        val etAge = findViewById<EditText>(R.id.etProfileAge)
        val etHeight = findViewById<EditText>(R.id.etProfileHeight)
        val spinnerGender = findViewById<Spinner>(R.id.spinnerGender)
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)
        val cardAnalytics = findViewById<View>(R.id.cardAnalytics)
        val tvBmi = findViewById<TextView>(R.id.tvBmiResult)
        val tvBmr = findViewById<TextView>(R.id.tvBmrResult)
        val btnApply = findViewById<Button>(R.id.btnApplyGoal)

        val genders = arrayOf("Male", "Female", "Other")
        spinnerGender.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genders)

        // Load existing
        etName.setText(UserPreferencesStore.getUserName(this))
        etAge.setText(UserPreferencesStore.getUserAge(this).toString())
        etHeight.setText(UserPreferencesStore.getUserHeight(this).toString())
        spinnerGender.setSelection(genders.indexOf(UserPreferencesStore.getUserGender(this)).coerceAtLeast(0))

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val age = etAge.text.toString().toIntOrNull() ?: 25
            val height = etHeight.text.toString().toIntOrNull() ?: 170
            val gender = spinnerGender.selectedItem.toString()

            UserPreferencesStore.setUserName(this, name)
            UserPreferencesStore.setUserAge(this, age)
            UserPreferencesStore.setUserHeight(this, height)
            UserPreferencesStore.setUserGender(this, gender)

            // Get weight for analytics
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val weight = healthDataManager.getWeight(today).let { if (it > 0) it else 70.0 }

            // 1. Calculate BMI
            val heightM = height / 100.0
            val bmi = weight / (heightM * heightM)
            val bmiStatus = when {
                bmi < 18.5 -> "Underweight"
                bmi < 25 -> "Normal"
                bmi < 30 -> "Overweight"
                else -> "Obese"
            }
            tvBmi.text = "Your BMI: %.1f ($bmiStatus)".format(bmi)

            // 2. Calculate BMR (Mifflin-St Jeor)
            calculatedBmr = if (gender == "Male") {
                ((10 * weight) + (6.25 * height) - (5 * age) + 5).toInt()
            } else {
                ((10 * weight) + (6.25 * height) - (5 * age) - 161).toInt()
            }
            // Add a small multiplier (1.2x) for light activity
            calculatedBmr = (calculatedBmr * 1.2).toInt()
            tvBmr.text = "Recommended Daily: $calculatedBmr kcal"

            cardAnalytics.visibility = View.VISIBLE
            Toast.makeText(this, "Profile Updated & Analytics Calculated! 📊", Toast.LENGTH_SHORT).show()
        }

        btnApply.setOnClickListener {
            if (calculatedBmr > 0) {
                healthDataManager.setDailyCalorieGoal(calculatedBmr)
                Toast.makeText(this, "Calorie Goal updated to $calculatedBmr kcal! 🎯", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
