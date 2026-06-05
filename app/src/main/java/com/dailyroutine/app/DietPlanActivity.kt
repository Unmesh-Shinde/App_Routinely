package com.dailyroutine.app

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout

class DietPlanActivity : AppCompatActivity() {

    private lateinit var planManager: PlanManager
    private lateinit var currentPlan: DietPlan
    private var selectedDay = 1
    private lateinit var adapter: MealAdapter
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diet_plan)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        planManager = PlanManager(this)
        
        tabLayout = findViewById(R.id.tabLayout)
        val rvMeals = findViewById<RecyclerView>(R.id.rvMeals)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAddMeal)
        val horizontalDayScroll = findViewById<LinearLayout>(R.id.llDays)

        adapter = MealAdapter { editMeal(it) }
        rvMeals.layoutManager = LinearLayoutManager(this)
        rvMeals.adapter = adapter

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val duration = when (tab?.position) {
                    0 -> PlanDuration.WEEKLY
                    1 -> PlanDuration.BI_WEEKLY
                    else -> PlanDuration.MONTHLY
                }
                loadPlan(duration)
                setupDaySelector(horizontalDayScroll)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        fabAdd.setOnClickListener { showMealDialog(null) }

        loadPlan(PlanDuration.WEEKLY)
        setupDaySelector(horizontalDayScroll)
    }

    private fun loadPlan(duration: PlanDuration) {
        currentPlan = planManager.getDietPlan(duration)
        selectedDay = 1
        refreshMeals()
    }

    private fun setupDaySelector(container: LinearLayout) {
        container.removeAllViews()
        val totalDays = currentPlan.duration.totalDays
        for (i in 1..totalDays) {
            val btn = Button(this, null, android.R.attr.buttonStyleSmall).apply {
                text = "Day $i"
                setOnClickListener {
                    selectedDay = i
                    updateDayButtons(container)
                    refreshMeals()
                }
                alpha = if (i == selectedDay) 1f else 0.5f
            }
            container.addView(btn)
        }
    }

    private fun updateDayButtons(container: LinearLayout) {
        for (i in 0 until container.childCount) {
            container.getChildAt(i).alpha = if (i + 1 == selectedDay) 1f else 0.5f
        }
    }

    private fun refreshMeals() {
        val meals = currentPlan.dailyMeals[selectedDay] ?: mutableListOf()
        adapter.submitList(meals.sortedBy { it.hour * 60 + it.minute })
    }

    private fun showMealDialog(existing: Meal?) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_edit_meal, null)
        val etName = v.findViewById<EditText>(R.id.etMealName)
        val etNotes = v.findViewById<EditText>(R.id.etMealNotes)
        val tvTime = v.findViewById<TextView>(R.id.tvMealTime)
        val btnTime = v.findViewById<Button>(R.id.btnPickMealTime)
        val swReminder = v.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swMealReminder)

        var selHour = existing?.hour ?: 8
        var selMin = existing?.minute ?: 0
        
        fun updateTimeLabel() { tvTime.text = "%02d:%02d".format(selHour, selMin) }
        updateTimeLabel()

        existing?.let {
            etName.setText(it.name)
            etNotes.setText(it.description)
            swReminder.isChecked = it.isReminderEnabled
        }

        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                selHour = h
                selMin = m
                updateTimeLabel()
            }, selHour, selMin, true).show()
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add Meal" else "Edit Meal")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                
                val newMeal = Meal(
                    id = existing?.id ?: System.currentTimeMillis().toInt(),
                    name = name,
                    description = etNotes.text.toString().trim(),
                    hour = selHour,
                    minute = selMin,
                    isReminderEnabled = swReminder.isChecked
                )
                
                val list = currentPlan.dailyMeals.getOrPut(selectedDay) { mutableListOf() }
                val idx = list.indexOfFirst { it.id == newMeal.id }
                if (idx >= 0) list[idx] = newMeal else list.add(newMeal)
                
                planManager.saveDietPlan(currentPlan)
                planManager.syncMealReminder(this@DietPlanActivity, newMeal, selectedDay, currentPlan.duration)
                refreshMeals()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editMeal(meal: Meal) {
        showMealDialog(meal)
    }

    inner class MealAdapter(private val onEdit: (Meal) -> Unit) : RecyclerView.Adapter<MealAdapter.VH>() {
        private var items = listOf<Meal>()

        fun submitList(list: List<Meal>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = items[position]
            holder.tvEmoji.text = "🍳"
            holder.tvTitle.text = m.name
            holder.tvSubtitle.text = m.description
            holder.tvTime.text = "%02d:%02d".format(m.hour, m.minute)
            holder.itemView.setOnClickListener { onEdit(m) }
            
            // Hide reminder toggle for now in plan list to keep simple
            holder.itemView.findViewById<View>(R.id.switchEnabled).visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.btnDelete).setOnClickListener {
                currentPlan.dailyMeals[selectedDay]?.remove(m)
                planManager.saveDietPlan(currentPlan)
                refreshMeals()
            }
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
