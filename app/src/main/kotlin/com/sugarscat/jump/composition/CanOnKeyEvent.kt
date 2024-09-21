package com.sugarscat.jump.composition

import android.view.KeyEvent

interface CanOnKeyEvent {
    fun onKeyEvent(f: (KeyEvent?) -> Unit): Unit
}