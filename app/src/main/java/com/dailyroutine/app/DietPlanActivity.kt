package com.dailyroutine.app

import android.view.Menu
import android.view.MenuItem
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.round

class DietPlanActivity : AppCompatActivity() {

    private lateinit var planManager: PlanManager
    private lateinit var adapter: MealAdapter
    private val calendar = Calendar.getInstance()
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
            toolbar.setBackgroundColor(if (isNightMode) Color.parseColor("#17351E") else Color.parseColor("#EAF7EA"))
            
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
                        deleteSelectedMeals()
                        true
                    } else false
                }
            }
        } else {
            toolbar.title = "Nutrition"
            toolbar.setBackgroundColor(Color.TRANSPARENT)
            
            val typedValue = TypedValue()
            theme.resolveAttribute(androidx.appcompat.R.attr.homeAsUpIndicator, typedValue, true)
            toolbar.setNavigationIcon(typedValue.resourceId)
            toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
            toolbar.setNavigationOnClickListener { finish() }
            
            toolbar.menu.clear()
        }
    }

    private fun deleteSelectedMeals() {
        val dateStr = dateFormatter.format(calendar.time)
        val selectedIds = adapter.getSelectedIds()
        val meals = planManager.getMealsForDate(dateStr).filter { selectedIds.contains(it.id) }
        
        AlertDialog.Builder(this)
            .setTitle("Delete Meals?")
            .setMessage("Remove ${meals.size} selected meals from your plan?")
            .setPositiveButton("Delete") { _, _ ->
                meals.forEach { meal ->
                    planManager.deleteMealForDate(dateStr, meal)
                }
                adapter.clearSelection()
                refreshMeals()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private data class TemplateDialogAction(
        val title: String,
        val description: String,
        val onClick: () -> Unit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diet_plan)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomPadding(findViewById(R.id.rvMeals))
        InsetHelper.applyBottomMargin(findViewById(R.id.fabAddMeal))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
        toolbar.setNavigationOnClickListener { finish() }

        planManager = PlanManager(this)
        
        setupRecyclerView()
        setupDatePickers()
        refreshMeals()

        findViewById<FloatingActionButton>(R.id.fabAddMeal).setOnClickListener {
            showEditMealDialog(null)
        }


        findViewById<MaterialButton>(R.id.btnTemplatesDiet).setOnClickListener {
            showTemplateActionsDialog()
        }

        findViewById<TextView>(R.id.tvTotalCaloriesToday).setOnClickListener {
            startActivity(Intent(this, CaloriesActivity::class.java))
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
        val scrollView = findViewById<HorizontalScrollView>(R.id.hsvDays)
        val llDays = findViewById<LinearLayout>(R.id.llDays)
        llDays.removeAllViews()
        
        val tempCal = calendar.clone() as Calendar
        tempCal.add(Calendar.DAY_OF_YEAR, -dateStripPastDays)

        val stripDateFormatter = SimpleDateFormat("EEE\ndd", Locale.US)
        val todayStr = dateFormatter.format(Date())
        var selectedButton: Button? = null

        repeat(dateStripPastDays + dateStripFutureDays + 1) {
            val dateStr = dateFormatter.format(tempCal.time)
            val isSelected = dateStr == dateFormatter.format(calendar.time)
            val isToday = dateStr == todayStr
            val hasMeals = planManager.hasMealsForDate(dateStr)
            val inTemplateRange = planManager.isDateInAppliedMealTemplateRange(dateStr)

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
                    calendar.time = clickedCal.time
                    this@DietPlanActivity.findViewById<TextView>(R.id.tvSelectedDate).text = displayFormatter.format(calendar.time)
                    refreshMeals()
                    updateDaysStrip()
                }
                alpha = 1f
            }
            styleMealStripButton(btn, isSelected, isToday, inTemplateRange, hasMeals)
            if (isSelected) selectedButton = btn
            llDays.addView(btn)
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        selectedButton?.let { button ->
            scrollView.post {
                val targetX = button.left - ((scrollView.width - button.width) / 2)
                scrollView.smoothScrollTo(targetX.coerceAtLeast(0), 0)
            }
        }
    }

    private fun styleMealStripButton(button: Button, isSelected: Boolean, isToday: Boolean, inTemplateRange: Boolean, hasMeals: Boolean) {
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val bgColor = when {
            inTemplateRange -> if (isNightMode) 0xFF1B5E20.toInt() else 0xFF1B5E20.toInt() // Darker green
            isSelected -> if (isNightMode) 0xFF12381A.toInt() else 0xFFE8F5E9.toInt()
            hasMeals -> if (isNightMode) 0xFF2E7D32.toInt() else 0xFFC8E6C9.toInt() // Lighter green
            isToday -> if (isNightMode) 0xFF1E2244.toInt() else 0xFFEEF0FF.toInt() // Subtle Indigo
            else -> if (isNightMode) 0xFF171B2F.toInt() else 0xFFEFF1F3.toInt()
        }
        val strokeColor = when {
            isToday -> if (isNightMode) 0xFF7C7FFF.toInt() else 0xFF5B5FEF.toInt() // Primary Indigo
            inTemplateRange && isSelected -> Color.WHITE
            inTemplateRange -> if (isNightMode) 0xFFA5D6A7.toInt() else 0xFF1B5E20.toInt()
            isSelected -> if (isNightMode) 0xFFA5D6A7.toInt() else 0xFF2E7D32.toInt()
            hasMeals -> if (isNightMode) 0xFFB9F6CA.toInt() else 0xFF4CAF50.toInt()
            else -> if (isNightMode) 0xFF354A3A.toInt() else 0xFFDDE1E6.toInt()
        }
        val strokeWidth = ((if (isToday || isSelected) 3 else if (inTemplateRange || hasMeals) 2 else 1) * resources.displayMetrics.density).toInt()

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * resources.displayMetrics.density
            setColor(bgColor)
            setStroke(strokeWidth, strokeColor)
        }
        button.background = drawable
        val textColor = when {
            inTemplateRange -> Color.WHITE
            isSelected -> if (isNightMode) Color.WHITE else 0xFF1B5E20.toInt()
            hasMeals -> if (isNightMode) Color.WHITE else 0xFF2E7D32.toInt()
            isToday -> if (isNightMode) Color.WHITE else 0xFF5B5FEF.toInt()
            else -> if (isNightMode) 0xFFF3F5FF.toInt() else 0xFF1A1A2E.toInt()
        }
        button.setTextColor(textColor)
        button.setTypeface(button.typeface, if (isSelected || isToday || hasMeals || inTemplateRange) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun showTemplateActionsDialog() {
        showTemplateActionDialog(
            title = "Meal Templates",
            message = "Create reusable meal plans, manage saved templates, or review ranges already applied to the calendar.",
            actions = listOf(
                TemplateDialogAction("Create template", "Save meals from a selected calendar range as a reusable plan.") { showCreateTemplateDialog() },
                TemplateDialogAction("Saved templates", "Apply, view, rename, or delete your saved meal templates.") { showManageSavedMealTemplatesDialog() },
                TemplateDialogAction("Applied meal plans", "Review or remove template markers already placed on the calendar.") { showAppliedMealPlansDialog() }
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
                    setColor(if (isNightMode) 0xFF17351E.toInt() else 0xFFEAF7EA.toInt())
                    setStroke(dp(1), if (isNightMode) 0xFF81C784.toInt() else 0xFF4CAF50.toInt())
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
                setTextColor(if (isNightMode) Color.WHITE else 0xFF1B5E20.toInt())
            })
            row.addView(TextView(this).apply {
                text = action.description
                textSize = 12f
                setPadding(0, dp(3), 0, 0)
                setTextColor(if (isNightMode) Color.argb(220, 255, 255, 255) else 0xFF2E7D32.toInt())
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
            "Remove applied marker"
        )
        AlertDialog.Builder(this)
            .setTitle(range.templateName)
            .setMessage("Applied from ${range.startDate} to ${range.endDate} • ${daysBetweenInclusive(range.startDate, range.endDate)} day(s)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAppliedMealRangeDetails(range)
                    1 -> {
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
        val todayStr = dateFormatter.format(Date())
        val meals = planManager.getMealsForDate(dateStr).sortedBy { it.hour * 60 + it.minute }
        adapter.setMeals(meals)
        
        val count = meals.size
        val mealWord = if (count == 1) "meal" else "meals"
        val timeWord = when {
            dateStr == todayStr -> "planned for today"
            dateStr < todayStr -> "were planned for this day"
            else -> "planned for this day"
        }
        findViewById<TextView>(R.id.tvMealCount).text = "$count $mealWord $timeWord"
        
        WellnessEngine.calculateIntakeForDate(this, dateStr) { total ->
            runOnUiThread {
                findViewById<TextView>(R.id.tvTotalCaloriesToday).text = "$total kcal"
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
        DialogInputHelper.hideKeyboardOnDone(etName, etDesc)

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

        fun showMealTimePicker() {
            DialogInputHelper.hideKeyboard(v)
            android.app.TimePickerDialog(this, { _, sh, sm ->
                h = sh; m = sm
                updateTimeLabel()
            }, h, m, false).show()
        }
        v.findViewById<Button>(R.id.btnPickMealTime).setOnClickListener { showMealTimePicker() }
        DialogInputHelper.makeTimeLabelClickable(tvTime, "Change meal time") { showMealTimePicker() }

        AlertDialog.Builder(this)
            .setTitle(if (meal == null) "Add Meal" else "Edit Meal")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                DialogInputHelper.hideKeyboard(v)
                val name = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Meal name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val loading = Toast.makeText(this, "Fetching calories and nutritional values...", Toast.LENGTH_SHORT)
                loading.show()
                
                CalorieSearchEngine.getMealNutritionResult(this, name, desc) { result ->
                    loading.cancel()
                    when (result) {
                        is NutritionResult.Success -> {
                            val nutrition = result.info
                            val newMeal = (meal ?: Meal()).copy(
                                name = name,
                                description = desc,
                                mealType = spinner.selectedItem.toString(),
                                hour = h,
                                minute = m,
                                isReminderEnabled = swReminder.isChecked,
                                calories = nutrition.calories,
                                nutritionInfo = nutrition
                            )
                            planManager.saveMealForDate(dateStr, newMeal)
                            planManager.syncMealReminder(this@DietPlanActivity, newMeal, dateStr)
                            refreshMeals()
                        }
                        is NutritionResult.Error -> {
                            val newMeal = (meal ?: Meal()).copy(
                                name = name,
                                description = desc,
                                mealType = spinner.selectedItem.toString(),
                                hour = h,
                                minute = m,
                                isReminderEnabled = swReminder.isChecked
                            )
                            planManager.saveMealForDate(dateStr, newMeal)
                            planManager.syncMealReminder(this@DietPlanActivity, newMeal, dateStr)
                            refreshMeals()

                            MaterialAlertDialogBuilder(this@DietPlanActivity)
                                .setTitle("Nutrition Fetch Notice")
                                .setMessage(result.message)
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMealNutritionPopup(meal: Meal) {
        val dateStr = dateFormatter.format(calendar.time)
        fun displayDialog(info: MealNutritionInfo) {
            val v = LayoutInflater.from(this).inflate(R.layout.dialog_meal_nutrition_info, null)
            v.findViewById<ImageView>(R.id.ivMealIcon).apply {
                setImageResource(RoutineIconMapper.iconForMealType(meal.mealType))
                setBackgroundResource(RoutineIconMapper.badgeForMealType(meal.mealType))
            }
            v.findViewById<TextView>(R.id.tvMealTitle).text = meal.name
            v.findViewById<TextView>(R.id.tvMealCategory).text = "${meal.mealType} • ${meal.formatTime()}"
            v.findViewById<TextView>(R.id.tvMealCalories).text = "${info.calories} kcal"

            val tvNotes = v.findViewById<TextView>(R.id.tvMealNotes)
            if (meal.description.isNotBlank()) {
                tvNotes.visibility = View.VISIBLE
                tvNotes.text = meal.description
            } else {
                tvNotes.visibility = View.GONE
            }

            val personalRda = PersonalizedRdaCalculator.calculate(this)

            // Macro Ratio Split
            val totalMacroG = (info.carbsG + info.proteinG + info.fatG).coerceAtLeast(1.0)
            val carbsPct = round((info.carbsG / totalMacroG) * 100).toInt()
            val proteinPct = round((info.proteinG / totalMacroG) * 100).toInt()
            val fatPct = (100 - carbsPct - proteinPct).coerceAtLeast(0)

            v.findViewById<TextView>(R.id.tvCarbsRatio).text = "%.1fg (%d%%)".format(info.carbsG, carbsPct)
            v.findViewById<TextView>(R.id.tvProteinRatio).text = "%.1fg (%d%%)".format(info.proteinG, proteinPct)
            v.findViewById<TextView>(R.id.tvFatRatio).text = "%.1fg (%d%%)".format(info.fatG, fatPct)

            val llTabs = v.findViewById<LinearLayout>(R.id.llCategoryTabs)
            val llList = v.findViewById<LinearLayout>(R.id.llNutrientsList)

            val categories = listOf("All", "Macros", "Vitamins", "Minerals", "Amino Acids", "Antioxidants", "Other")
            var activeCategoryIndex = 0

            fun addNutrientRow(name: String, valueStr: String, accentColorHex: Int, drawableRes: Int, isChild: Boolean = false, isWarning: Boolean = false) {
                val (pct, label) = personalRda.calculateRdaPercentage(name, valueStr)
                val displayVal = if (label != null) "$valueStr • $label" else valueStr
                val row = LayoutInflater.from(this@DietPlanActivity).inflate(R.layout.item_nutrient_bar, llList, false)

                val tvName = row.findViewById<TextView>(R.id.tvNutrientName)
                tvName.text = if (isChild) "  ├ $name" else name
                if (isWarning) {
                    tvName.setTextColor(ContextCompat.getColor(this@DietPlanActivity, R.color.nutritionWarning))
                }

                val tvVal = row.findViewById<TextView>(R.id.tvNutrientValue)
                tvVal.text = displayVal
                if (isWarning) {
                    tvVal.setTextColor(ContextCompat.getColor(this@DietPlanActivity, R.color.nutritionWarning))
                }

                row.findViewById<View>(R.id.vCategoryAccent).setBackgroundColor(accentColorHex)

                val bar = row.findViewById<ProgressBar>(R.id.pbNutrientBar)
                bar.progress = if (pct == 0) 0 else pct.coerceIn(8, 100)
                bar.progressDrawable = ContextCompat.getDrawable(this@DietPlanActivity, drawableRes)
                llList.addView(row)
            }

            fun parseNum(str: String): Double {
                return Regex("""(\d+(?:\.\d+)?)""").find(str)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            }

            fun renderNutrientList(catIndex: Int) {
                llList.removeAllViews()

                val cMacros = ContextCompat.getColor(this@DietPlanActivity, R.color.nutritionMacros)
                val cVitamins = ContextCompat.getColor(this@DietPlanActivity, R.color.nutritionVitamins)
                val cMinerals = ContextCompat.getColor(this@DietPlanActivity, R.color.nutritionMinerals)
                val cAmino = ContextCompat.getColor(this@DietPlanActivity, R.color.nutritionAminoAcids)
                val cAntiox = ContextCompat.getColor(this@DietPlanActivity, R.color.nutritionAntioxidants)
                val cOther = ContextCompat.getColor(this@DietPlanActivity, R.color.nutritionOthers)

                // MACROS (Category 0 = All or Category 1 = Macros)
                if (catIndex == 0 || catIndex == 1) {
                    addNutrientRow("Carbohydrates", "%.1f g".format(info.carbsG), cMacros, R.drawable.bg_nutrient_progress_macros)
                    info.carbBreakdown.forEach { (key, valStr) ->
                        val isAddedSugarWarning = key.lowercase().contains("added sugar") && parseNum(valStr) > 25.0
                        addNutrientRow(key, valStr, cMacros, R.drawable.bg_nutrient_progress_macros, isChild = true, isWarning = isAddedSugarWarning)
                    }

                    addNutrientRow("Protein", "%.1f g".format(info.proteinG), cMacros, R.drawable.bg_nutrient_progress_macros)

                    addNutrientRow("Fat", "%.1f g".format(info.fatG), cMacros, R.drawable.bg_nutrient_progress_macros)
                    info.fatBreakdown.forEach { (key, valStr) ->
                        val isTransFatWarning = key.lowercase().contains("trans fat") && parseNum(valStr) > 0.0
                        addNutrientRow(key, valStr, cMacros, R.drawable.bg_nutrient_progress_macros, isChild = true, isWarning = isTransFatWarning)
                    }

                    if (info.fiberG > 0) {
                        addNutrientRow("Dietary Fiber", "%.1f g".format(info.fiberG), cMacros, R.drawable.bg_nutrient_progress_macros)
                    }
                }

                // VITAMINS (Category 0 or Category 2)
                if ((catIndex == 0 || catIndex == 2) && info.vitamins.isNotEmpty()) {
                    info.vitamins.forEach { (key, valStr) ->
                        addNutrientRow(key, valStr, cVitamins, R.drawable.bg_nutrient_progress_vitamins)
                    }
                }

                // MINERALS (Category 0 or Category 3)
                if ((catIndex == 0 || catIndex == 3) && info.minerals.isNotEmpty()) {
                    info.minerals.forEach { (key, valStr) ->
                        addNutrientRow(key, valStr, cMinerals, R.drawable.bg_nutrient_progress_minerals)
                    }
                }

                // AMINO ACIDS (Category 0 or Category 4)
                if ((catIndex == 0 || catIndex == 4) && info.aminoAcids.isNotEmpty()) {
                    info.aminoAcids.forEach { (key, valStr) ->
                        addNutrientRow(key, valStr, cAmino, R.drawable.bg_nutrient_progress_amino)
                    }
                }

                // ANTIOXIDANTS (Category 0 or Category 5)
                if ((catIndex == 0 || catIndex == 5) && info.antioxidants.isNotEmpty()) {
                    info.antioxidants.forEach { (key, valStr) ->
                        addNutrientRow(key, valStr, cAntiox, R.drawable.bg_nutrient_progress_antioxidants)
                    }
                }

                // OTHER (Category 0 or Category 6)
                if ((catIndex == 0 || catIndex == 6) && info.otherNutrients.isNotEmpty()) {
                    info.otherNutrients.forEach { (key, valStr) ->
                        addNutrientRow(key, valStr, cOther, R.drawable.bg_nutrient_progress_others)
                    }
                }
            }

            fun updateCategoryTabs() {
                llTabs.removeAllViews()
                val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val tabColors = listOf(
                    if (isNightMode) 0xFF7C7FFF.toInt() else 0xFF5B5FEF.toInt(), // All: Neutral Indigo
                    0xFF93C572.toInt(), // Macros: Pistachio
                    0xFFED3293.toInt(), // Vitamins: Pink
                    0xFF0096FF.toInt(), // Minerals: Bright Blue
                    0xFFFFBF00.toInt(), // Amino Acids: Amber
                    0xFF00F0A8.toInt(), // AntiOxidants: Spring Green
                    0xFF5F9EA0.toInt()  // Other: Cadet Blue
                )

                categories.forEachIndexed { idx, catName ->
                    val isSelected = idx == activeCategoryIndex
                    val catColor = tabColors.getOrElse(idx) { 0xFF5B5FEF.toInt() }
                    val btn = Button(this@DietPlanActivity, null, android.R.attr.buttonStyleSmall).apply {
                        text = catName
                        setAllCaps(false)
                        textSize = 12f
                        setPadding(dp(12), dp(4), dp(12), dp(4))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(dp(3), 0, dp(3), 0) }

                        val drawable = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(14).toFloat()
                            if (isSelected) {
                                setColor(catColor)
                            } else {
                                setColor(if (isNightMode) 0xFF1C221D.toInt() else 0xFFF1F5F2.toInt())
                                setStroke(dp(1), catColor)
                            }
                        }
                        background = drawable
                        val useBlackText = isSelected && (idx == 1 || idx == 4 || idx == 5)
                        setTextColor(if (isSelected) (if (useBlackText) Color.BLACK else Color.WHITE) else catColor)
                        setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)

                        setOnClickListener {
                            activeCategoryIndex = idx
                            updateCategoryTabs()
                            renderNutrientList(idx)
                        }
                    }
                    llTabs.addView(btn)
                }
            }

            updateCategoryTabs()
            renderNutrientList(0)

            MaterialAlertDialogBuilder(this)
                .setView(v)
                .setPositiveButton("Close", null)
                .show()
        }

        val existingInfo = meal.nutritionInfo
        if (existingInfo != null && existingInfo.hasDetailedNutrition()) {
            displayDialog(existingInfo)
        } else {
            val progress = Toast.makeText(this, "Fetching calories and nutritional values...", Toast.LENGTH_SHORT)
            progress.show()
            CalorieSearchEngine.getMealNutritionResult(this, meal.name, meal.description) { result ->
                progress.cancel()
                when (result) {
                    is NutritionResult.Success -> {
                        val info = result.info
                        val updatedMeal = meal.copy(calories = info.calories, nutritionInfo = info)
                        planManager.saveMealForDate(dateStr, updatedMeal)
                        displayDialog(info)
                    }
                    is NutritionResult.Error -> {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Nutrition Error")
                            .setMessage(result.message)
                            .setPositiveButton("Close", null)
                            .show()
                    }
                }
            }
        }
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
            val isSelected = selectedIds.contains(m.id)
            
            holder.ivIcon.setImageResource(RoutineIconMapper.iconForMealType(m.mealType))
            holder.ivIcon.setBackgroundResource(RoutineIconMapper.badgeForMealType(m.mealType))
            holder.tvTitle.text = m.name
            holder.tvSubtitle.text = m.mealType
            holder.tvSubtitle.setBackgroundResource(R.drawable.bg_chip)
            holder.tvSubtitle.visibility = View.VISIBLE
            
            val hasDetails = m.nutritionInfo?.hasDetailedNutrition() == true
            if (m.calories == 0 || !hasDetails) {
                val targetDate = dateFormatter.format(calendar.time)
                CalorieSearchEngine.getMealNutrition(this@DietPlanActivity, m.name, m.description) { info ->
                    if (info.hasDetailedNutrition()) {
                        val updatedMeal = m.copy(calories = info.calories, nutritionInfo = info)
                        planManager.saveMealForDate(targetDate, updatedMeal)
                    }
                }
            }

            if (m.calories > 0) {
                holder.tvTime.text = "${m.formatTime()} • ${m.calories} kcal"
            } else {
                holder.tvTime.text = m.formatTime()
            }

            val tvHint = holder.itemView.findViewById<TextView>(R.id.tvHint)
            tvHint.visibility = if (selectedIds.isNotEmpty()) View.GONE else View.VISIBLE

            val cardView = holder.itemView as MaterialCardView
            val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            if (isSelected) {
                cardView.strokeColor = if (isNightMode) Color.parseColor("#81C784") else Color.parseColor("#4CAF50")
                cardView.strokeWidth = dp(2)
                cardView.setCardBackgroundColor(if (isNightMode) Color.parseColor("#17351E") else Color.parseColor("#EAF7EA"))
            } else {
                cardView.strokeColor = ContextCompat.getColor(this@DietPlanActivity, R.color.cardStroke)
                cardView.strokeWidth = dp(1)
                cardView.setCardBackgroundColor(ContextCompat.getColor(this@DietPlanActivity, R.color.cardBg))
            }

            holder.itemView.setOnLongClickListener {
                if (selectedIds.isEmpty()) {
                    toggleSelection(m.id)
                }
                true
            }

            holder.itemView.setOnClickListener { 
                if (selectedIds.isNotEmpty()) {
                    toggleSelection(m.id)
                } else {
                    showMealNutritionPopup(m) 
                }
            }
            
            val btnDelete = holder.itemView.findViewById<View>(R.id.btnDelete)
            btnDelete.visibility = if (selectedIds.isNotEmpty()) View.GONE else View.VISIBLE
            btnDelete.setOnClickListener { onDelete(m.id) }

            val btnEdit = holder.itemView.findViewById<View>(R.id.btnEdit)
            btnEdit.visibility = if (selectedIds.isNotEmpty()) View.GONE else View.VISIBLE
            btnEdit.setOnClickListener { onEdit(m) }
            
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
