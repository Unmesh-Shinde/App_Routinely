package com.dailyroutine.app

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HealthSyncWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("DailyRoutineWorker", "doWork: Starting adaptive background sync...")
        val syncMode = inputData.getString(KEY_SYNC_MODE) ?: SYNC_MODE_ALL
        val historyStartOffset = inputData.getInt(KEY_HISTORY_START_OFFSET, 0)

        val result = HealthSyncManager(applicationContext).performSync(
            hcm = HealthConnectManager(applicationContext),
            syncMode = syncMode,
            historyStartOffset = historyStartOffset
        )

        if (result is Result.Success) {
            when (syncMode) {
                SYNC_MODE_STEPS -> schedule2HourStepSync(applicationContext)
                SYNC_MODE_SLEEP -> scheduleSleepFollowUp(applicationContext)
                SYNC_MODE_CORRECTION -> scheduleHistoryFollowUp(applicationContext)
                SYNC_MODE_HISTORY -> {
                    val nextOffset = historyStartOffset + HISTORY_CHUNK_SIZE
                    if (nextOffset < HealthDataManager.SYNC_HISTORY_DAYS) {
                        enqueueHistoryChunk(applicationContext, nextOffset)
                    } else {
                        scheduleHistoryFollowUp(applicationContext)
                    }
                }
                SYNC_MODE_ALL -> {
                    val nextOffset = historyStartOffset + HISTORY_CHUNK_SIZE
                    if (nextOffset < HealthDataManager.SYNC_HISTORY_DAYS) {
                        enqueueHistoryChunk(applicationContext, nextOffset, SYNC_MODE_ALL)
                    } else {
                        schedule2HourStepSync(applicationContext)
                        scheduleSleepFollowUp(applicationContext)
                        scheduleHistoryFollowUp(applicationContext)
                    }
                }
            }
        }

        result
    }

    companion object {
        const val TAG_STEP_SYNC = "step_sync_periodic"
        const val TAG_SLEEP_SYNC = "sleep_sync_scheduled"
        const val TAG_HISTORY_SYNC = "history_sync_daily"
        const val KEY_SYNC_MODE = "sync_mode"
        const val KEY_HISTORY_START_OFFSET = "history_start_offset"
        const val HISTORY_CHUNK_SIZE = 30
        const val SYNC_MODE_ALL = "all"
        const val SYNC_MODE_STEPS = "steps"
        const val SYNC_MODE_SLEEP = "sleep"
        const val SYNC_MODE_HISTORY = "history"
        const val SYNC_MODE_CORRECTION = "correction"

        private const val WORK_NAME_PERIODIC_SYNC = "HealthPeriodicSync"

        fun scheduleAutoSync(context: Context, rescheduleForClockChange: Boolean = false) {
            if (rescheduleForClockChange) {
                val workManager = WorkManager.getInstance(context)
                workManager.cancelAllWorkByTag(TAG_STEP_SYNC)
                workManager.cancelAllWorkByTag(TAG_SLEEP_SYNC)
                workManager.cancelAllWorkByTag(TAG_HISTORY_SYNC)
            }
            scheduleInitialHistorySyncIfNeeded(context)
            schedule2HourStepSync(context)
            scheduleSleepSync(context, 7, 0, "SleepSyncMorning")
            scheduleSleepSync(context, 19, 0, "SleepSyncEvening")
            scheduleHistorySync(context)
        }

        fun schedule2HourStepSync(context: Context) {
            // Cancel legacy unanchored periodic work if present
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC_SYNC)
            } catch (e: Exception) {
                Log.w("HealthSyncWorker", "Could not cancel legacy periodic work", e)
            }

            val target = getNext2HourSlotTarget()
            val delayMs = delayUntil(target)
            val uniqueName = uniqueWorkName("StepSync2Hour", target)

            enqueueUniqueSync(context, uniqueName, TAG_STEP_SYNC, delayMs, SYNC_MODE_STEPS)
        }

        fun getNext2HourSlotTarget(now: Calendar = Calendar.getInstance()): Calendar {
            val dailySlots = intArrayOf(0, 6, 8, 10, 12, 14, 16, 18, 20, 22)
            for (dayOffset in 0..1) {
                for (hour in dailySlots) {
                    val target = now.clone() as Calendar
                    target.add(Calendar.DAY_OF_YEAR, dayOffset)
                    target.set(Calendar.HOUR_OF_DAY, hour)
                    target.set(Calendar.MINUTE, 0)
                    target.set(Calendar.SECOND, 0)
                    target.set(Calendar.MILLISECOND, 0)
                    if (target.after(now)) return target
                }
            }
            error("Could not calculate the next step sync slot")
        }

        fun shouldScheduleStepFollowUp(syncMode: String): Boolean {
            return syncMode == SYNC_MODE_STEPS || syncMode == SYNC_MODE_ALL
        }

        private fun scheduleSleepFollowUp(context: Context) {
            scheduleSleepSync(context, 7, 0, "SleepSyncMorning")
            scheduleSleepSync(context, 19, 0, "SleepSyncEvening")
        }

        private fun scheduleHistoryFollowUp(context: Context) {
            scheduleHistorySync(context)
        }

        private fun scheduleInitialHistorySyncIfNeeded(context: Context) {
            val hdm = HealthDataManager(context)
            val connectedSource = HealthDataManager.HEALTH_CONNECT_ALL_ORIGINS_SOURCE
            val needsStepHistory = hdm.isConnected() &&
                (!hdm.isInitialHistorySyncDone() || !hdm.hasSyncedHealthConnectSource(connectedSource))
            if (!needsStepHistory) return
            enqueueUniqueSync(context, "HealthHistoryFullSync_v3", TAG_HISTORY_SYNC, 0L, SYNC_MODE_HISTORY)
        }

        private fun scheduleSleepSync(context: Context, hour: Int, min: Int, uniqueName: String) {
            val target = nextTargetAt(hour, min)
            enqueueUniqueSync(context, uniqueWorkName(uniqueName, target), TAG_SLEEP_SYNC, delayUntil(target), SYNC_MODE_SLEEP)
        }

        private fun scheduleHistorySync(context: Context) {
            val target = nextTargetAt(3, 0)
            enqueueUniqueSync(context, uniqueWorkName("HistorySync", target), TAG_HISTORY_SYNC, delayUntil(target), SYNC_MODE_CORRECTION)
        }

        private fun enqueueHistoryChunk(
            context: Context,
            startOffset: Int,
            syncMode: String = SYNC_MODE_HISTORY
        ) {
            val request = OneTimeWorkRequestBuilder<HealthSyncWorker>()
                .setInputData(workDataOf(
                    KEY_SYNC_MODE to syncMode,
                    KEY_HISTORY_START_OFFSET to startOffset
                ))
                .addTag(TAG_HISTORY_SYNC)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "HealthHistoryRepair_v3_$startOffset",
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        private fun nextTargetAt(hour: Int, min: Int): Calendar {
            val now = Calendar.getInstance()
            return Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // If the target is in the past (today), add 1 day to target tomorrow at that hour.
                if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        private fun delayUntil(target: Calendar): Long {
            val now = Calendar.getInstance()
            return target.timeInMillis - now.timeInMillis
        }

        private fun uniqueWorkName(prefix: String, target: Calendar): String {
            val slot = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(target.time)
            return "${prefix}_$slot"
        }

        private fun enqueueUniqueSync(context: Context, uniqueName: String, tag: String, delayMs: Long, syncMode: String) {
            val request = OneTimeWorkRequestBuilder<HealthSyncWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_SYNC_MODE to syncMode))
                .addTag(tag)
                .build()

            // Each run is named by its target slot. That lets a running job enqueue the next
            // target slot while KEEP prevents duplicates when the app is opened repeatedly.
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
