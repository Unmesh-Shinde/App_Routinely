package com.dailyroutine.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Handles widget water taps in a brief foreground window so Android delivers touch haptics. */
class WidgetWaterActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.action
        val amount = when (action) {
            WellnessWidget.ACTION_ADD_WATER -> 0.25
            WellnessWidget.ACTION_REMOVE_WATER -> -0.25
            else -> null
        }

        if (amount != null) {
            window.decorView.post {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                HealthDataManager(applicationContext).adjustWaterIntake(today, amount)

                val manager = AppWidgetManager.getInstance(applicationContext)
                val ids = manager.getAppWidgetIds(
                    ComponentName(applicationContext, WellnessWidget::class.java)
                )
                ids.forEach { widgetId ->
                    WellnessWidget.updateAppWidget(applicationContext, manager, widgetId)
                }
                sendBroadcast(Intent(WellnessWidget.ACTION_DATA_UPDATED).setPackage(packageName))
                finish()
            }
        } else {
            finish()
        }
    }
}
