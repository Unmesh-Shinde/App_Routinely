package com.dailyroutine.app

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ReminderAdapter(
    private val listener: OnReminderListener
) : RecyclerView.Adapter<ReminderAdapter.VH>() {

    interface OnReminderListener {
        fun onToggle(reminder: Reminder)
        fun onEdit(reminder: Reminder)
        fun onDelete(reminder: Reminder)
        fun onSelectionChanged(count: Int)
    }

    private val items = mutableListOf<Reminder>()
    private val selectedIds = mutableSetOf<Int>()

    fun getSelectedIds(): Set<Int> = selectedIds

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
        listener.onSelectionChanged(0)
    }

    fun toggleSelection(id: Int) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        notifyDataSetChanged()
        listener.onSelectionChanged(selectedIds.size)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setReminders(list: List<Reminder>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.tvEmoji)
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvSubtitle: TextView = view.findViewById(R.id.tvSubtitle)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)
        private val switchEnabled: SwitchCompat = view.findViewById(R.id.switchEnabled)
        private val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        private val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)

        fun bind(r: Reminder) {
            val isSelected = selectedIds.contains(r.id)
            ivIcon.setImageResource(RoutineIconMapper.iconForReminder(r.type))
            ivIcon.setBackgroundResource(RoutineIconMapper.badgeForReminder(r.type))
            tvTitle.text = r.title
            
            val sub = r.type.label
            tvSubtitle.text = sub

            tvTime.text = if (r.isIntervalBased) {
                "Every ${r.intervalMinutes} min"
            } else {
                val days = buildDaysLabel(r.repeatDays)
                "${r.formatTime()}  $days"
            }

            val cardView = itemView as MaterialCardView
            val isNightMode = (itemView.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dpDensity = itemView.resources.displayMetrics.density

            if (isSelected) {
                cardView.strokeColor = if (isNightMode) Color.parseColor("#9FA8DA") else Color.parseColor("#3F51B5")
                cardView.strokeWidth = (2 * dpDensity).toInt()
                cardView.setCardBackgroundColor(if (isNightMode) Color.parseColor("#1A237E") else Color.parseColor("#E8EAF6"))
            } else {
                cardView.strokeColor = ContextCompat.getColor(itemView.context, R.color.cardStroke)
                cardView.strokeWidth = (1 * dpDensity).toInt()
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.cardBg))
            }

            itemView.alpha = if (r.isEnabled || isSelected) 1f else 0.45f

            switchEnabled.visibility = if (selectedIds.isNotEmpty()) View.GONE else View.VISIBLE
            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.isChecked = r.isEnabled
            switchEnabled.setOnCheckedChangeListener { _, _ -> listener.onToggle(r) }

            btnEdit.visibility = View.GONE
            btnEdit.setOnClickListener(null)
            btnDelete.visibility = if (selectedIds.isNotEmpty()) View.GONE else View.VISIBLE
            btnDelete.setOnClickListener { listener.onDelete(r) }

            itemView.setOnLongClickListener {
                if (selectedIds.isEmpty()) {
                    toggleSelection(r.id)
                }
                true
            }

            itemView.setOnClickListener {
                if (selectedIds.isNotEmpty()) {
                    toggleSelection(r.id)
                } else {
                    listener.onEdit(r)
                }
            }
        }

        private fun buildDaysLabel(days: List<Int>): String {
            if (days.size == 7) return "Every day"
            val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val sorted = days.sorted()
            return when (sorted) {
                listOf(1, 2, 3, 4, 5) -> "Weekdays"
                listOf(6, 7) -> "Weekends"
                else -> sorted.joinToString(", ") { names.getOrElse(it - 1) { "" } }
            }
        }
    }
}
