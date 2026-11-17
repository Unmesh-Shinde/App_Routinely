package com.dailyroutine.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private val selectedReportParameters = ReportParameterSelection()
    private var selectedReportDays: Int? = null
    private val profileImagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) showProfileImageCrop(uri)
    }

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
        findViewById<View>(R.id.btnUploadProfilePhoto).setOnClickListener {
            profileImagePicker.launch(arrayOf("image/*"))
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

        val rangeOptions = listOf<WellnessReportPeriod?>(null) + WellnessReportPeriods.options
        val rangeLabels = rangeOptions.map { it?.label ?: "Select a date range" }
        val rangeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, rangeLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val spinnerReportRange = findViewById<Spinner>(R.id.spinnerReportRange)
        spinnerReportRange.adapter = rangeAdapter
        val tvReportExportHint = findViewById<TextView>(R.id.tvReportExportHint)
        val reportExportButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExportReport)
        val updateReportExportState = {
            val hasDateRange = selectedReportDays != null
            val hasParameters = selectedReportParameters.selected.isNotEmpty()
            reportExportButton.isEnabled = hasDateRange && hasParameters
            tvReportExportHint.text = when {
                !hasDateRange -> "Choose a date range to enable report export."
                !hasParameters -> "Select at least one report parameter to enable export."
                else -> "Your report is ready to export."
            }
            tvReportExportHint.visibility = if (hasDateRange && hasParameters) View.GONE else View.VISIBLE

            val enabledColor = ContextCompat.getColor(this, R.color.healthProfile)
            val disabledColor = ContextCompat.getColor(this, R.color.cardStroke)
            val enabledTextColor = ContextCompat.getColor(this, R.color.textOnProfile)
            val disabledTextColor = ContextCompat.getColor(this, R.color.textSecondary)
            val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_enabled))
            reportExportButton.backgroundTintList = ColorStateList(states, intArrayOf(disabledColor, enabledColor))
            reportExportButton.strokeColor = ColorStateList(states, intArrayOf(disabledColor, enabledColor))
            reportExportButton.setTextColor(ColorStateList(states, intArrayOf(disabledTextColor, enabledTextColor)))
            reportExportButton.iconTint = ColorStateList(states, intArrayOf(disabledTextColor, enabledTextColor))
        }
        spinnerReportRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedReportDays = rangeOptions.getOrNull(position)?.days
                updateReportExportState()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedReportDays = null
                updateReportExportState()
            }
        }

        val btnReportParameters = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReportParameters)
        updateReportParameterButton(btnReportParameters)
        updateReportExportState()
        btnReportParameters.setOnClickListener {
            showReportParameterPicker(btnReportParameters, updateReportExportState)
        }

        btnExport.setOnClickListener {
            val days = selectedReportDays ?: run {
                tvReportExportHint.visibility = View.VISIBLE
                tvReportExportHint.text = "Choose a date range before exporting your wellness report."
                return@setOnClickListener
            }
            if (selectedReportParameters.selected.isEmpty()) {
                Toast.makeText(this, "Select at least one report parameter.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                WellnessReportManager.generateAndShareReport(this@ProfileActivity, days, selectedReportParameters.selected)
            }
        }

        findViewById<View>(R.id.btnViewNutritionPolicy)?.setOnClickListener {
            startActivity(Intent(this, NutritionPolicyActivity::class.java))
        }

        findViewById<View>(R.id.btnDownloadNutritionPdf)?.setOnClickListener {
            val file = NutritionPdfExporter.generatePdf(this)
            if (file != null && file.exists()) {
                Toast.makeText(this, "Downloaded to Downloads folder: ${file.name}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Failed to download PDF. Please try again.", Toast.LENGTH_SHORT).show()
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
                lifecycleScope.launch {
                    HealthMetricsRoomWriter(this@ProfileActivity).upsertMetric(
                        date = today,
                        weightKg = weight,
                        source = "manual_entry"
                    )
                }
            }

            Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateAvatarDisplay()
        updateNutritionPolicyCard()
    }

    private fun updateNutritionPolicyCard() {
        val tvChip = findViewById<TextView>(R.id.tvPolicyAckChip) ?: return
        val isAck = UserSettingsStore.hasAcknowledgedNutritionPolicy(this)

        if (isAck) {
            tvChip.text = "✓ Acknowledged"
            tvChip.setTextColor(ContextCompat.getColor(this, R.color.healthNutrition))
        } else {
            tvChip.text = "⚠️ Unacknowledged"
            tvChip.setTextColor(ContextCompat.getColor(this, R.color.calorieStatusOrange))
        }
    }

    private fun updateAvatarDisplay() {
        val avatarId = UserPreferencesStore.getUserAvatarId(this)
        findViewById<ImageView>(R.id.ivProfileAvatar)?.let {
            AvatarHelper.applyAvatar(it, avatarId)
        }
    }

    private fun saveProfileImage(uri: Uri) {
        lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { AvatarHelper.saveCustomAvatar(this@ProfileActivity, uri) } }
            result.onSuccess {
                updateAvatarDisplay()
                Toast.makeText(this@ProfileActivity, "Profile photo updated.", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(this@ProfileActivity, error.message ?: "Unable to save this image.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showProfileImageCrop(uri: Uri) {
        lifecycleScope.launch {
            val decoded = runCatching { decodeImageForCrop(uri) }
            decoded.onSuccess { bitmap ->
                val content = layoutInflater.inflate(R.layout.dialog_profile_photo_crop, null, false)
                val cropView = AvatarCropView(this@ProfileActivity, bitmap).apply {
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                content.findViewById<FrameLayout>(R.id.profilePhotoCropFrame).addView(cropView)
                val dialog = MaterialAlertDialogBuilder(this@ProfileActivity)
                    .setView(content)
                    .create()
                content.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveCroppedPhoto)
                    .setOnClickListener {
                        val cropped = cropView.createCroppedBitmap()
                        dialog.dismiss()
                        saveCroppedProfileImage(cropped)
                    }
                content.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUseOriginalPhoto)
                    .setOnClickListener {
                        dialog.dismiss()
                        saveProfileImage(uri)
                    }
                content.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelPhotoCrop)
                    .setOnClickListener { dialog.dismiss() }
                dialog.show()
            }.onFailure {
                MaterialAlertDialogBuilder(this@ProfileActivity)
                    .setTitle("Crop preview unavailable")
                    .setMessage("This image can still be used at its original size.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Use original") { _, _ -> saveProfileImage(uri) }
                    .show()
            }
        }
    }

    private suspend fun decodeImageForCrop(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val result = imageLoader.execute(
            ImageRequest.Builder(this@ProfileActivity)
                .data(uri)
                .size(2048, 2048)
                .allowHardware(false)
                .build()
        )
        if (result !is SuccessResult) throw IllegalArgumentException("This image format cannot be previewed for cropping.")
        result.drawable.toBitmap()
    }

    private fun saveCroppedProfileImage(bitmap: Bitmap) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { AvatarHelper.saveCroppedAvatar(this@ProfileActivity, bitmap) }
            }
            result.onSuccess {
                updateAvatarDisplay()
                Toast.makeText(this@ProfileActivity, "Profile photo updated.", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(this@ProfileActivity, error.message ?: "Unable to save this image.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateReportParameterButton(button: com.google.android.material.button.MaterialButton) {
        val allParameters = WellnessReportManager.Parameter.values().toSet()
        button.text = when {
            selectedReportParameters.selected.size == allParameters.size -> "All parameters selected"
            selectedReportParameters.selected.isEmpty() -> "No parameters selected"
            else -> "${selectedReportParameters.selected.size} parameters selected"
        }
    }

    private fun showReportParameterPicker(
        button: com.google.android.material.button.MaterialButton,
        onSelectionChanged: () -> Unit
    ) {
        val parameters = WellnessReportManager.Parameter.values()
        val draftSelection = ReportParameterSelection(selectedReportParameters.selected)
        val checked = parameters.map { it in draftSelection.selected }.toBooleanArray()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val selectAll = MaterialCheckBox(this).apply {
            text = "Select all"
            textSize = 15f
            isChecked = checked.all { it }
        }
        content.addView(selectAll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360))
        }
        val rows = mutableListOf<MaterialCheckBox>()
        var updatingAll = false
        parameters.forEachIndexed { index, parameter ->
            val row = MaterialCheckBox(this).apply {
                text = parameter.label
                textSize = 14f
                isChecked = checked[index]
            }
            rows.add(row)
            content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
            row.setOnCheckedChangeListener { _, isChecked ->
                checked[index] = isChecked
                draftSelection.setSelected(parameter, isChecked)
                if (!updatingAll) {
                    updatingAll = true
                    selectAll.isChecked = checked.all { it }
                    updatingAll = false
                }
            }
        }
        selectAll.setOnCheckedChangeListener { _, isChecked ->
            if (updatingAll) return@setOnCheckedChangeListener
            updatingAll = true
            draftSelection.selectAll(isChecked)
            checked.indices.forEach { index ->
                checked[index] = isChecked
                rows[index].isChecked = isChecked
            }
            updatingAll = false
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Choose report parameters")
            .setView(scrollView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done") { _, _ ->
                selectedReportParameters.replaceSelection(draftSelection.selected)
                updateReportParameterButton(button)
                onSelectionChanged()
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
