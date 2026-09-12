package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val PIC_EXEC_PREFIX = "[EXEC]"
private const val PIC_AWAITING_OWNER = "Awaiting Owner Review"

/**
 * Personal Executive Action Inbox for Director / Manager EduTrans.
 * PIC can start work, post progress, and submit completion evidence.
 * Final closure remains Owner-controlled in ExecutiveActionQueue.
 */
@Composable
fun GmuNativeAppWithPicActionInbox(vm: MainViewModel) {
    var openInbox by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<ErpRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val api = remember { SupabaseApi() }

    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithExecutiveActionQueue(vm)

        val state = vm.state
        if (state is AppState.LoggedIn) {
            val session = state.session
            val role = session.profile.role
            val eligible = ErpRoles.isDirector(role) || ErpRoles.isManagerEduTrans(role)

            if (eligible && vm.currentPage == AppPage.DASHBOARD) {
                LaunchedEffect(session.userId, refreshKey) {
                    loading = true
                    loadError = null
                    runCatching {
                        api.getRows(session.accessToken, "staff_assignments", "created_at.desc")
                    }.onSuccess { loaded ->
                        rows = loaded.filter { row ->
                            row.text("title").startsWith(PIC_EXEC_PREFIX) &&
                                row.text("staff_id") in setOf(session.userId, session.profile.id)
                        }
                    }.onFailure { error ->
                        rows = emptyList()
                        loadError = error.message ?: "Executive Action Inbox gagal dimuat."
                    }
                    loading = false
                }

                val openCount = rows.count { !picActionDone(it) && it.text("status") != PIC_AWAITING_OWNER }
                val reviewCount = rows.count { it.text("status") == PIC_AWAITING_OWNER }
                val overdue = rows.count { !picActionDone(it) && picDaysUntil(it.text("due_date"))?.let { d -> d < 0 } == true }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 126.dp, start = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (overdue > 0) Color(0xFFFFECEC) else Color.White,
                    tonalElevation = 5.dp,
                    shadowElevation = 5.dp
                ) {
                    TextButton(onClick = { openInbox = true }) {
                        Column {
                            Text(
                                "My Executive Actions",
                                fontWeight = FontWeight.Black,
                                color = GmuDark,
                                fontSize = 11.sp
                            )
                            Text(
                                when {
                                    loading -> "Memuat…"
                                    loadError != null -> "Perlu refresh"
                                    openCount + reviewCount == 0 -> "Tidak ada action"
                                    else -> "$openCount kerja • $reviewCount review"
                                },
                                color = if (overdue > 0) GmuDanger else GmuGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                if (openInbox) {
                    PicActionInboxDialog(
                        vm = vm,
                        session = session,
                        rows = rows,
                        loading = loading,
                        loadError = loadError,
                        onRefresh = { refreshKey++ },
                        onDismiss = { openInbox = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun PicActionInboxDialog(
    vm: MainViewModel,
    session: SessionState,
    rows: List<ErpRow>,
    loading: Boolean,
    loadError: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var filter by remember { mutableStateOf("Open") }
    var notice by remember { mutableStateOf<String?>(null) }
    var progressRow by remember { mutableStateOf<ErpRow?>(null) }
    var completionRow by remember { mutableStateOf<ErpRow?>(null) }

    val filtered = when (filter) {
        "Open" -> rows.filter { !picActionDone(it) && it.text("status") != PIC_AWAITING_OWNER }
        "Owner Review" -> rows.filter { it.text("status") == PIC_AWAITING_OWNER }
        "Done" -> rows.filter(::picActionDone)
        else -> rows
    }.sortedWith(compareBy<ErpRow> { picDueSort(it) }.thenBy { it.text("due_date") })

    val openCount = rows.count { !picActionDone(it) && it.text("status") != PIC_AWAITING_OWNER }
    val ownerReview = rows.count { it.text("status") == PIC_AWAITING_OWNER }
    val overdue = rows.count { !picActionDone(it) && picDaysUntil(it.text("due_date"))?.let { d -> d < 0 } == true }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFF7F8FA)
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Personal Action Inbox", fontWeight = FontWeight.Black, fontSize = 19.sp, color = GmuDark)
                        Text(
                            "${session.profile.fullName} • ${session.profile.roleLabel}",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PicMetric("Dikerjakan", openCount, GmuWarn, Modifier.weight(1f))
                    PicMetric("Owner Review", ownerReview, GmuGold, Modifier.weight(1f))
                    PicMetric("SLA", overdue, GmuDanger, Modifier.weight(1f))
                }

                notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7EF))) {
                        Text(it, Modifier.fillMaxWidth().padding(10.dp), fontSize = 10.sp, color = GmuGreen)
                    }
                }
                loadError?.let {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFECEC))) {
                        Text(it, Modifier.fillMaxWidth().padding(10.dp), fontSize = 10.sp, color = GmuDanger)
                    }
                }

                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Open", "Owner Review", "Done", "All").forEach { value ->
                        FilterChip(
                            selected = filter == value,
                            onClick = { filter = value },
                            label = { Text(value, fontSize = 9.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    if (loading && filtered.isEmpty()) {
                        item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    } else if (filtered.isEmpty()) {
                        item {
                            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Text(
                                    "Tidak ada executive action pada filter ini.",
                                    Modifier.fillMaxWidth().padding(18.dp),
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    items(filtered, key = { it.id }) { row ->
                        PicActionCard(
                            vm = vm,
                            row = row,
                            onProgress = { progressRow = row },
                            onSubmitDone = { completionRow = row },
                            onChanged = { ok, msg ->
                                notice = msg
                                if (ok) onRefresh()
                            }
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (loading) "Memuat…" else "Refresh My Actions", fontSize = 10.sp)
                }
            }
        }
    }

    progressRow?.let { row ->
        PicProgressDialog(
            title = "Update Progress",
            confirmLabel = "Simpan Update",
            busy = vm.actionBusy,
            onDismiss = { progressRow = null },
            onConfirm = { progress ->
                val notes = picAppendNote(row.text("notes"), "PIC UPDATE", progress)
                vm.update(
                    "staff_assignments",
                    row.id,
                    mapOf("notes" to notes, "status" to "In Progress"),
                    "Progress executive action diperbarui."
                ) { ok, msg ->
                    notice = msg
                    if (ok) {
                        progressRow = null
                        onRefresh()
                    }
                }
            }
        )
    }

    completionRow?.let { row ->
        PicProgressDialog(
            title = "Ajukan Selesai ke Owner",
            confirmLabel = "Ajukan Selesai",
            busy = vm.actionBusy,
            onDismiss = { completionRow = null },
            onConfirm = { evidence ->
                val notes = picAppendNote(row.text("notes"), "PIC COMPLETION", evidence)
                vm.update(
                    "staff_assignments",
                    row.id,
                    mapOf("notes" to notes, "status" to PIC_AWAITING_OWNER),
                    "Executive action diajukan selesai ke Owner."
                ) { ok, msg ->
                    notice = msg
                    if (ok) {
                        completionRow = null
                        onRefresh()
                    }
                }
            }
        )
    }
}

@Composable
private fun PicMetric(label: String, value: Int, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(11.dp)) {
            Text(value.toString(), fontWeight = FontWeight.Black, fontSize = 18.sp, color = accent)
            Text(label, fontSize = 9.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun PicActionCard(
    vm: MainViewModel,
    row: ErpRow,
    onProgress: () -> Unit,
    onSubmitDone: () -> Unit,
    onChanged: (Boolean, String) -> Unit
) {
    val status = row.text("status").ifBlank { "Assigned" }
    val priority = row.text("priority").ifBlank { "Normal" }
    val due = row.text("due_date")
    val days = picDaysUntil(due)
    val title = row.text("title").removePrefix(PIC_EXEC_PREFIX).trim().ifBlank { "Executive action" }
    val done = picActionDone(row)
    val awaiting = status == PIC_AWAITING_OWNER
    val overdue = !done && days != null && days < 0
    val accent = when {
        done -> GmuGreen
        overdue -> GmuDanger
        days == 0 -> GmuWarn
        priority.equals("Critical", true) -> GmuDanger
        else -> GmuDark
    }

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(priority.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = accent)
                    Text(title, fontWeight = FontWeight.Black, fontSize = 12.sp, color = GmuDark)
                    if (due.isNotBlank()) Text("Deadline $due • ${picSlaLabel(row)}", fontSize = 9.sp, color = accent)
                }
                Text(status, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = accent)
            }

            val notes = row.text("notes")
            if (notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(notes.takeLast(360), fontSize = 10.sp, color = Color.Gray)
            }

            Spacer(Modifier.height(9.dp))
            when {
                done -> Text("Ditutup final oleh Owner.", fontSize = 10.sp, color = GmuGreen, fontWeight = FontWeight.Bold)
                awaiting -> Text(
                    "Sudah diajukan. Menunggu Owner memverifikasi dan menutup action.",
                    fontSize = 10.sp,
                    color = GmuGold,
                    fontWeight = FontWeight.Bold
                )
                status == "Assigned" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = {
                            vm.update(
                                "staff_assignments",
                                row.id,
                                mapOf("status" to "In Progress"),
                                "Executive action mulai dikerjakan PIC."
                            ) { ok, msg -> onChanged(ok, msg) }
                        },
                        enabled = !vm.actionBusy,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Mulai", fontSize = 10.sp) }
                }
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onProgress, enabled = !vm.actionBusy) { Text("Update Progress", fontSize = 10.sp) }
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = onSubmitDone, enabled = !vm.actionBusy, shape = RoundedCornerShape(12.dp)) {
                        Text("Ajukan Selesai", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PicProgressDialog(
    title: String,
    confirmLabel: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(title) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (title.startsWith("Ajukan"))
                        "Tulis hasil/output yang sudah diselesaikan. Owner tetap memegang penutupan final."
                    else "Catat progres terbaru agar Owner dapat memantau tindak lanjut.",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("Catatan") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim()) },
                enabled = !busy && text.isNotBlank()
            ) { Text(if (busy) "Menyimpan…" else confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

private fun picActionDone(row: ErpRow): Boolean =
    row.text("status") in setOf("Done", "Completed", "Closed", "Resolved")

private fun picDueSort(row: ErpRow): Int {
    if (picActionDone(row)) return 4
    if (row.text("status") == PIC_AWAITING_OWNER) return 3
    val days = picDaysUntil(row.text("due_date")) ?: return 2
    return when {
        days < 0 -> 0
        days <= 1 -> 1
        else -> 2
    }
}

private fun picSlaLabel(row: ErpRow): String {
    if (picActionDone(row)) return "CLOSED"
    if (row.text("status") == PIC_AWAITING_OWNER) return "OWNER REVIEW"
    val days = picDaysUntil(row.text("due_date")) ?: return "CHECK DATE"
    return when {
        days < 0 -> "SLA +${-days} HARI"
        days == 0 -> "DUE TODAY"
        days == 1 -> "DUE BESOK"
        else -> "H-$days"
    }
}

private fun picDaysUntil(date: String): Int? = runCatching {
    if (date.isBlank()) return@runCatching null
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val target = formatter.parse(date) ?: return@runCatching null
    val today = formatter.parse(formatter.format(Date())) ?: return@runCatching null
    ((target.time - today.time).toDouble() / 86_400_000.0).roundToInt()
}.getOrNull()

private fun picAppendNote(existing: String, type: String, text: String): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
    val entry = "$type $stamp: ${text.trim()}"
    return listOf(existing.trim(), entry).filter { it.isNotBlank() }.joinToString("\n")
}
