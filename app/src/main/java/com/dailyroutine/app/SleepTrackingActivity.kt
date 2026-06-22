package com.dailyroutine.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*

class SleepTrackingActivity : AppCompatActivity() {

    private lateinit var healthDataManager: HealthDataManager
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_tracking)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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
        refreshWeeklyView()
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
        val container = findViewById<LinearLayout>(R.id.llWeeklySleepGraph)
        container.removeAllViews()
        
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        
        calendar.add(Calendar.WEEK_OF_YEAR, -3) // Show last 4 weeks

        val dateBarFormatter = SimpleDateFormat("dd MMM", Locale.US)
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        for (w in 0 until 4) {
            for (i in 0 until 7) {
                val dateKey = dateFormatter.format(calendar.time)
                val sleepHours = healthDataManager.getHistoricalSleep(dateKey)

                val barView = android.view.LayoutInflater.from(this).inflate(R.layout.item_calorie_bar, container, false)
                barView.findViewById<TextView>(R.id.tvBarLabel).text = days[i]
                barView.findViewById<TextView>(R.id.tvBarDate).text = dateBarFormatter.format(calendar.time)
                barView.findViewById<TextView>(R.id.tvBarValue).text = if (sleepHours > 0) "%.1fh".format(sleepHours) else "-"
                
                val bar = barView.findViewById<View>(R.id.viewBar)
                bar.setBackgroundColor(0xFF5C6BC0.toInt())
                val params = bar.layoutParams as LinearLayout.LayoutParams
                params.height = (sleepHours * 250 / 12.0).toInt().let { dpToPx(it) }.coerceAtLeast(2)
                bar.layoutParams = params
                
                container.addView(barView)
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            
            val divider = View(this).apply { 
                layoutParams = LinearLayout.LayoutParams(dpToPx(3), dpToPx(180)).apply {
                    setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(40))
                }
                setBackgroundColor(0xFF303F9F.toInt()) 
            }
            container.addView(divider)
        }
    }

    private fun refreshMonthlyView() {
        val rv = findViewById<RecyclerView>(R.id.rvMonthlySleep)
        val monthDataList = mutableListOf<MonthData>()
        
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.add(Calendar.MONTH, -5)
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        val monthFormatter = SimpleDateFormat("MMMM", Locale.US)
        val yearFormatter = SimpleDateFormat("yyyy", Locale.US)
        val rangeFormatter = SimpleDateFormat("dd MMM", Locale.US)
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val deficitWeeks = mutableListOf<String>()

        for (m in 0 until 6) {
            val monthName = monthFormatter.format(calendar.time)
            val year = yearFormatter.format(calendar.time)
            val currentMonth = calendar.get(Calendar.MONTH)
            
            val barItems = mutableListOf<BarItem>()
            var weekIndex = 1

            while (calendar.get(Calendar.MONTH) == currentMonth) {
                var weekSum = 0.0
                val weekStart = calendar.time
                
                var isWeekOver = false
                while (!isWeekOver) {
                    val dateKey = dateFormatter.format(calendar.time)
                    weekSum += healthDataManager.getHistoricalSleep(dateKey)
                    
                    val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    val isSunday = (currentDayOfWeek == Calendar.SUNDAY)
                    
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    val isNewMonth = (calendar.get(Calendar.MONTH) != currentMonth)
                    
                    if (isSunday || isNewMonth) {
                        isWeekOver = true
                    }
                }
                
                if (weekSum < 56.0 && weekSum > 0) {
                    val endCal = calendar.clone() as Calendar
                    endCal.add(Calendar.DAY_OF_YEAR, -1)
                    deficitWeeks.add("${rangeFormatter.format(weekStart)} - ${rangeFormatter.format(endCal.time)}")
                }

                val height = (weekSum * 250 / 70.0).toInt().coerceIn(2, 250)

                barItems.add(BarItem(BarData(
                    label = "Week $weekIndex",
                    date = rangeFormatter.format(weekStart),
                    valueDisplay = if (weekSum > 0) "%.0fh".format(weekSum) else "-",
                    heightPx = dpToPx(height),
                    color = 0xFF5C6BC0.toInt()
                )))
                weekIndex++
            }
            monthDataList.add(MonthData(monthName, year, barItems))
        }

        rv.adapter = MonthGraphAdapter(monthDataList)
        rv.onFlingListener = null
        androidx.recyclerview.widget.PagerSnapHelper().attachToRecyclerView(rv)

        val warning = findViewById<TextView>(R.id.tvMonthlySleepWarning)
        if (deficitWeeks.isNotEmpty()) {
            warning.visibility = View.VISIBLE
            warning.text = "⚠️ Target sleep (56h) not met in: ${deficitWeeks.take(3).joinToString(", ")}..."
        } else {
            warning.visibility = View.GONE
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
