package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manager Ops Agent v1.3 — Pre-Departure Approval & Publish Control.
 *
 * Publication is intentionally separated from draft editing. The backend issues a
 * dedicated short-lived publish token and re-checks payment, crew, operation sheet,
 * plus content fingerprints before making anything customer-visible.
 */
@Composable
fun GmuNativeAppWithManagerOpsPublishControl(vm: MainViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithManagerOpsControlPanel(vm)

        val session = (vm.state as? AppState.LoggedIn)?.session
        if (
            session != null &&
            (ErpRoles.isManagerEduTrans(session.profile.role) || session.profile.role == ErpRoles.OWNER) &&
            vm.currentPage == AppPage.DASHBOARD
        ) {
            ManagerOpsPublishDock(vm = vm, session = session)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.ManagerOpsPublishDock(vm: MainViewModel, session: SessionState) {
    var opened by remember { mutableStateOf(false) }
    val activeBookings = remember(vm.bookings) {
        vm.bookings
            .filter { it.status.lowercase() !in setOf("completed", "closed", "cancelled", "canceled", "rejected") }
            .sortedBy { it.tripDate }
    }

    Surface(
        onClick = { opened = true },
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 18.dp, bottom = 152.dp),
        shape = RoundedCornerShape(18.dp),
        color = GmuDark,
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Publish, contentDescription = null, tint = GmuGold, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Publish Guard v1.3", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("Final preview wajib", color = Color.White.copy(alpha = .68f), fontSize = 9.sp)
            }
        }
    }

    if (opened) {
        ModalBottomSheet(
            onDismissRequest = { opened = false },
            containerColor = GmuBg,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            ManagerOpsPublishPanel(
                session = session,
                activeBookings = activeBookings,
                onClose = { opened = false }
            )
        }
    }
}

@Composable
private fun ManagerOpsPublishPanel(
    session: SessionState,
    activeBookings: List<Booking>,
    onClose: () -> Unit
) {
    val api = remember { ManagerOpsPublishApi() }
    val scope = rememberCoroutineScope()
    var selectedBookingId by remember(activeBookings) { mutableStateOf(activeBookings.firstOrNull()?.id.orEmpty()) }
    var state by remember { mutableStateOf<JSONObject?>(null) }
    var previewResult by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var committing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedBookingId, session.accessToken, reloadKey) {
        if (selectedBookingId.isBlank()) {
            state = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching { api.publishState(session.accessToken, selectedBookingId) }
            .onSuccess { state = it }
            .onFailure { error = it.message ?: "Publish state gagal dimuat." }
        loading = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 42.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = GmuDark) {
                Icon(Icons.Rounded.Publish, contentDescription = null, tint = GmuGold, modifier = Modifier.size(48.dp).padding(12.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Pre-Departure Publish Control", fontSize = 19.sp, fontWeight = FontWeight.Black, color = GmuDark)
                Text("v1.3 • Ready to Publish → Final Preview → Publish", fontSize = 10.sp, color = Color.Gray)
            }
            TextButton(onClick = onClose) { Text("Tutup") }
        }

        Spacer(Modifier.height(14.dp))
        if (activeBookings.isEmpty()) {
            PublishInfoCard("Belum ada booking aktif untuk publish control.", false)
            return@Column
        }

        Text("Pilih trip", fontSize = 12.sp, fontWeight = FontWeight.Black, color = GmuDark)
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            activeBookings.take(8).forEach { booking ->
                FilterChip(
                    selected = selectedBookingId == booking.id,
                    onClick = {
                        selectedBookingId = booking.id
                        previewResult = null
                        message = null
                    },
                    label = { Text(booking.bookingNo, fontSize = 10.sp) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        error?.let {
            PublishInfoCard(it, true)
            Spacer(Modifier.height(10.dp))
        }
        message?.let {
            PublishInfoCard(it, false)
            Spacer(Modifier.height(10.dp))
        }

        state?.let { data ->
            val publicationState = data.optString("publication_state", "PREPARING")
            val ready = data.optBoolean("ready_to_publish", false)
            val guards = data.optJSONObject("guards") ?: JSONObject()
            val departure = data.optJSONObject("departure")
            val rundown = data.optJSONObject("rundown") ?: JSONObject()
            val blockers = data.optJSONArray("blockers") ?: JSONArray()

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (publicationState == "PUBLISHED") Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = if (publicationState == "PUBLISHED") GmuGreen else if (ready) GmuGreen else GmuWarn
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Publication State", fontSize = 10.sp, color = Color.Gray)
                            Text(publicationState.replace('_', ' '), fontSize = 16.sp, fontWeight = FontWeight.Black, color = GmuDark)
                        }
                        if (ready) {
                            AssistChip(onClick = {}, label = { Text("READY", fontWeight = FontWeight.Bold) })
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    PublishGuardRow("Payment clear", guards.optBoolean("payment_clear", false))
                    PublishGuardRow("Crew assigned", guards.optBoolean("team_assigned", false))
                    PublishGuardRow("Operation Sheet READY", guards.optBoolean("operation_ready", false))
                    PublishGuardRow(
                        "Departure draft lengkap",
                        departure != null && departure.optString("status") == "DRAFT" &&
                            departure.optString("meeting_point").isNotBlank() && departure.optString("meeting_time").isNotBlank()
                    )
                    PublishGuardRow("Rundown draft tersedia", rundown.optInt("draft_count", 0) > 0)
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(15.dp)) {
                    Text("Customer Content", fontWeight = FontWeight.Black, color = GmuDark)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        departure?.let {
                            "Meeting: ${it.optString("meeting_point", "-")} • ${it.optString("meeting_time", "-")}"
                        } ?: "Departure info belum dibuat.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        "Rundown draft ${rundown.optInt("draft_count", 0)} item • published ${rundown.optInt("published_count", 0)} item",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            if (blockers.length() > 0) {
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3EF))
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Text("Belum bisa dipublish", fontWeight = FontWeight.Black, color = GmuDark)
                        Spacer(Modifier.height(7.dp))
                        for (i in 0 until blockers.length()) {
                            val item = blockers.optJSONObject(i) ?: continue
                            Text("• ${item.optString("label", item.optString("code"))}", fontSize = 10.sp, color = GmuWarn)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            when {
                publicationState == "PUBLISHED" -> PublishInfoCard(
                    "Rundown dan info keberangkatan sudah customer-visible. Perubahan berikutnya harus melalui flow revisi, bukan publish ulang diam-diam.",
                    false
                )
                ready -> Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            message = null
                            runCatching { api.previewPublish(session.accessToken, selectedBookingId) }
                                .onSuccess { previewResult = it }
                                .onFailure { error = it.message ?: "Final preview gagal dibuat." }
                            loading = false
                        }
                    }
                ) {
                    Icon(Icons.Rounded.Publish, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Final Preview Publish")
                }
                else -> OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    onClick = {}
                ) { Text("Selesaikan blocker sebelum publish") }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Publish tidak mengubah status booking dan tidak membuat transaksi keuangan. Portal notification dibuat setelah publish berhasil; email/WhatsApp tidak dikirim otomatis oleh tombol ini.",
                fontSize = 9.sp,
                color = Color.Gray
            )
        }
    }

    previewResult?.let { result ->
        val requestId = result.optString("request_id")
        val confirmToken = result.optString("confirm_token")
        val preview = result.optJSONObject("preview") ?: JSONObject()
        val departure = preview.optJSONObject("departure") ?: JSONObject()
        val items = preview.optJSONArray("rundown_items") ?: JSONArray()

        AlertDialog(
            onDismissRequest = { if (!committing) previewResult = null },
            title = { Text("Final Preview ke Customer") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Info keberangkatan", fontWeight = FontWeight.Black, color = GmuDark)
                    Text(
                        "${departure.optString("meeting_point", "-")} • ${departure.optString("meeting_time", "-")}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    departure.optString("departure_instruction").takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(5.dp)); Text(it, fontSize = 10.sp, color = Color.Gray)
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Rundown (${items.length()} item)", fontWeight = FontWeight.Black, color = GmuDark)
                    for (i in 0 until minOf(items.length(), 20)) {
                        val item = items.optJSONObject(i) ?: continue
                        Text(
                            "${item.optString("activity_time", "--:--")} • ${item.optString("activity", "-")}",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                    if (items.length() > 20) Text("+${items.length() - 20} item lainnya", fontSize = 9.sp, color = Color.Gray)

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Setelah Confirm Publish, konten di atas menjadi terlihat customer. Jika draft berubah sesudah preview, backend akan menolak publish dan meminta preview ulang.",
                        fontSize = 10.sp,
                        color = GmuWarn
                    )
                    if (committing) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !committing, onClick = { previewResult = null }) { Text("Batal") }
            },
            confirmButton = {
                Button(
                    enabled = !committing && requestId.isNotBlank() && confirmToken.length >= 20,
                    onClick = {
                        scope.launch {
                            committing = true
                            error = null
                            runCatching { api.commitPublish(session.accessToken, requestId, confirmToken) }
                                .onSuccess {
                                    message = "Rundown dan info keberangkatan berhasil dipublish ke Customer Portal."
                                    previewResult = null
                                    reloadKey++
                                }
                                .onFailure { error = it.message ?: "Publish gagal." }
                            committing = false
                        }
                    }
                ) { Text("Confirm Publish") }
            }
        )
    }
}

@Composable
private fun PublishGuardRow(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = if (ok) GmuGreen else GmuWarn,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 10.sp, color = GmuDark)
        Text(if (ok) "OK" else "BLOCK", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (ok) GmuGreen else GmuWarn)
    }
}

@Composable
private fun PublishInfoCard(text: String, danger: Boolean) {
    Card(
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = if (danger) Color(0xFFFFEDEC) else Color(0xFFEFF8F3))
    ) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(13.dp), fontSize = 10.sp, color = if (danger) GmuDanger else GmuDark)
    }
}

private class ManagerOpsPublishApi {
    private val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/internal-manager-ops-agent"
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun publishState(accessToken: String, bookingId: String): JSONObject =
        call(accessToken, JSONObject().put("action", "PUBLISH_STATE").put("booking_id", bookingId))

    suspend fun previewPublish(accessToken: String, bookingId: String): JSONObject =
        call(accessToken, JSONObject().put("action", "PREVIEW_PUBLISH").put("booking_id", bookingId))

    suspend fun commitPublish(accessToken: String, requestId: String, confirmToken: String): JSONObject =
        call(
            accessToken,
            JSONObject()
                .put("action", "COMMIT_PUBLISH")
                .put("request_id", requestId)
                .put("confirm_token", confirmToken)
        )

    private suspend fun call(accessToken: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("apikey", key)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = BufferedReader(InputStreamReader(stream ?: connection.inputStream)).use { reader ->
                buildString {
                    while (true) {
                        val line = reader.readLine() ?: break
                        append(line)
                    }
                }
            }
            val root = if (body.isBlank()) JSONObject() else JSONObject(body)
            if (code !in 200..299 || !root.optBoolean("ok", false)) {
                throw IllegalStateException(root.optString("error", "Publish Control gagal diproses."))
            }
            root.optJSONObject("result") ?: JSONObject()
        } finally {
            connection.disconnect()
        }
    }
}
