package com.sugarscat.jump.util

import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.StringUtils.getString
import com.sugarscat.jump.R
import com.sugarscat.jump.appScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

private inline fun <reified T> createJsonFlow(
    key: String,
    crossinline default: () -> T,
    crossinline transform: (T) -> T = { it }
): MutableStateFlow<T> {
    val str = kv.getString(key, null)
    val initValue = if (str != null) {
        try {
            json.decodeFromString<T>(str)
        } catch (e: Exception) {
            e.printStackTrace()
            LogUtils.d(e)
            null
        }
    } else {
        null
    }
    val stateFlow = MutableStateFlow(transform(initValue ?: default()))
    appScope.launch {
        stateFlow.drop(1).collect {
            withContext(Dispatchers.IO) {
                kv.encode(key, json.encodeToString(it))
            }
        }
    }
    return stateFlow
}

private fun createLongFlow(
    key: String,
    default: Long = 0,
    transform: (Long) -> Long = { it }
): MutableStateFlow<Long> {
    val stateFlow = MutableStateFlow(transform(kv.getLong(key, default)))
    appScope.launch {
        stateFlow.drop(1).collect {
            withContext(Dispatchers.IO) { kv.encode(key, it) }
        }
    }
    return stateFlow
}

@Serializable
data class Store(
    val enableService: Boolean = true,
    val enableMatch: Boolean = true,
    val enableStatusService: Boolean = true,
    val excludeFromRecents: Boolean = false,
    val captureScreenshot: Boolean = false,
    val httpServerPort: Int = 8888,
    val updateSubsInterval: Long = UpdateTimeOption.Everyday.value,
    val captureVolumeChange: Boolean = false,
    val autoCheckAppUpdate: Boolean = com.sugarscat.jump.META.updateEnabled,
    val toastWhenClick: Boolean = true,
    val clickToast: String = "Jump",
    val autoClearMemorySubs: Boolean = true,
    val hideSnapshotStatusBar: Boolean = false,
    val enableShizukuActivity: Boolean = false,
    val enableShizukuClick: Boolean = false,
    val log2FileSwitch: Boolean = true,
    val enableDarkTheme: Boolean? = null,
    val enableDynamicColor: Boolean = true,
    val enableAbFloatWindow: Boolean = true,
    val sortType: Int = SortTypeOption.SortByName.value,
    val showSystemApp: Boolean = true,
    val showHiddenApp: Boolean = false,
    val showSaveSnapshotToast: Boolean = true,
    val useSystemToast: Boolean = false,
    val useCustomNotIfText: Boolean = false,
    val customNotIfText: String = getString(R.string.global_app_rule_trigger),
    val enableActivityLog: Boolean = false,
    val updateChannel: Int = if (com.sugarscat.jump.META.versionName.contains("beta")) UpdateChannelOption.Beta.value else UpdateChannelOption.Stable.value,
)

val storeFlow by lazy {
    createJsonFlow(
        key = "store-v2",
        default = { Store() },
        transform = {
            if (UpdateTimeOption.allSubObject.all { e -> e.value != it.updateSubsInterval }) {
                it.copy(
                    updateSubsInterval = UpdateTimeOption.Everyday.value
                )
            } else {
                it
            }
        }
    )
}

//@Deprecated("use actionCountFlow instead")
@Serializable
private data class RecordStore(
    val clickCount: Int = 0,
)

//@Deprecated("use actionCountFlow instead")
private val recordStoreFlow by lazy {
    createJsonFlow(
        key = "record_store-v2",
        default = { RecordStore() }
    )
}

val actionCountFlow by lazy {
    createLongFlow(
        key = "action_count",
        transform = {
            if (it == 0L) {
                recordStoreFlow.value.clickCount.toLong()
            } else {
                it
            }
        }
    )
}

@Serializable
data class PrivacyStore(
    val githubCookie: String? = null,
)

val privacyStoreFlow by lazy {
    createJsonFlow(
        key = "privacy_store",
        default = { PrivacyStore() }
    )
}

val lastRestartA11yServiceTimeFlow by lazy {
    createLongFlow("last_restart_a11y_service_time")
}

fun initStore() {
    storeFlow.value
    actionCountFlow.value
}
