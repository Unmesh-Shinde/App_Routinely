package com.dailyroutine.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*

class WalkingDataActivity : AppCompatActivity() {

    private lateinit var healthDataManager: HealthDataManager
    private var stepGoal: Int = 10000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_walking_data)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomPadding(findViewById(R.id.contentRoot))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        healthDataManager = HealthDataManager(this)
        
        stepGoal = healthDataManager.getDailyStepGoal()
        updateGoalDisplay()

        findViewById<Button>(R.id.btnEditStepGoal).setOnClickListener { showStepGoalDialog() }

        setupTabs()
        refreshTodayView()
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
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateGoalDisplay() {
        findViewById<TextView>(R.id.tvStepGoal).text = "%,d steps".format(stepGoal)
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
        val stepsStr = healthDataManager.getSteps().replace(",", "")
        val steps = stepsStr.toIntOrNull() ?: 0
        
        findViewById<TextView>(R.id.tvCurrentSteps).text = "%,d".format(steps)
        
        val distance = healthDataManager.calculateDistanceKm(steps)
        findViewById<TextView>(R.id.tvDistance).text = "%.2f km".format(distance)
        
        val duration = healthDataManager.calculateDurationMin(steps)
        findViewById<TextView>(R.id.tvDuration).text = "%d min".format(duration)

        val heartPoints = healthDataManager.getHeartPoints()
        val isConnected = healthDataManager.isConnected()

        android.util.Log.d("WalkingDataActivity", "Today Heart Points: $heartPoints, isConnected: $isConnected")

        findViewById<TextView>(R.id.tvHeartPoints).text = heartPoints.toString()
    }

    private fun refreshWeeklyView() {
        val container = findViewById<LinearLayout>(R.id.llWeeklyStepsGraph)
        container.removeAllViews()

        val weekGroups = HistoryDateOrder.monthBoundedWeeklyGroups(HealthDataManager.SYNC_HISTORY_DAYS)

        val dayLabelFormatter = SimpleDateFormat("EEE", Locale.US)
        val dateBarFormatter = SimpleDateFormat("dd MMM", Locale.US)
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        weekGroups.forEachIndexed { weekIndex, weekDates ->
            weekDates.forEach { calendar ->
                val dateStr = dateFormatter.format(calendar.time)

            // Filter: Only show data if health is connected and there's a signal
                val isConnected = healthDataManager.isConnected()
                val dailySteps = if (isConnected) healthDataManager.getHistoricalSteps(dateStr).toInt() else 0
                val dailyHP = if (isConnected) healthDataManager.getHistoricalHeartPoints(dateStr).toInt() else 0

                val hasSignal = dailySteps >= 200 || dailyHP > 0
                if (dailyHP > 0) {
                    android.util.Log.d("WalkingDataActivity", "Weekly - Date: $dateStr, Steps: $dailySteps, HP: $dailyHP")
                }

                val barView = LayoutInflater.from(this).inflate(R.layout.item_step_heart_bar, container, false)
                barView.findViewById<TextView>(R.id.tvBarLabel).text = dayLabelFormatter.format(calendar.time)
                barView.findViewById<TextView>(R.id.tvBarDate).text = dateBarFormatter.format(calendar.time)

                val tvSteps = barView.findViewById<TextView>(R.id.tvStepValue)
                val tvHP = barView.findViewById<TextView>(R.id.tvHeartValue)

                tvSteps.text = if (hasSignal && dailySteps > 0) formatStepText(dailySteps) else "-"
                tvHP.text = if (hasSignal && dailyHP > 0) dailyHP.toString() else "-"

                val viewStepBar = barView.findViewById<View>(R.id.viewStepBar)
                val viewHeartBar = barView.findViewById<View>(R.id.viewHeartBar)

                val stepParams = viewStepBar.layoutParams as LinearLayout.LayoutParams
                val sHeight = if (hasSignal && dailySteps > 0) (dailySteps * 250 / 15000).coerceAtLeast(2) else 0
                stepParams.height = dpToPx(sHeight.coerceAtMost(250))
                viewStepBar.layoutParams = stepParams

                val heartParams = viewHeartBar.layoutParams as LinearLayout.LayoutParams
                val hHeight = if (hasSignal && dailyHP > 0) (dailyHP * 250 / 100).coerceAtLeast(2) else 0
                heartParams.height = dpToPx(hHeight.coerceAtMost(250))
                viewHeartBar.layoutParams = heartParams

                container.addView(barView)
            }

            if (weekIndex != weekGroups.lastIndex) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(3), dpToPx(180)).apply {
                        setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(40))
                    }
                    setBackgroundColor(0xFF455A64.toInt())
                }
                container.addView(divider)
            }
        }
    }

    private fun formatStepText(steps: Int): String {
        return if (steps >= 1000) "%.1fk".format(steps / 1000.0) else steps.toString()
    }

    private fun refreshMonthlyView() {
        val rv = findViewById<RecyclerView>(R.id.rvMonthlySteps)
        val monthDataList = mutableListOf<MonthData>()
        
        val today = Calendar.getInstance()
        val oldestSyncedDay = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -(HealthDataManager.SYNC_HISTORY_DAYS - 1))
        }
        val calendar = oldestSyncedDay.clone() as Calendar
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        val monthFormatter = SimpleDateFormat("MMMM", Locale.US)
        val yearFormatter = SimpleDateFormat("yyyy", Locale.US)
        val rangeFormatter = SimpleDateFormat("dd MMM", Locale.US)
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

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
                                weekSteps += healthDataManager.getHistoricalSteps(dateKey)
                                weekHP += healthDataManager.getHistoricalHeartPoints(dateKey).toLong()
                            }
                        }
                    }

                    val heightSteps = (weekSteps * 250 / 100000).toInt().coerceIn(if (weekSteps > 0) 2 else 0, 250)
                    val heightHP = (weekHP * 250 / 500).toInt().coerceIn(if (weekHP > 0) 2 else 0, 250)

                    barItems.add(BarItem(BarData(
                        label = "Week $weekIndex",
                        date = rangeFormatter.format(week.start.time),
                        valueDisplay = if (weekSteps > 0) formatStepText(weekSteps.toInt()) else "-",
                        heightPx = dpToPx(heightSteps),
                        color = 0xFF009688.toInt(),
                        isDoubleBar = true,
                        secondaryValueDisplay = if (weekHP > 0) weekHP.toString() else "-",
                        secondaryHeightPx = dpToPx(heightHP)
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
                var weekSteps = 0L
                var weekHP = 0L
                val weekStart = calendar.time
                val weekDates = mutableListOf<String>()
                
                var isWeekOver = false
                while (!isWeekOver) {
                    val dateKey = dateFormatter.format(calendar.time)
                    if (!calendar.before(oldestSyncedDay) && !calendar.after(today)) {
                        weekSteps += healthDataManager.getHistoricalSteps(dateKey)
                        weekHP += healthDataManager.getHistoricalHeartPoints(dateKey).toLong()
                        weekDates.add(dateKey)
                    }
                    
                    val isSunday = (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                    
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    val isNewMonth = (calendar.get(Calendar.MONTH) != currentMonth)
                    val isAfterToday = calendar.after(today)
                    
                    if (isSunday || isNewMonth || isAfterToday) {
                        isWeekOver = true
                    }
                }

                // For monthly view, we reuse MonthGraphAdapter but it only supports single bars.
                // We'll calculate a combined "Activity" score or just show Steps for now,
                // OR update MonthGraphAdapter to handle double bars.
                // I'll stick to a combined view or just steps to maintain pattern if adapter is shared.

                val displayValSteps = if (weekSteps > 0) formatStepText(weekSteps.toInt()) else "-"
                val displayValHP = if (weekHP > 0) weekHP.toString() else "-"

                val heightSteps = (weekSteps * 250 / 100000).toInt().coerceIn(if (weekSteps > 0) 2 else 0, 250)
                val heightHP = (weekHP * 250 / 500).toInt().coerceIn(if (weekHP > 0) 2 else 0, 250)

                barItems.add(BarItem(BarData(
                    label = "Week $weekIndex",
                    date = rangeFormatter.format(weekStart),
                    valueDisplay = displayValSteps,
                    heightPx = dpToPx(heightSteps),
                    color = 0xFF009688.toInt(),
                    isDoubleBar = true,
                    secondaryValueDisplay = displayValHP,
                    secondaryHeightPx = dpToPx(heightHP)
                )))
                weekIndex++
            }

            if (barItems.isNotEmpty()) {
                monthDataList.add(MonthData(monthName, yearName, barItems))
            }
        }

        rv.adapter = MonthGraphAdapter(monthDataList.asReversed())
        rv.onFlingListener = null
        androidx.recyclerview.widget.PagerSnapHelper().attachToRecyclerView(rv)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
