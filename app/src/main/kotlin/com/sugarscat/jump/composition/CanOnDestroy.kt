package com.sugarscat.jump.composition

interface CanOnDestroy {
    fun onDestroy(f: () -> Unit): Boolean
}