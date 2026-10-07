package com.dailyroutine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HealthDataManagerTest {
    private lateinit var context: Context
    private lateinit var manager: HealthDataManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE).edit().clear().commit()
        manager = HealthDataManager(context)
    }

    @Test
    fun pruneHistoricalData_keepsLast180DaysAndRemovesOlderHealthKeys() {
        val today = LocalDate.now()
        val keptDate = today.minusDays((HealthDataManager.SYNC_HISTORY_DAYS - 1).toLong()).toString()
        val oldDate = today.minusDays(HealthDataManager.SYNC_HISTORY_DAYS.toLong()).toString()
        val prefs = context.getSharedPreferences("health_data_pref", Context.MODE_PRIVATE)

        manager.saveHistoricalSteps(keptDate, 1234L)
        manager.saveHistoricalSleep(keptDate, 7.5)
        manager.saveHistoricalCalories(keptDate, 1800.0)
        manager.saveHistoricalHeartPoints(keptDate, 22.0)
        manager.adjustWaterIntake(keptDate, 1.5)
        manager.saveWeight(keptDate, 72.5)
        prefs.edit().putString("hist_dist_$keptDate", "2.00 km").commit()

        manager.saveHistoricalSteps(oldDate, 4321L)
        manager.saveHistoricalSleep(oldDate, 6.0)
        manager.saveHistoricalCalories(oldDate, 1600.0)
        manager.saveHistoricalHeartPoints(oldDate, 11.0)
        manager.adjustWaterIntake(oldDate, 2.0)
        manager.saveWeight(oldDate, 80.0)
        prefs.edit().putString("hist_dist_$oldDate", "4.00 km").commit()

        manager.pruneHistoricalData()

        assertEquals(1234L, manager.getHistoricalSteps(keptDate))
        assertTrue(manager.isHistoricalStepsSynced(keptDate))
        assertEquals(7.5, manager.getHistoricalSleep(keptDate), 0.001)
        assertEquals(22.0, manager.getHistoricalHeartPoints(keptDate), 0.001)
        assertEquals(1.5, manager.getWaterIntake(keptDate), 0.001)
        assertEquals(72.5, manager.getWeight(keptDate), 0.001)
        assertEquals(2.0, manager.getHistoricalDistance(keptDate), 0.001)

        assertFalse(prefs.contains("hist_steps_$oldDate"))
        assertFalse(prefs.contains("hist_steps_synced_$oldDate"))
        assertFalse(prefs.contains("hist_sleep_$oldDate"))
        assertFalse(prefs.contains("hist_cals_$oldDate"))
        assertFalse(prefs.contains("hist_active_cals_$oldDate"))
        assertFalse(prefs.contains("hist_heart_pts_$oldDate"))
        assertFalse(prefs.contains("hist_heart_pts_synced_$oldDate"))
        assertFalse(prefs.contains("hist_dist_$oldDate"))
        assertFalse(prefs.contains("water_intake_$oldDate"))
        assertEquals(0.0, manager.getWeight(oldDate), 0.001)
    }

    @Test
    fun calculateDistanceKm_usesHeightBasedStrideLength() {
        UserPreferencesStore.setUserHeight(context, 180.0)
        UserPreferencesStore.setUserGender(context, "Male")
        // 180 * 0.415 = 74.7cm = 0.747m
        // 1000 steps * 0.747m = 747m = 0.747km
        assertEquals(0.747, manager.calculateDistanceKm(1000), 0.001)

        UserPreferencesStore.setUserGender(context, "Female")
        // 180 * 0.413 = 74.34cm = 0.7434m
        // 1000 steps * 0.7434m = 743.4m = 0.7434km
        assertEquals(0.7434, manager.calculateDistanceKm(1000), 0.001)
    }

    @Test
    fun historicalStepCompletion_countsOnlySyncedMarkersInsideRetentionWindow() {
        val today = LocalDate.now()
        repeat(HealthDataManager.SYNC_HISTORY_DAYS) { offset ->
            manager.saveHistoricalSteps(today.minusDays(offset.toLong()).toString(), offset.toLong())
        }

        assertEquals(HealthDataManager.SYNC_HISTORY_DAYS, manager.countHistoricalStepSyncedDays())
        assertTrue(manager.isStepHistoryComplete())
    }

    @Test
    fun historicalHeartPointsCompletion_countsSyncedMarkersIncludingZeroPointDays() {
        val today = LocalDate.now()
        repeat(HealthDataManager.SYNC_HISTORY_DAYS) { offset ->
            manager.saveHistoricalHeartPoints(today.minusDays(offset.toLong()).toString(), 0.0)
        }

        assertEquals(HealthDataManager.SYNC_HISTORY_DAYS, manager.countHistoricalHeartPointsSyncedDays())
        assertTrue(manager.isHeartPointsHistoryComplete())
    }

    @Test
    fun distanceGoal_canBeSavedAndRetrieved() {
        manager.setDailyDistanceGoal(7.5f)
        assertEquals(7.5f, manager.getDailyDistanceGoal(), 0.001f)
    }

    @Test
    fun historicalActiveCalories_canBeSavedAndRetrieved() {
        val date = "2026-07-09"
        manager.saveHistoricalActiveCalories(date, 450.5)
        assertEquals(450.5, manager.getHistoricalActiveCalories(date), 0.001)
    }

    @Test
    fun historicalDistance_handlesFloatStorage() {
        val date = "2026-07-09"
        manager.saveHistoricalDistance(date, 5.25)
        assertEquals(5.25, manager.getHistoricalDistance(date), 0.001)
    }

    @Test
    fun normalizeDistanceKm_convertsLegacyMetersAndRejectsInvalidValues() {
        assertEquals(1.3, HealthDataManager.normalizeDistanceKm(1300.0), 0.001)
        assertEquals(2.0, HealthDataManager.normalizeDistanceKm(2.0), 0.001)
        assertEquals(0.0, HealthDataManager.normalizeDistanceKm(Double.NaN), 0.001)
        assertEquals(0.0, HealthDataManager.normalizeDistanceKm(-1.0), 0.001)
    }
}

