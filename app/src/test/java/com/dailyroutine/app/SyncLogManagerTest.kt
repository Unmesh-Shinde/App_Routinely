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
}
