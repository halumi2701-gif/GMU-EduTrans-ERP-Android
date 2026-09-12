package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Refresh
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

/** Recipient-policy UI. Visibility is controlled by RecipientPolicyActivationGate. */
@Composable
fun RecipientPolicyCenterPreview(session: SessionState) {
    val canEdit = session.profile.role == ErpRoles.OWNER
    var open by remember { mutableStateOf(false) }

    Surface(
        onClick = { open = true },
        shape = RoundedCornerShape(18.dp),
        color = GmuDark
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.AdminPanelSettings, null, tint = GmuGold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Column {
                Text("Recipient Policy", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text(if (canEdit) "Owner control" else "View only", color = Color.White.copy(alpha = .68f), fontSize = 8.sp)
            }
        }
    }

    if (open) RecipientPolicyDialog(session = session, canEdit = canEdit, onClose = { open = false })
}

@Composable
private fun RecipientPolicyDialog(session: SessionState, canEdit: Boolean, onClose: () -> Unit) {
    val api = remember { RecipientPolicyAdminApi() }
    val scope = rememberCoroutineScope()
    var policies by remember { mutableStateOf<List<RecipientPolicyItem>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        busy = true
        val result = runCatching { api.policies(session.accessToken) }
        policies = result.getOrElse { policies }
        error = result.exceptionOrNull()?.message
        busy = false
    }

    LaunchedEffect(session.userId) { reload() }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        title = {
            Column {
                Text("Recipient & Notification Policy", fontWeight = FontWeight.Black)
                Text(
                    "Atur siapa menerima event ERP. Channel Email/Push tetap mengikuti Notification Rules.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 650.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF4F7F3)) {
                    Text(
                        "Default GMU: Direktur = critical/approval, Manager = operasional, Finance = pembayaran, TL = trip yang ditugaskan. Scope ASSIGNED_USER tidak pernah broadcast ke semua TL.",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 10.sp,
                        color = Color.DarkGray
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (busy && policies.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())

                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RecipientPolicyCatalog.events.forEach { event ->
                        val eventPolicies = policies.filter { it.eventKey == event.key }
                        EventPolicyCard(
                            event = event,
                            policies = eventPolicies,
                            busy = busy,
                            canEdit = canEdit,
                            onSet = { role, scopeName, enabled, severity ->
                                scope.launch {
                                    busy = true
                                    val result = runCatching {
                                        api.setPolicy(
                                            accessToken = session.accessToken,
                                            eventKey = event.key,
                                            label = event.label,
                                            targetRole = role,
                                            recipientScope = scopeName,
                                            minSeverity = severity,
                                            active = enabled
                                        )
                                    }
                                    error = result.exceptionOrNull()?.message
                                    reload()
                                }
                            }
                        )
                        Spacer(Modifier.height(9.dp))
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
private fun EventPolicyCard(
    event: RecipientPolicyEvent,
    policies: List<RecipientPolicyItem>,
    busy: Boolean,
    canEdit: Boolean,
    onSet: (String, String, Boolean, String) -> Unit
) {
    Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(13.dp)) {
            Text(event.label, fontSize = 12.sp, fontWeight = FontWeight.Black, color = GmuDark)
            Text("${event.key} • ${event.scope}", fontSize = 8.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            if (event.scope == "REQUESTER") {
                val item = policies.firstOrNull { it.recipientScope == "REQUESTER" }
                PolicyToggleChip(
                    label = "Pengaju saja",
                    selected = item?.active == true,
                    enabled = canEdit && !busy,
                    onClick = { onSet("ANY", "REQUESTER", item?.active != true, item?.minSeverity ?: event.defaultSeverity) }
                )
            } else if (event.scope == "ASSIGNED_USER") {
                val role = event.roles.firstOrNull() ?: "TL"
                val item = policies.firstOrNull { it.targetRole == role && it.recipientScope == "ASSIGNED_USER" }
                PolicyToggleChip(
                    label = "$role yang ditugaskan",
                    selected = item?.active == true,
                    enabled = canEdit && !busy,
                    onClick = { onSet(role, "ASSIGNED_USER", item?.active != true, item?.minSeverity ?: event.defaultSeverity) }
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    event.roles.forEach { role ->
                        val item = policies.firstOrNull { it.targetRole == role && it.recipientScope == "ROLE" }
                        PolicyToggleChip(
                            label = role,
                            selected = item?.active == true,
                            enabled = canEdit && !busy,
                            onClick = { onSet(role, "ROLE", item?.active != true, item?.minSeverity ?: event.defaultSeverity) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(7.dp))
            val active = policies.filter { it.active }
            val severity = active.minByOrNull { severityRank(it.minSeverity) }?.minSeverity ?: event.defaultSeverity
            Text("Minimum severity: $severity", fontSize = 9.sp, color = Color.Gray)
            if (!canEdit) Text("Direktur: view-only. Perubahan recipient policy dikunci untuk Owner.", fontSize = 8.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun PolicyToggleChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label, fontSize = 9.sp) }
    )
}

private fun severityRank(value: String): Int = when (value.uppercase()) {
    "CRITICAL" -> 3
    "WARNING" -> 2
    else -> 1
}

private data class RecipientPolicyEvent(
    val key: String,
    val label: String,
    val scope: String,
    val roles: List<String>,
    val defaultSeverity: String
)

private object RecipientPolicyCatalog {
    val events = listOf(
        RecipientPolicyEvent("APPROVAL_ESCALATED", "Approval / Director Handoff", "ROLE", listOf("Owner", "Director"), "CRITICAL"),
        RecipientPolicyEvent("APPROVAL_RESOLVED", "Hasil keputusan approval", "REQUESTER", listOf("ANY"), "INFO"),
        RecipientPolicyEvent("OPERATION_ALERT", "Alert operasional EduTrans", "ROLE", listOf("Manager EduTrans", "Operation"), "WARNING"),
        RecipientPolicyEvent("PAYMENT_RECORDED", "Pembayaran tercatat", "ROLE", listOf("Finance"), "INFO"),
        RecipientPolicyEvent("TRIP_ASSIGNMENT", "Penugasan trip", "ASSIGNED_USER", listOf("TL"), "INFO"),
        RecipientPolicyEvent("SYSTEM_CRITICAL", "Critical system alert", "ROLE", listOf("Owner", "Director"), "CRITICAL"),
        RecipientPolicyEvent("DELIVERY_FAILURE_ESCALATION", "Kegagalan kanal notifikasi kritis", "ROLE", listOf("Owner", "Director"), "CRITICAL")
    )
}

private data class RecipientPolicyItem(
    val id: String,
    val eventKey: String,
    val label: String,
    val targetRole: String,
    val recipientScope: String,
    val minSeverity: String,
    val active: Boolean
)

private class RecipientPolicyAdminApi {
    suspend fun policies(accessToken: String): List<RecipientPolicyItem> = withContext(Dispatchers.IO) {
        val root = call(accessToken, JSONObject().put("action", "recipient_policies"))
        val array = root.optJSONArray("recipient_policies") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val x = array.getJSONObject(i)
                add(
                    RecipientPolicyItem(
                        id = x.optString("id"),
                        eventKey = x.optString("event_key"),
                        label = x.optString("label", x.optString("event_key")),
                        targetRole = x.optString("target_role"),
                        recipientScope = x.optString("recipient_scope", "ROLE"),
                        minSeverity = x.optString("min_severity", "INFO"),
                        active = x.optBoolean("is_active", true)
                    )
                )
            }
        }
    }

    suspend fun setPolicy(
        accessToken: String,
        eventKey: String,
        label: String,
        targetRole: String,
        recipientScope: String,
        minSeverity: String,
        active: Boolean
    ) = withContext(Dispatchers.IO) {
        call(
            accessToken,
            JSONObject()
                .put("action", "set_recipient_policy")
                .put("event_key", eventKey)
                .put("label", label)
                .put("target_role", targetRole)
                .put("recipient_scope", recipientScope)
                .put("min_severity", minSeverity)
                .put("is_active", active)
        )
        Unit
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
        if (status !in 200..299) {
            throw IllegalStateException(root.optString("message", root.optString("detail", root.optString("error", "HTTP $status"))))
        }
        return root
    }
}
