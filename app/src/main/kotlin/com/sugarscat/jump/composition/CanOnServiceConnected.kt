package com.sugarscat.jump.composition

interface CanOnServiceConnected {
    fun onServiceConnected(f: () -> Unit):Boolean
}