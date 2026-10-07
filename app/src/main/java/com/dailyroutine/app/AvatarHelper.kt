package com.dailyroutine.app

import android.widget.ImageView
import coil.load
import coil.transform.CircleCropTransformation

object AvatarHelper {
    const val TOTAL_AVATARS = 16

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

    fun applyAvatar(view: ImageView, avatarId: Int) {
        val resId = getAvatarResourceId(avatarId)
        view.load(resId) {
            crossfade(true)
            transformations(CircleCropTransformation())
        }
    }
}
