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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
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
        InsetHelper.applyTopPadding(findViewById(R.id.appBar))
        InsetHelper.applyBottomPadding(findViewById(R.id.rvMeals))
        InsetHelper.applyBottomMargin(findViewById(R.id.btnQuickRoutine))
        InsetHelper.applyBottomMargin(findViewById(R.id.fabAddMeal))

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

        val stripDateFormatter = SimpleDateFormat("EEE\ndd", Locale.US)

        for (i in 0 until 7) {
            val dateStr = dateFormatter.format(tempCal.time)
            val isSelected = dateStr == dateFormatter.format(calendar.time)
            
            val btn = Button(this, null, android.R.attr.buttonStyleSmall).apply {
                text = stripDateFormatter.format(tempCal.time)
                setOnClickListener {
                    val clickedCal = Calendar.getInstance()
                    clickedCal.time = dateFormatter.parse(dateStr) ?: Date()
                    calendar.time = clickedCal.time
                    this@DietPlanActivity.findViewById<TextView>(R.id.tvSelectedDate).text = displayFormatter.format(calendar.time)
                    refreshMeals()
                    updateDaysStrip()
                }
                alpha = if (isSelected) 1f else 0.5f
                if (isSelected) setBackgroundColor(0x33FFFFFF)
            }
            llDays.addView(btn)
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
        fun updateTimeLabel() {
            val displayHour = if (h == 0 || h == 12) 12 else h % 12
            val amPm = if (h < 12) "AM" else "PM"
            tvTime.text = "%02d:%02d %s".format(displayHour, m, amPm)
        }
        updateTimeLabel()
        
        meal?.let {
            etName.setText(it.name)
            etDesc.setText(it.description)
            spinner.setSelection(types.indexOf(it.mealType).coerceAtLeast(0))
            updateTimeLabel()
        }

        v.findViewById<Button>(R.id.btnPickMealTime).setOnClickListener {
            android.app.TimePickerDialog(this, { _, sh, sm ->
                h = sh; m = sm
                updateTimeLabel()
            }, h, m, false).show()
        }

        AlertDialog.Builder(this)
            .setTitle(if (meal == null) "Add Meal" else "Edit Meal")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Meal name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val loading = Toast.makeText(this, "Calculating calories...", Toast.LENGTH_SHORT)
                loading.show()
                
                CalorieSearchEngine.getCalories(this, name, desc) { cals ->
                    val newMeal = (meal ?: Meal()).copy(
                        name = name,
                        description = desc,
                        mealType = spinner.selectedItem.toString(),
                        hour = h,
                        minute = m,
                        calories = cals
                    )
                    planManager.saveMealForDate(dateStr, newMeal)
                    planManager.syncMealReminder(this@DietPlanActivity, newMeal, dateStr)
                    refreshMeals()
                    loading.cancel()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMeal(mealId: Int) {
        val dateStr = dateFormatter.format(calendar.time)
        val meal = planManager.getMealsForDate(dateStr).find { it.id == mealId } ?: return
        
        AlertDialog.Builder(this)
            .setTitle("Delete Meal?")
            .setMessage("Remove ${meal.name} from your plan?")
            .setPositiveButton("Delete") { _, _ ->
                planManager.deleteMealForDate(dateStr, meal)
                refreshMeals()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class MealAdapter(
        private val onEdit: (Meal) -> Unit,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<MealAdapter.VH>() {
        private var items = listOf<Meal>()

        fun setMeals(list: List<Meal>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = items[position]
            holder.ivIcon.setImageResource(RoutineIconMapper.iconForMealType(m.mealType))
            holder.ivIcon.setBackgroundResource(RoutineIconMapper.badgeForMealType(m.mealType))
            holder.tvTitle.text = m.name
            holder.tvSubtitle.text = m.mealType
            holder.tvSubtitle.setBackgroundResource(R.drawable.bg_chip)
            holder.tvSubtitle.visibility = View.VISIBLE
            
            if (m.calories > 0) {
                holder.tvTime.text = "${m.formatTime()} • ${m.calories} kcal"
            } else {
                holder.tvTime.text = m.formatTime()
            }

            holder.itemView.setOnClickListener { onEdit(m) }
            
            val btnDelete = holder.itemView.findViewById<View>(R.id.btnDelete)
            btnDelete.visibility = View.VISIBLE
            btnDelete.setOnClickListener { onDelete(m.id) }

            val btnEdit = holder.itemView.findViewById<View>(R.id.btnEdit)
            btnEdit.visibility = View.GONE
            btnEdit.setOnClickListener(null)
            
            // Hide switch as it's not used here
            holder.itemView.findViewById<View>(R.id.switchEnabled).visibility = View.GONE
        }

        override fun getItemCount() = items.size

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivIcon: ImageView = v.findViewById(R.id.tvEmoji)
            val tvTitle: TextView = v.findViewById(R.id.tvTitle)
            val tvSubtitle: TextView = v.findViewById(R.id.tvSubtitle)
            val tvTime: TextView = v.findViewById(R.id.tvTime)
        }
    }
}
