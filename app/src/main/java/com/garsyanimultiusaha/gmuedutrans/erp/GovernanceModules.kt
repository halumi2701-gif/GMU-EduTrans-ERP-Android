package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
fun WorkflowScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    var filter by remember { mutableStateOf("Pending") }
    var add by remember { mutableStateOf(false) }
    val approvals = vm.table("approvals").filter { filter == "All" || it.text("status") == filter }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("Workflow & Approval", "Review approval sesuai kewenangan backend")
            if (session.profile.role in listOf("Owner", "Manager", "Finance", "Operation", "Admin")) {
                TextButton(onClick = { add = true }) { Text("+ Request") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Pending", "Approved", "Rejected", "All").forEach { s ->
                FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text(s) })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (approvals.isEmpty()) item { EmptyCard("Tidak ada approval " + filter.lowercase() + ".") }
            items(approvals, key = { it.id }) { a ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(a.text("approval_type"), fontWeight = FontWeight.Black, color = GmuDark)
                            StatusChip(a.text("status"))
                        }
                        Text(bookingLabel(vm, a.text("booking_id")), fontSize = 11.sp, color = Color.Gray)
                        if (a.text("notes").isNotBlank()) Text(a.text("notes"), fontSize = 11.sp)
                        val canApprove = when (session.profile.role) {
                            "Owner" -> true
                            "Manager" -> a.text("approval_type") in listOf("Operation Sheet", "PO Vendor")
                            "Finance" -> a.text("approval_type") == "Invoice"
                            else -> false
                        }
                        if (a.text("status") == "Pending" && canApprove) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    vm.approve(a.id, false, "Rejected from Android RC") { _, msg -> onNotice(msg) }
                                }) { Text("Reject", color = GmuDanger) }
                                Button(onClick = {
                                    vm.approve(a.id, true, "Approved from Android RC") { _, msg -> onNotice(msg) }
                                }) { Text("Approve") }
                            }
                        } else if (a.text("status") == "Pending") {
                            Text(
                                "Menunggu approver yang berwenang.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (add) {
        ApprovalRequestDialog(
            bookings = vm.bookings,
            busy = vm.actionBusy,
            onDismiss = { add = false },
            onSave = { bookingId, type, notes ->
                vm.insert(
                    "approvals",
                    mapOf(
                        "booking_id" to bookingId,
                        "approval_type" to type,
                        "status" to "Pending",
                        "requested_by" to session.userId,
                        "notes" to notes.ifBlank { null }
                    ),
                    "Approval request berhasil dibuat."
                ) { ok, msg ->
                    onNotice(msg)
                    if (ok) add = false
                }
            }
        )
    }
}

@Composable
private fun ApprovalRequestDialog(
    bookings: List<Booking>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var booking by remember { mutableStateOf(bookings.firstOrNull()) }
    var menu by remember { mutableStateOf(false) }
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
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(booking?.bookingNo ?: "Pilih Booking")
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        bookings.forEach { b ->
                            DropdownMenuItem(text = { Text(b.bookingNo + " • " + b.programName) }, onClick = { booking = b; menu = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(type) }
                    DropdownMenu(typeMenu, onDismissRequest = { typeMenu = false }) {
                        types.forEach { x -> DropdownMenuItem(text = { Text(x) }, onClick = { type = x; typeMenu = false }) }
                    }
                }
                GmuField(notes, { notes = it }, "Catatan")
            }
        },
        confirmButton = {
            Button(onClick = { onSave(booking!!.id, type, notes) }, enabled = !busy && booking != null) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun SopScreen(vm: MainViewModel) {
    val rows = vm.table("sop_deadlines").sortedByDescending { it.int("offset_days") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        SectionTitle("SOP Deadline", "Timeline tanggung jawab administrasi & operasional")
        Spacer(Modifier.height(10.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rows.isEmpty()) item { EmptyCard("Konfigurasi SOP belum tersedia.") }
            items(rows, key = { it.id }) { r ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (r.int("offset_days") == 0) "H-Day" else if (r.int("offset_days") < 0) "H" + r.int("offset_days") else "H+" + r.int("offset_days"),
                                color = GmuGold,
                                fontWeight = FontWeight.Black
                            )
                            Text(r.text("task_name"), fontWeight = FontWeight.Bold, color = GmuDark)
                            Text("PIC: " + r.text("responsible_role") + " • " + r.text("code"), fontSize = 11.sp, color = Color.Gray)
                        }
                        StatusChip(if (r.text("is_active") == "false") "Inactive" else "Active")
                    }
                }
            }
        }
    }
}

@Composable
fun ReportsScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    var tab by remember { mutableStateOf("Trip Report") }
    var add by remember { mutableStateOf(false) }
    val reports = vm.table("trip_reports")
    val evals = vm.table("evaluations")

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("Report & Evaluation", "Laporan trip dan evaluasi Customer/Vendor/TL")
            TextButton(onClick = { add = true }) { Text("+ Tambah") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == "Trip Report", onClick = { tab = "Trip Report" }, label = { Text("Trip Report") })
            FilterChip(selected = tab == "Evaluation", onClick = { tab = "Evaluation" }, label = { Text("Evaluation") })
        }
        Spacer(Modifier.height(8.dp))
        if (tab == "Trip Report") {
            LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (reports.isEmpty()) item { EmptyCard("Belum ada laporan trip.") }
                items(reports, key = { it.id }) { r ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Text(bookingLabel(vm, r.text("booking_id")), fontWeight = FontWeight.Black, color = GmuDark)
                            Text(r.text("summary"), fontSize = 12.sp)
                            if (r.text("incidents").isNotBlank()) Text("Incidents: " + r.text("incidents"), fontSize = 11.sp, color = GmuDanger)
                            if (r.text("achievements").isNotBlank()) Text("Achievements: " + r.text("achievements"), fontSize = 11.sp, color = GmuGreen)
                        }
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (evals.isEmpty()) item { EmptyCard("Belum ada evaluasi.") }
                items(evals, key = { it.id }) { e ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(e.text("evaluation_type"), fontWeight = FontWeight.Black, color = GmuDark)
                                Text("★ " + e.text("score") + "/5", color = GmuGold, fontWeight = FontWeight.Bold)
                            }
                            Text(bookingLabel(vm, e.text("booking_id")), fontSize = 11.sp, color = Color.Gray)
                            Text(e.text("feedback"), fontSize = 12.sp)
                            if (e.text("corrective_action").isNotBlank()) Text("Action: " + e.text("corrective_action"), fontSize = 11.sp, color = GmuWarn)
                        }
                    }
                }
            }
        }
    }

    if (add) {
        ReportEvaluationDialog(
            mode = tab,
            bookings = vm.bookings,
            busy = vm.actionBusy,
            onDismiss = { add = false },
            onSave = { table, values, msg ->
                val withUser = values + ("created_by" to session.userId)
                vm.insert(table, withUser, msg) { ok, message ->
                    onNotice(message)
                    if (ok) add = false
                }
            }
        )
    }
}

@Composable
private fun ReportEvaluationDialog(
    mode: String,
    bookings: List<Booking>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, Any?>, String) -> Unit
) {
    var booking by remember { mutableStateOf(bookings.firstOrNull()) }
    var menu by remember { mutableStateOf(false) }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah " + mode) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(booking?.bookingNo ?: "Pilih Booking") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        bookings.forEach { x -> DropdownMenuItem(text = { Text(x.bookingNo) }, onClick = { booking = x; menu = false }) }
                    }
                }
                if (mode == "Trip Report") {
                    GmuField(a, { a = it }, "Summary *")
                    GmuField(b, { b = it }, "Incidents")
                    GmuField(c, { c = it }, "Achievements")
                    GmuField(d, { d = it }, "Follow-up")
                } else {
                    Spacer(Modifier.height(8.dp))
                    GmuSelect(
                        value = a.ifBlank { "Customer" },
                        label = "Evaluation Type",
                        options = listOf("Customer", "Vendor", "TL"),
                        onSelect = { a = it }
                    )
                    GmuField(b, { b = it.filter(Char::isDigit) }, "Score 1-5 *")
                    GmuField(c, { c = it }, "Feedback *")
                    GmuField(d, { d = it }, "Corrective Action")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && booking != null && a.isNotBlank(),
                onClick = {
                    if (mode == "Trip Report") {
                        onSave(
                            "trip_reports",
                            mapOf(
                                "booking_id" to booking!!.id,
                                "summary" to a,
                                "incidents" to b.ifBlank { null },
                                "achievements" to c.ifBlank { null },
                                "follow_up" to d.ifBlank { null }
                            ),
                            "Trip Report berhasil disimpan."
                        )
                    } else {
                        onSave(
                            "evaluations",
                            mapOf(
                                "booking_id" to booking!!.id,
                                "evaluation_type" to a,
                                "score" to (b.toIntOrNull() ?: 5).coerceIn(1, 5),
                                "feedback" to c,
                                "corrective_action" to d.ifBlank { null }
                            ),
                            "Evaluation berhasil disimpan."
                        )
                    }
                }
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun ClosingScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    val closedIds = vm.table("trip_closings").map { it.text("booking_id") }.toSet()
    val candidates = vm.bookings.filter { it.status in listOf("Completed", "Closed") || it.id in closedIds }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        SectionTitle("Trip Closing", "Final profitability & checklist closing")
        Spacer(Modifier.height(10.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (candidates.isEmpty()) item { EmptyCard("Belum ada trip siap closing.") }
            items(candidates, key = { it.id }) { b ->
                val actual = vm.actualCostForBooking(b.id)
                val profit = b.omzet - actual
                val margin = if (b.omzet > 0) profit / b.omzet * 100 else 0.0
                val isClosed = b.id in closedIds || b.status == "Closed"
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(b.bookingNo, fontWeight = FontWeight.Black, color = GmuDark)
                            StatusChip(if (isClosed) "Closed" else "Ready")
                        }
                        Text(b.programName + " • " + b.customerName, fontSize = 12.sp)
                        Spacer(Modifier.height(7.dp))
                        Text("Omzet " + rupiah(b.omzet) + " • Aktual " + rupiah(actual), fontSize = 11.sp)
                        Text("Net Profit " + rupiah(profit) + " • Margin " + String.format("%.1f%%", margin), fontWeight = FontWeight.Bold, color = if (margin >= 25) GmuGreen else GmuWarn)
                        Text("Laba/Pax " + rupiah(if (b.pax > 0) profit / b.pax else 0.0), fontSize = 11.sp, color = Color.Gray)
                        if (!isClosed && session.profile.role == "Owner") {
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val checklist = JSONObject()
                                        .put("payment_checked", true)
                                        .put("cost_checked", true)
                                        .put("documents_checked", true)
                                        .put("report_checked", true)
                                    vm.insert(
                                        "trip_closings",
                                        mapOf(
                                            "booking_id" to b.id,
                                            "checklist" to checklist,
                                            "notes" to "Closed via Android Native RC",
                                            "closed_by" to session.userId
                                        ),
                                        "Trip berhasil di-closing."
                                    ) { ok, msg ->
                                        onNotice(msg)
                                        if (ok) {
                                            vm.update("bookings", b.id, mapOf("status" to "Closed"), "Booking ditutup.") { _, _ -> }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("CLOSE TRIP") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UsersScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    if (session.profile.role != "Owner") {
        Box(Modifier.fillMaxSize().padding(18.dp)) { EmptyCard("User & Role hanya tersedia untuk Owner.") }
        return
    }

    var add by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<ErpRow?>(null) }
    val profiles = vm.table("profiles")

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("Team & Access", "Kelola akun staf, role dan status")
            Button(onClick = { add = true }, shape = RoundedCornerShape(14.dp)) { Text("+ Staff") }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (profiles.isEmpty()) item { EmptyCard("Belum ada data profile.") }
            items(profiles, key = { it.id }) { p ->
                val isOwner = p.text("role") == "Owner"
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(p.text("full_name"), fontWeight = FontWeight.Black, color = GmuDark)
                                Text(p.text("role") + " • " + p.text("phone"), fontSize = 11.sp, color = Color.Gray)
                            }
                            StatusChip(if (p.text("is_active") == "false") "Inactive" else "Active")
                        }
                        if (!isOwner) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    vm.setStaffActive(p.id, p.text("is_active") == "false") { _, msg -> onNotice(msg) }
                                }) { Text(if (p.text("is_active") == "false") "Activate" else "Deactivate") }
                                TextButton(onClick = { target = p }) { Text("Manage") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (add) {
        AddStaffDialog(
            busy = vm.actionBusy,
            onDismiss = { add = false },
            onSave = { name, email, phone, role, password ->
                vm.createStaff(name, email, phone, role, password) { ok, msg ->
                    onNotice(msg)
                    if (ok) add = false
                }
            }
        )
    }

    target?.let { p ->
        ManageStaffDialog(
            row = p,
            busy = vm.actionBusy,
            onDismiss = { target = null },
            onRole = { role ->
                vm.setStaffRole(p.id, role) { ok, msg ->
                    onNotice(msg)
                    if (ok) target = null
                }
            },
            onReset = { password ->
                vm.resetStaffPassword(p.id, password) { ok, msg ->
                    onNotice(msg)
                    if (ok) target = null
                }
            }
        )
    }
}

@Composable
private fun AddStaffDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Sales") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Staf") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GmuField(name, { name = it }, "Nama *")
                GmuField(email, { email = it }, "Email *")
                GmuField(phone, { phone = it }, "WhatsApp")
                Spacer(Modifier.height(8.dp))
                GmuSelect(
                    value = role,
                    label = "Role",
                    options = listOf("Manager", "Admin", "Sales", "Finance", "Operation", "TL"),
                    onSelect = { role = it }
                )
                GmuField(password, { password = it }, "Password awal min. 8 karakter")
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, email, phone, role, password) },
                enabled = !busy && name.isNotBlank() && email.isNotBlank() && password.length >= 8 && role != "Owner"
            ) { Text("Buat Akun") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun ManageStaffDialog(
    row: ErpRow,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRole: (String) -> Unit,
    onReset: (String) -> Unit
) {
    var role by remember { mutableStateOf(row.text("role")) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage " + row.text("full_name")) },
        text = {
            Column {
                Spacer(Modifier.height(8.dp))
                GmuSelect(
                    value = role,
                    label = "Role",
                    options = listOf("Manager", "Admin", "Sales", "Finance", "Operation", "TL"),
                    onSelect = { role = it }
                )
                GmuField(password, { password = it }, "Password baru (opsional)")
                Text("Role valid: Manager, Admin, Sales, Finance, Operation, TL.", fontSize = 10.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Column {
                Button(onClick = { onRole(role) }, enabled = !busy && role != "Owner") { Text("Update Role") }
                if (password.length >= 8) TextButton(onClick = { onReset(password) }, enabled = !busy) { Text("Reset Password") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun AuditScreen(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val profiles = vm.table("profiles").associate { it.id to it.text("full_name") }
    val logs = vm.table("audit_logs").filter {
        query.isBlank() || it.text("action").contains(query, true) || it.text("table_name").contains(query, true) || it.text("message").contains(query, true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        SectionTitle("Audit Trail", "Riwayat aktivitas dan perubahan sistem")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cari action / modul / pesan") },
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (logs.isEmpty()) item { EmptyCard("Tidak ada audit log.") }
            items(logs, key = { it.id }) { a ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(a.text("action"), fontWeight = FontWeight.Black, color = GmuDark)
                            Text(a.text("created_at").take(16).replace("T", " "), fontSize = 10.sp, color = Color.Gray)
                        }
                        Text(a.text("table_name") + " • " + (profiles[a.text("user_id")] ?: "User"), fontSize = 11.sp, color = GmuGreen)
                        if (a.text("message").isNotBlank()) Text(a.text("message"), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
