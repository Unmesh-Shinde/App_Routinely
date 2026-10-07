package com.dailyroutine.app

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomPadding(findViewById(R.id.profileScroll))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
        toolbar.setNavigationOnClickListener { finish() }

        val etName = findViewById<EditText>(R.id.etProfileName)
        val spinnerAge = findViewById<Spinner>(R.id.spinnerProfileAge)
        val spinnerFeet = findViewById<Spinner>(R.id.spinnerProfileFeet)
        val spinnerInches = findViewById<Spinner>(R.id.spinnerProfileInches)
        val etWeight = findViewById<EditText>(R.id.etProfileWeight)
        val spinnerGender = findViewById<Spinner>(R.id.spinnerGender)
        val spinnerWeightGoal = findViewById<Spinner>(R.id.spinnerWeightGoal)
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

        val weightGoals = arrayOf("Maintain Weight", "Lose Weight", "Gain Weight")
        spinnerWeightGoal.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weightGoals)

        // Age Options (15-75)
        val ageList = (15..75).map { it.toString() }
        spinnerAge.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ageList)

        // Height: Feet (3-8)
        val feetList = (3..8).map { "$it ft" }
        spinnerFeet.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, feetList)

        // Height: Inches (0-11.5 with 0.5 steps)
        val inchesList = mutableListOf<String>()
        var inch = 0.0
        while (inch < 12.0) {
            inchesList.add(if (inch % 1.0 == 0.0) "${inch.toInt()} in" else "$inch in")
            inch += 0.5
        }
        spinnerInches.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, inchesList)

        // Load existing data
        etName.setText(UserPreferencesStore.getUserName(this))
        
        val savedAge = UserPreferencesStore.getUserAge(this)
        spinnerAge.setSelection(ageList.indexOf(savedAge.toString()).coerceAtLeast(0))

        val savedHeightCm = UserPreferencesStore.getUserHeight(this)
        val totalInches = savedHeightCm / 2.54
        val feet = (totalInches / 12).toInt().coerceIn(3, 8)
        val remainingInches = ((totalInches % 12) * 2).toInt() / 2.0 // Round to nearest 0.5
        spinnerFeet.setSelection(feet - 3)
        
        val inchLabel = if (remainingInches % 1.0 == 0.0) "${remainingInches.toInt()} in" else "$remainingInches in"
        spinnerInches.setSelection(inchesList.indexOf(inchLabel).coerceAtLeast(0))

        etWeight.setText("%.1f".format(UserPreferencesStore.getUserWeight(this)))
        spinnerGender.setSelection(genders.indexOf(UserPreferencesStore.getUserGender(this)).coerceAtLeast(0))
        spinnerWeightGoal.setSelection(weightGoals.indexOf(UserPreferencesStore.getUserGoal(this)).coerceAtLeast(0))

        updateAvatarDisplay()

        findViewById<View>(R.id.containerProfileAvatar).setOnClickListener {
            startActivity(Intent(this, AvatarSelectionActivity::class.java))
        }

        fun getSelectedHeightCm(): Double {
            val f = (spinnerFeet.selectedItem as String).split(" ")[0].toInt()
            val i = (spinnerInches.selectedItem as String).split(" ")[0].toDouble()
            return ((f * 12) + i) * 2.54
        }

        fun updateLiveMetrics() {
            val age = (spinnerAge.selectedItem as? String)?.toIntOrNull() ?: 0
            val height = getSelectedHeightCm()
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

        spinnerAge.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) = updateLiveMetrics()
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        spinnerFeet.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) = updateLiveMetrics()
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        spinnerInches.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) = updateLiveMetrics()
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateLiveMetrics()
            override fun afterTextChanged(s: Editable?) {}
        }
        etWeight.addTextChangedListener(watcher)
        spinnerGender.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateLiveMetrics()
            override fun onNothingSelected(parent: AdapterView<*>?) = updateLiveMetrics()
        }
        updateLiveMetrics()

        btnExport.setOnClickListener {
            lifecycleScope.launch {
                WellnessReportManager.generateAndShareReport(this@ProfileActivity)
            }
        }

        findViewById<View>(R.id.btnProfileSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val age = (spinnerAge.selectedItem as String).toInt()
            val height = getSelectedHeightCm()
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
            UserPreferencesStore.setUserGoal(this, spinnerWeightGoal.selectedItem.toString())

            if (weight > 0.0) {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                HealthDataManager(this).saveWeight(today, weight)
            }

            Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateAvatarDisplay()
    }

    private fun updateAvatarDisplay() {
        val avatarId = UserPreferencesStore.getUserAvatarId(this)
        findViewById<ImageView>(R.id.ivProfileAvatar)?.let {
            AvatarHelper.applyAvatar(it, avatarId)
        }
    }
}
