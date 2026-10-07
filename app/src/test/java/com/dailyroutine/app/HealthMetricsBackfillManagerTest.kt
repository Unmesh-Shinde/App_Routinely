package com.dailyroutine.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HealthMetricsBackfillManagerTest {
    private lateinit var context: Context
    private lateinit var database: RoutinelyDatabase
    private lateinit var repository: HealthMetricsRepository
    private lateinit var manager: HealthMetricsBackfillManager

    private val today = LocalDate.of(2026, 7, 9)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        database = Room.inMemoryDatabaseBuilder(context, RoutinelyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HealthMetricsRepository(database.dailyHealthMetricDao())
        manager = HealthMetricsBackfillManager(
            context = context,
            roomWriter = HealthMetricsRoomWriter(repository),
            todayProvider = { today }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun backfillIfNeeded_copiesExistingHistoricalPrefsIntoRoom() = runBlocking {
        val date = today.toString()
        val healthPrefs = context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE)
        healthPrefs.edit()
            .putLong("hist_steps_$date", 8123L)
            .putBoolean("hist_steps_synced_$date", true)
            .putFloat("hist_sleep_$date", 7.25f)
            .putFloat("hist_cals_$date", 2140.5f)
            .putFloat("hist_heart_pts_$date", 19.5f)
            .putString("hist_dist_$date", "6.17 km")
            .putString("weight_history_data", Gson().toJson(mapOf(date to 72.8)))
            .commit()

        val result = manager.backfillIfNeeded()
        val metric = repository.getMetric(date)

        assertTrue(result.success)
        assertFalse(result.skipped)
        assertEquals(1, result.stepDays)
        assertEquals(1, result.sleepDays)
        assertEquals(1, result.calorieDays)
        assertEquals(1, result.heartPointDays)
        assertEquals(1, result.distanceDays)
        assertEquals(1, result.weightDays)
        assertEquals(8123L, metric?.steps)
        assertTrue(metric?.stepsSynced == true)
        assertEquals(7.25, metric?.sleepHours ?: 0.0, 0.001)
        assertEquals(2140.5, metric?.totalCalories ?: 0.0, 0.001)
        assertEquals(19.5, metric?.heartPoints ?: 0.0, 0.001)
        assertEquals(6.17, metric?.distanceKm ?: 0.0, 0.001)
        assertEquals(72.8, metric?.weightKg ?: 0.0, 0.001)
    }

    @Test
    fun backfillIfNeeded_doesNotInventStepSyncedMarkerWhenPrefsMarkerMissing() = runBlocking {
        val date = today.toString()
        context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE).edit()
            .putLong("hist_steps_$date", 0L)
            .commit()

        manager.backfillIfNeeded()

        val metric = repository.getMetric(date)
        assertEquals(0L, metric?.steps)
        assertFalse(metric?.stepsSynced == true)
    }

    @Test
    fun backfillIfNeeded_preservesExistingRoomStepSyncedWhenPrefsMarkerMissing() = runBlocking {
        val date = today.toString()
        repository.upsertSteps(date, steps = 5000L, synced = true)
        context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE).edit()
            .putLong("hist_steps_$date", 6000L)
            .commit()

        manager.backfillIfNeeded(force = true)

        val metric = repository.getMetric(date)
        assertEquals(6000L, metric?.steps)
        assertTrue(metric?.stepsSynced == true)
    }

    @Test
    fun backfillIfNeeded_parsesDistanceValueFromFormattedText() = runBlocking {
        val date = today.toString()
        context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE).edit()
            .putString("hist_dist_$date", "Distance: 4.32 km")
            .commit()

        manager.backfillIfNeeded()

        assertEquals(4.32, repository.getMetric(date)?.distanceKm ?: 0.0, 0.001)
    }

    @Test
    fun backfillIfNeeded_parsesDistanceValueFromFloat() = runBlocking {
        val date = today.toString()
        context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE).edit()
            .putFloat("hist_dist_$date", 5.67f)
            .commit()

        manager.backfillIfNeeded(force = true)

        assertEquals(5.67, repository.getMetric(date)?.distanceKm ?: 0.0, 0.001)
    }

    @Test
    fun backfillIfNeeded_prunesRoomRowsOlderThanRetentionWindow() = runBlocking {
        val oldDate = today.minusDays(HealthDataManager.SYNC_HISTORY_DAYS.toLong()).toString()
        val keptDate = today.minusDays((HealthDataManager.SYNC_HISTORY_DAYS - 1).toLong()).toString()
        repository.upsertSteps(oldDate, steps = 1000L, synced = true)
        repository.upsertSteps(keptDate, steps = 9000L, synced = true)

        val result = manager.backfillIfNeeded()

        assertTrue(result.success)
        assertNull(repository.getMetric(oldDate))
        assertEquals(9000L, repository.getMetric(keptDate)?.steps)
    }

    @Test
    fun backfillIfNeeded_skipsAfterSuccessfulOneTimeBackfill() = runBlocking {
        val date = today.toString()
        val healthPrefs = context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE)
        healthPrefs.edit().putLong("hist_steps_$date", 7000L).commit()
        manager.backfillIfNeeded()

        healthPrefs.edit().putLong("hist_steps_$date", 9000L).commit()
        val secondResult = manager.backfillIfNeeded()

        assertTrue(secondResult.skipped)
        assertTrue(secondResult.success)
        assertEquals(7000L, repository.getMetric(date)?.steps)
    }
}
