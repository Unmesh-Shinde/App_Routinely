package com.dailyroutine.app

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class HeaderAnimationStartupGateTest {

    @Test
    fun shouldPlayOnHomeCreate_onlyReturnsTrueForNullBundle() {
        assertTrue(HeaderAnimationStartupGate.shouldPlayOnHomeCreate(null))
        assertFalse(HeaderAnimationStartupGate.shouldPlayOnHomeCreate(android.os.Bundle()))
    }
}



