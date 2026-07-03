package com.dailyroutine.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*

class WellnessScoreActivity : AppCompatActivity() {

    private lateinit var sliderSleep: Slider
    private lateinit var sliderWorkout: Slider
    private lateinit var sliderNutrition: Slider
    private lateinit var sliderSteps: Slider

    private lateinit var tvSleepWeight: TextView
    private lateinit var tvWorkoutWeight: TextView
    private lateinit var tvNutritionWeight: TextView
    private lateinit var tvStepsWeight: TextView
    private lateinit var tvTotalWeight: TextView

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wellness_score)

        InsetHelper.applyTopPadding(findViewById(R.id.appBar))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        initViews()
        loadSavedWeights()
        setupTabs()
        setupListeners()
    }

    private fun initViews() {
        sliderSleep = findViewById(R.id.sliderSleep)
        sliderWorkout = findViewById(R.id.sliderWorkout)
        sliderNutrition = findViewById(R.id.sliderNutrition)
        sliderSteps = findViewById(R.id.sliderSteps)

        tvSleepWeight = findViewById(R.id.tvSleepWeight)
        tvWorkoutWeight = findViewById(R.id.tvWorkoutWeight)
        tvNutritionWeight = findViewById(R.id.tvNutritionWeight)
        tvStepsWeight = findViewById(R.id.tvStepsWeight)
        tvTotalWeight = findViewById(R.id.tvTotalWeight)

        findViewById<MaterialButton>(R.id.btnSaveWeights).setOnClickListener {
            saveWeights()
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
                    1 -> refreshWeeklyView()
                    2 -> refreshMonthlyView()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadSavedWeights() {
        val wSleep = UserPreferencesStore.getSleepWeight(this)
        val wWorkout = UserPreferencesStore.getWorkoutWeight(this)
        val wNutrition = UserPreferencesStore.getNutritionWeight(this)
        val wSteps = UserPreferencesStore.getStepsWeight(this)

        sliderSleep.value = wSleep.toFloat()
        sliderWorkout.value = wWorkout.toFloat()
        sliderNutrition.value = wNutrition.toFloat()
        sliderSteps.value = wSteps.toFloat()

        updateLabels()
    }

    private fun setupListeners() {
        val listener = Slider.OnChangeListener { _, _, _ ->
            updateLabels()
        }
        sliderSleep.addOnChangeListener(listener)
        sliderWorkout.addOnChangeListener(listener)
        sliderNutrition.addOnChangeListener(listener)
        sliderSteps.addOnChangeListener(listener)
    }

    private fun updateLabels() {
        val s = sliderSleep.value.toInt()
        val w = sliderWorkout.value.toInt()
        val n = sliderNutrition.value.toInt()
        val st = sliderSteps.value.toInt()

        tvSleepWeight.text = "$s%"
        tvWorkoutWeight.text = "$w%"
        tvNutritionWeight.text = "$n%"
        tvStepsWeight.text = "$st%"

        val total = s + w + n + st
        tvTotalWeight.text = "Total: $total%"

        if (total != 100) {
            tvTotalWeight.setTextColor(android.graphics.Color.RED)
        } else {
            tvTotalWeight.setTextColor(getColor(R.color.textPrimary))
        }
    }

    private fun saveWeights() {
        val s = sliderSleep.value.toInt()
        val w = sliderWorkout.value.toInt()
        val n = sliderNutrition.value.toInt()
        val st = sliderSteps.value.toInt()

        if (s + w + n + st != 100) {
            Toast.makeText(this, "Total weight must be exactly 100%", Toast.LENGTH_SHORT).show()
            return
        }

        UserPreferencesStore.setSleepWeight(this, s)
        UserPreferencesStore.setWorkoutWeight(this, w)
        UserPreferencesStore.setNutritionWeight(this, n)
        UserPreferencesStore.setStepsWeight(this, st)

        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
    }

    private fun getScoreForDate(dateStr: String): Int {
        return WellnessScoreManager.getSavedDailyScore(this, dateStr) ?: -1
    }

    private fun refreshWeeklyView() {
        val container = findViewById<LinearLayout>(R.id.llWeeklyGraph)
        container.removeAllViews()

        val weekGroups = HistoryDateOrder.monthBoundedWeeklyGroups(HealthDataManager.SYNC_HISTORY_DAYS)

        val dayLabelFormatter = SimpleDateFormat("EEE", Locale.US)
        val dateBarFormatter = SimpleDateFormat("dd MMM", Locale.US)

        weekGroups.forEachIndexed { weekIndex, weekDates ->
            weekDates.forEach { calendar ->
                val dateStr = dateFormatter.format(calendar.time)
                val score = getScoreForDate(dateStr)

                val barView = LayoutInflater.from(this).inflate(R.layout.item_calorie_bar, container, false)
                barView.findViewById<TextView>(R.id.tvBarLabel).text = dayLabelFormatter.format(calendar.time)
                barView.findViewById<TextView>(R.id.tvBarDate).text = dateBarFormatter.format(calendar.time)
                barView.findViewById<TextView>(R.id.tvBarValue).text = if (score >= 0) score.toString() else "-"

                val bar = barView.findViewById<View>(R.id.viewBar)
                bar.setBackgroundResource(R.drawable.bg_calorie_bar)
                val params = bar.layoutParams as LinearLayout.LayoutParams
                val displayHeight = if (score <= 0) 0 else (score * 250 / 100).coerceAtMost(250)
                params.height = dpToPx(displayHeight)
                bar.layoutParams = params

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

    private fun refreshMonthlyView() {
        val rv = findViewById<RecyclerView>(R.id.rvMonthlyScore)
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

        while (!calendar.after(today)) {
            val monthName = monthFormatter.format(calendar.time)
            val yearName = yearFormatter.format(calendar.time)
            val currentMonth = calendar.get(Calendar.MONTH)
            val isCurrentMonth = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) && currentMonth == today.get(Calendar.MONTH)

            val barItems = mutableListOf<BarItem>()
            var weekIndex = 1

            if (isCurrentMonth) {
                HistoryDateOrder.monthWeeksForMonthlyView(calendar, today).forEach { week ->
                    val scores = if (week.isComplete) {
                        week.dates
                            .filter { day -> !day.before(oldestSyncedDay) && !day.after(today) }
                            .map { day -> getScoreForDate(dateFormatter.format(day.time)) }
                            .filter { it >= 0 }
                    } else {
                        emptyList()
                    }

                    val weekAvg = if (scores.isNotEmpty()) scores.average().toInt() else -1
                    val height = if (weekAvg <= 0) 0 else (weekAvg * 250 / 100).coerceAtMost(250)
                    barItems.add(BarItem(BarData(
                        label = "Week $weekIndex",
                        date = rangeFormatter.format(week.start.time),
                        valueDisplay = if (weekAvg >= 0) weekAvg.toString() else "-",
                        heightPx = dpToPx(height),
                        color = 0xFF009688.toInt(),
                        backgroundRes = R.drawable.bg_calorie_bar
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

                // Filter out days with no real data (-1) before averaging
                val scores = weekDates.map { getScoreForDate(it) }.filter { it >= 0 }
                val weekAvg = if (scores.isNotEmpty()) scores.average().toInt() else -1

                // No minimal height for weekAvg 0 or -1. Max height 250dp for score 100.
                val height = if (weekAvg <= 0) 0 else (weekAvg * 250 / 100).coerceAtMost(250)
                barItems.add(BarItem(BarData(
                    label = "Week $weekIndex",
                    date = rangeFormatter.format(weekStart),
                    valueDisplay = if (weekAvg >= 0) weekAvg.toString() else "-",
                    heightPx = dpToPx(height),
                    color = 0xFF009688.toInt(), // healthSync color
                    backgroundRes = R.drawable.bg_calorie_bar
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
