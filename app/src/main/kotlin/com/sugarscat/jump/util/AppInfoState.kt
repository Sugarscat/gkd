package com.sugarscat.jump.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import com.blankj.utilcode.util.LogUtils
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.sugarscat.jump.app
import com.sugarscat.jump.appScope
import com.sugarscat.jump.data.AppInfo
import com.sugarscat.jump.data.toAppInfo

val appInfoCacheFlow = MutableStateFlow(persistentMapOf<String, AppInfo>().toImmutableMap())

val systemAppInfoCacheFlow by lazy {
    appInfoCacheFlow.map(com.sugarscat.jump.appScope) { c ->
        c.filter { a -> a.value.isSystem }.toImmutableMap()
    }
}

val systemAppsFlow by lazy { systemAppInfoCacheFlow.map(com.sugarscat.jump.appScope) { c -> c.keys } }

val orderedAppInfosFlow by lazy {
    appInfoCacheFlow.map(com.sugarscat.jump.appScope) { c ->
        c.values.sortedWith { a, b ->
            collator.compare(a.name, b.name)
        }.toImmutableList()
    }
}

private val packageReceiver by lazy {
    object : BroadcastReceiver() {
        /**
         * 例: 小米应用商店更新应用产生连续 3个事件: PACKAGE_REMOVED->PACKAGE_ADDED->PACKAGE_REPLACED
         *
         */
        override fun onReceive(context: Context?, intent: Intent?) {
            val appId = intent?.data?.schemeSpecificPart ?: return
            if (intent.action == Intent.ACTION_PACKAGE_ADDED || intent.action == Intent.ACTION_PACKAGE_REPLACED || intent.action == Intent.ACTION_PACKAGE_REMOVED) {
                // update
                updateAppInfo(appId)
            }
        }
    }.apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            com.sugarscat.jump.app.registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }, Context.RECEIVER_EXPORTED)
        } else {
            com.sugarscat.jump.app.registerReceiver(
                this,
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addDataScheme("package")
                },
            )
        }
    }
}


private val updateAppMutex by lazy { Mutex() }

private fun updateAppInfo(appId: String) {
    com.sugarscat.jump.appScope.launchTry(Dispatchers.IO) {
        val packageManager = com.sugarscat.jump.app.packageManager
        updateAppMutex.withLock {
            val newMap = appInfoCacheFlow.value.toMutableMap()
            val info = try {
                packageManager.getPackageInfo(appId, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
            val newAppInfo = info?.toAppInfo()
            if (newAppInfo != null) {
                newMap[appId] = newAppInfo
            } else {
                newMap.remove(appId)
            }
            appInfoCacheFlow.value = newMap.toImmutableMap()
        }
    }
}

val appRefreshingFlow = MutableStateFlow(false)

suspend fun initOrResetAppInfoCache() {
    if (updateAppMutex.isLocked) return
    LogUtils.d("initOrResetAppInfoCache start")
    appRefreshingFlow.value = true
    updateAppMutex.withLock {
        val oldAppIds = appInfoCacheFlow.value.keys
        val appMap = appInfoCacheFlow.value.toMutableMap()
        withContext(Dispatchers.IO) {
            com.sugarscat.jump.app.packageManager.getInstalledPackages(0).forEach { packageInfo ->
                if (!oldAppIds.contains(packageInfo.packageName)) {
                    val info = packageInfo.toAppInfo()
                    if (info != null) {
                        appMap[packageInfo.packageName] = info
                    }
                }
            }
        }
        appInfoCacheFlow.value = appMap.toImmutableMap()
    }
    appRefreshingFlow.value = false
    LogUtils.d("initOrResetAppInfoCache end")
}

fun initAppState() {
    packageReceiver
    com.sugarscat.jump.appScope.launchTry(Dispatchers.IO) {
        initOrResetAppInfoCache()
    }
}