package com.sugarscat.jump.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.util.LruCache
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.StringUtils.getString
import com.sugarscat.jump.R
import com.sugarscat.jump.composition.CompositionAbService
import com.sugarscat.jump.composition.CompositionExt.useLifeCycleLog
import com.sugarscat.jump.composition.CompositionExt.useScope
import com.sugarscat.jump.data.ActionPerformer
import com.sugarscat.jump.data.ActionResult
import com.sugarscat.jump.data.AppRule
import com.sugarscat.jump.data.AttrInfo
import com.sugarscat.jump.data.JumpAction
import com.sugarscat.jump.data.ResolvedRule
import com.sugarscat.jump.data.RpcError
import com.sugarscat.jump.data.RuleStatus
import com.sugarscat.jump.data.clearNodeCache
import com.sugarscat.jump.debug.SnapshotExt
import com.sugarscat.jump.shizuku.getShizukuCanUsedFlow
import com.sugarscat.jump.shizuku.shizukuIsSafeOK
import com.sugarscat.jump.shizuku.useSafeGetTasksFc
import com.sugarscat.jump.shizuku.useSafeInputTapFc
import com.sugarscat.jump.shizuku.useShizukuAliveState
import com.sugarscat.jump.util.UpdateTimeOption
import com.sugarscat.jump.util.checkSubsUpdate
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.map
import com.sugarscat.jump.util.showActionToast
import com.sugarscat.jump.util.storeFlow
import com.sugarscat.jump.util.toast
import com.sugarscat.selector.MatchOption
import com.sugarscat.selector.Selector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class JumpAbService : CompositionAbService({
    useLifeCycleLog()
    updateLauncherAppId()

    val context = this as JumpAbService
    val scope = useScope()

    service = context
    onDestroy {
        service = null
    }

    val shizukuAliveFlow = useShizukuAliveState()
    val shizukuGrantFlow = MutableStateFlow(false)
    var lastCheckShizukuTime = 0L
    onAccessibilityEvent { // 借助无障碍轮询校验 shizuku 权限, 因为 shizuku 可能无故被关闭
        if ((storeFlow.value.enableShizukuActivity || storeFlow.value.enableShizukuClick) && it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {// 筛选降低判断频率
            val t = System.currentTimeMillis()
            if (t - lastCheckShizukuTime > 60_000L) {
                lastCheckShizukuTime = t
                scope.launchTry(Dispatchers.IO) {
                    shizukuGrantFlow.value = if (shizukuAliveFlow.value) {
                        shizukuIsSafeOK()
                    } else {
                        false
                    }
                }
            }
        }
    }
    val shizukuCanUsedFlow = getShizukuCanUsedFlow(
        scope,
        shizukuGrantFlow,
        shizukuAliveFlow,
        storeFlow.map(scope) { s -> s.enableShizukuActivity }
    )
    val safeGetTasksFc by lazy { useSafeGetTasksFc(scope, shizukuCanUsedFlow) }

    val shizukuClickCanUsedFlow = getShizukuCanUsedFlow(
        scope,
        shizukuGrantFlow,
        shizukuAliveFlow,
        storeFlow.map(scope) { s -> s.enableShizukuClick }
    )
    val safeInjectClickEventFc = useSafeInputTapFc(scope, shizukuClickCanUsedFlow)
    injectClickEventFc = safeInjectClickEventFc
    onDestroy {
        injectClickEventFc = null
    }

    // 当锁屏/上拉通知栏时, safeActiveWindow 没有 activityId, 但是此时 shizuku 获取到是前台 app 的 appId 和 activityId
    fun getShizukuTopActivity(): TopActivity? {
        if (!storeFlow.value.enableShizukuActivity) return null
        // 平均耗时 5 ms
        val top = safeGetTasksFc()?.lastOrNull()?.topActivity ?: return null
        return TopActivity(appId = top.packageName, activityId = top.className)
    }
    shizukuTopActivityGetter = ::getShizukuTopActivity
    onDestroy {
        shizukuTopActivityGetter = null
    }

    val activityCache = object : LruCache<Pair<String, String>, Boolean>(128) {
        override fun create(key: Pair<String, String>): Boolean {
            return kotlin.runCatching {
                packageManager.getActivityInfo(
                    ComponentName(
                        key.first, key.second
                    ), 0
                )
            }.getOrNull() != null
        }
    }

    fun isActivity(
        appId: String,
        activityId: String,
    ): Boolean {
        if (appId == topActivityFlow.value.appId && activityId == topActivityFlow.value.activityId) return true
        val cacheKey = Pair(appId, activityId)
        return activityCache.get(cacheKey)
    }

    var lastTriggerShizukuTime = 0L
    var lastContentEventTime = 0L
    val events = mutableListOf<AccessibilityNodeInfo>()
    var queryTaskJob: Job? = null
    fun newQueryTask(
        byEvent: Boolean = false,
        byForced: Boolean = false,
        delayRule: ResolvedRule? = null
    ) {
        if (!storeFlow.value.enableMatch) return
        queryTaskJob = scope.launchTry(queryThread) {
            var latestEvent = if (delayRule != null) {// 延迟规则不消耗事件
                null
            } else {
                synchronized(events) {
                    val size = events.size
                    if (size == 0 && byEvent) return@launchTry
                    val node = if (size > 1) {
                        if (com.sugarscat.jump.META.debuggable) {
                            Log.d("latestEvent", "丢弃事件=$size")
                        }
                        null
                    } else {
                        events.lastOrNull()
                    }
                    events.clear()
                    node
                }
            }
            val activityRule = getAndUpdateCurrentRules()
            if (activityRule.currentRules.isEmpty()) {
                return@launchTry
            }
            clearNodeCache()
            for (rule in activityRule.currentRules) { // 规则数量有可能过多导致耗时过长
                if (delayRule != null && delayRule !== rule) continue
                val statusCode = rule.status
                if (statusCode == RuleStatus.Status3 && rule.matchDelayJob == null) {
                    rule.matchDelayJob = scope.launch(actionThread) {
                        delay(rule.matchDelay)
                        rule.matchDelayJob = null
                        newQueryTask(delayRule = rule)
                    }
                }
                if (statusCode != RuleStatus.StatusOk) continue
                if (byForced && !rule.checkForced()) continue
                latestEvent?.let { n ->
                    val refreshOk = try {
                        n.refresh()
                    } catch (_: Exception) {
                        false
                    }
                    if (!refreshOk) {
                        if (com.sugarscat.jump.META.debuggable) {
                            Log.d("latestEvent", "最新事件已过期")
                        }
                        latestEvent = null
                    }
                }
                val nodeVal = (latestEvent ?: safeActiveWindow) ?: continue
                val rightAppId = nodeVal.packageName?.toString() ?: break
                val matchApp = rule.matchActivity(
                    rightAppId
                )
                if (topActivityFlow.value.appId != rightAppId || (!matchApp && rule is AppRule)) {
                    eventExecutor.execute {
                        if (topActivityFlow.value.appId != rightAppId) {
                            val shizukuTop = getShizukuTopActivity()
                            if (shizukuTop?.appId == rightAppId) {
                                updateTopActivity(shizukuTop)
                            } else {
                                updateTopActivity(TopActivity(appId = rightAppId))
                            }
                            getAndUpdateCurrentRules()
                            scope.launch(actionThread) {
                                delay(300)
                                if (queryTaskJob?.isActive != true) {
                                    newQueryTask()
                                }
                            }
                        }
                    }
                    return@launchTry
                }
                if (!matchApp) continue
                val target = rule.query(nodeVal, latestEvent == null) ?: continue
                if (activityRule !== getAndUpdateCurrentRules()) break
                if (rule.checkDelay() && rule.actionDelayJob == null) {
                    rule.actionDelayJob = scope.launch(actionThread) {
                        delay(rule.actionDelay)
                        rule.actionDelayJob = null
                        newQueryTask(delayRule = rule)
                    }
                    continue
                }
                if (rule.status != RuleStatus.StatusOk) break
                val actionResult = rule.performAction(context, target, safeInjectClickEventFc)
                if (actionResult.result) {
                    rule.trigger()
                    scope.launch(actionThread) {
                        delay(300)
                        if (queryTaskJob?.isActive != true) {
                            newQueryTask()
                        }
                    }
                    showActionToast(context)
                    com.sugarscat.jump.appScope.launchTry(Dispatchers.IO) {
                        insertClickLog(rule)
                        LogUtils.d(
                            rule.statusText(),
                            AttrInfo.info2data(target, 0, 0),
                            actionResult
                        )
                    }
                }
            }
            val t = System.currentTimeMillis()
            if (t - lastTriggerTime < 3000L || t - appChangeTime < 5000L) {
                scope.launch(actionThread) {
                    delay(300)
                    if (queryTaskJob?.isActive != true) {
                        newQueryTask()
                    }
                }
            } else {
                if (activityRule.currentRules.any { r -> r.checkForced() && r.status.let { s -> s == RuleStatus.StatusOk || s == RuleStatus.Status5 } }) {
                    scope.launch(actionThread) {
                        delay(300)
                        if (queryTaskJob?.isActive != true) {
                            newQueryTask(byForced = true)
                        }
                    }
                }
            }
        }
    }

    val skipAppIds = setOf("com.android.systemui")
    onAccessibilityEvent { event ->
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            skipAppIds.contains(event.packageName.toString())
        ) {
            return@onAccessibilityEvent
        }

        val fixedEvent = event.toAbEvent() ?: return@onAccessibilityEvent
        if (fixedEvent.type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (fixedEvent.time - lastContentEventTime < 100 && fixedEvent.time - appChangeTime > 5000 && fixedEvent.time - lastTriggerTime > 3000) {
                return@onAccessibilityEvent
            }
            lastContentEventTime = fixedEvent.time
        }
        if (com.sugarscat.jump.META.debuggable) {
            Log.d(
                "AccessibilityEvent",
                "type:${event.eventType},app:${event.packageName},cls:${event.className}"
            )
        }

        // AccessibilityEvent 的 clear 方法会在后续时间被 某些系统 调用导致内部数据丢失
        // 因此不要在协程/子线程内传递引用, 此处使用 data class 保存数据
        val evAppId = fixedEvent.appId
        val evActivityId = fixedEvent.className

        eventExecutor.execute launch@{
            val oldAppId = topActivityFlow.value.appId
            val rightAppId = if (oldAppId == evAppId) {
                oldAppId
            } else {
                safeActiveWindow?.packageName?.toString() ?: return@launch
            }
            if (rightAppId == evAppId) {
                if (fixedEvent.type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    // tv.danmaku.bili, com.miui.home, com.miui.home.launcher.Launcher
                    if (isActivity(evAppId, evActivityId)) {
                        updateTopActivity(
                            TopActivity(
                                evAppId, evActivityId, topActivityFlow.value.number + 1
                            )
                        )
                    }
                } else {
                    if (storeFlow.value.enableShizukuActivity && fixedEvent.time - lastTriggerShizukuTime > 300) {
                        val shizukuTop = getShizukuTopActivity()
                        if (shizukuTop != null && shizukuTop.appId == rightAppId) {
                            if (shizukuTop.activityId == evActivityId) {
                                updateTopActivity(
                                    TopActivity(
                                        evAppId, evActivityId, topActivityFlow.value.number + 1
                                    )
                                )
                            }
                            updateTopActivity(shizukuTop)
                        }
                        lastTriggerShizukuTime = fixedEvent.time
                    }
                }
            }
            if (rightAppId != topActivityFlow.value.appId) {
                // 从 锁屏,下拉通知栏 返回等情况, 应用不会发送事件, 但是系统组件会发送事件
                val shizukuTop = getShizukuTopActivity()
                if (shizukuTop?.appId == rightAppId) {
                    updateTopActivity(shizukuTop)
                } else {
                    updateTopActivity(TopActivity(rightAppId))
                }
            }

            if (getAndUpdateCurrentRules().currentRules.isEmpty()) {
                // 放在 evAppId != rightAppId 的前面使得 TopActivity 能借助 lastTopActivity 恢复
                return@launch
            }

            if (evAppId != rightAppId) {
                return@launch
            }
            if (!storeFlow.value.enableMatch) return@launch
            val eventNode = event.safeSource
            synchronized(events) {
                val eventLog = events.lastOrNull()
                if (eventNode != null) {
                    if (eventLog == eventNode) {
                        events.removeAt(events.lastIndex)
                    }
                    events.add(eventNode)
                }
            }
            newQueryTask(eventNode != null)
        }
    }

    var lastUpdateSubsTime = System.currentTimeMillis() - 25000
    onAccessibilityEvent {// 借助 无障碍事件 触发自动检测更新
        if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {// 筛选降低判断频率
            val i = storeFlow.value.updateSubsInterval
            if (i <= 0) return@onAccessibilityEvent
            val t = System.currentTimeMillis()
            if (t - lastUpdateSubsTime > i.coerceAtLeast(UpdateTimeOption.Everyday.value)) {
                lastUpdateSubsTime = t
                checkSubsUpdate()
            }
        }
    }

    scope.launch(Dispatchers.IO) {
        activityRuleFlow.debounce(300).collect {
            if (storeFlow.value.enableMatch && it.currentRules.isNotEmpty()) {
                LogUtils.d(it.topActivity, *it.currentRules.map { r ->
                    r.statusText()
                }.toTypedArray())
            }
        }
    }

    var aliveView: View? = null
    val wm by lazy { context.getSystemService(WINDOW_SERVICE) as WindowManager }
    onServiceConnected {
        scope.launchTry {
            storeFlow.map(scope) { s -> s.enableAbFloatWindow }.collect {
                if (aliveView != null) {
                    withContext(Dispatchers.Main) {
                        wm.removeView(aliveView)
                    }
                }
                if (it) {
                    val tempView = View(context)
                    val lp = WindowManager.LayoutParams().apply {
                        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        format = PixelFormat.TRANSLUCENT
                        flags =
                            flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        width = 1
                        height = 1
                        packageName = context.packageName
                    }
                    withContext(Dispatchers.Main) {
                        try {
                            // 在某些机型创建失败, 原因未知
                            wm.addView(tempView, lp)
                            aliveView = tempView
                        } catch (e: Exception) {
                            LogUtils.d("创建无障碍悬浮窗失败", e)
                            toast(getString(R.string.failed_to_create_accessible_floating_window))
                            storeFlow.update { store ->
                                store.copy(enableAbFloatWindow = false)
                            }
                        }
                    }
                } else {
                    aliveView = null
                }
            }
        }
    }
    onDestroy {
        if (aliveView != null) {
            wm.removeView(aliveView)
        }
    }

    val volumeChangedAction = "android.media.VOLUME_CHANGED_ACTION"
    fun createVolumeReceiver() = object : BroadcastReceiver() {
        var lastTriggerTime = -1L
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == volumeChangedAction) {
                val t = System.currentTimeMillis()
                if (t - lastTriggerTime > 3000 && !ScreenUtils.isScreenLock()) {
                    lastTriggerTime = t
                    scope.launchTry(Dispatchers.IO) {
                        SnapshotExt.captureSnapshot()
                        toast(getString(R.string.snapshot_succeeded))
                    }
                }
            }
        }
    }

    var captureVolumeReceiver: BroadcastReceiver? = null
    scope.launch {
        storeFlow.map(scope) { s -> s.captureVolumeChange }.collect {
            if (captureVolumeReceiver != null) {
                context.unregisterReceiver(captureVolumeReceiver)
            }
            captureVolumeReceiver = if (it) {
                createVolumeReceiver().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(
                            this, IntentFilter(volumeChangedAction), Context.RECEIVER_EXPORTED
                        )
                    } else {
                        context.registerReceiver(this, IntentFilter(volumeChangedAction))
                    }
                }
            } else {
                null
            }
        }
    }
    onDestroy {
        if (captureVolumeReceiver != null) {
            context.unregisterReceiver(captureVolumeReceiver)
        }
    }

    onAccessibilityEvent { e ->
        if (!storeFlow.value.captureScreenshot) return@onAccessibilityEvent
        val appId = e.packageName ?: return@onAccessibilityEvent
        val appCls = e.className ?: return@onAccessibilityEvent
        val screenshotThumbnailText = getString(R.string.screenshot_thumbnail)
        if (appId.contentEquals("com.miui.screenshot") && e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !e.isFullScreen && appCls.contentEquals(
                "android.widget.RelativeLayout"
            ) && e.text.firstOrNull()
                ?.contentEquals(screenshotThumbnailText) == true // [截屏缩略图, 截长屏, 发送]
        ) {
            LogUtils.d("captureScreenshot", e)
            scope.launchTry(Dispatchers.IO) {
                SnapshotExt.captureSnapshot(skipScreenshot = true)
            }
        }
    }

    isRunning.value = true
    onDestroy {
        isRunning.value = false
    }

    ManageService.autoStart(context)
    onDestroy {
        if (!storeFlow.value.enableStatusService && ManageService.isRunning.value) {
            ManageService.stop()
        }
    }
}) {

    companion object {
        // AccessibilityInteractionClient.getInstanceForThread(threadId)
        val queryThread by lazy { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }
        val eventExecutor by lazy { Executors.newSingleThreadExecutor()!! }
        val actionThread by lazy { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }

        var shizukuTopActivityGetter: (() -> TopActivity?)? = null
        private var injectClickEventFc: ((x: Float, y: Float) -> Boolean?)? = null
        var service: JumpAbService? = null

        val isRunning = MutableStateFlow(false)

        fun execAction(jumpAction: JumpAction): ActionResult {
            val accessibilityIsNotAvailableText = getString(R.string.accessibility_is_not_available)
            val illegalSelectorText = getString(R.string.illegal_selector)
            val noNodeFoundText = getString(R.string.no_node_found)
            val serviceVal = service ?: throw RpcError(accessibilityIsNotAvailableText)
            val selector =
                Selector.parseOrNull(jumpAction.selector) ?: throw RpcError(illegalSelectorText)
            selector.checkSelector()?.let {
                throw RpcError(it)
            }
            val targetNode = serviceVal.safeActiveWindow?.querySelector(
                selector,
                MatchOption(
                    quickFind = jumpAction.quickFind,
                    fastQuery = jumpAction.fastQuery,
                ),
                createCacheTransform().transform,
                isRootNode = true
            ) ?: throw RpcError(noNodeFoundText)

            if (jumpAction.action == null) {
                // 仅查询
                return ActionResult(
                    action = null,
                    result = true
                )
            }

            return ActionPerformer.getAction(jumpAction.action)
                .perform(serviceVal, targetNode, jumpAction.position, injectClickEventFc)
        }


        suspend fun currentScreenshot() = service?.run {
            suspendCoroutine {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    takeScreenshot(Display.DEFAULT_DISPLAY,
                        application.mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshot: ScreenshotResult) {
                                try {
                                    it.resume(
                                        Bitmap.wrapHardwareBuffer(
                                            screenshot.hardwareBuffer, screenshot.colorSpace
                                        )
                                    )
                                } finally {
                                    screenshot.hardwareBuffer.close()
                                }
                            }

                            override fun onFailure(errorCode: Int) = it.resume(null)
                        })
                } else {
                    it.resume(null)
                }
            }
        }
    }
}