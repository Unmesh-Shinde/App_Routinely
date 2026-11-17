package com.dailyroutine.app

import android.app.Activity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.time.LocalDate

/** Shared calendar-strip layout and navigation for meal and workout planners. */
internal object PlannerDateStrip {
    fun render(
        activity: Activity,
        scrollView: HorizontalScrollView,
        container: LinearLayout,
        selectedDate: Date,
        dateFormatter: SimpleDateFormat,
        pastDays: Int,
        futureDays: Int,
        hasItems: (String) -> Boolean,
        isInTemplateRange: (String) -> Boolean,
        styleButton: (Button, Boolean, Boolean, Boolean, Boolean) -> Unit,
        onDateSelected: (String) -> Unit
    ) {
        container.removeAllViews()
        val tempCal = Calendar.getInstance().apply {
            time = selectedDate
            add(Calendar.DAY_OF_YEAR, -pastDays)
        }
        val stripDateFormatter = SimpleDateFormat("EEE\ndd", Locale.US)
        val todayStr = dateFormatter.format(Date())
        val selectedDateStr = dateFormatter.format(selectedDate)
        var selectedButton: Button? = null

        repeat(pastDays + futureDays + 1) {
            val dateStr = dateFormatter.format(tempCal.time)
            val isSelected = dateStr == selectedDateStr
            val isToday = dateStr == todayStr
            val hasItemsForDate = hasItems(dateStr)
            val isApplied = isInTemplateRange(dateStr)
            val button = Button(activity, null, android.R.attr.buttonStyleSmall).apply {
                text = if (isToday) "Today\n${SimpleDateFormat("dd", Locale.US).format(tempCal.time)}" else stripDateFormatter.format(tempCal.time)
                setAllCaps(false)
                minWidth = (64 * activity.resources.displayMetrics.density).toInt()
                setPadding(10, 6, 10, 6)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    val margin = (4 * activity.resources.displayMetrics.density).toInt()
                    setMargins(margin, 0, margin, 0)
                }
                setOnClickListener { onDateSelected(dateStr) }
                alpha = 1f
            }
            styleButton(button, isSelected, isToday, isApplied, hasItemsForDate)
            if (isSelected) selectedButton = button
            container.addView(button)
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        selectedButton?.let { button ->
            scrollView.post {
                val targetX = button.left - ((scrollView.width - button.width) / 2)
                scrollView.smoothScrollTo(targetX.coerceAtLeast(0), 0)
            }
        }
    }
}

internal object PlannerTemplateDateUtils {
    fun daysBetweenInclusive(startDate: String, endDate: String): Int = runCatching {
        (LocalDate.parse(endDate).toEpochDay() - LocalDate.parse(startDate).toEpochDay()).toInt() + 1
    }.getOrDefault(0)

    fun addDaysToDate(date: String, days: Int): String =
        LocalDate.parse(date).plusDays(days.toLong()).toString()

    fun buildDateRangePreview(startDate: String, dayCount: Int): String {
        if (dayCount <= 0) return startDate
        if (dayCount <= 5) {
            return (0 until dayCount).joinToString(", ") { addDaysToDate(startDate, it) }
        }
        return "${addDaysToDate(startDate, 0)}, ${addDaysToDate(startDate, 1)}, ... ${addDaysToDate(startDate, dayCount - 1)}"
    }
}
