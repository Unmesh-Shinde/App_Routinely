package com.dailyroutine.app

import android.content.Context
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HealthSyncIntegrationTest {
    private lateinit var context: Context
    private lateinit var hcm: IHealthConnectManager
    private lateinit var hdm: HealthDataManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        
        // Clear prefs first
        context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("sync_logs_pref", Context.MODE_PRIVATE).edit().clear().commit()

        // Clear Room DB
        runBlocking {
            HealthMetricsRepository.create(context).pruneOlderThan("9999-99-99")
            RoutinelyDatabase.getInstance(context).dailyHealthMetricDao().deleteAll()
        }

        hdm = HealthDataManager(context)
        hdm.setConnected(true)
        hcm = mock()
    }

    @Test
    fun performSync_persistsSyncedDistanceAndActiveCalories() = runBlocking {
        val today = LocalDate.now().toString()
        
        // Mock permissions using actual SDK strings
        whenever(hcm.getGrantedPermissionsOrNull()).doReturn(setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        ))
        
        // Mock data
        whenever(hcm.readStepsWithFallback(any(), any())).doReturn(5000L)
        whenever(hcm.readDistanceMeters(any(), any())).doReturn(4200.0)
        whenever(hcm.readActiveCalories(any(), any())).doReturn(350.0)

        val manager = HealthSyncManager(context)
        manager.performSync(hcm, HealthSyncWorker.SYNC_MODE_STEPS)

        // Verify SharedPreferences
        assertEquals(4.2, hdm.getHistoricalDistance(today), 0.01)
        assertEquals(350.0, hdm.getHistoricalActiveCalories(today), 0.01)
        
        // Verify Room
        val repo = HealthMetricsRepository.create(context)
        val metric = repo.getMetric(today)
        assertEquals(4.2, metric?.distanceKm ?: 0.0, 0.01)
        assertEquals(350.0, metric?.activeCalories ?: 0.0, 0.01)
    }

    @Test
    fun performSync_persistsWalkingDurationFromHealthConnectExerciseSessions() = runBlocking {
        val today = LocalDate.now().toString()
        whenever(hcm.getGrantedPermissionsOrNull()).doReturn(setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        ))
        whenever(hcm.readStepsWithFallback(any(), any())).doReturn(2500L)
        whenever(hcm.readWalkingDurationMinutesOrNull(any(), any())).doReturn(37L)

        HealthSyncManager(context).performSync(hcm, HealthSyncWorker.SYNC_MODE_STEPS)

        assertEquals(37L, hdm.getHistoricalWalkingDuration(today))
    }

    @Test
    fun performSync_keepsMissingHealthConnectDistanceAtZero() = runBlocking {
        val today = LocalDate.now().toString()
        UserPreferencesStore.setUserHeight(context, 180.0)
        UserPreferencesStore.setUserGender(context, "Male")

        whenever(hcm.getGrantedPermissionsOrNull()).doReturn(setOf(
            HealthPermission.getReadPermission(StepsRecord::class)
        ))
        whenever(hcm.readStepsWithFallback(any(), any())).doReturn(1000L)
        whenever(hcm.readDistanceMeters(any(), any())).doReturn(0.0)

        val manager = HealthSyncManager(context)
        manager.performSync(hcm, HealthSyncWorker.SYNC_MODE_STEPS)

        // Stay 0 in historical distance storage because sync returned 0
        assertEquals(0.0, hdm.getHistoricalDistance(today), 0.01)
        
        assertEquals(1000, hdm.getHistoricalSteps(today).toInt())
    }

    @Test
    fun performSync_handlesSyncModeHistoryAcrossDays() = runBlocking {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1).toString()
        
        whenever(hcm.getGrantedPermissionsOrNull()).doReturn(setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class)
        ))
        
        whenever(hcm.readStepsWithFallback(any(), any())).doReturn(8000L)
        whenever(hcm.readDistanceMeters(any(), any())).doReturn(6000.0)

        val manager = HealthSyncManager(context)
        manager.performSync(hcm, HealthSyncWorker.SYNC_MODE_HISTORY)

        // Verify yesterday's data was synced
        assertEquals(6.0, hdm.getHistoricalDistance(yesterday), 0.01)
        val repo = HealthMetricsRepository.create(context)
        assertEquals(8000L, repo.getMetric(yesterday)?.steps)
    }

    @Test
    fun performSync_writesSyncLog_whenNotHistoryMode() = runBlocking {
        whenever(hcm.getGrantedPermissionsOrNull()).doReturn(setOf(
            HealthPermission.getReadPermission(StepsRecord::class)
        ))
        whenever(hcm.readStepsWithFallback(any(), any())).doReturn(3000L)

        val manager = HealthSyncManager(context)
        manager.performSync(hcm, HealthSyncWorker.SYNC_MODE_STEPS)

        val logs = SyncLogManager.getLogs(context)
        assertFalse("Sync logs should not be empty after sync", logs.isEmpty())
        assertTrue("Log entry should report Steps", logs[0].message.contains("Steps"))
    }

    @Test
    fun performSync_whenDisconnected_returnsSuccessWithoutSync() = runBlocking {
        hdm.setConnected(false)

        val manager = HealthSyncManager(context)
        val result = manager.performSync(hcm, HealthSyncWorker.SYNC_MODE_STEPS)

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("No logs should be logged when disconnected", SyncLogManager.getLogs(context).isEmpty())
    }

    @Test
    fun performSync_whenSleepMode_readsSleepSessions() = runBlocking {
        val today = LocalDate.now().toString()
        whenever(hcm.getGrantedPermissionsOrNull()).doReturn(setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        ))

        val start = Instant.now().minusSeconds(28800) // 8 hours ago
        val end = Instant.now()
        
        // Construct Metadata
        val metadataConstructor = Metadata::class.java.declaredConstructors.first()
        metadataConstructor.isAccessible = true
        val metaParams = arrayOfNulls<Any>(metadataConstructor.parameterTypes.size)
        for (i in metaParams.indices) {
            metaParams[i] = when (val pt = metadataConstructor.parameterTypes[i]) {
                String::class.java -> "sleep_id"
                DataOrigin::class.java -> DataOrigin("com.dailyroutine.app")
                Instant::class.java -> start
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> 0L
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> 0
                else -> null
            }
        }
        val mockMetadata = metadataConstructor.newInstance(*metaParams)

        // Construct SleepSessionRecord
        val recordConstructor = SleepSessionRecord::class.java.declaredConstructors.first()
        recordConstructor.isAccessible = true
        val recParams = arrayOfNulls<Any>(recordConstructor.parameterTypes.size)
        var instantCount = 0
        for (i in recParams.indices) {
            recParams[i] = when (val pt = recordConstructor.parameterTypes[i]) {
                Instant::class.java -> {
                    instantCount++
                    if (instantCount == 1) start else end
                }
                ZoneOffset::class.java -> ZoneOffset.UTC
                String::class.java -> "sleep"
                Metadata::class.java -> mockMetadata
                List::class.java -> emptyList<Any>()
                else -> null
            }
        }
        
        val realSleepSession = recordConstructor.newInstance(*recParams) as SleepSessionRecord
        whenever(hcm.readSleepSessions(any(), any())).doReturn(listOf(realSleepSession))

        val manager = HealthSyncManager(context)
        manager.performSync(hcm, HealthSyncWorker.SYNC_MODE_SLEEP)

        val repo = HealthMetricsRepository.create(context)
        val metric = repo.getMetric(today)
        assertEquals(8.0, metric?.sleepHours ?: 0.0, 0.01)
    }

    @Test
    fun performSync_mergesOverlappingSleepSessionsInsteadOfDoubleCounting() = runBlocking {
        val today = LocalDate.now().toString()
        whenever(hcm.getGrantedPermissionsOrNull()).doReturn(setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        ))

        val now = Instant.now()
        val dayStart = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val elapsedSeconds = java.time.Duration.between(dayStart, now).seconds
        val overlapStart = dayStart.plusSeconds(elapsedSeconds / 2)
        val firstSession = createSleepSession(dayStart, now)
        val overlappingDuplicate = createSleepSession(overlapStart, now)
        whenever(hcm.readSleepSessions(any(), any())).doReturn(listOf(firstSession, overlappingDuplicate))

        HealthSyncManager(context).performSync(hcm, HealthSyncWorker.SYNC_MODE_SLEEP)

        val metric = HealthMetricsRepository.create(context).getMetric(today)
        val expectedHours = java.util.concurrent.TimeUnit.SECONDS.toMinutes(elapsedSeconds) / 60.0
        assertEquals("The overlapping half-day session must not be counted twice", expectedHours, metric?.sleepHours ?: 0.0, 0.01)
        assertEquals(expectedHours, hdm.getHistoricalSleep(today), 0.01)
    }

    private fun createSleepSession(start: Instant, end: Instant): SleepSessionRecord {
        val metadataConstructor = Metadata::class.java.declaredConstructors.first()
        metadataConstructor.isAccessible = true
        val metadataArgs = arrayOfNulls<Any>(metadataConstructor.parameterTypes.size)
        for (i in metadataArgs.indices) {
            metadataArgs[i] = when (metadataConstructor.parameterTypes[i]) {
                String::class.java -> "sleep_$i"
                DataOrigin::class.java -> DataOrigin("com.example.health-provider")
                Instant::class.java -> start
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> 0L
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> 0
                else -> null
            }
        }
        val metadata = metadataConstructor.newInstance(*metadataArgs)

        val recordConstructor = SleepSessionRecord::class.java.declaredConstructors.first()
        recordConstructor.isAccessible = true
        val recordArgs = arrayOfNulls<Any>(recordConstructor.parameterTypes.size)
        var instantCount = 0
        for (i in recordArgs.indices) {
            recordArgs[i] = when (recordConstructor.parameterTypes[i]) {
                Instant::class.java -> if (instantCount++ == 0) start else end
                ZoneOffset::class.java -> ZoneOffset.UTC
                String::class.java -> "sleep_$i"
                Metadata::class.java -> metadata
                List::class.java -> emptyList<Any>()
                else -> null
            }
        }
        return recordConstructor.newInstance(*recordArgs) as SleepSessionRecord
    }
}
