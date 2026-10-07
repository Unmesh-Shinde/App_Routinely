package com.dailyroutine.app

import android.os.Bundle

object HeaderAnimationStartupGate {
    fun shouldPlayOnHomeCreate(savedInstanceState: Bundle?): Boolean = savedInstanceState == null
}



