package site.garsyanimultiusaha.gawone.mitra

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object GawoneNotificationChannels {
    const val ORDER = "gawone_order"
    const val CHAT = "gawone_chat"
    const val GENERAL = "gawone_general"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                ORDER,
                "Order GAWONE",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Offer dan perubahan order Mitra"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHAT,
                "Chat GAWONE",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pesan customer dan operasional"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                GENERAL,
                "Informasi GAWONE",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "KYC, pendapatan, payout, dan informasi sistem"
            }
        )
    }
}

class GawoneMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        GawoneNotificationChannels.ensure(this)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (!BuildConfig.PUSH_PROVIDER_CONFIGURED) return
        if (SecureSessionStore(applicationContext).read() == null) return

        scope.launch {
            runCatching {
                Stage4GCommunicationClient(applicationContext).registerPushToken(
                    token = token,
                    appVersion = BuildConfig.VERSION_NAME,
                    deviceName = Build.MANUFACTURER + " " + Build.MODEL
                )
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val kind = data["kind"].orEmpty()
        val route = data["route"]
        val title = message.notification?.title
            ?: data["title"]
            ?: "GAWONE Mitra"
        val body = message.notification?.body
            ?: data["body"]
            ?: "Ada pembaruan baru."

        val channel = when (kind) {
            "ORDER_OFFER", "ORDER_ASSIGNED", "ASSIGNMENT_STATUS", "ORDER_STATUS" ->
                GawoneNotificationChannels.ORDER
            "CHAT_MESSAGE" ->
                GawoneNotificationChannels.CHAT
            else ->
                GawoneNotificationChannels.GENERAL
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (!route.isNullOrBlank()) data = Uri.parse(route)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            route?.hashCode() ?: System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (channel == GawoneNotificationChannels.GENERAL)
                    NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_HIGH
            )
            .build()

        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(
                message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(),
                notification
            )
        }
    }
}
