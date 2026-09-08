package site.garsyanimultiusaha.gawone.mitra

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CancellationTokenSource
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
internal fun Stage4CPresencePanel(serviceCode: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { Stage4CPresenceClient(context.applicationContext) }

    var snapshot by remember { mutableStateOf<PresenceSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val locationGranted =
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        info = if (locationGranted) {
            "Izin lokasi aktif. Tekan Online sekali lagi."
        } else {
            "GAWONE memerlukan lokasi presisi saat Mitra mengaktifkan status Online."
        }
    }

    suspend fun refresh() {
        snapshot = client.snapshot()
    }

    LaunchedEffect(serviceCode) {
        if (!serviceCode.isNullOrBlank()) {
            runCatching { refresh() }
            while (isActive) {
                delay(10_000)
                runCatching { refresh() }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Online & GPS", fontWeight = FontWeight.Bold)
            Text(
                "Stage 4C menggunakan presence session dan heartbeat server. " +
                    "Jika aplikasi berhenti mengirim heartbeat, Mitra dianggap stale oleh backend."
            )

            val current = snapshot
            if (current == null) {
                Text("Status: Offline")
            } else {
                Text(
                    "Status: ${if (current.fresh) "Online" else "Koneksi stale"}",
                    color = if (current.fresh)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Mode: ${current.trackingMode} • heartbeat ${current.heartbeatIntervalSeconds} dtk"
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            info?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            if (loading) {
                CircularProgressIndicator()
            }

            if (current == null) {
                Button(
                    onClick = {
                        if (serviceCode.isNullOrBlank()) {
                            error = "Pilih layanan Mitra terlebih dahulu."
                            return@Button
                        }

                        if (!hasPresencePermissions(context)) {
                            val permissions = buildList {
                                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                if (Build.VERSION.SDK_INT >= 33) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }.toTypedArray()
                            permissionLauncher.launch(permissions)
                            return@Button
                        }

                        scope.launch {
                            loading = true
                            error = null
                            info = null
                            runCatching {
                                val location = getPreciseLocation(context)
                                val started = client.start(
                                    serviceCode = serviceCode,
                                    deviceSessionId = installationSessionId(context),
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    accuracyM = location.accuracy,
                                )

                                val fresh = client.snapshot()
                                    ?: throw IllegalStateException("Presence session tidak terbaca.")

                                val serviceIntent = Intent(
                                    context,
                                    PartnerPresenceService::class.java
                                ).apply {
                                    putExtra(
                                        PartnerPresenceService.EXTRA_SESSION_ID,
                                        started.sessionId
                                    )
                                    putExtra(
                                        PartnerPresenceService.EXTRA_HEARTBEAT_SECONDS,
                                        started.heartbeatIntervalSeconds
                                    )
                                    putExtra(
                                        PartnerPresenceService.EXTRA_TRACKING_MODE,
                                        fresh.trackingMode
                                    )
                                }
                                ContextCompat.startForegroundService(context, serviceIntent)
                                snapshot = fresh
                                info = "Status Online aktif."
                            }.onFailure {
                                error = it.message ?: "Gagal mengaktifkan status Online."
                            }
                            loading = false
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mulai Online")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                loading = true
                                error = null
                                runCatching {
                                    client.stop(current.sessionId)
                                    context.stopService(
                                        Intent(context, PartnerPresenceService::class.java)
                                    )
                                    snapshot = null
                                    info = "Status Offline."
                                }.onFailure {
                                    error = it.message ?: "Gagal mengakhiri status Online."
                                }
                                loading = false
                            }
                        },
                        enabled = !loading
                    ) {
                        Text("Offline")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                loading = true
                                runCatching { refresh() }
                                    .onFailure {
                                        error = it.message ?: "Gagal refresh presence."
                                    }
                                loading = false
                            }
                        },
                        enabled = !loading
                    ) {
                        Text("Refresh")
                    }
                }
            }
        }
    }
}

private fun hasPresencePermissions(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun installationSessionId(context: Context): String {
    val prefs = context.getSharedPreferences(
        "gawone_mitra_presence_device",
        Context.MODE_PRIVATE
    )
    val existing = prefs.getString("device_session_id", null)
    if (!existing.isNullOrBlank()) return existing

    val created = "android-" + UUID.randomUUID().toString()
    prefs.edit().putString("device_session_id", created).apply()
    return created
}

private suspend fun getPreciseLocation(context: Context): android.location.Location =
    suspendCancellableCoroutine { continuation ->
        if (!hasPresencePermissions(context)) {
            continuation.resumeWithException(
                SecurityException("Izin lokasi presisi belum diberikan.")
            )
            return@suspendCancellableCoroutine
        }

        val source = CancellationTokenSource()
        continuation.invokeOnCancellation { source.cancel() }

        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    source.token
                )
                .addOnSuccessListener { location ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    if (location == null) {
                        continuation.resumeWithException(
                            IllegalStateException(
                                "Lokasi belum tersedia. Aktifkan GPS lalu coba lagi."
                            )
                        )
                    } else {
                        continuation.resume(location)
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
        } catch (error: SecurityException) {
            if (continuation.isActive) {
                continuation.resumeWithException(error)
            }
        }
    }
