package com.sugarscat.jump.debug

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import com.sugarscat.jump.app
import com.sugarscat.jump.composition.CompositionExt.useLifeCycleLog
import com.sugarscat.jump.composition.CompositionService
import com.sugarscat.jump.notif.createNotif
import com.sugarscat.jump.notif.screenshotChannel
import com.sugarscat.jump.notif.screenshotNotif
import com.sugarscat.jump.util.ScreenshotUtil

class ScreenshotService : CompositionService({
    useLifeCycleLog()
    createNotif(this, screenshotChannel.id, screenshotNotif)

    onStartCommand { intent, _, _ ->
        if (intent == null) return@onStartCommand
        screenshotUtil?.destroy()
        screenshotUtil = ScreenshotUtil(this, intent)
        LogUtils.d("screenshot restart")
    }
    onDestroy {
        screenshotUtil?.destroy()
        screenshotUtil = null
    }

    isRunning.value = true
    onDestroy {
        isRunning.value = false
    }
}) {
    companion object {
        suspend fun screenshot() = screenshotUtil?.execute()

        @SuppressLint("StaticFieldLeak")
        private var screenshotUtil: ScreenshotUtil? = null

        fun start(context: Context = com.sugarscat.jump.app, intent: Intent) {
            intent.component = ComponentName(context, ScreenshotService::class.java)
            context.startForegroundService(intent)
        }

        val isRunning = MutableStateFlow(false)
        fun stop(context: Context = com.sugarscat.jump.app) {
            context.stopService(Intent(context, ScreenshotService::class.java))
        }
    }
}