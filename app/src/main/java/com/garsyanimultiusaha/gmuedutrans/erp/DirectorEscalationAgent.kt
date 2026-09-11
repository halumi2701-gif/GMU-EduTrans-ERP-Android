package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Escalation handoff layer between Manager EduTrans and Director.
 * Uses the existing approvals table so there is one authoritative workflow.
 */
@Composable
fun GmuNativeAppWithDirectorEscalation(vm: MainViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithManagerProactiveAgent(vm)
        val session = (vm.state as? AppState.LoggedIn)?.session
        if (session != null && vm.currentPage == AppPage.DASHBOARD) {
            when {
                ErpRoles.isManagerEduTrans(session.profile.role) -> ManagerEscalationDock(vm, session)
                ErpRoles.isDirector(session.profile.role) || session.profile.role == ErpRoles.OWNER -> DirectorHandoffDock(vm, session)
            }
        }
    }
}

@Composable
private fun BoxScope.ManagerEscalationDock(vm: MainViewModel, session: SessionState) {
    val candidates = remember(vm.bookings, vm.rows) { buildEscalationCandidates(vm) }
    var open by remember { mutableStateOf(false) }
    val pendingRefs = vm.table("approvals")
        .filter { it.text("status") == "Pending" && it.text("approval_type") == "Director Escalation" }
        .map { it.text("reference_id") }
        .toSet()
    val actionable = candidates.filter { "AI_ESCALATION:${it.booking.id}" !in pendingRefs }

    if (actionable.isEmpty()) return

    Surface(
        onClick = { open = true },
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 82.dp, end = 14.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFF0ED),
        shadowElevation = 7.dp
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = GmuDanger, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("${actionable.size} perlu eskalasi", fontSize = 10.sp, fontWeight = FontWeight.Black, color = GmuDark)
        }
    }

    if (open) {
        ManagerEscalationDialog(vm = vm, session = session, candidates = actionable, onDismiss = { open = false })
    }
}

@Composable
private fun ManagerEscalationDialog(
    vm: MainViewModel,
    session: SessionState,
    candidates: List<DirectorEscalationCandidate>,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<DirectorEscalationCandidate?>(candidates.firstOrNull()) }
    var submitting by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escalation to Director") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Ops Agent menyiapkan handoff hanya untuk masalah kritis atau keputusan di atas kewenangan Manager.", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(10.dp))
                candidates.take(5).forEach { item ->
                    Card(
                        onClick = { selected = item },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = if (selected?.booking?.id == item.booking.id) Color(0xFFFFF7E8) else Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${item.booking.bookingNo} • ${item.booking.customerName}", fontWeight = FontWeight.Black, color = GmuDark, fontSize = 12.sp)
                            Text(item.reason, fontSize = 10.sp, color = GmuDanger, fontWeight = FontWeight.Bold)
                            Text(item.impact, fontSize = 10.sp, color = Color.Gray)
                            if (item.amountIdr > 0) Text("Nilai keputusan: ${rupiah(item.amountIdr)}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                selected?.let { item ->
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(16.dp), color = GmuSoft) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Ringkasan untuk Direktur", fontWeight = FontWeight.Black, fontSize = 12.sp, color = GmuDark)
                            Text("Masalah: ${item.reason}", fontSize = 10.sp)
                            Text("Dampak: ${item.impact}", fontSize = 10.sp)
                            Text("Rekomendasi Agent: ${item.recommendation}", fontSize = 10.sp, color = GmuGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                notice?.let { Text(it, fontSize = 10.sp, color = GmuGreen, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                enabled = selected != null && !submitting,
                onClick = {
                    val item = selected ?: return@Button
                    submitting = true
                    val notes = buildDirectorEscalationNote(item)
                    vm.insert(
                        "approvals",
                        mapOf(
                            "booking_id" to item.booking.id,
                            "approval_type" to "Director Escalation",
                            "reference_id" to "AI_ESCALATION:${item.booking.id}",
                            "status" to "Pending",
                            "requested_by" to session.userId,
                            "notes" to notes,
                            "sla_status" to "Escalated",
                            "sla_escalation_level" to 1
                        ),
                        "Eskalasi berhasil dikirim ke Direktur."
                    ) { ok, msg ->
                        submitting = false
                        notice = msg
                        if (ok) vm.loadAll()
                    }
                }
            ) { Text(if (submitting) "Mengirim…" else "Escalate to Director") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun BoxScope.DirectorHandoffDock(vm: MainViewModel, session: SessionState) {
    val pending = vm.table("approvals").filter {
        it.text("status") == "Pending" && it.text("approval_type") == "Director Escalation"
    }
    if (pending.isEmpty()) return
    var open by remember { mutableStateOf(false) }

    Surface(
        onClick = { open = true },
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 82.dp, end = 14.dp),
        shape = RoundedCornerShape(18.dp),
        color = GmuDark,
        shadowElevation = 8.dp
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = GmuGold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("${pending.size} Director Handoff", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
    }

    if (open) DirectorHandoffDialog(vm, session, pending, onDismiss = { open = false })
}

@Composable
private fun DirectorHandoffDialog(
    vm: MainViewModel,
    session: SessionState,
    pending: List<ErpRow>,
    onDismiss: () -> Unit
) {
    var busyId by remember { mutableStateOf<String?>(null) }
    var returnNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Director Handoff Center") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Ringkasan eskalasi dari Manager EduTrans. Direktur dapat memutuskan tanpa membuka seluruh modul operasional.", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(10.dp))
                pending.forEach { row ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(bookingLabel(vm, row.text("booking_id")), fontWeight = FontWeight.Black, color = GmuDark, modifier = Modifier.weight(1f))
                                StatusChip("Director")
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(row.text("notes"), fontSize = 10.sp, color = Color.DarkGray)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = returnNote,
                                onValueChange = { returnNote = it },
                                label = { Text("Catatan keputusan / return") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 1,
                                maxLines = 3
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    enabled = busyId == null,
                                    onClick = {
                                        busyId = row.id
                                        vm.update(
                                            "approvals",
                                            row.id,
                                            mapOf(
                                                "status" to "Returned",
                                                "approved_by" to session.userId,
                                                "notes" to (row.text("notes") + "\n\nRETURN DIRECTOR: " + returnNote.ifBlank { "Perlu revisi / kelengkapan sebelum diputuskan." }),
                                                "sla_status" to "Returned"
                                            ),
                                            "Dikembalikan ke Manager."
                                        ) { _, _ -> busyId = null; vm.loadAll() }
                                    }
                                ) { Text("Return") }
                                TextButton(
                                    enabled = busyId == null,
                                    onClick = {
                                        busyId = row.id
                                        vm.approve(row.id, false, "Rejected by Director. ${returnNote}".trim()) { _, _ -> busyId = null; vm.loadAll() }
                                    }
                                ) { Text("Reject", color = GmuDanger) }
                                Button(
                                    enabled = busyId == null,
                                    onClick = {
                                        busyId = row.id
                                        vm.approve(row.id, true, "Approved by Director. ${returnNote}".trim()) { _, _ -> busyId = null; vm.loadAll() }
                                    }
                                ) { Text("Approve") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Selesai") } }
    )
}

private data class DirectorEscalationCandidate(
    val booking: Booking,
    val reason: String,
    val impact: String,
    val recommendation: String,
    val amountIdr: Double = 0.0
)

private fun buildEscalationCandidates(vm: MainViewModel): List<DirectorEscalationCandidate> {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    ) ?: Date()
    val approvals = vm.table("approvals")
    val costs = vm.table("trip_costs")
    val vendorPos = vm.table("vendor_pos")
    val assignments = vm.table("staff_assignments")
    val rundown = vm.table("rundown_items")
    val operationSheets = vm.table("operation_sheets")
    val manifests = vm.table("manifests")
    val docs = vm.table("documents")

    return vm.bookings
        .filter { it.status !in listOf("Lead", "Quotation", "Completed", "Closed", "Cancelled") }
        .mapNotNull { b ->
            val tripDate = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(b.tripDate) }.getOrNull() ?: return@mapNotNull null
            val days = ((tripDate.time - today.time).toDouble() / 86_400_000.0).roundToInt()
            val hasRundown = rundown.any { it.text("booking_id") == b.id }
            val hasOps = operationSheets.any { it.text("booking_id") == b.id }
            val hasManifest = manifests.any { it.text("booking_id") == b.id }
            val hasCrew = assignments.any { it.text("booking_id") == b.id || it.text("title").contains(b.bookingNo, true) }
            val hasVendor = vendorPos.any { it.text("booking_id") == b.id && it.text("status") !in listOf("Rejected", "Cancelled") }
            val hasDocs = docs.count { it.text("booking_id") == b.id } >= 3
            val pendingApproval = approvals.any { it.text("booking_id") == b.id && it.text("status") == "Pending" }
            val rabTotal = costs.filter { it.text("booking_id") == b.id }.sumOf { it.number("rab_amount") }
            val missingCount = listOf(hasRundown, hasOps, hasManifest, hasCrew, hasVendor, hasDocs).count { !it }

            when {
                rabTotal > ManagerEduTransPolicy.approvalLimitIdr -> DirectorEscalationCandidate(
                    b,
                    "RAB operasional melewati limit Manager",
                    "Nilai RAB ${rupiah(rabTotal)} melebihi limit ${rupiah(ManagerEduTransPolicy.approvalLimitIdr.toDouble())}.",
                    "Direktur menilai kebutuhan biaya dan memberi keputusan sebelum komitmen vendor.",
                    rabTotal
                )
                days <= 1 && missingCount >= 2 -> DirectorEscalationCandidate(
                    b,
                    "Trip mendesak belum siap",
                    "${if (days <= 0) "Trip hari ini" else "Trip besok"} masih memiliki $missingCount komponen kritis yang belum lengkap.",
                    "Direktur tentukan prioritas, mitigasi, atau keputusan go/no-go bersama Manager."
                )
                days <= 1 && pendingApproval -> DirectorEscalationCandidate(
                    b,
                    "Keputusan masih tertahan menjelang keberangkatan",
                    "Ada approval pending untuk trip ${if (days <= 0) "hari ini" else "besok"}.",
                    "Direktur review approval dan putuskan segera agar operasional tidak terhambat."
                )
                else -> null
            }
        }
        .sortedBy { it.booking.tripDate }
}

private fun buildDirectorEscalationNote(item: DirectorEscalationCandidate): String = buildString {
    append("AI DIRECTOR HANDOFF\n")
    append("Booking: ${item.booking.bookingNo}\n")
    append("Customer: ${item.booking.customerName}\n")
    append("Program: ${item.booking.programName}\n")
    append("Trip: ${item.booking.tripDate} • ${item.booking.pax} pax\n")
    append("Masalah: ${item.reason}\n")
    append("Dampak: ${item.impact}\n")
    append("Rekomendasi: ${item.recommendation}")
    if (item.amountIdr > 0) append("\nNilai keputusan: ${rupiah(item.amountIdr)}")
}
