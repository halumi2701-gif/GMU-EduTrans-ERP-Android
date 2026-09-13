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
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val REVIEW_CAP_PREFIX = "[ECAP]"
private val REVIEW_STATES = listOf("Improving", "Stable", "At Risk")
private val REVIEW_AUDIT_REGEX = Regex("\\[(\\d{4}-\\d{2}-\\d{2}) \\d{2}:\\d{2}] ECAP STATUS: (Improving|Stable|At Risk)")

private data class ReviewPoint(val date: Date, val dateText: String, val state: String)
private data class ReviewControl(
    val row: ErpRow,
    val cadence: String,
    val cadenceDays: Int,
    val currentState: String,
    val lastReview: String,
    val nextReview: String,
    val daysUntil: Int,
    val consecutiveAtRisk: Boolean
)

@Composable
fun GmuNativeAppWithCorrectivePlanReviewCalendar(vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithExecutiveCorrectiveActionPlan(vm)
        val state = vm.state
        if (state is AppState.LoggedIn && vm.currentPage == AppPage.DASHBOARD) {
            val role = state.session.profile.role
            val all = remember(vm.rows) { reviewControls(vm) }
            val visible = when {
                role == ErpRoles.OWNER -> all
                ErpRoles.isDirector(role) || ErpRoles.isManagerEduTrans(role) -> all.filter {
                    it.row.text("staff_id") in setOf(state.session.userId, state.session.profile.id)
                }
                else -> emptyList()
            }
            val due = visible.count { it.daysUntil <= 3 }
            val overdue = visible.count { it.daysUntil < 0 }
            val escalated = visible.count { it.consecutiveAtRisk }

            if (visible.isNotEmpty() && (role == ErpRoles.OWNER || ErpRoles.isDirector(role) || ErpRoles.isManagerEduTrans(role))) {
                Surface(
                    onClick = { open = true },
                    modifier = Modifier
                        .align(if (role == ErpRoles.OWNER) Alignment.TopStart else Alignment.TopEnd)
                        .padding(top = 358.dp, start = if (role == ErpRoles.OWNER) 16.dp else 0.dp, end = if (role == ErpRoles.OWNER) 0.dp else 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (escalated > 0 || overdue > 0) Color(0xFFFFECEC) else Color.White,
                    shadowElevation = 5.dp
                ) {
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalAlignment = if (role == ErpRoles.OWNER) Alignment.Start else Alignment.End
                    ) {
                        Text(if (role == ErpRoles.OWNER) "Review Calendar" else "My Review Reminder", fontWeight = FontWeight.Black, color = GmuDark, fontSize = 11.sp)
                        Text(
                            when {
                                escalated > 0 -> "$escalated escalated • $overdue overdue"
                                overdue > 0 -> "$overdue overdue • $due due"
                                else -> "$due due ≤3 hari"
                            },
                            color = if (escalated > 0 || overdue > 0) GmuDanger else GmuGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            if (open) {
                ReviewCalendarDialog(
                    vm = vm,
                    session = state.session,
                    ownerMode = role == ErpRoles.OWNER,
                    controls = visible,
                    onDismiss = { open = false }
                )
            }
        }
    }
}

@Composable
private fun ReviewCalendarDialog(
    vm: MainViewModel,
    session: SessionState,
    ownerMode: Boolean,
    controls: List<ReviewControl>,
    onDismiss: () -> Unit
) {
    var filter by remember { mutableStateOf("Due") }
    var reviewTarget by remember { mutableStateOf<ReviewControl?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val rows = when (filter) {
        "Due" -> controls.filter { it.daysUntil <= 3 || it.consecutiveAtRisk }
        "Escalated" -> controls.filter { it.consecutiveAtRisk }
        "Overdue" -> controls.filter { it.daysUntil < 0 }
        else -> controls
    }.sortedWith(compareByDescending<ReviewControl> { it.consecutiveAtRisk }.thenBy { it.daysUntil })

    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(.9f), shape = RoundedCornerShape(26.dp), color = Color(0xFFF7F8FA)) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Corrective Plan Review Calendar", fontWeight = FontWeight.Black, fontSize = 19.sp, color = GmuDark)
                        Text(
                            if (ownerMode) "Owner review control • Weekly / Biweekly / Monthly" else "Reminder PIC • status evaluasi tetap dikontrol Owner",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }

                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ReviewMetric("Due ≤3d", controls.count { it.daysUntil in 0..3 }, GmuWarn, Modifier.weight(1f))
                    ReviewMetric("Overdue", controls.count { it.daysUntil < 0 }, GmuDanger, Modifier.weight(1f))
                    ReviewMetric("Escalated", controls.count { it.consecutiveAtRisk }, GmuDanger, Modifier.weight(1f))
                }
                notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7EF))) {
                        Text(it, Modifier.fillMaxWidth().padding(10.dp), fontSize = 10.sp, color = GmuGreen)
                    }
                }

                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Due", "Escalated", "Overdue", "All").forEach { value ->
                        FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value, fontSize = 9.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                    if (rows.isEmpty()) item { Text("Tidak ada review pada filter ini.", fontSize = 10.sp, color = Color.Gray) }
                    items(rows, key = { it.row.id }) { control ->
                        ReviewCalendarCard(
                            vm = vm,
                            control = control,
                            ownerMode = ownerMode,
                            onReview = { reviewTarget = control }
                        )
                    }
                }

                OutlinedButton(onClick = { vm.loadAll() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("Refresh Review Calendar", fontSize = 10.sp)
                }
            }
        }
    }

    reviewTarget?.let { target ->
        ReviewNowDialog(vm, session, target, onDismiss = { if (!vm.actionBusy) reviewTarget = null }) { msg ->
            notice = msg
            reviewTarget = null
        }
    }
}

@Composable
private fun ReviewMetric(label: String, value: Int, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(10.dp)) {
            Text(value.toString(), fontWeight = FontWeight.Black, fontSize = 17.sp, color = accent)
            Text(label, fontSize = 8.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun ReviewCalendarCard(vm: MainViewModel, control: ReviewControl, ownerMode: Boolean, onReview: () -> Unit) {
    val pic = vm.table("profiles").firstOrNull { it.id == control.row.text("staff_id") }
    val accent = when {
        control.consecutiveAtRisk -> GmuDanger
        control.daysUntil < 0 -> GmuDanger
        control.daysUntil <= 3 -> GmuWarn
        else -> GmuGreen
    }
    val status = when {
        control.consecutiveAtRisk -> "ESCALATED • 2x AT RISK"
        control.daysUntil < 0 -> "OVERDUE ${-control.daysUntil} HARI"
        control.daysUntil == 0 -> "REVIEW TODAY"
        control.daysUntil <= 3 -> "DUE H-${control.daysUntil}"
        else -> "NEXT ${control.nextReview}"
    }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(control.row.text("title").removePrefix(REVIEW_CAP_PREFIX).trim(), fontWeight = FontWeight.Black, fontSize = 12.sp, color = GmuDark)
                    Text("PIC: ${pic?.text("full_name").takeUnless { it.isNullOrBlank() } ?: control.row.text("staff_id").take(8)}", fontSize = 9.sp, color = Color.Gray)
                }
                Text(status, fontSize = 8.sp, fontWeight = FontWeight.Black, color = accent)
            }
            Spacer(Modifier.height(6.dp))
            Text("Cadence ${control.cadence} • Last ${control.lastReview} • Next ${control.nextReview}", fontSize = 9.sp, color = Color.DarkGray)
            Text("Status Owner: ${control.currentState}", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = accent)
            if (control.consecutiveAtRisk) {
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFECEC)) {
                    Text("Escalation wajib: dua review berturut-turut berstatus At Risk.", Modifier.fillMaxWidth().padding(9.dp), fontSize = 9.sp, color = GmuDanger, fontWeight = FontWeight.Bold)
                }
            }
            if (ownerMode) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onReview, enabled = !vm.actionBusy, shape = RoundedCornerShape(12.dp)) { Text("Review Sekarang", fontSize = 10.sp) }
                }
            }
        }
    }
}

@Composable
private fun ReviewNowDialog(vm: MainViewModel, session: SessionState, control: ReviewControl, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var state by remember(control.row.id) { mutableStateOf(control.currentState) }
    var note by remember(control.row.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!vm.actionBusy) onDismiss() },
        title = { Text("Review Corrective Plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Review ini otomatis menggeser Next Review Date sesuai cadence ${control.cadence}.", fontSize = 10.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    REVIEW_STATES.forEach { value -> FilterChip(selected = state == value, onClick = { state = value }, label = { Text(value, fontSize = 9.sp) }) }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Evidence / catatan review") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = {
            Button(
                enabled = !vm.actionBusy && note.isNotBlank(),
                onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                    val audit = "[$stamp] ECAP STATUS: $state by ${session.profile.fullName}: ${note.trim()}"
                    val notes = listOf(control.row.text("notes").trim(), audit).filter { it.isNotBlank() }.joinToString("\n")
                    vm.update("staff_assignments", control.row.id, mapOf("notes" to notes), "Review calendar diperbarui.") { ok, msg -> if (ok) onDone(msg) }
                },
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (vm.actionBusy) "Menyimpan…" else "Simpan Review") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.actionBusy) { Text("Batal") } }
    )
}

private fun reviewControls(vm: MainViewModel): List<ReviewControl> = vm.table("staff_assignments")
    .filter { it.text("title").startsWith(REVIEW_CAP_PREFIX) && it.text("status") !in setOf("Done", "Completed", "Closed", "Resolved") }
    .mapNotNull(::buildReviewControl)

private fun buildReviewControl(row: ErpRow): ReviewControl? {
    val cadence = Regex("Review: (Weekly|Biweekly|Monthly)").find(row.text("notes"))?.groupValues?.getOrNull(1) ?: "Weekly"
    val cadenceDays = when (cadence) { "Biweekly" -> 14; "Monthly" -> 30; else -> 7 }
    val points = REVIEW_AUDIT_REGEX.findAll(row.text("notes")).mapNotNull { match ->
        val dateText = match.groupValues[1]
        val state = match.groupValues[2]
        parseReviewDate(dateText)?.let { ReviewPoint(it, dateText, state) }
    }.toList()
    val last = points.lastOrNull() ?: return null
    val next = Calendar.getInstance().apply { time = last.date; add(Calendar.DAY_OF_MONTH, cadenceDays) }.time
    val nextText = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(next)
    val days = reviewDaysFromToday(next)
    val twoRisk = points.size >= 2 && points.takeLast(2).all { it.state == "At Risk" }
    return ReviewControl(row, cadence, cadenceDays, last.state, last.dateText, nextText, days, twoRisk)
}

private fun parseReviewDate(value: String): Date? = runCatching {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)
}.getOrNull()

private fun reviewDaysFromToday(target: Date): Int {
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val today = f.parse(f.format(Date())) ?: Date()
    return ((target.time - today.time) / 86_400_000L).toInt()
}
