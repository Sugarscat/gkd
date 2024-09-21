package com.sugarscat.jump.debug

import android.app.Service
import android.content.Context
import android.content.Intent
import com.blankj.utilcode.util.LogUtils
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import com.sugarscat.jump.data.AppInfo
import com.sugarscat.jump.data.DeviceInfo
import com.sugarscat.jump.data.JumpAction
import com.sugarscat.jump.data.RawSubscription
import com.sugarscat.jump.data.RpcError
import com.sugarscat.jump.data.SubsItem
import com.sugarscat.jump.data.deleteSubscription
import com.sugarscat.jump.data.selfAppInfo
import com.sugarscat.jump.db.DbSet
import com.sugarscat.jump.debug.SnapshotExt.captureSnapshot
import com.sugarscat.jump.notif.createNotif
import com.sugarscat.jump.notif.httpChannel
import com.sugarscat.jump.notif.httpNotif
import com.sugarscat.jump.service.JumpAbService
import com.sugarscat.jump.util.LOCAL_HTTP_SUBS_ID
import com.sugarscat.jump.util.SERVER_SCRIPT_URL
import com.sugarscat.jump.util.getIpAddressInLocalNetwork
import com.sugarscat.jump.util.keepNullJson
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.map
import com.sugarscat.jump.util.storeFlow
import com.sugarscat.jump.util.subsItemsFlow
import com.sugarscat.jump.util.toast
import com.sugarscat.jump.util.updateSubscription
import java.io.File


class HttpService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)

    private var server: CIOApplicationEngine? = null
    override fun onCreate() {
        super.onCreate()
        isRunning.value = true
        localNetworkIpsFlow.value = getIpAddressInLocalNetwork()
        val httpServerPortFlow = storeFlow.map(scope) { s -> s.httpServerPort }
        scope.launchTry(Dispatchers.IO) {
            httpServerPortFlow.collect { port ->
                server?.stop()
                server = try {
                    createServer(port).apply { start() }
                } catch (e: Exception) {
                    LogUtils.d("HTTP服务启动失败", e)
                    null
                }
                if (server == null) {
                    toast("HTTP服务启动失败,您可以尝试切换端口后重新启动")
                    stopSelf()
                    return@collect
                }
                createNotif(
                    this@HttpService,
                    httpChannel.id,
                    httpNotif.copy(text = "HTTP服务-端口$port")
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.value = false
        localNetworkIpsFlow.value = emptyList()

        scope.launchTry(Dispatchers.IO) {
            server?.stop()
            if (storeFlow.value.autoClearMemorySubs) {
                deleteSubscription(LOCAL_HTTP_SUBS_ID)
            }
            delay(3000)
            scope.cancel()
        }
    }

    companion object {
        val isRunning = MutableStateFlow(false)
        val localNetworkIpsFlow = MutableStateFlow(emptyList<String>())
        fun stop(context: Context = com.sugarscat.jump.app) {
            context.stopService(Intent(context, HttpService::class.java))
        }

        fun start(context: Context = com.sugarscat.jump.app) {
            context.startForegroundService(Intent(context, HttpService::class.java))
        }
    }

    override fun onBind(intent: Intent?) = null
}

@Serializable
data class RpcOk(
    val message: String? = null,
)

@Serializable
data class ReqId(
    val id: Long,
)

@Serializable
data class ServerInfo(
    val device: DeviceInfo = DeviceInfo.instance,
    val jumpAppInfo: AppInfo = selfAppInfo
)

fun clearHttpSubs() {
    // 如果 app 被直接在任务列表划掉, HTTP订阅会没有清除, 所以在后续的第一次启动时清除
    if (HttpService.isRunning.value) return
    com.sugarscat.jump.appScope.launchTry(Dispatchers.IO) {
        delay(1000)
        if (storeFlow.value.autoClearMemorySubs) {
            deleteSubscription(LOCAL_HTTP_SUBS_ID)
        }
    }
}

private val httpSubsItem by lazy {
    SubsItem(
        id = LOCAL_HTTP_SUBS_ID,
        order = -1,
        enableUpdate = false,
    )
}

private fun createServer(port: Int): CIOApplicationEngine {
    return embeddedServer(CIO, port) {
        install(KtorCorsPlugin)
        install(KtorErrorPlugin)
        install(ContentNegotiation) { json(keepNullJson) }
        routing {
            get("/") { call.respondText(ContentType.Text.Html) { "<script type='module' src='$SERVER_SCRIPT_URL'></script>" } }
            route("/api") {
                // Deprecated
                get("/device") { call.respond(DeviceInfo.instance) }

                post("/getServerInfo") { call.respond(ServerInfo()) }

                // Deprecated
                get("/snapshot") {
                    val id = call.request.queryParameters["id"]?.toLongOrNull()
                        ?: throw RpcError("miss id")
                    val fp = File(SnapshotExt.getSnapshotPath(id))
                    if (!fp.exists()) {
                        throw RpcError("对应快照不存在")
                    }
                    call.respondFile(fp)
                }
                post("/getSnapshot") {
                    val data = call.receive<ReqId>()
                    val fp = File(SnapshotExt.getSnapshotPath(data.id))
                    if (!fp.exists()) {
                        throw RpcError("对应快照不存在")
                    }
                    call.respond(fp)
                }

                // Deprecated
                get("/screenshot") {
                    val id = call.request.queryParameters["id"]?.toLongOrNull()
                        ?: throw RpcError("miss id")
                    val fp = File(SnapshotExt.getScreenshotPath(id))
                    if (!fp.exists()) {
                        throw RpcError("对应截图不存在")
                    }
                    call.respondFile(fp)
                }
                post("/getScreenshot") {
                    val data = call.receive<ReqId>()
                    val fp = File(SnapshotExt.getScreenshotPath(data.id))
                    if (!fp.exists()) {
                        throw RpcError("对应截图不存在")
                    }
                    call.respondFile(fp)
                }

                // Deprecated
                get("/captureSnapshot") {
                    call.respond(captureSnapshot())
                }
                post("/captureSnapshot") {
                    call.respond(captureSnapshot())
                }

                // Deprecated
                get("/snapshots") {
                    call.respond(DbSet.snapshotDao.query().first())
                }
                post("/getSnapshots") {
                    call.respond(DbSet.snapshotDao.query().first())
                }

                post("/updateSubscription") {
                    val subscription =
                        RawSubscription.parse(call.receiveText(), json5 = false)
                            .copy(
                                id = LOCAL_HTTP_SUBS_ID,
                                name = "内存订阅",
                                version = 0,
                                author = "@gkd-kit/inspect"
                            )
                    updateSubscription(subscription)
                    DbSet.subsItemDao.insert((subsItemsFlow.value.find { s -> s.id == httpSubsItem.id }
                        ?: httpSubsItem).copy(mtime = System.currentTimeMillis()))
                    call.respond(RpcOk())
                }
                post("/execSelector") {
                    if (!JumpAbService.isRunning.value) {
                        throw RpcError("无障碍没有运行")
                    }
                    val jumpAction = call.receive<JumpAction>()
                    call.respond(JumpAbService.execAction(jumpAction))
                }
            }
        }
    }
}