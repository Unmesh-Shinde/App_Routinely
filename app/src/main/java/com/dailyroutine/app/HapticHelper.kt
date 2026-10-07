package com.dailyroutine.app

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

object HapticHelper {
    
    /**
     * Triggers a refined "premium click" vibration.
     * Tuned for precise intensity control (150/255) and 30ms duration.
     */
    fun triggerThump(context: Context) {
        try {
            val vibrator = context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Using a sharp 30ms pulse at exactly 150 intensity.
                val effect = VibrationEffect.createOneShot(30, 150)
                
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                
                vibrator.vibrate(effect, attributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        } catch (e: Exception) {
            Log.e("HapticHelper", "Failed to trigger vibration", e)
        }
    }
}
