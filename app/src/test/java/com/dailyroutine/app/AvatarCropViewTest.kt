package com.dailyroutine.app

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AvatarCropViewTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun createCroppedBitmap_returnsSquareCropForLandscapeSource() {
        assertProducesSquareCrop(Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888))
    }

    @Test
    fun createCroppedBitmap_returnsSquareCropForPortraitSource() {
        assertProducesSquareCrop(Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888))
    }

    private fun assertProducesSquareCrop(source: Bitmap) {
        val cropView = AvatarCropView(context, source)
        cropView.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY)
        )
        cropView.layout(0, 0, 320, 240)

        val cropped = cropView.createCroppedBitmap()
        assertTrue(cropped.width > 0)
        assertEquals(cropped.width, cropped.height)
    }
}
