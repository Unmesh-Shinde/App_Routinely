package com.dailyroutine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class SyncLogManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sync_logs_pref", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun addLog_savesAndRetrievesLogs() {
        SyncLogManager.addLog(context, "HealthConnect", "Synced 5000 steps", true)

        val logs = SyncLogManager.getLogs(context)
        assertEquals(1, logs.size)
        assertEquals("HealthConnect", logs[0].source)
        assertEquals("Synced 5000 steps", logs[0].message)
        assertTrue(logs[0].isSuccess)
        assertNotNull(logs[0].formatTime())
    }

    @Test
    fun addLog_trimsLogsToMax50() {
        for (i in 1..60) {
            SyncLogManager.addLog(context, "HealthConnect", "Sync #$i", true)
        }

        val logs = SyncLogManager.getLogs(context)
        assertEquals(50, logs.size)
        // Newest entry should be at index 0
        assertEquals("Sync #60", logs[0].message)
    }

    @Test
    fun getLogs_returnsEmptyList_whenCorruptedJson() {
        context.getSharedPreferences("sync_logs_pref", Context.MODE_PRIVATE)
            .edit()
            .putString("daily_sync_logs", "invalid_json_{}")
            .commit()

        val logs = SyncLogManager.getLogs(context)
        assertTrue(logs.isEmpty())
    }

    @Test
    fun getLogs_resetsPreviousOperationalDayAtSixAm() {
        val prefs = context.getSharedPreferences("sync_logs_pref", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("daily_sync_logs", "[{\"timestamp\":1,\"source\":\"HealthConnect\",\"message\":\"Yesterday\",\"isSuccess\":true}]")
            .putString("last_log_reset_date", "2026-06-04")
            .commit()
        val sixAm = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JUNE, 5, 6, 0, 0)
        }

        val logs = SyncLogManager.getLogs(context, sixAm)

        assertTrue(logs.isEmpty())
        assertEquals("2026-06-05", prefs.getString("last_log_reset_date", null))
    }

    @Test
    fun getLogs_keepsPreviousOperationalDayBeforeSixAm() {
        val prefs = context.getSharedPreferences("sync_logs_pref", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("daily_sync_logs", "[{\"timestamp\":1,\"source\":\"HealthConnect\",\"message\":\"Midnight sync\",\"isSuccess\":true}]")
            .putString("last_log_reset_date", "2026-06-04")
            .commit()
        val beforeSixAm = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JUNE, 5, 5, 59, 0)
        }

        val logs = SyncLogManager.getLogs(context, beforeSixAm)

        assertEquals(1, logs.size)
        assertEquals("Midnight sync", logs[0].message)
        assertEquals("2026-06-04", prefs.getString("last_log_reset_date", null))
    }
}
