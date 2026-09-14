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
        "Invoice",
        "Planned Expense",
        "Unplanned Expense",
        "Emergency Expense",
        "Discount",
        "Margin Review"
    )

    private val strategicKeywords = listOf(
        "director escalation",
        "trip closing",
        "refund",
        "cancellation",
        "pembatalan",
        "journal",
        "pricing master",
        "master price",
        "ubah harga",
        "rekening",
        "bank credential",
        "reserve",
        "cadangan kas",
        "capex",
        "investasi",
        "hiring permanent",
        "pegawai tetap",
        "legal",
        "fraud"
    )

    fun canRequest(role: String): Boolean =
        role == ErpRoles.OWNER ||
            ErpRoles.isDirector(role) ||
            ErpRoles.isManagerEduTrans(role) ||
            role == "Operation" ||
            role == "Admin" ||
            role == "Finance" ||
            role == "Sales"

    fun amountIdr(row: ErpRow): Long? {
        val keys = listOf("amount", "amount_idr", "requested_amount", "rab_amount")
        val structured = keys.asSequence()
            .mapNotNull { key -> row.data[key]?.toDoubleOrNull()?.toLong() }
            .firstOrNull { it > 0L }
        if (structured != null) return structured

        val notes = row.text("notes")
        val match = Regex("(?i)(?:nilai|amount|rp)\\s*[:=]?\\s*(?:rp\\s*)?([0-9][0-9.,]*)").find(notes)
            ?: Regex("(?i)rp\\s*([0-9][0-9.,]*)").find(notes)
        return match?.groupValues?.getOrNull(1)
            ?.replace(".", "")
            ?.replace(",", "")
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    }

    private fun percentFrom(row: ErpRow, labels: List<String>): Double? {
        val text = (row.text("approval_type") + " " + row.text("notes")).lowercase()
        for (label in labels) {
            val regex = Regex("(?i)$label\\s*[:=]?\\s*([0-9]+(?:[.,][0-9]+)?)\\s*%?")
            val value = regex.find(text)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun isUnplanned(row: ErpRow): Boolean {
        val text = (row.text("approval_type") + " " + row.text("notes")).lowercase()
        return listOf("unplanned", "di luar rab", "luar rab", "tidak ada di rab").any(text::contains)
    }

    private fun isEmergency(row: ErpRow): Boolean {
        val text = (row.text("approval_type") + " " + row.text("notes")).lowercase()
        return listOf("emergency", "darurat").any(text::contains)
    }

    fun requiresDirector(row: ErpRow): Boolean {
        val text = (row.text("approval_type") + " " + row.text("notes")).lowercase()
        if (strategicKeywords.any(text::contains)) return true

        val margin = percentFrom(row, listOf("margin"))
        if (margin != null && margin < ManagerEduTransPolicy.criticalMarginPct) return true

        val discount = percentFrom(row, listOf("discount", "diskon"))
        if (discount != null && ManagerEduTransPolicy.requiresDirectorForDiscount(discount)) return true

        val amount = amountIdr(row) ?: return false
        return when {
            isEmergency(row) -> ManagerEduTransPolicy.requiresDirectorForEmergency(amount)
            isUnplanned(row) -> ManagerEduTransPolicy.requiresDirectorForUnplanned(amount)
            else -> ManagerEduTransPolicy.requiresDirectorApproval(amount)
        }
    }

    fun canApprove(role: String, row: ErpRow): Boolean {
        if (role == ErpRoles.OWNER || ErpRoles.isDirector(role)) return true
        if (!ErpRoles.isManagerEduTrans(role) || requiresDirector(row)) return false
        return row.text("approval_type") in managerOperationalTypes
    }

    fun canReturn(role: String, row: ErpRow): Boolean = when {
        role == ErpRoles.OWNER || ErpRoles.isDirector(role) -> true
        ErpRoles.isManagerEduTrans(role) -> !requiresDirector(row)
        else -> false
    }

    fun canResubmit(role: String, row: ErpRow, userId: String): Boolean =
        row.text("requested_by") == userId ||
            role == ErpRoles.OWNER ||
            ErpRoles.isDirector(role) ||
            ErpRoles.isManagerEduTrans(role)

    fun authorityLabel(role: String): String = when {
        role == ErpRoles.OWNER -> "Kewenangan penuh Owner"
        ErpRoles.isDirector(role) -> "Persetujuan strategis dan operasional"
        ErpRoles.isManagerEduTrans(role) -> "RAB ≤ Rp1 jt • luar RAB ≤ Rp250 rb • darurat ≤ Rp500 rb • diskon ≤5%"
        role in setOf("Operation", "Admin", "Finance", "Sales") -> "Dapat mengajukan persetujuan sesuai tugas"
        else -> "Akses baca alur persetujuan"
    }

    fun authorityReason(row: ErpRow): String {
        if (!requiresDirector(row)) return "Masuk kewenangan operasional Manager"
        val text = (row.text("approval_type") + " " + row.text("notes")).lowercase()
        val margin = percentFrom(row, listOf("margin"))
        if (margin != null && margin < ManagerEduTransPolicy.criticalMarginPct) return "Margin di bawah 20%"
        val discount = percentFrom(row, listOf("discount", "diskon"))
        if (discount != null && discount > ManagerEduTransPolicy.maxDiscountPct) return "Diskon di atas 5%"
        val amount = amountIdr(row)
        if (amount != null && isEmergency(row) && amount > ManagerEduTransPolicy.emergencyTripLimitIdr) return "Biaya darurat di atas Rp500.000"
        if (amount != null && isUnplanned(row) && amount > ManagerEduTransPolicy.unplannedLimitIdr) return "Biaya di luar RAB di atas Rp250.000"
        if (amount != null && amount > ManagerEduTransPolicy.plannedRabLimitIdr) return "Biaya dalam RAB di atas Rp1.000.000"
        if (strategicKeywords.any(text::contains)) return "Keputusan strategis / sensitif"
        return "Memerlukan kewenangan Direktur"
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
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Pusat Persetujuan", fontWeight = FontWeight.Black, fontSize = 19.sp, color = GmuDark)
                        Text(ApprovalAuthorityMatrix.authorityLabel(session.profile.role), fontSize = 11.sp, color = Color.Gray)
                    }
                    if (ApprovalAuthorityMatrix.canRequest(session.profile.role)) {
                        Button(onClick = { showRequest = true }, shape = RoundedCornerShape(14.dp)) {
                            Text("+ Ajukan")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Manager: biaya dalam RAB sampai Rp1 juta, di luar RAB sampai Rp250 ribu, darurat trip sampai Rp500 ribu, dan diskon sampai 5%. Margin di bawah 20% serta keputusan strategis wajib Direktur.",
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
                    label = { Text(approvalStatusLabel(value), fontSize = 10.sp) }
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
                            "Tidak ada persetujuan dengan status ${approvalStatusLabel(filter).lowercase()}.",
                            Modifier.fillMaxWidth().padding(18.dp),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            items(rows, key = { it.id }) { row ->
                ApprovalAuthorityCard(vm, session, row) { notice = it }
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
                    "Pengajuan persetujuan berhasil dibuat."
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
    val canReturn = status == "Pending" && ApprovalAuthorityMatrix.canReturn(session.profile.role, row)
    val canResubmit = status == "Returned" && ApprovalAuthorityMatrix.canResubmit(session.profile.role, row, session.userId)

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(approvalTypeLabel(type), fontWeight = FontWeight.Black, color = GmuDark)
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
                    AuthorityPill("Persetujuan Direktur", Color(0xFFFFF2D9), GmuWarn)
                } else if (ErpRoles.isManagerEduTrans(session.profile.role)) {
                    AuthorityPill("Kewenangan Manager", Color(0xFFEAF7EF), GmuGreen)
                }
                amount?.let { AuthorityPill(formatApprovalIdr(it), Color(0xFFF1F3F5), GmuDark) }
            }
            if (needsDirector) {
                Spacer(Modifier.height(5.dp))
                Text(ApprovalAuthorityMatrix.authorityReason(row), fontSize = 10.sp, color = GmuWarn, fontWeight = FontWeight.SemiBold)
            }

            if (canApprove || canReturn) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (canReturn) {
                        TextButton(onClick = {
                            val appended = buildString {
                                append(row.text("notes"))
                                if (isNotEmpty()) append("\n\n")
                                append("DIKEMBALIKAN: Mohon revisi / lengkapi sebelum diputuskan.")
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
                                "Pengajuan dikembalikan untuk revisi."
                            ) { _, msg -> onNotice(msg) }
                        }) { Text("Kembalikan", color = GmuWarn) }
                    }
                    if (canApprove) {
                        TextButton(onClick = {
                            vm.approve(row.id, false, "Ditolak melalui Matriks Kewenangan") { _, msg -> onNotice(msg) }
                        }) { Text("Tolak", color = GmuDanger) }
                        Button(onClick = {
                            vm.approve(row.id, true, "Disetujui melalui Matriks Kewenangan") { _, msg -> onNotice(msg) }
                        }) { Text("Setujui") }
                    }
                }
            } else if (canResubmit) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val appended = buildString {
                            append(row.text("notes"))
                            if (isNotEmpty()) append("\n\n")
                            append("KIRIM ULANG: Revisi telah dilengkapi.")
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
                            "Pengajuan berhasil dikirim ulang."
                        ) { _, msg -> onNotice(msg) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Revisi Selesai · Kirim Ulang") }
            } else if (status == "Pending") {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (needsDirector) "Menunggu keputusan Direktur/Owner." else "Menunggu pihak yang berwenang.",
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
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val types = listOf(
        "Operation Sheet" to "Lembar Operasional",
        "PO Vendor" to "Pesanan Vendor",
        "Invoice" to "Tagihan",
        "Planned Expense" to "Biaya dalam RAB",
        "Unplanned Expense" to "Biaya di luar RAB",
        "Emergency Expense" to "Biaya Darurat Trip",
        "Discount" to "Diskon",
        "Margin Review" to "Tinjauan Margin",
        "Trip Closing" to "Penutupan Kegiatan"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajukan Persetujuan") },
        text = {
            Column {
                Box {
                    OutlinedButton(onClick = { bookingMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(booking?.let { it.bookingNo + " · " + it.programName } ?: "Pilih Pemesanan")
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
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(approvalTypeLabel(type)) }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        types.forEach { item ->
                            DropdownMenuItem(text = { Text(item.second) }, onClick = { type = item.first; typeMenu = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit) },
                    label = { Text("Nilai rupiah (jika ada)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Alasan / catatan / diskon% / margin%") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && booking != null,
                onClick = {
                    booking?.let {
                        val normalized = buildString {
                            if (amount.isNotBlank()) append("NILAI: Rp$amount")
                            if (amount.isNotBlank() && notes.isNotBlank()) append("\n")
                            append(notes.trim())
                        }
                        onSubmit(it.id, type, normalized)
                    }
                }
            ) { Text("Ajukan") }
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
        Text(approvalStatusLabel(status), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun AuthorityPill(label: String, background: Color, foreground: Color) {
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = foreground)
    }
}

private fun approvalStatusLabel(status: String): String = when (status) {
    "Pending" -> "Menunggu"
    "Returned" -> "Dikembalikan"
    "Approved" -> "Disetujui"
    "Rejected" -> "Ditolak"
    "All" -> "Semua"
    else -> status.ifBlank { "Menunggu" }
}

private fun approvalTypeLabel(type: String): String = when (type) {
    "Operation Sheet" -> "Lembar Operasional"
    "PO Vendor" -> "Pesanan Vendor"
    "Invoice" -> "Tagihan"
    "Planned Expense" -> "Biaya dalam RAB"
    "Unplanned Expense" -> "Biaya di luar RAB"
    "Emergency Expense" -> "Biaya Darurat Trip"
    "Discount" -> "Diskon"
    "Margin Review" -> "Tinjauan Margin"
    "Trip Closing" -> "Penutupan Kegiatan"
    else -> type.ifBlank { "Persetujuan" }
}

private fun approvalBookingLabel(vm: MainViewModel, bookingId: String): String {
    val booking = vm.bookings.firstOrNull { it.id == bookingId }
    return booking?.let { it.bookingNo + " · " + it.customerName } ?: bookingId.ifBlank { "Tanpa pemesanan" }
}

private fun formatApprovalIdr(value: Long): String =
    "Rp" + String.format("%,d", value).replace(',', '.')
