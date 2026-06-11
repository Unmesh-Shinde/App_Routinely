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

class WorkoutPlanActivity : AppCompatActivity() {

    private lateinit var planManager: PlanManager
    private lateinit var adapter: ExerciseAdapter
    private var selectedCalendar = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_plan)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        planManager = PlanManager(this)

        val rvExercises = findViewById<RecyclerView>(R.id.rvExercises)
        adapter = ExerciseAdapter { editExercise(it) }
        rvExercises.layoutManager = LinearLayoutManager(this)
        rvExercises.adapter = adapter

        findViewById<MaterialButton>(R.id.btnPickDateWorkout).setOnClickListener {
            showDatePicker()
        }

        findViewById<FloatingActionButton>(R.id.fabAddExercise).setOnClickListener {
            showExerciseDialog(null)
        }

        setupDateStrip()
        updateDateUI()
    }

    private fun setupDateStrip() {
        val container = findViewById<LinearLayout>(R.id.llWorkoutDaysStrip)
        container.removeAllViews()
        
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
        findViewById<TextView>(R.id.tvSelectedDateWorkout).text = displayFormatter.format(selectedCalendar.time)
        refreshExercises()
    }

    private fun refreshExercises() {
        val dateStr = dateFormatter.format(selectedCalendar.time)
        val list = planManager.getExercisesForDate(dateStr)
        adapter.submitList(list.sortedBy { it.hour * 60 + it.minute })
    }

    private fun showExerciseDialog(existing: Exercise?) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_edit_exercise, null)
        val etName = v.findViewById<EditText>(R.id.etExName)
        val etSets = v.findViewById<EditText>(R.id.etExSets)
        val etReps = v.findViewById<EditText>(R.id.etExReps)
        val tvTime = v.findViewById<TextView>(R.id.tvExTime)
        val btnTime = v.findViewById<Button>(R.id.btnPickExTime)
        val swReminder = v.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.swExReminder)
        val etTarget = v.findViewById<EditText>(R.id.etExTarget)
        val sliderIntensity = v.findViewById<com.google.android.material.slider.Slider>(R.id.sliderExIntensity)

        var selHour = existing?.hour ?: 7
        var selMin = existing?.minute ?: 0
        fun updateTimeLabel() {
            val h = if (selHour == 0 || selHour == 12) 12 else selHour % 12
            val amPm = if (selHour < 12) "AM" else "PM"
            tvTime.text = "%02d:%02d %s".format(h, selMin, amPm)
        }
        updateTimeLabel()

        existing?.let {
            etName.setText(it.name)
            etSets.setText(it.sets.toString())
            etReps.setText(it.reps)
            swReminder.isChecked = it.isReminderEnabled
            etTarget.setText(it.targetArea)
            sliderIntensity.value = it.intensity.toFloat()
        }

        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                selHour = h
                selMin = m
                updateTimeLabel()
            }, selHour, selMin, false).show()
        }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "Add Exercise" else "Edit Exercise")
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
            
            val newEx = Exercise(
                id = existing?.id ?: Exercise().id,
                name = name,
                sets = etSets.text.toString().toIntOrNull() ?: 3,
                reps = etReps.text.toString(),
                hour = selHour,
                minute = selMin,
                isReminderEnabled = swReminder.isChecked,
                targetArea = etTarget.text.toString().trim(),
                intensity = sliderIntensity.value.toInt()
            )
            
            val dateStr = dateFormatter.format(selectedCalendar.time)
            planManager.saveExerciseForDate(dateStr, newEx)
            planManager.syncExerciseReminder(this@WorkoutPlanActivity, newEx, dateStr)
            refreshExercises()
            dialog.dismiss()
        }
    }

    private fun editExercise(ex: Exercise) {
        showExerciseDialog(ex)
    }

    inner class ExerciseAdapter(private val onEdit: (Exercise) -> Unit) : RecyclerView.Adapter<ExerciseAdapter.VH>() {
        private var items = listOf<Exercise>()

        fun submitList(list: List<Exercise>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ex = items[position]
            val isDone = RoutineProgressStore.getDoneIds(this@WorkoutPlanActivity).contains(ex.id.toString())

            holder.tvEmoji.text = "💪"
            holder.tvTitle.text = ex.name
            holder.tvSubtitle.text = "${ex.sets}x${ex.reps} • ${ex.targetArea} (${ex.intensity}%)"
            holder.tvTime.text = ex.formatTime()

            holder.itemView.setOnClickListener { onEdit(ex) }
            holder.itemView.findViewById<View>(R.id.btnEdit).setOnClickListener { onEdit(ex) }
            
            val switch = holder.itemView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchEnabled)
            switch.visibility = View.VISIBLE
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = isDone
            switch.setOnCheckedChangeListener { _, checked ->
                RoutineProgressStore.setDoneStatus(this@WorkoutPlanActivity, ex.id, checked)
                refreshExercises()
            }

            holder.itemView.findViewById<View>(R.id.btnDelete).setOnClickListener {
                val dateStr = dateFormatter.format(selectedCalendar.time)
                planManager.deleteExerciseForDate(dateStr, ex)
                refreshExercises()
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
