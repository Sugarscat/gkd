package com.sugarscat.jump.service

import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.sugarscat.jump.META
import com.sugarscat.jump.app
import com.sugarscat.jump.appScope
import com.sugarscat.jump.data.ActivityLog
import com.sugarscat.jump.data.AppRule
import com.sugarscat.jump.data.ClickLog
import com.sugarscat.jump.data.GlobalRule
import com.sugarscat.jump.data.ResolvedRule
import com.sugarscat.jump.data.SubsConfig
import com.sugarscat.jump.db.DbSet
import com.sugarscat.jump.isActivityVisible
import com.sugarscat.jump.util.RuleSummary
import com.sugarscat.jump.util.getDefaultLauncherAppId
import com.sugarscat.jump.util.increaseClickCount
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.recordStoreFlow
import com.sugarscat.jump.util.ruleSummaryFlow
import com.sugarscat.jump.util.storeFlow

data class TopActivity(
    val appId: String = "",
    val activityId: String? = null,
    val number: Int = 0
) {
    fun format(): String {
        return "${appId}/${activityId}/${number}"
    }
}

val topActivityFlow = MutableStateFlow(TopActivity())
private val activityLogMutex by lazy { Mutex() }

private var activityLogCount = 0
private var lastActivityChangeTime = 0L
fun updateTopActivity(topActivity: TopActivity) {
    val isSameActivity =
        topActivityFlow.value.appId == topActivity.appId && topActivityFlow.value.activityId == topActivity.activityId
    if (isSameActivity) {
        if (isActivityVisible() && topActivity.appId == com.sugarscat.jump.META.appId) {
            return
        }
        if (topActivityFlow.value.number == topActivity.number) {
            return
        }
        val t = System.currentTimeMillis()
        if (t - lastActivityChangeTime < 1000) {
            return
        }
    }
    if (storeFlow.value.enableActivityLog) {
        com.sugarscat.jump.appScope.launchTry(Dispatchers.IO) {
            activityLogMutex.withLock {
                DbSet.activityLogDao.insert(
                    ActivityLog(
                        appId = topActivity.appId,
                        activityId = topActivity.activityId
                    )
                )
                activityLogCount++
                if (activityLogCount % 100 == 0) {
                    DbSet.activityLogDao.deleteKeepLatest()
                }
            }
        }
    }
    LogUtils.d(
        "${topActivityFlow.value.format()} -> ${topActivity.format()}"
    )
    topActivityFlow.value = topActivity
    lastActivityChangeTime = System.currentTimeMillis()
}

data class ActivityRule(
    val appRules: List<AppRule> = emptyList(),
    val globalRules: List<GlobalRule> = emptyList(),
    val topActivity: TopActivity = TopActivity(),
    val ruleSummary: RuleSummary = RuleSummary(),
) {
    val currentRules = (appRules + globalRules).sortedBy { r -> r.order }
}

val activityRuleFlow by lazy { MutableStateFlow(ActivityRule()) }

private var lastTopActivity: TopActivity = topActivityFlow.value

private fun getFixTopActivity(): TopActivity {
    val top = topActivityFlow.value
    if (top.activityId == null) {
        if (lastTopActivity.appId == top.appId) {
            // 当从通知栏上拉返回应用, 从锁屏返回 等时, activityId 的无障碍事件不会触发, 此时复用上一次获得的 activityId 填充
            updateTopActivity(lastTopActivity)
        }
    } else {
        // 仅保留最近的有 activityId 的单个 TopActivity
        lastTopActivity = top
    }
    return topActivityFlow.value
}

fun getAndUpdateCurrentRules(): ActivityRule {
    val topActivity = getFixTopActivity()
    val oldActivityRule = activityRuleFlow.value
    val allRules = ruleSummaryFlow.value
    val idChanged = topActivity.appId != oldActivityRule.topActivity.appId
    val topChanged = idChanged || oldActivityRule.topActivity != topActivity
    val ruleChanged = oldActivityRule.ruleSummary !== allRules
    if (topChanged || ruleChanged) {
        val t = System.currentTimeMillis()
        val newActivityRule = ActivityRule(
            ruleSummary = allRules,
            topActivity = topActivity,
            appRules = (allRules.appIdToRules[topActivity.appId] ?: emptyList()).filter { rule ->
                rule.matchActivity(topActivity.appId, topActivity.activityId)
            },
            globalRules = ruleSummaryFlow.value.globalRules.filter { r ->
                r.matchActivity(topActivity.appId, topActivity.activityId)
            },
        )
        if (idChanged) {
            appChangeTime = t
            allRules.globalRules.forEach { r ->
                r.actionDelayTriggerTime = 0
                r.actionCount.value = 0
                r.matchChangedTime = t
            }
            allRules.appIdToRules[oldActivityRule.topActivity.appId]?.forEach { r ->
                r.actionDelayTriggerTime = 0
                r.actionCount.value = 0
                r.matchChangedTime = t
            }
            newActivityRule.appRules.forEach { r ->
                r.actionDelayTriggerTime = 0
                r.actionCount.value = 0
                r.matchChangedTime = t
            }
        } else {
            newActivityRule.currentRules.forEach { r ->
                if (r.resetMatchTypeWhenActivity) {
                    r.actionDelayTriggerTime = 0
                    r.actionCount.value = 0
                }
                if (!oldActivityRule.currentRules.contains(r)) {
                    // 新增规则
                    r.matchChangedTime = t
                }
            }
        }
        activityRuleFlow.value = newActivityRule
    }
    return activityRuleFlow.value
}

var lastTriggerRule: ResolvedRule? = null

@Volatile
var lastTriggerTime = 0L

@Volatile
var appChangeTime = 0L

var launcherAppId = ""
fun updateLauncherAppId() {
    launcherAppId = com.sugarscat.jump.app.packageManager.getDefaultLauncherAppId() ?: ""
}

val clickLogMutex by lazy { Mutex() }
suspend fun insertClickLog(rule: ResolvedRule) {
    clickLogMutex.withLock {
        actionCountFlow.value++
        val clickLog = ClickLog(
            appId = topActivityFlow.value.appId,
            activityId = topActivityFlow.value.activityId,
            subsId = rule.subsItem.id,
            subsVersion = rule.rawSubs.version,
            groupKey = rule.g.group.key,
            groupType = when (rule) {
                is AppRule -> SubsConfig.AppGroupType
                is GlobalRule -> SubsConfig.GlobalGroupType
            },
            ruleIndex = rule.index,
            ruleKey = rule.key,
        )
        DbSet.clickLogDao.insert(clickLog)
        if (actionCountFlow.value % 100 == 0L) {
            DbSet.clickLogDao.deleteKeepLatest()
        }
    }
}
