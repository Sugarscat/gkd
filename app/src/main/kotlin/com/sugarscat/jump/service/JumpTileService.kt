package com.sugarscat.jump.service

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.flow.update
import com.sugarscat.jump.permission.writeSecureSettingsState
import com.sugarscat.jump.util.lastRestartA11yServiceTimeFlow
import com.sugarscat.jump.util.storeFlow
import com.sugarscat.jump.util.toast

class JumpTileService : TileService() {
    private fun updateTile(): Boolean {
        val oldState = qsTile.state
        val newState = if (JumpAbService.isRunning.value) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        if (oldState != newState) {
            qsTile.state = newState
            qsTile.updateTile()
            return true
        }
        return false
    }

    private fun autoUpdateTile() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!updateTile()) {
                Handler(Looper.getMainLooper()).postDelayed(::updateTile, 250)
            }
        }, 250)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        if (fixRestartService()) {
            autoUpdateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (switchA11yService()) {
            autoUpdateTile()
        }
    }
}

private fun getServiceNames(): MutableList<String> {
    val value = try {
        Settings.Secure.getString(
            com.sugarscat.jump.app.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    } catch (_: Exception) {
        null
    } ?: ""
    if (value.isEmpty()) return mutableListOf()
    return value.split(':').toMutableList()
}

private fun updateServiceNames(names: List<String>) {
    Settings.Secure.putString(
        com.sugarscat.jump.app.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        names.joinToString(":")
    )
}

private fun enableA11yService() {
    Settings.Secure.putInt(
        com.sugarscat.jump.app.contentResolver,
        Settings.Secure.ACCESSIBILITY_ENABLED,
        1
    )
}

fun switchA11yService(): Boolean {
    if (!writeSecureSettingsState.updateAndGet()) {
        toast("请先授予[写入安全设置]权限")
        return false
    }
    val names = getServiceNames()
    if (JumpAbService.isRunning.value) {
        names.remove(a11yClsName)
        updateServiceNames(names)
        storeFlow.update { it.copy(enableService = false) }
        toast("关闭无障碍")
    } else {
        enableA11yService()
        if (names.contains(a11yClsName)) { // 当前无障碍异常, 重启服务
            names.remove(a11yClsName)
            updateServiceNames(names)
        }
        names.add(a11yClsName)
        updateServiceNames(names)
        storeFlow.update { it.copy(enableService = true) }
        toast("开启无障碍")
    }
    return true
}

fun fixRestartService(): Boolean {
    // 1. 服务没有运行
    // 2. 用户配置开启了服务
    // 3. 有写入系统设置权限
    if (!JumpAbService.isRunning.value && storeFlow.value.enableService && writeSecureSettingsState.updateAndGet()) {
        val t = System.currentTimeMillis()
        if (t - lastRestartA11yServiceTimeFlow.value < 10_000) return false
        lastRestartA11yServiceTimeFlow.value = t
        val names = getServiceNames()
        val a11yBroken = names.contains(a11yClsName)
        if (a11yBroken) {
            // 无障碍出现故障, 重启服务
            names.remove(a11yClsName)
            updateServiceNames(names)
        }
        names.add(a11yClsName)
        updateServiceNames(names)
        toast("重启无障碍")
        return true
    }
    return false
}

private val a11yClsName by lazy {
    ComponentName(com.sugarscat.jump.app, JumpAbService::class.java).flattenToShortString()
}