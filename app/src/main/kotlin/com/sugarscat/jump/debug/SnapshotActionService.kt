package com.sugarscat.jump.debug

import android.app.Service
import android.content.Intent
import android.os.Binder
import com.sugarscat.jump.appScope
import com.sugarscat.jump.util.launchTry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * https://github.com/gkd-kit/gkd/issues/253
 */
class SnapshotActionService : Service() {
    override fun onBind(intent: Intent?): Binder? = null
    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            delay(1000)
            stopSelf()
        }
        appScope.launchTry {
            SnapshotExt.captureSnapshot()
        }
    }
}