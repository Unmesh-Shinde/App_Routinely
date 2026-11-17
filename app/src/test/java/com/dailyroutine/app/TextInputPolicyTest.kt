package com.dailyroutine.app

import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TextInputPolicyTest {
    @Test fun rejectsEmptyRequiredNumericOnlyAndSymbolOnlyTitles() {
        assertEquals("Meal name is required", TextInputPolicy.error("  ", true, "Meal name", 80))
        assertEquals("Meal name must include at least one letter", TextInputPolicy.error("12345", true, "Meal name", 80))
        assertEquals("Meal name contains unsupported characters", TextInputPolicy.error("😀", true, "Meal name", 80))
    }

    @Test fun acceptsMixedNumbersUnicodeAndApprovedPunctuation() {
        listOf("5K Run", "B12 breakfast", "Mom’s lunch", "Push-up", "Mobility / Yoga", "Fish & rice", "Café 2").forEach {
            assertNull("Expected to accept: $it", TextInputPolicy.error(it, true, "Meal name", 80))
        }
    }

    @Test fun rejectsUnsupportedControlsConsecutivePunctuationAndExcessLength() {
        assertEquals("Meal name contains unsupported characters", TextInputPolicy.error("Run\nfast", true, "Meal name", 80))
        assertEquals("Meal name cannot contain consecutive punctuation", TextInputPolicy.error("Run--fast", true, "Meal name", 80))
        assertEquals("Meal name must be 3 characters or fewer", TextInputPolicy.error("Four", true, "Meal name", 3))
    }

    @Test fun optionalBlankDescriptionIsValidButNumericOnlyDescriptionIsNot() {
        assertNull(TextInputPolicy.error("  ", false, "Meal details", 300))
        assertEquals("Meal details must include at least one letter", TextInputPolicy.error("2026", false, "Meal details", 300))
    }

    @Test fun mealDescription_allowsUpToFivePercentSignsAndInputFilterCapsTypingAndPaste() {
        val fivePercentSigns = "1% oats, 2% milk, 3% fruit, 4% seeds, 5% honey"
        assertNull(TextInputPolicy.error(fivePercentSigns, false, "Meal details", 300, allowPercent = true))
        assertEquals(
            "Meal details can contain at most 5 percent signs",
            TextInputPolicy.error("oats 1% milk 2% fruit 3% seeds 4% honey 5% 6%", false, "Meal details", 300, allowPercent = true)
        )
        assertEquals("Meal details contains unsupported characters", TextInputPolicy.error("oats 1%", false, "Meal details", 300))

        val field = EditText(ApplicationProvider.getApplicationContext())
        TextInputPolicy.applyDescriptionLimit(field)
        field.setText("oats 1% milk 2% fruit 3% seeds 4% honey 5% 6%")
        assertEquals(5, TextInputPolicy.percentCount(field.text))

        field.setText("oats 1% milk 2% fruit 3% seeds 4% honey")
        field.append(" 5%")
        assertEquals(5, TextInputPolicy.percentCount(field.text))
    }

    @Test fun mealDescription_replacingAnExistingPercentSignFreesItsSlot() {
        val field = EditText(ApplicationProvider.getApplicationContext())
        TextInputPolicy.applyDescriptionLimit(field)
        field.setText("1% 2% 3% 4% 5%")
        val firstPercent = field.text.indexOf('%')

        field.text.replace(firstPercent, firstPercent + 1, " percent")
        assertEquals(4, TextInputPolicy.percentCount(field.text))
        field.text.insert(firstPercent, "%")
        assertEquals(5, TextInputPolicy.percentCount(field.text))
    }

    @Test fun mealDescription_warningAppearsAtFiveSignsAndHidesBelowTheLimit() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val field = EditText(context)
        val warning = TextView(context)
        TextInputPolicy.applyDescriptionLimit(field)
        TextInputPolicy.bindPercentLimitWarning(field, warning)

        assertEquals(android.view.View.GONE, warning.visibility)
        field.setText("1% 2% 3% 4%")
        assertEquals(android.view.View.GONE, warning.visibility)
        field.append(" 5%")
        assertEquals(android.view.View.VISIBLE, warning.visibility)
        field.text.delete(field.text.indexOf('%'), field.text.indexOf('%') + 1)
        assertEquals(android.view.View.GONE, warning.visibility)
    }
}
