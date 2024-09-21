package com.sugarscat.jump.debug

import android.app.Service
import android.content.Intent
import android.os.Binder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.sugarscat.jump.appScope
import com.sugarscat.jump.util.launchTry

/**
 * https://github.com/gkd-kit/gkd/issues/253
 */
class SnapshotActionService : Service() {
    override fun onBind(intent: Intent?): Binder? = null
    override fun onCreate() {
        super.onCreate()
        com.sugarscat.jump.appScope.launch {
            delay(1000)
            stopSelf()
        }
        com.sugarscat.jump.appScope.launchTry {
            SnapshotExt.captureSnapshot()
        }
    }
}