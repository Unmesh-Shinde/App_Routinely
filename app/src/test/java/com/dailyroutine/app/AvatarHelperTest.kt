package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AvatarHelperTest {

    @Test
    fun getAvatarResourceId_returnsValidResourceForInBoundsId() {
        // Test a few specific mappings
        assertEquals(R.drawable.avatar_male_20, AvatarHelper.getAvatarResourceId(1))
        assertEquals(R.drawable.avatar_male_55, AvatarHelper.getAvatarResourceId(8))
        assertEquals(R.drawable.avatar_female_20, AvatarHelper.getAvatarResourceId(9))
        assertEquals(R.drawable.avatar_female_55, AvatarHelper.getAvatarResourceId(16))
    }

    @Test
    fun getAvatarResourceId_returnsFallbackForOutOfBoundsId() {
        val fallback = R.drawable.avatar_male_20
        assertEquals(fallback, AvatarHelper.getAvatarResourceId(0))
        assertEquals(fallback, AvatarHelper.getAvatarResourceId(17))
        assertEquals(fallback, AvatarHelper.getAvatarResourceId(-1))
    }

    @Test
    fun totalAvatars_matchesExpectedCount() {
        assertEquals(16, AvatarHelper.TOTAL_AVATARS)
    }

    @Test
    fun allAvatars_haveUniqueResources() {
        val resources = mutableSetOf<Int>()
        for (i in 1..AvatarHelper.TOTAL_AVATARS) {
            val resId = AvatarHelper.getAvatarResourceId(i)
            assertNotEquals("Avatar ID $i should have a valid resource", 0, resId)
            resources.add(resId)
        }
        assertEquals("Each avatar ID should map to a unique resource", AvatarHelper.TOTAL_AVATARS, resources.size)
    }
}
