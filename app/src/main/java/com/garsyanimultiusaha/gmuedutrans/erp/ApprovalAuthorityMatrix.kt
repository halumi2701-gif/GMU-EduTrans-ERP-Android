package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ApprovalAuthorityMatrix {
    private val managerOperationalTypes = setOf(
        "Operation Sheet",
        "PO Vendor",
        "Invoice"
    )

    private val strategicKeywords = listOf(
        "director escalation",
        "trip closing",
        "refund",
        "journal",
        "pricing",
        "rekening",
        "bank",
        "finance"
    )

    fun canRequest(role: String): Boolean =
        role == ErpRoles.OWNER ||
            ErpRoles.isDirector(role) ||
            ErpRoles.isManagerEduTrans(role) ||
            role == "Operation" ||
            role == "Admin"

    fun amountIdr(row: ErpRow): Long? {
        val keys = listOf("amount", "amount_idr", "requested_amount", "rab_amount")
        return keys.asSequence()
            .mapNotNull { key -> row.data[key]?.toDoubleOrNull()?.toLong() }
            .firstOrNull { it > 0L }
    }

    fun requiresDirector(row: ErpRow): Boolean {
        val type = row.text("approval_type").lowercase()
        val amount = amountIdr(row)
        return strategicKeywords.any(type::contains) ||
            (amount != null && ManagerEduTransPolicy.requiresDirectorApproval(amount))
    }

    fun canApprove(role: String, row: ErpRow): Boolean {
        if (role == ErpRoles.OWNER || ErpRoles.isDirector(role)) return true
        if (!ErpRoles.isManagerEduTrans(role)) return false
        if (requiresDirector(row)) return false
        return row.text("approval_type") in managerOperationalTypes
    }

    fun canReturn(role: String): Boolean =
        role == ErpRoles.OWNER || ErpRoles.isDirector(role)

    fun canResubmit(role: String, row: ErpRow, userId: String): Boolean =
        row.text("requested_by") == userId ||
            role == ErpRoles.OWNER ||
            ErpRoles.isDirector(role)

    fun authorityLabel(role: String): String = when {
        role == ErpRoles.OWNER -> "Full approval authority"
        ErpRoles.isDirector(role) -> "Strategic + operational approval authority"
        ErpRoles.isManagerEduTrans(role) -> "Operational authority up to Rp2.000.000"
        role == "Operation" || role == "Admin" -> "Request authority only"
        else -> "Read-only workflow access"
    }
}

@Composable
fun GmuNativeAppWithApprovalAuthority(vm: MainViewModel) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithRecipientPolicyGate(vm)
        val state = vm.state
        if (state is AppState.LoggedIn && vm.currentPage == AppPage.WORKFLOW) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F8FA)) {
                ApprovalWorkflowV2(vm, state.session)
            }
        }
    }
}

@Composable
private fun ApprovalWorkflowV2(vm: MainViewModel, session: SessionState) {
    var filter by remember { mutableStateOf("Pending") }
    var showRequest by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val rows = vm.table("approvals")
        .filter { filter == "All" || it.text("status") == filter }
        .sortedByDescending { it.text("requested_at") }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Workflow & Approval v2", fontWeight = FontWeight.Black, fontSize = 19.sp, color = GmuDark)
                        Text(
                            ApprovalAuthorityMatrix.authorityLabel(session.profile.role),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    if (ApprovalAuthorityMatrix.canRequest(session.profile.role)) {
                        Button(onClick = { showRequest = true }, shape = RoundedCornerShape(14.dp)) {
                            Text("+ Request")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Owner/Direktur dapat memutuskan seluruh approval. Manager EduTrans hanya approval operasional dalam kewenangan; keputusan strategis atau di atas Rp2 juta diarahkan ke Direktur.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        notice?.let {
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7EF))) {
                Text(it, Modifier.padding(12.dp), fontSize = 11.sp, color = GmuGreen)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Pending", "Returned", "Approved", "Rejected", "All").forEach { value ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value },
                    label = { Text(value, fontSize = 10.sp) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (rows.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Text(
                            "Tidak ada approval ${filter.lowercase()}.",
                            Modifier.fillMaxWidth().padding(18.dp),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            items(rows, key = { it.id }) { row ->
                ApprovalAuthorityCard(
                    vm = vm,
                    session = session,
                    row = row,
                    onNotice = { notice = it }
                )
            }
        }
    }

    if (showRequest) {
        ApprovalAuthorityRequestDialog(
            bookings = vm.bookings,
            busy = vm.actionBusy,
            onDismiss = { if (!vm.actionBusy) showRequest = false },
            onSubmit = { bookingId, type, notes ->
                vm.insert(
                    "approvals",
                    mapOf(
                        "booking_id" to bookingId,
                        "approval_type" to type,
                        "status" to "Pending",
                        "requested_by" to session.userId,
                        "notes" to notes.ifBlank { null },
                        "sla_status" to "Pending"
                    ),
                    "Approval request berhasil dibuat."
                ) { ok, msg ->
                    notice = msg
                    if (ok) showRequest = false
                }
            }
        )
    }
}

@Composable
private fun ApprovalAuthorityCard(
    vm: MainViewModel,
    session: SessionState,
    row: ErpRow,
    onNotice: (String) -> Unit
) {
    val status = row.text("status")
    val type = row.text("approval_type")
    val amount = ApprovalAuthorityMatrix.amountIdr(row)
    val needsDirector = ApprovalAuthorityMatrix.requiresDirector(row)
    val canApprove = status == "Pending" && ApprovalAuthorityMatrix.canApprove(session.profile.role, row)
    val canReturn = status == "Pending" && ApprovalAuthorityMatrix.canReturn(session.profile.role)
    val canResubmit = status == "Returned" && ApprovalAuthorityMatrix.canResubmit(session.profile.role, row, session.userId)

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(type.ifBlank { "Approval" }, fontWeight = FontWeight.Black, color = GmuDark)
                    Text(approvalBookingLabel(vm, row.text("booking_id")), fontSize = 11.sp, color = Color.Gray)
                }
                ApprovalStatusPill(status)
            }

            if (row.text("notes").isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(row.text("notes"), fontSize = 11.sp)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (needsDirector) {
                    AuthorityPill("Director authority", Color(0xFFFFF2D9), GmuWarn)
                } else if (ErpRoles.isManagerEduTrans(session.profile.role) && type in setOf("Operation Sheet", "PO Vendor", "Invoice")) {
                    AuthorityPill("Manager scope", Color(0xFFEAF7EF), GmuGreen)
                }
                amount?.let { AuthorityPill(formatApprovalIdr(it), Color(0xFFF1F3F5), GmuDark) }
            }

            if (canApprove || canReturn) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (canReturn) {
                        TextButton(onClick = {
                            val appended = buildString {
                                append(row.text("notes"))
                                if (isNotEmpty()) append("\n\n")
                                append("RETURN AUTHORITY MATRIX: Mohon revisi / lengkapi sebelum diputuskan.")
                            }
                            vm.update(
                                "approvals",
                                row.id,
                                mapOf(
                                    "status" to "Returned",
                                    "approved_by" to session.userId,
                                    "notes" to appended,
                                    "sla_status" to "Returned"
                                ),
                                "Approval dikembalikan untuk revisi."
                            ) { _, msg -> onNotice(msg) }
                        }) { Text("Return", color = GmuWarn) }
                    }
                    if (canApprove) {
                        TextButton(onClick = {
                            vm.approve(row.id, false, "Rejected via Authority Matrix v2") { _, msg -> onNotice(msg) }
                        }) { Text("Reject", color = GmuDanger) }
                        Button(onClick = {
                            vm.approve(row.id, true, "Approved via Authority Matrix v2") { _, msg -> onNotice(msg) }
                        }) { Text("Approve") }
                    }
                }
            } else if (canResubmit) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val appended = buildString {
                            append(row.text("notes"))
                            if (isNotEmpty()) append("\n\n")
                            append("RESUBMIT: Revisi telah dilengkapi dan dikirim ulang.")
                        }
                        vm.update(
                            "approvals",
                            row.id,
                            mapOf(
                                "status" to "Pending",
                                "approved_by" to null,
                                "approved_at" to null,
                                "notes" to appended,
                                "sla_status" to "Pending",
                                "sla_resolved_at" to null
                            ),
                            "Approval berhasil dikirim ulang."
                        ) { _, msg -> onNotice(msg) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Revisi Selesai · Resubmit") }
            } else if (status == "Pending") {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (needsDirector) "Menunggu keputusan Direktur/Owner." else "Menunggu approver yang berwenang.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun ApprovalAuthorityRequestDialog(
    bookings: List<Booking>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    var booking by remember { mutableStateOf(bookings.firstOrNull()) }
    var bookingMenu by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("Operation Sheet") }
    var typeMenu by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    val types = listOf("Operation Sheet", "PO Vendor", "Invoice", "Trip Closing")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request Approval") },
        text = {
            Column {
                Box {
                    OutlinedButton(onClick = { bookingMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(booking?.let { it.bookingNo + " · " + it.programName } ?: "Pilih Booking")
                    }
                    DropdownMenu(expanded = bookingMenu, onDismissRequest = { bookingMenu = false }) {
                        bookings.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.bookingNo + " · " + item.programName) },
                                onClick = { booking = item; bookingMenu = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(type) }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        types.forEach { item ->
                            DropdownMenuItem(text = { Text(item) }, onClick = { type = item; typeMenu = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan / nilai keputusan") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && booking != null,
                onClick = { booking?.let { onSubmit(it.id, type, notes) } }
            ) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun ApprovalStatusPill(status: String) {
    val bg = when (status) {
        "Approved" -> Color(0xFFEAF7EF)
        "Rejected" -> Color(0xFFFFE9E9)
        "Returned" -> Color(0xFFFFF2D9)
        else -> Color(0xFFF1F3F5)
    }
    val fg = when (status) {
        "Approved" -> GmuGreen
        "Rejected" -> GmuDanger
        "Returned" -> GmuWarn
        else -> GmuDark
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(status.ifBlank { "Pending" }, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun AuthorityPill(label: String, background: Color, foreground: Color) {
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = foreground)
    }
}

private fun approvalBookingLabel(vm: MainViewModel, bookingId: String): String {
    val booking = vm.bookings.firstOrNull { it.id == bookingId }
    return booking?.let { it.bookingNo + " · " + it.customerName } ?: bookingId.ifBlank { "Tanpa booking" }
}

private fun formatApprovalIdr(value: Long): String =
    "Rp" + String.format("%,d", value).replace(',', '.')
