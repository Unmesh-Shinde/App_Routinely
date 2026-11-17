package com.dailyroutine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AvatarHelperStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val avatarFile = File(context.filesDir, "profile_avatar")
    private val temporaryFile = File(context.filesDir, "profile_avatar.tmp")

    @Before
    fun setUp() {
        cleanUpAvatarFiles()
    }

    @After
    fun tearDown() {
        cleanUpAvatarFiles()
    }

    private fun cleanUpAvatarFiles() {
        avatarFile.delete()
        temporaryFile.delete()
    }

    @Test
    fun saveCustomAvatar_acceptsImageOfExactlyTenMegabytes() {
        val data = ByteArray(AvatarHelper.MAX_CUSTOM_AVATAR_BYTES.toInt()) { 7 }

        AvatarHelper.saveCustomAvatar(context, ByteArrayInputStream(data))

        assertEquals(AvatarHelper.MAX_CUSTOM_AVATAR_BYTES, avatarFile.length())
    }

    @Test
    fun saveCustomAvatar_rejectsImageOverTenMegabytesAndPreservesExistingAvatar() {
        val original = byteArrayOf(1, 2, 3, 4)
        AvatarHelper.saveCustomAvatar(context, ByteArrayInputStream(original))
        val oversized = ByteArray(AvatarHelper.MAX_CUSTOM_AVATAR_BYTES.toInt() + 1) { 9 }

        assertThrows(IllegalArgumentException::class.java) {
            AvatarHelper.saveCustomAvatar(context, ByteArrayInputStream(oversized))
        }

        assertArrayEquals(original, avatarFile.readBytes())
        assertEquals(false, temporaryFile.exists())
    }
}
