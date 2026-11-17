package com.dailyroutine.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.ImageView
import coil.load
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object AvatarHelper {
    const val TOTAL_AVATARS = 16
    const val MAX_CUSTOM_AVATAR_BYTES = 10L * 1024L * 1024L

    private fun customAvatarFile(context: Context) = File(context.filesDir, "profile_avatar")

    fun clearCustomAvatar(context: Context) {
        customAvatarFile(context).delete()
    }

    /** Copies the selected image into app-private storage, enforcing the limit on actual bytes read. */
    fun saveCustomAvatar(context: Context, uri: Uri) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Unable to open the selected image.")
        input.use { saveCustomAvatar(context, it) }
    }

    internal fun saveCustomAvatar(context: Context, source: InputStream) {
        val destination = customAvatarFile(context)
        val temporary = File(context.filesDir, "profile_avatar.tmp")
        var copied = 0L
        try {
            source.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > MAX_CUSTOM_AVATAR_BYTES) {
                            throw IllegalArgumentException("Choose an image that is 10 MB or smaller.")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (!temporary.renameTo(destination)) {
                destination.delete()
                if (!temporary.renameTo(destination)) {
                    throw IllegalStateException("Unable to save the selected image.")
                }
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    fun saveCroppedAvatar(context: Context, bitmap: Bitmap) {
        val destination = customAvatarFile(context)
        val temporary = File(context.filesDir, "profile_avatar.tmp")
        var workingBitmap = bitmap
        try {
            var bytes = compressCroppedBitmap(workingBitmap)
            if (bytes.size > MAX_CUSTOM_AVATAR_BYTES) {
                val scaled = Bitmap.createScaledBitmap(
                    workingBitmap,
                    (workingBitmap.width / 2).coerceAtLeast(1),
                    (workingBitmap.height / 2).coerceAtLeast(1),
                    true
                )
                if (scaled !== workingBitmap) workingBitmap = scaled
                bytes = compressCroppedBitmap(workingBitmap)
            }
            if (bytes.size > MAX_CUSTOM_AVATAR_BYTES) {
                throw IllegalArgumentException("The cropped image is too large to save.")
            }
            FileOutputStream(temporary).use { it.write(bytes) }
            if (!temporary.renameTo(destination)) {
                destination.delete()
                if (!temporary.renameTo(destination)) {
                    throw IllegalStateException("Unable to save the selected image.")
                }
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun compressCroppedBitmap(bitmap: Bitmap): ByteArray {
        var quality = 92
        var bytes: ByteArray
        do {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            bytes = output.toByteArray()
            quality -= 7
        } while (bytes.size > MAX_CUSTOM_AVATAR_BYTES && quality >= 64)
        return bytes
    }

    private val avatarResources = intArrayOf(
        R.drawable.avatar_male_20,
        R.drawable.avatar_male_25,
        R.drawable.avatar_male_30,
        R.drawable.avatar_male_35,
        R.drawable.avatar_male_40,
        R.drawable.avatar_male_45,
        R.drawable.avatar_male_50,
        R.drawable.avatar_male_55,
        R.drawable.avatar_female_20,
        R.drawable.avatar_female_25,
        R.drawable.avatar_female_30,
        R.drawable.avatar_female_35,
        R.drawable.avatar_female_40,
        R.drawable.avatar_female_45,
        R.drawable.avatar_female_50,
        R.drawable.avatar_female_55
    )

    fun getAvatarResourceId(avatarId: Int): Int {
        val index = avatarId - 1
        return if (index in avatarResources.indices) {
            avatarResources[index]
        } else {
            avatarResources[0]
        }
    }

    fun applyAvatar(view: ImageView, avatarId: Int, useCustomAvatar: Boolean = true) {
        val customAvatar = customAvatarFile(view.context)
        val image: Any = if (useCustomAvatar && customAvatar.isFile) customAvatar else getAvatarResourceId(avatarId)
        view.load(image) {
            crossfade(true)
        }
    }
}
