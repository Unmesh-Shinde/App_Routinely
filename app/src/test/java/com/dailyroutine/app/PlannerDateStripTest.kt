package com.dailyroutine.app

import android.app.Activity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlannerDateStripTest {
    @Test
    fun render_buildsDateButtonsPassesDateStateAndSelectsClickedDate() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val scrollView = HorizontalScrollView(activity)
        val container = LinearLayout(activity)
        scrollView.addView(container)
        activity.setContentView(scrollView)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val selectedDate = formatter.parse("2026-06-15")!!
        val buttonStates = mutableListOf<Triple<Boolean, Boolean, Boolean>>()
        val clickedDates = mutableListOf<String>()

        PlannerDateStrip.render(
            activity = activity,
            scrollView = scrollView,
            container = container,
            selectedDate = selectedDate,
            dateFormatter = formatter,
            pastDays = 1,
            futureDays = 1,
            hasItems = { it == "2026-06-15" },
            isInTemplateRange = { it == "2026-06-14" },
            styleButton = { _, selected, _, applied, hasItems ->
                buttonStates += Triple(selected, applied, hasItems)
            },
            onDateSelected = clickedDates::add
        )

        assertEquals(3, container.childCount)
        assertEquals(
            listOf(Triple(false, true, false), Triple(true, false, true), Triple(false, false, false)),
            buttonStates
        )
        assertTrue(container.getChildAt(1).performClick())
        assertEquals(listOf("2026-06-15"), clickedDates)
    }
}
