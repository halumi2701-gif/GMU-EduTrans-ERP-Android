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

private enum class ExecutiveSeverity { CRITICAL, DECISION, CONTROL }

private data class ExecutiveException(
    val key: String,
    val severity: ExecutiveSeverity,
    val title: String,
    val detail: String,
    val bookingLabel: String = "",
    val page: AppPage
)

/**
 * Owner-only exception layer. It never writes records automatically; it summarizes
 * high-risk conditions already present in ERP and routes Owner to the responsible module.
 */
@Composable
fun GmuNativeAppWithOwnerExecutiveExceptions(vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithApprovalAuthority(vm)

        val state = vm.state
        if (
            state is AppState.LoggedIn &&
            state.session.profile.role == ErpRoles.OWNER &&
            vm.currentPage == AppPage.DASHBOARD
        ) {
            val exceptions = remember(vm.bookings, vm.erpTablesSnapshotKey()) {
                buildExecutiveExceptions(vm)
            }
            val critical = exceptions.count { it.severity == ExecutiveSeverity.CRITICAL }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 76.dp, end = 16.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (critical > 0) Color(0xFFFFECEC) else Color.White,
                tonalElevation = 5.dp,
                shadowElevation = 5.dp
            ) {
                TextButton(onClick = { open = true }) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Executive Exceptions",
                            fontWeight = FontWeight.Black,
                            color = GmuDark,
                            fontSize = 11.sp
                        )
                        Text(
                            if (exceptions.isEmpty()) "Terkendali" else "${exceptions.size} item • $critical kritis",
                            color = if (critical > 0) GmuDanger else GmuGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            if (open) {
                ExecutiveExceptionDialog(
                    vm = vm,
                    exceptions = exceptions,
                    onDismiss = { open = false },
                    onOpen = { page ->
                        open = false
                        vm.navigate(page)
                    }
                )
            }
        }
    }
}

/**
 * Cheap change key for remember(). It is deliberately derived from data already loaded
 * by MainViewModel; no additional API request is made by the exception center.
 */
private fun MainViewModel.erpTablesSnapshotKey(): String = listOf(
    table("approvals").size,
    table("trip_costs").size,
    table("trip_closings").size,
    table("trip_reports").size,
    table("rundown_items").size,
    table("operation_sheets").size,
    table("staff_assignments").size,
    table("vendor_pos").size,
    table("manifests").size,
    table("documents").size
).joinToString(":")

@Composable
private fun ExecutiveExceptionDialog(
    vm: MainViewModel,
    exceptions: List<ExecutiveException>,
    onDismiss: () -> Unit,
    onOpen: (AppPage) -> Unit
) {
    val critical = exceptions.count { it.severity == ExecutiveSeverity.CRITICAL }
    val decisions = exceptions.count { it.severity == ExecutiveSeverity.DECISION }
    val controls = exceptions.count { it.severity == ExecutiveSeverity.CONTROL }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFF7F8FA)
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Owner Executive Exception Center", fontWeight = FontWeight.Black, fontSize = 19.sp, color = GmuDark)
                        Text(
                            "Hanya exception yang membutuhkan perhatian Owner. Operasional rutin tetap di Manager/Direktur.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExecutiveMetric("Kritis", critical, GmuDanger, Modifier.weight(1f))
                    ExecutiveMetric("Keputusan", decisions, GmuWarn, Modifier.weight(1f))
                    ExecutiveMetric("Kontrol", controls, GmuGreen, Modifier.weight(1f))
                }

                Spacer(Modifier.height(12.dp))
                if (exceptions.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7EF))
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Executive risk terkendali", fontWeight = FontWeight.Black, color = GmuGreen)
                            Text("Tidak ada exception besar dari data ERP yang sedang dimuat.", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(exceptions, key = { it.key }) { item ->
                            ExecutiveExceptionCard(item = item, onOpen = { onOpen(item.page) })
                        }
                    }
                }

                OutlinedButton(
                    onClick = { vm.loadAll() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Refresh Executive Analysis", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ExecutiveMetric(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(value.toString(), fontWeight = FontWeight.Black, fontSize = 20.sp, color = accent)
            Text(label, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun ExecutiveExceptionCard(item: ExecutiveException, onOpen: () -> Unit) {
    val accent = when (item.severity) {
        ExecutiveSeverity.CRITICAL -> GmuDanger
        ExecutiveSeverity.DECISION -> GmuWarn
        ExecutiveSeverity.CONTROL -> GmuGreen
    }
    val label = when (item.severity) {
        ExecutiveSeverity.CRITICAL -> "CRITICAL"
        ExecutiveSeverity.DECISION -> "DECISION"
        ExecutiveSeverity.CONTROL -> "CONTROL"
    }

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Text(item.title, color = GmuDark, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    if (item.bookingLabel.isNotBlank()) {
                        Text(item.bookingLabel, color = GmuGold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
                TextButton(onClick = onOpen) { Text("Buka", fontSize = 10.sp) }
            }
            Text(item.detail, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

private fun buildExecutiveExceptions(vm: MainViewModel): List<ExecutiveException> {
    val result = mutableListOf<ExecutiveException>()
    val approvals = vm.table("approvals")
    val tripCosts = vm.table("trip_costs")
    val tripClosings = vm.table("trip_closings")
    val tripReports = vm.table("trip_reports")

    approvals
        .filter { it.text("status") == "Pending" && ApprovalAuthorityMatrix.requiresDirector(it) }
        .forEach { row ->
            val booking = vm.bookings.firstOrNull { it.id == row.text("booking_id") }
            val amount = ApprovalAuthorityMatrix.amountIdr(row)
            val type = row.text("approval_type")
            result += ExecutiveException(
                key = "approval:${row.id}",
                severity = if (type.equals("Director Escalation", true)) ExecutiveSeverity.CRITICAL else ExecutiveSeverity.DECISION,
                title = if (type.equals("Director Escalation", true)) "Director escalation belum diputuskan" else "Approval strategis menunggu keputusan",
                detail = buildString {
                    append(type.ifBlank { "Approval" })
                    if (amount != null) append(" • Rp${formatExecutiveNumber(amount)}")
                },
                bookingLabel = booking?.let { "${it.bookingNo} • ${it.customerName}" }.orEmpty(),
                page = AppPage.WORKFLOW
            )
        }

    buildExecutiveTripRisk(vm).forEach { risk -> result += risk }

    vm.bookings
        .filter { it.status == "Completed" && executiveDaysFromTrip(it.tripDate) >= 1 }
        .filter { booking -> tripClosings.none { it.text("booking_id") == booking.id } }
        .forEach { booking ->
            val overdue = executiveDaysFromTrip(booking.tripDate)
            result += ExecutiveException(
                key = "closing:${booking.id}",
                severity = if (overdue >= 7) ExecutiveSeverity.CRITICAL else ExecutiveSeverity.CONTROL,
                title = "Trip closing terlambat",
                detail = "Trip selesai $overdue hari lalu tetapi belum mempunyai closing final.",
                bookingLabel = "${booking.bookingNo} • ${booking.customerName}",
                page = AppPage.CLOSING
            )
        }

    tripCosts.groupBy { it.text("booking_id") }.forEach { (bookingId, rows) ->
        if (bookingId.isBlank()) return@forEach
        val rab = rows.sumOf { it.number("rab_amount") }
        val actual = rows.sumOf { it.number("actual_amount") }
        if (rab > 0.0 && actual > rab) {
            val variance = ((actual - rab) / rab) * 100.0
            if (variance >= 20.0) {
                val booking = vm.bookings.firstOrNull { it.id == bookingId }
                result += ExecutiveException(
                    key = "variance:$bookingId",
                    severity = if (variance >= 35.0) ExecutiveSeverity.CRITICAL else ExecutiveSeverity.DECISION,
                    title = "Actual cost melewati RAB",
                    detail = "Actual Rp${formatExecutiveNumber(actual.toLong())} vs RAB Rp${formatExecutiveNumber(rab.toLong())} • +${variance.roundToInt()}%.",
                    bookingLabel = booking?.let { "${it.bookingNo} • ${it.customerName}" }.orEmpty(),
                    page = AppPage.CLOSING
                )
            }
        }
    }

    tripReports
        .filter { row -> executiveIncidentIsMeaningful(row.text("incidents")) }
        .forEach { row ->
            val booking = vm.bookings.firstOrNull { it.id == row.text("booking_id") }
            result += ExecutiveException(
                key = "incident:${row.id}",
                severity = ExecutiveSeverity.DECISION,
                title = "Insiden trip tercatat",
                detail = row.text("incidents").take(180),
                bookingLabel = booking?.let { "${it.bookingNo} • ${it.customerName}" }.orEmpty(),
                page = AppPage.REPORTS
            )
        }

    return result
        .distinctBy { it.key }
        .sortedWith(
            compareBy<ExecutiveException> {
                when (it.severity) {
                    ExecutiveSeverity.CRITICAL -> 0
                    ExecutiveSeverity.DECISION -> 1
                    ExecutiveSeverity.CONTROL -> 2
                }
            }.thenBy { it.title }
        )
}

private fun buildExecutiveTripRisk(vm: MainViewModel): List<ExecutiveException> {
    val rundown = vm.table("rundown_items")
    val opsSheet = vm.table("operation_sheets")
    val assignments = vm.table("staff_assignments")
    val vendorPos = vm.table("vendor_pos")
    val manifests = vm.table("manifests")
    val documents = vm.table("documents")
    val tripCosts = vm.table("trip_costs")

    return vm.bookings
        .filter { it.status !in listOf("Lead", "Quotation", "Completed", "Closed", "Cancelled") }
        .mapNotNull { booking ->
            val days = executiveDaysUntilTrip(booking.tripDate)
            if (days !in 0..1) return@mapNotNull null

            val ready = listOf(
                rundown.any { it.text("booking_id") == booking.id },
                opsSheet.any { it.text("booking_id") == booking.id },
                assignments.any {
                    (it.text("booking_id") == booking.id || it.text("title").contains(booking.bookingNo, true)) &&
                        it.text("status") !in listOf("Cancelled", "Done", "Completed")
                },
                vendorPos.any { it.text("booking_id") == booking.id && it.text("status") !in listOf("Rejected", "Cancelled") },
                manifests.any { it.text("booking_id") == booking.id },
                documents.count { it.text("booking_id") == booking.id } >= 3,
                tripCosts.any { it.text("booking_id") == booking.id }
            ).count { it }

            val readiness = ((ready / 7.0) * 100.0).roundToInt()
            if (readiness >= 80) return@mapNotNull null

            ExecutiveException(
                key = "triprisk:${booking.id}",
                severity = ExecutiveSeverity.CRITICAL,
                title = if (days == 0) "Trip hari ini belum siap" else "Trip besok berisiko",
                detail = "Readiness $readiness% • Owner perlu memastikan Direktur/Manager menutup gap sebelum keberangkatan.",
                bookingLabel = "${booking.bookingNo} • ${booking.customerName}",
                page = AppPage.OPERATIONS
            )
        }
}

private fun executiveIncidentIsMeaningful(value: String): Boolean {
    val normalized = value.trim().lowercase()
    return normalized.isNotBlank() && normalized !in setOf("-", "none", "nihil", "tidak ada", "no incident", "tidak ada insiden")
}

private fun executiveDaysUntilTrip(date: String): Int = runCatching {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val target = formatter.parse(date) ?: return@runCatching 999
    val today = formatter.parse(formatter.format(Date())) ?: return@runCatching 999
    ((target.time - today.time).toDouble() / 86_400_000.0).roundToInt()
}.getOrDefault(999)

private fun executiveDaysFromTrip(date: String): Int {
    val until = executiveDaysUntilTrip(date)
    return if (until == 999) -1 else -until
}

private fun formatExecutiveNumber(value: Long): String = String.format(Locale("id", "ID"), "%,d", value)
