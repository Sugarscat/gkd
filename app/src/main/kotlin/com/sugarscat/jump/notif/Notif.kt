package com.sugarscat.jump.notif

import com.blankj.utilcode.util.StringUtils.getString
import com.sugarscat.jump.R
import com.sugarscat.jump.app
import com.sugarscat.jump.util.SafeR


data class Notif(
    val id: Int,
    val smallIcon: Int = SafeR.ic_status,
    val title: String = app.getString(SafeR.app_name),
    val text: String,
    val ongoing: Boolean,
    val autoCancel: Boolean,
    val uri: String? = null,
)

val abNotif by lazy {
    Notif(
        id = 100,
        text = getString(R.string.accessibility_is_running),
        ongoing = true,
        autoCancel = false,
    )
}

val screenshotNotif by lazy {
    Notif(
        id = 101,
        text = getString(R.string.screenshot_service_is_running),
        ongoing = true,
        autoCancel = false,
        uri = "gkd://page/1",
    )
}

val floatingNotif by lazy {
    Notif(
        id = 102,
        text = getString(R.string.dropzone_button_is_displayed),
        ongoing = true,
        autoCancel = false,
        uri = "gkd://page/1",
    )
}

val httpNotif by lazy {
    Notif(
        id = 103,
        text = getString(R.string.http_service_is_running),
        ongoing = true,
        autoCancel = false,
        uri = "gkd://page/1",
    )
}