package com.dailyroutine.app

import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsPinVisibilityTest {

    @Test
    fun pinInput_startsMaskedAndEyeRevealsThenMasksItAgain() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val createPinInput = SettingsActivity::class.java
            .getDeclaredMethod("createPinInput", String::class.java)
            .apply { isAccessible = true }
        val input = createPinInput.invoke(activity, "Enter PIN")
        assertNotNull(input)

        val inputClass = input!!.javaClass
        val row = inputClass.getMethod("getRow").invoke(input) as LinearLayout
        val editText = inputClass.getMethod("getEditText").invoke(input) as EditText
        val eyeButton = row.getChildAt(1) as ImageButton

        assertEquals(
            InputType.TYPE_CLASS_NUMBER,
            editText.inputType
        )
        assertNotNull(editText.transformationMethod)
        assertEquals("Show PIN", eyeButton.contentDescription)

        editText.setText("123456")
        assertEquals("••••••", maskedText(editText))
        eyeButton.performClick()

        assertEquals(
            InputType.TYPE_CLASS_NUMBER,
            editText.inputType
        )
        assertEquals("123456", editText.text.toString())
        assertEquals("Hide PIN", eyeButton.contentDescription)
        assertEquals(null, editText.transformationMethod)

        eyeButton.performClick()

        assertEquals(
            InputType.TYPE_CLASS_NUMBER,
            editText.inputType
        )
        assertEquals("123456", editText.text.toString())
        assertEquals("Show PIN", eyeButton.contentDescription)
        assertEquals("••••••", maskedText(editText))
        assertTrue(eyeButton.drawable != null)
    }

    private fun maskedText(editText: EditText): String =
        editText.transformationMethod!!.getTransformation(editText.text, editText).toString()
}
