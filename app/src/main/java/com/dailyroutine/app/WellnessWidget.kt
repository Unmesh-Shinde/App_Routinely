package com.dailyroutine.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class WellnessWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_ADD_WATER || intent.action == ACTION_REMOVE_WATER) {
            val hdm = HealthDataManager(context)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val amount = if (intent.action == ACTION_ADD_WATER) 0.25 else -0.25
            hdm.adjustWaterIntake(today, amount)
            
            // Refresh all widgets
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WellnessWidget::class.java))
            onUpdate(context, manager, ids)
            
            // Also notify app if running (optional, but good for sync)
            context.sendBroadcast(Intent(ACTION_DATA_UPDATED).setPackage(context.packageName))
        }
    }

    companion object {
        const val ACTION_ADD_WATER = "com.dailyroutine.app.ACTION_ADD_WATER"
        const val ACTION_REMOVE_WATER = "com.dailyroutine.app.ACTION_REMOVE_WATER"
        const val ACTION_DATA_UPDATED = "com.dailyroutine.app.DATA_UPDATED"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_wellness)
            
            val hdm = HealthDataManager(context)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            
            // 1. Get Data
            val stepsStr = hdm.getSteps().replace(",", "")
            val stepsInt = stepsStr.toIntOrNull() ?: 0
            val stepGoal = hdm.getDailyStepGoal().coerceAtLeast(1)
            val progressPct = (stepsInt * 100 / stepGoal).coerceIn(0, 100)
            
            val doneIds = RoutineProgressStore.getDoneIds(context)
            val waterFromReminders = ReminderManager(context).getAllReminders()
                .filter { it.isEnabled && it.type == ReminderType.HYDRATION && it.id.toString() in doneIds }
                .size * 0.25
            val water = hdm.getWaterIntake(today) + waterFromReminders
            
            // 1a. Distance Precedence: Synced vs Formula
            val syncedDistance = hdm.getHistoricalDistance(today)
            val distance = if (syncedDistance > 0.0) syncedDistance else hdm.calculateDistanceKm(stepsInt)

            // 2. Set Views
            views.setTextViewText(R.id.tvWidgetSteps, String.format(Locale.US, "%,d", stepsInt))
            views.setTextViewText(R.id.tvWidgetStepProgress, String.format(Locale.US, "/ %,d", stepGoal))
            views.setProgressBar(R.id.pbWidgetSteps, 100, progressPct, false)
            views.setTextViewText(R.id.tvWidgetDistance, String.format(Locale.US, "%.2f km", distance))
            views.setTextViewText(R.id.tvWidgetWater, String.format(Locale.US, "%.1f L", water))

            // 3. Set Icon Tints (Compatibility Safe)
            val walkingColor = ContextCompat.getColor(context, R.color.strokeWalking)
            val hydrationColor = ContextCompat.getColor(context, R.color.strokeHydration)
            views.setInt(R.id.ivWidgetStepsIcon, "setColorFilter", walkingColor)
            views.setInt(R.id.ivWidgetWaterIcon, "setColorFilter", hydrationColor)

            // 4. Click Intents for Water
            val waterIntent = Intent(context, WellnessWidget::class.java).apply {
                action = ACTION_ADD_WATER
            }
            val waterPendingIntent = PendingIntent.getBroadcast(
                context, 0, waterIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetAddWater, waterPendingIntent)

            val removeWaterIntent = Intent(context, WellnessWidget::class.java).apply {
                action = ACTION_REMOVE_WATER
            }
            val removeWaterPendingIntent = PendingIntent.getBroadcast(
                context, 1, removeWaterIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetRemoveWater, removeWaterPendingIntent)

            // 5. Click Intent to open App
            val openIntent = Intent(context, MainActivity::class.java)
            val openPendingIntent = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun refresh(context: Context) {
            val intent = Intent(context, WellnessWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, WellnessWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
