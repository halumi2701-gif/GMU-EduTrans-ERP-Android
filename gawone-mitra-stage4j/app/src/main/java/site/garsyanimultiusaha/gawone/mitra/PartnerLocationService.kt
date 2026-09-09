package site.garsyanimultiusaha.gawone.mitra

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PartnerLocationService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "GAWONE Mitra Online",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("GAWONE Mitra aktif")
            .setContentText("Status lokasi digunakan saat Anda memilih Online.")
            .setOngoing(true)
            .build()
        startForeground(44, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object { const val CHANNEL = "gawone_partner_presence" }
}
