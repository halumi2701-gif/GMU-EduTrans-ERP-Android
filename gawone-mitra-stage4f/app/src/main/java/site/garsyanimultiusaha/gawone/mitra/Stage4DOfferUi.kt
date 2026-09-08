package site.garsyanimultiusaha.gawone.mitra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun Stage4DOfferPanel(serviceCode: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { Stage4DOfferClient(context.applicationContext) }

    var offers by remember { mutableStateOf<List<PartnerOffer>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<PartnerAssignment>>(emptyList()) }
    var selected by remember { mutableStateOf<PartnerOfferDetail?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var acceptTarget by remember { mutableStateOf<PartnerOfferDetail?>(null) }
    var rejectTarget by remember { mutableStateOf<PartnerOfferDetail?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    suspend fun refreshAll() {
        offers = client.offers()
        assignments = client.assignments()
        selected?.let { current ->
            if (offers.none { it.offerId == current.offerId }) {
                selected = null
            }
        }
    }

    LaunchedEffect(serviceCode) {
        if (serviceCode.isNullOrBlank()) return@LaunchedEffect

        runCatching { refreshAll() }
            .onFailure { error = it.message }

        while (isActive) {
            delay(2_000)
            runCatching { refreshAll() }
                .onFailure { error = it.message }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Order Masuk", fontWeight = FontWeight.Bold)

            Text(
                "Stage 4D hanya menampilkan offer server yang masih aktif. " +
                    "Accept tetap diputuskan atomik oleh backend: Mitra tercepat yang valid mendapatkan slot."
            )

            if (serviceCode.isNullOrBlank()) {
                Text(
                    "Pilih layanan Mitra terlebih dahulu.",
                    color = MaterialTheme.colorScheme.error
                )
                return@Column
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            info?.let {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(it, Modifier.padding(12.dp))
                }
            }

            if (loading) {
                CircularProgressIndicator()
            }

            selected?.let { detail ->
                OfferDetailCard(
                    detail = detail,
                    nowMs = nowMs,
                    loading = loading,
                    onAccept = { acceptTarget = detail },
                    onReject = { rejectTarget = detail },
                    onBack = { selected = null }
                )
            } ?: run {
                if (offers.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Belum ada offer aktif", fontWeight = FontWeight.Bold)
                            Text(
                                "Pastikan status Online dan GPS fresh. " +
                                    "Offer yang kedaluwarsa tidak ditampilkan."
                            )
                        }
                    }
                } else {
                    offers.forEach { offer ->
                        OfferSummaryCard(
                            offer = offer,
                            nowMs = nowMs,
                            onOpen = {
                                scope.launch {
                                    loading = true
                                    error = null
                                    runCatching { client.detail(offer.offerId) }
                                        .onSuccess { selected = it }
                                        .onFailure {
                                            error = it.message ?: "Gagal membuka detail offer."
                                            runCatching { refreshAll() }
                                        }
                                    loading = false
                                }
                            }
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            runCatching { refreshAll() }
                                .onFailure {
                                    error = it.message ?: "Gagal memperbarui offer."
                                }
                            loading = false
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh Offer")
                }
            }

            if (assignments.isNotEmpty()) {
                Divider()
                Text("Pekerjaan Aktif", fontWeight = FontWeight.Bold)
                assignments.forEach { assignment ->
                    AssignmentCard(assignment)
                }
                Text(
                    "Tahapan Menuju Lokasi → Check-in → Mulai → Selesai " +
                        "baru diaktifkan pada Stage 4E.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    acceptTarget?.let { detail ->
        AlertDialog(
            onDismissRequest = { if (!loading) acceptTarget = null },
            title = { Text("Terima order?") },
            text = {
                Text(
                    "${detail.serviceName} • ${detail.orderNo}\n" +
                        "Estimasi diterima: ${rupiah(detail.estimatedPartnerNet)}\n\n" +
                        "Backend akan mengecek ulang Online, KYC, slot, dan status order."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            val target = detail.offerId
                            acceptTarget = null

                            runCatching { client.accept(target) }
                                .onSuccess { result ->
                                    info = "Order ${result.orderNo} berhasil diterima. " +
                                        "Assignment sudah dibuat."
                                    selected = null
                                    runCatching { refreshAll() }
                                }
                                .onFailure {
                                    error = it.message ?: "Gagal menerima order."
                                    selected = null
                                    runCatching { refreshAll() }
                                }
                            loading = false
                        }
                    },
                    enabled = !loading && detail.expiresAtEpochMs > nowMs
                ) {
                    Text("Ya, Terima")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { acceptTarget = null },
                    enabled = !loading
                ) {
                    Text("Batal")
                }
            }
        )
    }

    rejectTarget?.let { detail ->
        AlertDialog(
            onDismissRequest = { if (!loading) rejectTarget = null },
            title = { Text("Tolak order") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pilih alasan agar operasional GAWONE dapat mengevaluasi matching.")
                    RejectReasonButton("Jarak terlalu jauh") { reason ->
                        rejectOffer(
                            scope = scope,
                            client = client,
                            detail = detail,
                            reason = reason,
                            setLoading = { loading = it },
                            setError = { error = it },
                            setInfo = { info = it },
                            clearDialog = { rejectTarget = null },
                            clearDetail = { selected = null },
                            refresh = { refreshAll() }
                        )
                    }
                    RejectReasonButton("Sedang tidak siap") { reason ->
                        rejectOffer(
                            scope, client, detail, reason,
                            { loading = it }, { error = it }, { info = it },
                            { rejectTarget = null }, { selected = null },
                            { refreshAll() }
                        )
                    }
                    RejectReasonButton("Kendaraan / alat bermasalah") { reason ->
                        rejectOffer(
                            scope, client, detail, reason,
                            { loading = it }, { error = it }, { info = it },
                            { rejectTarget = null }, { selected = null },
                            { refreshAll() }
                        )
                    }
                    RejectReasonButton("Alasan lainnya") { reason ->
                        rejectOffer(
                            scope, client, detail, reason,
                            { loading = it }, { error = it }, { info = it },
                            { rejectTarget = null }, { selected = null },
                            { refreshAll() }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }, enabled = !loading) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun OfferSummaryCard(
    offer: PartnerOffer,
    nowMs: Long,
    onOpen: () -> Unit
) {
    val remaining = (offer.expiresAtEpochMs - nowMs).coerceAtLeast(0L)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(offer.serviceName, fontWeight = FontWeight.Bold)
                    Text(
                        offer.orderNo,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    countdown(remaining),
                    color = if (remaining > 10_000)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            offer.pickupAddress?.let {
                Text("Dari: $it")
            }
            offer.destinationAddress?.let {
                Text("Tujuan: $it")
            }
            offer.distanceKm?.let {
                Text("Jarak ke pickup: ${"%.1f".format(Locale.US, it)} km")
            }
            Text(
                "Estimasi diterima: ${rupiah(offer.estimatedPartnerNet)}",
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onOpen,
                enabled = remaining > 0L,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (remaining > 0L) "Lihat Detail" else "Offer Kedaluwarsa")
            }
        }
    }
}

@Composable
private fun OfferDetailCard(
    detail: PartnerOfferDetail,
    nowMs: Long,
    loading: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onBack: () -> Unit,
) {
    val remaining = (detail.expiresAtEpochMs - nowMs).coerceAtLeast(0L)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(detail.serviceName, fontWeight = FontWeight.Bold)
            Text("${detail.orderNo} • ${detail.orderType}")
            Text(
                "Sisa waktu: ${countdown(remaining)}",
                color = if (remaining > 10_000)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )

            Divider()

            detail.pickupAddress?.let { Text("Lokasi: $it") }
            detail.destinationAddress?.let { Text("Tujuan: $it") }
            detail.distanceKm?.let {
                Text("Jarak ke pickup: ${"%.1f".format(Locale.US, it)} km")
            }
            if (detail.requiredWorkers > 1) {
                Text("Kebutuhan tenaga: ${detail.requiredWorkers} orang")
            }
            detail.scheduledStart?.let { Text("Mulai: $it") }
            detail.notes?.let { Text("Catatan: $it") }

            Text(
                "Estimasi pendapatan Mitra: ${rupiah(detail.estimatedPartnerNet)}",
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(2.dp))

            Button(
                onClick = onAccept,
                enabled = !loading && remaining > 0L,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Terima Order")
            }

            OutlinedButton(
                onClick = onReject,
                enabled = !loading && remaining > 0L,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tolak")
            }

            TextButton(
                onClick = onBack,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kembali")
            }
        }
    }
}

@Composable
private fun AssignmentCard(assignment: PartnerAssignment) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "${assignment.serviceName} • ${assignment.orderNo}",
                fontWeight = FontWeight.Bold
            )
            Text("Assignment: ${assignment.assignmentStatus}")
            assignment.pickupAddress?.let { Text("Lokasi: $it") }
            assignment.destinationAddress?.let { Text("Tujuan: $it") }
        }
    }
}

@Composable
private fun RejectReasonButton(
    label: String,
    onClick: (String) -> Unit
) {
    OutlinedButton(
        onClick = { onClick(label.uppercase(Locale.US).replace(" / ", "_").replace(" ", "_")) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}

private fun rejectOffer(
    scope: kotlinx.coroutines.CoroutineScope,
    client: Stage4DOfferClient,
    detail: PartnerOfferDetail,
    reason: String,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    setInfo: (String?) -> Unit,
    clearDialog: () -> Unit,
    clearDetail: () -> Unit,
    refresh: suspend () -> Unit,
) {
    scope.launch {
        setLoading(true)
        setError(null)
        clearDialog()

        runCatching { client.reject(detail.offerId, reason) }
            .onSuccess {
                setInfo("Offer ${detail.orderNo} ditolak.")
                clearDetail()
                runCatching { refresh() }
            }
            .onFailure {
                setError(it.message ?: "Gagal menolak offer.")
                clearDetail()
                runCatching { refresh() }
            }

        setLoading(false)
    }
}

private fun countdown(remainingMs: Long): String {
    if (remainingMs <= 0L) return "00:00"
    val totalSeconds = remainingMs / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(Locale.US, minutes, seconds)
}

private fun rupiah(value: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    formatter.maximumFractionDigits = 0
    return formatter.format(value)
}
