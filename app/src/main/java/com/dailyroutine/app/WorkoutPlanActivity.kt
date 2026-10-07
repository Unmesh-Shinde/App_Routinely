package com.dailyroutine.app

import android.view.Menu
import android.view.MenuItem
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class WorkoutPlanActivity : AppCompatActivity() {

    private lateinit var planManager: PlanManager
    private lateinit var adapter: ExerciseAdapter
    private var selectedCalendar = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
    private val dateStripPastDays = 30
    private val dateStripFutureDays = 30
    
    fun onSelectionChanged(count: Int) {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        if (count > 0) {
            toolbar.title = "$count selected"
            
            // Set up contextual action bar style
            val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            toolbar.setBackgroundColor(if (isNightMode) Color.parseColor("#4A2C10") else Color.parseColor("#FFE0B2"))
            
            // Change nav icon to close
            val typedValue = TypedValue()
            theme.resolveAttribute(androidx.appcompat.R.attr.actionModeCloseDrawable, typedValue, true)
            if (typedValue.resourceId != 0) {
                toolbar.setNavigationIcon(typedValue.resourceId)
            }
            toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
            toolbar.setNavigationOnClickListener { adapter.clearSelection() }
            
            if (toolbar.menu.findItem(1) == null) {
                toolbar.menu.add(Menu.NONE, 1, Menu.NONE, "Delete")
                    .setIcon(android.R.drawable.ic_menu_delete)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                toolbar.setOnMenuItemClickListener {
                    if (it.itemId == 1) {
                        deleteSelectedExercises()
                        true
                    } else false
                }
            }
        } else {
            toolbar.title = "Workout"
            toolbar.setBackgroundColor(Color.TRANSPARENT)
            
            val typedValue = TypedValue()
            theme.resolveAttribute(androidx.appcompat.R.attr.homeAsUpIndicator, typedValue, true)
            toolbar.setNavigationIcon(typedValue.resourceId)
            toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
            toolbar.setNavigationOnClickListener { finish() }
            
            toolbar.menu.clear()
        }
    }

    private fun deleteSelectedExercises() {
        val dateStr = dateFormatter.format(selectedCalendar.time)
        val selectedIds = adapter.getSelectedIds()
        val exercises = planManager.getExercisesForDate(dateStr).filter { selectedIds.contains(it.id) }
        
        AlertDialog.Builder(this)
            .setTitle("Delete Exercises?")
            .setMessage("Remove ${exercises.size} selected exercises from your plan?")
            .setPositiveButton("Delete") { _, _ ->
                exercises.forEach { ex ->
                    planManager.deleteExerciseForDate(dateStr, ex)
                }
                adapter.clearSelection()
                refreshExercises()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private val exerciseTypes = listOf(
        "Strength / Bodyweight",
        "Strength / Weighted",
        "Cardio",
        "HIIT / Circuit",
        "Timed Hold / Isometric",
        "Mobility / Stretching / Yoga",
        "Warmup / Cooldown"
    )
    private val effortLabels = listOf("Light", "Moderate", "Hard", "Very Hard", "Max")
    private val restUnits = listOf("Sec", "Min")

    private data class ExercisePreset(
        val name: String,
        val type: String,
        val target: String,
        val effort: String = "Moderate",
        val sets: Int = 3,
        val reps: Int = 10,
        val durationMinutes: Double = 10.0,
        val rounds: Int = 4,
        val workSeconds: Int = 40,
        val restValue: Double = 60.0,
        val restUnit: String = "Sec",
        val addedWeightKg: Double = 0.0
    )

    private val exercisePresets = listOf(
        ExercisePreset("Push Ups", "Strength / Bodyweight", "Chest, Triceps, Shoulders, Core", sets = 3, reps = 12, restValue = 60.0),
        ExercisePreset("Pull Ups", "Strength / Bodyweight", "Back, Biceps, Core", sets = 3, reps = 6, restValue = 90.0),
        ExercisePreset("Squats", "Strength / Bodyweight", "Legs, Glutes, Core", sets = 3, reps = 15, restValue = 60.0),
        ExercisePreset("Lunges", "Strength / Bodyweight", "Legs, Glutes", sets = 3, reps = 12, restValue = 60.0),
        ExercisePreset("Dips", "Strength / Bodyweight", "Chest, Triceps, Shoulders", sets = 3, reps = 8, restValue = 75.0),
        ExercisePreset("Goblet Squat", "Strength / Weighted", "Legs, Glutes, Core", effort = "Hard", sets = 4, reps = 10, restValue = 90.0),
        ExercisePreset("Dumbbell Shoulder Press", "Strength / Weighted", "Shoulders, Triceps", sets = 3, reps = 10, restValue = 90.0),
        ExercisePreset("Running", "Cardio", "Full Body / Cardio", effort = "Hard", durationMinutes = 20.0),
        ExercisePreset("Cycling", "Cardio", "Legs / Cardio", durationMinutes = 30.0),
        ExercisePreset("Jumping Jacks", "Cardio", "Full Body / Cardio", effort = "Hard", durationMinutes = 10.0),
        ExercisePreset("Burpees", "HIIT / Circuit", "Full Body / Cardio", effort = "Very Hard", rounds = 5, workSeconds = 30, restValue = 30.0),
        ExercisePreset("High Knees", "HIIT / Circuit", "Legs / Cardio", effort = "Hard", rounds = 4, workSeconds = 40, restValue = 20.0),
        ExercisePreset("Mountain Climbers", "HIIT / Circuit", "Full Body, Core, Cardio", effort = "Hard", rounds = 4, workSeconds = 40, restValue = 20.0),
        ExercisePreset("Plank", "Timed Hold / Isometric", "Core", sets = 3, durationMinutes = 1.0, restValue = 45.0),
        ExercisePreset("Wall Sit", "Timed Hold / Isometric", "Legs, Glutes", sets = 3, durationMinutes = 0.75, restValue = 45.0),
        ExercisePreset("Stretching", "Mobility / Stretching / Yoga", "Mobility", effort = "Light", durationMinutes = 10.0),
        ExercisePreset("Yoga Flow", "Mobility / Stretching / Yoga", "Mobility, Full Body", durationMinutes = 20.0),
        ExercisePreset("Dynamic Warmup", "Warmup / Cooldown", "Warmup", effort = "Light", durationMinutes = 8.0)
    )

    private data class TemplateDialogAction(
        val title: String,
        val description: String,
        val onClick: () -> Unit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_plan)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomPadding(findViewById(R.id.rvExercises))
        InsetHelper.applyBottomMargin(findViewById(R.id.fabAddExercise))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
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
        val scrollView = findViewById<HorizontalScrollView>(R.id.hsvWorkoutDaysStrip)
        val container = findViewById<LinearLayout>(R.id.llWorkoutDaysStrip)
        container.removeAllViews()
        
        val tempCal = selectedCalendar.clone() as Calendar
        tempCal.add(Calendar.DAY_OF_YEAR, -dateStripPastDays)

        val stripDateFormatter = SimpleDateFormat("EEE\ndd", Locale.US)
        val todayStr = dateFormatter.format(Date())
        var selectedButton: Button? = null

        repeat(dateStripPastDays + dateStripFutureDays + 1) {
            val dateStr = dateFormatter.format(tempCal.time)
            val isSelected = dateStr == dateFormatter.format(selectedCalendar.time)
            val isToday = dateStr == todayStr
            val hasExercises = planManager.hasExercisesForDate(dateStr)
            val inTemplateRange = planManager.isDateInAppliedWorkoutTemplateRange(dateStr)

            val btn = Button(this, null, android.R.attr.buttonStyleSmall).apply {
                text = if (isToday) "Today\n${SimpleDateFormat("dd", Locale.US).format(tempCal.time)}" else stripDateFormatter.format(tempCal.time)
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
            styleWorkoutStripButton(btn, isSelected, isToday, inTemplateRange, hasExercises)
            if (isSelected) selectedButton = btn
            container.addView(btn)
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        selectedButton?.let { button ->
            scrollView.post {
                val targetX = button.left - ((scrollView.width - button.width) / 2)
                scrollView.smoothScrollTo(targetX.coerceAtLeast(0), 0)
            }
        }
    }

    private fun styleWorkoutStripButton(button: Button, isSelected: Boolean, isToday: Boolean, inTemplateRange: Boolean, hasExercises: Boolean) {
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val bgColor = when {
            inTemplateRange -> if (isNightMode) 0xFF6D2D00.toInt() else 0xFFE65100.toInt() // Darker brown/orange
            isSelected -> if (isNightMode) 0xFF5D2A00.toInt() else 0xFFFFF3E0.toInt()
            hasExercises -> if (isNightMode) 0xFF4A2C10.toInt() else 0xFFFFE0B2.toInt() // Lighter orange/tan
            isToday -> if (isNightMode) 0xFF1E2244.toInt() else 0xFFEEF0FF.toInt() // Subtle Indigo
            else -> if (isNightMode) 0xFF171B2F.toInt() else 0xFFEFF1F3.toInt()
        }
        val strokeColor = when {
            isToday -> if (isNightMode) 0xFF7C7FFF.toInt() else 0xFF5B5FEF.toInt() // Primary Indigo
            inTemplateRange && isSelected -> Color.WHITE
            inTemplateRange -> if (isNightMode) 0xFFFFE0B2.toInt() else 0xFFE65100.toInt()
            isSelected -> if (isNightMode) 0xFFFFE0B2.toInt() else 0xFFE65100.toInt()
            hasExercises -> if (isNightMode) 0xFFFFCC80.toInt() else 0xFFFF9800.toInt()
            else -> if (isNightMode) 0xFF4A526E.toInt() else 0xFFDDE1E6.toInt()
        }
        val strokeWidth = ((if (isToday || isSelected) 3 else if (inTemplateRange || hasExercises) 2 else 1) * resources.displayMetrics.density).toInt()

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * resources.displayMetrics.density
            setColor(bgColor)
            setStroke(strokeWidth, strokeColor)
        }
        button.background = drawable
        val textColor = when {
            inTemplateRange -> Color.WHITE
            isSelected -> if (isNightMode) Color.WHITE else 0xFF4A1800.toInt()
            hasExercises -> if (isNightMode) Color.WHITE else 0xFFE65100.toInt()
            isToday -> if (isNightMode) Color.WHITE else 0xFF5B5FEF.toInt()
            else -> if (isNightMode) 0xFFF3F5FF.toInt() else 0xFF1A1A2E.toInt()
        }
        button.setTextColor(textColor)
        button.setTypeface(button.typeface, if (isSelected || isToday || hasExercises || inTemplateRange) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
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
        val todayStr = dateFormatter.format(Date())
        val list = planManager.getExercisesForDate(dateStr).sortedBy { it.hour * 60 + it.minute }
        adapter.submitList(list)
        
        val count = list.size
        val exWord = if (count == 1) "exercise" else "exercises"
        val timeWord = when {
            dateStr == todayStr -> "planned for today"
            dateStr < todayStr -> "were planned for this day"
            else -> "planned for this day"
        }
        findViewById<TextView>(R.id.tvExerciseCount).text = "$count $exWord $timeWord"
        
        setupDateStrip()
    }

    private fun showTemplateActionsDialog() {
        showTemplateActionDialog(
            title = "Workout Templates",
            message = "Create reusable workout plans, manage saved templates, or review ranges already applied to the calendar.",
            actions = listOf(
                TemplateDialogAction("Create template", "Save workouts from a selected calendar range as a reusable plan.") { showCreateTemplateDialog() },
                TemplateDialogAction("Saved templates", "Apply, view, rename, or delete your saved workout templates.") { showManageSavedWorkoutTemplatesDialog() },
                TemplateDialogAction("Applied workout plans", "Review or remove template markers already placed on the calendar.") { showAppliedWorkoutPlansDialog() }
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
            "Remove applied marker"
        )
        AlertDialog.Builder(this)
            .setTitle(range.templateName)
            .setMessage("Applied from ${range.startDate} to ${range.endDate} • ${daysBetweenInclusive(range.startDate, range.endDate)} day(s)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAppliedWorkoutRangeDetails(range)
                    1 -> {
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
        DialogInputHelper.hideKeyboardOnDone(etName)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(etName)
            .setPositiveButton("Save") { _, _ ->
                DialogInputHelper.hideKeyboard(etName)
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
        val etName = v.findViewById<AutoCompleteTextView>(R.id.etExName)
        val etSets = v.findViewById<EditText>(R.id.etExSets)
        val etReps = v.findViewById<EditText>(R.id.etExReps)
        val etRestSeconds = v.findViewById<EditText>(R.id.etExRestSeconds)
        val etAddedWeightKg = v.findViewById<EditText>(R.id.etExAddedWeightKg)
        val etDurationMinutes = v.findViewById<EditText>(R.id.etExDurationMinutes)
        val etDistanceKm = v.findViewById<EditText>(R.id.etExDistanceKm)
        val etRounds = v.findViewById<EditText>(R.id.etExRounds)
        val etWorkSeconds = v.findViewById<EditText>(R.id.etExWorkSeconds)
        val etHiitRestSeconds = v.findViewById<EditText>(R.id.etExHiitRestSeconds)
        val tvTime = v.findViewById<TextView>(R.id.tvExTime)
        val tvExerciseTypeHelp = v.findViewById<TextView>(R.id.tvExerciseTypeHelp)
        val tvEstimatedBurn = v.findViewById<TextView>(R.id.tvEstimatedBurn)
        val tvEstimatedBurnDetail = v.findViewById<TextView>(R.id.tvEstimatedBurnDetail)
        val btnTime = v.findViewById<Button>(R.id.btnPickExTime)
        val swReminder = v.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swExReminder)
        val etTarget = v.findViewById<EditText>(R.id.etExTarget)
        val spinnerType = v.findViewById<Spinner>(R.id.spinnerExType)
        val spinnerEffort = v.findViewById<Spinner>(R.id.spinnerExEffort)
        val spinnerRestUnit = v.findViewById<Spinner>(R.id.spinnerExRestUnit)
        val spinnerHiitRestUnit = v.findViewById<Spinner>(R.id.spinnerExHiitRestUnit)
        val sectionStrength = v.findViewById<View>(R.id.sectionStrength)
        val sectionDuration = v.findViewById<View>(R.id.sectionDuration)
        val sectionCardioMetrics = v.findViewById<View>(R.id.sectionCardioMetrics)
        val sectionHiit = v.findViewById<View>(R.id.sectionHiit)
        val groupAddedWeight = v.findViewById<View>(R.id.groupAddedWeight)
        val tvPaceSummary = v.findViewById<TextView>(R.id.tvPaceSummary)
        val groupReps = etReps.parent as View
        DialogInputHelper.hideKeyboardOnDone(
            etName,
            etTarget,
            etSets,
            etReps,
            etRestSeconds,
            etAddedWeightKg,
            etDurationMinutes,
            etDistanceKm,
            etRounds,
            etWorkSeconds,
            etHiitRestSeconds
        )

        fun setupSpinner(spinner: Spinner, values: List<String>) {
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, values).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        setupSpinner(spinnerType, exerciseTypes)
        setupSpinner(spinnerEffort, effortLabels)
        setupSpinner(spinnerRestUnit, restUnits)
        setupSpinner(spinnerHiitRestUnit, restUnits)

        etName.threshold = 1
        etName.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, exercisePresets.map { it.name }))
        etName.setOnClickListener { etName.showDropDown() }

        var selHour = existing?.hour ?: 7
        var selMin = existing?.minute ?: 0
        fun updateTimeLabel() {
            val h = if (selHour == 0 || selHour == 12) 12 else selHour % 12
            val amPm = if (selHour < 12) "AM" else "PM"
            tvTime.text = "%02d:%02d %s".format(h, selMin, amPm)
        }
        updateTimeLabel()

        spinnerType.setSelection(indexOrDefault(exerciseTypes, existing?.exerciseType, 0))
        spinnerEffort.setSelection(indexOrDefault(effortLabels, existing?.effortLabel ?: effortLabelForIntensity(existing?.intensity ?: 50), 1))

        existing?.let {
            etName.setText(it.name)
            etSets.setText(it.sets.toString())
            etReps.setText(firstNumberText(it.reps, "10"))
            swReminder.isChecked = it.isReminderEnabled
            etTarget.setText(it.targetArea)
            val restDisplay = restDisplayValueAndUnit(it.restSeconds.takeIf { value -> value > 0 } ?: 60)
            etRestSeconds.setText(formatDecimal(restDisplay.first))
            spinnerRestUnit.setSelection(indexOrDefault(restUnits, restDisplay.second, 0))
            etAddedWeightKg.setText(if (it.addedWeightKg > 0.0) formatDecimal(it.addedWeightKg) else "")
            etDurationMinutes.setText(formatDecimal(durationMinutesFromExercise(it)))
            etDistanceKm.setText(if (it.distanceKm > 0.0) formatDecimal(it.distanceKm) else "")
            etRounds.setText((it.rounds.takeIf { value -> value > 0 } ?: it.sets.takeIf { value -> value > 0 } ?: 4).toString())
            etWorkSeconds.setText((it.workSeconds.takeIf { value -> value > 0 } ?: secondsFromRepsText(it.reps) ?: 40).toString())
            val hiitRestDisplay = restDisplayValueAndUnit(it.restSeconds.takeIf { value -> value > 0 } ?: 20)
            etHiitRestSeconds.setText(formatDecimal(hiitRestDisplay.first))
            spinnerHiitRestUnit.setSelection(indexOrDefault(restUnits, hiitRestDisplay.second, 0))
        }

        fun selectedType() = spinnerType.selectedItem?.toString() ?: exerciseTypes.first()
        fun selectedEffort() = spinnerEffort.selectedItem?.toString() ?: "Moderate"
        fun selectedRestUnit() = spinnerRestUnit.selectedItem?.toString() ?: "Sec"
        fun selectedHiitRestUnit() = spinnerHiitRestUnit.selectedItem?.toString() ?: "Sec"
        var lastConfiguredType: String? = null

        fun setTextIfDefault(field: EditText, value: String, defaults: Set<String>) {
            val current = field.text.toString().trim()
            if (current.isBlank() || current in defaults) field.setText(value)
        }

        fun applyDefaultsForTypeSwitch(type: String) {
            val commonRestDefaults = setOf("0", "20", "45", "60", "90")
            val commonDurationDefaults = setOf("1", "5", "10")
            setTextIfDefault(etRestSeconds, defaultRestForType(type).toString(), commonRestDefaults)
            spinnerRestUnit.setSelection(0)
            setTextIfDefault(etDurationMinutes, formatDecimal(defaultDurationMinutesForType(type)), commonDurationDefaults)
            setTextIfDefault(etHiitRestSeconds, defaultRestForType(type).takeIf { it > 0 }?.toString() ?: "20", commonRestDefaults)
            spinnerHiitRestUnit.setSelection(0)
            setTextIfDefault(etRounds, "4", setOf("3", "4"))
            setTextIfDefault(etWorkSeconds, "40", setOf("30", "40", "45", "60"))
            if (etTarget.text.isNullOrBlank()) etTarget.setText(defaultTargetForType(type))
        }

        fun applyPreset(preset: ExercisePreset) {
            etName.setText(preset.name, false)
            spinnerType.setSelection(indexOrDefault(exerciseTypes, preset.type, 0))
            spinnerEffort.setSelection(indexOrDefault(effortLabels, preset.effort, 1))
            etTarget.setText(preset.target)
            etSets.setText(preset.sets.toString())
            etReps.setText(preset.reps.toString())
            etDurationMinutes.setText(formatDecimal(preset.durationMinutes))
            etRounds.setText(preset.rounds.toString())
            etWorkSeconds.setText(preset.workSeconds.toString())
            etRestSeconds.setText(formatDecimal(preset.restValue))
            etHiitRestSeconds.setText(formatDecimal(preset.restValue))
            spinnerRestUnit.setSelection(indexOrDefault(restUnits, preset.restUnit, 0))
            spinnerHiitRestUnit.setSelection(indexOrDefault(restUnits, preset.restUnit, 0))
            etAddedWeightKg.setText(if (preset.addedWeightKg > 0.0) formatDecimal(preset.addedWeightKg) else "")
        }

        fun updateTypeHelp(type: String) {
            tvExerciseTypeHelp.text = when (type) {
                "Strength / Bodyweight" -> "Use sets, reps and rest. Best for push ups, pull ups, squats, lunges and dips."
                "Strength / Weighted" -> "Use sets, reps, rest and added load. Best for dumbbells, barbells and kettlebells."
                "Cardio" -> "Use total duration. Best for running, cycling, skipping, dance and steady cardio."
                "HIIT / Circuit" -> "Use rounds, work seconds and rest seconds. Best for burpees, high knees and intervals."
                "Timed Hold / Isometric" -> "Use sets, hold duration and rest. Best for planks, wall sits and static holds."
                "Mobility / Stretching / Yoga" -> "Use total duration. Calorie burn stays conservative for mobility-focused work."
                "Warmup / Cooldown" -> "Use total duration. This is estimated as light movement unless effort is raised."
                else -> getString(R.string.exercise_type_help_default)
            }
        }

        fun configureFields() {
            val type = selectedType()
            val isWeighted = type == "Strength / Weighted"
            val isStrength = type == "Strength / Bodyweight" || isWeighted
            val isHiit = type == "HIIT / Circuit"
            val isHold = type == "Timed Hold / Isometric"
            val isDurationOnly = type == "Cardio" || type == "Mobility / Stretching / Yoga" || type == "Warmup / Cooldown"

            sectionStrength.visibility = if (isStrength || isHold) View.VISIBLE else View.GONE
            sectionDuration.visibility = if (isDurationOnly || isHold) View.VISIBLE else View.GONE
            sectionCardioMetrics.visibility = if (type == "Cardio") View.VISIBLE else View.GONE
            sectionHiit.visibility = if (isHiit) View.VISIBLE else View.GONE
            groupReps.visibility = if (isHold) View.GONE else View.VISIBLE
            groupAddedWeight.visibility = if (isWeighted) View.VISIBLE else View.GONE

            if (lastConfiguredType != null && lastConfiguredType != type) applyDefaultsForTypeSwitch(type)
            if (isHold && etDurationMinutes.text.isNullOrBlank()) etDurationMinutes.setText("1")
            updateTypeHelp(type)
            tvEstimatedBurnDetail.text = "Approximate MET-based estimate. ${selectedEffort()} effort maps to ${intensityForEffort(selectedEffort())}% internally."
            lastConfiguredType = type
        }

        fun buildExerciseFromInputs(requireName: Boolean): Exercise? {
            val type = selectedType()
            val effort = selectedEffort()
            val intensity = intensityForEffort(effort)
            val name = etName.text.toString().trim()
            if (requireName && name.isEmpty()) {
                etName.error = "Required"
                return null
            }
            val safeName = name.ifEmpty { type }
            val target = etTarget.text.toString().trim().ifEmpty { defaultTargetForType(type) }

            val finalSets: Int
            val finalReps: String
            val finalDurationSeconds: Int
            val finalRestSeconds: Int
            val finalRounds: Int
            val finalWorkSeconds: Int

            when (type) {
                "Cardio", "Mobility / Stretching / Yoga", "Warmup / Cooldown" -> {
                    val durationMinutes = readDoubleField(etDurationMinutes, "Duration", defaultDurationMinutesForType(type), 0.1, 1440.0, requireName) ?: return null
                    val durationSeconds = (durationMinutes * 60).toInt()
                    if (requireName && durationSeconds <= 0) {
                        etDurationMinutes.error = "Required"
                        return null
                    }
                    finalSets = 1
                    finalReps = "${formatDecimal(durationSeconds / 60.0)} min"
                    finalDurationSeconds = durationSeconds
                    finalRestSeconds = 0
                    finalRounds = 0
                    finalWorkSeconds = 0
                }
                "HIIT / Circuit" -> {
                    val rounds = readIntField(etRounds, "Rounds", 4, 1, 200, requireName) ?: return null
                    val workSeconds = readIntField(etWorkSeconds, "Work seconds", 40, 1, 3600, requireName) ?: return null
                    val hiitRestSeconds = readRestSecondsField(etHiitRestSeconds, selectedHiitRestUnit(), "Rest", 20, requireName) ?: return null
                    finalSets = rounds
                    finalReps = "$workSeconds sec"
                    finalDurationSeconds = 0
                    finalRestSeconds = hiitRestSeconds
                    finalRounds = rounds
                    finalWorkSeconds = workSeconds
                }
                "Timed Hold / Isometric" -> {
                    val sets = readIntField(etSets, "Sets", 1, 1, 500, requireName) ?: return null
                    val restSeconds = readRestSecondsField(etRestSeconds, selectedRestUnit(), "Rest", defaultRestForType(type), requireName) ?: return null
                    val durationMinutes = readDoubleField(etDurationMinutes, "Hold duration", defaultDurationMinutesForType(type), 0.1, 1440.0, requireName) ?: return null
                    val durationSeconds = (durationMinutes * 60).toInt()
                    if (requireName && durationSeconds <= 0) {
                        etDurationMinutes.error = "Required"
                        return null
                    }
                    finalSets = sets
                    finalReps = "$durationSeconds sec"
                    finalDurationSeconds = durationSeconds
                    finalRestSeconds = restSeconds
                    finalRounds = 0
                    finalWorkSeconds = 0
                }
                else -> {
                    val sets = readIntField(etSets, "Sets", 1, 1, 500, requireName) ?: return null
                    val reps = readIntField(etReps, "Reps", 10, 1, 1000, requireName) ?: return null
                    val restSeconds = readRestSecondsField(etRestSeconds, selectedRestUnit(), "Rest", defaultRestForType(type), requireName) ?: return null
                    finalSets = sets
                    finalReps = reps.toString()
                    finalDurationSeconds = 0
                    finalRestSeconds = restSeconds
                    finalRounds = 0
                    finalWorkSeconds = 0
                }
            }

            val addedWeightKg = if (type == "Strength / Weighted") {
                readDoubleField(etAddedWeightKg, "Added weight", 0.0, 0.0, 500.0, requireName = false) ?: 0.0
            } else {
                0.0
            }
            val distanceKm = if (type == "Cardio") {
                readDoubleField(etDistanceKm, "Distance", 0.0, 0.0, 1000.0, requireName = false) ?: 0.0
            } else {
                0.0
            }

            return Exercise(
                id = existing?.id ?: Exercise().id,
                name = safeName,
                sets = finalSets,
                reps = finalReps,
                hour = selHour,
                minute = selMin,
                isReminderEnabled = swReminder.isChecked,
                targetArea = target,
                intensity = intensity,
                exerciseType = type,
                effortLabel = effort,
                durationSeconds = finalDurationSeconds,
                restSeconds = finalRestSeconds,
                rounds = finalRounds,
                workSeconds = finalWorkSeconds,
                addedWeightKg = addedWeightKg,
                distanceKm = distanceKm
            )
        }

        fun updatePaceSummary() {
            val distanceKm = etDistanceKm.text.toString().trim().toDoubleOrNull() ?: 0.0
            val durationMinutes = etDurationMinutes.text.toString().trim().toDoubleOrNull() ?: 0.0
            tvPaceSummary.text = if (selectedType() == "Cardio" && distanceKm > 0.0 && durationMinutes > 0.0) {
                val paceMinPerKm = durationMinutes / distanceKm
                val speedKmH = distanceKm / (durationMinutes / 60.0)
                "Pace: ${formatPace(paceMinPerKm)} min/km • Speed: ${formatDecimal(speedKmH)} km/h"
            } else {
                getString(R.string.exercise_pace_summary_empty)
            }
        }

        fun updateEstimateFromInputs() {
            val estimateExercise = buildExerciseFromInputs(requireName = false)
            val dateStr = dateFormatter.format(selectedCalendar.time)
            val weight = HealthDataManager(this).getWeight(dateStr).let { if (it > 0) it else 70.0 }
            val burn = estimateExercise?.let { WellnessEngine.estimateWorkoutBurnForExercise(it, weight) } ?: 0.0
            updatePaceSummary()
            tvEstimatedBurn.text = getString(R.string.exercise_estimated_burn_value, burn.toInt())
        }

        fun bindTextWatchers(vararg fields: EditText) {
            fields.forEach { field ->
                field.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) = updateEstimateFromInputs()
                })
            }
        }

        bindTextWatchers(etName, etTarget, etSets, etReps, etRestSeconds, etAddedWeightKg, etDurationMinutes, etDistanceKm, etRounds, etWorkSeconds, etHiitRestSeconds)
        etName.setOnItemClickListener { _, _, position, _ ->
            val selected = etName.adapter.getItem(position)?.toString().orEmpty()
            exercisePresets.firstOrNull { it.name.equals(selected, ignoreCase = true) }?.let { applyPreset(it) }
        }
        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                configureFields()
                updateEstimateFromInputs()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        spinnerEffort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                tvEstimatedBurnDetail.text = "Approximate MET-based estimate. ${selectedEffort()} effort maps to ${intensityForEffort(selectedEffort())}% internally."
                updateEstimateFromInputs()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        spinnerRestUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateEstimateFromInputs()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        spinnerHiitRestUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateEstimateFromInputs()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        configureFields()
        updateEstimateFromInputs()

        fun showExerciseTimePicker() {
            DialogInputHelper.hideKeyboard(v)
            TimePickerDialog(this, { _, h, m ->
                selHour = h
                selMin = m
                updateTimeLabel()
            }, selHour, selMin, false).show()
        }
        btnTime.setOnClickListener { showExerciseTimePicker() }
        DialogInputHelper.makeTimeLabelClickable(tvTime, "Change workout time") { showExerciseTimePicker() }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "Add Exercise" else "Edit Exercise")
            .setView(v)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            DialogInputHelper.hideKeyboard(v)
            val newEx = buildExerciseFromInputs(requireName = true) ?: return@setOnClickListener

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
            current.intensity == saved.intensity &&
            current.exerciseType == saved.exerciseType &&
            current.effortLabel == saved.effortLabel &&
            current.durationSeconds == saved.durationSeconds &&
            current.restSeconds == saved.restSeconds &&
            current.rounds == saved.rounds &&
            current.workSeconds == saved.workSeconds &&
            current.addedWeightKg == saved.addedWeightKg &&
            current.distanceKm == saved.distanceKm
    }

    private fun indexOrDefault(values: List<String>, selected: String?, defaultIndex: Int): Int {
        val idx = values.indexOf(selected)
        return if (idx >= 0) idx else defaultIndex.coerceIn(values.indices)
    }

    private fun intensityForEffort(effort: String): Int {
        return when (effort) {
            "Light" -> 25
            "Hard" -> 75
            "Very Hard" -> 90
            "Max" -> 100
            else -> 50
        }
    }

    private fun effortLabelForIntensity(intensity: Int): String {
        return when {
            intensity >= 95 -> "Max"
            intensity >= 85 -> "Very Hard"
            intensity >= 65 -> "Hard"
            intensity <= 35 -> "Light"
            else -> "Moderate"
        }
    }

    private fun defaultTargetForType(type: String): String {
        return when (type) {
            "Cardio", "HIIT / Circuit" -> "Full Body / Cardio"
            "Mobility / Stretching / Yoga" -> "Mobility"
            "Warmup / Cooldown" -> "Warmup"
            "Timed Hold / Isometric" -> "Core"
            else -> "Full Body"
        }
    }

    private fun defaultRestForType(type: String): Int {
        return when (type) {
            "HIIT / Circuit" -> 20
            "Timed Hold / Isometric" -> 45
            "Strength / Weighted" -> 90
            "Strength / Bodyweight" -> 60
            else -> 0
        }
    }

    private fun defaultDurationMinutesForType(type: String): Double {
        return when (type) {
            "Timed Hold / Isometric" -> 1.0
            "Mobility / Stretching / Yoga", "Warmup / Cooldown" -> 5.0
            else -> 10.0
        }
    }

    private fun firstNumberText(text: String, fallback: String): String {
        return Regex("""\d+(?:\.\d+)?""").find(text)?.value ?: fallback
    }

    private fun secondsFromRepsText(text: String): Int? {
        return Regex("""(\d+)\s*(sec|secs|second|seconds)\b""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun durationMinutesFromExercise(exercise: Exercise): Double {
        if (exercise.durationSeconds > 0) return exercise.durationSeconds / 60.0
        return Regex("""(\d+(?:\.\d+)?)\s*(min|mins|minute|minutes)\b""", RegexOption.IGNORE_CASE)
            .find(exercise.reps)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?: 10.0
    }

    private fun readIntField(field: EditText, label: String, fallback: Int, min: Int, max: Int, requireName: Boolean): Int? {
        val raw = field.text.toString().trim()
        if (raw.isBlank()) {
            if (requireName) {
                field.error = "$label required"
                return null
            }
            return fallback
        }
        val value = raw.toIntOrNull()
        if (value == null || value !in min..max) {
            if (requireName) {
                field.error = "Enter $min-$max"
                return null
            }
            return fallback
        }
        field.error = null
        return value
    }

    private fun readDoubleField(field: EditText, label: String, fallback: Double, min: Double, max: Double, requireName: Boolean): Double? {
        val raw = field.text.toString().trim()
        if (raw.isBlank()) {
            if (requireName) {
                field.error = "$label required"
                return null
            }
            return fallback
        }
        val value = raw.toDoubleOrNull()
        if (value == null || value < min || value > max) {
            if (requireName) {
                field.error = "Enter ${formatDecimal(min)}-${formatDecimal(max)}"
                return null
            }
            return fallback
        }
        field.error = null
        return value
    }

    private fun readRestSecondsField(field: EditText, unit: String, label: String, fallbackSeconds: Int, requireName: Boolean): Int? {
        val maxSeconds = 600
        val fallbackValue = if (unit == "Min") fallbackSeconds / 60.0 else fallbackSeconds.toDouble()
        val maxValue = if (unit == "Min") maxSeconds / 60.0 else maxSeconds.toDouble()
        val value = readDoubleField(field, label, fallbackValue, 0.0, maxValue, requireName) ?: return null
        return if (unit == "Min") (value * 60).roundToInt().coerceIn(0, maxSeconds) else value.roundToInt().coerceIn(0, maxSeconds)
    }

    private fun restDisplayValueAndUnit(restSeconds: Int): Pair<Double, String> {
        return if (restSeconds >= 60 && restSeconds % 60 == 0) {
            (restSeconds / 60.0) to "Min"
        } else {
            restSeconds.toDouble() to "Sec"
        }
    }

    private fun formatPace(minutesPerKm: Double): String {
        val totalSeconds = (minutesPerKm * 60).toInt().coerceAtLeast(0)
        return "%d:%02d".format(Locale.US, totalSeconds / 60, totalSeconds % 60)
    }

    private fun formatDecimal(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.US, value)
    }

    private fun exerciseSummary(ex: Exercise): String {
        val effort = ex.effortLabel ?: effortLabelForIntensity(ex.intensity)
        val type = ex.exerciseType?.takeIf { it.isNotBlank() }
        val load = if (ex.addedWeightKg > 0.0) " • +${formatDecimal(ex.addedWeightKg)}kg" else ""
        val work = when {
            ex.rounds > 0 && ex.workSeconds > 0 -> "${ex.rounds} rounds x ${ex.workSeconds}s"
            ex.durationSeconds > 0 && ex.exerciseType == "Timed Hold / Isometric" -> "${ex.sets} x ${ex.durationSeconds}s"
            ex.durationSeconds > 0 && ex.distanceKm > 0.0 -> "${formatDecimal(ex.durationSeconds / 60.0)} min • ${formatDecimal(ex.distanceKm)} km"
            ex.durationSeconds > 0 -> "${formatDecimal(ex.durationSeconds / 60.0)} min"
            else -> "${ex.sets}x${ex.reps}"
        }
        return listOfNotNull(work, ex.targetArea.takeIf { it.isNotBlank() }, effort, type).joinToString(" • ") + load
    }

    private fun editExercise(ex: Exercise) {
        showExerciseDialog(ex)
    }

    inner class ExerciseAdapter(private val onEdit: (Exercise) -> Unit) : RecyclerView.Adapter<ExerciseAdapter.VH>() {
        private var items = listOf<Exercise>()
        private val selectedIds = mutableSetOf<Int>()

        fun getSelectedIds(): Set<Int> = selectedIds

        fun clearSelection() {
            selectedIds.clear()
            notifyDataSetChanged()
            onSelectionChanged(0)
        }

        fun toggleSelection(id: Int) {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id)
            } else {
                selectedIds.add(id)
            }
            notifyDataSetChanged()
            onSelectionChanged(selectedIds.size)
        }

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
            val isSelected = selectedIds.contains(ex.id)

            holder.ivIcon.setImageResource(if (isDone) R.drawable.ic_check else R.drawable.ic_workout)
            holder.ivIcon.setBackgroundResource(if (isDone) R.drawable.bg_circle_walking else R.drawable.bg_circle_workout)
            holder.tvTitle.text = ex.name
            holder.tvSubtitle.text = exerciseSummary(ex)
            holder.tvTime.text = ex.formatTime()

            val cardView = holder.itemView as MaterialCardView
            val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            if (isSelected) {
                cardView.strokeColor = if (isNightMode) Color.parseColor("#FFB74D") else Color.parseColor("#FF9800")
                cardView.strokeWidth = dp(2)
                cardView.setCardBackgroundColor(if (isNightMode) Color.parseColor("#4A2C10") else Color.parseColor("#FFE0B2"))
            } else {
                cardView.strokeColor = ContextCompat.getColor(this@WorkoutPlanActivity, R.color.cardStroke)
                cardView.strokeWidth = dp(1)
                cardView.setCardBackgroundColor(ContextCompat.getColor(this@WorkoutPlanActivity, R.color.cardBg))
            }

            holder.itemView.setOnLongClickListener {
                if (selectedIds.isEmpty()) {
                    toggleSelection(ex.id)
                }
                true
            }

            holder.itemView.setOnClickListener { 
                if (selectedIds.isNotEmpty()) {
                    toggleSelection(ex.id)
                } else {
                    onEdit(ex) 
                }
            }
            
            val switch = holder.itemView.findViewById<SwitchCompat>(R.id.switchEnabled)
            switch.visibility = if (selectedIds.isNotEmpty()) View.GONE else View.VISIBLE
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = isDone
            switch.setOnCheckedChangeListener { _, checked ->
                RoutineProgressStore.setDoneStatus(this@WorkoutPlanActivity, dateStr, ex.id, checked)
                refreshExercises()
            }

            val btnDelete = holder.itemView.findViewById<View>(R.id.btnDelete)
            btnDelete.visibility = if (selectedIds.isNotEmpty()) View.GONE else View.VISIBLE
            btnDelete.setOnClickListener {
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
