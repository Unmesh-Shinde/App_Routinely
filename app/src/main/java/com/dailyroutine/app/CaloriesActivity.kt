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
        val display = "$dailyGoal kcal"
        findViewById<TextView>(R.id.tvCalorieGoal).text = display
        
        // Show if it matches BMR recommendation (simulated)
        val weight = healthDataManager.getWeight(dateFormatter.format(Date())).let { if (it > 0) it else 70.0 }
        val age = UserPreferencesStore.getUserAge(this)
        val height = UserPreferencesStore.getUserHeight(this)
        val gender = UserPreferencesStore.getUserGender(this)
        
        var bmr = if (gender == "Male") {
            ((10 * weight) + (6.25 * height) - (5 * age) + 5).toInt()
        } else {
            ((10 * weight) + (6.25 * height) - (5 * age) - 161).toInt()
        }
        bmr = (bmr * 1.2).toInt() // Light activity multiplier
        
        if (dailyGoal != bmr) {
            // We could show a small hint here if needed
        }
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
        val doneIds = RoutineProgressStore.getDoneIds(this)
        
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
        
        val intakeTotal = meals.sumOf { CalorieSearchEngine.getCalories("${it.name} ${it.description}") }
        findViewById<View>(R.id.tvDailyWarning).visibility = if (intakeTotal > dailyGoal) View.VISIBLE else View.GONE

        // Energy Balance Calculation
        val stepsStr = healthDataManager.getSteps().replace(",", "")
        val steps = stepsStr.toIntOrNull() ?: 0
        val weight = healthDataManager.getWeight(todayStr).let { if (it > 0) it else 70.0 }
        
        val doneExercises = planManager.getExercisesForDate(todayStr).filter { it.id.toString() in doneIds }
        val burnedTotal = CalorieSearchEngine.calculateActiveBurn(steps, weight, doneExercises)
        val netBalance = intakeTotal - burnedTotal

        findViewById<TextView>(R.id.tvIntakeSum).text = intakeTotal.toString()
        findViewById<TextView>(R.id.tvBurnedSum).text = burnedTotal.toString()
        findViewById<TextView>(R.id.tvNetBalance).text = netBalance.toString()
        
        findViewById<TextView>(R.id.tvDailyWarning).visibility = if (netBalance > dailyGoal) View.VISIBLE else View.GONE
    }

    private fun refreshWeeklyView() {
        val container = findViewById<LinearLayout>(R.id.llWeeklyGraph)
        container.removeAllViews()
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.add(Calendar.WEEK_OF_YEAR, -3)

        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val dateBarFormatter = SimpleDateFormat("dd MMM", Locale.US)
        var currentWeekTotal = 0
        val now = Calendar.getInstance()

        for (w in 0 until 4) {
            var weekSum = 0
            for (d in 0 until 7) {
                val dateStr = dateFormatter.format(calendar.time)
                val meals = planManager.getMealsForDate(dateStr)
                val dailyTotal = meals.sumOf { CalorieSearchEngine.getCalories("${it.name} ${it.description}") }
                weekSum += dailyTotal
                
                val barView = LayoutInflater.from(this).inflate(R.layout.item_calorie_bar, container, false)
                barView.findViewById<TextView>(R.id.tvBarLabel).text = days[d]
                barView.findViewById<TextView>(R.id.tvBarDate).text = dateBarFormatter.format(calendar.time)
                barView.findViewById<TextView>(R.id.tvBarValue).text = dailyTotal.toString()
                
                val bar = barView.findViewById<View>(R.id.viewBar)
                val params = bar.layoutParams as LinearLayout.LayoutParams
                params.height = (dailyTotal * 250 / 3500).coerceAtMost(250).let { dpToPx(it) }
                bar.layoutParams = params
                
                container.addView(barView)
                
                if (calendar.get(Calendar.WEEK_OF_YEAR) == now.get(Calendar.WEEK_OF_YEAR)) {
                    currentWeekTotal = weekSum
                }

                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            // Week separator
            val divider = View(this).apply { 
                layoutParams = LinearLayout.LayoutParams(dpToPx(3), dpToPx(180)).apply {
                    setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(40))
                }
                setBackgroundColor(0xFF455A64.toInt()) // Bold charcoal grey
            }
            container.addView(divider)
        }
        
        findViewById<View>(R.id.tvWeeklyWarning).visibility = if (currentWeekTotal > dailyGoal * 7) View.VISIBLE else View.GONE
    }

    private fun refreshMonthlyView() {
        val container = findViewById<LinearLayout>(R.id.llMonthlyGraph)
        container.removeAllViews()
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.MONTH, -5)
        
        val exceededWeekStrings = mutableListOf<String>()
        val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.US)
        val rangeFormatter = SimpleDateFormat("dd MMM", Locale.US)

        for (m in 0 until 6) {
            val monthLabel = monthFormatter.format(calendar.time)
            
            // Month Header
            val monthText = TextView(this).apply {
                text = monthLabel
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(8))
                setTextColor(0xFFE57373.toInt())
            }
            container.addView(monthText)

            val currentMonth = calendar.get(Calendar.MONTH)
            var weekIndex = 1
            
            // Iterate through the month in 7-day chunks
            while (calendar.get(Calendar.MONTH) == currentMonth) {
                var weekSum = 0
                val weekStart = calendar.time
                
                // Count up to 7 days, but stop if we hit a new month
                var daysInThisWeek = 0
                while (daysInThisWeek < 7 && calendar.get(Calendar.MONTH) == currentMonth) {
                    val dateStr = dateFormatter.format(calendar.time)
                    weekSum += planManager.getMealsForDate(dateStr).sumOf { 
                        CalorieSearchEngine.getCalories("${it.name} ${it.description}") 
                    }
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    daysInThisWeek++
                }

                if (weekSum > dailyGoal * 7) {
                    exceededWeekStrings.add("${rangeFormatter.format(weekStart)} - ${rangeFormatter.format(calendar.time)}")
                }

                val barView = LayoutInflater.from(this).inflate(R.layout.item_calorie_bar, container, false)
                barView.findViewById<TextView>(R.id.tvBarLabel).text = "Week $weekIndex"
                barView.findViewById<TextView>(R.id.tvBarDate).text = "${rangeFormatter.format(weekStart)}"
                barView.findViewById<TextView>(R.id.tvBarValue).text = weekSum.toString()
                
                val bar = barView.findViewById<View>(R.id.viewBar)
                val params = bar.layoutParams as LinearLayout.LayoutParams
                params.height = (weekSum * 250 / (3500 * 7)).coerceAtMost(250).let { dpToPx(it) }
                bar.layoutParams = params
                
                container.addView(barView)
                weekIndex++
            }
            
            // Bold Month Separator
            val divider = View(this).apply { 
                layoutParams = LinearLayout.LayoutParams(dpToPx(4), dpToPx(200)).apply {
                    setMargins(dpToPx(24), 0, dpToPx(24), dpToPx(40))
                }
                setBackgroundColor(0xFF263238.toInt()) 
            }
            container.addView(divider)
            
            // Calendar is already at the 1st of the next month due to the while loop
        }
        
        val warning = findViewById<TextView>(R.id.tvMonthlyWarning)
        if (exceededWeekStrings.isNotEmpty()) {
            warning.visibility = View.VISIBLE
            warning.text = "⚠️ Limit exceeded in: ${exceededWeekStrings.joinToString(", ")}"
        } else {
            warning.visibility = View.GONE
        }
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
            val cals = CalorieSearchEngine.getCalories("${m.name} ${m.description}")
            
            holder.tvEmoji.text = when (m.mealType) {
                "Breakfast" -> "🍳"
                "Lunch" -> "🥗"
                "Dinner" -> "🍲"
                else -> "🍎"
            }
            holder.tvTitle.text = m.name
            holder.tvSubtitle.text = "${m.mealType} • Calculated"
            holder.tvTime.text = "$cals kcal"
            
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
