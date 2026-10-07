package com.dailyroutine.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class WeightActivity : AppCompatActivity() {

    private lateinit var healthDataManager: HealthDataManager
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weight)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomPadding(findViewById(R.id.weightContentContainer))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
        toolbar.setNavigationOnClickListener { finish() }

        healthDataManager = HealthDataManager(this)

        findViewById<Button>(R.id.btnLogWeight).setOnClickListener { showWeightDialog() }

        setupTabs()
        refreshTodayView()
    }

    override fun onResume() {
        super.onResume()
        updateIdealWeightMessage()
        if (::healthDataManager.isInitialized) {
            when (findViewById<TabLayout>(R.id.tabLayoutWeight).selectedTabPosition) {
                0 -> refreshTodayView()
                1 -> refreshWeeklyView()
                2 -> refreshMonthlyView()
            }
        }
    }

    private fun showWeightDialog() {
        val input = EditText(this).apply {
            hint = "e.g. 75.5"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            val todayStr = dateFormatter.format(Date())
            val current = healthDataManager.getWeight(todayStr)
            if (current > 0) setText(current.toString())
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Log Today's Weight")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val weight = input.text.toString().toDoubleOrNull() ?: 0.0
                if (weight > 0) {
                    val todayStr = dateFormatter.format(Date())
                    healthDataManager.saveWeight(todayStr, weight)
                    UserPreferencesStore.setUserWeight(this, weight)
                    refreshTodayView()
                    Toast.makeText(this, "Weight Logged! ⚖️", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupTabs() {
        val tabLayout = findViewById<TabLayout>(R.id.tabLayoutWeight)
        val vToday = findViewById<View>(R.id.viewTodayWeight)
        val vWeekly = findViewById<View>(R.id.viewWeeklyWeight)
        val vMonthly = findViewById<View>(R.id.viewMonthlyWeight)

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
        lifecycleScope.launch {
            val todayStr = dateFormatter.format(Date())
            val weight = healthDataManager.getWeightRoom(todayStr)
            withContext(Dispatchers.Main) {
                findViewById<TextView>(R.id.tvCurrentWeight).text = if (weight > 0) "%.1f kg".format(weight) else "No weight data added"
                updateIdealWeightMessage()
            }
        }
    }

    private fun updateIdealWeightMessage() {
        val tv = findViewById<TextView>(R.id.tvIdealWeightMessage)
        val metrics = ProfileHealthMetricsCalculator.calculate(this)
        if (metrics == null) {
            tv.text = "Complete age, height, weight, and gender in Profile to calculate your ideal weight."
            return
        }
        tv.text = "Ideal Weight: %.1f kg according to your profile.".format(metrics.idealWeightKg)
    }

    private fun refreshWeeklyView() {
        lifecycleScope.launch {
            val idealWeight = ProfileHealthMetricsCalculator.calculate(this@WeightActivity)?.idealWeightKg
            val container = findViewById<LinearLayout>(R.id.llWeeklyWeightGraph)
            val today = Calendar.getInstance()
            val startRange = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(HealthDataManager.SYNC_HISTORY_DAYS - 1)) }
            val metricsMap = healthDataManager.getMetricsMap(dateFormatter.format(startRange.time), dateFormatter.format(today.time))

            withContext(Dispatchers.Main) {
                findViewById<GoalLineOverlayView>(R.id.weeklyWeightGoalOverlay)
                    .setGoalLines(weightGoalLines(idealWeight))
                container.removeAllViews()

                val weekGroups = HistoryDateOrder.monthBoundedWeeklyGroups(HealthDataManager.SYNC_HISTORY_DAYS)

                val dayLabelFormatter = SimpleDateFormat("EEE", Locale.US)
                val dateBarFormatter = SimpleDateFormat("dd MMM", Locale.US)

                weekGroups.forEachIndexed { weekIndex, weekDates ->
                    weekDates.forEach { calendar ->
                        val dateStr = dateFormatter.format(calendar.time)
                        val weight = metricsMap[dateStr]?.weightKg ?: healthDataManager.getWeight(dateStr)

                        val barView = LayoutInflater.from(this@WeightActivity).inflate(R.layout.item_calorie_bar, container, false)
                        barView.findViewById<TextView>(R.id.tvBarLabel).text = dayLabelFormatter.format(calendar.time)
                        barView.findViewById<TextView>(R.id.tvBarDate).text = dateBarFormatter.format(calendar.time)

                        val tvValue = barView.findViewById<TextView>(R.id.tvBarValue)
                        val tvEmpty = barView.findViewById<TextView>(R.id.tvEmptyError)
                        val viewBar = barView.findViewById<View>(R.id.viewBar)

                        if (weight > 0) {
                            tvValue.text = "%.0f kg".format(weight)
                            tvValue.visibility = View.VISIBLE
                            tvEmpty.visibility = View.GONE
                            viewBar.visibility = View.VISIBLE
                        } else {
                            tvValue.visibility = View.INVISIBLE
                            tvEmpty.visibility = View.VISIBLE
                            viewBar.visibility = View.INVISIBLE
                        }

                        viewBar.setBackgroundResource(R.drawable.bg_weight_bar)
                        // Max height is 250dp for 150kg
                        viewBar.layoutParams.height = if (weight > 0) (weight * 250 / 150.0).toInt().let { dpToPx(it) } else 0

                        container.addView(barView)
                    }

                    if (weekIndex != weekGroups.lastIndex) {
                        val divider = View(this@WeightActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(dpToPx(3), dpToPx(180)).apply {
                                setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(40))
                            }
                            setBackgroundColor(0xFF455A64.toInt())
                        }
                        container.addView(divider)
                    }
                }
            }
        }
    }

    private fun refreshMonthlyView() {
        lifecycleScope.launch {
            val idealWeight = ProfileHealthMetricsCalculator.calculate(this@WeightActivity)?.idealWeightKg
            val rv = findViewById<RecyclerView>(R.id.rvMonthlyWeight)
            val today = Calendar.getInstance()
            val startRange = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(HealthDataManager.SYNC_HISTORY_DAYS - 1)) }
            
            // 🚀 BATCH FETCH
            val metricsMap = healthDataManager.getMetricsMap(dateFormatter.format(startRange.time), dateFormatter.format(today.time))

            // 🚀 BACKGROUND PRE-CALCULATION
            val monthDataList = mutableListOf<MonthData>()
            val oldestSyncedDay = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -(HealthDataManager.SYNC_HISTORY_DAYS - 1))
            }
            val calendar = oldestSyncedDay.clone() as Calendar
            calendar.set(Calendar.DAY_OF_MONTH, 1)

            val monthFormatter = SimpleDateFormat("MMMM", Locale.US)
            val yearFormatter = SimpleDateFormat("yyyy", Locale.US)
            val rangeFormatter = SimpleDateFormat("dd MMM", Locale.US)

            while (!calendar.after(today)) {
                val monthName = monthFormatter.format(calendar.time)
                val year = yearFormatter.format(calendar.time)
                val currentMonth = calendar.get(Calendar.MONTH)
                val isCurrentMonth = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) && currentMonth == today.get(Calendar.MONTH)

                val barItems = mutableListOf<BarItem>()
                var weekIndex = 1

                if (isCurrentMonth) {
                    HistoryDateOrder.monthWeeksForMonthlyView(calendar, today).forEach { week ->
                        var weekSum = 0.0
                        var count = 0

                        if (week.isComplete) {
                            week.dates.forEach { day ->
                                val dateKey = dateFormatter.format(day.time)
                                if (!day.before(oldestSyncedDay) && !day.after(today)) {
                                    val w = metricsMap[dateKey]?.weightKg ?: healthDataManager.getWeight(dateKey)
                                    if (w > 0) {
                                        weekSum += w
                                        count++
                                    }
                                }
                            }
                        }

                        val avgWeight = if (count > 0) weekSum / count else 0.0
                        val height = (avgWeight * 250 / 150.0).toInt().coerceIn(if (avgWeight > 0) 2 else 0, 250)

                        barItems.add(BarItem(BarData(
                            label = "Week $weekIndex",
                            date = rangeFormatter.format(week.start.time),
                            valueDisplay = if (avgWeight > 0) "%.0f kg".format(avgWeight) else "-",
                            heightPx = dpToPx(height),
                            color = ContextCompat.getColor(this@WeightActivity, R.color.graphWeight),
                            backgroundRes = R.drawable.bg_weight_bar,
                            isEmpty = avgWeight <= 0
                        )))
                        weekIndex++
                    }

                    calendar.add(Calendar.MONTH, 1)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    if (barItems.isNotEmpty()) {
                        monthDataList.add(MonthData(monthName, year, barItems, weightGoalLines(idealWeight)))
                    }
                    continue
                }

                while (calendar.get(Calendar.MONTH) == currentMonth) {
                    var weekSum = 0.0
                    var count = 0
                    val weekStart = calendar.time

                    var daysInThisWeek = 0
                    while (daysInThisWeek < 7 && calendar.get(Calendar.MONTH) == currentMonth) {
                        val dateKey = dateFormatter.format(calendar.time)
                        if (!calendar.before(oldestSyncedDay) && !calendar.after(today)) {
                            val w = metricsMap[dateKey]?.weightKg ?: healthDataManager.getWeight(dateKey)
                            if (w > 0) {
                                weekSum += w
                                count++
                            }
                        }
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                        daysInThisWeek++
                        if (calendar.after(today)) {
                            break
                        }
                    }

                    val avgWeight = if (count > 0) weekSum / count else 0.0
                    val height = (avgWeight * 250 / 150.0).toInt().coerceIn(2, 250)

                    barItems.add(BarItem(BarData(
                        label = "Week $weekIndex",
                        date = rangeFormatter.format(weekStart),
                        valueDisplay = if (avgWeight > 0) "%.0f kg".format(avgWeight) else "-",
                        heightPx = dpToPx(height),
                        color = ContextCompat.getColor(this@WeightActivity, R.color.graphWeight),
                        backgroundRes = R.drawable.bg_weight_bar,
                        isEmpty = avgWeight <= 0
                    )))
                    weekIndex++
                }
                if (barItems.isNotEmpty()) {
                    monthDataList.add(MonthData(monthName, year, barItems, weightGoalLines(idealWeight)))
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

    private fun weightGoalLines(idealWeight: Double?): List<GoalLineSpec> {
        if (idealWeight == null || idealWeight <= 0.0) return emptyList()
        return listOf(
            GoalLineSpec(
                value = idealWeight,
                maxValue = 150.0,
                label = "Ideal Weight: %.1f kg".format(Locale.US, idealWeight),
                color = ContextCompat.getColor(this, R.color.graphWeight)
            )
        )
    }
}
