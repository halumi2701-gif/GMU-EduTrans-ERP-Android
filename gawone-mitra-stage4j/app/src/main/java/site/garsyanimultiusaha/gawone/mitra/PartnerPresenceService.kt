package site.garsyanimultiusaha.gawone.mitra

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PartnerPresenceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: Stage4CPresenceClient
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var callback: LocationCallback? = null
    private var checkpointJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        client = Stage4CPresenceClient(applicationContext)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        if (sessionId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val intervalSeconds = intent.getIntExtra(EXTRA_HEARTBEAT_SECONDS, 60)
            .coerceIn(15, 600)
        val trackingMode = intent.getStringExtra(EXTRA_TRACKING_MODE)
            ?.ifBlank { "CONTINUOUS" }
            ?: "CONTINUOUS"

        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("GAWONE Mitra sedang online")
                .setContentText(
                    if (trackingMode == "CHECKPOINT_ONLY")
                        "Menjaga status ketersediaan."
                    else
                        "Lokasi digunakan selama status Online."
                )
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
        )

        stopTracking()

        if (trackingMode == "CHECKPOINT_ONLY") {
            checkpointJob = scope.launch {
                while (isActive) {
                    runCatching {
                        client.heartbeat(
                            sessionId = sessionId,
                            latitude = null,
                            longitude = null,
                            accuracyM = null,
                            heading = null,
                            speedMps = null,
                        )
                    }.onFailure {
                        if (it.message?.contains("ENDED", ignoreCase = true) == true) {
                            stopSelf()
                        }
                    }
                    delay(intervalSeconds * 1000L)
                }
            }
        } else {
            startLocationTracking(sessionId, intervalSeconds)
        }

        return START_STICKY
    }

    private fun startLocationTracking(sessionId: String, intervalSeconds: Int) {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) {
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalSeconds * 1000L
        )
            .setMinUpdateIntervalMillis((intervalSeconds * 800L).coerceAtLeast(10_000L))
            .setMaxUpdateDelayMillis(intervalSeconds * 1500L)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                scope.launch {
                    runCatching {
                        client.heartbeat(
                            sessionId = sessionId,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyM = location.accuracy,
                            heading = location.bearing.takeIf { location.hasBearing() },
                            speedMps = location.speed.takeIf { location.hasSpeed() },
                        )
                    }.onFailure {
                        if (it.message?.contains("ENDED", ignoreCase = true) == true) {
                            stopSelf()
                        }
                    }
                }
            }
        }
        callback = locationCallback

        try {
            fused.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun stopTracking() {
        checkpointJob?.cancel()
        checkpointJob = null
        callback?.let { fused.removeLocationUpdates(it) }
        callback = null
    }

    override fun onDestroy() {
        stopTracking()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Status Online Mitra",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Menjaga status online dan lokasi operasional GAWONE Mitra."
                }
            )
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "presence_session_id"
        const val EXTRA_HEARTBEAT_SECONDS = "heartbeat_seconds"
        const val EXTRA_TRACKING_MODE = "tracking_mode"

        private const val CHANNEL_ID = "gawone_mitra_presence"
        private const val NOTIFICATION_ID = 4101
    }
}
