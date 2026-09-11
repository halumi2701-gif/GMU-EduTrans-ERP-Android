package com.garsyanimultiusaha.gmuedutrans.erp

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class GmuFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushNotifications.onNewToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "GMU EduTrans ERP"
        val body = message.notification?.body
            ?: message.data["message"]
            ?: "Ada keputusan baru di ERP."
        val target = message.data["target_page"] ?: "WORKFLOW"
        showNotification(title, body, target, message.data["notification_id"])
    }

    private fun showNotification(
        title: String,
        body: String,
        target: String,
        notificationId: String?
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("target_page", target)
            notificationId?.let { putExtra("notification_id", it) }
        }
        val pending = PendingIntent.getActivity(
            this,
            (notificationId ?: body).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, PushNotifications.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        @Suppress("DEPRECATION")
        val notification = builder
            .setSmallIcon(R.drawable.ic_launcher_gmu)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((notificationId ?: "$title|$body").hashCode(), notification)
    }
}
