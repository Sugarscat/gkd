package com.sugarscat.jump.permission

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.StringUtils.getString
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.sugarscat.jump.R
import com.sugarscat.jump.app
import com.sugarscat.jump.service.fixRestartService
import com.sugarscat.jump.shizuku.newActivityTaskManager
import com.sugarscat.jump.shizuku.safeGetTasks
import com.sugarscat.jump.shizuku.shizukuIsSafeOK
import com.sugarscat.jump.util.initOrResetAppInfoCache
import com.sugarscat.jump.util.launchTry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PermissionState(
    val check: () -> Boolean,
    val request: (suspend (context: Activity) -> PermissionResult)? = null,
    /**
     * show it when user doNotAskAgain
     */
    val reason: AuthReason? = null,
) {
    val stateFlow = MutableStateFlow(false)
    fun updateAndGet(): Boolean {
        return stateFlow.updateAndGet { check() }
    }
}

private fun checkSelfPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(
        app,
        permission
    ) == PackageManager.PERMISSION_GRANTED
}

private suspend fun asyncRequestPermission(
    context: Activity,
    permission: String,
): PermissionResult {
    if (XXPermissions.isGranted(context, permission)) {
        return PermissionResult.Granted
    }
    return suspendCoroutine { continuation ->
        XXPermissions.with(context)
            .unchecked()
            .permission(permission)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (allGranted) {
                        continuation.resume(PermissionResult.Granted)
                    } else {
                        continuation.resume(PermissionResult.Denied(false))
                    }
                }

                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                    continuation.resume(PermissionResult.Denied(doNotAskAgain))
                }
            })
    }
}

val permissionSettingsText: String = getString(R.string.go_to_app_permission_settings)

val notificationState by lazy {
    PermissionState(
        check = {
            XXPermissions.isGranted(app, Permission.NOTIFICATION_SERVICE)
        },
        request = {
            asyncRequestPermission(it, Permission.POST_NOTIFICATIONS)
        },
        reason = AuthReason(
            text = getString(R.string.notification_permission_required) + permissionSettingsText,
            confirm = {
                XXPermissions.startPermissionActivity(app, Permission.POST_NOTIFICATIONS)
            }
        ),
    )
}

val canQueryPkgState by lazy {
    PermissionState(
        check = {
            XXPermissions.isGranted(app, Permission.GET_INSTALLED_APPS)
        },
        request = {
            asyncRequestPermission(it, Permission.GET_INSTALLED_APPS)
        },
        reason = AuthReason(
            text = getString(R.string.get_installed_apps_permission_required) + permissionSettingsText,
            confirm = {
                XXPermissions.startPermissionActivity(app, Permission.GET_INSTALLED_APPS)
            }
        ),
    )
}

val canDrawOverlaysState by lazy {
    PermissionState(
        check = {
            Settings.canDrawOverlays(app)
        },
        request = {
            // 无法直接请求悬浮窗权限
            if (!Settings.canDrawOverlays(app)) {
                PermissionResult.Denied(true)
            } else {
                PermissionResult.Granted
            }
        },
        reason = AuthReason(
            text = getString(R.string.system_alert_window_permission_required) + permissionSettingsText,
            confirm = {
                XXPermissions.startPermissionActivity(app, Manifest.permission.SYSTEM_ALERT_WINDOW)
            }
        ),
    )
}

val canWriteExternalStorage by lazy {
    PermissionState(
        check = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                true
            }
        },
        request = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                asyncRequestPermission(it, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                PermissionResult.Granted
            }
        },
        reason = AuthReason(
            text = getString(R.string.write_external_storage_permission_required) + permissionSettingsText,
            confirm = {
                XXPermissions.startPermissionActivity(
                    app,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        ),
    )
}

val writeSecureSettingsState by lazy {
    PermissionState(
        check = { checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) },
    )
}

val shizukuOkState by lazy {
    PermissionState(
        check = {
            shizukuIsSafeOK() && (try {
                // 打开 shizuku 点击右上角停止, 此时 shizukuIsSafeOK() == true, 因此需要二次检查状态
                newActivityTaskManager()?.safeGetTasks(log = false)?.isNotEmpty() == true
            } catch (e: Exception) {
                false
            })
        },
    )
}

private val checkAuthMutex by lazy { Mutex() }
suspend fun updatePermissionState() {
    if (checkAuthMutex.isLocked) return
    checkAuthMutex.withLock {
        arrayOf(
            notificationState,
            canDrawOverlaysState,
            canWriteExternalStorage,
        ).forEach { it.updateAndGet() }
        if (canQueryPkgState.stateFlow.value != canQueryPkgState.updateAndGet()) {
            com.sugarscat.jump.appScope.launchTry {
                initOrResetAppInfoCache()
            }
        }
        if (writeSecureSettingsState.stateFlow.value != writeSecureSettingsState.updateAndGet()) {
            fixRestartService()
        }
        shizukuOkState.updateAndGet()
    }
}