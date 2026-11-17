package com.dailyroutine.app

object StepCountResolver {
    fun resolve(
        selectedOriginSteps: Long?,
        allOriginSteps: Long?,
        hasOriginFilter: Boolean
    ): Long? {
        if (!hasOriginFilter) return selectedOriginSteps
        // Health Connect's unfiltered aggregate is the platform's deduplicated total. A
        // provider-filtered aggregate is only a partial view and must never be heuristically
        // added to other origins: even a small delta can be genuine activity or duplication.
        // Fall back to the selected result only when the all-origin aggregate failed.
        return allOriginSteps ?: selectedOriginSteps
    }
}
