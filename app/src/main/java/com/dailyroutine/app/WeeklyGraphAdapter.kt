package com.dailyroutine.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WeeklyGraphAdapter : RecyclerView.Adapter<WeeklyGraphAdapter.VH>() {

    private var items = listOf<WeeklyGraphItem>()

    fun submitList(list: List<WeeklyGraphItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        // If viewType == 1, it's a divider.
        if (viewType == TYPE_DIVIDER) {
            val v = View(parent.context).apply {
                val dp3 = (3 * parent.context.resources.displayMetrics.density).toInt()
                val dp180 = (180 * parent.context.resources.displayMetrics.density).toInt()
                val dp16 = (16 * parent.context.resources.displayMetrics.density).toInt()
                val dp40 = (40 * parent.context.resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(dp3, dp180).apply {
                    setMargins(dp16, 0, dp16, dp40)
                }
                setBackgroundColor(0xFF455A64.toInt())
            }
            // Use a dummy VH for divider
            return VH(LinearLayout(parent.context).apply { 
                addView(v)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = android.view.Gravity.BOTTOM
            })
        }
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_step_heart_bar, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        if (item.isDivider) return

        holder.tvBarLabel?.text = item.dayLabel
        holder.tvBarDate?.text = item.dateLabel
        
        val tvPrimary = holder.itemView.findViewById<TextView>(R.id.tvStepValue)
        val tvSecondary = holder.itemView.findViewById<TextView>(R.id.tvHeartValue)
        val tvEmpty = holder.itemView.findViewById<TextView>(R.id.tvEmptyError)
        val barContainer = holder.itemView.findViewById<View>(R.id.llBarContainer)

        if (item.hasSignal) {
            tvPrimary.text = item.primaryValue
            tvPrimary.setTextColor(item.primaryColor)
            tvPrimary.visibility = View.VISIBLE
            
            if (item.secondaryValue != null) {
                tvSecondary.text = item.secondaryValue
                tvSecondary.setTextColor(item.secondaryColor)
                tvSecondary.visibility = View.VISIBLE
            } else {
                tvSecondary.visibility = View.GONE
            }
            
            tvEmpty.visibility = View.GONE
            barContainer.visibility = View.VISIBLE
        } else {
            tvPrimary.visibility = View.INVISIBLE
            tvSecondary.visibility = View.INVISIBLE
            tvEmpty.visibility = View.VISIBLE
            barContainer.visibility = View.INVISIBLE
        }

        val viewPrimaryBar = holder.itemView.findViewById<View>(R.id.viewStepBar)
        val viewSecondaryBar = holder.itemView.findViewById<View>(R.id.viewHeartBar)
        
        // RecyclerView reuses these views. Reassign LayoutParams (rather than only
        // mutating the existing instance) so each item's measured height is applied.
        viewPrimaryBar.layoutParams = viewPrimaryBar.layoutParams.apply {
            height = item.primaryHeightPx
        }
        viewPrimaryBar.requestLayout()
        viewPrimaryBar.clipToOutline = true
        if (item.primaryBackgroundRes != 0) {
            viewPrimaryBar.setBackgroundResource(item.primaryBackgroundRes)
        } else {
            viewPrimaryBar.setBackgroundColor(item.primaryColor)
        }
        
        if ((item.showSecondaryBar && item.secondaryValue != null) || item.isSecondaryStacked) {
            viewSecondaryBar.visibility = View.VISIBLE
            viewSecondaryBar.layoutParams = viewSecondaryBar.layoutParams.apply {
                height = item.secondaryHeightPx
            }
            viewSecondaryBar.requestLayout()
            
            if (item.isSecondaryStacked) {
                if (item.secondaryBackgroundRes != 0) {
                    viewSecondaryBar.setBackgroundResource(item.secondaryBackgroundRes)
                } else {
                    viewSecondaryBar.setBackgroundResource(0)
                }
                viewSecondaryBar.clipToOutline = true
                
                val viewBmr = holder.itemView.findViewById<View>(R.id.viewBurnedBmr)
                val viewActive = holder.itemView.findViewById<View>(R.id.viewBurnedActive)
                
                viewBmr.setBackgroundColor(item.secondaryBottomColor)
                viewBmr.layoutParams = viewBmr.layoutParams.apply {
                    height = item.secondaryBottomHeightPx
                }
                viewBmr.requestLayout()
                
                viewActive.setBackgroundColor(item.secondaryTopColor)
                viewActive.layoutParams = viewActive.layoutParams.apply {
                    height = item.secondaryTopHeightPx
                }
                viewActive.requestLayout()
            } else {
                if (item.secondaryBackgroundRes != 0) {
                    viewSecondaryBar.setBackgroundResource(item.secondaryBackgroundRes)
                } else {
                    viewSecondaryBar.setBackgroundColor(item.secondaryColor)
                }
                // Ensure stacked views are hidden if not used
                holder.itemView.findViewById<View>(R.id.viewBurnedBmr).apply {
                    layoutParams = layoutParams.apply { height = 0 }
                    requestLayout()
                }
                holder.itemView.findViewById<View>(R.id.viewBurnedActive).apply {
                    layoutParams = layoutParams.apply { height = 0 }
                    requestLayout()
                }
            }
        } else {
            viewSecondaryBar.visibility = View.GONE
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].isDivider) TYPE_DIVIDER else TYPE_ITEM
    }

    override fun getItemCount() = items.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvBarLabel: TextView? = v.findViewById(R.id.tvBarLabel)
        val tvBarDate: TextView? = v.findViewById(R.id.tvBarDate)
    }
    
    private companion object {
        const val TYPE_ITEM = 0
        const val TYPE_DIVIDER = 1
    }
}
