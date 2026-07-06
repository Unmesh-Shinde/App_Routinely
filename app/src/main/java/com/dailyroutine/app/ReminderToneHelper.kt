package com.dailyroutine.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri

object ReminderToneHelper {
    const val MAX_TONE_DURATION_MS = 6_000L

    @Volatile
    private var cachedSystemTones: List<ToneOption>? = null

    data class ToneOption(
        val title: String,
        val uri: Uri?
    )

    fun getToneTitle(context: Context, uriString: String?): String {
        if (uriString.isNullOrBlank()) return "Default Notification Tone"

        return runCatching {
            val uri = Uri.parse(uriString)
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull() ?: "Custom Audio File"
    }

    fun eligibleSystemNotificationTones(context: Context): List<ToneOption> {
        cachedSystemTones?.let { return it }

        return synchronized(this) {
            cachedSystemTones ?: loadEligibleSystemNotificationTones(context.applicationContext).also {
                cachedSystemTones = it
            }
        }
    }

    fun cachedSystemNotificationTones(): List<ToneOption>? = cachedSystemTones

    fun preloadSystemNotificationTones(context: Context) {
        if (cachedSystemTones != null) return
        Thread {
            eligibleSystemNotificationTones(context.applicationContext)
        }.start()
    }

    private fun loadEligibleSystemNotificationTones(context: Context): List<ToneOption> {
        val tones = mutableListOf<ToneOption>()
        tones.add(ToneOption("Default Notification Tone", null))

        val manager = RingtoneManager(context).apply {
            setType(RingtoneManager.TYPE_NOTIFICATION)
        }
        val cursor = manager.cursor ?: return tones

        for (position in 0 until cursor.count) {
            val uri = runCatching { manager.getRingtoneUri(position) }.getOrNull() ?: continue
            if (!isToneDurationAllowed(context, uri)) continue
            val title = runCatching { manager.getRingtone(position)?.getTitle(context) }.getOrNull() ?: continue
            tones.add(ToneOption(title, uri))
        }

        return tones.distinctBy { it.uri?.toString().orEmpty() }
    }

    fun isToneDurationAllowed(context: Context, uri: Uri): Boolean {
        val durationMs = getDurationMs(context, uri) ?: return false
        return durationMs in 1..MAX_TONE_DURATION_MS
    }

    fun durationWarningText(): String {
        return "Choose a notification sound that is 6 seconds or shorter. Longer audio is not suitable for reminder alerts."
    }

    private fun getDurationMs(context: Context, uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
