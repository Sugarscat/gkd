package com.sugarscat.jump.debug

import android.content.Context
import android.content.Intent
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sugarscat.jump.R
import com.sugarscat.jump.app
import com.sugarscat.jump.composition.CompositionExt.useLifeCycleLog
import com.sugarscat.jump.composition.CompositionFbService
import com.sugarscat.jump.data.Tuple3
import com.sugarscat.jump.notif.createNotif
import com.sugarscat.jump.notif.floatingChannel
import com.sugarscat.jump.notif.floatingNotif
import com.sugarscat.jump.util.launchTry
import com.sugarscat.jump.util.toast
import com.torrydo.floatingbubbleview.FloatingBubbleListener
import com.torrydo.floatingbubbleview.service.expandable.BubbleBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.sqrt

class FloatingService : CompositionFbService({
    useLifeCycleLog()
    configBubble { resolve ->
        val builder = BubbleBuilder(this).bubbleCompose {
            Icon(
                imageVector = Icons.Default.CenterFocusWeak,
                contentDescription = "capture",
                modifier = Modifier.size(40.dp),
                tint = Color.Red
            )
        }.enableAnimateToEdge(false)

        // https://github.com/gkd-kit/gkd/issues/62
        // https://github.com/gkd-kit/gkd/issues/61
        val defaultFingerData = Tuple3(0L, 0f, 0f)
        var fingerDownData = defaultFingerData
        val maxDistanceOffset = 50
        builder.addFloatingBubbleListener(object : FloatingBubbleListener {
            override fun onFingerDown(x: Float, y: Float) {
                fingerDownData = Tuple3(System.currentTimeMillis(), x, y)
            }

            override fun onFingerMove(x: Float, y: Float) {
                if (fingerDownData === defaultFingerData) {
                    return
                }
                val dx = fingerDownData.t1 - x
                val dy = fingerDownData.t2 - y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > maxDistanceOffset) {
                    // reset
                    fingerDownData = defaultFingerData
                }
            }

            override fun onFingerUp(x: Float, y: Float) {
                if (System.currentTimeMillis() - fingerDownData.t0 < ViewConfiguration.getTapTimeout()) {
                    // is onClick
                    com.sugarscat.jump.appScope.launchTry(Dispatchers.IO) {
                        SnapshotExt.captureSnapshot()
                        toast(getString(R.string.snapshot_succeeded))
                    }
                }
            }
        })
        resolve(builder)
    }

    isRunning.value = true
    onDestroy {
        isRunning.value = false
    }
}) {

    override fun onCreate() {
        super.onCreate()
        minimize()
    }

    override fun startNotificationForeground() {
        createNotif(this, floatingChannel.id, floatingNotif)
    }

    companion object {
        val isRunning = MutableStateFlow(false)
        fun stop(context: Context = app) {
            context.stopService(Intent(context, FloatingService::class.java))
        }
    }
}