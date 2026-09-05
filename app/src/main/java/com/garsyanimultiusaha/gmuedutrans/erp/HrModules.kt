package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.horizontalScroll
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HrScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    if (session.profile.role !in listOf("Owner", "Manager")) {
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            EmptyCard("Team & HR hanya tersedia untuk Owner dan Manager.")
        }
        return
    }

    var tab by remember { mutableStateOf("Overview") }
    var showAdd by remember { mutableStateOf(false) }
    val tabs = listOf(
        "Overview", "Staff", "Attendance", "Assignment", "KPI",
        "Performance", "Leave", "Warning/SP", "Training", "Contract", "Offboarding"
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("Team & HR", "People operations GMU EduTrans")
            if (tab !in listOf("Overview", "Staff")) {
                Button(onClick = { showAdd = true }, shape = RoundedCornerShape(14.dp)) { Text("+ Add") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            tabs.forEach { label ->
                FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(10.dp))

        when (tab) {
            "Overview" -> HrOverview(vm)
            "Staff" -> HrStaffList(vm)
            else -> HrDataList(vm, tab, session, onNotice)
        }
    }

    if (showAdd) {
        HrEntryDialog(
            vm = vm,
            session = session,
            type = tab,
            busy = vm.actionBusy,
            onDismiss = { showAdd = false },
            onSave = { table, values, message ->
                vm.insert(table, values, message) { ok, msg ->
                    onNotice(msg)
                    if (ok) showAdd = false
                }
            }
        )
    }
}

@Composable
private fun HrOverview(vm: MainViewModel) {
    val profiles = hrProfiles(vm)
    val active = profiles.count { it.text("is_active") != "false" }
    val attendanceToday = vm.table("staff_attendance").count { it.text("attendance_date") == hrToday() }
    val assignments = vm.table("staff_assignments").count { it.text("status") in listOf("Assigned", "In Progress") }
    val leave = vm.table("staff_leave").count { it.text("status") == "Pending" }
    val warnings = vm.table("staff_warnings").count { it.text("status") != "Resolved" }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Total Staff", profiles.size.toString(), Modifier.weight(1f))
                MetricCard("Active Staff", active.toString(), Modifier.weight(1f), accent = true)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Attendance Today", attendanceToday.toString(), Modifier.weight(1f))
                MetricCard("Open Assignment", assignments.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Pending Leave", leave.toString(), Modifier.weight(1f))
                MetricCard("Warning/SP", warnings.toString(), Modifier.weight(1f))
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (leave + warnings == 0) Color(0xFFEAF7EF) else Color(0xFFFFF7E8))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("People attention", fontWeight = FontWeight.Black, color = GmuDark)
                    Spacer(Modifier.height(6.dp))
                    if (leave == 0 && warnings == 0) {
                        Text("Tidak ada isu SDM yang perlu tindakan saat ini.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        if (leave > 0) Text("• " + leave + " pengajuan izin/cuti menunggu keputusan.", fontSize = 12.sp)
                        if (warnings > 0) Text("• " + warnings + " warning/SP masih terbuka.", fontSize = 12.sp)
                    }
                }
            }
        }
        item { Text("Staff Directory", fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark) }
        items(profiles.take(8), key = { it.id }) { HrStaffCard(it) }
    }
}

@Composable
private fun HrStaffList(vm: MainViewModel) {
    val profiles = hrProfiles(vm)
    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (profiles.isEmpty()) item { EmptyCard("Belum ada data staff.") }
        items(profiles, key = { it.id }) { HrStaffCard(it) }
    }
}

@Composable
private fun HrStaffCard(p: ErpRow) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(p.text("full_name").ifBlank { "Staff GMU" }, fontWeight = FontWeight.Black, color = GmuDark)
                Text(p.text("role") + if (p.text("phone").isNotBlank()) " • " + p.text("phone") else "", fontSize = 11.sp, color = Color.Gray)
            }
            StatusChip(if (p.text("is_active") == "false") "Inactive" else "Active")
        }
    }
}

@Composable
private fun HrDataList(vm: MainViewModel, type: String, session: SessionState, onNotice: (String) -> Unit) {
    val table = hrTableFor(type)
    val rows = vm.table(table)
    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (rows.isEmpty()) item { EmptyCard("Belum ada data " + type + ".") }
        items(rows, key = { it.id }) { r ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(hrTitle(vm, type, r), fontWeight = FontWeight.Black, color = GmuDark, modifier = Modifier.weight(1f))
                        StatusChip(hrStatus(type, r))
                    }
                    val subtitle = hrSubtitle(vm, type, r)
                    if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, color = Color.Gray)
                    val note = hrNote(type, r)
                    if (note.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(note, fontSize = 11.sp)
                    }

                    if (type == "Leave" && r.text("status") == "Pending") {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                vm.update(
                                    "staff_leave", r.id,
                                    mapOf("status" to "Rejected", "approved_by" to session.userId, "approved_at" to hrNow()),
                                    "Pengajuan leave ditolak."
                                ) { _, msg -> onNotice(msg) }
                            }) { Text("Reject", color = GmuDanger) }
                            Button(onClick = {
                                vm.update(
                                    "staff_leave", r.id,
                                    mapOf("status" to "Approved", "approved_by" to session.userId, "approved_at" to hrNow()),
                                    "Pengajuan leave disetujui."
                                ) { _, msg -> onNotice(msg) }
                            }) { Text("Approve") }
                        }
                    }

                    if (type == "Offboarding") {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (!r.bool("handover_complete")) {
                                TextButton(onClick = {
                                    vm.update("staff_offboarding", r.id, mapOf("handover_complete" to true), "Handover ditandai selesai.") { _, msg -> onNotice(msg) }
                                }) { Text("Handover Done") }
                            }
                            if (!r.bool("access_revoked")) {
                                Button(onClick = {
                                    vm.update("staff_offboarding", r.id, mapOf("access_revoked" to true), "Akses offboarding ditandai revoked.") { _, msg -> onNotice(msg) }
                                }) { Text("Revoke Access") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HrEntryDialog(
    vm: MainViewModel,
    session: SessionState,
    type: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, Any?>, String) -> Unit
) {
    val profiles = hrProfiles(vm)
    var staff by remember { mutableStateOf(profiles.firstOrNull()) }
    var staffMenu by remember { mutableStateOf(false) }
    val v = remember(type) { mutableStateMapOf<String, String>() }

    fun value(key: String, default: String = ""): String {
        if (!v.containsKey(key)) v[key] = default
        return v[key].orEmpty()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add " + type) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box {
                    OutlinedButton(onClick = { staffMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(staff?.text("full_name") ?: "Pilih Staff")
                    }
                    DropdownMenu(expanded = staffMenu, onDismissRequest = { staffMenu = false }) {
                        profiles.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.text("full_name") + " • " + p.text("role")) },
                                onClick = { staff = p; staffMenu = false }
                            )
                        }
                    }
                }
                HrFields(type, v)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = staff ?: return@Button
                    when (type) {
                        "Attendance" -> onSave(
                            "staff_attendance",
                            mapOf(
                                "staff_id" to s.id,
                                "attendance_date" to value("date", hrToday()),
                                "status" to value("status", "Hadir"),
                                "check_in" to value("check_in").ifBlank { null },
                                "check_out" to value("check_out").ifBlank { null },
                                "notes" to value("notes").ifBlank { null },
                                "created_by" to session.userId
                            ),
                            "Attendance staff disimpan."
                        )
                        "Assignment" -> onSave(
                            "staff_assignments",
                            mapOf(
                                "staff_id" to s.id, "title" to value("title"),
                                "due_date" to value("due").ifBlank { null },
                                "status" to value("status", "Assigned"),
                                "priority" to value("priority", "Normal"),
                                "notes" to value("notes").ifBlank { null },
                                "assigned_by" to session.userId
                            ),
                            "Assignment berhasil dibuat."
                        )
                        "KPI" -> {
                            val target = value("target").toDoubleOrNull() ?: 0.0
                            val actual = value("actual").toDoubleOrNull() ?: 0.0
                            val score = if (target > 0) actual / target * 100.0 else 0.0
                            onSave(
                                "staff_kpis",
                                mapOf(
                                    "staff_id" to s.id, "period" to value("period", hrMonth()),
                                    "metric_name" to value("metric"), "target" to target, "actual" to actual,
                                    "unit" to value("unit").ifBlank { null }, "score" to score,
                                    "created_by" to session.userId
                                ),
                                "KPI staff berhasil dibuat."
                            )
                        }
                        "Performance" -> {
                            val scores = listOf("discipline","quality","communication","responsibility","teamwork","sop").map {
                                value(it, "5").toIntOrNull()?.coerceIn(1,5) ?: 5
                            }
                            val avg = scores.average()
                            val grade = if (avg >= 4.5) "A" else if (avg >= 3.5) "B" else if (avg >= 2.5) "C" else "D"
                            onSave(
                                "staff_reviews",
                                mapOf(
                                    "staff_id" to s.id, "review_period" to value("period", hrMonth()),
                                    "discipline" to scores[0], "quality" to scores[1], "communication" to scores[2],
                                    "responsibility" to scores[3], "teamwork" to scores[4], "sop_compliance" to scores[5],
                                    "overall_score" to avg, "grade" to grade, "notes" to value("notes").ifBlank { null },
                                    "reviewed_by" to session.userId
                                ),
                                "Performance review berhasil disimpan."
                            )
                        }
                        "Leave" -> onSave(
                            "staff_leave",
                            mapOf(
                                "staff_id" to s.id, "leave_type" to value("leave_type", "Izin"),
                                "start_date" to value("start", hrToday()), "end_date" to value("end", hrToday()),
                                "reason" to value("reason").ifBlank { null }, "status" to "Pending"
                            ),
                            "Pengajuan leave berhasil dibuat."
                        )
                        "Warning/SP" -> onSave(
                            "staff_warnings",
                            mapOf(
                                "staff_id" to s.id, "warning_level" to value("level", "Teguran Lisan"),
                                "incident_date" to value("date", hrToday()), "reason" to value("reason"),
                                "action_plan" to value("action").ifBlank { null }, "status" to "Open",
                                "issued_by" to session.userId
                            ),
                            "Warning/SP berhasil dicatat."
                        )
                        "Training" -> onSave(
                            "staff_training",
                            mapOf(
                                "staff_id" to s.id, "title" to value("title"),
                                "provider" to value("provider").ifBlank { null },
                                "training_date" to value("date", hrToday()),
                                "certificate_no" to value("certificate").ifBlank { null },
                                "expiry_date" to value("expiry").ifBlank { null },
                                "created_by" to session.userId
                            ),
                            "Training staff berhasil dicatat."
                        )
                        "Contract" -> onSave(
                            "staff_contracts",
                            mapOf(
                                "staff_id" to s.id, "employment_status" to value("employment", "Kontrak"),
                                "start_date" to value("start", hrToday()), "end_date" to value("end").ifBlank { null },
                                "position_title" to value("position").ifBlank { null },
                                "notes" to value("notes").ifBlank { null }, "created_by" to session.userId
                            ),
                            "Data kontrak staff berhasil disimpan."
                        )
                        else -> onSave(
                            "staff_offboarding",
                            mapOf(
                                "staff_id" to s.id, "last_working_date" to value("last_day", hrToday()),
                                "handover_complete" to false, "access_revoked" to false,
                                "notes" to value("notes").ifBlank { null }, "processed_by" to session.userId
                            ),
                            "Proses offboarding berhasil dibuat."
                        )
                    }
                },
                enabled = !busy && staff != null && hrValid(type, v)
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun HrFields(type: String, v: MutableMap<String, String>) {
    fun field(key: String, label: String, default: String = "") {
        if (!v.containsKey(key)) v[key] = default
        GmuField(v[key].orEmpty(), { v[key] = it }, label)
    }
    fun select(key: String, label: String, options: List<String>, default: String) {
        if (!v.containsKey(key)) v[key] = default
        Spacer(Modifier.height(8.dp))
        GmuSelect(v[key].orEmpty(), label, options, { v[key] = it })
    }

    when (type) {
        "Attendance" -> {
            field("date", "Tanggal YYYY-MM-DD", hrToday())
            select("status", "Status", listOf("Hadir","Izin","Sakit","Terlambat","Tidak Hadir"), "Hadir")
            field("check_in", "Check In HH:MM")
            field("check_out", "Check Out HH:MM")
            field("notes", "Catatan")
        }
        "Assignment" -> {
            field("title", "Judul Tugas *")
            field("due", "Due Date YYYY-MM-DD")
            select("priority", "Priority", listOf("Low","Normal","High","Urgent"), "Normal")
            select("status", "Status", listOf("Assigned","In Progress","Done","Cancelled"), "Assigned")
            field("notes", "Catatan")
        }
        "KPI" -> {
            field("period", "Periode YYYY-MM", hrMonth())
            field("metric", "Metric *")
            field("target", "Target")
            field("actual", "Actual")
            field("unit", "Unit (lead/booking/pax/%/trip)")
        }
        "Performance" -> {
            field("period", "Periode YYYY-MM", hrMonth())
            select("discipline", "Discipline", listOf("1","2","3","4","5"), "5")
            select("quality", "Quality", listOf("1","2","3","4","5"), "5")
            select("communication", "Communication", listOf("1","2","3","4","5"), "5")
            select("responsibility", "Responsibility", listOf("1","2","3","4","5"), "5")
            select("teamwork", "Teamwork", listOf("1","2","3","4","5"), "5")
            select("sop", "SOP Compliance", listOf("1","2","3","4","5"), "5")
            field("notes", "Catatan")
        }
        "Leave" -> {
            select("leave_type", "Jenis", listOf("Izin","Cuti","Sakit","Keperluan Keluarga","Lainnya"), "Izin")
            field("start", "Mulai YYYY-MM-DD", hrToday())
            field("end", "Selesai YYYY-MM-DD", hrToday())
            field("reason", "Alasan")
        }
        "Warning/SP" -> {
            select("level", "Level", listOf("Teguran Lisan","SP1","SP2","SP3","Pelanggaran Berat"), "Teguran Lisan")
            field("date", "Tanggal Kejadian", hrToday())
            field("reason", "Alasan *")
            field("action", "Tindak Lanjut")
        }
        "Training" -> {
            field("title", "Nama Training *")
            field("provider", "Provider")
            field("date", "Tanggal Training", hrToday())
            field("certificate", "No. Sertifikat")
            field("expiry", "Berlaku Sampai YYYY-MM-DD")
        }
        "Contract" -> {
            select("employment", "Status Kerja", listOf("Tetap","Kontrak","Freelance","Part Time","Magang"), "Kontrak")
            field("start", "Tanggal Mulai", hrToday())
            field("end", "Tanggal Berakhir")
            field("position", "Jabatan")
            field("notes", "Catatan")
        }
        else -> {
            field("last_day", "Hari Kerja Terakhir", hrToday())
            field("notes", "Catatan Serah Terima")
        }
    }
}

private fun hrValid(type: String, v: Map<String, String>): Boolean = when (type) {
    "Assignment" -> v["title"].orEmpty().isNotBlank()
    "KPI" -> v["metric"].orEmpty().isNotBlank()
    "Warning/SP" -> v["reason"].orEmpty().isNotBlank()
    "Training" -> v["title"].orEmpty().isNotBlank()
    else -> true
}

private fun hrTableFor(type: String): String = when (type) {
    "Attendance" -> "staff_attendance"
    "Assignment" -> "staff_assignments"
    "KPI" -> "staff_kpis"
    "Performance" -> "staff_reviews"
    "Leave" -> "staff_leave"
    "Warning/SP" -> "staff_warnings"
    "Training" -> "staff_training"
    "Contract" -> "staff_contracts"
    else -> "staff_offboarding"
}

private fun hrTitle(vm: MainViewModel, type: String, r: ErpRow): String = when (type) {
    "Assignment" -> r.text("title")
    "KPI" -> r.text("metric_name")
    "Training" -> r.text("title")
    else -> hrStaffName(vm, r.text("staff_id"))
}

private fun hrSubtitle(vm: MainViewModel, type: String, r: ErpRow): String = when (type) {
    "Attendance" -> r.text("attendance_date") + " • " + listOf(r.text("check_in"), r.text("check_out")).filter { it.isNotBlank() }.joinToString(" - ")
    "Assignment" -> hrStaffName(vm, r.text("staff_id")) + " • Due " + r.text("due_date")
    "KPI" -> hrStaffName(vm, r.text("staff_id")) + " • " + r.text("period")
    "Performance" -> r.text("review_period") + " • Score " + r.text("overall_score")
    "Leave" -> r.text("leave_type") + " • " + r.text("start_date") + " s/d " + r.text("end_date")
    "Warning/SP" -> r.text("incident_date") + " • " + r.text("warning_level")
    "Training" -> hrStaffName(vm, r.text("staff_id")) + " • " + r.text("training_date")
    "Contract" -> r.text("position_title") + " • " + r.text("start_date") + if (r.text("end_date").isNotBlank()) " s/d " + r.text("end_date") else ""
    else -> "Last day: " + r.text("last_working_date")
}

private fun hrStatus(type: String, r: ErpRow): String = when (type) {
    "Attendance" -> r.text("status")
    "Assignment" -> r.text("status")
    "KPI" -> if (r.number("score") >= 100) "Achieved" else if (r.number("score") >= 75) "On Track" else "Need Improvement"
    "Performance" -> "Grade " + r.text("grade")
    "Leave" -> r.text("status")
    "Warning/SP" -> r.text("status")
    "Training" -> if (r.text("certificate_no").isBlank()) "Training" else "Certified"
    "Contract" -> r.text("employment_status")
    else -> if (r.bool("access_revoked")) "Completed" else "In Progress"
}

private fun hrNote(type: String, r: ErpRow): String = when (type) {
    "Attendance" -> r.text("notes")
    "Assignment" -> r.text("priority") + if (r.text("notes").isNotBlank()) " • " + r.text("notes") else ""
    "KPI" -> "Target " + r.text("target") + " " + r.text("unit") + " • Actual " + r.text("actual") + " • Score " + String.format("%.0f", r.number("score")) + "%"
    "Performance" -> r.text("notes")
    "Leave" -> r.text("reason")
    "Warning/SP" -> r.text("reason")
    "Training" -> listOf(r.text("provider"), r.text("certificate_no")).filter { it.isNotBlank() }.joinToString(" • ")
    "Contract" -> r.text("notes")
    else -> r.text("notes")
}

private fun hrProfiles(vm: MainViewModel): List<ErpRow> =
    vm.table("profiles").filter { it.text("role") in listOf("Owner","Manager","Admin","Sales","Finance","Operation","TL") }

private fun hrStaffName(vm: MainViewModel, id: String): String =
    hrProfiles(vm).firstOrNull { it.id == id }?.text("full_name").orEmpty().ifBlank { "Staff GMU" }

private fun hrToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private fun hrMonth(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
private fun hrNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
