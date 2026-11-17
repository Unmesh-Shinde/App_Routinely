package com.dailyroutine.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Interactive square crop preview; the circular mask matches the profile photo display. */
class AvatarCropView(context: Context, private val source: Bitmap) : View(context) {

    private val imageMatrix = Matrix()
    private val imageBounds = RectF()
    private val cropBounds = RectF()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val outsidePath = Path()
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isScaling = false
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val values = FloatArray(9)
            imageMatrix.getValues(values)
            val currentScale = values[Matrix.MSCALE_X]
            val minimumScale = max(cropBounds.width() / source.width, cropBounds.height() / source.height)
            val nextScale = (currentScale * detector.scaleFactor).coerceIn(minimumScale, minimumScale * 8f)
            val scaleFactor = nextScale / currentScale
            imageMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            constrainImage()
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    })

    init {
        setBackgroundColor(Color.rgb(30, 30, 38))
        isClickable = true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val cropSize = min(width, height) * 0.9f
        val left = (width - cropSize) / 2f
        val top = (height - cropSize) / 2f
        cropBounds.set(left, top, left + cropSize, top + cropSize)

        val scale = max(cropSize / source.width, cropSize / source.height)
        imageMatrix.setScale(scale, scale)
        imageMatrix.postTranslate(
            cropBounds.centerX() - source.width * scale / 2f,
            cropBounds.centerY() - source.height * scale / 2f
        )
        constrainImage()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(source, imageMatrix, imagePaint)
        outsidePath.reset()
        outsidePath.fillType = Path.FillType.EVEN_ODD
        outsidePath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        outsidePath.addCircle(cropBounds.centerX(), cropBounds.centerY(), cropBounds.width() / 2f, Path.Direction.CW)
        canvas.drawPath(outsidePath, shadePaint)
        canvas.drawCircle(cropBounds.centerX(), cropBounds.centerY(), cropBounds.width() / 2f, outlinePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isScaling && event.pointerCount == 1) {
                    imageMatrix.postTranslate(event.x - lastTouchX, event.y - lastTouchY)
                    constrainImage()
                    invalidate()
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isScaling = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    fun createCroppedBitmap(): Bitmap {
        val inverse = Matrix()
        check(imageMatrix.invert(inverse)) { "Unable to crop this image." }
        val sourceCrop = RectF(cropBounds)
        inverse.mapRect(sourceCrop)
        val left = sourceCrop.left.toInt().coerceIn(0, source.width - 1)
        val top = sourceCrop.top.toInt().coerceIn(0, source.height - 1)
        val size = min(
            min(sourceCrop.right.toInt(), source.width) - left,
            min(sourceCrop.bottom.toInt(), source.height) - top
        ).coerceAtLeast(1)
        return Bitmap.createBitmap(source, left, top, size, size)
    }

    private fun constrainImage() {
        imageBounds.set(0f, 0f, source.width.toFloat(), source.height.toFloat())
        imageMatrix.mapRect(imageBounds)
        var dx = 0f
        var dy = 0f
        if (imageBounds.left > cropBounds.left) dx = cropBounds.left - imageBounds.left
        if (imageBounds.right < cropBounds.right) dx = cropBounds.right - imageBounds.right
        if (imageBounds.top > cropBounds.top) dy = cropBounds.top - imageBounds.top
        if (imageBounds.bottom < cropBounds.bottom) dy = cropBounds.bottom - imageBounds.bottom
        if (dx != 0f || dy != 0f) imageMatrix.postTranslate(dx, dy)
    }
}
