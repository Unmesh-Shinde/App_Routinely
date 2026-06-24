package com.dailyroutine.app

import android.app.AlertDialog
import android.app.DatePickerDialog
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
    private val calendar = Calendar.getInstance()
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
        
        setupRecyclerView()
        setupDatePickers()
        refreshMeals()

        findViewById<FloatingActionButton>(R.id.fabAddMeal).setOnClickListener {
            showEditMealDialog(null)
        }

        findViewById<MaterialButton>(R.id.btnQuickRoutine).setOnClickListener {
            planManager.addQuickIndianDiet(dateFormatter.format(calendar.time))
            refreshMeals()
        }
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvMeals)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = MealAdapter(
            onEdit = { showEditMealDialog(it) },
            onDelete = { deleteMeal(it) }
        )
        rv.adapter = adapter
    }

    private fun setupDatePickers() {
        val tvSelectedDate = findViewById<TextView>(R.id.tvSelectedDate)
        val btnPickDate = findViewById<Button>(R.id.btnPickDate)

        val updateDateText = {
            tvSelectedDate.text = displayFormatter.format(calendar.time)
            refreshMeals()
            updateDaysStrip()
        }

        btnPickDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                calendar.set(y, m, d)
                updateDateText()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        updateDateText()
    }

    private fun updateDaysStrip() {
        val llDays = findViewById<LinearLayout>(R.id.llDays)
        llDays.removeAllViews()
        val tempCal = calendar.clone() as Calendar
        tempCal.add(Calendar.DAY_OF_YEAR, -3)

        for (i in 0 until 7) {
            val dateStr = dateFormatter.format(tempCal.time)
            val isSelected = dateStr == dateFormatter.format(calendar.time)
            
            val dayView = LayoutInflater.from(this).inflate(R.layout.item_meal, llDays, false).apply {
                val tv = findViewById<TextView>(R.id.tvTitle)
                tv.text = SimpleDateFormat("EEE\ndd", Locale.US).format(tempCal.time)
                tv.textAlignment = View.TEXT_ALIGNMENT_CENTER
                
                alpha = if (isSelected) 1.0f else 0.5f
                setOnClickListener {
                    calendar.time = dateFormatter.parse(dateStr)!!
                    findViewById<TextView>(R.id.tvSelectedDate).text = displayFormatter.format(calendar.time)
                    refreshMeals()
                    updateDaysStrip()
                }
            }
            llDays.addView(dayView)
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun refreshMeals() {
        val dateStr = dateFormatter.format(calendar.time)
        val meals = planManager.getMealsForDate(dateStr).sortedBy { it.hour * 60 + it.minute }
        adapter.setMeals(meals)
        
        findViewById<TextView>(R.id.tvMealCount).text = "${meals.size} meals planned for today"
        
        WellnessEngine.calculateIntakeForDate(this, dateStr) { total ->
            runOnUiThread {
                findViewById<TextView>(R.id.tvTotalCaloriesToday).text = "~$total kcal"
            }
        }
    }

    private fun showEditMealDialog(meal: Meal?) {
        val dateStr = dateFormatter.format(calendar.time)
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_edit_meal, null)
        val etName = v.findViewById<EditText>(R.id.etMealName)
        val etDesc = v.findViewById<EditText>(R.id.etMealNotes)
        val spinner = v.findViewById<Spinner>(R.id.spinnerMealType)
        val tvTime = v.findViewById<TextView>(R.id.tvMealTime)
        
        val types = arrayOf("Breakfast", "Lunch", "Dinner", "Snack")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)

        var h = meal?.hour ?: 12
        var m = meal?.minute ?: 0
        
        meal?.let {
            etName.setText(it.name)
            etDesc.setText(it.description)
            spinner.setSelection(types.indexOf(it.mealType).coerceAtLeast(0))
            tvTime.text = String.format(Locale.US, "%02d:%02d", h, m)
        }

        v.findViewById<Button>(R.id.btnPickMealTime).setOnClickListener {
            android.app.TimePickerDialog(this, { _, sh, sm ->
                h = sh; m = sm
                tvTime.text = String.format(Locale.US, "%02d:%02d", h, m)
            }, h, m, false).show()
        }

        AlertDialog.Builder(this)
            .setTitle(if (meal == null) "Add Meal" else "Edit Meal")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                val newMeal = (meal ?: Meal()).copy(
                    name = etName.text.toString(),
                    description = etDesc.text.toString(),
                    mealType = spinner.selectedItem.toString(),
                    hour = h,
                    minute = m
                )
                planManager.saveMealForDate(dateStr, newMeal)
                refreshMeals()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMeal(meal: Meal) {
        AlertDialog.Builder(this)
            .setTitle("Delete Meal?")
            .setMessage("Remove ${meal.name} from your plan?")
            .setPositiveButton("Delete") { _, _ ->
                planManager.deleteMealForDate(dateFormatter.format(calendar.time), meal)
                refreshMeals()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class MealAdapter(
        private val onEdit: (Meal) -> Unit,
        private val onDelete: (Meal) -> Unit
    ) : RecyclerView.Adapter<MealAdapter.VH>() {
        private var items = listOf<Meal>()

        fun setMeals(list: List<Meal>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_meal, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = items[position]
            holder.tvEmoji.text = when (m.mealType) {
                "Breakfast" -> "🍳"
                "Lunch" -> "🥗"
                "Dinner" -> "🍲"
                else -> "🍎"
            }
            holder.tvTitle.text = m.name
            holder.tvSubtitle.text = "${m.formatTime()} • ${m.mealType}"
            
            CalorieSearchEngine.getCalories(holder.itemView.context, "${m.name} ${m.description}") { cals ->
                holder.itemView.post {
                    holder.tvTime.text = "$cals kcal"
                }
            }

            holder.itemView.setOnClickListener { onEdit(m) }
            holder.itemView.setOnLongClickListener { onDelete(m); true }
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
