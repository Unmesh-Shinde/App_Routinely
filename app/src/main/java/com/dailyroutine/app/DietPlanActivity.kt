package com.dailyroutine.app

import android.app.AlertDialog
import android.app.DatePickerDialog
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*

class DietPlanActivity : AppCompatActivity() {

    private lateinit var planManager: PlanManager
    private lateinit var adapter: MealAdapter
    private var selectedCalendar = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diet_plan)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        planManager = PlanManager(this)

        val rvMeals = findViewById<RecyclerView>(R.id.rvMeals)
        adapter = MealAdapter { editMeal(it) }
        rvMeals.layoutManager = LinearLayoutManager(this)
        rvMeals.adapter = adapter

        findViewById<MaterialButton>(R.id.btnPickDate).setOnClickListener {
            showDatePicker()
        }

        findViewById<FloatingActionButton>(R.id.fabAddMeal).setOnClickListener {
            showMealDialog(null)
        }

        findViewById<MaterialButton>(R.id.btnQuickRoutine).setOnClickListener {
            addQuickRoutine()
        }

        setupDateStrip()
        updateDateUI()
    }

    private fun setupDateStrip() {
        val container = findViewById<LinearLayout>(R.id.llDays)
        container.removeAllViews()
        
        // Show 7 days around selected date
        val tempCal = selectedCalendar.clone() as Calendar
        tempCal.add(Calendar.DAY_OF_YEAR, -3)

        val stripDateFormatter = SimpleDateFormat("EEE\ndd", Locale.US)

        for (i in 0 until 7) {
            val dateStr = dateFormatter.format(tempCal.time)
            val isSelected = dateStr == dateFormatter.format(selectedCalendar.time)
            
            val btn = Button(this, null, android.R.attr.buttonStyleSmall).apply {
                text = stripDateFormatter.format(tempCal.time)
                setOnClickListener {
                    val clickedCal = Calendar.getInstance()
                    clickedCal.time = dateFormatter.parse(dateStr) ?: Date()
                    selectedCalendar = clickedCal
                    updateDateUI()
                    setupDateStrip()
                }
                alpha = if (isSelected) 1f else 0.5f
                if (isSelected) setBackgroundColor(0x33FFFFFF)
            }
            container.addView(btn)
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(this, { _, y, m, d ->
            selectedCalendar.set(y, m, d)
            updateDateUI()
            setupDateStrip()
        }, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), selectedCalendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateUI() {
        findViewById<TextView>(R.id.tvSelectedDate).text = displayFormatter.format(selectedCalendar.time)
        refreshMeals()
    }

    private fun refreshMeals() {
        val dateStr = dateFormatter.format(selectedCalendar.time)
        val list = planManager.getMealsForDate(dateStr)
        adapter.submitList(list.sortedBy { it.hour * 60 + it.minute })

        // Update summary strip
        val count = list.size
        val dateLabel = if (dateStr == dateFormatter.format(Date())) "today" else "this day"
        findViewById<TextView>(R.id.tvMealCount).text = "$count meals planned for $dateLabel"
        
        val totalCals = list.sumOf { CalorieSearchEngine.getCalories("${it.name} ${it.description}") }
        findViewById<TextView>(R.id.tvTotalCaloriesToday).text = "~$totalCals kcal"
    }

    private fun showMealDialog(existing: Meal?) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_edit_meal, null)
        val etName = v.findViewById<EditText>(R.id.etMealName)
        val etDesc = v.findViewById<EditText>(R.id.etMealNotes)
        val spinnerType = v.findViewById<Spinner>(R.id.spinnerMealType)
        val tvTime = v.findViewById<TextView>(R.id.tvMealTime)
        val btnTime = v.findViewById<Button>(R.id.btnPickMealTime)
        val swReminder = v.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swMealReminder)

        val types = arrayOf("Breakfast", "Lunch", "Dinner", "Snack")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        spinnerType.adapter = spinnerAdapter

        var selHour = existing?.hour ?: 12
        var selMin = existing?.minute ?: 0

        fun updateTimeLabel() {
            val h = if (selHour == 0 || selHour == 12) 12 else selHour % 12
            val amPm = if (selHour < 12) "AM" else "PM"
            tvTime.text = "%02d:%02d %s".format(h, selMin, amPm)
        }
        updateTimeLabel()

        existing?.let {
            etName.setText(it.name)
            etDesc.setText(it.description)
            spinnerType.setSelection(types.indexOf(it.mealType).coerceAtLeast(0))
            swReminder.isChecked = it.isReminderEnabled
        }

        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                selHour = h
                selMin = m
                updateTimeLabel()
            }, selHour, selMin, false).show()
        }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "Add Meal" else "Edit Meal")
            .setView(v)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                etName.error = "Required"
                return@setOnClickListener
            }

            val newMeal = Meal(
                id = existing?.id ?: Meal().id,
                name = name,
                description = etDesc.text.toString().trim(),
                hour = selHour,
                minute = selMin,
                mealType = spinnerType.selectedItem.toString(),
                isReminderEnabled = swReminder.isChecked
            )

            val dateStr = dateFormatter.format(selectedCalendar.time)
            planManager.saveMealForDate(dateStr, newMeal)
            planManager.syncMealReminder(this@DietPlanActivity, newMeal, dateStr)
            refreshMeals()
            dialog.dismiss()
        }
    }

    private fun editMeal(meal: Meal) {
        showMealDialog(meal)
    }

    private fun addQuickRoutine() {
        val dateStr = dateFormatter.format(selectedCalendar.time)
        val routine = listOf(
            Meal(name = "Oats with Milk", hour = 8, minute = 0, mealType = "Breakfast"),
            Meal(name = "Brown Rice & Dal", hour = 13, minute = 0, mealType = "Lunch"),
            Meal(name = "Roti & Sabzi", hour = 20, minute = 0, mealType = "Dinner")
        )
        routine.forEach { 
            planManager.saveMealForDate(dateStr, it)
            planManager.syncMealReminder(this, it, dateStr)
        }
        refreshMeals()
        Toast.makeText(this, "Quick Routine Added for Today!", Toast.LENGTH_SHORT).show()
    }

    inner class MealAdapter(private val onEdit: (Meal) -> Unit) : RecyclerView.Adapter<MealAdapter.VH>() {
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
            val isDone = RoutineProgressStore.getDoneIds(this@DietPlanActivity).contains(m.id.toString())

            holder.tvEmoji.text = when (m.mealType) {
                "Breakfast" -> "🍳"
                "Lunch" -> "🥗"
                "Dinner" -> "🍲"
                "Snack" -> "🍎"
                else -> "🍴"
            }
            holder.tvTitle.text = m.name
            holder.tvSubtitle.text = "${m.mealType} • ${m.description}"
            holder.tvTime.text = m.formatTime()
            
            holder.itemView.setOnClickListener { onEdit(m) }
            holder.itemView.findViewById<View>(R.id.btnEdit).setOnClickListener { onEdit(m) }
            
            val switch = holder.itemView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchEnabled)
            switch.visibility = View.VISIBLE
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = isDone
            switch.setOnCheckedChangeListener { _, checked ->
                RoutineProgressStore.setDoneStatus(this@DietPlanActivity, m.id, checked)
                refreshMeals()
            }

            holder.itemView.findViewById<View>(R.id.btnDelete).setOnClickListener {
                val dateStr = dateFormatter.format(selectedCalendar.time)
                planManager.deleteMealForDate(dateStr, m)
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
