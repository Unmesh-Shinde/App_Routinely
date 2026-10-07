package com.dailyroutine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HealthSyncWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun getNext2HourSlotTarget_whenBefore6AM_targets6AMToday() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 5)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val target = HealthSyncWorker.getNext2HourSlotTarget(now)

        assertEquals(6, target.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, target.get(Calendar.MINUTE))
        assertEquals(now.get(Calendar.DAY_OF_YEAR), target.get(Calendar.DAY_OF_YEAR))
        assertTrue(target.after(now))
    }

    @Test
    fun getNext2HourSlotTarget_whenExactlyAt6AM_targets8AMToday() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val target = HealthSyncWorker.getNext2HourSlotTarget(now)

        assertEquals(8, target.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, target.get(Calendar.MINUTE))
        assertEquals(now.get(Calendar.DAY_OF_YEAR), target.get(Calendar.DAY_OF_YEAR))
        assertTrue(target.after(now))
    }

    @Test
    fun getNext2HourSlotTarget_whenBetween6AMAnd8AM_targets8AMToday() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val target = HealthSyncWorker.getNext2HourSlotTarget(now)

        assertEquals(8, target.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, target.get(Calendar.MINUTE))
        assertEquals(now.get(Calendar.DAY_OF_YEAR), target.get(Calendar.DAY_OF_YEAR))
        assertTrue(target.after(now))
    }

    @Test
    fun getNext2HourSlotTarget_whenAt1130PM_targetsMidnightTomorrow() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val target = HealthSyncWorker.getNext2HourSlotTarget(now)

        assertEquals(0, target.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, target.get(Calendar.MINUTE))
        assertTrue(target.after(now))
    }

    @Test
    fun getNext2HourSlotTarget_whenAt4AM_targets6AMToday() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val target = HealthSyncWorker.getNext2HourSlotTarget(now)

        assertEquals(6, target.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, target.get(Calendar.MINUTE))
        assertEquals(now.get(Calendar.DAY_OF_YEAR), target.get(Calendar.DAY_OF_YEAR))
        assertTrue(target.after(now))
    }

    @Test
    fun getNext2HourSlotTarget_whenOnOddHour_targetsNextEvenHour() {
        val timesAndExpected = listOf(
            Pair(1, 15) to 6,   // 01:15 -> 06:00
            Pair(7, 45) to 8,   // 07:45 -> 08:00
            Pair(13, 1) to 14,  // 13:01 -> 14:00
            Pair(21, 30) to 22  // 21:30 -> 22:00
        )

        for ((pair, expectedHour) in timesAndExpected) {
            val (h, m) = pair
            val now = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val target = HealthSyncWorker.getNext2HourSlotTarget(now)
            assertEquals("Expected hour $expectedHour for $h:$m", expectedHour, target.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, target.get(Calendar.MINUTE))
            assertTrue(target.after(now))
        }
    }

    @Test
    fun getNext2HourSlotTarget_whenExactlyAtEvenSlot_targetsTwoHoursLater() {
        val slotTimes = listOf(0, 2, 6, 8, 12, 18, 22)

        for (h in slotTimes) {
            val now = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val target = HealthSyncWorker.getNext2HourSlotTarget(now)
            val expectedNext = when (h) {
                0, 2 -> 6
                22 -> 0
                else -> h + 2
            }
            assertEquals("Expected hour $expectedNext after $h:00", expectedNext, target.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, target.get(Calendar.MINUTE))
            assertTrue(target.after(now))
        }
    }

    @Test
    fun getNext2HourSlotTarget_whenAtLateNightBoundary_targetsMidnightTomorrow() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 0)
        }

        val target = HealthSyncWorker.getNext2HourSlotTarget(now)

        assertEquals(0, target.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, target.get(Calendar.MINUTE))
        assertTrue(target.after(now))
    }

    @Test
    fun shouldScheduleStepFollowUp_onlyForStepCarryingSyncModes() {
        assertTrue(HealthSyncWorker.shouldScheduleStepFollowUp(HealthSyncWorker.SYNC_MODE_STEPS))
        assertTrue(HealthSyncWorker.shouldScheduleStepFollowUp(HealthSyncWorker.SYNC_MODE_ALL))

        assertEquals(false, HealthSyncWorker.shouldScheduleStepFollowUp(HealthSyncWorker.SYNC_MODE_SLEEP))
        assertEquals(false, HealthSyncWorker.shouldScheduleStepFollowUp(HealthSyncWorker.SYNC_MODE_HISTORY))
    }

    @Test
    fun schedule2HourStepSync_enqueuesWorkWithTag() {
        HealthSyncWorker.schedule2HourStepSync(context)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosByTag(HealthSyncWorker.TAG_STEP_SYNC)
            .get()

        assertNotNull(workInfos)
        assertTrue("Work should be enqueued", workInfos.isNotEmpty())
    }

    @Test
    fun scheduleAutoSync_enqueuesAllScheduledWorkers() {
        HealthSyncWorker.scheduleAutoSync(context)

        val stepWork = WorkManager.getInstance(context)
            .getWorkInfosByTag(HealthSyncWorker.TAG_STEP_SYNC)
            .get()
        val sleepWork = WorkManager.getInstance(context)
            .getWorkInfosByTag(HealthSyncWorker.TAG_SLEEP_SYNC)
            .get()
        val historyWork = WorkManager.getInstance(context)
            .getWorkInfosByTag(HealthSyncWorker.TAG_HISTORY_SYNC)
            .get()

        assertTrue("Step sync should be scheduled", stepWork.isNotEmpty())
        assertTrue("Sleep sync should be scheduled (morning & evening)", sleepWork.size >= 2)
        assertTrue("History sync should be scheduled", historyWork.isNotEmpty())
    }
}
