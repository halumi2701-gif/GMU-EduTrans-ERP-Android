package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

private const val OWNER_REVIEW_PREFIX = "[EXEC]"
private const val OWNER_REVIEW_STATUS = "Awaiting Owner Review"

/**
 * Owner-only final review gate for executive actions.
 * PIC may submit completion evidence, but only Owner can close the action.
 */
@Composable
fun GmuNativeAppWithOwnerReviewGate(vm: MainViewModel) {
    var openReview by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithPicActionInbox(vm)

        val state = vm.state
        if (
            state is AppState.LoggedIn &&
            state.session.profile.role == ErpRoles.OWNER &&
            vm.currentPage == AppPage.DASHBOARD
        ) {
            val reviewRows = ownerReviewRows(vm)
            val critical = reviewRows.count { it.text("priority").equals("Critical", true) }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 184.dp, end = 16.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (critical > 0) Color(0xFFFFECEC) else Color.White,
                tonalElevation = 5.dp,
                shadowElevation = 5.dp
            ) {
                TextButton(onClick = { openReview = true }) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Owner Review Gate",
                            fontWeight = FontWeight.Black,
                            color = GmuDark,
                            fontSize = 11.sp
                        )
                        Text(
                            if (reviewRows.isEmpty()) "Tidak ada review" else "${reviewRows.size} menunggu • $critical critical",
                            color = if (critical > 0) GmuDanger else GmuGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            if (openReview) {
                OwnerReviewDialog(
                    vm = vm,
                    session = state.session,
                    onDismiss = { openReview = false }
                )
            }
        }
    }
}

@Composable
private fun OwnerReviewDialog(
    vm: MainViewModel,
    session: SessionState,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<ErpRow?>(null) }
    var decision by remember { mutableStateOf<OwnerReviewDecision?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val rows = ownerReviewRows(vm).sortedWith(
        compareBy<ErpRow> { ownerReviewPriorityRank(it.text("priority")) }
            .thenBy { it.text("due_date") }
    )
    val critical = rows.count { it.text("priority").equals("Critical", true) }
    val overdue = rows.count { ownerReviewDaysUntil(it.text("due_date"))?.let { days -> days < 0 } == true }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFF7F8FA)
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Owner Review & Evidence Gate", fontWeight = FontWeight.Black, fontSize = 19.sp, color = GmuDark)
                        Text(
                            "PIC mengajukan selesai → Owner verifikasi bukti → Close atau Return.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnerReviewMetric("Review", rows.size, GmuGold, Modifier.weight(1f))
                    OwnerReviewMetric("Critical", critical, GmuDanger, Modifier.weight(1f))
                    OwnerReviewMetric("Overdue", overdue, GmuWarn, Modifier.weight(1f))
                }

                notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7EF))) {
                        Text(it, Modifier.fillMaxWidth().padding(10.dp), fontSize = 10.sp, color = GmuGreen)
                    }
                }

                Spacer(Modifier.height(9.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    if (rows.isEmpty()) {
                        item {
                            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                    Text("Semua executive action sudah ditinjau.", fontWeight = FontWeight.Bold, color = GmuGreen)
                                    Text("Tidak ada PIC completion yang menunggu keputusan Owner.", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                    items(rows, key = { it.id }) { row ->
                        OwnerReviewCard(
                            vm = vm,
                            row = row,
                            onOpenEvidence = { selected = row },
                            onApprove = { selected = row; decision = OwnerReviewDecision.APPROVE },
                            onReturn = { selected = row; decision = OwnerReviewDecision.RETURN }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { vm.loadAll() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Refresh Owner Review", fontSize = 10.sp)
                }
            }
        }
    }

    selected?.takeIf { decision == null }?.let { row ->
        OwnerEvidenceDialog(
            vm = vm,
            row = row,
            onDismiss = { selected = null },
            onApprove = { decision = OwnerReviewDecision.APPROVE },
            onReturn = { decision = OwnerReviewDecision.RETURN }
        )
    }

    val activeRow = selected
    val activeDecision = decision
    if (activeRow != null && activeDecision != null) {
        OwnerDecisionDialog(
            vm = vm,
            session = session,
            row = activeRow,
            decision = activeDecision,
            onDismiss = { if (!vm.actionBusy) { decision = null; selected = null } },
            onDone = { msg ->
                notice = msg
                decision = null
                selected = null
            }
        )
    }
}

private enum class OwnerReviewDecision { APPROVE, RETURN }

@Composable
private fun OwnerReviewMetric(label: String, value: Int, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(11.dp)) {
            Text(value.toString(), fontWeight = FontWeight.Black, fontSize = 18.sp, color = accent)
            Text(label, fontSize = 9.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun OwnerReviewCard(
    vm: MainViewModel,
    row: ErpRow,
    onOpenEvidence: () -> Unit,
    onApprove: () -> Unit,
    onReturn: () -> Unit
) {
    val pic = vm.table("profiles").firstOrNull { it.id == row.text("staff_id") }
    val priority = row.text("priority").ifBlank { "Normal" }
    val title = row.text("title").removePrefix(OWNER_REVIEW_PREFIX).trim().ifBlank { "Executive action" }
    val days = ownerReviewDaysUntil(row.text("due_date"))
    val accent = when {
        priority.equals("Critical", true) -> GmuDanger
        days != null && days < 0 -> GmuDanger
        days == 0 -> GmuWarn
        else -> GmuGold
    }

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(priority.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = accent)
                    Text(title, fontWeight = FontWeight.Black, fontSize = 12.sp, color = GmuDark)
                    Text(
                        "PIC: ${pic?.text("full_name").takeUnless { it.isNullOrBlank() } ?: row.text("staff_id").take(8)}",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("OWNER REVIEW", fontSize = 9.sp, fontWeight = FontWeight.Black, color = GmuGold)
                    row.text("due_date").takeIf { it.isNotBlank() }?.let {
                        Text("Deadline $it", fontSize = 9.sp, color = accent)
                    }
                }
            }

            val evidence = ownerCompletionEvidence(row.text("notes"))
            Spacer(Modifier.height(7.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F5F7))) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Text("Evidence terakhir PIC", fontSize = 9.sp, fontWeight = FontWeight.Black, color = GmuDark)
                    Text(
                        evidence.ifBlank { "Belum ditemukan catatan evidence PIC." }.takeLast(520),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenEvidence, enabled = !vm.actionBusy) { Text("Lihat Bukti", fontSize = 10.sp) }
                TextButton(onClick = onReturn, enabled = !vm.actionBusy) { Text("Return", fontSize = 10.sp, color = GmuDanger) }
                Spacer(Modifier.width(4.dp))
                Button(onClick = onApprove, enabled = !vm.actionBusy, shape = RoundedCornerShape(12.dp)) {
                    Text("Approve & Close", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun OwnerEvidenceDialog(
    vm: MainViewModel,
    row: ErpRow,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReturn: () -> Unit
) {
    val title = row.text("title").removePrefix(OWNER_REVIEW_PREFIX).trim().ifBlank { "Executive action" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Evidence Review") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontWeight = FontWeight.Black, color = GmuDark)
                Text("Status: ${row.text("status")}", fontSize = 10.sp, color = GmuGold)
                Text("Deadline: ${row.text("due_date").ifBlank { "-" }}", fontSize = 10.sp, color = Color.Gray)
                HorizontalDivider()
                Text("Progress & evidence trail", fontSize = 10.sp, fontWeight = FontWeight.Black, color = GmuDark)
                Text(
                    row.text("notes").ifBlank { "Belum ada catatan bukti/progress." },
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(onClick = onApprove, enabled = !vm.actionBusy) { Text("Approve & Close") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReturn, enabled = !vm.actionBusy) { Text("Return to PIC", color = GmuDanger) }
                TextButton(onClick = onDismiss, enabled = !vm.actionBusy) { Text("Tutup") }
            }
        }
    )
}

@Composable
private fun OwnerDecisionDialog(
    vm: MainViewModel,
    session: SessionState,
    row: ErpRow,
    decision: OwnerReviewDecision,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    val approving = decision == OwnerReviewDecision.APPROVE
    var note by remember(row.id, decision) {
        mutableStateOf(if (approving) "Bukti dan progress PIC telah diverifikasi." else "")
    }

    AlertDialog(
        onDismissRequest = { if (!vm.actionBusy) onDismiss() },
        title = { Text(if (approving) "Approve & Close" else "Return to PIC") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (approving)
                        "Penutupan final hanya dilakukan setelah Owner memverifikasi bukti PIC."
                    else
                        "Catatan revisi wajib diisi. Action akan kembali ke PIC sebagai In Progress.",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text(if (approving) "Catatan verifikasi Owner" else "Catatan revisi") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tag = if (approving) "OWNER APPROVED" else "OWNER RETURN"
                    val notes = ownerAppendReviewNote(
                        existing = row.text("notes"),
                        tag = tag,
                        reviewer = session.profile.fullName,
                        note = note.trim()
                    )
                    vm.update(
                        "staff_assignments",
                        row.id,
                        mapOf(
                            "status" to if (approving) "Done" else "In Progress",
                            "notes" to notes
                        ),
                        if (approving) "Executive action diverifikasi dan ditutup Owner." else "Executive action dikembalikan ke PIC untuk revisi."
                    ) { ok, msg ->
                        if (ok) onDone(msg)
                    }
                },
                enabled = !vm.actionBusy && note.isNotBlank()
            ) { Text(if (vm.actionBusy) "Menyimpan…" else if (approving) "Approve & Close" else "Return to PIC") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.actionBusy) { Text("Batal") } }
    )
}

private fun ownerReviewRows(vm: MainViewModel): List<ErpRow> =
    vm.table("staff_assignments").filter {
        it.text("title").startsWith(OWNER_REVIEW_PREFIX) && it.text("status") == OWNER_REVIEW_STATUS
    }

private fun ownerReviewPriorityRank(priority: String): Int = when (priority.lowercase(Locale.US)) {
    "critical" -> 0
    "high" -> 1
    else -> 2
}

private fun ownerCompletionEvidence(notes: String): String {
    if (notes.isBlank()) return ""
    val marker = "PIC COMPLETION"
    val index = notes.lastIndexOf(marker)
    return if (index >= 0) notes.substring(index) else notes.takeLast(700)
}

private fun ownerAppendReviewNote(existing: String, tag: String, reviewer: String, note: String): String {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
    val entry = "[$timestamp] $tag by $reviewer: $note"
    return if (existing.isBlank()) entry else existing.trimEnd() + "\n" + entry
}

private fun ownerReviewDaysUntil(date: String): Int? = runCatching {
    if (date.isBlank()) return@runCatching null
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val target = formatter.parse(date) ?: return@runCatching null
    val today = formatter.parse(formatter.format(Date())) ?: return@runCatching null
    ((target.time - today.time) / 86_400_000L).toInt()
}.getOrNull()
