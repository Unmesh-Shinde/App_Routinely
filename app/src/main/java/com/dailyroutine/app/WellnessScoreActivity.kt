package com.dailyroutine.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val lastValidSliderValues = mutableMapOf<Slider, Float>()
    private var isRestoringSliderValue = false
    private var overLimitMessageShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wellness_score)

        InsetHelper.applyTopPadding(findViewById(R.id.appBar))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, R.color.textPrimary))
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
        val wSleep = UserPreferencesStore.getSleepWeight(this) / 10
        val wWorkout = UserPreferencesStore.getWorkoutWeight(this) / 10
        val wNutrition = UserPreferencesStore.getNutritionWeight(this) / 10
        val wSteps = UserPreferencesStore.getStepsWeight(this) / 10

        sliderSleep.value = wSleep.toFloat().coerceIn(0f, 10f)
        sliderWorkout.value = wWorkout.toFloat().coerceIn(0f, 10f)
        sliderNutrition.value = wNutrition.toFloat().coerceIn(0f, 10f)
        sliderSteps.value = wSteps.toFloat().coerceIn(0f, 10f)

        updateLabels()
    }

    private fun setupListeners() {
        listOf(sliderSleep, sliderWorkout, sliderNutrition, sliderSteps).forEach {
            lastValidSliderValues[it] = it.value
        }

        val listener = Slider.OnChangeListener { slider, value, _ ->
            if (isRestoringSliderValue) return@OnChangeListener

            val total = sliderSleep.value + sliderWorkout.value + sliderNutrition.value + sliderSteps.value
            if (total > 10f) {
                if (!overLimitMessageShown) {
                    Toast.makeText(this, "Total weight cannot exceed 10", Toast.LENGTH_SHORT).show()
                    overLimitMessageShown = true
                }
                isRestoringSliderValue = true
                slider.value = lastValidSliderValues[slider] ?: value
                isRestoringSliderValue = false
            } else {
                overLimitMessageShown = false
                lastValidSliderValues[slider] = value
                updateLabels()
            }
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

        tvSleepWeight.text = s.toString()
        tvWorkoutWeight.text = w.toString()
        tvNutritionWeight.text = n.toString()
        tvStepsWeight.text = st.toString()

        val total = s + w + n + st
        tvTotalWeight.text = "Total: $total"

        if (total != 10) {
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

        if (s + w + n + st != 10) {
            Toast.makeText(this, "Total weight must be exactly 10", Toast.LENGTH_SHORT).show()
            return
        }

        UserPreferencesStore.setSleepWeight(this, (s * 10))
        UserPreferencesStore.setWorkoutWeight(this, (w * 10))
        UserPreferencesStore.setNutritionWeight(this, (n * 10))
        UserPreferencesStore.setStepsWeight(this, (st * 10))

        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun getScoreForDate(dateStr: String): Int {
        return WellnessScoreManager.getSavedDailyScore(this, dateStr) ?: -1
    }

    private fun refreshWeeklyView() {
        lifecycleScope.launch {
            val container = findViewById<LinearLayout>(R.id.llWeeklyGraph)
            withContext(Dispatchers.Main) {
                container.removeAllViews()
            }

            val weekGroups = HistoryDateOrder.monthBoundedWeeklyGroups(HealthDataManager.SYNC_HISTORY_DAYS)
            val dayLabelFormatter = SimpleDateFormat("EEE", Locale.US)
            val dateBarFormatter = SimpleDateFormat("dd MMM", Locale.US)

            weekGroups.forEachIndexed { weekIndex, weekDates ->
                withContext(Dispatchers.Main) {
                    weekDates.forEach { calendar ->
                        val dateStr = dateFormatter.format(calendar.time)
                        val score = getScoreForDate(dateStr)

                        val barView = LayoutInflater.from(this@WellnessScoreActivity).inflate(R.layout.item_calorie_bar, container, false)
                        barView.findViewById<TextView>(R.id.tvBarLabel).text = dayLabelFormatter.format(calendar.time)
                        barView.findViewById<TextView>(R.id.tvBarDate).text = dateBarFormatter.format(calendar.time)
                        barView.findViewById<TextView>(R.id.tvBarValue).text = if (score >= 0) score.toString() else "-"

                        val bar = barView.findViewById<View>(R.id.viewBar)
                        bar.setBackgroundResource(R.drawable.bg_wellness_bar)
                        val displayHeight = if (score <= 0) 0 else (score * 250 / 10).coerceAtMost(250)
                        bar.layoutParams.height = dpToPx(displayHeight)

                        container.addView(barView)
                    }

                    if (weekIndex != weekGroups.lastIndex) {
                        val divider = View(this@WellnessScoreActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(dpToPx(3), dpToPx(180)).apply {
                                setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(40))
                            }
                            setBackgroundColor(getColor(R.color.graphWellnessEnd))
                        }
                        container.addView(divider)
                    }
                }
            }
        }
    }

    private fun refreshMonthlyView() {
        lifecycleScope.launch {
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
                        val height = if (weekAvg <= 0) 0 else (weekAvg * 250 / 10).coerceAtMost(250)
                        barItems.add(BarItem(BarData(
                            label = "Week $weekIndex",
                            date = rangeFormatter.format(week.start.time),
                            valueDisplay = if (weekAvg >= 0) weekAvg.toString() else "-",
                            heightPx = dpToPx(height),
                            color = ContextCompat.getColor(this@WellnessScoreActivity, R.color.graphWellness),
                            backgroundRes = R.drawable.bg_wellness_bar
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

                    // No minimal height for weekAvg 0 or -1. Max height 250dp for score 10.
                    val height = if (weekAvg <= 0) 0 else (weekAvg * 250 / 10).coerceAtMost(250)
                    barItems.add(BarItem(BarData(
                        label = "Week $weekIndex",
                        date = rangeFormatter.format(weekStart),
                        valueDisplay = if (weekAvg >= 0) weekAvg.toString() else "-",
                        heightPx = dpToPx(height),
                        color = ContextCompat.getColor(this@WellnessScoreActivity, R.color.graphWellness),
                        backgroundRes = R.drawable.bg_wellness_bar
                    )))
                    weekIndex++
                }
                if (barItems.isNotEmpty()) {
                    monthDataList.add(MonthData(monthName, yearName, barItems))
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
