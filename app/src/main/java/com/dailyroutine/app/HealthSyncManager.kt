package com.dailyroutine.app

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ListenableWorker.Result
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class HealthSyncManager(private val context: Context) {

    suspend fun performSync(
        hcm: IHealthConnectManager,
        gfit: IGoogleFitHeartPointsManager,
        syncMode: String
    ): Result {
        val hdm = HealthDataManager(context)
        if (!hdm.isConnected()) return Result.success()

        val roomWriter = HealthMetricsRoomWriter(context)
        val appPkg = hdm.getConnectedAppPackage()
        val shouldSyncHistory = syncMode == HealthSyncWorker.SYNC_MODE_ALL || syncMode == HealthSyncWorker.SYNC_MODE_HISTORY
        val shouldSyncSteps = shouldSyncHistory || syncMode == HealthSyncWorker.SYNC_MODE_STEPS
        val shouldSyncSleep = shouldSyncHistory || syncMode == HealthSyncWorker.SYNC_MODE_SLEEP

        val now = Instant.now()
        val todayDate = LocalDate.now()
        val todayKey = todayDate.toString()
        val zoneId = ZoneId.systemDefault()
        val startOfToday = todayDate.atStartOfDay(zoneId).toInstant()
        var heartPointsByDate = emptyMap<String, Double>()

        try {
            val granted = hcm.getGrantedPermissions()
            val hasStepsPerm = granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
            val hasDistPerm = granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))
            val hasActiveCalPerm = granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
            val hasTotalCalPerm = granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
            val hasSleepPerm = granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))
            val hasWeightPerm = granted.contains(HealthPermission.getReadPermission(WeightRecord::class))

            val prefs = context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            var stepHistoryHadReadFailures = false

            val canReadGoogleFitHeartPoints = gfit.hasReadPermission()

            if (canReadGoogleFitHeartPoints) {
                try {
                    val oldestDate = if (shouldSyncHistory) {
                        todayDate.minusDays((HealthDataManager.SYNC_HISTORY_DAYS - 1).toLong())
                    } else {
                        todayDate.minusDays(1)
                    }
                    heartPointsByDate = gfit.readDailyHeartPoints(oldestDate, now, zoneId)
                    val heartPointsToday = heartPointsByDate[todayDate.toString()] ?: gfit.readHeartPoints(startOfToday, now)
                    if (heartPointsToday > 0.0) {
                        hdm.setHeartPoints(heartPointsToday.toInt())
                        roomWriter.upsertMetric(date = todayKey, heartPoints = heartPointsToday)
                    }
                } catch (e: Exception) {
                    Log.e("HealthSyncManager", "Failed to sync Google Fit heart points: ${e.message}")
                }
            }
            
            if (shouldSyncSteps && hasStepsPerm) {
                val steps = hcm.readStepsWithFallback(startOfToday, now, appPkg) ?: 0L
                editor.putString("steps_count", "%,d".format(steps))
                roomWriter.upsertMetric(date = todayKey, steps = steps, stepsSynced = true)

                if (hasDistPerm) {
                    val dist = hcm.readDistanceMeters(startOfToday, now, appPkg) / 1000.0
                    editor.putString("distance_val", "%.2f km".format(dist))
                    hdm.saveHistoricalDistance(todayKey, dist)
                    roomWriter.upsertMetric(date = todayKey, distanceKm = dist)
                }

                var activeSynced = false
                if (hasActiveCalPerm) {
                    val activeBurned = hcm.readActiveCalories(startOfToday, now, appPkg)
                    if (activeBurned > 0) {
                        hdm.saveHistoricalActiveCalories(todayKey, activeBurned)
                        roomWriter.upsertMetric(date = todayKey, activeCalories = activeBurned)
                        activeSynced = true
                    }
                }

                if (!activeSynced && hasTotalCalPerm) {
                    val burnedCals = hcm.readTotalCalories(startOfToday, now, appPkg)
                    if (burnedCals > 0) {
                        editor.putString("calories_burnt", "%.0f".format(burnedCals))
                        roomWriter.upsertMetric(date = todayKey, totalCalories = burnedCals)
                    }
                }
            }

            if (shouldSyncSleep && hasSleepPerm) {
                val sessions = hcm.readSleepSessions(startOfToday, now, appPkg)
                val totalDurationMin = sessions.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                editor.putString("sleep_hours", "${totalDurationMin / 60}h ${totalDurationMin % 60}m")
                roomWriter.upsertMetric(date = todayKey, sleepHours = totalDurationMin / 60.0)
            }

            if (shouldSyncSteps && hasWeightPerm) {
                 val weightKg = hcm.readWeightKg(startOfToday, now, appPkg)
                  if (weightKg > 0) {
                      editor.putString("current_weight", "%.1f kg".format(weightKg))
                      roomWriter.upsertMetric(date = todayKey, weightKg = weightKg)
                  }
             }

            val historyRange = if (shouldSyncHistory) {
                // For a full history sync, we check the entire 180-day window.
                0 until HealthDataManager.SYNC_HISTORY_DAYS
            } else {
                // For a normal sync, we only check today and yesterday to handle day-rollover.
                0..1
            }

            // Optimization: Fetch the metrics we already have in Room to identify gaps.
            val startDate = todayDate.minusDays(historyRange.last.toLong()).toString()
            val existingMetrics = roomWriter.repository.getMetricsMap(startDate, todayDate.toString())
            val syncedMetrics = mutableMapOf<String, DailyHealthMetricEntity>()

            for (i in historyRange) {
                val date = todayDate.minusDays(i.toLong())
                val dateKey = date.toString()
                
                // Incremental Sync Logic: 
                // 1. If it's today (i == 0), we always sync to get latest live data.
                // 2. If it's a historical day, only sync if the data is missing OR was never fully synced.
                val existing = existingMetrics[dateKey]
                val isToday = i == 0
                val needsSteps = isToday || existing?.stepsSynced != true
                val needsSleep = isToday || existing?.sleepHours == null
                val needsHeartPoints = isToday || (canReadGoogleFitHeartPoints && existing?.heartPoints == null)

                // If this is a historical day and we already have EVERYTHING, skip it.
                if (!isToday && !needsSteps && !needsSleep && !needsHeartPoints) {
                    continue 
                }

                val dayStart = date.atStartOfDay(zoneId).toInstant()
                val dayEnd = if (isToday) now else date.plusDays(1).atStartOfDay(zoneId).toInstant()
                
                var currentMetric = existing ?: DailyHealthMetricEntity(date = dateKey)

                // Sync Steps & Distance & Calories
                if (shouldSyncSteps && hasStepsPerm) {
                    // Only fetch from Health Connect if we actually need steps for this day
                    if (needsSteps) {
                        val historicalSteps = hcm.readStepsWithFallback(dayStart, dayEnd, appPkg)
                        if (historicalSteps != null) {
                            hdm.saveHistoricalSteps(dateKey, historicalSteps)
                            currentMetric = currentMetric.copy(steps = historicalSteps, stepsSynced = true)
                        } else {
                            stepHistoryHadReadFailures = true
                        }

                        if (hasDistPerm) {
                            val historicalDist = hcm.readDistanceMeters(dayStart, dayEnd, appPkg) / 1000.0
                            if (historicalDist > 0) {
                                hdm.saveHistoricalDistance(dateKey, historicalDist)
                                currentMetric = currentMetric.copy(distanceKm = historicalDist)
                            }
                        }

                        var histActiveSynced = false
                        if (hasActiveCalPerm) {
                            val historicalActive = hcm.readActiveCalories(dayStart, dayEnd, appPkg)
                            if (historicalActive > 0) {
                                hdm.saveHistoricalActiveCalories(dateKey, historicalActive)
                                currentMetric = currentMetric.copy(activeCalories = historicalActive)
                                histActiveSynced = true
                            }
                        }

                        if (!histActiveSynced && hasTotalCalPerm) {
                            val historicalCalories = hcm.readTotalCalories(dayStart, dayEnd, appPkg)
                            if (historicalCalories > 0) {
                                hdm.saveHistoricalCalories(dateKey, historicalCalories)
                                currentMetric = currentMetric.copy(totalCalories = historicalCalories)
                            }
                        }
                    }
                }
                
                // Sync Sleep
                if (shouldSyncSleep && hasSleepPerm) {
                    if (needsSleep) {
                        val sessions = hcm.readSleepSessions(dayStart, dayEnd, appPkg)
                        val mins = sessions.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                        hdm.saveHistoricalSleep(dateKey, mins / 60.0)
                        currentMetric = currentMetric.copy(sleepHours = mins / 60.0)
                    }
                }
                
                // Sync Weight
                if (shouldSyncSteps && hasWeightPerm) {
                    // Weight is rarely changed, but we sync it if we are syncing steps for that day
                    if (needsSteps) {
                        val weightKg = hcm.readWeightKg(dayStart, dayEnd, appPkg)
                        if (weightKg > 0) {
                            hdm.saveWeight(dateKey, weightKg)
                            currentMetric = currentMetric.copy(weightKg = weightKg)
                        }
                    }
                }

                // Sync Heart Points
                if (canReadGoogleFitHeartPoints && heartPointsByDate.containsKey(dateKey)) {
                    if (needsHeartPoints) {
                        val pts = heartPointsByDate[dateKey] ?: 0.0
                        hdm.saveHistoricalHeartPoints(dateKey, pts)
                        currentMetric = currentMetric.copy(heartPoints = pts)
                    }
                }
                
                syncedMetrics[dateKey] = currentMetric
            }
            
            if (syncedMetrics.isNotEmpty()) {
                roomWriter.upsertAllMetrics(syncedMetrics.values.toList())
            }
            if (shouldSyncSteps) {
                editor.putString("last_finalized_day", todayDate.toString())
            }

            if (shouldSyncHistory) {
                val historyComplete = hasStepsPerm && !stepHistoryHadReadFailures && hdm.isStepHistoryComplete()
                hdm.setInitialHistorySyncDone(historyComplete)
                val heartPointsHistoryComplete = canReadGoogleFitHeartPoints && heartPointsByDate.isNotEmpty() && hdm.isHeartPointsHistoryComplete()
                hdm.setInitialHeartPointsHistorySyncDone(heartPointsHistoryComplete)
            }

            editor.apply()
            hdm.pruneHistoricalData()
            roomWriter.pruneToRetentionWindow()

            val timestamp = SimpleDateFormat("hh:mm a, dd MMM", Locale.US).format(Date())
            hdm.setLastSyncTime(timestamp)

            if (syncMode != HealthSyncWorker.SYNC_MODE_HISTORY) {
                val metrics = mutableListOf<String>()
                if (shouldSyncSteps && hasStepsPerm) metrics.add("Steps")
                if (shouldSyncSleep && hasSleepPerm) metrics.add("Sleep")
                if (canReadGoogleFitHeartPoints) metrics.add("Heart Points")
                
                val msg = if (metrics.isEmpty()) "Background Check (No updates)" else "Auto-Synced: ${metrics.joinToString(", ")}"
                SyncLogManager.addLog(context, hdm.getConnectedAppName(), msg, true)
            }

            context.sendBroadcast(Intent("com.dailyroutine.app.DATA_UPDATED"))
            WellnessWidget.refresh(context)

            return Result.success()
        } catch (e: Exception) {
            Log.e("HealthSyncManager", "Sync Failed", e)
            return Result.retry()
        }
    }
}
