package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WellnessWidgetContractTest {

    @Test
    fun wellnessWidget_usesRelativeLayoutRootForEdgeAwareness() {
        val layout = readResourceText("layout/widget_wellness.xml")
        assertTrue("Widget root should be RelativeLayout for boundary awareness", 
            layout.contains("<RelativeLayout"))
    }

    @Test
    fun wellnessWidget_hasHeroCircularProgressAndWaterControls() {
        val layout = readResourceText("layout/widget_wellness.xml")
        
        assertTrue(layout.contains("android:id=\"@+id/pbWidgetSteps\""))
        assertTrue(layout.contains("android:progressDrawable=\"@drawable/bg_widget_progress_circle\""))
        
        assertTrue(layout.contains("android:id=\"@+id/llWaterSection\""))
        assertTrue(layout.contains("android:layout_alignParentBottom=\"true\""))
        
        // Symmetry check: The [-] Value [+] should be inside a centered container
        assertTrue(layout.contains("android:id=\"@+id/llWaterControls\""))
        assertTrue(layout.contains("android:layout_centerInParent=\"true\""))

        assertTrue(layout.contains("android:id=\"@+id/btnWidgetRemoveWater\""))
        assertTrue(layout.contains("android:id=\"@+id/btnWidgetAddWater\""))
        assertTrue(layout.contains("android:id=\"@+id/tvWidgetWater\""))
    }

    @Test
    fun wellnessWidget_hasFixedSizingAndNoResizeMode() {
        val info = readResourceText("xml/wellness_widget_info.xml")
        
        assertTrue(info.contains("android:resizeMode=\"horizontal|vertical\""))
        assertTrue(info.contains("android:minHeight=\"110dp\""))
    }

    private fun readResourceText(relativeResPath: String): String {
        val file = sequenceOf(
            File("src/main/res/$relativeResPath"),
            File("app/src/main/res/$relativeResPath")
        ).firstOrNull { it.exists() } ?: error("Could not find resource $relativeResPath")
        return file.readText()
    }
}
