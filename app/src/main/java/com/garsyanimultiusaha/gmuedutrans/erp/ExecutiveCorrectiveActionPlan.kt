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
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val CAP_PREFIX = "[ECAP]"
private val CAP_STATES = listOf("Improving", "Stable", "At Risk")

/**
 * Executive Corrective Action Plan on top of existing staff_assignments.
 * Owner owns creation/review/closure. Director/Manager PIC gets read-only visibility.
 */
@Composable
fun GmuNativeAppWithExecutiveCorrectiveActionPlan(vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithExecutiveTrendAccountability(vm)
        val state = vm.state
        if (state is AppState.LoggedIn && vm.currentPage == AppPage.DASHBOARD) {
            val session = state.session
            val role = session.profile.role
            val all = correctivePlans(vm)

            if (role == ErpRoles.OWNER) {
                val active = all.count { !capDone(it) }
                val risk = all.count { !capDone(it) && capState(it) == "At Risk" }
                Surface(
                    onClick = { open = true },
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 300.dp, start = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (risk > 0) Color(0xFFFFECEC) else Color.White,
                    shadowElevation = 5.dp
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text("Corrective Action Plan", fontWeight = FontWeight.Black, color = GmuDark, fontSize = 11.sp)
                        Text("$active aktif • $risk at risk", color = if (risk > 0) GmuDanger else GmuGreen, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                }
            } else if (ErpRoles.isDirector(role) || ErpRoles.isManagerEduTrans(role)) {
                val mine = all.filter { it.text("staff_id") in setOf(session.userId, session.profile.id) }
                val active = mine.count { !capDone(it) }
                val risk = mine.count { !capDone(it) && capState(it) == "At Risk" }
                if (mine.isNotEmpty()) {
                    Surface(
                        onClick = { open = true },
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 300.dp, end = 16.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = if (risk > 0) Color(0xFFFFECEC) else Color.White,
                        shadowElevation = 5.dp
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalAlignment = Alignment.End) {
                            Text("My Improvement Plan", fontWeight = FontWeight.Black, color = GmuDark, fontSize = 11.sp)
                            Text("$active aktif • $risk at risk", color = if (risk > 0) GmuDanger else GmuGreen, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                        }
                    }
                }
            }

            if (open) {
                if (role == ErpRoles.OWNER) {
                    OwnerCorrectivePlanDialog(vm, session) { open = false }
                } else {
                    PicCorrectivePlanDialog(vm, session) { open = false }
                }
            }
        }
    }
}

@Composable
private fun OwnerCorrectivePlanDialog(vm: MainViewModel, session: SessionState, onDismiss: () -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    var reviewRow by remember { mutableStateOf<ErpRow?>(null) }
    var filter by remember { mutableStateOf("Active") }
    var notice by remember { mutableStateOf<String?>(null) }
    val all = correctivePlans(vm)
    val rows = when (filter) {
        "Active" -> all.filterNot(::capDone)
        "At Risk" -> all.filter { !capDone(it) && capState(it) == "At Risk" }
        "Done" -> all.filter(::capDone)
        else -> all
    }.sortedWith(compareBy<ErpRow> { capDueSort(it) }.thenBy { it.text("due_date") })
    val active = all.count { !capDone(it) }
    val risk = all.count { !capDone(it) && capState(it) == "At Risk" }
    val due = all.count { !capDone(it) && capDaysUntil(it.text("due_date"))?.let { d -> d <= 7 } == true }

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(.92f), shape = RoundedCornerShape(26.dp), color = Color(0xFFF7F8FA)) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Executive Corrective Action Plan", fontWeight = FontWeight.Black, fontSize = 19.sp, color = GmuDark)
                        Text("Owner control • target 30/60/90 hari • review berkala", fontSize = 10.sp, color = Color.Gray)
                    }
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CapMetric("Active", active, GmuGold, Modifier.weight(1f))
                    CapMetric("At Risk", risk, GmuDanger, Modifier.weight(1f))
                    CapMetric("Due ≤7d", due, GmuWarn, Modifier.weight(1f))
                }
                notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7EF))) {
                        Text(it, Modifier.fillMaxWidth().padding(10.dp), fontSize = 10.sp, color = GmuGreen)
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("Active", "At Risk", "Done", "All").forEach { value ->
                            FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value, fontSize = 9.sp) })
                        }
                    }
                    Button(onClick = { showCreate = true }, shape = RoundedCornerShape(12.dp)) { Text("+ Buat Plan", fontSize = 10.sp) }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                    if (rows.isEmpty()) item { Text("Belum ada corrective plan pada filter ini.", fontSize = 10.sp, color = Color.Gray) }
                    items(rows, key = { it.id }) { row ->
                        CapOwnerCard(
                            vm = vm,
                            row = row,
                            onReview = { reviewRow = row },
                            onNotice = { notice = it }
                        )
                    }
                }
                OutlinedButton(onClick = { vm.loadAll() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("Refresh Corrective Plans", fontSize = 10.sp)
                }
            }
        }
    }

    if (showCreate) {
        CapCreateDialog(vm, session, onDismiss = { if (!vm.actionBusy) showCreate = false }) { ok, msg ->
            notice = msg
            if (ok) showCreate = false
        }
    }
    reviewRow?.let { row ->
        CapReviewDialog(vm, session, row, onDismiss = { if (!vm.actionBusy) reviewRow = null }) { msg ->
            notice = msg
            reviewRow = null
        }
    }
}

@Composable
private fun PicCorrectivePlanDialog(vm: MainViewModel, session: SessionState, onDismiss: () -> Unit) {
    val rows = correctivePlans(vm).filter { it.text("staff_id") in setOf(session.userId, session.profile.id) }
        .sortedWith(compareBy<ErpRow> { capDueSort(it) }.thenBy { it.text("due_date") })
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(.88f), shape = RoundedCornerShape(26.dp), color = Color(0xFFF7F8FA)) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("My Improvement Plan", fontWeight = FontWeight.Black, fontSize = 19.sp, color = GmuDark)
                        Text("${session.profile.fullName} • evaluasi status dikontrol Owner", fontSize = 10.sp, color = Color.Gray)
                    }
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }
                Spacer(Modifier.height(9.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (rows.isEmpty()) item { Text("Tidak ada corrective plan yang ditugaskan.", fontSize = 10.sp, color = Color.Gray) }
                    items(rows, key = { it.id }) { row -> CapReadOnlyCard(row) }
                }
                OutlinedButton(onClick = { vm.loadAll() }, modifier = Modifier.fillMaxWidth()) { Text("Refresh", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun CapMetric(label: String, value: Int, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(10.dp)) { Text(value.toString(), fontWeight = FontWeight.Black, fontSize = 17.sp, color = accent); Text(label, fontSize = 8.sp, color = Color.Gray) }
    }
}

@Composable
private fun CapOwnerCard(vm: MainViewModel, row: ErpRow, onReview: () -> Unit, onNotice: (String) -> Unit) {
    val pic = vm.table("profiles").firstOrNull { it.id == row.text("staff_id") }
    val state = capState(row)
    val accent = capStateColor(state)
    val done = capDone(row)
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(row.text("title").removePrefix(CAP_PREFIX).trim(), fontWeight = FontWeight.Black, fontSize = 12.sp, color = GmuDark)
                    Text("PIC: ${pic?.text("full_name").takeUnless { it.isNullOrBlank() } ?: row.text("staff_id").take(8)}", fontSize = 9.sp, color = Color.Gray)
                    Text("Deadline ${row.text("due_date").ifBlank { "-" }} • ${capHorizon(row)} hari", fontSize = 9.sp, color = Color.Gray)
                }
                Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = .12f)) {
                    Text(if (done) "CLOSED" else state.uppercase(), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 8.sp, fontWeight = FontWeight.Black, color = if (done) GmuGreen else accent)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(capPlanSummary(row), fontSize = 9.sp, color = Color.DarkGray)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!done) {
                    TextButton(onClick = onReview, enabled = !vm.actionBusy) { Text("Review", fontSize = 10.sp) }
                    Button(
                        onClick = {
                            val notes = capAppend(row.text("notes"), "ECAP CLOSED", sessionName = "Owner", state = null, note = "Corrective plan ditutup Owner.")
                            vm.update("staff_assignments", row.id, mapOf("status" to "Done", "notes" to notes), "Corrective plan ditutup.") { _, msg -> onNotice(msg) }
                        },
                        enabled = !vm.actionBusy,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Close Plan", fontSize = 10.sp) }
                } else {
                    TextButton(
                        onClick = { vm.update("staff_assignments", row.id, mapOf("status" to "Active"), "Corrective plan dibuka kembali.") { _, msg -> onNotice(msg) } },
                        enabled = !vm.actionBusy
                    ) { Text("Reopen", fontSize = 10.sp) }
                }
            }
        }
    }
}

@Composable
private fun CapReadOnlyCard(row: ErpRow) {
    val state = capState(row)
    val accent = capStateColor(state)
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(row.text("title").removePrefix(CAP_PREFIX).trim(), fontWeight = FontWeight.Black, fontSize = 12.sp, color = GmuDark)
                    Text("Deadline ${row.text("due_date").ifBlank { "-" }} • ${capHorizon(row)} hari", fontSize = 9.sp, color = Color.Gray)
                }
                Text(if (capDone(row)) "CLOSED" else state.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Black, color = if (capDone(row)) GmuGreen else accent)
            }
            Spacer(Modifier.height(7.dp))
            Text(capPlanSummary(row), fontSize = 9.sp, color = Color.DarkGray)
            val lastReview = capLastReview(row)
            if (lastReview.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(lastReview.takeLast(420), fontSize = 9.sp, color = accent) }
        }
    }
}

@Composable
private fun CapCreateDialog(vm: MainViewModel, session: SessionState, onDismiss: () -> Unit, onDone: (Boolean, String) -> Unit) {
    val pics = vm.table("profiles").filter { it.text("is_active") != "false" && (ErpRoles.isDirector(it.text("role")) || ErpRoles.isManagerEduTrans(it.text("role"))) }
    var pic by remember(pics) { mutableStateOf(pics.firstOrNull()) }
    var picMenu by remember { mutableStateOf(false) }
    var horizon by remember { mutableIntStateOf(30) }
    var target by remember { mutableStateOf("") }
    var indicator by remember { mutableStateOf("") }
    var baseline by remember { mutableStateOf("") }
    var cadence by remember { mutableStateOf("Weekly") }
    var cadenceMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!vm.actionBusy) onDismiss() },
        title = { Text("Buat Corrective Action Plan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Plan disimpan sebagai assignment [ECAP]. Owner mengendalikan review dan status evaluasi.", fontSize = 10.sp, color = Color.Gray)
                Box {
                    OutlinedButton(onClick = { picMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(pic?.let { "${it.text("full_name")} • ${ErpRoles.displayName(it.text("role"))}" } ?: "Pilih PIC") }
                    DropdownMenu(expanded = picMenu, onDismissRequest = { picMenu = false }) {
                        pics.forEach { p -> DropdownMenuItem(text = { Text("${p.text("full_name")} • ${ErpRoles.displayName(p.text("role"))}") }, onClick = { pic = p; picMenu = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(30, 60, 90).forEach { d -> FilterChip(selected = horizon == d, onClick = { horizon = d }, label = { Text("$d hari") }) } }
                OutlinedTextField(target, { target = it }, label = { Text("Target perbaikan") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(indicator, { indicator = it }, label = { Text("Indikator keberhasilan") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(baseline, { baseline = it }, label = { Text("Baseline / masalah awal") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Box {
                    OutlinedButton(onClick = { cadenceMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Review: $cadence") }
                    DropdownMenu(expanded = cadenceMenu, onDismissRequest = { cadenceMenu = false }) {
                        listOf("Weekly", "Biweekly", "Monthly").forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { cadence = c; cadenceMenu = false }) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !vm.actionBusy && pic != null && target.isNotBlank() && indicator.isNotBlank(),
                onClick = {
                    val selected = pic ?: return@Button
                    val due = capDatePlus(horizon)
                    val notes = buildString {
                        append("ECAP | Horizon: $horizon | Review: $cadence\n")
                        append("Target: ${target.trim()}\n")
                        append("Success Indicator: ${indicator.trim()}\n")
                        if (baseline.isNotBlank()) append("Baseline: ${baseline.trim()}\n")
                        append(capAuditLine("ECAP STATUS", session.profile.fullName, "Stable", "Plan dibuat Owner."))
                    }
                    vm.insert(
                        "staff_assignments",
                        mapOf(
                            "staff_id" to selected.id,
                            "title" to "$CAP_PREFIX $horizonD ${target.trim()}",
                            "due_date" to due,
                            "status" to "Active",
                            "priority" to "High",
                            "notes" to notes,
                            "assigned_by" to session.userId
                        ),
                        "Corrective Action Plan berhasil dibuat."
                    ) { ok, msg -> onDone(ok, msg) }
                }
            ) { Text(if (vm.actionBusy) "Menyimpan…" else "Buat Plan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.actionBusy) { Text("Batal") } }
    )
}

@Composable
private fun CapReviewDialog(vm: MainViewModel, session: SessionState, row: ErpRow, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var state by remember(row.id) { mutableStateOf(capState(row)) }
    var note by remember(row.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!vm.actionBusy) onDismiss() },
        title = { Text("Review Corrective Plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status hanya dapat ditetapkan Owner dan akan masuk audit trail.", fontSize = 10.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    CAP_STATES.forEach { value -> FilterChip(selected = state == value, onClick = { state = value }, label = { Text(value, fontSize = 9.sp) }) }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Catatan review / evidence") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = {
            Button(
                enabled = !vm.actionBusy && note.isNotBlank(),
                onClick = {
                    val notes = capAppend(row.text("notes"), "ECAP STATUS", session.profile.fullName, state, note.trim())
                    vm.update("staff_assignments", row.id, mapOf("notes" to notes), "Review corrective plan tersimpan.") { ok, msg -> if (ok) onDone(msg) }
                }
            ) { Text(if (vm.actionBusy) "Menyimpan…" else "Simpan Review") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.actionBusy) { Text("Batal") } }
    )
}

private fun correctivePlans(vm: MainViewModel): List<ErpRow> = vm.table("staff_assignments").filter { it.text("title").startsWith(CAP_PREFIX) }
private fun capDone(row: ErpRow): Boolean = row.text("status") in setOf("Done", "Completed", "Closed", "Resolved")
private fun capState(row: ErpRow): String {
    val match = Regex("ECAP STATUS: (Improving|Stable|At Risk)").findAll(row.text("notes")).lastOrNull()
    return match?.groupValues?.getOrNull(1) ?: "Stable"
}
private fun capStateColor(state: String): Color = when (state) { "Improving" -> GmuGreen; "At Risk" -> GmuDanger; else -> GmuGold }
private fun capHorizon(row: ErpRow): String = Regex("Horizon: (30|60|90)").find(row.text("notes"))?.groupValues?.getOrNull(1) ?: "-"
private fun capPlanSummary(row: ErpRow): String = row.text("notes").lineSequence().filter { it.startsWith("Target:") || it.startsWith("Success Indicator:") || it.startsWith("Baseline:") }.take(3).joinToString("\n")
private fun capLastReview(row: ErpRow): String = row.text("notes").lineSequence().filter { it.contains("ECAP STATUS:") }.lastOrNull().orEmpty()
private fun capDueSort(row: ErpRow): Int { if (capDone(row)) return 4; if (capState(row) == "At Risk") return 0; val d = capDaysUntil(row.text("due_date")) ?: return 3; return when { d < 0 -> 0; d <= 7 -> 1; else -> 2 } }
private fun capDaysUntil(date: String): Int? = runCatching { val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }; val target = f.parse(date) ?: return@runCatching null; val today = f.parse(f.format(Date())) ?: return@runCatching null; ((target.time - today.time) / 86_400_000L).toInt() }.getOrNull()
private fun capDatePlus(days: Int): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, days) }.time)
private fun capAuditLine(tag: String, sessionName: String, state: String?, note: String): String { val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()); return "[$stamp] $tag${state?.let { ": $it" }.orEmpty()} by $sessionName: ${note.trim()}" }
private fun capAppend(existing: String, tag: String, sessionName: String, state: String?, note: String): String = listOf(existing.trim(), capAuditLine(tag, sessionName, state, note)).filter { it.isNotBlank() }.joinToString("\n")
