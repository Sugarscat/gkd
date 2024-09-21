package com.sugarscat.jump.data

import com.blankj.utilcode.util.ScreenUtils
import kotlinx.serialization.Serializable
import com.sugarscat.jump.service.JumpAbService
import com.sugarscat.jump.service.getAndUpdateCurrentRules
import com.sugarscat.jump.service.safeActiveWindow

@Serializable
data class ComplexSnapshot(
    override val id: Long,

    override val appId: String?,
    override val activityId: String?,

    override val screenHeight: Int,
    override val screenWidth: Int,
    override val isLandscape: Boolean,

    val appInfo: AppInfo? = appId?.let { com.sugarscat.jump.app.packageManager.getPackageInfo(appId, 0)?.toAppInfo() },
    val jumpAppInfo: AppInfo? = selfAppInfo,
    val device: DeviceInfo = DeviceInfo.instance,

    @Deprecated("use appInfo")
    override val appName: String? = appInfo?.name,
    @Deprecated("use appInfo")
    override val appVersionCode: Long? = appInfo?.versionCode,
    @Deprecated("use appInfo")
    override val appVersionName: String? = appInfo?.versionName,

    val nodes: List<NodeInfo>,
) : BaseSnapshot


fun createComplexSnapshot(): ComplexSnapshot {
    val currentAbNode = JumpAbService.service?.safeActiveWindow
    val appId = currentAbNode?.packageName?.toString()
    val currentActivityId = getAndUpdateCurrentRules().topActivity.activityId

    return ComplexSnapshot(
        id = System.currentTimeMillis(),

        appId = appId,
        activityId = currentActivityId,

        screenHeight = ScreenUtils.getScreenHeight(),
        screenWidth = ScreenUtils.getScreenWidth(),
        isLandscape = ScreenUtils.isLandscape(),

        nodes = NodeInfo.info2nodeList(currentAbNode)
    )
}

fun ComplexSnapshot.toSnapshot(): Snapshot {
    return Snapshot(
        id = id,

        appId = appId,
        activityId = activityId,

        screenHeight = screenHeight,
        screenWidth = screenWidth,
        isLandscape = isLandscape,

        appName = appInfo?.name,
        appVersionCode = appInfo?.versionCode,
        appVersionName = appInfo?.versionName,
    )
}


