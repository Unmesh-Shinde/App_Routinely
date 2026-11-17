package com.dailyroutine.app

import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Pure retry state transitions shared by the worker and unit tests. */
internal object NutritionRetryPolicy {
    private val dayRetryDelaysMinutes = intArrayOf(30, 45, 60)
    private val overnightSlotsHours = intArrayOf(0, 2, 6, 8)

    fun queueFirstRetry(current: Meal, errorCode: String, now: Long): Meal = current.copy(
        isPendingOvernightRetry = true,
        queuedTimestamp = current.queuedTimestamp.takeIf { it > 0L } ?: now,
        manualRetryAttempted = false,
        nutritionErrorCode = errorCode,
        nutritionDayRetriesUsed = 1,
        nutritionOvernightRetriesUsed = 0,
        nutritionRetryStage = "day",
        nextNutritionRetryAt = now + TimeUnit.MINUTES.toMillis(dayRetryDelaysMinutes[0].toLong()),
        nutritionAutoRetriesExhausted = false,
        nutritionOvernightWindowStartAt = 0L
    )

    fun afterRetryableFailure(current: Meal, errorCode: String, now: Long): Meal {
        return when (current.nutritionRetryStage ?: "day") {
            "day" -> {
                val scheduledRetries = current.nutritionDayRetriesUsed + 1
                if (scheduledRetries <= dayRetryDelaysMinutes.size) {
                    current.copy(
                        nutritionErrorCode = errorCode,
                        nutritionDayRetriesUsed = scheduledRetries,
                        nextNutritionRetryAt = now + TimeUnit.MINUTES.toMillis(dayRetryDelaysMinutes[scheduledRetries - 1].toLong()),
                        nutritionRetryStage = "day"
                    )
                } else {
                    val nextSlot = nextOvernightSlot(now)
                    if (nextSlot == null) exhausted(current, errorCode)
                    else current.copy(
                        nutritionErrorCode = errorCode,
                        nutritionDayRetriesUsed = dayRetryDelaysMinutes.size,
                        nutritionRetryStage = "overnight",
                        nutritionOvernightRetriesUsed = 0,
                        nutritionOvernightWindowStartAt = nextSlot.first,
                        nextNutritionRetryAt = nextSlot.second
                    )
                }
            }
            "overnight" -> {
                val used = current.nutritionOvernightRetriesUsed + 1
                if (used >= overnightSlotsHours.size) exhausted(current, errorCode)
                else {
                    val nextSlot = nextOvernightSlot(now, current.nutritionOvernightWindowStartAt)
                    if (nextSlot == null) exhausted(current, errorCode)
                    else current.copy(
                        nutritionErrorCode = errorCode,
                        nutritionOvernightRetriesUsed = used,
                        nextNutritionRetryAt = nextSlot.second
                    )
                }
            }
            else -> exhausted(current, errorCode)
        }
    }

    fun afterPermanentFailure(current: Meal, errorCode: String): Meal = exhausted(current, errorCode)

    fun deferForBudget(current: Meal, errorCode: String, retryAt: Long, now: Long): Meal = current.copy(
        nutritionErrorCode = errorCode,
        nextNutritionRetryAt = retryAt.takeIf { it > now } ?: now + TimeUnit.MINUTES.toMillis(30)
    )

    fun nextOvernightSlot(afterMillis: Long, anchorMillis: Long = 0L): Pair<Long, Long>? {
        val now = Calendar.getInstance().apply { timeInMillis = afterMillis }
        val day = if (anchorMillis > 0L) {
            Calendar.getInstance().apply { timeInMillis = anchorMillis }
        } else {
            (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
        for (offset in 0..1) {
            val candidateDay = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            for (hour in overnightSlotsHours) {
                val candidate = (candidateDay.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (candidate.timeInMillis > afterMillis && (anchorMillis == 0L || offset == 0)) {
                    val anchor = (candidate.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis
                    return anchor to candidate.timeInMillis
                }
            }
            if (anchorMillis > 0L) return null
        }
        return null
    }

    private fun exhausted(meal: Meal, errorCode: String) = meal.copy(
        isPendingOvernightRetry = false,
        nutritionErrorCode = errorCode,
        nutritionRetryStage = null,
        nextNutritionRetryAt = 0L,
        nutritionAutoRetriesExhausted = true
    )
}
