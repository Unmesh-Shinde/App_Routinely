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
    }

    private fun refreshWeeklyView() {
        val container = findViewById<LinearLayout>(R.id.llWeeklyStepsGraph)
        container.removeAllViews()
        
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.add(Calendar.WEEK_OF_YEAR, -3)

        val dateBarFormatter = SimpleDateFormat("dd MMM", Locale.US)

        for (w in 0 until 4) {
            for (i in 0 until 7) {
                // Remove random mock data - strictly 0 unless today is matched (simulated logic for history can be added later if needed)
                val dailySteps = 0
                
                val barView = LayoutInflater.from(this).inflate(R.layout.item_calorie_bar, container, false)
                barView.findViewById<TextView>(R.id.tvBarLabel).text = days[i]
                barView.findViewById<TextView>(R.id.tvBarDate).text = dateBarFormatter.format(calendar.time)
                barView.findViewById<TextView>(R.id.tvBarValue).text = dailySteps.toString()
                
                val bar = barView.findViewById<View>(R.id.viewBar)
                bar.setBackgroundResource(R.drawable.bg_step_bar)
                val params = bar.layoutParams as LinearLayout.LayoutParams
                params.height = (dailySteps * 250 / 15000).coerceAtMost(250).let { dpToPx(it) }
                bar.layoutParams = params
                
                container.addView(barView)
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            val divider = View(this).apply { 
                layoutParams = LinearLayout.LayoutParams(dpToPx(3), dpToPx(180)).apply {
                    setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(40))
                }
                setBackgroundColor(0xFF455A64.toInt()) 
            }
            container.addView(divider)
        }
    }

    private fun refreshMonthlyView() {
        val rv = findViewById<RecyclerView>(R.id.rvMonthlySteps)
        val monthDataList = mutableListOf<MonthData>()
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.MONTH, -5)

        val monthFormatter = SimpleDateFormat("MMMM", Locale.US)
        val yearFormatter = SimpleDateFormat("yyyy", Locale.US)
        val rangeFormatter = SimpleDateFormat("dd MMM", Locale.US)
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        for (m in 0 until 6) {
            val monthName = monthFormatter.format(calendar.time)
            val year = yearFormatter.format(calendar.time)
            val currentMonth = calendar.get(Calendar.MONTH)
            
            val barItems = mutableListOf<BarItem>()
            var weekIndex = 1

            while (calendar.get(Calendar.MONTH) == currentMonth) {
                var weekSum = 0L
                val weekStart = calendar.time
                
                var daysInThisWeek = 0
                while (daysInThisWeek < 7 && calendar.get(Calendar.MONTH) == currentMonth) {
                    val dateKey = dateFormatter.format(calendar.time)
                    weekSum += healthDataManager.getHistoricalSteps(dateKey)
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    daysInThisWeek++
                }
                
                val displayVal = if (weekSum > 1000) "%.1fk".format(weekSum/1000.0) else weekSum.toString()
                val height = (weekSum * 250 / 100000).toInt().coerceIn(2, 250)

                barItems.add(BarItem(BarData(
                    label = "Week $weekIndex",
                    date = rangeFormatter.format(weekStart),
                    valueDisplay = displayVal,
                    heightPx = dpToPx(height),
                    color = 0xFF009688.toInt(),
                    backgroundRes = R.drawable.bg_step_bar
                )))
                weekIndex++
            }
            
            monthDataList.add(MonthData(monthName, year, barItems))
        }

        rv.adapter = MonthGraphAdapter(monthDataList)
        // Add snapping
        rv.onFlingListener = null
        androidx.recyclerview.widget.PagerSnapHelper().attachToRecyclerView(rv)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
