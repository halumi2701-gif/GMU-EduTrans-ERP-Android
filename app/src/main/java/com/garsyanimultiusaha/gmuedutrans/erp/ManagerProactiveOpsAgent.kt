package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Proactive operational brief for Manager EduTrans.
 * It only reads ERP data and routes the Manager to the relevant module.
 * It never creates or changes operational records by itself.
 */
@Composable
fun ManagerProactiveOpsSection(vm: MainViewModel, session: SessionState) {
    val snapshots = buildProactiveTripSnapshots(vm)
    val priorities = buildProactivePriorities(vm, snapshots)
    val priorityTrip = snapshots
        .sortedWith(compareBy<ProactiveTripSnapshot> { it.daysUntil }.thenBy { it.readiness })
        .firstOrNull()

    val criticalCount = snapshots.count {
        it.readiness < 60 || (it.daysUntil <= 1 && it.readiness < 80)
    }
    val attentionCount = priorities.size

    Column(Modifier.padding(horizontal = 16.dp)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = GmuDark)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = .12f)
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = GmuGold,
                            modifier = Modifier.size(44.dp).padding(10.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Proactive Ops Agent",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp
                        )
                        Text(
                            "Brief otomatis dari data operasional ERP",
                            color = Color.White.copy(alpha = .7f),
                            fontSize = 11.sp
                        )
                    }
                    if (criticalCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFFFE4E1)
                        ) {
                            Text(
                                "$criticalCount kritis",
                                color = GmuDanger,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                if (priorityTrip == null) {
                    Text(
                        "Tidak ada trip aktif. Agent tidak menemukan pekerjaan operasional yang mendesak.",
                        color = Color.White.copy(alpha = .82f),
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        proactiveBriefText(priorityTrip, attentionCount),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${priorityTrip.booking.bookingNo} • ${priorityTrip.booking.customerName}",
                                color = GmuGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "${priorityTrip.booking.tripDate} • ${priorityTrip.booking.pax} pax",
                                color = Color.White.copy(alpha = .72f),
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            "${priorityTrip.readiness}% ready",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    LinearProgressIndicator(
                        progress = { priorityTrip.readiness / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (priorityTrip.readiness >= 80) GmuGold else Color(0xFFFFC36A),
                        trackColor = Color.White.copy(alpha = .15f)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (priorities.isEmpty()) Color(0xFFEAF7EF) else Color.White
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (priorities.isEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = if (priorities.isEmpty()) GmuGreen else GmuWarn
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (priorities.isEmpty()) "Operasional terkendali" else "Prioritas Manager hari ini",
                            fontWeight = FontWeight.Black,
                            color = GmuDark,
                            fontSize = 14.sp
                        )
                        Text(
                            if (priorities.isEmpty()) "Tidak ada kekurangan utama pada trip aktif."
                            else "Agent menemukan ${priorities.size} item yang perlu ditindaklanjuti.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                if (priorities.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    priorities.take(4).forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = Color.Black.copy(alpha = .05f))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (item.severity == ProactiveSeverity.CRITICAL) Color(0xFFFFEEEE) else Color(0xFFFFF7E8)
                            ) {
                                Text(
                                    if (item.severity == ProactiveSeverity.CRITICAL) "!" else "•",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    color = if (item.severity == ProactiveSeverity.CRITICAL) GmuDanger else GmuWarn,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.Black, color = GmuDark)
                                Text(item.detail, fontSize = 10.sp, color = Color.Gray)
                            }
                            TextButton(onClick = { vm.navigate(item.page) }) {
                                Text("Buka", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { vm.loadAll() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Refresh analisis Ops Agent", fontSize = 11.sp)
                }
            }
        }
    }
}

private enum class ProactiveSeverity { CRITICAL, ATTENTION }

private data class ProactivePriority(
    val severity: ProactiveSeverity,
    val title: String,
    val detail: String,
    val page: AppPage
)

private data class ProactiveTripSnapshot(
    val booking: Booking,
    val readiness: Int,
    val daysUntil: Int,
    val hasRundown: Boolean,
    val hasOperationSheet: Boolean,
    val hasCrew: Boolean,
    val hasVendor: Boolean,
    val hasManifest: Boolean,
    val hasDocuments: Boolean,
    val hasRab: Boolean,
    val pendingApproval: Boolean
)

private fun buildProactiveTripSnapshots(vm: MainViewModel): List<ProactiveTripSnapshot> {
    val rundownRows = vm.table("rundown_items")
    val operationSheets = vm.table("operation_sheets")
    val assignments = vm.table("staff_assignments")
    val vendorPos = vm.table("vendor_pos")
    val manifests = vm.table("manifests")
    val documents = vm.table("documents")
    val tripCosts = vm.table("trip_costs")
    val approvals = vm.table("approvals")

    return vm.bookings
        .filter { it.status !in listOf("Lead", "Quotation", "Completed", "Closed", "Cancelled") }
        .map { booking ->
            val hasRundown = rundownRows.any { it.text("booking_id") == booking.id }
            val hasOperationSheet = operationSheets.any { it.text("booking_id") == booking.id }
            val hasCrew = assignments.any {
                (it.text("booking_id") == booking.id || it.text("title").contains(booking.bookingNo, ignoreCase = true)) &&
                    it.text("status") !in listOf("Cancelled", "Done", "Completed")
            }
            val hasVendor = vendorPos.any {
                it.text("booking_id") == booking.id && it.text("status") !in listOf("Rejected", "Cancelled")
            }
            val hasManifest = manifests.any { it.text("booking_id") == booking.id }
            val docCount = documents.count { it.text("booking_id") == booking.id }
            val hasDocuments = docCount >= 3
            val hasRab = tripCosts.any { it.text("booking_id") == booking.id }
            val pendingApproval = approvals.any {
                it.text("booking_id") == booking.id && it.text("status") == "Pending"
            }

            val score = listOf(
                hasRundown to 15,
                hasOperationSheet to 15,
                hasCrew to 15,
                hasVendor to 15,
                hasManifest to 10,
                hasDocuments to 15,
                hasRab to 15
            ).sumOf { (ready, weight) -> if (ready) weight else 0 }

            ProactiveTripSnapshot(
                booking = booking,
                readiness = score.coerceIn(0, 100),
                daysUntil = daysUntilTrip(booking.tripDate),
                hasRundown = hasRundown,
                hasOperationSheet = hasOperationSheet,
                hasCrew = hasCrew,
                hasVendor = hasVendor,
                hasManifest = hasManifest,
                hasDocuments = hasDocuments,
                hasRab = hasRab,
                pendingApproval = pendingApproval
            )
        }
}

private fun buildProactivePriorities(
    vm: MainViewModel,
    snapshots: List<ProactiveTripSnapshot>
): List<ProactivePriority> {
    val result = mutableListOf<ProactivePriority>()
    val ordered = snapshots.sortedWith(compareBy<ProactiveTripSnapshot> { it.daysUntil }.thenBy { it.readiness })

    ordered.forEach { snapshot ->
        val urgent = snapshot.daysUntil <= 1
        val tripLabel = "${snapshot.booking.bookingNo} • ${snapshot.booking.customerName}"

        if (!snapshot.hasRundown) {
            result += ProactivePriority(
                if (urgent) ProactiveSeverity.CRITICAL else ProactiveSeverity.ATTENTION,
                "Rundown belum siap",
                "$tripLabel • ${dayLabel(snapshot.daysUntil)}",
                AppPage.OPERATIONS
            )
        }
        if (!snapshot.hasOperationSheet) {
            result += ProactivePriority(
                if (urgent) ProactiveSeverity.CRITICAL else ProactiveSeverity.ATTENTION,
                "Operation Sheet belum tersedia",
                "$tripLabel • ${dayLabel(snapshot.daysUntil)}",
                AppPage.OPERATIONS
            )
        }
        if (!snapshot.hasCrew) {
            result += ProactivePriority(
                if (snapshot.daysUntil <= 2) ProactiveSeverity.CRITICAL else ProactiveSeverity.ATTENTION,
                "TL / crew belum assigned",
                "$tripLabel • perlu penanggung jawab trip",
                AppPage.TEAM_HR
            )
        }
        if (!snapshot.hasVendor) {
            result += ProactivePriority(
                if (snapshot.daysUntil <= 2) ProactiveSeverity.CRITICAL else ProactiveSeverity.ATTENTION,
                "Vendor / PO belum terkonfirmasi",
                "$tripLabel • cek kebutuhan vendor & transport",
                AppPage.VENDORS
            )
        }
        if (!snapshot.hasManifest) {
            result += ProactivePriority(
                if (urgent) ProactiveSeverity.CRITICAL else ProactiveSeverity.ATTENTION,
                "Manifest peserta belum lengkap",
                "$tripLabel • data peserta diperlukan sebelum hari H",
                AppPage.OPERATIONS
            )
        }
        if (!snapshot.hasDocuments) {
            result += ProactivePriority(
                if (urgent) ProactiveSeverity.CRITICAL else ProactiveSeverity.ATTENTION,
                "Trip Folder belum lengkap",
                "$tripLabel • dokumen operasional masih kurang",
                AppPage.TRIP_FOLDER
            )
        }
        if (!snapshot.hasRab) {
            result += ProactivePriority(
                if (snapshot.daysUntil <= 2) ProactiveSeverity.CRITICAL else ProactiveSeverity.ATTENTION,
                "RAB operasional belum tercatat",
                "$tripLabel • siapkan kebutuhan biaya trip",
                AppPage.OPERATIONS
            )
        }
        if (snapshot.pendingApproval) {
            result += ProactivePriority(
                if (urgent) ProactiveSeverity.CRITICAL else ProactiveSeverity.ATTENTION,
                "Approval masih menunggu",
                "$tripLabel • ada keputusan yang belum diselesaikan",
                AppPage.WORKFLOW
            )
        }
    }

    return result.sortedWith(
        compareBy<ProactivePriority> { if (it.severity == ProactiveSeverity.CRITICAL) 0 else 1 }
            .thenBy { it.title }
    )
}

private fun proactiveBriefText(snapshot: ProactiveTripSnapshot, attentionCount: Int): String {
    val timing = dayLabel(snapshot.daysUntil)
    return when {
        snapshot.readiness >= 90 && attentionCount == 0 -> "Operasional siap. Trip prioritas $timing sudah ${snapshot.readiness}% lengkap."
        snapshot.daysUntil <= 1 && snapshot.readiness < 80 -> "Prioritas tinggi: trip $timing baru ${snapshot.readiness}% siap. Selesaikan item kritis sebelum keberangkatan."
        attentionCount > 0 -> "Ada $attentionCount pekerjaan operasional. Trip prioritas $timing berada di ${snapshot.readiness}% readiness."
        else -> "Trip prioritas $timing berada di ${snapshot.readiness}% readiness."
    }
}

private fun dayLabel(daysUntil: Int): String = when {
    daysUntil < 0 -> "melewati jadwal ${-daysUntil} hari"
    daysUntil == 0 -> "hari ini"
    daysUntil == 1 -> "besok"
    daysUntil == 2 -> "lusa"
    else -> "H-$daysUntil"
}

private fun daysUntilTrip(date: String): Int {
    return runCatching {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val target = formatter.parse(date) ?: return@runCatching 999
        val todayString = formatter.format(Date())
        val today = formatter.parse(todayString) ?: return@runCatching 999
        ((target.time - today.time).toDouble() / 86_400_000.0).roundToInt()
    }.getOrDefault(999)
}
