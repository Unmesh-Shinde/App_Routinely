package com.dailyroutine.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SleepTrackingActivity : AppCompatActivity() {

    private lateinit var healthDataManager: HealthDataManager
    private lateinit var weeklyAdapter: WeeklyGraphAdapter
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_tracking)
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomPadding(findViewById(R.id.sleepContentContainer))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
        toolbar.setNavigationOnClickListener { finish() }

        healthDataManager = HealthDataManager(this)
        isConnected = healthDataManager.isConnected()

        if (!isConnected) {
            setupOnboarding()
        } else {
            showAnalytics()
        }
    }

    private fun setupOnboarding() {
        findViewById<View>(R.id.llConnectApp).visibility = View.VISIBLE
        findViewById<View>(R.id.llSleepAnalytics).visibility = View.GONE
        findViewById<View>(R.id.tabLayoutSleep).visibility = View.GONE

        val container = findViewById<LinearLayout>(R.id.llAppList)
        val tvStatus = findViewById<TextView>(R.id.tvFoundApps)

        val apps = HealthAppScanner.getInstalledFitnessApps(this)
        
        if (apps.isEmpty()) {
            tvStatus.text = "No health apps detected. Please install Google Fit, Samsung Health, or a similar app to sync data."
        } else {
            tvStatus.text = "Select your preferred health source to sync data:"
            apps.forEach { app ->
                val btn = Button(this, null, android.R.attr.buttonStyleSmall).apply {
                    text = "Connect ${app.name}"
                    setOnClickListener { startSync(app.name) }
                }
                container.addView(btn)
            }
        }
    }

    private fun startSync(appName: String) {
        findViewById<LinearLayout>(R.id.llAppList).visibility = View.GONE
        findViewById<ProgressBar>(R.id.pbSyncing).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvSyncing).apply {
            visibility = View.VISIBLE
            text = "Syncing with $appName..."
        }

        healthDataManager.setConnectedAppName(appName)
        healthDataManager.setConnected(true)

        // Simulate a brief connection wait then show analytics
        Handler(Looper.getMainLooper()).postDelayed({
            isConnected = true
            showAnalytics()
            Toast.makeText(this, "Successfully connected to $appName! 🌙", Toast.LENGTH_SHORT).show()
        }, 1500)
    }

    private fun showAnalytics() {
        findViewById<View>(R.id.llConnectApp).visibility = View.GONE
        findViewById<View>(R.id.llSleepAnalytics).visibility = View.VISIBLE
        findViewById<View>(R.id.tabLayoutSleep).visibility = View.VISIBLE

        setupTabs()
        setupWeeklyGraph()
        refreshWeeklyView()
    }

    private fun setupWeeklyGraph() {
        val rv = findViewById<RecyclerView>(R.id.rvWeeklySleepGraph)
        rv.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        weeklyAdapter = WeeklyGraphAdapter()
        rv.adapter = weeklyAdapter
    }

    private fun setupTabs() {
        val tabLayout = findViewById<TabLayout>(R.id.tabLayoutSleep)
        val vWeekly = findViewById<View>(R.id.viewWeeklySleep)
        val vMonthly = findViewById<View>(R.id.viewMonthlySleep)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                vWeekly.visibility = if (tab?.position == 0) View.VISIBLE else View.GONE
                vMonthly.visibility = if (tab?.position == 1) View.VISIBLE else View.GONE
                
                when (tab?.position) {
                    0 -> refreshWeeklyView()
                    1 -> refreshMonthlyView()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun refreshWeeklyView() {
        lifecycleScope.launch {
            val today = Calendar.getInstance()
            val startRange = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(HealthDataManager.SYNC_HISTORY_DAYS - 1)) }
            val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val metricsMap = healthDataManager.getMetricsMap(dateFormatter.format(startRange.time), dateFormatter.format(today.time))

            withContext(Dispatchers.Main) {
                val sleepBarColor = ContextCompat.getColor(this@SleepTrackingActivity, R.color.graphSleep)
                val sleepGoalLineColor = ContextCompat.getColor(this@SleepTrackingActivity, R.color.graphSleepGoalLine)
                val goalOverlay = findViewById<GoalLineOverlayView>(R.id.weeklySleepGoalOverlay)
                goalOverlay.visibility = View.VISIBLE
                goalOverlay.setGoalLines(
                    listOf(
                        GoalLineSpec(7.0, 12.0, "7h", sleepGoalLineColor),
                        GoalLineSpec(8.0, 12.0, "Ideal Sleep: 8h", sleepGoalLineColor)
                    )
                )

                val weekGroups = HistoryDateOrder.monthBoundedWeeklyGroups(HealthDataManager.SYNC_HISTORY_DAYS)

                val dayLabelFormatter = SimpleDateFormat("EEE", Locale.US)
                val dateBarFormatter = SimpleDateFormat("dd MMM", Locale.US)

                val graphItems = mutableListOf<WeeklyGraphItem>()

                weekGroups.forEachIndexed { weekIndex, weekDates ->
                    weekDates.forEach { calendar ->
                        val dateKey = dateFormatter.format(calendar.time)
                        val sleepHours = metricsMap[dateKey]?.sleepHours ?: healthDataManager.getHistoricalSleep(dateKey)
                        val hasSignal = sleepHours > 0

                        val hPx = (sleepHours * 200.0 / 12.0).let { dpToPx(it.toInt()) }.coerceAtLeast(2)

                        graphItems.add(WeeklyGraphItem(
                            dayLabel = dayLabelFormatter.format(calendar.time),
                            dateLabel = dateBarFormatter.format(calendar.time),
                            primaryValue = "%.1fh".format(sleepHours),
                            primaryHeightPx = hPx,
                            primaryColor = sleepBarColor,
                            primaryBackgroundRes = R.drawable.bg_sleep_bar,
                            hasSignal = hasSignal
                        ))
                    }

                    if (weekIndex != weekGroups.lastIndex) {
                        graphItems.add(WeeklyGraphItem(
                            dayLabel = "", dateLabel = "", primaryValue = "", primaryHeightPx = 0, primaryColor = 0, isDivider = true
                        ))
                    }
                }

                weeklyAdapter.submitList(graphItems)
            }
        }
    }

    private fun refreshMonthlyView() {
        lifecycleScope.launch {
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
            val deficitWeeks = mutableListOf<String>()
            val sleepBarColor = ContextCompat.getColor(this@SleepTrackingActivity, R.color.graphSleep)

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

                        if (week.isComplete) {
                            week.dates.forEach { day ->
                                val dateKey = dateFormatter.format(day.time)
                                if (!day.before(oldestSyncedDay) && !day.after(today)) {
                                    weekSum += metricsMap[dateKey]?.sleepHours ?: healthDataManager.getHistoricalSleep(dateKey)
                                }
                            }
                        }

                        if (week.isComplete && weekSum < 56.0 && weekSum > 0) {
                            deficitWeeks.add("${rangeFormatter.format(week.start.time)} - ${rangeFormatter.format(week.end.time)}")
                        }

                        val height = (weekSum * 250 / 70.0).toInt().coerceIn(if (weekSum > 0) 2 else 0, 250)
                        barItems.add(BarItem(BarData(
                            label = "Week $weekIndex",
                            date = rangeFormatter.format(week.start.time),
                            valueDisplay = if (weekSum > 0) "%.0fh".format(weekSum) else "0h",
                            heightPx = dpToPx(height),
                            color = sleepBarColor,
                            backgroundRes = R.drawable.bg_sleep_bar,
                            isEmpty = weekSum <= 0
                        )))
                        weekIndex++
                    }

                    calendar.add(Calendar.MONTH, 1)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    if (barItems.isNotEmpty()) {
                        monthDataList.add(MonthData(monthName, year, barItems, monthlySleepGoalLines()))
                    }
                    continue
                }

                while (calendar.get(Calendar.MONTH) == currentMonth) {
                    var weekSum = 0.0
                    val weekStart = calendar.time

                    var isWeekOver = false
                    while (!isWeekOver) {
                        val dateKey = dateFormatter.format(calendar.time)
                        if (!calendar.before(oldestSyncedDay) && !calendar.after(today)) {
                            weekSum += metricsMap[dateKey]?.sleepHours ?: healthDataManager.getHistoricalSleep(dateKey)
                        }

                        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                        val isSunday = (currentDayOfWeek == Calendar.SUNDAY)

                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                        val isNewMonth = (calendar.get(Calendar.MONTH) != currentMonth)
                        val isAfterToday = calendar.after(today)

                        if (isSunday || isNewMonth || isAfterToday) {
                            isWeekOver = true
                        }
                    }

                    if (weekSum < 56.0 && weekSum > 0) {
                        val endCal = calendar.clone() as Calendar
                        endCal.add(Calendar.DAY_OF_YEAR, -1)
                        deficitWeeks.add("${rangeFormatter.format(weekStart)} - ${rangeFormatter.format(endCal.time)}")
                    }

                    val height = (weekSum * 250 / 70.0).toInt().coerceIn(if (weekSum > 0) 2 else 0, 250)

                    barItems.add(BarItem(BarData(
                        label = "Week $weekIndex",
                        date = rangeFormatter.format(weekStart),
                        valueDisplay = if (weekSum > 0) "%.0fh".format(weekSum) else "0h",
                        heightPx = dpToPx(height),
                        color = sleepBarColor,
                        backgroundRes = R.drawable.bg_sleep_bar,
                        isEmpty = weekSum <= 0
                    )))
                    weekIndex++
                }
                if (barItems.isNotEmpty()) {
                    monthDataList.add(MonthData(monthName, year, barItems, monthlySleepGoalLines()))
                }
            }

            withContext(Dispatchers.Main) {
                val rv = findViewById<RecyclerView>(R.id.rvMonthlySleep)
                rv.adapter = MonthGraphAdapter(monthDataList.asReversed())
                rv.onFlingListener = null
                androidx.recyclerview.widget.PagerSnapHelper().attachToRecyclerView(rv)

                val warning = findViewById<TextView>(R.id.tvMonthlySleepWarning)
                if (deficitWeeks.isNotEmpty()) {
                    warning.visibility = View.VISIBLE
                    warning.text = "⚠️ Target sleep (56h) not met in: ${deficitWeeks.asReversed().take(3).joinToString(", ")}..."
                } else {
                    warning.visibility = View.GONE
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun monthlySleepGoalLines(): List<GoalLineSpec> {
        val sleepGoalLineColor = ContextCompat.getColor(this, R.color.graphSleepGoalLine)
        return listOf(
            GoalLineSpec(49.0, 70.0, "49h", sleepGoalLineColor),
            GoalLineSpec(56.0, 70.0, "Ideal Sleep: 56h", sleepGoalLineColor)
        )
    }
}
