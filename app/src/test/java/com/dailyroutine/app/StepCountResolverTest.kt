package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepCountResolverTest {

    @Test
    fun resolve_withoutOriginFilter_returnsSelectedOriginValueOnly() {
        val resolved = StepCountResolver.resolve(
            selectedOriginSteps = 8_000L,
            allOriginSteps = 8_120L,
            hasOriginFilter = false
        )

        assertEquals(8_000L, resolved)
    }

    @Test
    fun resolve_withoutOriginFilter_preservesReadFailure() {
        val resolved = StepCountResolver.resolve(
            selectedOriginSteps = null,
            allOriginSteps = 8_120L,
            hasOriginFilter = false
        )

        assertNull(resolved)
    }

    @Test
    fun resolve_withOriginFilter_usesAllOriginWhenSelectedOriginReadFails() {
        val resolved = StepCountResolver.resolve(
            selectedOriginSteps = null,
            allOriginSteps = 8_120L,
            hasOriginFilter = true
        )

        assertEquals(8_120L, resolved)
    }

    @Test
    fun resolve_withOriginFilter_usesAllOriginWhenSelectedOriginIsZero() {
        val resolved = StepCountResolver.resolve(
            selectedOriginSteps = 0L,
            allOriginSteps = 8_120L,
            hasOriginFilter = true
        )

        assertEquals(8_120L, resolved)
    }

    @Test
    fun resolve_withOriginFilter_acceptsSmallAllOriginIncrease() {
        val resolved = StepCountResolver.resolve(
            selectedOriginSteps = 8_942L,
            allOriginSteps = 8_968L,
            hasOriginFilter = true
        )

        assertEquals(8_968L, resolved)
    }

    @Test
    fun resolve_withOriginFilter_keepsSelectedOriginWhenAllOriginIsSuspiciouslyHigher() {
        val resolved = StepCountResolver.resolve(
            selectedOriginSteps = 8_942L,
            allOriginSteps = 17_600L,
            hasOriginFilter = true
        )

        assertEquals(8_942L, resolved)
    }

    @Test
    fun resolve_withOriginFilter_keepsSelectedOriginWhenAllOriginIsLower() {
        val resolved = StepCountResolver.resolve(
            selectedOriginSteps = 8_942L,
            allOriginSteps = 8_900L,
            hasOriginFilter = true
        )

        assertEquals(8_942L, resolved)
    }
}
