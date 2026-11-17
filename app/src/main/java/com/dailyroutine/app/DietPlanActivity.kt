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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private fun showUnifiedMealNoticeDialog(
        title: String,
        statusMessage: String,
        explanation: String,
        nextSteps: String,
        positiveButtonText: String = "Got It",
        onPositive: (() -> Unit)? = null
    ) {
        val fullMessage = StringBuilder().apply {
            append(statusMessage)
            if (explanation.isNotBlank()) append("\n\n").append(explanation)
            if (nextSteps.isNotBlank()) append("\n\n").append(nextSteps)
        }.toString()

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(fullMessage)
            .setPositiveButton(positiveButtonText) { _, _ -> onPositive?.invoke() }
            .setCancelable(true)
            .show()
    }

    private fun showUnifiedMealDeleteGuidanceDialog(
        meal: Meal,
        onEditChosen: () -> Unit,
        onDeleteConfirmed: () -> Unit
    ) {
        val statusMessage = "Are you sure you want to delete '${meal.name}'?"
        val explanation = "Tip: Each meal can be updated up to 2 times to adjust its name, category, or details without deleting it. Deleting a meal grants 1 replacement AI calculation for ${meal.mealType} today."
        val nextSteps = "Would you like to edit this meal instead, or proceed with deletion?"

        val fullMessage = "$statusMessage\n\n$explanation\n\n$nextSteps"

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Meal Guidance")
            .setMessage(fullMessage)
            .setNeutralButton("Edit Meal") { _, _ -> onEditChosen() }
            .setPositiveButton("Delete") { _, _ -> onDeleteConfirmed() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkStreakRewards() {
        val todayStr = dateFormatter.format(Date())
        lifecycleScope.launch(Dispatchers.IO) {
            planManager.evaluateStreaks(todayStr)
            val pendingPopup = planManager.consumePendingStreakPopup()
            if (pendingPopup != null) {
                withContext(Dispatchers.Main) {
                    showPersistentStreakRewardDialog(pendingPopup)
                }
            }
        }
    }

    private fun showPersistentStreakRewardDialog(type: String) {
        val (title, status, rewardText) = when (type) {
            "edit" -> Triple(
                "Streak Reward: Extra Edit Unlocked!",
                "Great discipline! You logged a primary meal and made no edits on 3 consecutive days.",
                "You have unlocked +1 bonus edit per meal (3 edits total per meal) for 3 calendar days, starting today. Any unused bonus expires after the third day."
            )
            "delete" -> Triple(
                "Streak Reward: Replacement Bonus Unlocked!",
                "Excellent planning! You logged a primary meal and deleted no meals on 3 consecutive days.",
                "You have unlocked +1 extra delete-and-replace AI credit per category for 3 calendar days, starting today. Any unused bonus expires after the third day."
            )
            else -> Triple(
                "Streak Reward: Dual Bonus Unlocked!",
                "Outstanding discipline! You logged a primary meal and made no edits or deletions on 3 consecutive days.",
                "You have unlocked +1 bonus edit per meal and +1 extra delete-and-replace credit per category for 3 calendar days, starting today. Any unused bonuses expire after the third day."
            )
        }

        val explanation = "Limits exist to ensure optimal AI performance and preserve server resources for all users."
        val fullMessage = "$status\n\n$explanation\n\n$rewardText"

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(fullMessage)
            .setPositiveButton("Understood", null)
            .setCancelable(false)
            .show()
    }

    private var firstTimeGuideDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onResume() {
        super.onResume()
        checkFirstTimeNutritionGuide()
        window.decorView.postDelayed({ showNutritionTextErrorPopupIfNeeded() }, 350L)
    }

    private fun showNutritionTextErrorPopupIfNeeded() {
        if (!::planManager.isInitialized || isFinishing || isDestroyed ||
            !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) ||
            !UserSettingsStore.hasAcknowledgedNutritionPolicy(this)) return
        val errors = planManager.getMealsWithNutritionTextErrors()
        if (errors.isEmpty()) return

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        content.addView(TextView(this).apply {
            text = "These meals need a title or description edit before their calorie estimate can be calculated. This reminder will appear again until they are resolved."
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@DietPlanActivity, R.color.textSecondary))
            setPadding(0, 0, 0, dp(12))
        })
        val scroll = ScrollView(this).apply { addView(content) }
        var issueDialog: androidx.appcompat.app.AlertDialog? = null
        errors.forEach { (date, meal) ->
            val warning = NutritionWarningPresentation.forMeal(meal.nutritionErrorCode)
            val fallbackAddedAt = (dateFormatter.parse(date)?.time ?: 0L) +
                (meal.hour * 60L + meal.minute) * 60_000L
            val whenAdded = java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                .format(Date(meal.addedTimestamp.takeIf { it > 0L } ?: fallbackAddedAt))
            val row = TextView(this).apply {
                text = "${meal.name}\n${warning.dashboardDetail.orEmpty()}\nAdded $whenAdded · Meal date $date\nTap to open this meal"
                textSize = 14f
                setTextColor(when (warning.tone) {
                    NutritionWarningTone.AMBER -> Color.rgb(183, 103, 0)
                    NutritionWarningTone.RED -> Color.rgb(183, 28, 28)
                    NutritionWarningTone.NONE -> ContextCompat.getColor(this@DietPlanActivity, R.color.textSecondary)
                })
                setPadding(dp(14), dp(13), dp(14), dp(13))
                setBackgroundResource(R.drawable.bg_chip)
                setOnClickListener {
                    issueDialog?.dismiss()
                    calendar.time = dateFormatter.parse(date) ?: Date()
                    findViewById<TextView>(R.id.tvSelectedDate).text = displayFormatter.format(calendar.time)
                    refreshMeals()
                    val mealList = findViewById<RecyclerView>(R.id.rvMeals)
                    mealList.post {
                        val visibleMeals = WaterIntakeSummary.mealsWithSummaryLast(planManager.getMealsForDate(date))
                        val index = visibleMeals.indexOfFirst { it.id == meal.id }
                        if (index >= 0) mealList.scrollToPosition(index)
                    }
                }
            }
            content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            })
        }
        issueDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Meals Need Attention")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .create()
        issueDialog?.show()
    }

    private fun checkFirstTimeNutritionGuide() {
        if (!UserSettingsStore.hasAcknowledgedNutritionPolicy(this)) {
            if (firstTimeGuideDialog == null || !firstTimeGuideDialog!!.isShowing) {
                showFirstTimeNutritionGuideDialog()
            }
        } else {
            firstTimeGuideDialog?.dismiss()
            firstTimeGuideDialog = null
        }
    }

    private fun showFirstTimeNutritionGuideDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 44, 56, 44)
        }

        val tvTitle = TextView(this).apply {
            text = "Welcome to AI Nutrition Logging"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@DietPlanActivity, R.color.healthNutrition))
        }
        layout.addView(tvTitle)

        val tvSub = TextView(this).apply {
            text = "Quick Summary of Fair Use Guidelines:"
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 16, 0, 10)
            setTextColor(ContextCompat.getColor(this@DietPlanActivity, R.color.textPrimary))
        }
        layout.addView(tvSub)

        val tvSummary = TextView(this).apply {
            text = "• Meal plan: Max $MAX_MEALS_PER_CATEGORY_PER_DAY active meals per category and date. This controls saved plan entries.\n" +
                   "• AI usage: Separate request allowances control Gemini calls (2 user-initiated calls/category/day; up to 8 automatic retry calls/category/day). Memory cache is 0 cost.\n" +
                   "• Edits: Max 2 updates to Title/Details/Category per meal. Time/reminder edits are unlimited.\n" +
                   "• Automatic retries: after 30 minutes, then 45 minutes, then 1 hour; overnight retry windows are 12 AM, 2 AM, 6 AM, and 8 AM.\n" +
                   "• 3-Day Streaks: Logging primary meals 3 days in a row unlocks bonus edits & replacement credits."
            textSize = 13.5f
            setLineSpacing(4f, 1.15f)
            setTextColor(ContextCompat.getColor(this@DietPlanActivity, R.color.textSecondary))
        }
        layout.addView(tvSummary)

        val btnFullGuide = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Read Complete Guide in Profile >"
            textSize = 13.5f
            setTextColor(ContextCompat.getColor(this@DietPlanActivity, R.color.primary))
            setPadding(0, 16, 0, 16)
        }
        layout.addView(btnFullGuide)

        val cbAgree = CheckBox(this).apply {
            text = "I have read and agree to the Fair Use Policy."
            textSize = 13.5f
            setTextColor(ContextCompat.getColor(this@DietPlanActivity, R.color.textPrimary))
        }
        layout.addView(cbAgree)

        val btnAck = com.google.android.material.button.MaterialButton(this).apply {
            text = "Acknowledge"
            isEnabled = false
            textSize = 14.5f
            setBackgroundColor(ContextCompat.getColor(this@DietPlanActivity, R.color.healthNutrition))
            setTextColor(Color.WHITE)
            cornerRadius = (12 * resources.displayMetrics.density).toInt()
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (52 * resources.displayMetrics.density).toInt()).apply {
            topMargin = 20
        }
        layout.addView(btnAck, params)

        cbAgree.setOnCheckedChangeListener { _, isChecked ->
            btnAck.isEnabled = isChecked
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(layout)
            .setCancelable(true)
            .create()

        dialog.setOnCancelListener {
            if (!UserSettingsStore.hasAcknowledgedNutritionPolicy(this)) {
                finish()
            }
        }

        firstTimeGuideDialog = dialog

        btnFullGuide.setOnClickListener {
            dialog.dismiss()
            firstTimeGuideDialog = null
            startActivity(Intent(this, NutritionPolicyActivity::class.java))
        }

        btnAck.setOnClickListener {
            val dateStr = dateFormatter.format(calendar.time)
            UserSettingsStore.setNutritionPolicyAcknowledged(this, dateStr)
            Toast.makeText(this, "Fair Use Policy Acknowledged!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            firstTimeGuideDialog = null
            window.decorView.postDelayed({ showNutritionTextErrorPopupIfNeeded() }, 250L)
        }

        dialog.show()
    }

    private fun deleteSelectedMeals() {
        val dateStr = dateFormatter.format(calendar.time)
        val selectedIds = adapter.getSelectedIds()
        val meals = planManager.getMealsForDate(dateStr).filter { selectedIds.contains(it.id) }
        val firstMeal = meals.firstOrNull() ?: return

        showUnifiedMealDeleteGuidanceDialog(
            meal = firstMeal,
            onEditChosen = {
                adapter.clearSelection()
                showEditMealDialog(firstMeal)
            },
            onDeleteConfirmed = {
                meals.forEach { meal ->
                    planManager.deleteMealForDate(dateStr, meal)
                }
                rescheduleNutritionRetries()
                adapter.clearSelection()
                refreshMeals()
            }
        )
    }


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
        lifecycleScope.launch(Dispatchers.IO) { NutritionRetryScheduler.schedule(applicationContext) }
        
        setupRecyclerView()
        setupDatePickers()
        refreshMeals()
        checkStreakRewards()
        checkFirstTimeNutritionGuide()

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
        PlannerDateStrip.render(
            activity = this,
            scrollView = scrollView,
            container = llDays,
            selectedDate = calendar.time,
            dateFormatter = dateFormatter,
            pastDays = dateStripPastDays,
            futureDays = dateStripFutureDays,
            hasItems = planManager::hasMealsForDate,
            isInTemplateRange = planManager::isDateInAppliedMealTemplateRange,
            styleButton = ::styleMealStripButton,
            onDateSelected = { dateStr ->
                calendar.time = dateFormatter.parse(dateStr) ?: Date()
                findViewById<TextView>(R.id.tvSelectedDate).text = displayFormatter.format(calendar.time)
                refreshMeals()
                updateDaysStrip()
            }
        )
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
            val selectedDays = PlannerTemplateDateUtils.daysBetweenInclusive(startDate, endDate)
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
                    "Target dates: ${PlannerTemplateDateUtils.buildDateRangePreview(startDate, selectedDays)}\n\n" +
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
            rescheduleNutritionRetries()
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
                val startDate = PlannerTemplateDateUtils.addDaysToDate(conflict.endDate, 1)
                val endDate = PlannerTemplateDateUtils.addDaysToDate(startDate, selectedDays - 1)
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
        showAppliedTemplateRangesDialog(
            title = "Applied Meal Plans",
            emptyMessage = "No meal templates applied yet",
            ranges = ranges,
            onRangeSelected = ::showAppliedMealRangeActions
        )
    }

    private fun showAppliedMealRangeActions(range: AppliedTemplateRange) {
        showAppliedTemplateRangeActions(
            range = range,
            onViewDetails = { showAppliedMealRangeDetails(range) },
            onRemoveMarker = {
                val removed = planManager.removeAppliedMealTemplateRange(range.startDate, range.endDate)
                if (removed) {
                    Toast.makeText(this, "Applied meal marker removed", Toast.LENGTH_SHORT).show()
                    updateDaysStrip()
                } else {
                    Toast.makeText(this, "Unable to remove applied marker", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showAppliedMealRangeDetails(range: AppliedTemplateRange) {
        val template = planManager.listMealTemplates().firstOrNull { it.id == range.templateId }
        val lines = mutableListOf<String>()
        lines += "Template: ${range.templateName}"
        lines += "Applied range: ${range.startDate} to ${range.endDate}"
        lines += "Range length: ${PlannerTemplateDateUtils.daysBetweenInclusive(range.startDate, range.endDate)} day(s)"
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

    private fun rescheduleNutritionRetries() {
        lifecycleScope.launch(Dispatchers.IO) { NutritionRetryScheduler.schedule(applicationContext) }
    }

    private fun refreshMeals() {
        val dateStr = dateFormatter.format(calendar.time)
        val todayStr = dateFormatter.format(Date())
        val meals = WaterIntakeSummary.mealsWithSummaryLast(planManager.getMealsForDate(dateStr))
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
        val percentLimitWarning = v.findViewById<TextView>(R.id.tvMealPercentLimitWarning)
        val spinner = v.findViewById<Spinner>(R.id.spinnerMealType)
        val tvTime = v.findViewById<TextView>(R.id.tvMealTime)
        val swReminder = v.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swMealReminder)
        val cardMealGuidance = v.findViewById<View>(R.id.cardMealGuidance)
        val tvMealGuidance = v.findViewById<TextView>(R.id.tvMealGuidance)

        TextInputPolicy.applyLimit(etName, TextInputPolicy.TITLE_LIMIT)
        TextInputPolicy.applyDescriptionLimit(etDesc)
        TextInputPolicy.bindPercentLimitWarning(etDesc, percentLimitWarning)

        val types = arrayOf("Breakfast", "Lunch", "Dinner", "Snack")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        DialogInputHelper.hideKeyboardOnDone(etName, etDesc)

        val currentEditCount = meal?.editCount ?: 0
        val maxAllowedEdits = planManager.getBonusAllowedEdits(dateFormatter.format(Date()))
        val remainingEdits = (maxAllowedEdits - currentEditCount).coerceAtLeast(0)

        if (meal != null) {
            when {
                remainingEdits > 1 -> {
                    tvMealGuidance.text = "Edits Remaining: $remainingEdits of $maxAllowedEdits. Updating name, details, or category recalculates AI nutrition info."
                }
                remainingEdits == 1 -> {
                    tvMealGuidance.text = "Edits Remaining: 1 of $maxAllowedEdits (Final Edit). Changing name or details will use your last allowed edit for this meal."
                }
                else -> {
                    tvMealGuidance.text = "Edits Remaining: 0 of $maxAllowedEdits (Limit Reached). AI fields (Name, Category, Description) are locked. You can still adjust time and reminders."
                    etName.isEnabled = false
                    etDesc.isEnabled = false
                    spinner.isEnabled = false
                }
            }
        }

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

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (meal == null) "Add Meal" else "Edit Meal")
            .setView(v)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                DialogInputHelper.hideKeyboard(v)
                val name = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                val selectedCategory = spinner.selectedItem.toString()

                val nameError = TextInputPolicy.error(name, true, "Meal name", TextInputPolicy.TITLE_LIMIT)
                val descError = TextInputPolicy.error(desc, false, "Meal details", TextInputPolicy.DESCRIPTION_LIMIT, allowPercent = true)
                if (nameError != null || descError != null) {
                    etName.error = nameError
                    etDesc.error = descError
                    (if (nameError != null) etName else etDesc).requestFocus()
                    return@setOnClickListener
                }

                val isAiFieldChanged = meal == null || name != meal.name || desc != meal.description || selectedCategory != meal.mealType
                val operationDate = dateFormatter.format(Date())

                if (meal != null && currentEditCount >= maxAllowedEdits && isAiFieldChanged) {
                    showUnifiedMealNoticeDialog(
                        title = "Meal Edit Limit Reached",
                        statusMessage = "This meal has reached its limit ($maxAllowedEdits edits) for AI recalculations.",
                        explanation = "Limits exist to ensure optimal AI performance and preserve server resources for all users.",
                        nextSteps = "You can still adjust meal time or reminder settings freely, or create a new meal entry."
                    )
                    return@setOnClickListener
                }

                if (planManager.isCategoryLimitReached(dateStr, selectedCategory, excludeMealId = meal?.id, maxLimit = 2)) {
                    showUnifiedMealNoticeDialog(
                        title = "Category Limit Reached",
                        statusMessage = "Maximum $MAX_MEALS_PER_CATEGORY_PER_DAY meals allowed for $selectedCategory on this date.",
                        explanation = "This limit controls how many meals can be saved in each category for a date. Gemini request allowances are separate and control AI usage.",
                        nextSteps = "You can edit existing $selectedCategory meals or choose a different meal category."
                    )
                    return@setOnClickListener
                }

                etName.error = null
                etDesc.error = null
                dialog.dismiss()

                val nextEditCount = if (meal != null && isAiFieldChanged) currentEditCount + 1 else currentEditCount

                if (!isAiFieldChanged && meal != null) {
                    val updatedMeal = meal.copy(
                        hour = h,
                        minute = m,
                        isReminderEnabled = swReminder.isChecked
                    )
                    planManager.saveMealForDate(dateStr, updatedMeal)
                    planManager.syncMealReminder(this@DietPlanActivity, updatedMeal, dateStr)
                    refreshMeals()
                    return@setOnClickListener
                }

                val cachedNutrition = CalorieEstimator.getLearnedMealNutrition(this, name, desc)
                if (cachedNutrition != null && cachedNutrition.hasAnyNutrition()) {
                    val newMeal = (meal ?: Meal()).copy(
                        name = name,
                        description = desc,
                        mealType = selectedCategory,
                        hour = h,
                        minute = m,
                        isReminderEnabled = swReminder.isChecked,
                        calories = cachedNutrition.calories,
                        nutritionInfo = cachedNutrition,
                        isPendingOvernightRetry = false,
                        queuedTimestamp = 0L,
                        manualRetryAttempted = false,
                        nutritionErrorCode = null,
                        nutritionDayRetriesUsed = 0,
                        nutritionOvernightRetriesUsed = 0,
                        nutritionRetryStage = null,
                        nextNutritionRetryAt = 0L,
                        nutritionAutoRetriesExhausted = false,
                        nutritionOvernightWindowStartAt = 0L,
                        editCount = nextEditCount
                    )
                    planManager.saveMealForDate(dateStr, newMeal)
                    if (meal != null) planManager.recordMealEditOccurred(operationDate)
                    planManager.syncMealReminder(this@DietPlanActivity, newMeal, dateStr)
                    rescheduleNutritionRetries()
                    refreshMeals()
                    return@setOnClickListener
                }

                val aiUsageDate = java.time.LocalDate.now().toString()
                if (planManager.isAiTokenLimitReached(aiUsageDate, selectedCategory)) {
                    showUnifiedMealNoticeDialog(
                        title = "Daily AI Limit Reached",
                        statusMessage = "Maximum daily AI calculations reached for $selectedCategory today.",
                        explanation = "Daily AI calculation limits exist to preserve server resources and maintain optimal performance for all users.",
                        nextSteps = "Please try adding meals to this category tomorrow or select a different meal category."
                    )
                    return@setOnClickListener
                }

                val loading = Toast.makeText(this, "Fetching calories and nutritional values...", Toast.LENGTH_SHORT)
                loading.show()

                CalorieSearchEngine.getMealNutritionWithRetries(
                    this,
                    name,
                    desc,
                    maxImmediateRetries = 1,
                    budget = planManager.createAiRequestBudget(aiUsageDate, selectedCategory)
                ) { result ->
                    loading.cancel()
                    when (result) {
                        is NutritionResult.Success -> {
                            val nutrition = result.info
                            val newMeal = (meal ?: Meal()).copy(
                                name = name,
                                description = desc,
                                mealType = selectedCategory,
                                hour = h,
                                minute = m,
                                isReminderEnabled = swReminder.isChecked,
                                calories = nutrition.calories,
                                nutritionInfo = nutrition,
                                isPendingOvernightRetry = false,
                                queuedTimestamp = 0L,
                                manualRetryAttempted = false,
                                nutritionErrorCode = null,
                                nutritionDayRetriesUsed = 0,
                                nutritionOvernightRetriesUsed = 0,
                                nutritionRetryStage = null,
                                nextNutritionRetryAt = 0L,
                                nutritionAutoRetriesExhausted = false,
                                nutritionOvernightWindowStartAt = 0L,
                                editCount = nextEditCount
                            )
                            planManager.saveMealForDate(dateStr, newMeal)
                            if (meal != null) planManager.recordMealEditOccurred(operationDate)
                            planManager.syncMealReminder(this@DietPlanActivity, newMeal, dateStr)
                            rescheduleNutritionRetries()
                            refreshMeals()
                        }
                        is NutritionResult.Error -> {
                            if (result.code == "ai_daily_limit_reached") {
                                showUnifiedMealNoticeDialog(
                                    title = "Daily AI Limit Reached",
                                    statusMessage = result.message,
                                    explanation = "No estimate request was started, so this meal was not saved or queued.",
                                    nextSteps = "Try again tomorrow or use a previously estimated meal."
                                )
                                return@getMealNutritionWithRetries
                            }
                            val isQuotaError = result.code in setOf("ai_provider_quota", "ai_provider_rate_limited")
                            val isAuthOrConfigError = result.code == "ai_provider_auth_error" ||
                                result.code == "unauthorized" || result.code == "ai_provider_not_configured"
                            val isMealTextError = result.code == "ai_payload_too_large" || result.code == "ai_content_blocked"
                            // Keep every failed estimate recoverable, including auth/config and quota failures.
                            val isRetryable = result.retryable || result.code in setOf("ai_provider_quota", "ai_provider_rate_limited")
                            val shouldQueueOvernight = !isMealTextError && isRetryable

                            val nowMs = System.currentTimeMillis()
                            val firstRetryState = if (shouldQueueOvernight) {
                                NutritionRetryPolicy.queueFirstRetry(meal ?: Meal(), result.code, nowMs)
                            } else null
                            val newMeal = (meal ?: Meal()).copy(
                                name = name,
                                description = desc,
                                mealType = selectedCategory,
                                hour = h,
                                minute = m,
                                isReminderEnabled = swReminder.isChecked,
                                calories = null,
                                nutritionInfo = null,
                                isPendingOvernightRetry = firstRetryState != null,
                                queuedTimestamp = firstRetryState?.queuedTimestamp ?: 0L,
                                manualRetryAttempted = false,
                                nutritionErrorCode = result.code,
                                nutritionDayRetriesUsed = firstRetryState?.nutritionDayRetriesUsed ?: 0,
                                nutritionOvernightRetriesUsed = firstRetryState?.nutritionOvernightRetriesUsed ?: 0,
                                nutritionRetryStage = firstRetryState?.nutritionRetryStage,
                                nextNutritionRetryAt = firstRetryState?.nextNutritionRetryAt ?: 0L,
                                nutritionAutoRetriesExhausted = !isMealTextError && !shouldQueueOvernight,
                                nutritionOvernightWindowStartAt = 0L,
                                editCount = nextEditCount
                            )
                            planManager.saveMealForDate(dateStr, newMeal)
                            if (meal != null) planManager.recordMealEditOccurred(operationDate)
                            planManager.syncMealReminder(this@DietPlanActivity, newMeal, dateStr)
                            rescheduleNutritionRetries()
                            refreshMeals()

                            if (isMealTextError) {
                                showUnifiedMealNoticeDialog(
                                    title = if (result.code == "ai_payload_too_large") "Meal Details Too Long" else "Meal Details Need Editing",
                                    statusMessage = result.message,
                                    explanation = "The meal is saved, but this error needs a change to its title or description before another estimate can work.",
                                    nextSteps = "Edit the meal title or description to continue.",
                                    positiveButtonText = "Edit Meal",
                                    onPositive = { showEditMealDialog(newMeal) }
                                )
                            } else if (isAuthOrConfigError) {
                                showUnifiedMealNoticeDialog(
                                    title = "Authentication Issue",
                                    statusMessage = result.message,
                                    explanation = "Your meal is saved without a calorie estimate. Automatic retries are paused for authentication or configuration errors.",
                                    nextSteps = "Check your connection or service settings, then edit and save the meal to start a new estimate."
                                )
                            } else if (isQuotaError) {
                                showUnifiedMealNoticeDialog(
                                    title = "AI Quota Reached — Estimate Queued",
                                    statusMessage = result.message,
                                    explanation = "Your meal is saved and remains queued for a later estimate.",
                                    nextSteps = "Automatic retries are scheduled after 30 minutes, 45 minutes later, and 1 hour later, followed by the overnight and morning retry windows."
                                )
                            } else {
                                showUnifiedMealNoticeDialog(
                                    title = "Calorie Estimate Queued",
                                    statusMessage = result.message,
                                    explanation = "Your meal is saved and remains queued for automatic retry.",
                                    nextSteps = "You can view or edit this meal anytime."
                                )
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showMealNutritionPopup(meal: Meal) {
        if (meal.nutritionErrorCode == "ai_payload_too_large" || meal.nutritionErrorCode == "ai_content_blocked") {
            val isPayloadTooLarge = meal.nutritionErrorCode == "ai_payload_too_large"
            showUnifiedMealNoticeDialog(
                title = if (isPayloadTooLarge) "Meal Details Too Long" else "Meal Details Need Editing",
                statusMessage = if (isPayloadTooLarge) {
                    "The meal details exceed the nutrition service's input limit."
                } else {
                    "The current meal title or description could not be used for an estimate."
                },
                explanation = "No automatic estimate will be attempted until the meal text is changed.",
                nextSteps = if (isPayloadTooLarge) "Shorten the description, then save the meal again." else "Edit the title or description to make it clearer or more neutral.",
                positiveButtonText = "Edit Meal",
                onPositive = { showEditMealDialog(meal) }
            )
            return
        }
        if (!meal.isWaterIntakeSummary && (meal.isPendingOvernightRetry || (meal.nutritionInfo?.hasAnyNutrition() != true && meal.displayCalories == null))) {
            MaterialAlertDialogBuilder(this)
                .setTitle(when {
                    meal.nutritionAutoRetriesExhausted -> "Estimate Not Available"
                    meal.isPendingOvernightRetry -> "Estimate Retry Scheduled"
                    else -> "No Estimate Yet"
                })
                .setMessage(
                    when {
                    meal.nutritionAutoRetriesExhausted -> {
                        "Automatic retries are finished or paused for this error. Edit and save the meal to start a new estimate."
                    }
                    meal.isPendingOvernightRetry -> {
                        "The first retry is after 30 minutes, the next is 45 minutes after that, and the third is 1 hour later. If still needed, the final retry windows are 12 AM, 2 AM, 6 AM, and 8 AM. Please wait."
                    }
                    else -> "Edit and save this meal to start a new estimate."
                })
                .setPositiveButton("Edit Meal") { _, _ -> showEditMealDialog(meal) }
                .setNegativeButton("Close", null)
                .show()
            return
        }
        val dateStr = dateFormatter.format(calendar.time)
        fun displayDialog(info: MealNutritionInfo) {
            val v = LayoutInflater.from(this).inflate(R.layout.dialog_meal_nutrition_info, null)
            v.findViewById<MealIconView>(R.id.ivMealIcon).apply {
                val icons = FoodIconResolver.getIconsForMeal(meal.name, meal.description, meal.mealType)
                setIconResList(icons, RoutineIconMapper.badgeForMealType(meal.mealType))
            }
            v.findViewById<TextView>(R.id.tvMealTitle).text = meal.name
            v.findViewById<TextView>(R.id.tvMealCategory).text = "${meal.mealType} • ${meal.formatTime()}"
            v.findViewById<TextView>(R.id.tvMealCalories).text = info.calories?.let { "$it kcal" } ?: "Calories not provided"

            val tvNotes = v.findViewById<TextView>(R.id.tvMealNotes)
            if (meal.description.isNotBlank()) {
                tvNotes.visibility = View.VISIBLE
                tvNotes.text = meal.description
            } else {
                tvNotes.visibility = View.GONE
            }

            val personalRda = PersonalizedRdaCalculator.calculate(this)

            // Macro Ratio Split
            val macroValues = listOf(info.carbsG, info.proteinG, info.fatG)
            val allMacrosKnown = macroValues.all { it != null }
            val totalMacroG = macroValues.filterNotNull().sum()
            fun formatMacroRatio(value: Double?): String {
                if (value == null) return "Not provided"
                val amount = "%.1fg".format(value)
                return if (allMacrosKnown && totalMacroG > 0.0) {
                    "$amount (${round((value / totalMacroG) * 100).toInt()}%)"
                } else {
                    amount
                }
            }

            v.findViewById<TextView>(R.id.tvCarbsRatio).text = formatMacroRatio(info.carbsG)
            v.findViewById<TextView>(R.id.tvProteinRatio).text = formatMacroRatio(info.proteinG)
            v.findViewById<TextView>(R.id.tvFatRatio).text = formatMacroRatio(info.fatG)

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
                    addNutrientRow("Carbohydrates", info.carbsG?.let { "%.1f g".format(it) } ?: "Not provided", cMacros, R.drawable.bg_nutrient_progress_macros)
                    info.carbBreakdown.forEach { (key, valStr) ->
                        val isAddedSugarWarning = key.lowercase().contains("added sugar") && parseNum(valStr) > 25.0
                        addNutrientRow(key, valStr, cMacros, R.drawable.bg_nutrient_progress_macros, isChild = true, isWarning = isAddedSugarWarning)
                    }

                    addNutrientRow("Protein", info.proteinG?.let { "%.1f g".format(it) } ?: "Not provided", cMacros, R.drawable.bg_nutrient_progress_macros)

                    addNutrientRow("Fat", info.fatG?.let { "%.1f g".format(it) } ?: "Not provided", cMacros, R.drawable.bg_nutrient_progress_macros)
                    info.fatBreakdown.forEach { (key, valStr) ->
                        val isTransFatWarning = key.lowercase().contains("trans fat") && parseNum(valStr) > 0.0
                        addNutrientRow(key, valStr, cMacros, R.drawable.bg_nutrient_progress_macros, isChild = true, isWarning = isTransFatWarning)
                    }

                    if (info.fiberG != null) {
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

                if (llList.childCount == 0) {
                    val padding = (16 * resources.displayMetrics.density).toInt()
                    llList.addView(TextView(this@DietPlanActivity).apply {
                        text = "No nutrient details provided"
                        textSize = 14f
                        setPadding(padding, padding, padding, padding)
                    })
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

        if (meal.isPendingOvernightRetry) {
            return
        }

        val existingInfo = meal.nutritionInfo
        if (meal.isWaterIntakeSummary) {
            displayDialog(MealNutritionInfo(otherNutrients = mapOf("Water intake" to meal.description)))
        } else if (existingInfo != null && existingInfo.hasAnyNutrition()) {
            displayDialog(existingInfo)
        } else if (meal.calories != null) {
            displayDialog(MealNutritionInfo(calories = meal.calories))
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("No Estimate Yet")
                .setMessage("Edit and save this meal to start a new estimate.")
                .setPositiveButton("Edit Meal") { _, _ -> showEditMealDialog(meal) }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun deleteMeal(mealId: Int) {
        val dateStr = dateFormatter.format(calendar.time)
        val meal = planManager.getMealsForDate(dateStr).find { it.id == mealId } ?: return

        showUnifiedMealDeleteGuidanceDialog(
            meal = meal,
            onEditChosen = { showEditMealDialog(meal) },
            onDeleteConfirmed = {
                planManager.deleteMealForDate(dateStr, meal)
                rescheduleNutritionRetries()
                refreshMeals()
            }
        )
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
            
            val icons = FoodIconResolver.getIconsForMeal(m.name, m.description, m.mealType)
            holder.ivIcon.setIconResList(icons, RoutineIconMapper.badgeForMealType(m.mealType))
            holder.tvTitle.text = if (m.isWaterIntakeSummary) {
                "${m.name} · ${WaterIntakeSummary.amountLabel(m)}"
            } else {
                m.name
            }
            holder.tvTitle.textSize = if (m.isWaterIntakeSummary) 14f else 16f
            holder.tvSubtitle.text = m.mealType
            holder.tvSubtitle.setBackgroundResource(R.drawable.bg_chip)
            holder.tvSubtitle.visibility = View.VISIBLE
            
            val mealCalories = m.nutritionInfo?.calories ?: m.calories?.takeIf { it > 0 }
            val cardWarning = when (m.nutritionErrorCode) {
                "ai_payload_too_large" -> "Warning: shorten meal details"
                "ai_content_blocked" -> "Warning: edit title or description"
                else -> if (m.nutritionAutoRetriesExhausted) "Estimate unavailable • edit meal" else null
            }
            if (m.isWaterIntakeSummary) {
                holder.tvTime.visibility = View.GONE
            } else if (mealCalories != null) {
                holder.tvTime.visibility = View.VISIBLE
                holder.tvTime.text = "${m.formatTime()} • $mealCalories kcal"
                holder.tvTime.setTextColor(ContextCompat.getColor(this@DietPlanActivity, R.color.textSecondary))
            } else {
                holder.tvTime.visibility = View.VISIBLE
                val estimateStatus = cardWarning ?: when {
                    m.isPendingOvernightRetry -> "Automatic retry scheduled"
                    m.nutritionInfo?.hasAnyNutrition() == true -> "Partial estimate"
                    else -> "No estimate"
                }
                holder.tvTime.text = "${m.formatTime()} • $estimateStatus"
                holder.tvTime.setTextColor(
                    when (m.nutritionErrorCode) {
                        "ai_payload_too_large" -> Color.rgb(183, 103, 0)
                        "ai_content_blocked" -> Color.rgb(183, 28, 28)
                        else -> if (m.nutritionAutoRetriesExhausted) Color.rgb(183, 103, 0) else ContextCompat.getColor(this@DietPlanActivity, R.color.textSecondary)
                    }
                )
            }

            val hasFullNutrition = m.nutritionInfo?.hasAnyNutrition() == true || (m.calories != null && m.calories > 0)
            val tvHint = holder.itemView.findViewById<TextView>(R.id.tvHint)
            tvHint.visibility = if (m.isWaterIntakeSummary || selectedIds.isNotEmpty() || m.isPendingOvernightRetry || cardWarning != null || !hasFullNutrition) View.GONE else View.VISIBLE

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
                if (!m.isWaterIntakeSummary && selectedIds.isEmpty()) {
                    toggleSelection(m.id)
                }
                true
            }

            holder.itemView.setOnClickListener { 
                if (m.isWaterIntakeSummary) {
                    return@setOnClickListener
                } else if (selectedIds.isNotEmpty()) {
                    toggleSelection(m.id)
                } else if (m.isPendingOvernightRetry) {
                    showMealNutritionPopup(m)
                } else {
                    showMealNutritionPopup(m) 
                }
            }
            
            val btnDelete = holder.itemView.findViewById<View>(R.id.btnDelete)
            btnDelete.visibility = if (WaterIntakeSummary.allowsDelete(m) && selectedIds.isEmpty()) View.VISIBLE else View.GONE
            btnDelete.setOnClickListener { onDelete(m.id) }

            val btnEdit = holder.itemView.findViewById<View>(R.id.btnEdit)
            btnEdit.visibility = if (selectedIds.isNotEmpty()) View.GONE else View.VISIBLE
            btnEdit.setOnClickListener {
                if (m.isWaterIntakeSummary) showWaterSummaryAdjustMenu(dateFormatter.format(calendar.time))
                else onEdit(m)
            }
            
            // Hide switch as it's not used here
            holder.itemView.findViewById<View>(R.id.switchEnabled).visibility = View.GONE
        }

        override fun getItemCount() = items.size

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivIcon: MealIconView = v.findViewById(R.id.tvEmoji)
            val tvTitle: TextView = v.findViewById(R.id.tvTitle)
            val tvSubtitle: TextView = v.findViewById(R.id.tvSubtitle)
            val tvTime: TextView = v.findViewById(R.id.tvTime)
        }
    }

    private fun showWaterSummaryAdjustMenu(date: String) {
        val labels = WaterIntakeSummary.adjustmentAmountsMl.map { amount ->
            val action = if (amount > 0) "Add" else "Remove"
            "$action ${kotlin.math.abs(amount)} ml"
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setItems(labels) { _, which ->
                val updated = WaterIntakeSummary.adjustDate(this, date, WaterIntakeSummary.adjustmentAmountsMl[which])
                refreshMeals()
                Toast.makeText(this, "Total water intake: ${WaterIntakeSummary.formatLiters(updated)} L", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
