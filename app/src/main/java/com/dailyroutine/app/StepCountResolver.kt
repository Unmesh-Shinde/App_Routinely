package com.dailyroutine.app

import kotlin.math.roundToLong

object StepCountResolver {
    internal const val MIN_SAFE_ALL_ORIGIN_EXTRA_STEPS = 250L
    internal const val MAX_SAFE_ALL_ORIGIN_EXTRA_STEPS = 750L
    internal const val MAX_SAFE_ALL_ORIGIN_EXTRA_RATIO = 0.03

    fun resolve(
        selectedOriginSteps: Long?,
        allOriginSteps: Long?,
        hasOriginFilter: Boolean
    ): Long? {
        if (!hasOriginFilter) return selectedOriginSteps

        if (selectedOriginSteps == null) return allOriginSteps

        if (selectedOriginSteps <= 0L) {
            return if (allOriginSteps != null && allOriginSteps > 0L) allOriginSteps else selectedOriginSteps
        }

        if (allOriginSteps == null || allOriginSteps <= selectedOriginSteps) return selectedOriginSteps

        val extraSteps = allOriginSteps - selectedOriginSteps
        return if (extraSteps <= safeExtraStepLimit(selectedOriginSteps)) {
            allOriginSteps
        } else {
            selectedOriginSteps
        }
    }

    private fun safeExtraStepLimit(selectedOriginSteps: Long): Long {
        val ratioLimit = (selectedOriginSteps * MAX_SAFE_ALL_ORIGIN_EXTRA_RATIO).roundToLong()
        return maxOf(MIN_SAFE_ALL_ORIGIN_EXTRA_STEPS, ratioLimit)
            .coerceAtMost(MAX_SAFE_ALL_ORIGIN_EXTRA_STEPS)
    }
}
