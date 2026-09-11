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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Closed-loop notification layer for Manager EduTrans <-> Director/Owner decisions.
 * Reads approval_notifications through RLS, so each user only sees their own inbox.
 */
@Composable
fun GmuNativeAppWithDecisionNotifications(vm: MainViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithDirectorEscalation(vm)
        val session = (vm.state as? AppState.LoggedIn)?.session
        if (session != null && vm.currentPage == AppPage.DASHBOARD && DecisionNotificationPolicy.supports(session.profile.role)) {
            DecisionNotificationDock(vm, session)
        }
    }
}

private object DecisionNotificationPolicy {
    fun supports(role: String): Boolean =
        role == ErpRoles.OWNER || ErpRoles.isDirector(role) || ErpRoles.isManagerEduTrans(role)
}

@Composable
private fun BoxScope.DecisionNotificationDock(vm: MainViewModel, session: SessionState) {
    val api = remember { SupabaseApi() }
    val scope = rememberCoroutineScope()
    var notifications by remember(session.userId) { mutableStateOf<List<ErpRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        val result = runCatching {
            api.getRows(session.accessToken, "approval_notifications", "created_at.desc")
        }
        notifications = result.getOrElse { emptyList() }
        error = result.exceptionOrNull()?.message
        loading = false
    }

    LaunchedEffect(session.userId, vm.rows) { reload() }

    val unread = notifications.filter { !it.bool("is_read") }
    val recent = notifications.take(20)

    if (unread.isNotEmpty()) {
        Surface(
            onClick = { open = true },
            modifier = Modifier.align(Alignment.TopStart).padding(top = 82.dp, start = 14.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (unread.any { it.text("severity") == "CRITICAL" }) Color(0xFFFFEDEC) else GmuDark,
            shadowElevation = 8.dp
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = if (unread.any { it.text("severity") == "CRITICAL" }) GmuDanger else GmuGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "${unread.size} Decision Inbox",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (unread.any { it.text("severity") == "CRITICAL" }) GmuDark else Color.White
                )
            }
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Decision & Escalation Inbox") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        when {
                            ErpRoles.isManagerEduTrans(session.profile.role) -> "Keputusan Direktur dan status eskalasi operasional Anda."
                            else -> "Eskalasi Manager EduTrans dan approval yang perlu perhatian Anda."
                        },
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(10.dp))

                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else if (recent.isEmpty()) {
                        Text("Belum ada notifikasi keputusan.", fontSize = 11.sp, color = Color.Gray)
                    } else {
                        recent.forEach { item ->
                            DecisionNotificationCard(
                                vm = vm,
                                item = item,
                                onRead = {
                                    scope.launch {
                                        runCatching {
                                            api.updateRow(
                                                session.accessToken,
                                                "approval_notifications",
                                                item.id,
                                                mapOf(
                                                    "is_read" to true,
                                                    "read_at" to nowIso()
                                                )
                                            )
                                        }
                                        reload()
                                    }
                                },
                                onOpenWorkflow = {
                                    if (!item.bool("is_read")) {
                                        scope.launch {
                                            runCatching {
                                                api.updateRow(
                                                    session.accessToken,
                                                    "approval_notifications",
                                                    item.id,
                                                    mapOf("is_read" to true, "read_at" to nowIso())
                                                )
                                            }
                                            reload()
                                        }
                                    }
                                    vm.navigate(AppPage.WORKFLOW)
                                    open = false
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("Inbox belum dapat disinkronkan: $it", fontSize = 10.sp, color = GmuDanger)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = unread.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            unread.forEach { item ->
                                runCatching {
                                    api.updateRow(
                                        session.accessToken,
                                        "approval_notifications",
                                        item.id,
                                        mapOf("is_read" to true, "read_at" to nowIso())
                                    )
                                }
                            }
                            reload()
                        }
                    }
                ) { Text("Tandai semua dibaca") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Tutup") } }
        )
    }
}

@Composable
private fun DecisionNotificationCard(
    vm: MainViewModel,
    item: ErpRow,
    onRead: () -> Unit,
    onOpenWorkflow: () -> Unit
) {
    val unread = !item.bool("is_read")
    val severity = item.text("severity")
    val accent = when (severity) {
        "CRITICAL" -> GmuDanger
        "WARNING" -> GmuWarn
        else -> GmuGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unread) Color(0xFFFFFBF2) else Color.White
        )
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(item.text("title"), fontWeight = FontWeight.Black, color = GmuDark, fontSize = 12.sp)
                    val bookingId = item.text("booking_id")
                    if (bookingId.isNotBlank()) {
                        Text(bookingLabel(vm, bookingId), fontSize = 10.sp, color = Color.Gray)
                    }
                }
                Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = .12f)) {
                    Text(
                        if (unread) "NEW" else severity.ifBlank { "INFO" },
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(item.text("message"), fontSize = 10.sp, color = Color.DarkGray)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (unread) TextButton(onClick = onRead) { Text("Dibaca", fontSize = 10.sp) }
                Button(onClick = onOpenWorkflow, shape = RoundedCornerShape(12.dp)) {
                    Text("Buka Workflow", fontSize = 10.sp)
                }
            }
        }
    }
}

private fun nowIso(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
