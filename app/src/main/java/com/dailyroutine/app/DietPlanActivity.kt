package com.dailyroutine.app

import android.app.AlertDialog
import android.app.DatePickerDialog
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

class DietPlanActivity : AppCompatActivity() {

    private lateinit var planManager: PlanManager
    private lateinit var adapter: MealAdapter
    private val calendar = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)

    private data class TemplateDialogAction(
        val title: String,
        val description: String,
        val onClick: () -> Unit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diet_plan)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomPadding(findViewById(R.id.rvMeals))
        InsetHelper.applyBottomMargin(findViewById(R.id.btnQuickRoutine))
        InsetHelper.applyBottomMargin(findViewById(R.id.fabAddMeal))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        planManager = PlanManager(this)
        
        setupRecyclerView()
        setupDatePickers()
        refreshMeals()

        findViewById<FloatingActionButton>(R.id.fabAddMeal).setOnClickListener {
            showEditMealDialog(null)
        }

        findViewById<MaterialButton>(R.id.btnQuickRoutine).setOnClickListener {
            planManager.addQuickIndianDiet(dateFormatter.format(calendar.time))
            refreshMeals()
        }

        findViewById<MaterialButton>(R.id.btnTemplatesDiet).setOnClickListener {
            showTemplateActionsDialog()
        }
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvMeals)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = MealAdapter(
            onEdit = { showEditMealDialog(it) },
            onDelete = { deleteMeal(it) }
        )
        rv.adapter = adapter
    }

    private fun setupDatePickers() {
        val tvSelectedDate = findViewById<TextView>(R.id.tvSelectedDate)
        val btnPickDate = findViewById<Button>(R.id.btnPickDate)

        val updateDateText = {
            tvSelectedDate.text = displayFormatter.format(calendar.time)
            refreshMeals()
            updateDaysStrip()
        }

        btnPickDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                calendar.set(y, m, d)
                updateDateText()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        updateDateText()
    }

    private fun updateDaysStrip() {
        val llDays = findViewById<LinearLayout>(R.id.llDays)
        llDays.removeAllViews()
        
        val tempCal = calendar.clone() as Calendar
        tempCal.add(Calendar.DAY_OF_YEAR, -3)

        val stripDateFormatter = SimpleDateFormat("EEE\ndd", Locale.US)

        for (i in 0 until 7) {
            val dateStr = dateFormatter.format(tempCal.time)
            val isSelected = dateStr == dateFormatter.format(calendar.time)
            val hasMeals = planManager.hasMealsForDate(dateStr)
            val inTemplateRange = planManager.isDateInAppliedMealTemplateRange(dateStr)

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
                    calendar.time = clickedCal.time
                    this@DietPlanActivity.findViewById<TextView>(R.id.tvSelectedDate).text = displayFormatter.format(calendar.time)
                    refreshMeals()
                    updateDaysStrip()
                }
                alpha = if (isSelected || hasMeals || inTemplateRange) 1f else 0.55f
            }
            styleMealStripButton(btn, isSelected, inTemplateRange, hasMeals)
            llDays.addView(btn)
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun styleMealStripButton(button: Button, isSelected: Boolean, inTemplateRange: Boolean, hasMeals: Boolean) {
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val bgColor = when {
            inTemplateRange -> if (isNightMode) 0xFFFF1744.toInt() else 0xFFD50000.toInt()
            isSelected -> if (isNightMode) 0x40FFFFFF else 0x33000000
            hasMeals -> if (isNightMode) 0x99E57373.toInt() else 0x88D32F2F.toInt()
            else -> 0x00FFFFFF
        }
        val strokeColor = when {
            inTemplateRange && isSelected -> if (isNightMode) Color.WHITE else Color.BLACK
            inTemplateRange -> Color.WHITE
            isSelected -> if (isNightMode) 0xCCFFFFFF.toInt() else 0xCC000000.toInt()
            hasMeals -> if (isNightMode) 0xFFE57373.toInt() else 0xFFB71C1C.toInt()
            else -> if (isNightMode) 0x66FFFFFF else 0x66000000
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
            isSelected -> if (isNightMode) Color.WHITE else Color.BLACK
            hasMeals -> if (isNightMode) Color.WHITE else Color.BLACK
            else -> if (isNightMode) Color.argb(230, 255, 255, 255) else Color.argb(230, 0, 0, 0)
        }
        button.setTextColor(textColor)
    }

    private fun showTemplateActionsDialog() {
        showTemplateActionDialog(
            title = "Meal Templates",
            message = "Choose what you want to do. Large rows are buttons; smaller text only explains the action.",
            actions = listOf(
                TemplateDialogAction("Apply meal template", "Pick a saved meal plan and date range.") { showApplyTemplatePicker() },
                TemplateDialogAction("Applied meal plans", "See where meal templates are active on the calendar.") { showAppliedMealPlansDialog() },
                TemplateDialogAction("Create from calendar", "Save meals from an existing date range as a reusable template.") { showCreateTemplateDialog() },
                TemplateDialogAction("Create empty template", "Create a blank template structure for future planning.") { showCreateNewTemplateDialog() },
                TemplateDialogAction("Manage saved templates", "Open templates to apply, view, rename, duplicate, or delete.") { showManageSavedMealTemplatesDialog() }
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
                    setColor(if (isNightMode) 0xFF2A1A1A.toInt() else 0xFFFFF5F5.toInt())
                    setStroke(dp(1), if (isNightMode) 0xFFFF8A80.toInt() else 0xFFD50000.toInt())
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
                setTextColor(if (isNightMode) Color.WHITE else 0xFF4A0000.toInt())
            })
            row.addView(TextView(this).apply {
                text = action.description
                textSize = 12f
                setPadding(0, dp(3), 0, 0)
                setTextColor(if (isNightMode) Color.argb(220, 255, 255, 255) else 0xFF7A1E1E.toInt())
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
            promptTemplateName("Create Meal Template", "My Meal Plan") { name ->
                val ok = planManager.createMealTemplateFromRange(
                    name = name,
                    startDate = startDate,
                    endDate = endDate,
                    allowEmpty = false
                )
                if (ok) {
                    Toast.makeText(this, "Template saved for $startDate to $endDate", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No meals found in selected date range", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showCreateNewTemplateDialog() {
        pickDateRange { startDate, endDate ->
            promptTemplateName("Create New Meal Template", "New Meal Template") { name ->
                val ok = planManager.createMealTemplateFromRange(
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
        val templates = planManager.listMealTemplates()
        if (templates.isEmpty()) {
            Toast.makeText(this, "No meal templates found", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = templates.map {
            "${it.name} • ${it.durationDays} days • ${mealTemplateMealCount(it)} meal(s) • ${mealTemplateAppliedCount(it)} applied"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Apply Meal Template")
            .setItems(labels) { _, which ->
                val template = templates[which]
                pickMealTemplateRangeAndConfirm(template)
            }
            .show()
    }

    private fun pickMealTemplateRangeAndConfirm(template: MealTemplate) {
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
            confirmMealTemplateApply(template, startDate, endDate, selectedDays)
        }
    }

    private fun confirmMealTemplateApply(template: MealTemplate, startDate: String, endDate: String, selectedDays: Int) {
        val configuredDays = mealTemplateConfiguredDayCount(template)
        val totalMeals = mealTemplateMealCount(template)
        AlertDialog.Builder(this)
            .setTitle("Apply '${template.name}'?")
            .setMessage(
                "Template length: ${template.durationDays} days\n" +
                    "Configured days: $configuredDays\n" +
                    "Meal entries inside template: $totalMeals\n" +
                    "Already applied ranges: ${mealTemplateAppliedCount(template)}\n" +
                    "Selected range: $startDate to $endDate ($selectedDays days)\n" +
                    "Target dates: ${buildDateRangePreview(startDate, selectedDays)}\n\n" +
                    "Meals from this template will be copied into the selected calendar dates."
            )
            .setPositiveButton("Apply") { _, _ ->
                applyMealTemplateToRange(template, startDate, endDate, selectedDays)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyMealTemplateToRange(template: MealTemplate, startDate: String, endDate: String, selectedDays: Int) {
        val result = planManager.applyMealTemplateToRange(template.id, startDate, endDate)
        if (result.applied) {
            Toast.makeText(this, "Template applied for $startDate to $endDate", Toast.LENGTH_LONG).show()
            refreshMeals()
        } else if (result.conflictRange != null) {
            showMealTemplateOverlapDialog(template, result.conflictRange, selectedDays)
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

    private fun showMealTemplateOverlapDialog(template: MealTemplate, conflict: AppliedTemplateRange, selectedDays: Int) {
        AlertDialog.Builder(this)
            .setTitle("Template overlap detected")
            .setMessage(
                "'${conflict.templateName}' is already applied from ${conflict.startDate} to ${conflict.endDate}.\n\n" +
                    "Start '${template.name}' after ${conflict.endDate}, or review applied meal plans first."
            )
            .setPositiveButton("Start after conflict") { _, _ ->
                val startDate = addDaysToDate(conflict.endDate, 1)
                val endDate = addDaysToDate(startDate, selectedDays - 1)
                confirmMealTemplateApply(template, startDate, endDate, selectedDays)
            }
            .setNeutralButton("View applied plans") { _, _ -> showAppliedMealPlansDialog() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManageSavedMealTemplatesDialog() {
        val templates = planManager.listMealTemplates()
        if (templates.isEmpty()) {
            Toast.makeText(this, "No saved meal templates", Toast.LENGTH_SHORT).show()
            return
        }

        showTemplateActionDialog(
            title = "Manage Meal Templates",
            message = "Tap a template card to open its actions. The smaller line summarizes what is inside and how often it is applied.",
            actions = templates.map { template ->
                TemplateDialogAction(
                    title = template.name,
                    description = "${template.durationDays} days • ${mealTemplateConfiguredDayCount(template)} configured day(s) • ${mealTemplateMealCount(template)} meal(s) • ${mealTemplateAppliedCount(template)} applied"
                ) { showSavedMealTemplateActions(template) }
            }
        )
    }

    private fun showSavedMealTemplateActions(template: MealTemplate) {
        val summary =
                "Duration: ${template.durationDays} days\n" +
                    "Configured days: ${mealTemplateConfiguredDayCount(template)}\n" +
                    "Meal entries: ${mealTemplateMealCount(template)}\n" +
                    "Applied ranges: ${mealTemplateAppliedCount(template)}"

        showTemplateActionDialog(
            title = template.name,
            message = "$summary\n\nChoose an action for this saved meal template.",
            actions = listOf(
                TemplateDialogAction("Apply to calendar", "Choose a date range and copy this template into those days.") { pickMealTemplateRangeAndConfirm(template) },
                TemplateDialogAction("View template details", "See day-by-day meals stored in this template.") { showMealTemplateDetails(template) },
                TemplateDialogAction("Rename template", "Change only the template name; meal contents stay unchanged.") {
                    promptTemplateName("Rename Meal Template", template.name) { newName ->
                        if (planManager.renameMealTemplate(template.id, newName)) {
                            Toast.makeText(this, "Template renamed", Toast.LENGTH_SHORT).show()
                            updateDaysStrip()
                        } else {
                            Toast.makeText(this, "Unable to rename template", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                TemplateDialogAction("Duplicate template", "Create a copy that you can rename and reuse separately.") {
                    promptTemplateName("Duplicate Meal Template", "${template.name} Copy") { newName ->
                        if (planManager.duplicateMealTemplate(template.id, newName)) {
                            Toast.makeText(this, "Template duplicated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Unable to duplicate template", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                TemplateDialogAction("Delete saved template", "Remove this template and its colored applied markers. Copied meals remain on calendar.") { confirmDeleteMealTemplate(template) }
            )
        )
    }

    private fun showMealTemplateDetails(template: MealTemplate) {
        val lines = mutableListOf<String>()
        lines += "Duration: ${template.durationDays} days"
        lines += "Configured days: ${mealTemplateConfiguredDayCount(template)}"
        lines += "Meal entries: ${mealTemplateMealCount(template)}"
        lines += "Applied ranges: ${mealTemplateAppliedCount(template)}"
        lines += ""
        for (offset in 0 until template.durationDays) {
            lines += "Day ${offset + 1}:"
            val meals = template.mealsByDayOffset[offset].orEmpty().sortedBy { it.hour * 60 + it.minute }
            if (meals.isEmpty()) {
                lines += "  No meals configured"
            } else {
                meals.forEach { meal ->
                    lines += "  ${meal.formatTime()} • ${meal.mealType}: ${meal.name}"
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle(template.name)
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton("Close", null)
            .show()
    }

    private fun confirmDeleteMealTemplate(template: MealTemplate) {
        val appliedCount = mealTemplateAppliedCount(template)
        AlertDialog.Builder(this)
            .setTitle("Delete '${template.name}'?")
            .setMessage(
                "Duration: ${template.durationDays} days\n" +
                    "Meal entries: ${mealTemplateMealCount(template)}\n" +
                    "Applied ranges to remove: $appliedCount\n\n" +
                    "This removes the saved template and its applied calendar markers. Existing copied meals will remain on the calendar."
            )
            .setPositiveButton("Delete") { _, _ ->
                planManager.deleteMealTemplate(template.id)
                Toast.makeText(this, "Template deleted", Toast.LENGTH_SHORT).show()
                updateDaysStrip()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAppliedMealPlansDialog() {
        val ranges = planManager.listAppliedMealTemplateRanges()
        if (ranges.isEmpty()) {
            Toast.makeText(this, "No meal templates applied yet", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = ranges.map {
            "${it.templateName} • ${it.startDate} to ${it.endDate} • ${daysBetweenInclusive(it.startDate, it.endDate)} day(s)"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Applied Meal Plans")
            .setItems(labels) { _, which ->
                showAppliedMealRangeActions(ranges[which])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAppliedMealRangeActions(range: AppliedTemplateRange) {
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
                    0 -> showAppliedMealRangeDetails(range)
                    1 -> jumpToMealDate(range.startDate)
                    2 -> jumpToMealDate(range.endDate)
                    3 -> {
                        val removed = planManager.removeAppliedMealTemplateRange(range.startDate, range.endDate)
                        if (removed) {
                            Toast.makeText(this, "Applied meal marker removed", Toast.LENGTH_SHORT).show()
                            updateDaysStrip()
                        } else {
                            Toast.makeText(this, "Unable to remove applied marker", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAppliedMealRangeDetails(range: AppliedTemplateRange) {
        val template = planManager.listMealTemplates().firstOrNull { it.id == range.templateId }
        val lines = mutableListOf<String>()
        lines += "Template: ${range.templateName}"
        lines += "Applied range: ${range.startDate} to ${range.endDate}"
        lines += "Range length: ${daysBetweenInclusive(range.startDate, range.endDate)} day(s)"
        lines += "Calendar color: strong red"
        if (template != null) {
            lines += ""
            lines += "Saved template details:"
            lines += "Duration: ${template.durationDays} days"
            lines += "Configured days: ${mealTemplateConfiguredDayCount(template)}"
            lines += "Meal entries: ${mealTemplateMealCount(template)}"
        }
        lines += ""
        lines += "Removing the marker clears the colored applied range, but copied meals remain on their dates."

        AlertDialog.Builder(this)
            .setTitle("Applied Meal Plan Details")
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton("Jump to start") { _, _ -> jumpToMealDate(range.startDate) }
            .setNeutralButton("Jump to end") { _, _ -> jumpToMealDate(range.endDate) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun jumpToMealDate(date: String) {
        dateFormatter.parse(date)?.let {
            calendar.time = it
            findViewById<TextView>(R.id.tvSelectedDate).text = displayFormatter.format(calendar.time)
            refreshMeals()
            updateDaysStrip()
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
                endPicker.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
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

    private fun mealTemplateConfiguredDayCount(template: MealTemplate): Int {
        return template.mealsByDayOffset.keys.distinct().size
    }

    private fun mealTemplateMealCount(template: MealTemplate): Int {
        return template.mealsByDayOffset.values.sumOf { it.size }
    }

    private fun mealTemplateAppliedCount(template: MealTemplate): Int {
        return planManager.listAppliedMealTemplateRanges().count { it.templateId == template.id }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun refreshMeals() {
        val dateStr = dateFormatter.format(calendar.time)
        val meals = planManager.getMealsForDate(dateStr).sortedBy { it.hour * 60 + it.minute }
        adapter.setMeals(meals)
        
        findViewById<TextView>(R.id.tvMealCount).text = "${meals.size} meals planned for today"
        
        WellnessEngine.calculateIntakeForDate(this, dateStr) { total ->
            runOnUiThread {
                findViewById<TextView>(R.id.tvTotalCaloriesToday).text = "~$total kcal"
            }
        }
        updateDaysStrip()
    }

    private fun showEditMealDialog(meal: Meal?) {
        val dateStr = dateFormatter.format(calendar.time)
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_edit_meal, null)
        val etName = v.findViewById<EditText>(R.id.etMealName)
        val etDesc = v.findViewById<EditText>(R.id.etMealNotes)
        val spinner = v.findViewById<Spinner>(R.id.spinnerMealType)
        val tvTime = v.findViewById<TextView>(R.id.tvMealTime)
        val swReminder = v.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swMealReminder)

        val types = arrayOf("Breakfast", "Lunch", "Dinner", "Snack")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)

        var h = meal?.hour ?: 12
        var m = meal?.minute ?: 0
        fun updateTimeLabel() {
            val displayHour = if (h == 0 || h == 12) 12 else h % 12
            val amPm = if (h < 12) "AM" else "PM"
            tvTime.text = "%02d:%02d %s".format(displayHour, m, amPm)
        }
        updateTimeLabel()
        
        meal?.let {
            etName.setText(it.name)
            etDesc.setText(it.description)
            spinner.setSelection(types.indexOf(it.mealType).coerceAtLeast(0))
            swReminder.isChecked = it.isReminderEnabled
            updateTimeLabel()
        }

        v.findViewById<Button>(R.id.btnPickMealTime).setOnClickListener {
            android.app.TimePickerDialog(this, { _, sh, sm ->
                h = sh; m = sm
                updateTimeLabel()
            }, h, m, false).show()
        }

        AlertDialog.Builder(this)
            .setTitle(if (meal == null) "Add Meal" else "Edit Meal")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Meal name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val loading = Toast.makeText(this, "Calculating calories...", Toast.LENGTH_SHORT)
                loading.show()
                
                CalorieSearchEngine.getCalories(this, name, desc) { cals ->
                    val newMeal = (meal ?: Meal()).copy(
                        name = name,
                        description = desc,
                        mealType = spinner.selectedItem.toString(),
                        hour = h,
                        minute = m,
                        isReminderEnabled = swReminder.isChecked,
                        calories = cals
                    )
                    planManager.saveMealForDate(dateStr, newMeal)
                    planManager.syncMealReminder(this@DietPlanActivity, newMeal, dateStr)
                    refreshMeals()
                    loading.cancel()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMeal(mealId: Int) {
        val dateStr = dateFormatter.format(calendar.time)
        val meal = planManager.getMealsForDate(dateStr).find { it.id == mealId } ?: return
        
        AlertDialog.Builder(this)
            .setTitle("Delete Meal?")
            .setMessage("Remove ${meal.name} from your plan?")
            .setPositiveButton("Delete") { _, _ ->
                planManager.deleteMealForDate(dateStr, meal)
                refreshMeals()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class MealAdapter(
        private val onEdit: (Meal) -> Unit,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<MealAdapter.VH>() {
        private var items = listOf<Meal>()

        fun setMeals(list: List<Meal>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = items[position]
            holder.ivIcon.setImageResource(RoutineIconMapper.iconForMealType(m.mealType))
            holder.ivIcon.setBackgroundResource(RoutineIconMapper.badgeForMealType(m.mealType))
            holder.tvTitle.text = m.name
            holder.tvSubtitle.text = m.mealType
            holder.tvSubtitle.setBackgroundResource(R.drawable.bg_chip)
            holder.tvSubtitle.visibility = View.VISIBLE
            
            if (m.calories > 0) {
                holder.tvTime.text = "${m.formatTime()} • ${m.calories} kcal"
            } else {
                holder.tvTime.text = m.formatTime()
            }

            holder.itemView.setOnClickListener { onEdit(m) }
            
            val btnDelete = holder.itemView.findViewById<View>(R.id.btnDelete)
            btnDelete.visibility = View.VISIBLE
            btnDelete.setOnClickListener { onDelete(m.id) }

            val btnEdit = holder.itemView.findViewById<View>(R.id.btnEdit)
            btnEdit.visibility = View.GONE
            btnEdit.setOnClickListener(null)
            
            // Hide switch as it's not used here
            holder.itemView.findViewById<View>(R.id.switchEnabled).visibility = View.GONE
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
