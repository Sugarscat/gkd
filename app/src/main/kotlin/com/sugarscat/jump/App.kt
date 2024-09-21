package com.sugarscat.jump

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils
import com.hjq.toast.Toaster
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import com.sugarscat.jump.data.selfAppInfo
import com.sugarscat.jump.debug.clearHttpSubs
import com.sugarscat.jump.notif.initChannel
import com.sugarscat.jump.service.JumpAbService
import com.sugarscat.jump.util.SafeR
import com.sugarscat.jump.util.initAppState
import com.sugarscat.jump.util.initFolder
import com.sugarscat.jump.util.initStore
import com.sugarscat.jump.util.initSubsState
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.setReactiveToastStyle
import org.lsposed.hiddenapibypass.HiddenApiBypass


val appScope by lazy { MainScope() }

private lateinit var innerApp: Application
val app: Application
    get() = com.sugarscat.jump.innerApp

val applicationInfo by lazy {
    com.sugarscat.jump.app.packageManager.getApplicationInfo(
        com.sugarscat.jump.app.packageName,
        PackageManager.GET_META_DATA
    )
}

data object META {
    val channel by lazy { com.sugarscat.jump.applicationInfo.metaData.getString("channel")!! }
    val commitId by lazy { com.sugarscat.jump.applicationInfo.metaData.getString("commitId")!! }
    val commitUrl by lazy { "https://github.com/sugarscat/jump/commit/${com.sugarscat.jump.META.commitId}" }
    val commitTime by lazy { com.sugarscat.jump.applicationInfo.metaData.getLong("commitTime") }
    val updateEnabled by lazy { com.sugarscat.jump.applicationInfo.metaData.getBoolean("updateEnabled") }
    val debuggable by lazy { com.sugarscat.jump.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    val versionCode by lazy { selfAppInfo.versionCode.toInt() }
    val versionName by lazy { selfAppInfo.versionName!! }
    val appId by lazy { selfAppInfo.id }
    val appName by lazy { com.sugarscat.jump.app.getString(SafeR.app_name) }
}

class App : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    override fun onCreate() {
        super.onCreate()
        com.sugarscat.jump.innerApp = this
        Utils.init(this)

        val errorHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            LogUtils.d("UncaughtExceptionHandler", t, e)
            errorHandler?.uncaughtException(t, e)
        }
        MMKV.initialize(this)

        Toaster.init(this)
        setReactiveToastStyle()

        LogUtils.getConfig().apply {
            setConsoleSwitch(com.sugarscat.jump.META.debuggable)
            saveDays = 7
            isLog2FileSwitch = true
        }
        LogUtils.d(
            "META", com.sugarscat.jump.META
        )
        initFolder()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                LogUtils.d("onActivityCreated", activity, savedInstanceState)
            }

            override fun onActivityStarted(activity: Activity) {
                LogUtils.d("onActivityStarted", activity)
            }

            override fun onActivityResumed(activity: Activity) {
                LogUtils.d("onActivityResumed", activity)
            }

            override fun onActivityPaused(activity: Activity) {
                LogUtils.d("onActivityPaused", activity)
            }

            override fun onActivityStopped(activity: Activity) {
                LogUtils.d("onActivityStopped", activity)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                LogUtils.d("onActivitySaveInstanceState", activity, outState)
            }

            override fun onActivityDestroyed(activity: Activity) {
                LogUtils.d("onActivityDestroyed", activity)
            }
        })
        com.sugarscat.jump.app.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    com.sugarscat.jump.a11yServiceEnabledFlow.value =
                        com.sugarscat.jump.getA11yServiceEnabled()
                }
            }
        )
        com.sugarscat.jump.appScope.launchTry(Dispatchers.IO) {
            initStore()
            initAppState()
            initSubsState()
            initChannel()
            clearHttpSubs()
            updatePermissionState()
        }
    }
}

val a11yServiceEnabledFlow by lazy { MutableStateFlow(com.sugarscat.jump.getA11yServiceEnabled()) }
private fun getA11yServiceEnabled(): Boolean {
    val value = try {
        Settings.Secure.getString(
            com.sugarscat.jump.app.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    } catch (_: Exception) {
        null
    }
    if (value.isNullOrEmpty()) return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(value)
    val name = ComponentName(com.sugarscat.jump.app, JumpAbService::class.java)
    while (colonSplitter.hasNext()) {
        if (ComponentName.unflattenFromString(colonSplitter.next()) == name) {
            return true
        }
    }
    return false
}
