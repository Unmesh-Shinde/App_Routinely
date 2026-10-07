package com.dailyroutine.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HealthMetricsRepositoryTest {
    private lateinit var database: RoutinelyDatabase
    private lateinit var repository: HealthMetricsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RoutinelyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HealthMetricsRepository(database.dailyHealthMetricDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertMetric_mergesPartialUpdatesWithoutOverwritingExistingFields() = runBlocking {
        repository.upsertSteps("2026-07-09", steps = 8000L, synced = true)
        repository.upsertSleep("2026-07-09", sleepHours = 7.25)
        repository.upsertTotalCalories("2026-07-09", calories = 2100.0)
        repository.upsertHeartPoints("2026-07-09", heartPoints = 18.0)
        repository.upsertDistance("2026-07-09", distanceKm = 5.6)
        repository.upsertWeight("2026-07-09", weightKg = 72.4)

        val metric = repository.getMetric("2026-07-09")

        assertEquals(8000L, metric?.steps)
        assertTrue(metric?.stepsSynced == true)
        assertEquals(7.25, metric?.sleepHours ?: 0.0, 0.001)
        assertEquals(2100.0, metric?.totalCalories ?: 0.0, 0.001)
        assertEquals(18.0, metric?.heartPoints ?: 0.0, 0.001)
        assertEquals(5.6, metric?.distanceKm ?: 0.0, 0.001)
        assertEquals(72.4, metric?.weightKg ?: 0.0, 0.001)
    }

    @Test
    fun getMetricsBetween_returnsInclusiveOrderedDateRange() = runBlocking {
        repository.upsertSteps("2026-07-08", 7000L)
        repository.upsertSteps("2026-07-09", 8000L)
        repository.upsertSteps("2026-07-10", 9000L)

        val metrics = repository.getMetricsBetween("2026-07-09", "2026-07-10")

        assertEquals(listOf("2026-07-09", "2026-07-10"), metrics.map { it.date })
        assertEquals(listOf(8000L, 9000L), metrics.map { it.steps })
    }

    @Test
    fun pruneOlderThan_deletesOnlyDatesBeforeCutoff() = runBlocking {
        repository.upsertSteps("2026-01-01", 1000L)
        repository.upsertSteps("2026-07-08", 7000L)
        repository.upsertSteps("2026-07-09", 8000L)

        val deleted = repository.pruneOlderThan("2026-07-08")

        assertEquals(1, deleted)
        assertNull(repository.getMetric("2026-01-01"))
        assertFalse(repository.getMetricsBetween("2026-07-08", "2026-07-09").isEmpty())
        assertEquals(2, repository.getMetricsBetween("2026-07-08", "2026-07-09").size)
    }
}

