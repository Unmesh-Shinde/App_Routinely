package com.dailyroutine.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate

internal data class TemplateDialogAction(
    val title: String,
    val description: String,
    val onClick: () -> Unit
)

internal fun AppCompatActivity.showTemplateActionDialog(
    title: String,
    message: String,
    actions: List<TemplateDialogAction>
) {
    val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(templateDp(20), templateDp(12), templateDp(20), templateDp(8))
    }
    container.addView(TextView(this).apply {
        text = message
        textSize = 13f
        setTextColor(if (isNightMode) Color.argb(220, 255, 255, 255) else Color.argb(210, 0, 0, 0))
        setPadding(0, 0, 0, templateDp(10))
    })

    var dialog: AlertDialog? = null
    actions.forEach { action ->
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(templateDp(14), templateDp(12), templateDp(14), templateDp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = templateDp(12).toFloat()
                setColor(if (isNightMode) 0xFF17351E.toInt() else 0xFFEAF7EA.toInt())
                setStroke(templateDp(1), if (isNightMode) 0xFF81C784.toInt() else 0xFF4CAF50.toInt())
            }
            setOnClickListener {
                dialog?.dismiss()
                action.onClick()
            }
        }
        row.addView(TextView(this).apply {
            text = action.title
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (isNightMode) Color.WHITE else 0xFF1B5E20.toInt())
        })
        row.addView(TextView(this).apply {
            text = action.description
            textSize = 12f
            setPadding(0, templateDp(3), 0, 0)
            setTextColor(if (isNightMode) Color.argb(220, 255, 255, 255) else 0xFF2E7D32.toInt())
        })
        container.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, templateDp(8)) })
    }

    dialog = AlertDialog.Builder(this)
        .setTitle(title)
        .setView(ScrollView(this).apply { addView(container) })
        .setNegativeButton("Close", null)
        .create()
    dialog.show()
}

private fun AppCompatActivity.templateDp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()

internal fun AppCompatActivity.showAppliedTemplateRangesDialog(
    title: String,
    emptyMessage: String,
    ranges: List<AppliedTemplateRange>,
    onRangeSelected: (AppliedTemplateRange) -> Unit
) {
    if (ranges.isEmpty()) {
        android.widget.Toast.makeText(this, emptyMessage, android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val labels = ranges.map { range ->
        "${range.templateName} • ${range.startDate} to ${range.endDate} • ${templateDaysBetweenInclusive(range.startDate, range.endDate)} day(s)"
    }.toTypedArray()
    AlertDialog.Builder(this)
        .setTitle(title)
        .setItems(labels) { _, which -> onRangeSelected(ranges[which]) }
        .setNegativeButton("Close", null)
        .show()
}

internal fun AppCompatActivity.showAppliedTemplateRangeActions(
    range: AppliedTemplateRange,
    onViewDetails: () -> Unit,
    onRemoveMarker: () -> Unit
) {
    AlertDialog.Builder(this)
        .setTitle(range.templateName)
        .setMessage("Applied from ${range.startDate} to ${range.endDate} • ${templateDaysBetweenInclusive(range.startDate, range.endDate)} day(s)")
        .setItems(arrayOf("View applied details", "Remove applied marker")) { _, which ->
            when (which) {
                0 -> onViewDetails()
                1 -> onRemoveMarker()
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun templateDaysBetweenInclusive(startDate: String, endDate: String): Int =
    (LocalDate.parse(endDate).toEpochDay() - LocalDate.parse(startDate).toEpochDay()).toInt() + 1
