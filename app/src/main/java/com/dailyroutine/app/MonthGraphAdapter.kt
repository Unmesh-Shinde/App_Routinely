package com.dailyroutine.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

data class MonthData(
    val monthName: String,
    val year: String,
    val barValues: List<BarItem>
)

data class BarData(
    val label: String,
    val date: String,
    val valueDisplay: String,
    val heightPx: Int,
    val color: Int,
    val backgroundRes: Int? = null
)

// Simplified Bar Item for the Horizontal Layout
data class BarItem(val data: BarData)

class MonthGraphAdapter(private val items: List<MonthData>) : RecyclerView.Adapter<MonthGraphAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMonth: TextView = view.findViewById(R.id.tvMonthName)
        private val tvYear: TextView = view.findViewById(R.id.tvYearName)
        private val llWeeks: LinearLayout = view.findViewById(R.id.llWeeksContainer)

        fun bind(m: MonthData) {
            tvMonth.text = m.monthName
            tvYear.text = m.year
            llWeeks.removeAllViews()

            m.barValues.forEach { bar ->
                val barView = LayoutInflater.from(itemView.context).inflate(R.layout.item_calorie_bar, llWeeks, false)
                barView.findViewById<TextView>(R.id.tvBarLabel).text = bar.data.label
                barView.findViewById<TextView>(R.id.tvBarDate).text = bar.data.date
                barView.findViewById<TextView>(R.id.tvBarValue).text = bar.data.valueDisplay
                
                val viewBar = barView.findViewById<View>(R.id.viewBar)
                if (bar.data.backgroundRes != null) {
                    viewBar.setBackgroundResource(bar.data.backgroundRes)
                } else {
                    viewBar.setBackgroundColor(bar.data.color)
                }

                val params = viewBar.layoutParams as LinearLayout.LayoutParams
                params.height = bar.data.heightPx
                viewBar.layoutParams = params

                // Set weight to distribute evenly
                val containerParams = barView.layoutParams as LinearLayout.LayoutParams
                containerParams.width = 0
                containerParams.weight = 1.0f
                barView.layoutParams = containerParams

                llWeeks.addView(barView)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_month_page, parent, false)
        // Ensure item fills the screen
        view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
}
