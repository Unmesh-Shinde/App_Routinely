package com.dailyroutine.app

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*

class WorkoutPlanActivity : AppCompatActivity() {

    private lateinit var planManager: PlanManager
    private lateinit var adapter: ExerciseAdapter
    private var selectedCalendar = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)

    private data class TemplateDialogAction(
        val title: String,
        val description: String,
        val onClick: () -> Unit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_plan)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomPadding(findViewById(R.id.rvExercises))
        InsetHelper.applyBottomMargin(findViewById(R.id.fabAddExercise))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        planManager = PlanManager(this)

        val rvExercises = findViewById<RecyclerView>(R.id.rvExercises)
        adapter = ExerciseAdapter { editExercise(it) }
        rvExercises.layoutManager = LinearLayoutManager(this)
        rvExercises.adapter = adapter

        findViewById<MaterialButton>(R.id.btnPickDateWorkout).setOnClickListener {
            showDatePicker()
        }

        findViewById<FloatingActionButton>(R.id.fabAddExercise).setOnClickListener {
            showExerciseDialog(null)
        }

        findViewById<MaterialButton>(R.id.btnTemplatesWorkout).setOnClickListener {
            showTemplateActionsDialog()
        }

        setupDateStrip()
        updateDateUI()
    }

    private fun setupDateStrip() {
        val container = findViewById<LinearLayout>(R.id.llWorkoutDaysStrip)
        container.removeAllViews()
        
        val tempCal = selectedCalendar.clone() as Calendar
        tempCal.add(Calendar.DAY_OF_YEAR, -3)

        val stripDateFormatter = SimpleDateFormat("EEE\ndd", Locale.US)

        repeat(7) {
            val dateStr = dateFormatter.format(tempCal.time)
            val isSelected = dateStr == dateFormatter.format(selectedCalendar.time)
            val hasExercises = planManager.hasExercisesForDate(dateStr)
            val inTemplateRange = planManager.isDateInAppliedWorkoutTemplateRange(dateStr)

            val btn = Button(this, null, android.R.attr.buttonStyleSmall).apply {
                text = stripDateFormatter.format(tempCal.time)
                setAllCaps(false)
                minWidth = (64 * resources.displayMetrics.density).toInt()
                setPadding(10, 6, 10, 6)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    val margin = (4 * resources.displayMetrics.density).toInt()
                    setMargins(margin, 0, margin, 0)
                }
                setOnClickListener {
                    val clickedCal = Calendar.getInstance()
                    clickedCal.time = dateFormatter.parse(dateStr) ?: Date()
                    selectedCalendar = clickedCal
                    updateDateUI()
                    setupDateStrip()
                }
                alpha = 1f
            }
            styleWorkoutStripButton(btn, isSelected, inTemplateRange, hasExercises)
            container.addView(btn)
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun styleWorkoutStripButton(button: Button, isSelected: Boolean, inTemplateRange: Boolean, hasExercises: Boolean) {
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val bgColor = when {
            inTemplateRange -> if (isNightMode) 0xFFB35A00.toInt() else 0xFFE65100.toInt()
            isSelected -> if (isNightMode) 0xFF2A3048.toInt() else 0xFFFFFFFF.toInt()
            hasExercises -> if (isNightMode) 0xFF3B2817.toInt() else 0xFFFFE2BF.toInt()
            else -> if (isNightMode) 0xFF171B2F.toInt() else 0xFFFFFFFF.toInt()
        }
        val strokeColor = when {
            inTemplateRange && isSelected -> Color.WHITE
            inTemplateRange -> if (isNightMode) 0xFFFFCC80.toInt() else 0xFFE65100.toInt()
            isSelected -> if (isNightMode) 0xFFFFFFFF.toInt() else 0xFF4A1800.toInt()
            hasExercises -> if (isNightMode) 0xFFFFB74D.toInt() else 0xFFEF6C00.toInt()
            else -> if (isNightMode) 0xFF4A526E.toInt() else 0xFFE0C3A0.toInt()
        }
        val strokeWidth = ((if (inTemplateRange) 3 else 1) * resources.displayMetrics.density).toInt()

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * resources.displayMetrics.density
            setColor(bgColor)
            setStroke(strokeWidth, strokeColor)
        }
        button.background = drawable
        val textColor = when {
            inTemplateRange -> Color.WHITE
            isSelected -> if (isNightMode) Color.WHITE else 0xFF3A1200.toInt()
            hasExercises -> if (isNightMode) 0xFFFFF1DF.toInt() else 0xFF3A1200.toInt()
            else -> if (isNightMode) 0xFFF3F5FF.toInt() else 0xFF1A1A2E.toInt()
        }
        button.setTextColor(textColor)
    }

    private fun showDatePicker() {
        DatePickerDialog(this, { _, y, m, d ->
            selectedCalendar.set(y, m, d)
            updateDateUI()
            setupDateStrip()
        }, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), selectedCalendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateUI() {
        findViewById<TextView>(R.id.tvSelectedDateWorkout).text = displayFormatter.format(selectedCalendar.time)
        refreshExercises()
    }

    private fun refreshExercises() {
        val dateStr = dateFormatter.format(selectedCalendar.time)
        val list = planManager.getExercisesForDate(dateStr)
        adapter.submitList(list.sortedBy { it.hour * 60 + it.minute })
        setupDateStrip()
    }

    private fun showTemplateActionsDialog() {
        showTemplateActionDialog(
            title = "Workout Templates",
            message = "Choose what you want to do. Large rows are buttons; smaller text only explains the action.",
            actions = listOf(
                TemplateDialogAction("Apply workout template", "Pick a saved workout plan and date range.") { showApplyTemplatePicker() },
                TemplateDialogAction("Applied workout plans", "See where workout templates are active on the calendar.") { showAppliedWorkoutPlansDialog() },
                TemplateDialogAction("Create from calendar", "Save exercises from an existing date range as a reusable template.") { showCreateTemplateDialog() },
                TemplateDialogAction("Create empty template", "Create a blank template structure for future planning.") { showCreateNewTemplateDialog() },
                TemplateDialogAction("Manage saved templates", "Open templates to apply, view, rename, duplicate, or delete.") { showManageSavedWorkoutTemplatesDialog() }
            )
        )
    }

    private fun showTemplateActionDialog(title: String, message: String, actions: List<TemplateDialogAction>) {
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }

        container.addView(TextView(this).apply {
            text = message
            textSize = 13f
            setTextColor(if (isNightMode) Color.argb(220, 255, 255, 255) else Color.argb(210, 0, 0, 0))
            setPadding(0, 0, 0, dp(10))
        })

        var dialog: AlertDialog? = null
        actions.forEach { action ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(if (isNightMode) 0xFF2A2116.toInt() else 0xFFFFF7ED.toInt())
                    setStroke(dp(1), if (isNightMode) 0xFFFFB74D.toInt() else 0xFFE65100.toInt())
                }
                setOnClickListener {
                    dialog?.dismiss()
                    action.onClick()
                }
            }
            row.addView(TextView(this).apply {
                text = action.title
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(if (isNightMode) Color.WHITE else 0xFF4A1800.toInt())
            })
            row.addView(TextView(this).apply {
                text = action.description
                textSize = 12f
                setPadding(0, dp(3), 0, 0)
                setTextColor(if (isNightMode) Color.argb(220, 255, 255, 255) else 0xFF6D2D00.toInt())
            })
            container.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) })
        }

        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(container) })
            .setNegativeButton("Close", null)
            .create()
        dialog.show()
    }

    private fun showCreateTemplateDialog() {
        pickDateRange { startDate, endDate ->
            promptTemplateName("Create Workout Template", "My Workout Plan") { name ->
                val ok = planManager.createWorkoutTemplateFromRange(
                    name = name,
                    startDate = startDate,
                    endDate = endDate,
                    allowEmpty = false
                )
                if (ok) {
                    Toast.makeText(this, "Template saved for $startDate to $endDate", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No workouts found in selected date range", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showCreateNewTemplateDialog() {
        pickDateRange { startDate, endDate ->
            promptTemplateName("Create New Workout Template", "New Workout Template") { name ->
                val ok = planManager.createWorkoutTemplateFromRange(
                    name = name,
                    startDate = startDate,
                    endDate = endDate,
                    allowEmpty = true
                )
                if (ok) {
                    Toast.makeText(this, "Template created for $startDate to $endDate", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Unable to create template", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showApplyTemplatePicker() {
        val templates = planManager.listWorkoutTemplates()
        if (templates.isEmpty()) {
            Toast.makeText(this, "No workout templates found", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = templates.map {
            "${it.name} • ${it.durationDays} days • ${workoutTemplateExerciseCount(it)} exercise(s) • ${workoutTemplateAppliedCount(it)} applied"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Apply Workout Template")
            .setItems(labels) { _, which ->
                val template = templates[which]
                pickWorkoutTemplateRangeAndConfirm(template)
            }
            .show()
    }

    private fun pickWorkoutTemplateRangeAndConfirm(template: WorkoutTemplate) {
        pickDateRange { startDate, endDate ->
            val selectedDays = daysBetweenInclusive(startDate, endDate)
            if (selectedDays > template.durationDays) {
                AlertDialog.Builder(this)
                    .setTitle("Cannot apply template")
                    .setMessage(
                        "Selected range is $selectedDays days, but '${template.name}' is a ${template.durationDays}-day template. Choose up to ${template.durationDays} days."
                    )
                    .setPositiveButton("OK", null)
                    .show()
                return@pickDateRange
            }
            confirmWorkoutTemplateApply(template, startDate, endDate, selectedDays)
        }
    }

    private fun confirmWorkoutTemplateApply(template: WorkoutTemplate, startDate: String, endDate: String, selectedDays: Int) {
        val configuredDays = workoutTemplateConfiguredDayCount(template)
        val totalExercises = workoutTemplateExerciseCount(template)
        AlertDialog.Builder(this)
            .setTitle("Apply '${template.name}'?")
            .setMessage(
                "Template length: ${template.durationDays} days\n" +
                    "Configured days: $configuredDays\n" +
                    "Exercise entries inside template: $totalExercises\n" +
                    "Already applied ranges: ${workoutTemplateAppliedCount(template)}\n" +
                    "Selected range: $startDate to $endDate ($selectedDays days)\n" +
                    "Target dates: ${buildDateRangePreview(startDate, selectedDays)}\n\n" +
                    "Exercises from this template will be copied into the selected calendar dates."
            )
            .setPositiveButton("Apply") { _, _ ->
                applyWorkoutTemplateToRange(template, startDate, endDate, selectedDays)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyWorkoutTemplateToRange(template: WorkoutTemplate, startDate: String, endDate: String, selectedDays: Int) {
        val result = planManager.applyWorkoutTemplateToRange(template.id, startDate, endDate)
        if (result.applied) {
            Toast.makeText(this, "Template applied for $startDate to $endDate", Toast.LENGTH_LONG).show()
            refreshExercises()
        } else if (result.conflictRange != null) {
            showWorkoutTemplateOverlapDialog(template, result.conflictRange, selectedDays)
        } else if (!result.failureReason.isNullOrBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Cannot apply template")
                .setMessage(result.failureReason)
                .setPositiveButton("OK", null)
                .show()
        } else {
            Toast.makeText(this, "Unable to apply template", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showWorkoutTemplateOverlapDialog(template: WorkoutTemplate, conflict: AppliedTemplateRange, selectedDays: Int) {
        AlertDialog.Builder(this)
            .setTitle("Template overlap detected")
            .setMessage(
                "'${conflict.templateName}' is already applied from ${conflict.startDate} to ${conflict.endDate}.\n\n" +
                    "Start '${template.name}' after ${conflict.endDate}, or review applied workout plans first."
            )
            .setPositiveButton("Start after conflict") { _, _ ->
                val startDate = addDaysToDate(conflict.endDate, 1)
                val endDate = addDaysToDate(startDate, selectedDays - 1)
                confirmWorkoutTemplateApply(template, startDate, endDate, selectedDays)
            }
            .setNeutralButton("View applied plans") { _, _ -> showAppliedWorkoutPlansDialog() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManageSavedWorkoutTemplatesDialog() {
        val templates = planManager.listWorkoutTemplates()
        if (templates.isEmpty()) {
            Toast.makeText(this, "No saved workout templates", Toast.LENGTH_SHORT).show()
            return
        }

        showTemplateActionDialog(
            title = "Manage Workout Templates",
            message = "Tap a template card to open its actions. The smaller line summarizes what is inside and how often it is applied.",
            actions = templates.map { template ->
                TemplateDialogAction(
                    title = template.name,
                    description = "${template.durationDays} days • ${workoutTemplateConfiguredDayCount(template)} configured day(s) • ${workoutTemplateExerciseCount(template)} exercise(s) • ${workoutTemplateAppliedCount(template)} applied"
                ) { showSavedWorkoutTemplateActions(template) }
            }
        )
    }

    private fun showSavedWorkoutTemplateActions(template: WorkoutTemplate) {
        val summary =
                "Duration: ${template.durationDays} days\n" +
                    "Configured days: ${workoutTemplateConfiguredDayCount(template)}\n" +
                    "Exercise entries: ${workoutTemplateExerciseCount(template)}\n" +
                    "Applied ranges: ${workoutTemplateAppliedCount(template)}"

        showTemplateActionDialog(
            title = template.name,
            message = "$summary\n\nChoose an action for this saved workout template.",
            actions = listOf(
                TemplateDialogAction("Apply to calendar", "Choose a date range and copy this template into those days.") { pickWorkoutTemplateRangeAndConfirm(template) },
                TemplateDialogAction("View template details", "See day-by-day exercises stored in this template.") { showWorkoutTemplateDetails(template) },
                TemplateDialogAction("Rename template", "Change only the template name; exercise contents stay unchanged.") {
                    promptTemplateName("Rename Workout Template", template.name) { newName ->
                        if (planManager.renameWorkoutTemplate(template.id, newName)) {
                            Toast.makeText(this, "Template renamed", Toast.LENGTH_SHORT).show()
                            setupDateStrip()
                        } else {
                            Toast.makeText(this, "Unable to rename template", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                TemplateDialogAction("Duplicate template", "Create a copy that you can rename and reuse separately.") {
                    promptTemplateName("Duplicate Workout Template", "${template.name} Copy") { newName ->
                        if (planManager.duplicateWorkoutTemplate(template.id, newName)) {
                            Toast.makeText(this, "Template duplicated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Unable to duplicate template", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                TemplateDialogAction("Delete saved template", "Remove this template and its colored applied markers. Copied exercises remain on calendar.") { confirmDeleteWorkoutTemplate(template) }
            )
        )
    }

    private fun showWorkoutTemplateDetails(template: WorkoutTemplate) {
        val lines = mutableListOf<String>()
        lines += "Duration: ${template.durationDays} days"
        lines += "Configured days: ${workoutTemplateConfiguredDayCount(template)}"
        lines += "Exercise entries: ${workoutTemplateExerciseCount(template)}"
        lines += "Applied ranges: ${workoutTemplateAppliedCount(template)}"
        lines += ""
        for (offset in 0 until template.durationDays) {
            lines += "Day ${offset + 1}:"
            val exercises = template.exercisesByDayOffset[offset].orEmpty().sortedBy { it.hour * 60 + it.minute }
            if (exercises.isEmpty()) {
                lines += "  No exercises configured"
            } else {
                exercises.forEach { exercise ->
                    lines += "  ${exercise.formatTime()} • ${exercise.name} • ${exercise.sets}x${exercise.reps}"
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle(template.name)
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton("Close", null)
            .show()
    }

    private fun confirmDeleteWorkoutTemplate(template: WorkoutTemplate) {
        val appliedCount = workoutTemplateAppliedCount(template)
        AlertDialog.Builder(this)
            .setTitle("Delete '${template.name}'?")
            .setMessage(
                "Duration: ${template.durationDays} days\n" +
                    "Exercise entries: ${workoutTemplateExerciseCount(template)}\n" +
                    "Applied ranges to remove: $appliedCount\n\n" +
                    "This removes the saved template and its applied calendar markers. Existing copied exercises will remain on the calendar."
            )
            .setPositiveButton("Delete") { _, _ ->
                planManager.deleteWorkoutTemplate(template.id)
                Toast.makeText(this, "Template deleted", Toast.LENGTH_SHORT).show()
                setupDateStrip()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAppliedWorkoutPlansDialog() {
        val ranges = planManager.listAppliedWorkoutTemplateRanges()
        if (ranges.isEmpty()) {
            Toast.makeText(this, "No workout templates applied yet", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = ranges.map {
            "${it.templateName} • ${it.startDate} to ${it.endDate} • ${daysBetweenInclusive(it.startDate, it.endDate)} day(s)"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Applied Workout Plans")
            .setItems(labels) { _, which ->
                showAppliedWorkoutRangeActions(ranges[which])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAppliedWorkoutRangeActions(range: AppliedTemplateRange) {
        val options = arrayOf(
            "View applied details",
            "Jump to start date",
            "Jump to end date",
            "Remove applied marker"
        )
        AlertDialog.Builder(this)
            .setTitle(range.templateName)
            .setMessage("Applied from ${range.startDate} to ${range.endDate} • ${daysBetweenInclusive(range.startDate, range.endDate)} day(s)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAppliedWorkoutRangeDetails(range)
                    1 -> jumpToWorkoutDate(range.startDate)
                    2 -> jumpToWorkoutDate(range.endDate)
                    3 -> {
                        val removed = planManager.removeAppliedWorkoutTemplateRange(range.startDate, range.endDate)
                        if (removed) {
                            Toast.makeText(this, "Applied workout marker removed", Toast.LENGTH_SHORT).show()
                            setupDateStrip()
                        } else {
                            Toast.makeText(this, "Unable to remove applied marker", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAppliedWorkoutRangeDetails(range: AppliedTemplateRange) {
        val template = planManager.listWorkoutTemplates().firstOrNull { it.id == range.templateId }
        val lines = mutableListOf<String>()
        lines += "Template: ${range.templateName}"
        lines += "Applied range: ${range.startDate} to ${range.endDate}"
        lines += "Range length: ${daysBetweenInclusive(range.startDate, range.endDate)} day(s)"
        lines += "Calendar color: strong orange"
        if (template != null) {
            lines += ""
            lines += "Saved template details:"
            lines += "Duration: ${template.durationDays} days"
            lines += "Configured days: ${workoutTemplateConfiguredDayCount(template)}"
            lines += "Exercise entries: ${workoutTemplateExerciseCount(template)}"
        }
        lines += ""
        lines += "Removing the marker clears the colored applied range, but copied exercises remain on their dates."

        AlertDialog.Builder(this)
            .setTitle("Applied Workout Plan Details")
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton("Jump to start") { _, _ -> jumpToWorkoutDate(range.startDate) }
            .setNeutralButton("Jump to end") { _, _ -> jumpToWorkoutDate(range.endDate) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun jumpToWorkoutDate(date: String) {
        dateFormatter.parse(date)?.let {
            selectedCalendar.time = it
            updateDateUI()
            setupDateStrip()
        }
    }

    private fun promptTemplateName(title: String, defaultName: String, onNameReady: (String) -> Unit) {
        val etName = EditText(this).apply {
            hint = "Template name"
            setText(defaultName)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(etName)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) {
                    onNameReady(name)
                } else {
                    Toast.makeText(this, "Template name is required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickDateRange(onRangeSelected: (String, String) -> Unit) {
        val startPicker = DatePickerDialog(
            this,
            { _, y, m, d ->
                val startCal = Calendar.getInstance().apply {
                    set(y, m, d, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endPicker = DatePickerDialog(
                    this,
                    { _, ey, em, ed ->
                        val endCal = Calendar.getInstance().apply {
                            set(ey, em, ed, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onRangeSelected(dateFormatter.format(startCal.time), dateFormatter.format(endCal.time))
                    },
                    startCal.get(Calendar.YEAR),
                    startCal.get(Calendar.MONTH),
                    startCal.get(Calendar.DAY_OF_MONTH)
                )
                endPicker.datePicker.minDate = startCal.timeInMillis
                endPicker.setTitle("Select end date")
                endPicker.show()
            },
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        )
        startPicker.setTitle("Select start date")
        startPicker.show()
    }

    private fun daysBetweenInclusive(startDate: String, endDate: String): Int {
        val start = dateFormatter.parse(startDate) ?: return 0
        val end = dateFormatter.parse(endDate) ?: return 0
        val millis = end.time - start.time
        return (millis / (24L * 60L * 60L * 1000L)).toInt() + 1
    }

    private fun addDaysToDate(date: String, days: Int): String {
        val cal = Calendar.getInstance().apply {
            time = dateFormatter.parse(date) ?: Date()
            add(Calendar.DAY_OF_YEAR, days)
        }
        return dateFormatter.format(cal.time)
    }

    private fun buildDateRangePreview(startDate: String, dayCount: Int): String {
        if (dayCount <= 0) return startDate
        if (dayCount <= 5) {
            return (0 until dayCount).joinToString(", ") { addDaysToDate(startDate, it) }
        }
        return "${addDaysToDate(startDate, 0)}, ${addDaysToDate(startDate, 1)}, ... ${addDaysToDate(startDate, dayCount - 1)}"
    }

    private fun workoutTemplateConfiguredDayCount(template: WorkoutTemplate): Int {
        return template.exercisesByDayOffset.keys.distinct().size
    }

    private fun workoutTemplateExerciseCount(template: WorkoutTemplate): Int {
        return template.exercisesByDayOffset.values.sumOf { it.size }
    }

    private fun workoutTemplateAppliedCount(template: WorkoutTemplate): Int {
        return planManager.listAppliedWorkoutTemplateRanges().count { it.templateId == template.id }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun showExerciseDialog(existing: Exercise?) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_edit_exercise, null)
        val etName = v.findViewById<EditText>(R.id.etExName)
        val etSets = v.findViewById<EditText>(R.id.etExSets)
        val etReps = v.findViewById<EditText>(R.id.etExReps)
        val tvTime = v.findViewById<TextView>(R.id.tvExTime)
        val btnTime = v.findViewById<Button>(R.id.btnPickExTime)
        val swReminder = v.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swExReminder)
        val etTarget = v.findViewById<EditText>(R.id.etExTarget)
        val sliderIntensity = v.findViewById<com.google.android.material.slider.Slider>(R.id.sliderExIntensity)

        var selHour = existing?.hour ?: 7
        var selMin = existing?.minute ?: 0
        fun updateTimeLabel() {
            val h = if (selHour == 0 || selHour == 12) 12 else selHour % 12
            val amPm = if (selHour < 12) "AM" else "PM"
            tvTime.text = "%02d:%02d %s".format(h, selMin, amPm)
        }
        updateTimeLabel()

        existing?.let {
            etName.setText(it.name)
            etSets.setText(it.sets.toString())
            etReps.setText(it.reps)
            swReminder.isChecked = it.isReminderEnabled
            etTarget.setText(it.targetArea)
            sliderIntensity.value = it.intensity.toFloat()
        }

        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                selHour = h
                selMin = m
                updateTimeLabel()
            }, selHour, selMin, false).show()
        }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "Add Exercise" else "Edit Exercise")
            .setView(v)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                etName.error = "Required"
                return@setOnClickListener
            }
            
            val newEx = Exercise(
                id = existing?.id ?: Exercise().id,
                name = name,
                sets = etSets.text.toString().toIntOrNull() ?: 3,
                reps = etReps.text.toString(),
                hour = selHour,
                minute = selMin,
                isReminderEnabled = swReminder.isChecked,
                targetArea = etTarget.text.toString().trim(),
                intensity = sliderIntensity.value.toInt()
            )
            
            val dateStr = dateFormatter.format(selectedCalendar.time)
            planManager.saveExerciseForDate(dateStr, newEx)
            planManager.syncExerciseReminder(this@WorkoutPlanActivity, newEx, dateStr)
            WorkoutMetSearchEngine.enrichIfNeeded(this@WorkoutPlanActivity, newEx) { enriched ->
                val current = planManager.getExercisesForDate(dateStr).firstOrNull { it.id == newEx.id }
                if (current != null && hasSameWorkoutInputs(current, newEx)) {
                    planManager.saveExerciseForDate(dateStr, enriched)
                    planManager.syncExerciseReminder(this@WorkoutPlanActivity, enriched, dateStr)
                    refreshExercises()
                }
            }
            refreshExercises()
            dialog.dismiss()
        }
    }

    private fun hasSameWorkoutInputs(current: Exercise, saved: Exercise): Boolean {
        return current.name == saved.name &&
            current.sets == saved.sets &&
            current.reps == saved.reps &&
            current.targetArea == saved.targetArea &&
            current.intensity == saved.intensity
    }

    private fun editExercise(ex: Exercise) {
        showExerciseDialog(ex)
    }

    inner class ExerciseAdapter(private val onEdit: (Exercise) -> Unit) : RecyclerView.Adapter<ExerciseAdapter.VH>() {
        private var items = listOf<Exercise>()

        fun submitList(list: List<Exercise>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_exercise, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ex = items[position]
            val dateStr = dateFormatter.format(selectedCalendar.time)
            val isDone = RoutineProgressStore.getDoneIds(this@WorkoutPlanActivity, dateStr).contains(ex.id.toString())

            holder.ivIcon.setImageResource(if (isDone) R.drawable.ic_check else R.drawable.ic_workout)
            holder.ivIcon.setBackgroundResource(if (isDone) R.drawable.bg_circle_walking else R.drawable.bg_circle_workout)
            holder.tvTitle.text = ex.name
            holder.tvSubtitle.text = "${ex.sets}x${ex.reps} • ${ex.targetArea} (${ex.intensity}%)"
            holder.tvTime.text = ex.formatTime()

            holder.itemView.setOnClickListener { onEdit(ex) }
            
            val switch = holder.itemView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchEnabled)
            switch.visibility = View.VISIBLE
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = isDone
            switch.setOnCheckedChangeListener { _, checked ->
                RoutineProgressStore.setDoneStatus(this@WorkoutPlanActivity, dateStr, ex.id, checked)
                refreshExercises()
            }

            holder.itemView.findViewById<View>(R.id.btnDelete).setOnClickListener {
                planManager.deleteExerciseForDate(dateStr, ex)
                refreshExercises()
            }
        }

        override fun getItemCount() = items.size

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivIcon: ImageView = v.findViewById(R.id.tvEmoji)
            val tvTitle: TextView = v.findViewById(R.id.tvTitle)
            val tvSubtitle: TextView = v.findViewById(R.id.tvSubtitle)
            val tvTime: TextView = v.findViewById(R.id.tvTime)
        }
    }
}
