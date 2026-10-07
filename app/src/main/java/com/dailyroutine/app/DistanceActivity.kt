package com.dailyroutine.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import android.widget.FrameLayout
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
import kotlin.math.roundToInt

class DistanceActivity : AppCompatActivity() {

    private lateinit var healthDataManager: HealthDataManager
    private lateinit var weeklyAdapter: WeeklyGraphAdapter
    private var distanceGoal: Float = 5.0f
    private var weeklyDistanceBarScale = WEEKLY_DISTANCE_BAR_SCALE

    private companion object {
        private const val BAR_MAX_HEIGHT_DP = 200
        private const val WEEKLY_DISTANCE_BAR_SCALE = 15.0 // 15km
        private const val MONTHLY_DISTANCE_BAR_SCALE = 100.0 // 100km
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_distance)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomPadding(findViewById(R.id.contentRoot))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
        toolbar.setNavigationOnClickListener { finish() }

        healthDataManager = HealthDataManager(this)
        
        distanceGoal = healthDataManager.getDailyDistanceGoal()
        updateGoalDisplay()

        findViewById<Button>(R.id.btnEditDistanceGoal).setOnClickListener { showDistanceGoalDialog() }
        findViewById<View>(R.id.cardTodaySteps).setOnClickListener { finish() }
        findViewById<View>(R.id.tvStepsCount).setOnClickListener { finish() }

        setupTabs()
        setupWeeklyGraph()
        refreshTodayView()
    }

    private fun setupWeeklyGraph() {
        val rv = findViewById<RecyclerView>(R.id.rvWeeklyDistanceGraph)
        rv.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        weeklyAdapter = WeeklyGraphAdapter()
        rv.adapter = weeklyAdapter
    }

    private fun showDistanceGoalDialog() {
        val input = EditText(this).apply {
            hint = "e.g. 5.0"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(distanceGoal.toString())
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Set Daily Distance Goal (km)")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val goal = input.text.toString().toFloatOrNull() ?: 5.0f
                distanceGoal = goal
                healthDataManager.setDailyDistanceGoal(goal)
                updateGoalDisplay()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateGoalDisplay() {
        findViewById<TextView>(R.id.tvDistanceGoal).text = "%.1f km".format(distanceGoal)
        updateGoalLines()
    }

    private fun updateGoalLines() {
        // Position goal lines after layout pass
        findViewById<View>(android.R.id.content).post {
            setupWeeklyGoalLine()
        }
    }

    private fun setupWeeklyGoalLine() {
        val overlay = findViewById<GoalLineOverlayView>(R.id.weeklyGoalOverlay) ?: return
        overlay.setGoalLines(emptyList())
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
                    1 -> {
                        refreshWeeklyView()
                        updateGoalLines()
                    }
                    2 -> {
                        refreshMonthlyView()
                        updateGoalLines()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun refreshTodayView() {
        lifecycleScope.launch {
            val stepsStr = healthDataManager.getSteps().replace(",", "")
            val steps = stepsStr.toIntOrNull() ?: 0
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            val distance = healthDataManager.getHistoricalDistanceRoom(todayStr)
                .let { if (it > 0) it else healthDataManager.calculateDistanceKm(steps) }

            withContext(Dispatchers.Main) {
                findViewById<TextView>(R.id.tvCurrentDistance).text = "%.1f".format(distance)
                findViewById<TextView>(R.id.tvStepsCount).text = "%,d".format(steps)

                val duration = healthDataManager.calculateDurationMin(steps)
                findViewById<TextView>(R.id.tvDuration).text = "%d min".format(duration)

                val heartPoints = healthDataManager.getHeartPoints()
                findViewById<TextView>(R.id.tvHeartPoints).text = heartPoints.toString()
            }
        }
    }

    private fun refreshWeeklyView() {
        lifecycleScope.launch {
            val today = Calendar.getInstance()
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(today.time)
            val startRange = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(HealthDataManager.SYNC_HISTORY_DAYS - 1)) }
            val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            // Permanently repair old Room records that were written in metres.
            healthDataManager.normalizeStoredDistanceData(
                dateFormatter.format(startRange.time),
                dateFormatter.format(today.time)
            )
            
            // 🚀 BATCH FETCH
            val metricsMap = healthDataManager.getMetricsMap(dateFormatter.format(startRange.time), dateFormatter.format(today.time))

            val weekGroups = HistoryDateOrder.monthBoundedWeeklyGroups(HealthDataManager.SYNC_HISTORY_DAYS)
            val dayLabelFormatter = SimpleDateFormat("EEE", Locale.US)
            val dateBarFormatter = SimpleDateFormat("dd MMM", Locale.US)

            data class DailyDistance(val calendar: Calendar, val distanceKm: Double, val hasSignal: Boolean)
            val dailyDistances = mutableListOf<DailyDistance?>()
            val distGraphColor = ContextCompat.getColor(this@DistanceActivity, R.color.graphDistance)

            weekGroups.forEachIndexed { weekIndex, weekDates ->
                weekDates.forEach { calendar ->
                    val dateStr = dateFormatter.format(calendar.time)
                    val m = metricsMap[dateStr]

                    val dailySteps = if (dateStr == todayStr) {
                        healthDataManager.getSteps().replace(",", "").toIntOrNull() ?: 0
                    } else {
                        m?.steps?.toInt() ?: healthDataManager.getHistoricalSteps(dateStr).toInt()
                    }
                    
                    val dailyDist = if (dateStr == todayStr) {
                        val stepsStr = healthDataManager.getSteps().replace(",", "")
                        val steps = stepsStr.toIntOrNull() ?: 0
                        healthDataManager.getHistoricalDistanceRoom(todayStr)
                            .let { if (it > 0) it else healthDataManager.calculateDistanceKm(steps) }
                    } else {
                        val rawDist = m?.distanceKm ?: healthDataManager.getHistoricalDistance(dateStr)
                        val normalizedDist = HealthDataManager.normalizeDistanceKm(rawDist)
                        if (normalizedDist > 0) normalizedDist else healthDataManager.calculateDistanceKm(dailySteps)
                    }

                    dailyDistances += DailyDistance(calendar, dailyDist, dailySteps >= 200 || dailyDist > 0.1)
                }
                if (weekIndex != weekGroups.lastIndex) dailyDistances += null
            }

            // Scale the whole visible series from the actual values. This keeps every
            // bar proportional and avoids clipping distinct distances at a fixed ceiling.
            val maxDistance = dailyDistances.filterNotNull().maxOfOrNull { it.distanceKm } ?: 0.0
            val graphScale = maxOf(distanceGoal.toDouble(), maxDistance, 1.0)
            val graphItems = dailyDistances.map { day ->
                if (day == null) {
                    WeeklyGraphItem(dayLabel = "", dateLabel = "", primaryValue = "", primaryHeightPx = 0, primaryColor = 0, isDivider = true)
                } else {
                    val dHeight = if (day.hasSignal && day.distanceKm > 0.0) {
                        (day.distanceKm / graphScale * BAR_MAX_HEIGHT_DP).roundToInt().coerceIn(1, BAR_MAX_HEIGHT_DP)
                    } else 0
                    WeeklyGraphItem(
                        dayLabel = dayLabelFormatter.format(day.calendar.time),
                        dateLabel = dateBarFormatter.format(day.calendar.time),
                        primaryValue = "%.1f km".format(day.distanceKm),
                        primaryHeightPx = dpToPx(dHeight.coerceAtMost(BAR_MAX_HEIGHT_DP)),
                        primaryColor = distGraphColor,
                        primaryBackgroundRes = R.drawable.bg_distance_bar_rounded,
                        secondaryValue = null,
                        hasSignal = day.hasSignal
                    )
                }
            }

            withContext(Dispatchers.Main) {
                weeklyDistanceBarScale = graphScale
                weeklyAdapter.submitList(graphItems)
                setupWeeklyGoalLine()
            }
        }
    }

    private fun refreshMonthlyView() {
        lifecycleScope.launch {
            val rv = findViewById<RecyclerView>(R.id.rvMonthlyDistance)
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
            val distGraphColor = ContextCompat.getColor(this@DistanceActivity, R.color.graphDistance)

            while (!calendar.after(today)) {
                val monthName = monthFormatter.format(calendar.time)
                val yearName = yearFormatter.format(calendar.time)
                val currentMonth = calendar.get(Calendar.MONTH)
                val isCurrentMonth = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) && currentMonth == today.get(Calendar.MONTH)

                val barItems = mutableListOf<BarItem>()
                var weekIndex = 1

                if (isCurrentMonth) {
                    HistoryDateOrder.monthWeeksForMonthlyView(calendar, today).forEach { week ->
                        var weekDist = 0.0

                        if (week.isComplete) {
                            week.dates.forEach { day ->
                                val dateKey = dateFormatter.format(day.time)
                                if (!day.before(oldestSyncedDay) && !day.after(today)) {
                                    val m = metricsMap[dateKey]
                                    var sDist = m?.distanceKm ?: healthDataManager.getHistoricalDistance(dateKey)
                                    if (sDist > 100.0) sDist /= 1000.0
                                    
                                    weekDist += if (sDist > 0.0) sDist else healthDataManager.calculateDistanceKm((m?.steps?.toInt() ?: healthDataManager.getHistoricalSteps(dateKey).toInt()))
                                }
                            }
                        }

                        // Standardized 200dp logic with precise Double math
                        val heightDist = (weekDist * 200.0 / MONTHLY_DISTANCE_BAR_SCALE).toInt().coerceIn(if (weekDist > 0) 2 else 0, 200)

                        barItems.add(BarItem(BarData(
                            label = "Week $weekIndex",
                            date = rangeFormatter.format(week.start.time),
                            valueDisplay = if (weekDist > 0.05) "%.1f km".format(weekDist) else "-",
                            heightPx = dpToPx(heightDist),
                            color = distGraphColor,
                            backgroundRes = R.drawable.bg_distance_bar_rounded,
                            isEmpty = weekDist <= 0.05
                        )))
                        weekIndex++
                    }

                    calendar.add(Calendar.MONTH, 1)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    if (barItems.isNotEmpty()) {
                        monthDataList.add(MonthData(
                            monthName, yearName, barItems,
                            emptyList()
                        ))
                    }
                    continue
                }

                while (calendar.get(Calendar.MONTH) == currentMonth) {
                    var weekDist = 0.0
                    val weekStart = calendar.time

                    var isWeekOver = false
                    while (!isWeekOver) {
                        val dateKey = dateFormatter.format(calendar.time)
                        if (!calendar.before(oldestSyncedDay) && !calendar.after(today)) {
                            val m = metricsMap[dateKey]
                            var sDist = m?.distanceKm ?: healthDataManager.getHistoricalDistance(dateKey)
                            if (sDist > 100.0) sDist /= 1000.0

                            weekDist += if (sDist > 0.0) sDist else healthDataManager.calculateDistanceKm((m?.steps?.toInt() ?: healthDataManager.getHistoricalSteps(dateKey).toInt()))
                        }

                        val isSunday = (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                        val isNewMonth = (calendar.get(Calendar.MONTH) != currentMonth)
                        val isAfterToday = calendar.after(today)

                        if (isSunday || isNewMonth || isAfterToday) isWeekOver = true
                    }

                    // Standardized 200dp logic with precise Double math
                    val heightDist = (weekDist * 200.0 / MONTHLY_DISTANCE_BAR_SCALE).toInt().coerceIn(if (weekDist > 0) 2 else 0, 200)

                            barItems.add(BarItem(BarData(
                            label = "Week $weekIndex",
                            date = rangeFormatter.format(weekStart),
                            valueDisplay = if (weekDist > 0.05) "%.1f km".format(weekDist) else "-",
                            heightPx = dpToPx(heightDist),
                            color = distGraphColor,
                            backgroundRes = R.drawable.bg_distance_bar_rounded,
                            isEmpty = weekDist <= 0.05
                        )))
                    weekIndex++
                }

                if (barItems.isNotEmpty()) {
                    monthDataList.add(MonthData(
                        monthName, yearName, barItems,
                        emptyList()
                    ))
                }
            }

            withContext(Dispatchers.Main) {
                rv.adapter = MonthGraphAdapter(monthDataList.asReversed())
                rv.onFlingListener = null
                androidx.recyclerview.widget.PagerSnapHelper().attachToRecyclerView(rv)
            }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
