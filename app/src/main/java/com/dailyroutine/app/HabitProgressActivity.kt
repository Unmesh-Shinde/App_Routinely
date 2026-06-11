package com.dailyroutine.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class HabitProgressActivity : AppCompatActivity() {

    private lateinit var mgr: ReminderManager
    private lateinit var adapter: TaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_habit_progress)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        mgr = ReminderManager(this)
        
        val rvTasks = findViewById<RecyclerView>(R.id.rvTasks)
        rvTasks.layoutManager = LinearLayoutManager(this)
        adapter = TaskAdapter()
        rvTasks.adapter = adapter

        refresh()
    }

    private fun refresh() {
        val planManager = PlanManager(this)
        val allReminders = mgr.getAllReminders().filter { !it.isHidden }
        val doneIds = RoutineProgressStore.getDoneIds(this)

        // Fetch today's plan items
        val calendar = Calendar.getInstance()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val planDay = if (dayOfWeek == 1) 7 else dayOfWeek - 1

        val meals = planManager.getMealsForDate(todayStr).map {
            Reminder(id = it.id, title = it.name, type = ReminderType.MEAL, hour = it.hour, minute = it.minute)
        }

        val workouts = planManager.getExercisesForDate(todayStr).map {
            Reminder(id = it.id, title = it.name, type = ReminderType.EXERCISE, hour = it.hour, minute = it.minute)
        }

        val all = allReminders + meals + workouts
        
        val completed = all.filter { it.id.toString() in doneIds }
        val pending = all.filter { it.isEnabled && it.id.toString() !in doneIds }

        findViewById<TextView>(R.id.tvDoneCount).text = completed.size.toString()
        findViewById<TextView>(R.id.tvPendingCount).text = pending.size.toString()

        // Combine for the list: Pending first, then completed
        val combined = pending.sortedBy { it.hour * 60 + it.minute } + 
                       completed.sortedBy { it.hour * 60 + it.minute }
        
        adapter.submitList(combined, doneIds)
    }

    inner class TaskAdapter : RecyclerView.Adapter<TaskAdapter.VH>() {
        private var items = listOf<Reminder>()
        private var doneIds = setOf<String>()

        fun submitList(list: List<Reminder>, done: Set<String>) {
            items = list
            doneIds = done
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = items[position]
            val isDone = r.id.toString() in doneIds
            
            holder.tvEmoji.text = if (isDone) "✅" else r.type.emoji
            holder.tvTitle.text = r.title
            holder.tvSubtitle.text = r.type.label
            holder.tvTime.text = r.formatTime()
            
            // UI Tweaks for progress list
            holder.itemView.alpha = if (isDone) 0.5f else 1.0f
            holder.itemView.findViewById<View>(R.id.switchEnabled).visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.btnEdit).visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.btnDelete).visibility = View.GONE
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
