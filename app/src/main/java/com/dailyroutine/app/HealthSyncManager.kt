package com.dailyroutine.app

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ListenableWorker.Result
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class HealthSyncManager(private val context: Context) {

    suspend fun performSync(
        hcm: IHealthConnectManager,
        syncMode: String,
        historyStartOffset: Int = 0
    ): Result = HealthSyncCoordinator.withLock {
        performSyncLocked(hcm, syncMode, historyStartOffset)
    }

    private suspend fun performSyncLocked(
        hcm: IHealthConnectManager,
        syncMode: String,
        historyStartOffset: Int
    ): Result {
        val hdm = HealthDataManager(context)
        val healthConnectConnected = hdm.isConnected()

        val roomWriter = HealthMetricsRoomWriter(context)
        val healthSourceIdentity = HealthDataManager.HEALTH_CONNECT_ALL_ORIGINS_SOURCE
        val healthConnectSourceChanged = !hdm.hasSyncedHealthConnectSource(healthSourceIdentity)
        val isFullHistorySync = syncMode == HealthSyncWorker.SYNC_MODE_HISTORY ||
            syncMode == HealthSyncWorker.SYNC_MODE_ALL
        val shouldSyncHistory = syncMode == HealthSyncWorker.SYNC_MODE_ALL || isFullHistorySync ||
            syncMode == HealthSyncWorker.SYNC_MODE_CORRECTION
        val shouldSyncSteps = shouldSyncHistory || syncMode == HealthSyncWorker.SYNC_MODE_STEPS
        val shouldSyncSleep = shouldSyncHistory || syncMode == HealthSyncWorker.SYNC_MODE_SLEEP

        val now = Instant.now()
        val todayDate = LocalDate.now()
        val todayKey = todayDate.toString()
        val zoneId = ZoneId.systemDefault()
        val startOfToday = todayDate.atStartOfDay(zoneId).toInstant()

        try {
            // An empty set from a successful query means permissions were revoked. A null/error
            // must remain retryable instead of being treated as a completed no-data sync.
            val granted = if (healthConnectConnected) {
                hcm.getGrantedPermissionsOrNull()
                    ?: throw IllegalStateException("Health Connect permissions could not be queried")
            } else {
                emptySet()
            }
            val hasStepsPerm = healthConnectConnected && granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
            val hasDistPerm = healthConnectConnected && granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))
            val hasActiveCalPerm = healthConnectConnected && granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
            val hasTotalCalPerm = healthConnectConnected && granted.contains(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
            val hasSleepPerm = healthConnectConnected && granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))
            val hasExercisePerm = healthConnectConnected && granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class))

            val prefs = context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            var stepHistoryHadReadFailures = false
            var metricReadHadFailures = false

            if (!healthConnectConnected) return Result.success()
            
            if (shouldSyncSteps && hasStepsPerm) {
                // Do not turn a provider read failure into a valid zero or a completion marker.
                // The per-day loop below records a successful result and leaves failures retryable.
                val steps = hcm.readStepsWithFallback(startOfToday, now)
                if (steps != null) {
                    editor.putString("steps_count", "%,d".format(steps))
                }

                if (hasDistPerm) {
                    val distMeters = hcm.readDistanceMetersOrNull(startOfToday, now)
                    if (distMeters != null) {
                        val dist = distMeters / 1000.0
                        editor.putString("distance_val", "%.2f km".format(dist))
                        hdm.saveHistoricalDistance(todayKey, dist)
                        roomWriter.upsertMetric(date = todayKey, distanceKm = dist, source = "health_connect:$healthSourceIdentity")
                    } else metricReadHadFailures = true
                }

                var activeSynced = false
                if (hasActiveCalPerm) {
                    val activeBurned = hcm.readActiveCaloriesOrNull(startOfToday, now)
                    if (activeBurned != null) {
                        hdm.saveHistoricalActiveCalories(todayKey, activeBurned)
                        roomWriter.upsertMetric(date = todayKey, activeCalories = activeBurned, source = "health_connect:$healthSourceIdentity")
                        activeSynced = activeBurned > 0
                    } else {
                        metricReadHadFailures = true
                    }
                }

                if (!activeSynced && hasTotalCalPerm) {
                    val burnedCals = hcm.readTotalCaloriesOrNull(startOfToday, now)
                    if (burnedCals != null && burnedCals > 0) {
                        editor.putString("calories_burnt", "%.0f".format(burnedCals))
                        roomWriter.upsertMetric(date = todayKey, totalCalories = burnedCals, source = "health_connect:$healthSourceIdentity")
                    } else if (burnedCals == null) metricReadHadFailures = true
                }
            }

            if (shouldSyncSleep && hasSleepPerm) {
                val sessions = hcm.readSleepSessionsOrNull(startOfToday, now)
                if (sessions != null) {
                    val totalDurationMin = sleepMinutesWithin(sessions, startOfToday, now)
                    editor.putString("sleep_hours", "${totalDurationMin / 60}h ${totalDurationMin % 60}m")
                    roomWriter.upsertMetric(date = todayKey, sleepHours = totalDurationMin / 60.0, source = "health_connect:$healthSourceIdentity")
                } else metricReadHadFailures = true
            }

            if (shouldSyncSteps && hasExercisePerm) {
                val walkingMinutes = hcm.readWalkingDurationMinutesOrNull(startOfToday, now)
                if (walkingMinutes != null) hdm.saveHistoricalWalkingDuration(todayKey, walkingMinutes)
                else metricReadHadFailures = true
            }

            val historyRange = if (isFullHistorySync) {
                val start = historyStartOffset.coerceIn(0, HealthDataManager.SYNC_HISTORY_DAYS - 1)
                start..minOf(start + HealthSyncWorker.HISTORY_CHUNK_SIZE - 1, HealthDataManager.SYNC_HISTORY_DAYS - 1)
            } else if (syncMode == HealthSyncWorker.SYNC_MODE_CORRECTION) {
                // The daily 3 AM repair checks a recent correction window; a manual or initial
                // full pass handles older gaps in bounded, resumable chunks.
                0..6
            } else if (shouldSyncHistory) {
                0 until HealthDataManager.SYNC_HISTORY_DAYS
            } else {
                // For a normal sync, we only check today and yesterday to handle day-rollover.
                0..1
            }

            // Optimization: Fetch the metrics we already have in Room to identify gaps.
            val startDate = todayDate.minusDays(historyRange.last.toLong()).toString()
            val existingMetrics = roomWriter.repository.getMetricsMap(startDate, todayDate.toString())

            for (i in historyRange) {
                val date = todayDate.minusDays(i.toLong())
                val dateKey = date.toString()
                
                // Incremental Sync Logic: 
                // 1. If it's today (i == 0), we always sync to get latest live data.
                // 2. If it's a historical day, only sync if the data is missing OR was never fully synced.
                val existing = existingMetrics[dateKey]
                val isToday = i == 0
                // Re-read yesterday on every normal sync. Providers can deliver late records,
                // and a successful partial-day read must not permanently finalize that date.
                val needsSteps = isToday || i == 1 || healthConnectSourceChanged || existing?.stepsSynced != true ||
                    existing?.stepsSyncStatus != "SYNCED" || existing?.stepsSource != "health_connect:$healthSourceIdentity"
                val needsSleep = isToday || i == 1 || existing?.sleepHours == null
                val needsWalkingDuration = isToday || i == 1 || isFullHistorySync ||
                    syncMode == HealthSyncWorker.SYNC_MODE_CORRECTION

                // If this is a historical day and we already have EVERYTHING, skip it.
                if (!isToday && !needsSteps && !needsSleep && !(hasExercisePerm && needsWalkingDuration)) {
                    continue 
                }

                val dayStart = date.atStartOfDay(zoneId).toInstant()
                val dayEnd = if (isToday) now else date.plusDays(1).atStartOfDay(zoneId).toInstant()
                
                // Sync Steps & Distance & Calories
                if (shouldSyncSteps && hasStepsPerm) {
                    // Only fetch from Health Connect if we actually need steps for this day
                    if (needsSteps) {
                        val historicalSteps = hcm.readStepsWithFallback(dayStart, dayEnd)
                        if (historicalSteps != null) {
                            hdm.saveHistoricalSteps(dateKey, historicalSteps)
                            roomWriter.upsertMetric(date = dateKey, steps = historicalSteps, stepsSynced = true, source = "health_connect:$healthSourceIdentity")
                        } else {
                            stepHistoryHadReadFailures = true
                        }

                        if (hasDistPerm) {
                            val historicalDistMeters = hcm.readDistanceMetersOrNull(dayStart, dayEnd)
                            if (historicalDistMeters != null) {
                                val historicalDist = historicalDistMeters / 1000.0
                                hdm.saveHistoricalDistance(dateKey, historicalDist)
                                roomWriter.upsertMetric(date = dateKey, distanceKm = historicalDist, source = "health_connect:$healthSourceIdentity")
                            } else metricReadHadFailures = true
                        }

                        var histActiveSynced = false
                        if (hasActiveCalPerm) {
                            val historicalActive = hcm.readActiveCaloriesOrNull(dayStart, dayEnd)
                            if (historicalActive != null) {
                                hdm.saveHistoricalActiveCalories(dateKey, historicalActive)
                                roomWriter.upsertMetric(date = dateKey, activeCalories = historicalActive, source = "health_connect:$healthSourceIdentity")
                                histActiveSynced = historicalActive > 0
                            } else {
                                metricReadHadFailures = true
                            }
                        }

                        if (!histActiveSynced && hasTotalCalPerm) {
                            val historicalCalories = hcm.readTotalCaloriesOrNull(dayStart, dayEnd)
                            if (historicalCalories != null && historicalCalories > 0) {
                                hdm.saveHistoricalCalories(dateKey, historicalCalories)
                                roomWriter.upsertMetric(date = dateKey, totalCalories = historicalCalories, source = "health_connect:$healthSourceIdentity")
                            } else if (historicalCalories == null) metricReadHadFailures = true
                        }
                    }
                }
                
                // Sync Sleep
                if (shouldSyncSleep && hasSleepPerm) {
                    if (needsSleep) {
                        val sessions = hcm.readSleepSessionsOrNull(dayStart, dayEnd)
                        if (sessions != null) {
                            val mins = sleepMinutesWithin(sessions, dayStart, dayEnd)
                            hdm.saveHistoricalSleep(dateKey, mins / 60.0)
                            roomWriter.upsertMetric(date = dateKey, sleepHours = mins / 60.0, source = "health_connect:$healthSourceIdentity")
                        } else metricReadHadFailures = true
                    }
                }

                if (shouldSyncSteps && hasExercisePerm && needsWalkingDuration) {
                    val walkingMinutes = hcm.readWalkingDurationMinutesOrNull(dayStart, dayEnd)
                    if (walkingMinutes != null) hdm.saveHistoricalWalkingDuration(dateKey, walkingMinutes)
                    else metricReadHadFailures = true
                }
                
            }
            if (shouldSyncSteps) {
                editor.putString("last_finalized_day", todayDate.toString())
            }

            if (isFullHistorySync) {
                val historyComplete = !healthConnectConnected ||
                    (hasStepsPerm && !stepHistoryHadReadFailures && hdm.isStepHistoryComplete())
                hdm.setInitialHistorySyncDone(historyComplete)
                if (historyComplete) hdm.markHealthConnectSourceSynced(healthSourceIdentity)
            }

            editor.apply()
            hdm.pruneHistoricalData()
            roomWriter.pruneToRetentionWindow()

            val readFailed = stepHistoryHadReadFailures || metricReadHadFailures
            if (!readFailed) {
                val timestamp = SimpleDateFormat("hh:mm a, dd MMM", Locale.US).format(Date())
                hdm.setLastSyncTime(timestamp)
            }

            val isFinalHistoryChunk = historyStartOffset + HealthSyncWorker.HISTORY_CHUNK_SIZE >=
                HealthDataManager.SYNC_HISTORY_DAYS
            if (syncMode != HealthSyncWorker.SYNC_MODE_HISTORY || isFinalHistoryChunk) {
                val metrics = mutableListOf<String>()
                if (shouldSyncSteps && hasStepsPerm) metrics.add("Steps")
                if (shouldSyncSleep && hasSleepPerm) metrics.add("Sleep")
                
                val syncLabel = if (syncMode == HealthSyncWorker.SYNC_MODE_HISTORY) "History sync" else "Auto-Synced"
                val msg = if (metrics.isEmpty()) "Background Check (No updates)"
                    else "${if (readFailed) "Partial sync; retrying" else syncLabel}: ${metrics.joinToString(", ")}"
                SyncLogManager.addLog(context, hdm.getConnectedAppName(), msg, !readFailed)
            }

            context.sendBroadcast(Intent("com.dailyroutine.app.DATA_UPDATED"))
            WellnessWidget.refresh(context)

            return if (readFailed) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e("HealthSyncManager", "Sync Failed", e)
            return Result.retry()
        }
    }

    private fun sleepMinutesWithin(
        sessions: List<SleepSessionRecord>,
        windowStart: Instant,
        windowEnd: Instant
    ): Long {
        val intervals = sessions.mapNotNull { session ->
            val clippedStart = maxOf(session.startTime, windowStart)
            val clippedEnd = minOf(session.endTime, windowEnd)
            if (clippedEnd.isAfter(clippedStart)) clippedStart to clippedEnd else null
        }.sortedBy { it.first }

        var mergedStart: Instant? = null
        var mergedEnd: Instant? = null
        var totalMillis = 0L
        for ((start, end) in intervals) {
            val currentEnd = mergedEnd
            if (currentEnd == null || start.isAfter(currentEnd)) {
                if (mergedStart != null && currentEnd != null) {
                    totalMillis += java.time.Duration.between(mergedStart, currentEnd).toMillis()
                }
                mergedStart = start
                mergedEnd = end
            } else if (end.isAfter(currentEnd)) {
                mergedEnd = end
            }
        }
        if (mergedStart != null && mergedEnd != null) {
            totalMillis += java.time.Duration.between(mergedStart, mergedEnd).toMillis()
        }
        return java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(totalMillis)
    }
}

/** Serializes manual and scheduled provider reads/writes within the app process. */
private object HealthSyncCoordinator {
    private val mutex = Mutex()
    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
