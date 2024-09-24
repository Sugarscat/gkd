package com.sugarscat.jump.service

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.sugarscat.jump.R
import com.sugarscat.jump.composition.CompositionExt.useLifeCycleLog
import com.sugarscat.jump.composition.CompositionExt.useScope
import com.sugarscat.jump.composition.CompositionService
import com.sugarscat.jump.notif.abNotif
import com.sugarscat.jump.notif.createNotif
import com.sugarscat.jump.notif.defaultChannel
import com.sugarscat.jump.util.actionCountFlow
import com.sugarscat.jump.util.getSubsStatus
import com.sugarscat.jump.util.ruleSummaryFlow
import com.sugarscat.jump.util.storeFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageService : CompositionService({
    useLifeCycleLog()
    val context = this
    createNotif(context, defaultChannel.id, abNotif)
    val scope = useScope()
    scope.launch {
        combine(
            JumpAbService.isRunning,
            storeFlow,
            ruleSummaryFlow,
            actionCountFlow,
        ) { abRunning, store, ruleSummary, count ->
            if (!abRunning) return@combine getString(R.string.accessibility_is_not_available)
            if (!store.enableMatch) return@combine getString(R.string.pause_rule_matching)
            if (store.useCustomNotIfText) {
                return@combine store.customNotIfText
                    .replace("\${i}", ruleSummary.globalGroups.size.toString())
                    .replace("\${k}", ruleSummary.appSize.toString())
                    .replace("\${u}", ruleSummary.appGroupSize.toString())
                    .replace("\${n}", count.toString())
            }
            return@combine getSubsStatus(ruleSummary, count)
        }.debounce(500L).stateIn(scope, SharingStarted.Eagerly, "").collect { text ->
            createNotif(
                context, defaultChannel.id, abNotif.copy(
                    text = text
                )
            )
        }
    }
    isRunning.value = true
    onDestroy {
        isRunning.value = false
    }
}) {
    companion object {
        fun start(context: Context = com.sugarscat.jump.app) {
            context.startForegroundService(Intent(context, ManageService::class.java))
        }

        val isRunning = MutableStateFlow(false)

        fun stop(context: Context = com.sugarscat.jump.app) {
            context.stopService(Intent(context, ManageService::class.java))
        }

        fun autoStart(context: Context) {
            // 在[系统重启]/[被其它高权限应用重启]时自动打开通知栏状态服务
            if (storeFlow.value.enableStatusService &&
                NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                !isRunning.value
            ) {
                start(context)
            }
        }
    }
}

