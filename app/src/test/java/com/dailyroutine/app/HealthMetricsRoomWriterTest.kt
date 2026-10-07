package com.dailyroutine.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HealthMetricsRoomWriterTest {
    private lateinit var database: RoutinelyDatabase
    private lateinit var repository: HealthMetricsRepository
    private lateinit var writer: HealthMetricsRoomWriter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RoutinelyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HealthMetricsRepository(database.dailyHealthMetricDao())
        writer = HealthMetricsRoomWriter(repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertMetric_mergesPartialDualWritesForSameDate() = runBlocking {
        assertTrue(writer.upsertMetric(date = "2026-07-09", steps = 8500L, stepsSynced = true))
        assertTrue(writer.upsertMetric(date = "2026-07-09", sleepHours = 7.5))
        assertTrue(writer.upsertMetric(date = "2026-07-09", totalCalories = 2200.0))
        assertTrue(writer.upsertMetric(date = "2026-07-09", heartPoints = 21.0))
        assertTrue(writer.upsertMetric(date = "2026-07-09", distanceKm = 6.2))
        assertTrue(writer.upsertMetric(date = "2026-07-09", weightKg = 73.1))

        val metric = repository.getMetric("2026-07-09")

        assertEquals(8500L, metric?.steps)
        assertTrue(metric?.stepsSynced == true)
        assertEquals(7.5, metric?.sleepHours ?: 0.0, 0.001)
        assertEquals(2200.0, metric?.totalCalories ?: 0.0, 0.001)
        assertEquals(21.0, metric?.heartPoints ?: 0.0, 0.001)
        assertEquals(6.2, metric?.distanceKm ?: 0.0, 0.001)
        assertEquals(73.1, metric?.weightKg ?: 0.0, 0.001)
    }

    @Test
    fun pruneToRetentionWindow_deletesOnlyRowsBeforeRetentionCutoff() = runBlocking {
        val today = java.time.LocalDate.now()
        val keptDate = today.minusDays((HealthDataManager.SYNC_HISTORY_DAYS - 1).toLong()).toString()
        val oldDate = today.minusDays(HealthDataManager.SYNC_HISTORY_DAYS.toLong()).toString()

        assertTrue(writer.upsertMetric(date = oldDate, steps = 1000L, stepsSynced = true))
        assertTrue(writer.upsertMetric(date = keptDate, steps = 9000L, stepsSynced = true))
        assertTrue(writer.pruneToRetentionWindow())

        assertNull(repository.getMetric(oldDate))
        assertEquals(9000L, repository.getMetric(keptDate)?.steps)
    }
}
