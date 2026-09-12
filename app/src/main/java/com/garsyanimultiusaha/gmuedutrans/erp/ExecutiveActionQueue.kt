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
import kotlin.math.roundToInt

private const val EXEC_PREFIX = "[EXEC]"

/**
 * Executive follow-up layer built on the existing staff_assignments table.
 * No new database schema is required. Owner creates and controls executive actions,
 * while normal HR assignments continue to work unchanged.
 */
@Composable
fun GmuNativeAppWithExecutiveActionQueue(vm: MainViewModel) {
    var openQueue by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithOwnerExecutiveExceptions(vm)

        val state = vm.state
        if (
            state is AppState.LoggedIn &&
            state.session.profile.role == ErpRoles.OWNER &&
            vm.currentPage == AppPage.DASHBOARD
        ) {
            val actions = executiveActions(vm)
            val openCount = actions.count { !executiveDone(it) }
            val escalated = actions.count { executiveSla(it).level == ExecutiveSlaLevel.ESCALATE }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 126.dp, end = 16.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (escalated > 0) Color(0xFFFFECEC) else Color.White,
                tonalElevation = 5.dp,
                shadowElevation = 5.dp
            ) {
                TextButton(onClick = { openQueue = true }) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Executive Actions",
                            fontWeight = FontWeight.Black,
                            color = GmuDark,
                            fontSize = 11.sp
                        )
                        Text(
                            if (openCount == 0) "Tidak ada action terbuka" else "$openCount open • $escalated SLA",
                            color = if (escalated > 0) GmuDanger else GmuGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            if (openQueue) {
                ExecutiveActionQueueDialog(
                    vm = vm,
                    session = state.session,
                    onDismiss = { openQueue = false }
                )
            }
        }
    }
}

private enum class ExecutiveSlaLevel { OK, DUE, ESCALATE, CLOSED }

private data class ExecutiveSla(
    val level: ExecutiveSlaLevel,
    val label: String
)

@Composable
private fun ExecutiveActionQueueDialog(
    vm: MainViewModel,
    session: SessionState,
    onDismiss: () -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("Open") }

    val all = executiveActions(vm)
    val rows = when (filter) {
        "Open" -> all.filterNot(::executiveDone)
        "Escalated" -> all.filter { executiveSla(it).level == ExecutiveSlaLevel.ESCALATE }
        "Done" -> all.filter(::executiveDone)
        else -> all
    }.sortedWith(
        compareBy<ErpRow> { executiveSlaSort(executiveSla(it).level) }
            .thenBy { it.text("due_date") }
    )

    val openCount = all.count { !executiveDone(it) }
    val overdue = all.count { executiveSla(it).level == ExecutiveSlaLevel.ESCALATE }
    val done = all.count(::executiveDone)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFF7F8FA)
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Executive Action Queue", fontWeight = FontWeight.Black, fontSize = 19.sp, color = GmuDark)
                        Text(
                            "Delegasi Owner → PIC → deadline → SLA → selesai.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExecutiveActionMetric("Open", openCount, GmuWarn, Modifier.weight(1f))
                    ExecutiveActionMetric("SLA", overdue, GmuDanger, Modifier.weight(1f))
                    ExecutiveActionMetric("Done", done, GmuGreen, Modifier.weight(1f))
                }

                notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7EF))) {
                        Text(it, Modifier.fillMaxWidth().padding(10.dp), fontSize = 10.sp, color = GmuGreen)
                    }
                }

                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("Open", "Escalated", "Done", "All").forEach { value ->
                            FilterChip(
                                selected = filter == value,
                                onClick = { filter = value },
                                label = { Text(value, fontSize = 9.sp) }
                            )
                        }
                    }
                    Button(
                        onClick = { showCreate = true },
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("+ Delegasikan", fontSize = 10.sp) }
                }

                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    if (rows.isEmpty()) {
                        item {
                            Card(shape = RoundedCornerShape(18.dp)) {
                                Text(
                                    "Tidak ada executive action pada filter ini.",
                                    Modifier.fillMaxWidth().padding(18.dp),
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    items(rows, key = { it.id }) { row ->
                        ExecutiveActionCard(
                            vm = vm,
                            row = row,
                            onNotice = { notice = it }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { vm.loadAll() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Refresh Action Queue", fontSize = 10.sp)
                }
            }
        }
    }

    if (showCreate) {
        ExecutiveActionCreateDialog(
            vm = vm,
            session = session,
            onDismiss = { if (!vm.actionBusy) showCreate = false },
            onNotice = { ok, msg ->
                notice = msg
                if (ok) showCreate = false
            }
        )
    }
}

@Composable
private fun ExecutiveActionMetric(label: String, value: Int, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(11.dp)) {
            Text(value.toString(), fontWeight = FontWeight.Black, fontSize = 18.sp, color = accent)
            Text(label, fontSize = 9.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun ExecutiveActionCard(
    vm: MainViewModel,
    row: ErpRow,
    onNotice: (String) -> Unit
) {
    val profiles = vm.table("profiles")
    val pic = profiles.firstOrNull { it.id == row.text("staff_id") }
    val status = row.text("status").ifBlank { "Assigned" }
    val priority = row.text("priority").ifBlank { "Normal" }
    val sla = executiveSla(row)
    val title = row.text("title").removePrefix(EXEC_PREFIX).trim()

    val accent = when (sla.level) {
        ExecutiveSlaLevel.ESCALATE -> GmuDanger
        ExecutiveSlaLevel.DUE -> GmuWarn
        ExecutiveSlaLevel.CLOSED -> GmuGreen
        ExecutiveSlaLevel.OK -> GmuDark
    }

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(priority.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = accent)
                    Text(title.ifBlank { "Executive action" }, fontWeight = FontWeight.Black, fontSize = 12.sp, color = GmuDark)
                    Text(
                        "PIC: ${pic?.text("full_name").takeUnless { it.isNullOrBlank() } ?: "Staff ${row.text("staff_id").take(8)}"}",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(status, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GmuDark)
                    Text(sla.label, fontSize = 9.sp, fontWeight = FontWeight.Black, color = accent)
                }
            }

            val notes = row.text("notes")
            if (notes.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(notes.take(220), fontSize = 10.sp, color = Color.Gray)
            }
            if (row.text("due_date").isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Deadline ${row.text("due_date")}", fontSize = 10.sp, color = accent, fontWeight = FontWeight.SemiBold)
            }

            if (!executiveDone(row)) {
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (status == "Assigned") {
                        TextButton(onClick = {
                            vm.update(
                                "staff_assignments",
                                row.id,
                                mapOf("status" to "In Progress"),
                                "Executive action dimulai."
                            ) { _, msg -> onNotice(msg) }
                        }) { Text("Mulai", fontSize = 10.sp) }
                    }
                    Button(
                        onClick = {
                            vm.update(
                                "staff_assignments",
                                row.id,
                                mapOf("status" to "Done"),
                                "Executive action ditutup sebagai selesai."
                            ) { _, msg -> onNotice(msg) }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Selesai", fontSize = 10.sp) }
                }
            } else {
                Spacer(Modifier.height(7.dp))
                TextButton(onClick = {
                    vm.update(
                        "staff_assignments",
                        row.id,
                        mapOf("status" to "In Progress"),
                        "Executive action dibuka kembali."
                    ) { _, msg -> onNotice(msg) }
                }) { Text("Reopen", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun ExecutiveActionCreateDialog(
    vm: MainViewModel,
    session: SessionState,
    onDismiss: () -> Unit,
    onNotice: (Boolean, String) -> Unit
) {
    val eligible = vm.table("profiles").filter { p ->
        p.text("is_active") != "false" &&
            (p.text("role") == ErpRoles.OWNER || ErpRoles.isDirector(p.text("role")) || ErpRoles.isManagerEduTrans(p.text("role")))
    }

    var selectedPic by remember(eligible) { mutableStateOf(eligible.firstOrNull()) }
    var selectedBooking by remember { mutableStateOf<Booking?>(null) }
    var picMenu by remember { mutableStateOf(false) }
    var bookingMenu by remember { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf("Executive Exception") }
    var title by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf(executiveTomorrow()) }
    var priority by remember { mutableStateOf("High") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!vm.actionBusy) onDismiss() },
        title = { Text("Delegasikan Executive Action") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Owner menetapkan PIC dan SLA. Data disimpan sebagai staff assignment bertanda [EXEC].",
                    fontSize = 10.sp,
                    color = Color.Gray
                )

                Box {
                    OutlinedButton(onClick = { picMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedPic?.let { "${it.text("full_name")} • ${ErpRoles.displayName(it.text("role"))}" } ?: "Pilih PIC")
                    }
                    DropdownMenu(expanded = picMenu, onDismissRequest = { picMenu = false }) {
                        eligible.forEach { p ->
                            DropdownMenuItem(
                                text = { Text("${p.text("full_name")} • ${ErpRoles.displayName(p.text("role"))}") },
                                onClick = { selectedPic = p; picMenu = false }
                            )
                        }
                    }
                }

                Box {
                    OutlinedButton(onClick = { bookingMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedBooking?.let { "${it.bookingNo} • ${it.customerName}" } ?: "Booking terkait (opsional)")
                    }
                    DropdownMenu(expanded = bookingMenu, onDismissRequest = { bookingMenu = false }) {
                        DropdownMenuItem(text = { Text("Tanpa booking") }, onClick = { selectedBooking = null; bookingMenu = false })
                        vm.bookings.take(30).forEach { booking ->
                            DropdownMenuItem(
                                text = { Text("${booking.bookingNo} • ${booking.customerName}") },
                                onClick = { selectedBooking = booking; bookingMenu = false }
                            )
                        }
                    }
                }

                Box {
                    OutlinedButton(onClick = { sourceMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Sumber: $source") }
                    DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                        listOf(
                            "Executive Exception",
                            "Approval strategis",
                            "Trip readiness",
                            "Closing terlambat",
                            "Cost variance",
                            "Trip incident",
                            "Owner instruction"
                        ).forEach { item ->
                            DropdownMenuItem(text = { Text(item) }, onClick = { source = item; sourceMenu = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tindak lanjut") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = deadline,
                    onValueChange = { deadline = it },
                    label = { Text("Deadline YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Box {
                    OutlinedButton(onClick = { priorityMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Priority: $priority") }
                    DropdownMenu(expanded = priorityMenu, onDismissRequest = { priorityMenu = false }) {
                        listOf("Critical", "High", "Normal").forEach { item ->
                            DropdownMenuItem(text = { Text(item) }, onClick = { priority = item; priorityMenu = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Instruksi / output yang diharapkan") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !vm.actionBusy && selectedPic != null && title.isNotBlank() && executiveValidDate(deadline),
                onClick = {
                    val pic = selectedPic ?: return@Button
                    val bookingPrefix = selectedBooking?.bookingNo?.let { "$it • " }.orEmpty()
                    val actionTitle = "$EXEC_PREFIX $bookingPrefix${title.trim()}"
                    val metadata = buildString {
                        append("Executive Action | Source: $source")
                        selectedBooking?.let { append(" | Booking: ${it.bookingNo}") }
                        if (notes.isNotBlank()) append("\n${notes.trim()}")
                    }
                    vm.insert(
                        "staff_assignments",
                        mapOf(
                            "staff_id" to pic.id,
                            "title" to actionTitle,
                            "due_date" to deadline,
                            "status" to "Assigned",
                            "priority" to priority,
                            "notes" to metadata,
                            "assigned_by" to session.userId
                        ),
                        "Executive action berhasil didelegasikan."
                    ) { ok, msg -> onNotice(ok, msg) }
                },
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (vm.actionBusy) "Menyimpan…" else "Delegasikan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.actionBusy) { Text("Batal") } }
    )
}

private fun executiveActions(vm: MainViewModel): List<ErpRow> =
    vm.table("staff_assignments").filter { it.text("title").startsWith(EXEC_PREFIX) }

private fun executiveDone(row: ErpRow): Boolean =
    row.text("status") in setOf("Done", "Completed", "Closed", "Resolved")

private fun executiveSla(row: ErpRow): ExecutiveSla {
    if (executiveDone(row)) return ExecutiveSla(ExecutiveSlaLevel.CLOSED, "CLOSED")
    val due = row.text("due_date")
    if (due.isBlank()) return ExecutiveSla(ExecutiveSlaLevel.DUE, "NO DEADLINE")
    val days = executiveDaysUntil(due)
    return when {
        days == null -> ExecutiveSla(ExecutiveSlaLevel.DUE, "CHECK DATE")
        days < 0 -> ExecutiveSla(ExecutiveSlaLevel.ESCALATE, "SLA +${-days} HARI")
        days == 0 -> ExecutiveSla(
            if (row.text("priority").equals("Critical", true)) ExecutiveSlaLevel.ESCALATE else ExecutiveSlaLevel.DUE,
            if (row.text("priority").equals("Critical", true)) "ESCALATE TODAY" else "DUE TODAY"
        )
        days == 1 -> ExecutiveSla(ExecutiveSlaLevel.DUE, "DUE BESOK")
        else -> ExecutiveSla(ExecutiveSlaLevel.OK, "H-$days")
    }
}

private fun executiveSlaSort(level: ExecutiveSlaLevel): Int = when (level) {
    ExecutiveSlaLevel.ESCALATE -> 0
    ExecutiveSlaLevel.DUE -> 1
    ExecutiveSlaLevel.OK -> 2
    ExecutiveSlaLevel.CLOSED -> 3
}

private fun executiveDaysUntil(date: String): Int? = runCatching {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val target = formatter.parse(date) ?: return@runCatching null
    val today = formatter.parse(formatter.format(Date())) ?: return@runCatching null
    ((target.time - today.time).toDouble() / 86_400_000.0).roundToInt()
}.getOrNull()

private fun executiveValidDate(date: String): Boolean = executiveDaysUntil(date) != null

private fun executiveTomorrow(): String {
    val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}
