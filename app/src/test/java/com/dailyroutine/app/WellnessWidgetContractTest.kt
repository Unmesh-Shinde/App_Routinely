package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream

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

    @Test
    fun wellnessWidgetPicker_usesLiveLayoutAndHighResolutionStaticPreview() {
        val info = readResourceText("xml/wellness_widget_info.xml")
        assertTrue(info.contains("android:previewImage=\"@drawable/widget_wellness_preview\""))
        assertTrue(info.contains("android:previewLayout=\"@layout/widget_wellness\""))

        val previewFile = findResourceFile("drawable-nodpi/widget_wellness_preview.png")
        DataInputStream(FileInputStream(previewFile)).use { input ->
            val signature = ByteArray(8)
            input.readFully(signature)
            assertTrue("Static widget preview should be a PNG", signature.contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            ))

            assertEquals("First PNG chunk should be 13 bytes", 13, input.readInt())
            val chunkType = ByteArray(4)
            input.readFully(chunkType)
            assertEquals("First PNG chunk should be IHDR", "IHDR", String(chunkType, Charsets.US_ASCII))
            val width = input.readInt()
            val height = input.readInt()
            assertEquals("Preview should remain square", width, height)
            assertTrue("Preview should have enough resolution for launcher scaling", width >= 900)
        }
    }

    private fun readResourceText(relativeResPath: String): String {
        return findResourceFile(relativeResPath).readText()
    }

    private fun findResourceFile(relativeResPath: String): File {
        return sequenceOf(
            File("src/main/res/$relativeResPath"),
            File("app/src/main/res/$relativeResPath")
        ).firstOrNull { it.exists() } ?: error("Could not find resource $relativeResPath")
    }
}
