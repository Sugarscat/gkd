package com.sugarscat.jump.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sugarscat.jump.MainActivity

fun createChannel(context: Context, notifChannel: NotifChannel) {
    val importance = NotificationManager.IMPORTANCE_LOW
    val channel = NotificationChannel(notifChannel.id, notifChannel.name, importance)
    channel.description = notifChannel.desc
    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.createNotificationChannel(channel)
}

fun createNotif(context: Service, channelId: String, notif: Notif) {
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            notif.uri?.let { data = Uri.parse(it) }
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(notif.smallIcon)
        .setContentTitle(notif.title)
        .setContentText(notif.text)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setOngoing(notif.ongoing)
        .setAutoCancel(notif.autoCancel)
        .build()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.startForeground(
            notif.id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        )
    } else {
        context.startForeground(notif.id, notification)
    }
}