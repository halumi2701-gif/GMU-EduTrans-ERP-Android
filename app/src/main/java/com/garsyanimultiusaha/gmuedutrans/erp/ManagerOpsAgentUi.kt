package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Root wrapper that keeps the existing ERP shell intact and adds the Manager EduTrans
 * Ops Agent only on the Manager dashboard.
 */
@Composable
fun GmuNativeAppWithManagerAgent(vm: MainViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeApp(vm)

        val session = (vm.state as? AppState.LoggedIn)?.session
        if (
            session != null &&
            ErpRoles.isManagerEduTrans(session.profile.role) &&
            vm.currentPage == AppPage.DASHBOARD
        ) {
            ManagerOpsAgentDock(vm = vm, session = session)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.ManagerOpsAgentDock(vm: MainViewModel, session: SessionState) {
    var expanded by remember { mutableStateOf(false) }
    val nextTrip = vm.bookings
        .filter { it.status !in listOf("Completed", "Closed") }
        .sortedBy { it.tripDate }
        .firstOrNull()
    val readiness = managerReadiness(vm, nextTrip?.id)

    Card(
        onClick = { expanded = true },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp, vertical = 92.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = GmuDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
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
                    ManagerOpsAgent.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
                Text(
                    if (nextTrip == null) "Siap membantu operasional Manager"
                    else "Next trip ${readiness.score}% ready • ${nextTrip.programName}",
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            AssistChip(
                onClick = { expanded = true },
                label = { Text("Buka", fontWeight = FontWeight.Bold) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color.White,
                    labelColor = GmuDark
                ),
                border = null
            )
        }
    }

    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            containerColor = GmuBg,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            ManagerOpsAgentPanel(
                vm = vm,
                session = session,
                onNavigate = { page ->
                    expanded = false
                    vm.navigate(page)
                }
            )
        }
    }
}

@Composable
private fun ManagerOpsAgentPanel(
    vm: MainViewModel,
    session: SessionState,
    onNavigate: (AppPage) -> Unit
) {
    var command by remember { mutableStateOf("") }
    var agentReply by remember { mutableStateOf("Saya siap membantu menyiapkan dan mengontrol operasional EduTrans.") }

    val nextTrip = vm.bookings
        .filter { it.status !in listOf("Completed", "Closed") }
        .sortedBy { it.tripDate }
        .firstOrNull()
    val readiness = managerReadiness(vm, nextTrip?.id)
    val pendingApprovals = vm.table("approvals").count { it.text("status") == "Pending" }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = GmuDark
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = GmuGold,
                    modifier = Modifier.size(54.dp).padding(13.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ManagerOpsAgent.displayName, fontSize = 20.sp, fontWeight = FontWeight.Black, color = GmuDark)
                Text(
                    "Assistant operasional • ${session.profile.role}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Morning / Daily Ops Brief", fontWeight = FontWeight.Black, color = GmuDark)
                Spacer(Modifier.height(8.dp))
                if (nextTrip == null) {
                    Text("Belum ada trip aktif atau mendatang.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    Text(nextTrip.programName, fontWeight = FontWeight.Bold, color = GmuDark)
                    Text(
                        "${nextTrip.customerName} • ${nextTrip.tripDate} • ${nextTrip.pax} pax",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Trip readiness", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            "${readiness.score}%",
                            fontWeight = FontWeight.Black,
                            color = if (readiness.score >= 80) GmuGreen else GmuWarn
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { readiness.score / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (readiness.score >= 80) GmuGreen else GmuGold,
                        trackColor = GmuSoft
                    )
                    if (readiness.missing.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Perlu perhatian: " + readiness.missing.joinToString(", "),
                            fontSize = 11.sp,
                            color = GmuWarn,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (pendingApprovals > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "$pendingApprovals approval sedang menunggu tindakan.",
                        fontSize = 11.sp,
                        color = GmuWarn,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Perintah cepat", fontSize = 16.sp, fontWeight = FontWeight.Black, color = GmuDark)
        Text("Pilih pekerjaan yang ingin dibantu Ops Agent.", fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(10.dp))

        ManagerOpsAgent.quickActions.chunked(2).forEach { actions ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                actions.forEach { action ->
                    AgentActionCard(
                        title = action,
                        icon = iconForAgentAction(action),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(destinationForAgentAction(action)) }
                    )
                }
                if (actions.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(6.dp))
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Ask Ops Agent", fontWeight = FontWeight.Black, color = GmuDark)
                Text(agentReply, fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Contoh: cek vendor trip besok") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val result = routeManagerCommand(command)
                                agentReply = result.first
                                command = ""
                                result.second?.let(onNavigate)
                            },
                            enabled = command.isNotBlank()
                        ) {
                            Icon(Icons.Rounded.Send, contentDescription = "Kirim")
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E8))
        ) {
            Text(
                "Ops Agent dapat menyiapkan dan menganalisis pekerjaan. Perubahan data penting tetap memerlukan konfirmasi Manager; transaksi di atas Rp2.000.000 atau tindakan strategis naik ke Direktur.",
                modifier = Modifier.padding(14.dp),
                fontSize = 10.sp,
                color = GmuDark
            )
        }
    }
}

private data class ManagerReadiness(
    val score: Int,
    val missing: List<String>
)

private fun managerReadiness(vm: MainViewModel, bookingId: String?): ManagerReadiness {
    if (bookingId.isNullOrBlank()) return ManagerReadiness(0, emptyList())

    val checks = listOf(
        "Rundown" to vm.table("rundown_items").any { it.text("booking_id") == bookingId },
        "Manifest" to vm.table("manifests").any { it.text("booking_id") == bookingId },
        "Operation Sheet" to vm.table("operation_sheets").any { it.text("booking_id") == bookingId },
        "Vendor/PO" to vm.table("vendor_pos").any { it.text("booking_id") == bookingId },
        "Documents" to (vm.table("documents").count { it.text("booking_id") == bookingId } >= 5)
    )
    val completed = checks.count { it.second }
    val score = completed * 100 / checks.size
    return ManagerReadiness(
        score = score,
        missing = checks.filterNot { it.second }.map { it.first }
    )
}

@Composable
private fun AgentActionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp)) {
            Surface(shape = RoundedCornerShape(13.dp), color = GmuSoft) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = GmuGreen,
                    modifier = Modifier.size(38.dp).padding(8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black, color = GmuDark)
            Text("Buka →", fontSize = 10.sp, color = GmuGreen)
        }
    }
}

private fun destinationForAgentAction(action: String): AppPage = when (action) {
    "Siapkan Trip", "Buat Rundown", "Cek Kesiapan", "Buat Operation Sheet" -> AppPage.OPERATIONS
    "Susun Crew" -> AppPage.TEAM_HR
    "Cek Vendor" -> AppPage.VENDORS
    "Analisis RAB" -> AppPage.PLANNING
    "Buat Laporan" -> AppPage.REPORTS
    else -> AppPage.OPERATIONS
}

private fun iconForAgentAction(action: String): ImageVector = when (action) {
    "Siapkan Trip" -> Icons.Rounded.Luggage
    "Buat Rundown" -> Icons.Rounded.Description
    "Cek Kesiapan" -> Icons.Rounded.CheckCircle
    "Susun Crew" -> Icons.Rounded.Groups
    "Cek Vendor" -> Icons.Rounded.Storefront
    "Analisis RAB" -> Icons.Rounded.Assessment
    "Buat Operation Sheet" -> Icons.Rounded.Badge
    "Buat Laporan" -> Icons.Rounded.Description
    else -> Icons.Rounded.AutoAwesome
}

private fun routeManagerCommand(command: String): Pair<String, AppPage?> {
    val normalized = command.lowercase().trim()
    return when {
        normalized.isBlank() -> "Tuliskan perintah operasional terlebih dahulu." to null
        "vendor" in normalized || "po" in normalized ->
            "Saya buka Vendor & PO agar Manager dapat mengecek konfirmasi dan kebutuhan vendor." to AppPage.VENDORS
        "crew" in normalized || "tim" in normalized || "tl" in normalized ->
            "Saya buka Team & HR untuk menyusun atau mengecek assignment crew." to AppPage.TEAM_HR
        "rab" in normalized || "biaya" in normalized || "cost" in normalized ->
            "Saya buka Planning & Control untuk mengecek RAB operasional dan kebutuhan approval." to AppPage.PLANNING
        "laporan" in normalized || "report" in normalized || "evaluasi" in normalized ->
            "Saya buka Reports untuk menyiapkan laporan dan evaluasi trip." to AppPage.REPORTS
        "dokumen" in normalized || "folder" in normalized ->
            "Saya buka Trip Folder untuk mengecek kelengkapan dokumen perjalanan." to AppPage.TRIP_FOLDER
        "rundown" in normalized || "manifest" in normalized || "kesiapan" in normalized || "trip" in normalized || "operation" in normalized ->
            "Saya buka Trip Operation agar Manager dapat melanjutkan persiapan trip." to AppPage.OPERATIONS
        else ->
            "Perintah belum spesifik. Coba sebutkan trip, rundown, crew, vendor, RAB, dokumen, atau laporan." to null
    }
}
