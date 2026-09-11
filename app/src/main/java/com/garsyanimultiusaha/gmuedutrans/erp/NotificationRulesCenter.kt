package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

/** Owner/Director policy layer on top of Notification Delivery Center. */
@Composable
fun GmuNativeAppWithNotificationRulesPreferences(vm: MainViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithNotificationDeliveryCenter(vm)
        val session = (vm.state as? AppState.LoggedIn)?.session
        if (
            session != null &&
            vm.currentPage == AppPage.DASHBOARD &&
            (session.profile.role == ErpRoles.OWNER || ErpRoles.isDirector(session.profile.role))
        ) {
            NotificationRulesDock(session)
        }
    }
}

@Composable
private fun BoxScope.NotificationRulesDock(session: SessionState) {
    var open by remember { mutableStateOf(false) }

    Surface(
        onClick = { open = true },
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 132.dp, end = 14.dp),
        shape = RoundedCornerShape(18.dp),
        color = GmuGreen,
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.NotificationsActive, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Column {
                Text("Notification Rules", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text("Channel & escalation", color = Color.White.copy(alpha = .72f), fontSize = 8.sp)
            }
        }
    }

    if (open) NotificationRulesDialog(session, onClose = { open = false })
}

@Composable
private fun NotificationRulesDialog(session: SessionState, onClose: () -> Unit) {
    val api = remember { NotificationRulesAdminApi() }
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf<List<NotificationRuleItem>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        busy = true
        val result = runCatching { api.rules(session.accessToken) }
        rules = result.getOrElse { rules }
        error = result.exceptionOrNull()?.message
        busy = false
    }

    LaunchedEffect(session.userId) { reload() }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        title = {
            Column {
                Text("Notification Rules & Preferences", fontWeight = FontWeight.Black)
                Text(
                    "Tentukan kanal notifikasi internal GMU untuk tiap jenis keputusan.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 650.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF4F7F3)) {
                    Text(
                        "Decision Inbox ERP selalu tersedia. Rule di bawah mengatur kanal tambahan Email/Push. Mode Critical juga mengaktifkan alert kegagalan delivery otomatis.",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 10.sp,
                        color = Color.DarkGray
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (busy && rules.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())

                Column(Modifier.verticalScroll(rememberScrollState())) {
                    rules.forEach { rule ->
                        NotificationRuleCard(
                            item = rule,
                            busy = busy,
                            locked = rule.eventType == "DELIVERY_FAILURE_ESCALATION",
                            onSave = { mode, minutes ->
                                scope.launch {
                                    busy = true
                                    val result = runCatching {
                                        api.updateRule(
                                            accessToken = session.accessToken,
                                            eventType = rule.eventType,
                                            deliveryMode = mode,
                                            escalationMinutes = minutes
                                        )
                                    }
                                    error = result.exceptionOrNull()?.message
                                    reload()
                                }
                            }
                        )
                        Spacer(Modifier.height(9.dp))
                    }
                    if (rules.isEmpty() && !busy) {
                        Text("Belum ada notification rule.", color = Color.Gray, fontSize = 11.sp)
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
private fun NotificationRuleCard(
    item: NotificationRuleItem,
    busy: Boolean,
    locked: Boolean,
    onSave: (String, Int) -> Unit
) {
    var mode by remember(item.eventType, item.updatedAt) { mutableStateOf(item.deliveryMode) }
    var minutes by remember(item.eventType, item.updatedAt) { mutableIntStateOf(item.escalationMinutes) }
    val dirty = mode != item.deliveryMode || minutes != item.escalationMinutes
    val modes = listOf(
        "ERP_ONLY" to "ERP Only",
        "EMAIL" to "Email",
        "PUSH" to "Push",
        "EMAIL_PUSH" to "Email + Push",
        "CRITICAL_ESCALATION" to "Critical"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(item.label, fontSize = 12.sp, fontWeight = FontWeight.Black, color = GmuDark)
                    Text(item.eventType, fontSize = 8.sp, color = Color.Gray)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = modeColor(mode).copy(alpha = .12f)
                ) {
                    Text(
                        modeLabel(mode),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = modeColor(mode),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(Modifier.height(9.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                modes.forEach { (value, label) ->
                    FilterChip(
                        selected = mode == value,
                        enabled = !busy && !locked,
                        onClick = { mode = value },
                        label = { Text(label, fontSize = 9.sp) }
                    )
                }
            }

            if (mode == "CRITICAL_ESCALATION") {
                Spacer(Modifier.height(8.dp))
                Text("Alert kegagalan setelah:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 15, 30).forEach { value ->
                        FilterChip(
                            selected = minutes == value,
                            enabled = !busy && !locked,
                            onClick = { minutes = value },
                            label = { Text("$value menit", fontSize = 9.sp) }
                        )
                    }
                }
            }

            if (locked) {
                Spacer(Modifier.height(7.dp))
                Text(
                    "System guard: tetap ERP Only agar kegagalan delivery tidak membuat loop notifikasi eksternal.",
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            } else if (dirty) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = { onSave(mode, minutes) },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Simpan Rule", fontSize = 9.sp) }
                }
            }
        }
    }
}

private fun modeLabel(mode: String): String = when (mode) {
    "ERP_ONLY" -> "ERP ONLY"
    "EMAIL" -> "EMAIL"
    "PUSH" -> "PUSH"
    "EMAIL_PUSH" -> "EMAIL + PUSH"
    "CRITICAL_ESCALATION" -> "CRITICAL"
    else -> mode
}

private fun modeColor(mode: String): Color = when (mode) {
    "ERP_ONLY" -> Color(0xFF52647A)
    "EMAIL" -> GmuGreen
    "PUSH" -> Color(0xFF4169A1)
    "EMAIL_PUSH" -> Color(0xFF6655A5)
    "CRITICAL_ESCALATION" -> GmuDanger
    else -> Color.Gray
}

private data class NotificationRuleItem(
    val eventType: String,
    val label: String,
    val deliveryMode: String,
    val emailEnabled: Boolean,
    val pushEnabled: Boolean,
    val escalateOnFailure: Boolean,
    val escalationMinutes: Int,
    val active: Boolean,
    val updatedAt: String
)

private class NotificationRulesAdminApi {
    suspend fun rules(accessToken: String): List<NotificationRuleItem> = withContext(Dispatchers.IO) {
        val root = call(accessToken, JSONObject().put("action", "dashboard"))
        val array = root.optJSONArray("rules") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val x = array.getJSONObject(i)
                add(
                    NotificationRuleItem(
                        eventType = x.optString("event_type"),
                        label = x.optString("label", x.optString("event_type")),
                        deliveryMode = x.optString("delivery_mode", "ERP_ONLY"),
                        emailEnabled = x.optBoolean("email_enabled", false),
                        pushEnabled = x.optBoolean("push_enabled", false),
                        escalateOnFailure = x.optBoolean("escalate_on_failure", false),
                        escalationMinutes = x.optInt("escalation_after_minutes", 15),
                        active = x.optBoolean("is_active", true),
                        updatedAt = x.optString("updated_at", "")
                    )
                )
            }
        }
    }

    suspend fun updateRule(
        accessToken: String,
        eventType: String,
        deliveryMode: String,
        escalationMinutes: Int
    ) = withContext(Dispatchers.IO) {
        call(
            accessToken,
            JSONObject()
                .put("action", "update_rule")
                .put("event_type", eventType)
                .put("delivery_mode", deliveryMode)
                .put("escalation_after_minutes", escalationMinutes)
                .put("is_active", true)
        )
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
        val text = if (stream != null) BufferedReader(InputStreamReader(stream)).use { it.readText() } else ""
        conn.disconnect()
        val root = if (text.isBlank()) JSONObject() else JSONObject(text)
        if (status !in 200..299) {
            throw IllegalStateException(root.optString("message", root.optString("detail", root.optString("error", "HTTP $status"))))
        }
        return root
    }
}
