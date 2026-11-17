package com.dailyroutine.app

import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.TextView

/** Shared policy for user-authored names, labels, and descriptions. */
internal object TextInputPolicy {
    const val TITLE_LIMIT = 80
    const val DESCRIPTION_LIMIT = 300
    const val DESCRIPTION_PERCENT_LIMIT = 5
    private val allowedPunctuation = setOf('\'', '’', '-', '–', ',', '.', '(', ')', '/', '&', '+', ':')

    fun applyLimit(field: TextView, limit: Int) {
        field.filters = field.filters.filterNot { it is InputFilter.LengthFilter }.toTypedArray() + InputFilter.LengthFilter(limit)
    }

    fun applyDescriptionLimit(field: TextView) {
        applyLimit(field, DESCRIPTION_LIMIT)
        field.filters = field.filters.filterNot { it is PercentCountFilter }.toTypedArray() + PercentCountFilter(DESCRIPTION_PERCENT_LIMIT)
    }

    fun percentCount(value: CharSequence): Int = value.count { it == '%' }

    fun bindPercentLimitWarning(field: TextView, warning: View) {
        fun updateVisibility() {
            warning.visibility = if (percentCount(field.text) >= DESCRIPTION_PERCENT_LIMIT) View.VISIBLE else View.GONE
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateVisibility()
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        updateVisibility()
    }

    /** Requires at least one Unicode letter and rejects controls and unsupported symbols. */
    fun error(value: String, required: Boolean, label: String, limit: Int, allowPercent: Boolean = false): String? {
        val text = value.trim()
        if (text.isEmpty()) return if (required) "$label is required" else null
        if (text.length > limit) return "$label must be $limit characters or fewer"
        if (allowPercent && percentCount(text) > DESCRIPTION_PERCENT_LIMIT) return "$label can contain at most $DESCRIPTION_PERCENT_LIMIT percent signs"
        if (text.any { ch -> ch.isISOControl() || !(ch.isLetterOrDigit() || ch.isWhitespace() || ch in allowedPunctuation || (allowPercent && ch == '%')) }) {
            return "$label contains unsupported characters"
        }
        if (text.none(Char::isLetter)) return "$label must include at least one letter"
        if (text.any { it in allowedPunctuation } && text.zipWithNext().any { (a, b) -> a in allowedPunctuation && b in allowedPunctuation }) {
            return "$label cannot contain consecutive punctuation"
        }
        return null
    }

    private class PercentCountFilter(private val limit: Int) : InputFilter {
        override fun filter(source: CharSequence, start: Int, end: Int, dest: android.text.Spanned, dstart: Int, dend: Int): CharSequence? {
            val existingCount = percentCount(dest.subSequence(0, dstart)) + percentCount(dest.subSequence(dend, dest.length))
            var remaining = (limit - existingCount).coerceAtLeast(0)
            val accepted = StringBuilder(end - start)
            for (index in start until end) {
                val char = source[index]
                if (char != '%' || remaining > 0) {
                    accepted.append(char)
                    if (char == '%') remaining--
                }
            }
            val result = accepted.toString()
            return if (result == source.subSequence(start, end).toString()) null else result
        }
    }
}
