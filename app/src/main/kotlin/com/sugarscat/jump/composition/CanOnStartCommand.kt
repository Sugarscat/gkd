package com.sugarscat.jump.composition

interface CanOnStartCommand {
    fun onStartCommand(f: StartCommandHook): Boolean
}