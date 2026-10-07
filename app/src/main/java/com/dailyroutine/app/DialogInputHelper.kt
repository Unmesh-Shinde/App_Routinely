package com.dailyroutine.app

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView

object DialogInputHelper {

    fun hideKeyboard(view: View) {
        val inputMethodManager = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    fun hideKeyboardOnDone(vararg editTexts: EditText) {
        editTexts.forEach { editText ->
            editText.imeOptions = (editText.imeOptions and EditorInfo.IME_MASK_ACTION.inv()) or EditorInfo.IME_ACTION_DONE
            editText.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard(v)
                    true
                } else {
                    false
                }
            }
        }
    }

    fun makeTimeLabelClickable(textView: TextView, description: String, onClick: () -> Unit) {
        textView.isClickable = true
        textView.isFocusable = true
        textView.contentDescription = description
        textView.setOnClickListener {
            hideKeyboard(textView)
            onClick()
        }
    }
}


