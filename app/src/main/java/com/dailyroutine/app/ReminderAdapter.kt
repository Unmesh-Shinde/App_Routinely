package com.dailyroutine.app

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class ReminderAdapter(
    private val listener: OnReminderListener
) : RecyclerView.Adapter<ReminderAdapter.VH>() {

    interface OnReminderListener {
        fun onToggle(reminder: Reminder)
        fun onEdit(reminder: Reminder)
        fun onDelete(reminder: Reminder)
    }

    private val items = mutableListOf<Reminder>()

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
        private val tvEmoji: TextView = view.findViewById(R.id.tvEmoji)
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvSubtitle: TextView = view.findViewById(R.id.tvSubtitle)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)
        private val switchEnabled: SwitchCompat = view.findViewById(R.id.switchEnabled)
        private val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        private val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)

        fun bind(r: Reminder) {
            tvEmoji.text = r.type.emoji
            tvTitle.text = r.title
            
            var sub = r.type.label
            if (r.type == ReminderType.MEAL && r.dishType.isNotBlank()) {
                sub += " • ${r.dishType}"
            }
            tvSubtitle.text = sub

            tvTime.text = if (r.isIntervalBased) {
                "Every ${r.intervalMinutes} min"
            } else {
                val days = buildDaysLabel(r.repeatDays)
                "%02d:%02d  %s".format(r.hour, r.minute, days)
            }

            itemView.alpha = if (r.isEnabled) 1f else 0.45f

            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.isChecked = r.isEnabled
            switchEnabled.setOnCheckedChangeListener { _, _ -> listener.onToggle(r) }

            btnEdit.setOnClickListener { listener.onEdit(r) }
            btnDelete.setOnClickListener { listener.onDelete(r) }
            itemView.setOnClickListener { listener.onEdit(r) }
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
