package com.dailyroutine.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class MonthData(
    val monthName: String,
    val year: String,
    val barValues: List<BarItem>,
    val goalLines: List<GoalLineSpec> = emptyList()
)

data class BarData(
    val label: String,
    val date: String,
    val valueDisplay: String,
    val heightPx: Int,
    val color: Int,
    val backgroundRes: Int? = null,
    val isDoubleBar: Boolean = false,
    val secondaryValueDisplay: String? = null,
    val secondaryHeightPx: Int = 0
)

// Simplified Bar Item for the Horizontal Layout
data class BarItem(val data: BarData)

class MonthGraphAdapter(private val items: List<MonthData>) : RecyclerView.Adapter<MonthGraphAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMonth: TextView = view.findViewById(R.id.tvMonthName)
        private val tvYear: TextView = view.findViewById(R.id.tvYearName)
        private val llWeeks: LinearLayout = view.findViewById(R.id.llWeeksContainer)
        private val goalOverlay: GoalLineOverlayView = view.findViewById(R.id.monthGoalOverlay)

        fun bind(m: MonthData) {
            tvMonth.text = m.monthName
            tvYear.text = m.year
            llWeeks.removeAllViews()
            goalOverlay.setGoalLines(m.goalLines)

            m.barValues.forEach { bar ->
                val barLayoutRes = if (bar.data.isDoubleBar) R.layout.item_step_heart_bar else R.layout.item_calorie_bar
                val barView = LayoutInflater.from(itemView.context).inflate(barLayoutRes, llWeeks, false)

                barView.findViewById<TextView>(R.id.tvBarLabel).text = bar.data.label
                barView.findViewById<TextView>(R.id.tvBarDate).text = bar.data.date

                if (bar.data.isDoubleBar) {
                    val tvSteps = barView.findViewById<TextView>(R.id.tvStepValue)
                    val tvHP = barView.findViewById<TextView>(R.id.tvHeartValue)
                    tvSteps.text = bar.data.valueDisplay
                    tvHP.text = bar.data.secondaryValueDisplay ?: "-"

                    val vStep = barView.findViewById<View>(R.id.viewStepBar)
                    val vHeart = barView.findViewById<View>(R.id.viewHeartBar)

                    val sParams = vStep.layoutParams as LinearLayout.LayoutParams
                    sParams.height = bar.data.heightPx
                    vStep.layoutParams = sParams

                    val hParams = vHeart.layoutParams as LinearLayout.LayoutParams
                    hParams.height = bar.data.secondaryHeightPx
                    vHeart.layoutParams = hParams
                } else {
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
                }

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
