package com.sugarscat.jump.composition

import android.view.accessibility.AccessibilityEvent

interface CanOnAccessibilityEvent {
    fun onAccessibilityEvent(f: (AccessibilityEvent) -> Unit): Boolean
}