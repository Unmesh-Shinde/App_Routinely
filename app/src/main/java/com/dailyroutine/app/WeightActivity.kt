package com.dailyroutine.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*

class WeightActivity : AppCompatActivity() {

    private lateinit var healthDataManager: HealthDataManager
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weight)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        healthDataManager = HealthDataManager(this)

        findViewById<Button>(R.id.btnLogWeight).setOnClickListener { showWeightDialog() }

        setupTabs()
        refreshTodayView()
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
        val todayStr = dateFormatter.format(Date())
        val weight = healthDataManager.getWeight(todayStr)
        findViewById<TextView>(R.id.tvCurrentWeight).text = if (weight > 0) "%.1f kg".format(weight) else "No weight data added"
    }

    private fun refreshWeeklyView() {
        val container = findViewById<LinearLayout>(R.id.llWeeklyWeightGraph)
        container.removeAllViews()
        
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.add(Calendar.WEEK_OF_YEAR, -3)

        val dateBarFormatter = SimpleDateFormat("dd MMM", Locale.US)

        for (w in 0 until 4) {
            for (i in 0 until 7) {
                val dateStr = dateFormatter.format(calendar.time)
                val weight = healthDataManager.getWeight(dateStr)
                
                val barView = LayoutInflater.from(this).inflate(R.layout.item_calorie_bar, container, false)
                barView.findViewById<TextView>(R.id.tvBarLabel).text = days[i]
                barView.findViewById<TextView>(R.id.tvBarDate).text = dateBarFormatter.format(calendar.time)
                barView.findViewById<TextView>(R.id.tvBarValue).text = if (weight > 0) "%.0f".format(weight) else "-"
                
                val bar = barView.findViewById<View>(R.id.viewBar)
                bar.setBackgroundColor(0xFF78909C.toInt()) // Weight color
                val params = bar.layoutParams as LinearLayout.LayoutParams
                // Max height is 250dp for 150kg
                params.height = if (weight > 0) (weight * 250 / 150.0).toInt().let { dpToPx(it) } else 2
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
        val container = findViewById<LinearLayout>(R.id.llMonthlyWeightGraph)
        container.removeAllViews()
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.MONTH, -5)

        val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.US)
        val rangeFormatter = SimpleDateFormat("dd MMM", Locale.US)

        for (m in 0 until 6) {
            val monthLabel = monthFormatter.format(calendar.time)
            
            val monthText = TextView(this).apply {
                text = monthLabel
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(8))
                setTextColor(0xFF78909C.toInt())
            }
            container.addView(monthText)

            val currentMonth = calendar.get(Calendar.MONTH)
            var weekIndex = 1

            while (calendar.get(Calendar.MONTH) == currentMonth) {
                var weekSum = 0.0
                var count = 0
                val weekStart = calendar.time
                
                var daysInThisWeek = 0
                while (daysInThisWeek < 7 && calendar.get(Calendar.MONTH) == currentMonth) {
                    val w = healthDataManager.getWeight(dateFormatter.format(calendar.time))
                    if (w > 0) {
                        weekSum += w
                        count++
                    }
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    daysInThisWeek++
                }
                
                val avgWeight = if (count > 0) weekSum / count else 0.0

                val barView = LayoutInflater.from(this).inflate(R.layout.item_calorie_bar, container, false)
                barView.findViewById<TextView>(R.id.tvBarLabel).text = "Week $weekIndex"
                barView.findViewById<TextView>(R.id.tvBarDate).text = "${rangeFormatter.format(weekStart)}"
                barView.findViewById<TextView>(R.id.tvBarValue).text = if (avgWeight > 0) "%.0f".format(avgWeight) else "-"
                
                val bar = barView.findViewById<View>(R.id.viewBar)
                bar.setBackgroundColor(0xFF78909C.toInt())
                val params = bar.layoutParams as LinearLayout.LayoutParams
                params.height = if (avgWeight > 0) (avgWeight * 250 / 150.0).toInt().let { dpToPx(it) } else 2
                bar.layoutParams = params
                
                container.addView(barView)
                weekIndex++
            }
            
            val divider = View(this).apply { 
                layoutParams = LinearLayout.LayoutParams(dpToPx(4), dpToPx(200)).apply {
                    setMargins(dpToPx(24), 0, dpToPx(24), dpToPx(40))
                }
                setBackgroundColor(0xFF263238.toInt()) 
            }
            container.addView(divider)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
