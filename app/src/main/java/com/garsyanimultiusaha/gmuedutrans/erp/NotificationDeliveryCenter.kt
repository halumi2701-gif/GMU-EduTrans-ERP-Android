package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
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

@Composable
fun GmuNativeAppWithNotificationDeliveryCenter(vm: MainViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithManagerOpsPublishControl(vm)
        val session = (vm.state as? AppState.LoggedIn)?.session
        if (
            session != null &&
            vm.currentPage == AppPage.DASHBOARD &&
            (session.profile.role == ErpRoles.OWNER || ErpRoles.isDirector(session.profile.role))
        ) {
            NotificationDeliveryDock(session)
        }
    }
}

@Composable
private fun BoxScope.NotificationDeliveryDock(session: SessionState) {
    var open by remember { mutableStateOf(false) }

    Surface(
        onClick = { open = true },
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 82.dp, end = 14.dp),
        shape = RoundedCornerShape(18.dp),
        color = GmuDark,
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.NotificationsActive, null, tint = GmuGold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Column {
                Text("Delivery Center", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text("Email & Push", color = Color.White.copy(alpha = .65f), fontSize = 8.sp)
            }
        }
    }

    if (open) {
        NotificationDeliveryDialog(session = session, onClose = { open = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationDeliveryDialog(session: SessionState, onClose: () -> Unit) {
    val api = remember { NotificationDeliveryAdminApi() }
    val scope = rememberCoroutineScope()
    var dashboard by remember { mutableStateOf<DeliveryAdminDashboard?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("ALL") }

    suspend fun reload() {
        busy = true
        val result = runCatching { api.dashboard(session.accessToken) }
        dashboard = result.getOrNull()
        error = result.exceptionOrNull()?.message
        busy = false
    }

    LaunchedEffect(session.userId) { reload() }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        title = {
            Column {
                Text("Notification Delivery Center", fontWeight = FontWeight.Black)
                Text(
                    "Kontrol email internal, push notification, retry, dan perangkat staf.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
                val data = dashboard
                if (busy && data == null) LinearProgressIndicator(Modifier.fillMaxWidth())

                data?.let { d ->
                    DeliveryStats(d)
                    Spacer(Modifier.height(10.dp))

                    TabRow(selectedTabIndex = tab) {
                        Tab(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            text = { Text("Delivery (${d.deliveries.size})", fontSize = 11.sp) }
                        )
                        Tab(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            text = { Text("Devices (${d.activeDevices})", fontSize = 11.sp) }
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    if (tab == 0) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("ALL", "SENT", "RETRY", "FAILED", "BLOCKED_CONFIG", "PENDING").forEach { s ->
                                FilterChip(
                                    selected = filter == s,
                                    onClick = { filter = s },
                                    label = { Text(if (s == "BLOCKED_CONFIG") "BLOCKED" else s, fontSize = 9.sp) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        val shown = d.deliveries.filter { filter == "ALL" || it.status == filter }
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            if (shown.isEmpty()) {
                                EmptyDeliveryState()
                            } else {
                                shown.forEach { item ->
                                    DeliveryCard(
                                        item = item,
                                        busy = busy,
                                        onRetry = {
                                            scope.launch {
                                                busy = true
                                                val result = runCatching { api.retry(session.accessToken, item.id) }
                                                error = result.exceptionOrNull()?.message
                                                reload()
                                            }
                                        }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    } else {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            if (d.devices.isEmpty()) {
                                Text("Belum ada perangkat push yang terdaftar.", fontSize = 11.sp, color = Color.Gray)
                            } else {
                                d.devices.forEach { device ->
                                    DeviceCard(device)
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFEDEC)) {
                        Text(it, modifier = Modifier.padding(10.dp), fontSize = 10.sp, color = GmuDanger)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val retryable = dashboard?.deliveries?.count { it.status in setOf("FAILED", "RETRY", "BLOCKED_CONFIG") } ?: 0
                if (retryable > 0) {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val result = runCatching { api.retryAll(session.accessToken) }
                                error = result.exceptionOrNull()?.message
                                reload()
                            }
                        }
                    ) { Text("Retry Semua ($retryable)") }
                }
                TextButton(enabled = !busy, onClick = { scope.launch { reload() } }) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
                Button(onClick = onClose) { Text("Tutup") }
            }
        }
    )
}

@Composable
private fun DeliveryStats(data: DeliveryAdminDashboard) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        StatusStat("Sent", data.stats["SENT"] ?: 0, GmuGreen)
        StatusStat("Retry", data.stats["RETRY"] ?: 0, GmuWarn)
        StatusStat("Failed", data.stats["FAILED"] ?: 0, GmuDanger)
        StatusStat("Blocked", data.stats["BLOCKED_CONFIG"] ?: 0, Color(0xFF7A6A47))
        StatusStat("Pending", data.stats["PENDING"] ?: 0, Color(0xFF52647A))
        StatusStat("Devices", data.activeDevices, GmuDark)
    }
}

@Composable
private fun StatusStat(label: String, value: Int, color: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = .10f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value.toString(), fontWeight = FontWeight.Black, fontSize = 16.sp, color = color)
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun DeliveryCard(item: DeliveryItem, busy: Boolean, onRetry: () -> Unit) {
    val color = statusColor(item.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (item.channel == "EMAIL") Icons.Rounded.Email else Icons.Rounded.NotificationsActive,
                        null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Column {
                        Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.Black, color = GmuDark)
                        Text("${item.channel} • ${item.recipientName}", fontSize = 9.sp, color = Color.Gray)
                    }
                }
                Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .12f)) {
                    Text(
                        item.status.replace("_CONFIG", ""),
                        color = color,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(item.message, fontSize = 9.sp, color = Color.DarkGray, maxLines = 3)
            if (item.bookingId.isNotBlank()) Text("Booking: ${item.bookingId}", fontSize = 8.sp, color = Color.Gray)
            Text("Tujuan: ${item.recipient}", fontSize = 8.sp, color = Color.Gray)
            Text("Percobaan: ${item.attempts}/${item.maxAttempts}", fontSize = 8.sp, color = Color.Gray)

            if (item.lastError.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text("Error: ${item.lastError}", fontSize = 8.sp, color = GmuDanger, maxLines = 2)
            }

            if (item.status in setOf("FAILED", "RETRY", "BLOCKED_CONFIG")) {
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onRetry, enabled = !busy, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retry", fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: PushDeviceItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (device.active) Color.White else Color(0xFFF3F3F3))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Devices, null, tint = if (device.active) GmuGreen else Color.Gray)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(device.userName, fontWeight = FontWeight.Black, fontSize = 11.sp, color = GmuDark)
                Text("${device.userRole} • ${device.deviceName.ifBlank { device.platform }}", fontSize = 9.sp, color = Color.Gray)
                Text("Token: ${device.token}", fontSize = 8.sp, color = Color.Gray)
                Text("App ${device.appVersion.ifBlank { "-" }} • Last seen ${device.lastSeenAt.take(16).replace('T', ' ')}", fontSize = 8.sp, color = Color.Gray)
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = (if (device.active) GmuGreen else Color.Gray).copy(alpha = .12f)
            ) {
                Text(
                    if (device.active) "ACTIVE" else "OFF",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = if (device.active) GmuGreen else Color.Gray,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyDeliveryState() {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF6F7F4)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.NotificationsActive, null, tint = GmuGreen)
            Spacer(Modifier.height(6.dp))
            Text("Belum ada delivery pada filter ini.", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Job email/push akan muncul otomatis saat Decision Inbox membuat notifikasi.", fontSize = 9.sp, color = Color.Gray)
        }
    }
}

private fun statusColor(status: String): Color = when (status) {
    "SENT" -> GmuGreen
    "RETRY" -> GmuWarn
    "FAILED" -> GmuDanger
    "BLOCKED_CONFIG" -> Color(0xFF7A6A47)
    "PROCESSING" -> Color(0xFF546E7A)
    else -> Color(0xFF52647A)
}

private data class DeliveryAdminDashboard(
    val stats: Map<String, Int>,
    val deliveries: List<DeliveryItem>,
    val devices: List<PushDeviceItem>,
    val activeDevices: Int
)

private data class DeliveryItem(
    val id: String,
    val channel: String,
    val recipient: String,
    val recipientName: String,
    val status: String,
    val attempts: Int,
    val maxAttempts: Int,
    val title: String,
    val message: String,
    val bookingId: String,
    val lastError: String
)

private data class PushDeviceItem(
    val id: String,
    val token: String,
    val userName: String,
    val userRole: String,
    val platform: String,
    val deviceName: String,
    val appVersion: String,
    val active: Boolean,
    val lastSeenAt: String
)

private class NotificationDeliveryAdminApi {
    suspend fun dashboard(accessToken: String): DeliveryAdminDashboard = withContext(Dispatchers.IO) {
        val root = call(accessToken, JSONObject().put("action", "dashboard"))
        val statsObj = root.optJSONObject("stats") ?: JSONObject()
        val stats = linkedMapOf<String, Int>()
        statsObj.keys().forEach { key -> stats[key] = statsObj.optInt(key, 0) }

        val deliveries = mutableListOf<DeliveryItem>()
        val da = root.optJSONArray("deliveries") ?: JSONArray()
        for (i in 0 until da.length()) {
            val x = da.getJSONObject(i)
            deliveries += DeliveryItem(
                id = x.optString("id"),
                channel = x.optString("channel"),
                recipient = x.optString("recipient"),
                recipientName = x.optString("recipient_name", "Staff GMU"),
                status = x.optString("status"),
                attempts = x.optInt("attempts", 0),
                maxAttempts = x.optInt("max_attempts", 0),
                title = x.optString("title", "Notifikasi ERP"),
                message = x.optString("message", ""),
                bookingId = x.optString("booking_id", ""),
                lastError = x.optString("last_error", "")
            )
        }

        val devices = mutableListOf<PushDeviceItem>()
        val dev = root.optJSONArray("devices") ?: JSONArray()
        for (i in 0 until dev.length()) {
            val x = dev.getJSONObject(i)
            devices += PushDeviceItem(
                id = x.optString("id"),
                token = x.optString("token"),
                userName = x.optString("user_name", "Staff GMU"),
                userRole = x.optString("user_role", ""),
                platform = x.optString("platform", "Android"),
                deviceName = x.optString("device_name", ""),
                appVersion = x.optString("app_version", ""),
                active = x.optBoolean("is_active", false),
                lastSeenAt = x.optString("last_seen_at", "")
            )
        }

        DeliveryAdminDashboard(stats, deliveries, devices, root.optInt("active_devices", 0))
    }

    suspend fun retry(accessToken: String, deliveryId: String) = withContext(Dispatchers.IO) {
        call(accessToken, JSONObject().put("action", "retry").put("delivery_id", deliveryId))
        Unit
    }

    suspend fun retryAll(accessToken: String) = withContext(Dispatchers.IO) {
        call(accessToken, JSONObject().put("action", "retry_all"))
        Unit
    }

    private fun call(accessToken: String, payload: JSONObject): JSONObject {
        val conn = (URL(BuildConfig.SUPABASE_URL + "/functions/v1/gmu-notification-delivery-admin").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
        conn.disconnect()
        val root = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
        if (status !in 200..299) {
            throw IllegalStateException(
                root.optString("message", root.optString("detail", root.optString("error", "Delivery Center gagal ($status)")))
            )
        }
        return root
    }
}
