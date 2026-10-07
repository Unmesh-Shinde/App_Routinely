package com.dailyroutine.app

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.round

class CaloriesActivity : AppCompatActivity() {

    private lateinit var healthDataManager: HealthDataManager
    private lateinit var planManager: PlanManager
    private lateinit var adapter: CalorieMealAdapter
    private lateinit var weeklyAdapter: WeeklyGraphAdapter
    private val monthlySnapHelper = androidx.recyclerview.widget.PagerSnapHelper()
    private var dailyGoal: Int = 2000
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calories)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomPadding(findViewById(R.id.contentRoot))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
        toolbar.setNavigationOnClickListener { finish() }

        healthDataManager = HealthDataManager(this)
        planManager = PlanManager(this)
        
        dailyGoal = healthDataManager.getDailyCalorieGoal()
        if (dailyGoal == 0) {
            showGoalDialog()
        } else {
            updateGoalDisplay()
        }

        findViewById<Button>(R.id.btnEditGoal).setOnClickListener { showGoalDialog() }

        setupTabs()
        setupTodayList()
        setupWeeklyGraph()
        setupMonthlyGraph()
        
        refreshTodayView()
    }

    private fun setupMonthlyGraph() {
        val rv = findViewById<RecyclerView>(R.id.rvMonthlyCalories)
        if (rv.onFlingListener == null) {
            monthlySnapHelper.attachToRecyclerView(rv)
        }
    }

    private fun setupWeeklyGraph() {
        val rv = findViewById<RecyclerView>(R.id.rvWeeklyGraph)
        rv.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        weeklyAdapter = WeeklyGraphAdapter()
        rv.adapter = weeklyAdapter
    }

    override fun onResume() {
        super.onResume()
        updateIdealCaloriesMessage()
    }

    private fun showGoalDialog() {
        val input = EditText(this).apply {
            hint = "e.g. 2000"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            if (dailyGoal > 0) setText(dailyGoal.toString())
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Set Daily Calorie Goal")
            .setMessage("How many calories do you aim to consume daily?")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val goal = input.text.toString().toIntOrNull() ?: 2000
                dailyGoal = goal
                healthDataManager.setDailyCalorieGoal(goal)
                updateGoalDisplay()
                refreshAllViews()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateGoalDisplay() {
        findViewById<TextView>(R.id.tvCalorieGoal).text = "$dailyGoal kcal"
    }

    private fun setupTabs() {
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val vToday = findViewById<View>(R.id.viewToday)
        val vWeekly = findViewById<View>(R.id.viewWeekly)
        val vMonthly = findViewById<View>(R.id.viewMonthly)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                vToday.visibility = if (tab?.position == 0) View.VISIBLE else View.GONE
                vWeekly.visibility = if (tab?.position == 1) View.VISIBLE else View.GONE
                vMonthly.visibility = if (tab?.position == 2) View.VISIBLE else View.GONE
                
                when (tab?.position) {
                    0 -> refreshTodayView()
                    1 -> refreshWeeklyView()
                    2 -> refreshMonthlyView()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupTodayList() {
        val rv = findViewById<RecyclerView>(R.id.rvTodayCalories)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = CalorieMealAdapter()
        rv.adapter = adapter
    }

    private fun refreshAllViews() {
        val pos = findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition
        if (pos == 0) refreshTodayView()
        else if (pos == 1) refreshWeeklyView()
        else refreshMonthlyView()
    }

    private suspend fun getBurnedCaloriesForDateRoom(date: String, metrics: DailyHealthMetricEntity?): Int {
        val today = dateFormatter.format(Date())
        val steps = if (date == today) {
            healthDataManager.getSteps().replace(",", "").toIntOrNull() ?: 0
        } else {
            metrics?.steps?.toInt() ?: healthDataManager.getHistoricalSteps(date).toInt()
        }
        val weight = metrics?.weightKg ?: healthDataManager.getWeight(date).let { if (it > 0) it else 70.0 }
        return WellnessEngine.calculateActiveBurnForDateRoom(this, date, steps, weight, metrics).toInt().coerceAtLeast(0)
    }

    private fun formatCaloriesText(value: Int): String {
        return if (value >= 1000) "%.1fk".format(value / 1000.0) else value.toString()
    }

    private fun refreshTodayView() {
        val todayStr = dateFormatter.format(Date())
        val meals = planManager.getMealsForDate(todayStr)
        updateIdealCaloriesMessage()
        
        val tvAnalyzing = findViewById<TextView>(R.id.tvAnalyzing)
        if (meals.isNotEmpty()) {
            tvAnalyzing.visibility = View.VISIBLE
            tvAnalyzing.postDelayed({ tvAnalyzing.visibility = View.GONE }, 1500)
        } else {
            tvAnalyzing.visibility = View.GONE
        }

        findViewById<View>(R.id.llTodayEmpty).visibility = if (meals.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.rvTodayCalories).visibility = if (meals.isEmpty()) View.GONE else View.VISIBLE
        
        adapter.submitList(meals)
        
        // Use async Master Wellness Engine for Energy Balance
        WellnessEngine.calculateIntakeForDate(this, todayStr) { intakeTotal ->
            runOnUiThread {
                val stepsStr = healthDataManager.getSteps().replace(",", "")
                val steps = stepsStr.toIntOrNull() ?: 0
                val weight = healthDataManager.getWeight(todayStr).let { if (it > 0) it else 70.0 }
                
                val bmr = WellnessEngine.calculateBMR(this@CaloriesActivity).toInt()
                val activeBurn = WellnessEngine.calculateActiveBurn(this@CaloriesActivity, steps, weight).toInt()
                val tef = WellnessEngine.calculateTEF(intakeTotal).toInt()
                val burnedTotal = bmr + activeBurn + tef
                val netBalance = intakeTotal - burnedTotal

                val goal = if (dailyGoal > 0) dailyGoal else 2000
                val margin = 50
                val minGreen = goal - margin
                val maxGreen = goal + margin

                val (netColorRes, netText) = when {
                    intakeTotal < minGreen -> {
                        R.color.calorieStatusOrange to netBalance.toString()
                    }
                    intakeTotal in minGreen..maxGreen -> {
                        R.color.calorieStatusGreen to netBalance.toString()
                    }
                    else -> { // intakeTotal > maxGreen
                        val text = if (netBalance > 0) "+$netBalance" else netBalance.toString()
                        R.color.calorieStatusRed to text
                    }
                }

                findViewById<TextView>(R.id.tvIntakeSum).text = intakeTotal.toString()
                findViewById<TextView>(R.id.tvBurnedSum).text = burnedTotal.toString()
                val tvNet = findViewById<TextView>(R.id.tvNetBalance)
                tvNet.text = netText
                tvNet.setTextColor(ContextCompat.getColor(this@CaloriesActivity, netColorRes))
                
                findViewById<View>(R.id.cardEnergyBalance).setOnClickListener {
                    showCalorieInfoDialog(intakeTotal, bmr, activeBurn, tef, netBalance)
                }

                lifecycleScope.launch {
                    val adaptiveInsight = WellnessEngine.getAdaptiveCalorieInsightRoom(this@CaloriesActivity)
                    val cardInsight = findViewById<View>(R.id.cardAdaptiveInsight)
                    if (adaptiveInsight != null) {
                        cardInsight.visibility = View.VISIBLE
                        findViewById<TextView>(R.id.tvAdaptiveInsightText).text = adaptiveInsight.message
                    } else {
                        cardInsight.visibility = View.GONE
                    }
                }

                findViewById<TextView>(R.id.tvDailyWarning).visibility = if (netBalance > dailyGoal) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showCalorieInfoDialog(intake: Int, bmr: Int, active: Int, tef: Int, net: Int) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_calorie_info, null)

        val goal = if (dailyGoal > 0) dailyGoal else 2000
        val margin = 50
        val minGreen = goal - margin
        val maxGreen = goal + margin

        val (netColorRes, netText) = when {
            intake < minGreen -> R.color.calorieStatusOrange to "$net kcal"
            intake in minGreen..maxGreen -> R.color.calorieStatusGreen to "$net kcal"
            else -> R.color.calorieStatusRed to (if (net > 0) "+$net kcal" else "$net kcal")
        }

        view.findViewById<TextView>(R.id.tvDialogIntake).apply {
            text = "$intake kcal"
            setTextColor(ContextCompat.getColor(this@CaloriesActivity, R.color.calorieStatusGreen))
        }
        view.findViewById<TextView>(R.id.tvDialogBmr).text = "$bmr kcal"
        view.findViewById<TextView>(R.id.tvDialogActive).text = "$active kcal"
        view.findViewById<TextView>(R.id.tvDialogTef).text = "$tef kcal"
        view.findViewById<TextView>(R.id.tvDialogTotalBurn).apply {
            text = "${bmr + active + tef} kcal"
            setTextColor(ContextCompat.getColor(this@CaloriesActivity, R.color.calorieStatusRed))
        }
        view.findViewById<TextView>(R.id.tvDialogNet).apply {
            text = netText
            setTextColor(ContextCompat.getColor(this@CaloriesActivity, netColorRes))
        }

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun updateIdealCaloriesMessage() {
        val tv = findViewById<TextView>(R.id.tvIdealCaloriesMessage)
        val metrics = ProfileHealthMetricsCalculator.calculate(this)
        if (metrics == null) {
            tv.text = "Complete age, height, weight, and gender in Profile to calculate your ideal calorie intake."
            return
        }
        tv.text = "Ideal Calorie Intake: ${metrics.idealCalories} kcal/day according to your profile."
    }

    private fun refreshWeeklyView() {
        lifecycleScope.launch {
            val now = Calendar.getInstance()
            val startRange = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(HealthDataManager.SYNC_HISTORY_DAYS - 1)) }
            
            // 🚀 BATCH FETCH
            val metricsMap = healthDataManager.getMetricsMap(dateFormatter.format(startRange.time), dateFormatter.format(now.time))
            val allMealsMap = planManager.getAllDietMealsByDate()

            val weekGroups = HistoryDateOrder.monthBoundedWeeklyGroups(HealthDataManager.SYNC_HISTORY_DAYS)

            val currentWeekStart = (now.clone() as Calendar).apply {
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val dayLabelFormatter = SimpleDateFormat("EEE", Locale.US)
            val dateBarFormatter = SimpleDateFormat("dd MMM", Locale.US)
            val intakeColor = ContextCompat.getColor(this@CaloriesActivity, R.color.graphCaloriesIntake)
            val burnedColor = ContextCompat.getColor(this@CaloriesActivity, R.color.graphCaloriesBurned)
            val maintenanceColor = ContextCompat.getColor(this@CaloriesActivity, R.color.graphCaloriesMaintenance)

            val graphItems = mutableListOf<WeeklyGraphItem>()
            var currentWeekTotalAccumulator = 0.0

            weekGroups.forEachIndexed { weekIndex, weekDates ->
                weekDates.forEach { calendar ->
                    val dateKey = dateFormatter.format(calendar.time)
                    val metrics = metricsMap[dateKey]
                    val intake = allMealsMap[dateKey]?.sumOf { it.calories.coerceAtLeast(0) } ?: 0
                    val activeBurn = getBurnedCaloriesForDateRoom(dateKey, metrics)
                    val bmr = WellnessEngine.calculateBMRForDateRoom(this@CaloriesActivity, dateKey, metrics).toInt()
                    val tef = WellnessEngine.calculateTEF(intake).toInt()
                    val totalBurned = bmr + activeBurn + tef

                    if (!calendar.before(currentWeekStart) && !calendar.after(now)) {
                        currentWeekTotalAccumulator += intake
                    }

                    val hasSignal = intake > 0 || totalBurned > bmr
                    
                    val heightIntake = (intake.toDouble() * 200.0 / 4500.0).toInt().coerceIn(if (intake > 0) 2 else 0, 200).let { dpToPx(it) }
                    val bmrH = (bmr.toDouble() * 200.0 / 4500.0).coerceAtLeast(if (bmr > 0) 1.0 else 0.0).let { dpToPx(it.toInt()) }
                    val activeH = ((activeBurn + tef).toDouble() * 200.0 / 4500.0).coerceAtLeast(0.0).let { dpToPx(it.toInt()) }

                    graphItems.add(WeeklyGraphItem(
                        dayLabel = dayLabelFormatter.format(calendar.time),
                        dateLabel = dateBarFormatter.format(calendar.time),
                        primaryValue = if (intake > 0) formatCaloriesText(intake) else "0",
                        primaryHeightPx = heightIntake,
                        primaryColor = intakeColor,
                        primaryBackgroundRes = R.drawable.bg_calorie_bar,
                        secondaryValue = if (totalBurned > 0) formatCaloriesText(totalBurned) else "0",
                        secondaryHeightPx = bmrH + activeH,
                        secondaryColor = burnedColor,
                        secondaryBackgroundRes = R.drawable.bg_calorie_burned_bar,
                        isSecondaryStacked = true,
                        secondaryBottomHeightPx = bmrH,
                        secondaryBottomColor = maintenanceColor,
                        secondaryTopHeightPx = activeH,
                        secondaryTopColor = burnedColor,
                        hasSignal = hasSignal
                    ))
                }

                if (weekIndex != weekGroups.lastIndex) {
                    graphItems.add(WeeklyGraphItem(
                        dayLabel = "", dateLabel = "", primaryValue = "", primaryHeightPx = 0, primaryColor = 0, isDivider = true
                    ))
                }
            }

            withContext(Dispatchers.Main) {
                weeklyAdapter.submitList(graphItems)
                findViewById<View>(R.id.tvWeeklyWarning).visibility = if (currentWeekTotalAccumulator > dailyGoal * 7) View.VISIBLE else View.GONE
            }
        }
    }

    private fun refreshMonthlyView() {
        lifecycleScope.launch {
            val today = Calendar.getInstance()
            val startRange = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(HealthDataManager.SYNC_HISTORY_DAYS - 1)) }
            
            // 🚀 BATCH FETCH
            val metricsMap = healthDataManager.getMetricsMap(dateFormatter.format(startRange.time), dateFormatter.format(today.time))
            val allMealsMap = planManager.getAllDietMealsByDate()

            // 🚀 BACKGROUND PRE-CALCULATION
            val monthDataList = mutableListOf<MonthData>()
            val oldestSyncedDay = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -(HealthDataManager.SYNC_HISTORY_DAYS - 1))
            }
            val calendar = oldestSyncedDay.clone() as Calendar
            calendar.firstDayOfWeek = Calendar.MONDAY
            calendar.set(Calendar.DAY_OF_MONTH, 1)

            val monthFormatter = SimpleDateFormat("MMMM", Locale.US)
            val yearFormatter = SimpleDateFormat("yyyy", Locale.US)
            val rangeFormatter = SimpleDateFormat("dd MMM", Locale.US)
            val intakeColor = ContextCompat.getColor(this@CaloriesActivity, R.color.graphCaloriesIntake)
            val burnedColor = ContextCompat.getColor(this@CaloriesActivity, R.color.graphCaloriesBurned)

            while (!calendar.after(today)) {
                val monthName = monthFormatter.format(calendar.time)
                val yearName = yearFormatter.format(calendar.time)
                val currentMonth = calendar.get(Calendar.MONTH)
                val isCurrentMonth = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) && currentMonth == today.get(Calendar.MONTH)

                val barItems = mutableListOf<BarItem>()
                var weekIndex = 1

                if (isCurrentMonth) {
                    HistoryDateOrder.monthWeeksForMonthlyView(calendar, today).forEach { week ->
                        var weekIntake = 0
                        var weekBmr = 0
                        var weekActive = 0
                        var weekTef = 0

                        if (week.isComplete) {
                            week.dates.forEach { day ->
                                val dateKey = dateFormatter.format(day.time)
                                if (!day.before(oldestSyncedDay) && !day.after(today)) {
                                    val metrics = metricsMap[dateKey]
                                    val dailyIntake = allMealsMap[dateKey]?.sumOf { it.calories.coerceAtLeast(0) } ?: 0
                                    weekIntake += dailyIntake
                                    weekBmr += WellnessEngine.calculateBMRForDateRoom(this@CaloriesActivity, dateKey, metrics).toInt()
                                    weekActive += getBurnedCaloriesForDateRoom(dateKey, metrics)
                                    weekTef += WellnessEngine.calculateTEF(dailyIntake).toInt()
                                }
                            }
                        }

                        val weekTotalBurned = weekBmr + weekActive + weekTef
                        val scaleM = 4500 * 7
                        val heightIntake = (weekIntake * 250 / scaleM).coerceIn(if (weekIntake > 0) 2 else 0, 250)
                        val hBmr = (weekBmr * 250 / scaleM).coerceAtLeast(0)
                        val hActiveAndTef = ((weekActive + weekTef) * 250 / scaleM).coerceAtLeast(0)

                        barItems.add(BarItem(BarData(
                            label = "Week $weekIndex",
                            date = rangeFormatter.format(week.start.time),
                            valueDisplay = if (weekIntake > 0) formatCaloriesText(weekIntake) else "0",
                            heightPx = dpToPx(heightIntake),
                            color = intakeColor,
                            backgroundRes = R.drawable.bg_calorie_bar,
                            isDoubleBar = true,
                            secondaryValueDisplay = if (weekTotalBurned > 0) formatCaloriesText(weekTotalBurned) else "0",
                            secondaryHeightPx = dpToPx(hBmr + hActiveAndTef),
                            secondaryColor = burnedColor,
                            secondaryBackgroundRes = R.drawable.bg_calorie_burned_bar,
                            isSecondaryStacked = true,
                            secondaryBottomHeightPx = dpToPx(hBmr),
                            secondaryBottomColor = ContextCompat.getColor(this@CaloriesActivity, R.color.graphCaloriesMaintenance),
                            secondaryTopHeightPx = dpToPx(hActiveAndTef),
                            secondaryTopBackgroundRes = R.drawable.bg_calorie_burned_bar,
                            isEmpty = weekIntake == 0 && weekActive == 0
                        )))
                        weekIndex++
                    }

                    calendar.add(Calendar.MONTH, 1)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    if (barItems.isNotEmpty()) {
                        monthDataList.add(MonthData(monthName, yearName, barItems))
                    }
                    continue
                }

                while (calendar.get(Calendar.MONTH) == currentMonth) {
                    val weekStart = calendar.time
                    val weekDates = mutableListOf<String>()

                    var isWeekOver = false
                    while (!isWeekOver) {
                        if (!calendar.before(oldestSyncedDay) && !calendar.after(today)) {
                            weekDates.add(dateFormatter.format(calendar.time))
                        }
                        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                        val isSunday = (currentDayOfWeek == Calendar.SUNDAY)
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                        val isNewMonth = (calendar.get(Calendar.MONTH) != currentMonth)
                        val isAfterToday = calendar.after(today)
                        if (isSunday || isNewMonth || isAfterToday) isWeekOver = true
                    }

                    var weekIntake = 0
                    var weekBmr = 0
                    var weekActive = 0
                    var weekTef = 0
                    weekDates.forEach {
                        val metrics = metricsMap[it]
                        val dailyIntake = allMealsMap[it]?.sumOf { it.calories.coerceAtLeast(0) } ?: 0
                        weekIntake += dailyIntake
                        weekBmr += WellnessEngine.calculateBMRForDateRoom(this@CaloriesActivity, it, metrics).toInt()
                        weekActive += getBurnedCaloriesForDateRoom(it, metrics)
                        weekTef += WellnessEngine.calculateTEF(dailyIntake).toInt()
                    }

                    val weekTotalBurned = weekBmr + weekActive + weekTef
                    val scaleM = 4500 * 7
                    val heightIntake = (weekIntake * 250 / scaleM).coerceIn(if (weekIntake > 0) 2 else 0, 250)
                    val hBmr = (weekBmr * 250 / scaleM).coerceAtLeast(0)
                    val hActiveAndTef = ((weekActive + weekTef) * 250 / scaleM).coerceAtLeast(0)

                    barItems.add(BarItem(BarData(
                        label = "Week $weekIndex",
                        date = rangeFormatter.format(weekStart),
                        valueDisplay = if (weekIntake > 0) formatCaloriesText(weekIntake) else "0",
                        heightPx = dpToPx(heightIntake),
                        color = intakeColor,
                        backgroundRes = R.drawable.bg_calorie_bar,
                        isDoubleBar = true,
                        secondaryValueDisplay = if (weekTotalBurned > 0) formatCaloriesText(weekTotalBurned) else "0",
                        secondaryHeightPx = dpToPx(hBmr + hActiveAndTef),
                        secondaryColor = burnedColor,
                        secondaryBackgroundRes = R.drawable.bg_calorie_burned_bar,
                        isSecondaryStacked = true,
                        secondaryBottomHeightPx = dpToPx(hBmr),
                        secondaryBottomColor = ContextCompat.getColor(this@CaloriesActivity, R.color.graphCaloriesMaintenance),
                        secondaryTopHeightPx = dpToPx(hActiveAndTef),
                        secondaryTopBackgroundRes = R.drawable.bg_calorie_burned_bar,
                        isEmpty = weekIntake == 0 && weekActive == 0
                    )))
                    weekIndex++
                }
                if (barItems.isNotEmpty()) {
                    monthDataList.add(MonthData(monthName, yearName, barItems))
                }
            }

            withContext(Dispatchers.Main) {
                val rv = findViewById<RecyclerView>(R.id.rvMonthlyCalories)
                rv.adapter = MonthGraphAdapter(monthDataList.asReversed())
            }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showMealNutritionPopup(meal: Meal) {
        val todayStr = dateFormatter.format(Date())
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
                val row = LayoutInflater.from(this@CaloriesActivity).inflate(R.layout.item_nutrient_bar, llList, false)

                val tvName = row.findViewById<TextView>(R.id.tvNutrientName)
                tvName.text = if (isChild) "  ├ $name" else name
                if (isWarning) {
                    tvName.setTextColor(ContextCompat.getColor(this@CaloriesActivity, R.color.nutritionWarning))
                }

                val tvVal = row.findViewById<TextView>(R.id.tvNutrientValue)
                tvVal.text = displayVal
                if (isWarning) {
                    tvVal.setTextColor(ContextCompat.getColor(this@CaloriesActivity, R.color.nutritionWarning))
                }

                row.findViewById<View>(R.id.vCategoryAccent).setBackgroundColor(accentColorHex)

                val bar = row.findViewById<ProgressBar>(R.id.pbNutrientBar)
                bar.progress = if (pct == 0) 0 else pct.coerceIn(8, 100)
                bar.progressDrawable = ContextCompat.getDrawable(this@CaloriesActivity, drawableRes)
                llList.addView(row)
            }

            fun parseNum(str: String): Double {
                return Regex("""(\d+(?:\.\d+)?)""").find(str)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            }

            fun renderNutrientList(catIndex: Int) {
                llList.removeAllViews()

                val cMacros = ContextCompat.getColor(this@CaloriesActivity, R.color.nutritionMacros)
                val cVitamins = ContextCompat.getColor(this@CaloriesActivity, R.color.nutritionVitamins)
                val cMinerals = ContextCompat.getColor(this@CaloriesActivity, R.color.nutritionMinerals)
                val cAmino = ContextCompat.getColor(this@CaloriesActivity, R.color.nutritionAminoAcids)
                val cAntiox = ContextCompat.getColor(this@CaloriesActivity, R.color.nutritionAntioxidants)
                val cOther = ContextCompat.getColor(this@CaloriesActivity, R.color.nutritionOthers)

                // MACROS (Category 0 = All or Category 1 = Macros)
                if (catIndex == 0 || catIndex == 1) {
                    // Carbs & sub-types
                    addNutrientRow("Carbohydrates", "%.1f g".format(info.carbsG), cMacros, R.drawable.bg_nutrient_progress_macros)
                    info.carbBreakdown.forEach { (key, valStr) ->
                        val isAddedSugarWarning = key.lowercase().contains("added sugar") && parseNum(valStr) > 25.0
                        addNutrientRow(key, valStr, cMacros, R.drawable.bg_nutrient_progress_macros, isChild = true, isWarning = isAddedSugarWarning)
                    }

                    // Protein
                    addNutrientRow("Protein", "%.1f g".format(info.proteinG), cMacros, R.drawable.bg_nutrient_progress_macros)

                    // Fat & sub-types (Saturated Fat, Unsaturated, MUFA, PUFA, Omega-3, Omega-6, Trans Fat)
                    addNutrientRow("Fat", "%.1f g".format(info.fatG), cMacros, R.drawable.bg_nutrient_progress_macros)
                    info.fatBreakdown.forEach { (key, valStr) ->
                        val isTransFatWarning = key.lowercase().contains("trans fat") && parseNum(valStr) > 0.0
                        addNutrientRow(key, valStr, cMacros, R.drawable.bg_nutrient_progress_macros, isChild = true, isWarning = isTransFatWarning)
                    }

                    // Fiber
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
                categories.forEachIndexed { idx, catName ->
                    val isSelected = idx == activeCategoryIndex
                    val btn = Button(this@CaloriesActivity, null, android.R.attr.buttonStyleSmall).apply {
                        text = catName
                        setAllCaps(false)
                        textSize = 12f
                        setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(dpToPx(3), 0, dpToPx(3), 0) }

                        val drawable = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dpToPx(14).toFloat()
                            if (isSelected) {
                                setColor(if (isNightMode) 0xFF81C784.toInt() else 0xFF2E7D32.toInt())
                            } else {
                                setColor(if (isNightMode) 0xFF1C221D.toInt() else 0xFFF1F5F2.toInt())
                                setStroke(dpToPx(1), if (isNightMode) 0xFF354A3A.toInt() else 0xFFDDE1E6.toInt())
                            }
                        }
                        background = drawable
                        setTextColor(if (isSelected) Color.WHITE else if (isNightMode) 0xFFD0D5D2.toInt() else 0xFF4A554D.toInt())
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
                        planManager.saveMealForDate(todayStr, updatedMeal)
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

    inner class CalorieMealAdapter : RecyclerView.Adapter<CalorieMealAdapter.VH>() {
        private var items = listOf<Meal>()

        fun submitList(list: List<Meal>) {
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
            
            holder.tvTime.text = if (m.calories > 0) "${m.calories} kcal" else "AI Syncing..."

            val tvHint = holder.itemView.findViewById<TextView>(R.id.tvHint)
            tvHint.visibility = View.VISIBLE

            val hasDetails = m.nutritionInfo?.hasDetailedNutrition() == true
            if (m.calories == 0 || !hasDetails) {
                CalorieSearchEngine.getMealNutrition(holder.itemView.context, m.name, m.description) { info ->
                    holder.itemView.post {
                        if (info.hasDetailedNutrition()) {
                            holder.tvTime.text = "${info.calories} kcal"
                            val todayStr = dateFormatter.format(Date())
                            planManager.saveMealForDate(todayStr, m.copy(calories = info.calories, nutritionInfo = info))
                            refreshTodayView()
                        }
                    }
                }
            }

            holder.itemView.setOnClickListener {
                showMealNutritionPopup(m)
            }
            
            holder.itemView.findViewById<View>(R.id.switchEnabled).visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.btnEdit).visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.btnDelete).visibility = View.GONE
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
