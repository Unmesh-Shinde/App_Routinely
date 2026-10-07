package com.dailyroutine.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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

class WalkingDataActivity : AppCompatActivity() {

    private lateinit var healthDataManager: HealthDataManager
    private lateinit var weeklyAdapter: WeeklyGraphAdapter
    private val monthlySnapHelper = androidx.recyclerview.widget.PagerSnapHelper()
    private var stepGoal: Int = 10000

    private companion object {
        private const val BAR_MAX_HEIGHT_DP = 200
        private const val WEEKLY_STEPS_BAR_SCALE = 20_000
        private const val WEEKLY_HEART_POINTS_BAR_SCALE = 135
        private const val MONTHLY_STEPS_BAR_SCALE = 140_000
        private const val MONTHLY_HEART_POINTS_BAR_SCALE = 700
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_walking_data)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomPadding(findViewById(R.id.contentRoot))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
        toolbar.setNavigationOnClickListener { finish() }

        healthDataManager = HealthDataManager(this)
        
        stepGoal = healthDataManager.getDailyStepGoal()
        updateGoalDisplay()

        findViewById<Button>(R.id.btnEditStepGoal).setOnClickListener { showStepGoalDialog() }
        findViewById<View>(R.id.cardDistance).setOnClickListener {
            startActivity(Intent(this, DistanceActivity::class.java))
        }

        // Navigation shortcuts to Weekly tab
        findViewById<View>(R.id.tvCurrentSteps).setOnClickListener {
            findViewById<TabLayout>(R.id.tabLayout).getTabAt(1)?.select()
        }
        findViewById<View>(R.id.cardHeartPoints).setOnClickListener {
            findViewById<TabLayout>(R.id.tabLayout).getTabAt(1)?.select()
        }

        setupTabs()
        setupWeeklyGraph()
        setupMonthlyGraph()
        refreshTodayView()
    }

    private fun setupMonthlyGraph() {
        val rv = findViewById<RecyclerView>(R.id.rvMonthlySteps)
        if (rv.onFlingListener == null) {
            monthlySnapHelper.attachToRecyclerView(rv)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::healthDataManager.isInitialized) {
            stepGoal = healthDataManager.getDailyStepGoal()
            updateGoalDisplay()
            when (findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition) {
                0 -> refreshTodayView()
                1 -> refreshWeeklyView()
                2 -> refreshMonthlyView()
            }
        }
    }

    private fun setupWeeklyGraph() {
        val rv = findViewById<RecyclerView>(R.id.rvWeeklyStepsGraph)
        rv.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        weeklyAdapter = WeeklyGraphAdapter()
        rv.adapter = weeklyAdapter
    }

    private fun showStepGoalDialog() {
        val input = EditText(this).apply {
            hint = "e.g. 10000"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(stepGoal.toString())
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Set Daily Step Goal")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val goal = input.text.toString().toIntOrNull() ?: 10000
                stepGoal = goal
                healthDataManager.setDailyStepGoal(goal)
                updateGoalDisplay()
                when (findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition) {
                    1 -> refreshWeeklyView()
                    2 -> refreshMonthlyView()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateGoalDisplay() {
        findViewById<TextView>(R.id.tvStepGoal).text = getString(R.string.step_goal_value, stepGoal)
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

    private fun refreshTodayView() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val stepsStr = healthDataManager.getSteps().replace(",", "")
        val steps = stepsStr.toIntOrNull() ?: 0
        
        findViewById<TextView>(R.id.tvCurrentSteps).text = "%,d".format(steps)
        
        // 1. Priority: Synced Distance from Google Fit/Health Connect
        val syncedDistance = healthDataManager.getHistoricalDistance(todayStr)
        val distance = if (syncedDistance > 0.0) syncedDistance else healthDataManager.calculateDistanceKm(steps)
        
        findViewById<TextView>(R.id.tvDistance).text = getString(R.string.distance_km_value, distance)

        val duration = healthDataManager.calculateDurationMin(steps)
        findViewById<TextView>(R.id.tvDuration).text = getString(R.string.duration_min_value, duration)

        val heartPoints = healthDataManager.getHeartPoints()
        val isConnected = healthDataManager.isConnected()

        android.util.Log.d("WalkingDataActivity", "Today Heart Points: $heartPoints, isConnected: $isConnected")

        findViewById<TextView>(R.id.tvHeartPoints).text = heartPoints.toString()
    }

    private fun refreshWeeklyView() {
        lifecycleScope.launch {
            val today = Calendar.getInstance()
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(today.time)
            val startRange = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(HealthDataManager.SYNC_HISTORY_DAYS - 1)) }
            val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            
            // 🚀 BATCH FETCH
            val metricsMap = healthDataManager.getMetricsMap(dateFormatter.format(startRange.time), dateFormatter.format(today.time))

            val weekGroups = HistoryDateOrder.monthBoundedWeeklyGroups(HealthDataManager.SYNC_HISTORY_DAYS)
            val dayLabelFormatter = SimpleDateFormat("EEE", Locale.US)
            val dateBarFormatter = SimpleDateFormat("dd MMM", Locale.US)

            val graphItems = mutableListOf<WeeklyGraphItem>()
            val stepGraphColor = ContextCompat.getColor(this@WalkingDataActivity, R.color.graphSteps)
            val hpGraphColor = ContextCompat.getColor(this@WalkingDataActivity, R.color.healthCalories)

            weekGroups.forEachIndexed { weekIndex, weekDates ->
                weekDates.forEach { calendar ->
                    val dateStr = dateFormatter.format(calendar.time)
                    val metrics = metricsMap[dateStr]
                    val isConnected = healthDataManager.isConnected()
                    
                    val dailySteps = if (dateStr == todayStr) {
                        healthDataManager.getSteps().replace(",", "").toIntOrNull() ?: 0
                    } else if (isConnected) {
                        metrics?.steps?.toInt() ?: healthDataManager.getHistoricalSteps(dateStr).toInt()
                    } else 0
                    
                    // The weekly heart-points bar must reflect an explicit synced
                    // value for that date. Do not fall back to legacy cached values:
                    // they can belong to an earlier sync and create a false bar.
                    val dailyHP = if (isConnected) {
                        metrics?.heartPoints?.toInt()?.coerceAtLeast(0) ?: 0
                    } else 0
                    
                    val hasSignal = dailySteps >= 200 || dailyHP > 0

                    val sHeight = if (hasSignal && dailySteps > 0) (dailySteps.toDouble() * BAR_MAX_HEIGHT_DP.toDouble() / WEEKLY_STEPS_BAR_SCALE).toInt().coerceAtLeast(2) else 0
                    val hHeight = if (hasSignal && dailyHP > 0) (dailyHP.toDouble() * BAR_MAX_HEIGHT_DP.toDouble() / WEEKLY_HEART_POINTS_BAR_SCALE).toInt().coerceAtLeast(2) else 0

                    graphItems.add(WeeklyGraphItem(
                        dayLabel = dayLabelFormatter.format(calendar.time),
                        dateLabel = dateBarFormatter.format(calendar.time),
                        primaryValue = if (dailySteps > 0) formatStepText(dailySteps) else "0",
                        primaryHeightPx = dpToPx(sHeight.coerceAtMost(BAR_MAX_HEIGHT_DP)),
                        primaryColor = stepGraphColor,
                        primaryBackgroundRes = R.drawable.bg_step_bar_rounded,
                        // Keep the zero label, while hiding the zero-value heart-points bar.
                        secondaryValue = dailyHP.toString(),
                        secondaryHeightPx = dpToPx(hHeight.coerceAtMost(BAR_MAX_HEIGHT_DP)),
                        secondaryColor = hpGraphColor,
                        secondaryBackgroundRes = R.drawable.bg_calorie_bar,
                        showSecondaryBar = dailyHP > 0,
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
                findViewById<GoalLineOverlayView>(R.id.weeklyStepsGoalOverlay).apply {
                    visibility = View.VISIBLE
                    setGoalLines(stepsGoalLines(stepGoal.toDouble(), WEEKLY_STEPS_BAR_SCALE.toDouble()))
                }
                weeklyAdapter.submitList(graphItems)
            }
        }
    }

    private fun formatStepText(steps: Int): String {
        return if (steps >= 1000) "%.1fk".format(steps / 1000.0) else steps.toString()
    }

    private fun refreshMonthlyView() {
        lifecycleScope.launch {
            val rv = findViewById<RecyclerView>(R.id.rvMonthlySteps)
            val today = Calendar.getInstance()
            val startRange = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(HealthDataManager.SYNC_HISTORY_DAYS - 1)) }
            val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            
            // 🚀 BATCH FETCH
            val metricsMap = healthDataManager.getMetricsMap(dateFormatter.format(startRange.time), dateFormatter.format(today.time))

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
            val stepGraphColor = ContextCompat.getColor(this@WalkingDataActivity, R.color.graphSteps)

            while (!calendar.after(today)) {
                val monthName = monthFormatter.format(calendar.time)
                val yearName = yearFormatter.format(calendar.time)
                val currentMonth = calendar.get(Calendar.MONTH)
                val isCurrentMonth = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) && currentMonth == today.get(Calendar.MONTH)

                val barItems = mutableListOf<BarItem>()
                var weekIndex = 1

                if (isCurrentMonth) {
                    HistoryDateOrder.monthWeeksForMonthlyView(calendar, today).forEach { week ->
                        var weekSteps = 0L
                        var weekHP = 0L

                        if (week.isComplete) {
                            week.dates.forEach { day ->
                                val dateKey = dateFormatter.format(day.time)
                                if (!day.before(oldestSyncedDay) && !day.after(today)) {
                                    val m = metricsMap[dateKey]
                                    weekSteps += m?.steps ?: healthDataManager.getHistoricalSteps(dateKey)
                                    weekHP += (m?.heartPoints?.toLong() ?: healthDataManager.getHistoricalHeartPoints(dateKey).toLong())
                                }
                            }
                        }

                        // Standardized 200dp logic
                        val heightSteps = (weekSteps.toDouble() * 200.0 / MONTHLY_STEPS_BAR_SCALE.toDouble()).toInt().coerceIn(if (weekSteps > 0) 2 else 0, 200)
                        val heightHP = (weekHP.toDouble() * 200.0 / MONTHLY_HEART_POINTS_BAR_SCALE.toDouble()).toInt().coerceIn(if (weekHP > 0) 2 else 0, 200)

                        barItems.add(BarItem(BarData(
                            label = "Week $weekIndex",
                            date = rangeFormatter.format(week.start.time),
                            valueDisplay = if (weekSteps > 0) formatStepText(weekSteps.toInt()) else "0",
                            heightPx = dpToPx(heightSteps),
                            color = stepGraphColor,
                            backgroundRes = R.drawable.bg_step_bar_rounded,
                            isDoubleBar = true,
                            secondaryValueDisplay = if (weekHP > 0) weekHP.toString() else "0",
                            secondaryHeightPx = dpToPx(heightHP),
                            secondaryBackgroundRes = R.drawable.bg_calorie_bar,
                            isEmpty = weekSteps < 200 && weekHP == 0L
                        )))
                        weekIndex++
                    }

                    calendar.add(Calendar.MONTH, 1)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    if (barItems.isNotEmpty()) {
                        monthDataList.add(MonthData(monthName, yearName, barItems, stepsGoalLines(stepGoal * 7.0, MONTHLY_STEPS_BAR_SCALE.toDouble())))
                    }
                    continue
                }

                while (calendar.get(Calendar.MONTH) == currentMonth) {
                    var weekSteps = 0L
                    var weekHP = 0L
                    val weekStart = calendar.time

                    var isWeekOver = false
                    while (!isWeekOver) {
                        val dateKey = dateFormatter.format(calendar.time)
                        if (!calendar.before(oldestSyncedDay) && !calendar.after(today)) {
                            val m = metricsMap[dateKey]
                            weekSteps += m?.steps ?: healthDataManager.getHistoricalSteps(dateKey)
                            weekHP += (m?.heartPoints?.toLong() ?: healthDataManager.getHistoricalHeartPoints(dateKey).toLong())
                        }

                        val isSunday = (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)

                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                        val isNewMonth = (calendar.get(Calendar.MONTH) != currentMonth)
                        val isAfterToday = calendar.after(today)

                        if (isSunday || isNewMonth || isAfterToday) {
                            isWeekOver = true
                        }
                    }

                    val displayValSteps = if (weekSteps > 0) formatStepText(weekSteps.toInt()) else "-"
                    val displayValHP = if (weekHP > 0) weekHP.toString() else "-"

                    // Standardized 200dp logic
                    val heightSteps = (weekSteps.toDouble() * 200.0 / MONTHLY_STEPS_BAR_SCALE.toDouble()).toInt().coerceIn(if (weekSteps > 0) 2 else 0, 200)
                    val heightHP = (weekHP.toDouble() * 200.0 / MONTHLY_HEART_POINTS_BAR_SCALE.toDouble()).toInt().coerceIn(if (weekHP > 0) 2 else 0, 200)

                    barItems.add(BarItem(BarData(
                        label = "Week $weekIndex",
                        date = rangeFormatter.format(weekStart),
                        valueDisplay = displayValSteps,
                        heightPx = dpToPx(heightSteps),
                        color = stepGraphColor,
                        backgroundRes = R.drawable.bg_step_bar_rounded,
                        isDoubleBar = true,
                        secondaryValueDisplay = displayValHP,
                        secondaryHeightPx = dpToPx(heightHP),
                        secondaryBackgroundRes = R.drawable.bg_calorie_bar
                    )))
                    weekIndex++
                }

                if (barItems.isNotEmpty()) {
                    monthDataList.add(MonthData(monthName, yearName, barItems, stepsGoalLines(stepGoal * 7.0, MONTHLY_STEPS_BAR_SCALE.toDouble())))
                }
            }

            withContext(Dispatchers.Main) {
                rv.adapter = MonthGraphAdapter(monthDataList.asReversed())
            }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun stepsGoalLines(goal: Double, maxValue: Double): List<GoalLineSpec> {
        return if (goal > 0.0) listOf(
            GoalLineSpec(
                value = goal,
                maxValue = maxValue,
                label = "Steps Goal: %,d".format(Locale.US, goal.toLong()),
                color = ContextCompat.getColor(this, R.color.graphSteps)
            )
        ) else emptyList()
    }
}
