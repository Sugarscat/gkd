package com.sugarscat.jump.notif

import com.blankj.utilcode.util.StringUtils.getString
import com.sugarscat.jump.R
import com.sugarscat.jump.app

data class NotifChannel(
    val id: String,
    val name: String,
    val desc: String,
)

val defaultChannel by lazy {
    NotifChannel(
        id = "default",
        name = getString(R.string.app_service),
        desc = getString(R.string.desc_app_service)
    )
}

val floatingChannel by lazy {
    NotifChannel(
        id = "floating",
        name = getString(R.string.floating_service),
        desc = getString(R.string.floating_service_desc)
    )
}
val screenshotChannel by lazy {
    NotifChannel(
        id = "screenshot",
        name = getString(R.string.screenshot_service),
        desc = getString(R.string.screenshot_service_desc)
    )
}
val httpChannel by lazy {
    NotifChannel(
        id = "http",
        name = getString(R.string.http_service),
        desc = getString(R.string.desc_http_service)
    )
}

fun initChannel() {
    createChannel(app, defaultChannel)
    createChannel(app, floatingChannel)
    createChannel(app, screenshotChannel)
    createChannel(app, httpChannel)
}