package site.garsyanimultiusaha.gawone.mitra

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
internal fun Stage4EExecutionPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offerClient = remember { Stage4DOfferClient(context.applicationContext) }
    val executionClient = remember { Stage4EExecutionClient(context.applicationContext) }

    var assignments by remember { mutableStateOf<List<PartnerAssignment>>(emptyList()) }
    var selected by remember { mutableStateOf<AssignmentExecution?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    var pendingProofAssignmentId by remember { mutableStateOf<String?>(null) }
    var pendingProofType by remember { mutableStateOf("WORK_RESULT") }

    var issueTarget by remember { mutableStateOf<AssignmentExecution?>(null) }
    var issueNote by remember { mutableStateOf("") }

    suspend fun refresh() {
        assignments = offerClient.assignments()
        val current = selected
        if (current != null) {
            selected = if (assignments.any { it.assignmentId == current.assignmentId }) {
                executionClient.detail(current.assignmentId)
            } else {
                null
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        info = if (granted) {
            "Izin lokasi presisi aktif. Tekan Check-in lagi."
        } else {
            "Izin lokasi presisi diperlukan untuk Check-in."
        }
    }

    val proofPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val assignmentId = pendingProofAssignmentId
        pendingProofAssignmentId = null
        if (uri != null && assignmentId != null) {
            scope.launch {
                loading = true
                error = null
                try {
                    selected = executionClient.uploadProof(
                        assignmentId = assignmentId,
                        uri = uri,
                        proofType = pendingProofType,
                        note = null,
                    )
                    info = "Bukti pekerjaan berhasil disimpan."
                } catch (t: Throwable) {
                    error = t.message ?: "Gagal mengunggah bukti."
                }
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { refresh() }
            .onFailure { error = it.message }

        while (isActive) {
            delay(3_000)
            runCatching { refresh() }
                .onFailure { error = it.message }
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
            Text("Pelaksanaan Pekerjaan", fontWeight = FontWeight.Bold)
            Text(
                "Stage 4E mengunci urutan status di server: " +
                    "Menuju Lokasi → Tiba → Check-in → Mulai → Selesai."
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            info?.let {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(it, Modifier.padding(10.dp))
                }
            }

            if (loading) CircularProgressIndicator()

            val current = selected
            if (current == null) {
                if (assignments.isEmpty()) {
                    Text("Belum ada pekerjaan aktif.")
                } else {
                    assignments.forEach { assignment ->
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    loading = true
                                    error = null
                                    try {
                                        selected = executionClient.detail(assignment.assignmentId)
                                    } catch (t: Throwable) {
                                        error = t.message ?: "Gagal membuka pekerjaan."
                                    }
                                    loading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    "${assignment.serviceName} • ${assignment.orderNo}",
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Status: ${assignment.assignmentStatus}")
                            }
                        }
                    }
                }
            } else {
                ExecutionDetailCard(
                    execution = current,
                    loading = loading,
                    onAction = { action ->
                        scope.launch {
                            loading = true
                            error = null
                            info = null

                            try {
                                selected = if (action == "CHECKED_IN") {
                                    if (!hasFineLocation(context)) {
                                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                        current
                                    } else {
                                        val location = getExecutionLocation(context)
                                        executionClient.action(
                                            assignmentId = current.assignmentId,
                                            action = action,
                                            latitude = location.latitude,
                                            longitude = location.longitude,
                                            accuracyM = location.accuracy,
                                        )
                                    }
                                } else {
                                    executionClient.action(
                                        assignmentId = current.assignmentId,
                                        action = action,
                                    )
                                }

                                if (selected !== current || action != "CHECKED_IN" || hasFineLocation(context)) {
                                    info = actionSuccessText(action)
                                }

                                refresh()
                            } catch (t: Throwable) {
                                error = t.message ?: "Aksi pekerjaan gagal."
                            }

                            loading = false
                        }
                    },
                    onUploadProof = {
                        pendingProofAssignmentId = current.assignmentId
                        pendingProofType = current.requiredFinishProofTypes.firstOrNull()
                            ?: "WORK_RESULT"
                        proofPicker.launch("*/*")
                    },
                    onIssue = {
                        issueNote = ""
                        issueTarget = current
                    },
                    onBack = { selected = null }
                )
            }
        }
    }

    issueTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!loading) issueTarget = null },
            title = { Text("Laporkan Kendala") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = issueNote,
                        onValueChange = { issueNote = it.take(500) },
                        label = { Text("Catatan (opsional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    IssueButton("Customer tidak dapat dihubungi") {
                        reportIssue(
                            scope, executionClient, target,
                            "CUSTOMER_UNREACHABLE", "NORMAL", issueNote,
                            { loading = it }, { error = it }, { info = it },
                            { selected = it }, { issueTarget = null }
                        )
                    }
                    IssueButton("Masalah lokasi") {
                        reportIssue(
                            scope, executionClient, target,
                            "LOCATION_PROBLEM", "BLOCKING", issueNote,
                            { loading = it }, { error = it }, { info = it },
                            { selected = it }, { issueTarget = null }
                        )
                    }
                    IssueButton("Kendaraan / alat bermasalah") {
                        reportIssue(
                            scope, executionClient, target,
                            if (target.serviceCode in setOf("RIDE","CAR","DELIVERY","DRIVER"))
                                "VEHICLE_PROBLEM" else "EQUIPMENT_PROBLEM",
                            "BLOCKING", issueNote,
                            { loading = it }, { error = it }, { info = it },
                            { selected = it }, { issueTarget = null }
                        )
                    }
                    IssueButton("Keselamatan / darurat") {
                        reportIssue(
                            scope, executionClient, target,
                            "SAFETY", "EMERGENCY", issueNote,
                            { loading = it }, { error = it }, { info = it },
                            { selected = it }, { issueTarget = null }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { issueTarget = null },
                    enabled = !loading
                ) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ExecutionDetailCard(
    execution: AssignmentExecution,
    loading: Boolean,
    onAction: (String) -> Unit,
    onUploadProof: () -> Unit,
    onIssue: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${execution.serviceName} • ${execution.orderNo}",
                fontWeight = FontWeight.Bold
            )
            Text("Status Mitra: ${execution.assignmentStatus}")
            Text("Status Order: ${execution.orderStatus}")
            execution.pickupAddress?.let { Text("Lokasi: $it") }

            if (execution.nextAction == "CHECKED_IN") {
                Text(
                    "Check-in maksimal ${execution.checkinRadiusM} m dari titik order " +
                        "dengan akurasi GPS ≤ ${execution.maxCheckinAccuracyM} m."
                )
            }

            Divider()

            if (execution.minimumFinishProofs > 0) {
                Text(
                    "Bukti wajib: ${execution.proofs.size}/${execution.minimumFinishProofs}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Jenis: ${execution.requiredFinishProofTypes.joinToString()}"
                )
            } else {
                Text("Bukti pekerjaan opsional.")
            }

            execution.proofs.forEach {
                Text("✓ ${it.proofType}", color = MaterialTheme.colorScheme.primary)
            }

            if (execution.assignmentStatus in setOf("CHECKED_IN","WORKING")) {
                OutlinedButton(
                    onClick = onUploadProof,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Upload Bukti")
                }
            }

            execution.nextAction?.let { action ->
                val requiredTypes = execution.requiredFinishProofTypes
                val matchingProofs = if (requiredTypes.isEmpty()) {
                    execution.proofs.size
                } else {
                    execution.proofs.count { it.proofType in requiredTypes }
                }
                val proofReady = matchingProofs >= execution.minimumFinishProofs
                val hasBlockingIssue = execution.issues.any {
                    it.status == "OPEN" && it.severity in setOf("BLOCKING","EMERGENCY")
                }

                Button(
                    onClick = { onAction(action) },
                    enabled = !loading &&
                        !(action == "FINISH_WORK" && (!proofReady || hasBlockingIssue)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(actionLabel(action))
                }

                if (action == "FINISH_WORK" && !proofReady) {
                    Text(
                        "Bukti penyelesaian wajib belum lengkap.",
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (action == "FINISH_WORK" && hasBlockingIssue) {
                    Text(
                        "Ada kendala BLOCKING/EMERGENCY yang masih OPEN.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            OutlinedButton(
                onClick = onIssue,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Laporkan Kendala")
            }

            TextButton(
                onClick = onBack,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kembali")
            }

            if (execution.issues.isNotEmpty()) {
                Divider()
                Text("Kendala dilaporkan", fontWeight = FontWeight.Bold)
                execution.issues.take(3).forEach {
                    Text("• ${it.issueType} • ${it.severity} • ${it.status}")
                }
            }
        }
    }
}

@Composable
private fun IssueButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}

private fun reportIssue(
    scope: kotlinx.coroutines.CoroutineScope,
    client: Stage4EExecutionClient,
    target: AssignmentExecution,
    type: String,
    severity: String,
    note: String,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    setInfo: (String?) -> Unit,
    setSelected: (AssignmentExecution?) -> Unit,
    close: () -> Unit,
) {
    scope.launch {
        setLoading(true)
        setError(null)
        close()
        try {
            val refreshed = client.reportIssue(
                assignmentId = target.assignmentId,
                issueType = type,
                severity = severity,
                note = note.takeIf { it.isNotBlank() },
            )
            setSelected(refreshed)
            setInfo(
                if (severity == "EMERGENCY")
                    "Kendala darurat tercatat dan masuk antrean dispatch prioritas."
                else if (severity == "BLOCKING")
                    "Kendala tercatat dan diteruskan ke dispatch."
                else
                    "Kendala berhasil dicatat."
            )
        } catch (t: Throwable) {
            setError(t.message ?: "Gagal melaporkan kendala.")
        }
        setLoading(false)
    }
}

private fun actionLabel(action: String): String = when (action) {
    "EN_ROUTE" -> "Menuju Lokasi"
    "ARRIVED" -> "Saya Sudah Tiba"
    "CHECKED_IN" -> "Check-in dengan GPS"
    "START_WORK" -> "Mulai Pekerjaan"
    "FINISH_WORK" -> "Selesaikan Pekerjaan"
    else -> action
}

private fun actionSuccessText(action: String): String = when (action) {
    "EN_ROUTE" -> "Status: menuju lokasi."
    "ARRIVED" -> "Status: sudah tiba."
    "CHECKED_IN" -> "Check-in berhasil diverifikasi."
    "START_WORK" -> "Pekerjaan dimulai."
    "FINISH_WORK" -> "Pekerjaan selesai."
    else -> "Status pekerjaan diperbarui."
}

private fun hasFineLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

private suspend fun getExecutionLocation(
    context: Context
): android.location.Location = suspendCancellableCoroutine { continuation ->
    if (!hasFineLocation(context)) {
        continuation.resumeWithException(
            SecurityException("Izin lokasi presisi belum diberikan.")
        )
        return@suspendCancellableCoroutine
    }

    val source = CancellationTokenSource()
    continuation.invokeOnCancellation { source.cancel() }

    try {
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, source.token)
            .addOnSuccessListener { location ->
                if (!continuation.isActive) return@addOnSuccessListener
                if (location == null) {
                    continuation.resumeWithException(
                        IllegalStateException(
                            "Lokasi belum tersedia. Aktifkan GPS dan coba lagi."
                        )
                    )
                } else {
                    continuation.resume(location)
                }
            }
            .addOnFailureListener { throwable ->
                if (continuation.isActive) {
                    continuation.resumeWithException(throwable)
                }
            }
    } catch (security: SecurityException) {
        continuation.resumeWithException(security)
    }
}
