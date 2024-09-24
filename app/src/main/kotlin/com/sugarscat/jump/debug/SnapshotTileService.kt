package com.sugarscat.jump.debug

import android.accessibilityservice.AccessibilityService
import android.service.quicksettings.TileService
import com.blankj.utilcode.util.LogUtils
import com.sugarscat.jump.R
import com.sugarscat.jump.debug.SnapshotExt.captureSnapshot
import com.sugarscat.jump.service.JumpAbService
import com.sugarscat.jump.service.JumpAbService.Companion.eventExecutor
import com.sugarscat.jump.service.JumpAbService.Companion.shizukuTopActivityGetter
import com.sugarscat.jump.service.TopActivity
import com.sugarscat.jump.service.getAndUpdateCurrentRules
import com.sugarscat.jump.service.safeActiveWindow
import com.sugarscat.jump.service.updateTopActivity
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

class SnapshotTileService : TileService() {
    override fun onClick() {
        super.onClick()
        LogUtils.d("SnapshotTileService::onClick")
        val service = JumpAbService.service
        if (service == null) {
            toast(getString(R.string.accessibility_is_not_available))
            return
        }
        com.sugarscat.jump.appScope.launchTry(Dispatchers.IO) {
            val oldAppId = service.safeActiveWindow?.packageName?.toString()
                ?: return@launchTry toast(getString(R.string.failed_to_get_the_root_node))

            val startTime = System.currentTimeMillis()
            fun timeout(): Boolean {
                return System.currentTimeMillis() - startTime > 3000L
            }

            val timeoutText = getString(R.string.no_interface_switch_was_detected)
            while (true) {
                val latestAppId = service.safeActiveWindow?.packageName?.toString()
                if (latestAppId == null) {
                    // https://github.com/gkd-kit/gkd/issues/713
                    delay(250)
                    if (timeout()) {
                        toast(timeoutText)
                        break
                    }
                } else if (latestAppId != oldAppId) {
                    LogUtils.d("SnapshotTileService::eventExecutor.execute")
                    eventExecutor.execute {
                        updateTopActivity(
                            shizukuTopActivityGetter?.invoke() ?: TopActivity(appId = latestAppId)
                        )
                        getAndUpdateCurrentRules()
                        com.sugarscat.jump.appScope.launchTry(Dispatchers.IO) {
                            captureSnapshot()
                        }
                    }
                    break
                } else {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    delay(500)
                    if (timeout()) {
                        toast(timeoutText)
                        break
                    }
                }
            }
        }
    }

}