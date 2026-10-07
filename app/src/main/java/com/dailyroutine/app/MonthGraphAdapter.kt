package com.dailyroutine.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
    val secondaryHeightPx: Int = 0,
    val secondaryColor: Int? = null,
    val secondaryBackgroundRes: Int? = null,
    val isSecondaryStacked: Boolean = false,
    val secondaryBottomHeightPx: Int = 0,
    val secondaryBottomColor: Int? = null,
    val secondaryTopHeightPx: Int = 0,
    val secondaryTopBackgroundRes: Int? = null,
    val isEmpty: Boolean = false
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

                val tvEmpty = barView.findViewById<TextView>(R.id.tvEmptyError)

                if (bar.data.isDoubleBar) {
                    val tvSteps = barView.findViewById<TextView>(R.id.tvStepValue)
                    val tvHP = barView.findViewById<TextView>(R.id.tvHeartValue)
                    val barContainer = barView.findViewById<View>(R.id.llBarContainer)
                    
                    if (bar.data.isEmpty) {
                        tvEmpty?.visibility = View.VISIBLE
                        barContainer?.visibility = View.INVISIBLE
                        tvSteps?.visibility = View.INVISIBLE
                        tvHP?.visibility = View.INVISIBLE
                    } else {
                        tvEmpty?.visibility = View.GONE
                        barContainer?.visibility = View.VISIBLE
                        tvSteps?.visibility = View.VISIBLE
                        tvHP?.visibility = View.VISIBLE
                        tvSteps?.text = bar.data.valueDisplay
                        tvHP?.text = bar.data.secondaryValueDisplay ?: "-"
                    }

                    val vStep = barView.findViewById<View>(R.id.viewStepBar)
                    val vHeart = barView.findViewById<View>(R.id.viewHeartBar)

                    tvSteps.setTextColor(bar.data.color)
                    bar.data.secondaryColor?.let { tvHP.setTextColor(it) }

                    if (bar.data.backgroundRes != null) {
                        vStep.setBackgroundResource(bar.data.backgroundRes)
                        vStep.clipToOutline = true
                    } else {
                        vStep.setBackgroundColor(bar.data.color)
                    }

                    if (bar.data.isSecondaryStacked) {
                        vHeart.setBackgroundResource(0)
                        val vBottom = barView.findViewById<View>(R.id.viewBurnedBmr)
                        val vTop = barView.findViewById<View>(R.id.viewBurnedActive)
                        bar.data.secondaryBottomColor?.let { vBottom.setBackgroundColor(it) }
                        bar.data.secondaryTopBackgroundRes?.let { vTop.setBackgroundResource(it) }
                        vBottom.layoutParams.height = bar.data.secondaryBottomHeightPx
                        vTop.layoutParams.height = bar.data.secondaryTopHeightPx
                    } else {
                        if (bar.data.secondaryBackgroundRes != null) {
                            vHeart.setBackgroundResource(bar.data.secondaryBackgroundRes)
                            vHeart.clipToOutline = true
                        } else if (bar.data.secondaryColor != null) {
                            vHeart.setBackgroundColor(bar.data.secondaryColor)
                        }
                    }

                    vStep.layoutParams.height = bar.data.heightPx
                    vHeart.layoutParams.height = bar.data.secondaryHeightPx
                } else {
                    val tvValue = barView.findViewById<TextView>(R.id.tvBarValue)
                    val viewBar = barView.findViewById<View>(R.id.viewBar)
                    
                    if (bar.data.isEmpty) {
                        tvEmpty?.visibility = View.VISIBLE
                        viewBar?.visibility = View.INVISIBLE
                        tvValue?.visibility = View.INVISIBLE
                    } else {
                        tvEmpty?.visibility = View.GONE
                        viewBar?.visibility = View.VISIBLE
                        tvValue?.visibility = View.VISIBLE
                        tvValue?.text = bar.data.valueDisplay
                    }

                    if (bar.data.backgroundRes != null) {
                        viewBar.setBackgroundResource(bar.data.backgroundRes)
                        viewBar.clipToOutline = true
                    } else {
                        viewBar.setBackgroundColor(bar.data.color)
                    }
                    viewBar.layoutParams.height = bar.data.heightPx
                }

                // Set weight to distribute evenly, but allow for overflow if more than 5 weeks
                val containerParams = barView.layoutParams as LinearLayout.LayoutParams
                containerParams.width = 0
                containerParams.weight = 1.0f
                barView.layoutParams = containerParams
                if (barView is ViewGroup) {
                    barView.clipChildren = false
                    barView.clipToPadding = false
                }

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
