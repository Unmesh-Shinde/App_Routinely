package com.dailyroutine.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*

class CaloriesActivity : AppCompatActivity() {

    private lateinit var healthDataManager: HealthDataManager
    private lateinit var planManager: PlanManager
    private lateinit var adapter: CalorieMealAdapter
    private var dailyGoal: Int = 2000
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calories)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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
        
        refreshTodayView()
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

    private fun refreshTodayView() {
        val todayStr = dateFormatter.format(Date())
        val meals = planManager.getMealsForDate(todayStr)
        
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
                
                val burnedTotal = WellnessEngine.calculateActiveBurn(this, steps, weight).toInt()
                val netBalance = intakeTotal - burnedTotal

                findViewById<TextView>(R.id.tvIntakeSum).text = intakeTotal.toString()
                findViewById<TextView>(R.id.tvBurnedSum).text = burnedTotal.toString()
                findViewById<TextView>(R.id.tvNetBalance).text = netBalance.toString()
                
                findViewById<TextView>(R.id.tvDailyWarning).visibility = if (netBalance > dailyGoal) View.VISIBLE else View.GONE
            }
        }
    }

    private fun refreshWeeklyView() {
        val container = findViewById<LinearLayout>(R.id.llWeeklyGraph)
        container.removeAllViews()
        
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        
        calendar.add(Calendar.WEEK_OF_YEAR, -7)

        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        var currentWeekTotal = 0.0
        val now = Calendar.getInstance()

        for (w in 0 until 8) {
            val processedWeekDateStrs = mutableListOf<String>()
            for (d in 0 until 7) {
                processedWeekDateStrs.add(dateFormatter.format(calendar.time))
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            val weekEnd = calendar.time
            var weekSum = 0.0
            var weekProcessedCount = 0
            val weekMealsByDay = mutableMapOf<String, Int>()

            processedWeekDateStrs.forEach { dateStr ->
                WellnessEngine.calculateIntakeForDate(this, dateStr) { dailyTotal: Int ->
                    weekMealsByDay[dateStr] = dailyTotal
                    weekSum += dailyTotal
                    weekProcessedCount++

                    if (weekProcessedCount == 7) {
                        runOnUiThread {
                            val thisWeekStart = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
                            if (processedWeekDateStrs.contains(dateFormatter.format(thisWeekStart.time))) {
                                currentWeekTotal = weekSum
                                findViewById<View>(R.id.tvWeeklyWarning).visibility = if (currentWeekTotal > dailyGoal * 7) View.VISIBLE else View.GONE
                            }

                            processedWeekDateStrs.forEachIndexed { idx, dStr ->
                                val dayVal = weekMealsByDay[dStr] ?: 0
                                val barView = LayoutInflater.from(this@CaloriesActivity).inflate(R.layout.item_calorie_bar, container, false)
                                barView.findViewById<TextView>(R.id.tvBarLabel).text = days[idx]
                                barView.findViewById<TextView>(R.id.tvBarDate).text = dStr.split("-").last()
                                barView.findViewById<TextView>(R.id.tvBarValue).text = if (dayVal > 0) dayVal.toString() else "-"
                                
                                val bar = barView.findViewById<View>(R.id.viewBar)
                                bar.setBackgroundResource(R.drawable.bg_calorie_bar)
                                val params = bar.layoutParams as LinearLayout.LayoutParams
                                params.height = (dayVal * 250 / 3500).coerceIn(2, 250).let { dpToPx(it) }
                                bar.layoutParams = params
                                
                                container.addView(barView)
                            }
                            
                            val divider = View(this@CaloriesActivity).apply { 
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
            calendar.time = weekEnd
        }
    }

    private fun refreshMonthlyView() {
        val rv = findViewById<RecyclerView>(R.id.rvMonthlyCalories)
        val monthDataList = mutableListOf<MonthData>()
        
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.add(Calendar.MONTH, -5)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        
        val monthFormatter = SimpleDateFormat("MMMM", Locale.US)
        val yearFormatter = SimpleDateFormat("yyyy", Locale.US)
        val rangeFormatter = SimpleDateFormat("dd MMM", Locale.US)

        for (m in 0 until 6) {
            val monthName = monthFormatter.format(calendar.time)
            val yearName = yearFormatter.format(calendar.time)
            val currentMonth = calendar.get(Calendar.MONTH)
            
            val barItems = mutableListOf<BarItem>()
            var weekIndex = 1
            
            while (calendar.get(Calendar.MONTH) == currentMonth) {
                val weekStart = calendar.time
                val weekDates = mutableListOf<String>()
                
                var isWeekOver = false
                while (!isWeekOver) {
                    weekDates.add(dateFormatter.format(calendar.time))
                    val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    val isSunday = (currentDayOfWeek == Calendar.SUNDAY)
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    val isNewMonth = (calendar.get(Calendar.MONTH) != currentMonth)
                    if (isSunday || isNewMonth) isWeekOver = true
                }

                // Using historical data for Monthly view for speed (it matches sync)
                var weekSum = 0.0
                weekDates.forEach { weekSum += healthDataManager.getHistoricalCalories(it) }

                val height = (weekSum * 250 / (3500 * 7)).toInt().coerceIn(2, 250)
                barItems.add(BarItem(BarData(
                    label = "Week $weekIndex",
                    date = rangeFormatter.format(weekStart),
                    valueDisplay = if (weekSum > 0) "%.0f".format(weekSum) else "-",
                    heightPx = dpToPx(height),
                    color = 0xFFEF5350.toInt(),
                    backgroundRes = R.drawable.bg_calorie_bar
                )))
                weekIndex++
            }
            monthDataList.add(MonthData(monthName, yearName, barItems))
        }

        rv.adapter = MonthGraphAdapter(monthDataList)
        rv.onFlingListener = null
        androidx.recyclerview.widget.PagerSnapHelper().attachToRecyclerView(rv)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    inner class CalorieMealAdapter : RecyclerView.Adapter<CalorieMealAdapter.VH>() {
        private var items = listOf<Meal>()

        fun submitList(list: List<Meal>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_meal, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = items[position]
            CalorieSearchEngine.getCalories(holder.itemView.context, "${m.name} ${m.description}") { cals ->
                holder.itemView.post {
                    holder.tvTime.text = "$cals kcal"
                }
            }
            
            holder.tvEmoji.text = when (m.mealType) {
                "Breakfast" -> "🍳"
                "Lunch" -> "🥗"
                "Dinner" -> "🍲"
                else -> "🍎"
            }
            holder.tvTitle.text = m.name
            holder.tvSubtitle.text = "${m.mealType} • AI Sync"
            
            holder.itemView.findViewById<View>(R.id.switchEnabled).visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.btnEdit).visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.btnDelete).visibility = View.GONE
        }

        override fun getItemCount() = items.size

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvEmoji: TextView = v.findViewById(R.id.tvEmoji)
            val tvTitle: TextView = v.findViewById(R.id.tvTitle)
            val tvSubtitle: TextView = v.findViewById(R.id.tvSubtitle)
            val tvTime: TextView = v.findViewById(R.id.tvTime)
        }
    }
}
