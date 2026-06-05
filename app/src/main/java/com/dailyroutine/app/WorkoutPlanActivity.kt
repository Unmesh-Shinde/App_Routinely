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

class WorkoutPlanActivity : AppCompatActivity() {

    private lateinit var planManager: PlanManager
    private lateinit var currentPlan: WorkoutPlan
    private var selectedDay = 1
    private lateinit var adapter: ExerciseAdapter
    private var currentDuration = ChallengeDuration.TEN_DAYS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_plan)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        planManager = PlanManager(this)

        val llChallenges = findViewById<LinearLayout>(R.id.llChallenges)
        val llDays = findViewById<LinearLayout>(R.id.llWorkoutDays)
        val rvExercises = findViewById<RecyclerView>(R.id.rvExercises)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAddExercise)

        adapter = ExerciseAdapter { editExercise(it) }
        rvExercises.layoutManager = LinearLayoutManager(this)
        rvExercises.adapter = adapter

        // Setup Challenge Selectors
        setupChallengeSelectors(llChallenges, llDays)

        fabAdd.setOnClickListener { showExerciseDialog(null) }

        loadPlan(ChallengeDuration.TEN_DAYS)
        setupDaySelector(llDays)
    }

    private fun setupChallengeSelectors(container: LinearLayout, dayContainer: LinearLayout) {
        for (i in 0 until container.childCount) {
            val card = container.getChildAt(i)
            card.setOnClickListener {
                currentDuration = when (i) {
                    0 -> ChallengeDuration.TEN_DAYS
                    1 -> ChallengeDuration.FIFTEEN_DAYS
                    else -> ChallengeDuration.THIRTY_DAYS
                }
                loadPlan(currentDuration)
                setupDaySelector(dayContainer)
                updateChallengeUI(container)
            }
        }
    }

    private fun updateChallengeUI(container: LinearLayout) {
        for (i in 0 until container.childCount) {
            container.getChildAt(i).alpha = if (
                (i == 0 && currentDuration == ChallengeDuration.TEN_DAYS) ||
                (i == 1 && currentDuration == ChallengeDuration.FIFTEEN_DAYS) ||
                (i == 2 && currentDuration == ChallengeDuration.THIRTY_DAYS)
            ) 1f else 0.6f
        }
    }

    private fun loadPlan(duration: ChallengeDuration) {
        currentPlan = planManager.getWorkoutPlan(duration)
        selectedDay = 1
        refreshExercises()
    }

    private fun setupDaySelector(container: LinearLayout) {
        container.removeAllViews()
        for (i in 1..currentPlan.duration.days) {
            val btn = Button(this, null, android.R.attr.buttonStyleSmall).apply {
                text = "Day $i"
                setOnClickListener {
                    selectedDay = i
                    updateDayButtons(container)
                    refreshExercises()
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

    private fun refreshExercises() {
        val list = currentPlan.dailyExercises[selectedDay] ?: mutableListOf()
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

        var selHour = existing?.hour ?: 7
        var selMin = existing?.minute ?: 0
        fun updateTimeLabel() { tvTime.text = "%02d:%02d".format(selHour, selMin) }
        updateTimeLabel()

        existing?.let {
            etName.setText(it.name)
            etSets.setText(it.sets.toString())
            etReps.setText(it.reps)
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
            .setTitle(if (existing == null) "Add Exercise" else "Edit Exercise")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                
                val newEx = Exercise(
                    id = existing?.id ?: System.currentTimeMillis().toInt(),
                    name = name,
                    sets = etSets.text.toString().toIntOrNull() ?: 3,
                    reps = etReps.text.toString(),
                    hour = selHour,
                    minute = selMin,
                    isReminderEnabled = swReminder.isChecked
                )
                
                val list = currentPlan.dailyExercises.getOrPut(selectedDay) { mutableListOf() }
                val idx = list.indexOfFirst { it.id == newEx.id }
                if (idx >= 0) list[idx] = newEx else list.add(newEx)
                
                planManager.saveWorkoutPlan(currentPlan)
                planManager.syncExerciseReminder(this@WorkoutPlanActivity, newEx, selectedDay, currentPlan.duration)
                refreshExercises()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            holder.tvEmoji.text = "💪"
            holder.tvTitle.text = ex.name
            holder.tvSubtitle.text = "${ex.sets} sets x ${ex.reps}"
            holder.tvTime.text = "%02d:%02d".format(ex.hour, ex.minute)
            holder.itemView.setOnClickListener { onEdit(ex) }
            
            holder.itemView.findViewById<View>(R.id.switchEnabled).visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.btnDelete).setOnClickListener {
                currentPlan.dailyExercises[selectedDay]?.remove(ex)
                planManager.saveWorkoutPlan(currentPlan)
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
