package com.dailyroutine.app

import android.graphics.Color

data class WeeklyGraphItem(
    val dayLabel: String,
    val dateLabel: String,
    val primaryValue: String,
    val primaryHeightPx: Int,
    val primaryColor: Int,
    val primaryBackgroundRes: Int = 0,
    val secondaryValue: String? = null,
    val secondaryHeightPx: Int = 0,
    val secondaryColor: Int = Color.TRANSPARENT,
    val secondaryBackgroundRes: Int = 0,
    val showSecondaryBar: Boolean = true,
    val isSecondaryStacked: Boolean = false,
    val secondaryBottomHeightPx: Int = 0,
    val secondaryBottomColor: Int = Color.TRANSPARENT,
    val secondaryTopHeightPx: Int = 0,
    val secondaryTopColor: Int = Color.TRANSPARENT,
    val hasSignal: Boolean = true,
    val isDivider: Boolean = false
)
