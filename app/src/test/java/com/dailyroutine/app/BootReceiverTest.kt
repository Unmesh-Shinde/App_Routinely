package com.dailyroutine.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BootReceiverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun onReceive_bootCompleted_executesWithoutException() {
        val receiver = BootReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)
    }

    @Test
    fun onReceive_quickBootPowerOn_executesWithoutException() {
        val receiver = BootReceiver()
        val intent = Intent("android.intent.action.QUICKBOOT_POWERON")
        receiver.onReceive(context, intent)
    }

    @Test
    fun onReceive_unrelatedAction_doesNothing() {
        val receiver = BootReceiver()
        val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        receiver.onReceive(context, intent)
    }
}
