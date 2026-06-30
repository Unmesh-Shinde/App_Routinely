package com.dailyroutine.app

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object InsetHelper {

    fun applyTopPadding(view: View) {
        val baseLeft = view.paddingLeft
        val baseTop = view.paddingTop
        val baseRight = view.paddingRight
        val baseBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            target.setPadding(
                baseLeft + maxOf(bars.left, cutout.left),
                baseTop + maxOf(bars.top, cutout.top),
                baseRight + maxOf(bars.right, cutout.right),
                baseBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun applyBottomPadding(view: View) {
        val baseLeft = view.paddingLeft
        val baseTop = view.paddingTop
        val baseRight = view.paddingRight
        val baseBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            target.setPadding(
                baseLeft + maxOf(bars.left, cutout.left),
                baseTop,
                baseRight + maxOf(bars.right, cutout.right),
                baseBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun applyBottomMargin(view: View) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val baseLeft = params.leftMargin
        val baseTop = params.topMargin
        val baseRight = params.rightMargin
        val baseBottom = params.bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val lp = target.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null) {
                lp.setMargins(
                    baseLeft + maxOf(bars.left, cutout.left),
                    baseTop,
                    baseRight + maxOf(bars.right, cutout.right),
                    baseBottom + bars.bottom
                )
                target.layoutParams = lp
            }
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
