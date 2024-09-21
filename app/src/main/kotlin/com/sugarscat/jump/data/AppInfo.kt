package com.sugarscat.jump.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class AppInfo(
    val id: String,
    val name: String,
    @Transient
    val icon: Drawable? = null,
    val versionCode: Long,
    val versionName: String?,
    val isSystem: Boolean,
    val time: Long,
    val hidden: Boolean,
)

val selfAppInfo by lazy {
    com.sugarscat.jump.app.packageManager.getPackageInfo(com.sugarscat.jump.app.packageName, 0).toAppInfo()!!
}

/**
 * 平均单次调用时间 11ms
 */
fun PackageInfo.toAppInfo(): AppInfo? {
    applicationInfo ?: return null
    return AppInfo(
        id = packageName,
        name = applicationInfo.loadLabel(com.sugarscat.jump.app.packageManager).toString(),
        icon = applicationInfo.loadIcon(com.sugarscat.jump.app.packageManager),
        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        },
        versionName = versionName,
        isSystem = (ApplicationInfo.FLAG_SYSTEM and applicationInfo.flags) != 0,
        time = lastUpdateTime,
        hidden = com.sugarscat.jump.app.packageManager.getLaunchIntentForPackage(packageName) == null
    )
}