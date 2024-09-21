package com.sugarscat.jump.composition

interface CanOnInterrupt {
    fun onInterrupt(f: () -> Unit):Boolean
}