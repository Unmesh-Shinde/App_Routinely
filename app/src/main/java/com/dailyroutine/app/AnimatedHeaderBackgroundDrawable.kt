package com.dailyroutine.app

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import kotlin.math.max

class AnimatedHeaderBackgroundDrawable private constructor(
    private val gradientColors: IntArray
) : Drawable(), Animatable {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePath = Path()
    private var startTime = 0L
    private var drawableAlpha = 255

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        backgroundPaint.shader = null
    }

    private val infiniteAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = IDLE_DURATION_MILLIS
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART // Always Restart to keep phase increasing linearly
        interpolator = LinearInterpolator()
        addUpdateListener {
            invalidateSelf()
        }
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.width() <= 0 || bounds.height() <= 0) return

        val elapsed = if (startTime > 0) android.os.SystemClock.uptimeMillis() - startTime else 0L
        val entranceProgress = (elapsed.toFloat() / ENTRANCE_DURATION_MILLIS).coerceIn(0f, 1f)
        val motionProgress = infiniteAnimator.animatedValue as Float

        if (backgroundPaint.shader == null) {
            backgroundPaint.shader = LinearGradient(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                gradientColors,
                null,
                Shader.TileMode.CLAMP
            )
        }
        backgroundPaint.alpha = drawableAlpha
        canvas.drawRect(bounds, backgroundPaint)

        drawMovingGlow(canvas, bounds, entranceProgress, motionProgress)
        
        // Use entranceProgress to grow height and opacity, while motionProgress handles flow
        val waveBaseAlpha = (255 * 0.12f).toInt()
        val dynamicAlpha = (waveBaseAlpha * entranceProgress).toInt()

        drawWave(canvas, bounds, baseRatio = 0.50f, amplitudeRatio = 0.06f * entranceProgress, alpha = dynamicAlpha, speed = 0.4f, progress = motionProgress)
        drawWave(canvas, bounds, baseRatio = 0.64f, amplitudeRatio = 0.08f * entranceProgress, alpha = (dynamicAlpha * 1.5).toInt().coerceAtMost(255), speed = 0.6f, progress = motionProgress)
        drawWave(canvas, bounds, baseRatio = 0.80f, amplitudeRatio = 0.05f * entranceProgress, alpha = dynamicAlpha, speed = 0.8f, progress = motionProgress)
    }

    private fun drawMovingGlow(canvas: Canvas, bounds: Rect, entranceProgress: Float, motionProgress: Float) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val radius = max(width, height) * 0.65f
        
        // Smooth transition for glow position
        val centerX = bounds.left + width * (0.85f + 0.10f * kotlin.math.sin(motionProgress * 2 * Math.PI).toFloat())
        val centerY = bounds.top + height * (0.22f + 0.15f * kotlin.math.sin(motionProgress * Math.PI).toFloat())
        
        val glowAlpha = (110 * entranceProgress).toInt()
        glowPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(Color.argb(glowAlpha, 255, 255, 255), Color.argb((glowAlpha * 0.3).toInt(), 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.alpha = drawableAlpha
        canvas.drawCircle(centerX, centerY, radius, glowPaint)
    }

    private fun drawWave(
        canvas: Canvas,
        bounds: Rect,
        baseRatio: Float,
        amplitudeRatio: Float,
        alpha: Int,
        speed: Float,
        progress: Float
    ) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val safeWidth = max(width, 1f)
        val baseY = bounds.top + height * baseRatio
        val amplitude = height * amplitudeRatio
        val phase = -progress * safeWidth * speed
        var x = bounds.left - safeWidth + phase

        wavePath.reset()
        wavePath.moveTo(x, bounds.bottom.toFloat())
        wavePath.lineTo(x, baseY)

        while (x < bounds.right + safeWidth) {
            wavePath.cubicTo(
                x + safeWidth * 0.18f,
                baseY - amplitude,
                x + safeWidth * 0.32f,
                baseY + amplitude,
                x + safeWidth * 0.50f,
                baseY
            )
            wavePath.cubicTo(
                x + safeWidth * 0.68f,
                baseY - amplitude,
                x + safeWidth * 0.82f,
                baseY + amplitude,
                x + safeWidth,
                baseY
            )
            x += safeWidth
        }

        wavePath.lineTo(bounds.right + safeWidth, bounds.bottom.toFloat())
        wavePath.close()

        wavePaint.shader = null
        wavePaint.color = Color.WHITE
        wavePaint.alpha = ((alpha * drawableAlpha) / 255).coerceIn(0, 255)
        canvas.drawPath(wavePath, wavePaint)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        wavePaint.colorFilter = colorFilter
        glowPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun start() {
        if (infiniteAnimator.isStarted) {
            infiniteAnimator.cancel()
        }
        startTime = android.os.SystemClock.uptimeMillis()
        infiniteAnimator.start()
    }

    override fun stop() {
        infiniteAnimator.cancel()
    }

    override fun isRunning(): Boolean = infiniteAnimator.isRunning

    companion object {
        internal const val ENTRANCE_DURATION_MILLIS = 2500L
        internal const val IDLE_DURATION_MILLIS = 10000L
        @Deprecated("Use ENTRANCE or IDLE variants", ReplaceWith("ENTRANCE_DURATION_MILLIS"))
        internal const val ANIMATION_DURATION_MILLIS = ENTRANCE_DURATION_MILLIS

        fun forTimeOfDay(context: Context): AnimatedHeaderBackgroundDrawable {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            return forHour(context, hour)
        }

        internal fun forHour(context: Context, hour: Int): AnimatedHeaderBackgroundDrawable {
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val isNight = nightMode == Configuration.UI_MODE_NIGHT_YES
            
            val colors = when (hour) {
                in 0..11 -> if (isNight) MORNING_DARK else MORNING_LIGHT
                in 12..16 -> if (isNight) AFTERNOON_DARK else AFTERNOON_LIGHT
                in 17..20 -> if (isNight) EVENING_DARK else EVENING_LIGHT
                else -> if (isNight) NIGHT_DARK else NIGHT_LIGHT
            }
            return AnimatedHeaderBackgroundDrawable(colors)
        }

        private val MORNING_LIGHT = intArrayOf(Color.parseColor("#00C853"), Color.parseColor("#26A69A"), Color.parseColor("#2196F3"))
        private val MORNING_DARK = intArrayOf(Color.parseColor("#0B2E22"), Color.parseColor("#103A4A"), Color.parseColor("#1A2457"))

        private val AFTERNOON_LIGHT = intArrayOf(Color.parseColor("#2196F3"), Color.parseColor("#03A9F4"), Color.parseColor("#00BCD4"))
        private val AFTERNOON_DARK = intArrayOf(Color.parseColor("#1A2457"), Color.parseColor("#123C55"), Color.parseColor("#103A4A"))

        private val EVENING_LIGHT = intArrayOf(Color.parseColor("#E91E63"), Color.parseColor("#FB8C00"), Color.parseColor("#FFC107"))
        private val EVENING_DARK = intArrayOf(Color.parseColor("#35162A"), Color.parseColor("#3A2611"), Color.parseColor("#4A3B12"))

        private val NIGHT_LIGHT = intArrayOf(Color.parseColor("#5E35B1"), Color.parseColor("#7E57C2"), Color.parseColor("#607D8B"))
        private val NIGHT_DARK = intArrayOf(Color.parseColor("#221B3C"), Color.parseColor("#1A1F3C"), Color.parseColor("#121629"))

        @Deprecated("Use forTimeOfDay", ReplaceWith("forTimeOfDay(context)"))
        fun random(context: Context): AnimatedHeaderBackgroundDrawable = forTimeOfDay(context)
    }
}


