package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Generic role-aware inbox for Finance, Operation, TL and Manager operational events. */
@Composable
fun StaffNotificationInboxPreview(vm: MainViewModel, session: SessionState) {
    val api = remember { StaffNotificationApi() }
    val scope = rememberCoroutineScope()
    var rows by remember(session.userId) { mutableStateOf<List<StaffNotificationItem>>(emptyList()) }
    var open by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        val result = runCatching { api.inbox(session.accessToken) }
        rows = result.getOrElse { rows }
        error = result.exceptionOrNull()?.message
        loading = false
    }

    LaunchedEffect(session.userId) { reload() }
    val unread = rows.count { !it.read }
    if (unread == 0) return

    Surface(
        onClick = { open = true },
        shape = RoundedCornerShape(18.dp),
        color = if (rows.any { !it.read && it.severity == "CRITICAL" }) Color(0xFFFFEDEC) else GmuDark,
        shadowElevation = 8.dp
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, null, tint = GmuGold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("$unread Staff Inbox", color = if (rows.any { !it.read && it.severity == "CRITICAL" }) GmuDark else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Staff Notification Inbox") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Notifikasi disaring server berdasarkan role dan konteks penugasan. TL hanya menerima trip yang ditugaskan kepadanya.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(10.dp))
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    rows.take(30).forEach { item ->
                        StaffNotificationCard(
                            item = item,
                            onOpen = {
                                scope.launch {
                                    if (!item.read) runCatching { api.markRead(session.accessToken, item.id) }
                                    navigateStaffNotification(vm, item.targetPage)
                                    open = false
                                }
                            },
                            onRead = {
                                scope.launch {
                                    runCatching { api.markRead(session.accessToken, item.id) }
                                    reload()
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    error?.let { Text(it, color = GmuDanger, fontSize = 9.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { api.markAllRead(session.accessToken) }
                        reload()
                    }
                }) { Text("Tandai semua dibaca") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Tutup") } }
        )
    }
}

@Composable
private fun StaffNotificationCard(item: StaffNotificationItem, onOpen: () -> Unit, onRead: () -> Unit) {
    val accent = when (item.severity) {
        "CRITICAL" -> GmuDanger
        "WARNING" -> GmuWarn
        else -> GmuGreen
    }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (item.read) Color.White else Color(0xFFFFFBF2))) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.Black, fontSize = 11.sp, color = GmuDark)
                    Text(item.eventKey, fontSize = 8.sp, color = Color.Gray)
                }
                Text(if (item.read) item.severity else "NEW", color = accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(5.dp))
            Text(item.message, fontSize = 9.sp, color = Color.DarkGray)
            if (item.bookingId.isNotBlank()) Text("Booking: ${item.bookingId}", fontSize = 8.sp, color = Color.Gray)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!item.read) TextButton(onClick = onRead) { Text("Dibaca", fontSize = 9.sp) }
                Button(onClick = onOpen, shape = RoundedCornerShape(12.dp)) { Text("Buka", fontSize = 9.sp) }
            }
        }
    }
}

private fun navigateStaffNotification(vm: MainViewModel, target: String) {
    val page = when (target.uppercase()) {
        "OPERATIONS" -> AppPage.OPERATIONS
        "WORKFLOW" -> AppPage.WORKFLOW
        "TRIP_FOLDER" -> AppPage.TRIP_FOLDER
        else -> AppPage.DASHBOARD
    }
    vm.navigate(page)
}

private data class StaffNotificationItem(
    val id: String,
    val eventKey: String,
    val severity: String,
    val title: String,
    val message: String,
    val bookingId: String,
    val targetPage: String,
    val read: Boolean
)

private class StaffNotificationApi {
    suspend fun inbox(accessToken: String): List<StaffNotificationItem> = withContext(Dispatchers.IO) {
        val root = call(accessToken, JSONObject().put("action", "staff_inbox"))
        val array = root.optJSONArray("notifications") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val x = array.getJSONObject(i)
                add(
                    StaffNotificationItem(
                        id = x.optString("id"),
                        eventKey = x.optString("event_key"),
                        severity = x.optString("severity", "INFO"),
                        title = x.optString("title", "Notifikasi ERP"),
                        message = x.optString("message", ""),
                        bookingId = x.optString("booking_id", ""),
                        targetPage = x.optString("target_page", "DASHBOARD"),
                        read = x.optBoolean("is_read", false)
                    )
                )
            }
        }
    }

    suspend fun markRead(accessToken: String, id: String) = withContext(Dispatchers.IO) {
        call(accessToken, JSONObject().put("action", "mark_staff_notification_read").put("id", id)); Unit
    }

    suspend fun markAllRead(accessToken: String) = withContext(Dispatchers.IO) {
        call(accessToken, JSONObject().put("action", "mark_all_staff_notifications_read")); Unit
    }

    private fun call(accessToken: String, payload: JSONObject): JSONObject {
        val conn = (URL(BuildConfig.SUPABASE_URL + "/functions/v1/gmu-recipient-policy-admin").openConnection() as HttpURLConnection).apply {
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
        val text = if (stream != null) BufferedReader(InputStreamReader(stream)).use { it.readText() } else ""
        conn.disconnect()
        val root = if (text.isBlank()) JSONObject() else JSONObject(text)
        if (status !in 200..299) throw IllegalStateException(root.optString("message", root.optString("detail", root.optString("error", "HTTP $status"))))
        return root
    }
}
