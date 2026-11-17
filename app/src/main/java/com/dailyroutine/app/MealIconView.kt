package com.dailyroutine.app

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes

class MealIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    /**
     * Compatibility method to set a single image resource ID.
     */
    fun setImageResource(@DrawableRes resId: Int) {
        if (resId != 0) {
            setIconResList(listOf(resId))
        }
    }

    /**
     * Binds up to 3 icon resource IDs (1, 2-in-1, or 3-in-1 combo).
     * @param icons List of drawable resource IDs.
     * @param badgeBgRes Optional background badge drawable resource ID (e.g. bg_circle_diet).
     */
    fun setIconResList(icons: List<Int>, @DrawableRes badgeBgRes: Int? = null) {
        removeAllViews()

        if (badgeBgRes != null && badgeBgRes != 0) {
            setBackgroundResource(badgeBgRes)
        }

        if (icons.isEmpty()) {
            return
        }

        if (icons.size <= 1) {
            // Single Icon Mode (Prominent 56dp badge with 5dp padding for clarity)
            val iv = ImageView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                    gravity = Gravity.CENTER
                }
                val paddingPx = dpToPx(5f)
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                setImageResource(icons[0])
                contentDescription = "Meal Icon"
            }
            addView(iv)
            return
        }

        if (icons.size == 2) {
            // 2-in-1 Diagonal Overlapped Mode (32dp sub-badges)
            val subSize = dpToPx(32f)
            val paddingPx = dpToPx(2.5f)

            // Sub Icon 2 (Secondary item, e.g. Coffee/Lassi - rendered in background first)
            val iv2 = ImageView(context).apply {
                layoutParams = LayoutParams(subSize, subSize).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    marginEnd = dpToPx(2f)
                    bottomMargin = dpToPx(2f)
                }
                setBackgroundResource(R.drawable.bg_meal_sub_icon_badge)
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                setImageResource(icons[1])
                contentDescription = "Secondary Meal Icon"
            }

            // Sub Icon 1 (Primary item, e.g. Paratha/Chicken - rendered in foreground second)
            val iv1 = ImageView(context).apply {
                layoutParams = LayoutParams(subSize, subSize).apply {
                    gravity = Gravity.TOP or Gravity.START
                    marginStart = dpToPx(2f)
                    topMargin = dpToPx(2f)
                }
                setBackgroundResource(R.drawable.bg_meal_sub_icon_badge)
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                setImageResource(icons[0])
                contentDescription = "Primary Meal Icon"
            }

            // Add secondary first (behind), primary second (in front)
            addView(iv2)
            addView(iv1)
            return
        }

        // 3-in-1 Triad Mode (size >= 3)
        val subSize = dpToPx(28f)
        val paddingPx = dpToPx(2f)

        // Sub Icon 1 (Top-Left)
        val iv1 = ImageView(context).apply {
            layoutParams = LayoutParams(subSize, subSize).apply {
                gravity = Gravity.TOP or Gravity.START
                marginStart = dpToPx(2f)
                topMargin = dpToPx(2f)
            }
            setBackgroundResource(R.drawable.bg_meal_sub_icon_badge)
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            setImageResource(icons[0])
            contentDescription = "Meal Icon 1"
        }

        // Sub Icon 2 (Top-Right)
        val iv2 = ImageView(context).apply {
            layoutParams = LayoutParams(subSize, subSize).apply {
                gravity = Gravity.TOP or Gravity.START
                marginStart = dpToPx(24f)
                topMargin = dpToPx(2f)
            }
            setBackgroundResource(R.drawable.bg_meal_sub_icon_badge)
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            setImageResource(icons[1])
            contentDescription = "Meal Icon 2"
        }

        // Sub Icon 3 (Bottom-Center)
        val iv3 = ImageView(context).apply {
            layoutParams = LayoutParams(subSize, subSize).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                marginStart = dpToPx(13f)
                bottomMargin = dpToPx(2f)
            }
            setBackgroundResource(R.drawable.bg_meal_sub_icon_badge)
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            setImageResource(icons[2])
            contentDescription = "Meal Icon 3"
        }

        addView(iv1)
        addView(iv2)
        addView(iv3)
    }
}
