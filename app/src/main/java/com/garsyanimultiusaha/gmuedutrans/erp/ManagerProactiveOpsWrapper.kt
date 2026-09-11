package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Makes the proactive brief visible from the Manager dashboard without replacing
 * the existing dashboard layout or the Natural Command / Action Agent layers.
 */
@Composable
fun GmuNativeAppWithManagerProactiveAgent(vm: MainViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithManagerNaturalAgent(vm)

        val session = (vm.state as? AppState.LoggedIn)?.session
        if (
            session != null &&
            ErpRoles.isManagerEduTrans(session.profile.role) &&
            vm.currentPage == AppPage.DASHBOARD
        ) {
            ManagerProactiveOpsDock(vm = vm, session = session)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.ManagerProactiveOpsDock(vm: MainViewModel, session: SessionState) {
    var opened by remember { mutableStateOf(false) }
    val activeBookings = vm.bookings.filter {
        it.status !in listOf("Lead", "Quotation", "Completed", "Closed", "Cancelled")
    }
    val approvalCount = vm.table("approvals").count { it.text("status") == "Pending" }
    val missingDocs = activeBookings.count { booking ->
        vm.table("documents").count { it.text("booking_id") == booking.id } < 3
    }
    val missingCoreOps = activeBookings.count { booking ->
        val rundownMissing = vm.table("rundown_items").none { it.text("booking_id") == booking.id }
        val sheetMissing = vm.table("operation_sheets").none { it.text("booking_id") == booking.id }
        rundownMissing || sheetMissing
    }
    val priorityCount = approvalCount + missingDocs + missingCoreOps

    Card(
        onClick = { opened = true },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 18.dp, vertical = 88.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GmuDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = .12f)) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = GmuGold,
                    modifier = Modifier.size(38.dp).padding(9.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Ops Agent Brief", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text(
                    when {
                        activeBookings.isEmpty() -> "Tidak ada trip aktif"
                        priorityCount == 0 -> "${activeBookings.size} trip aktif • operasional terkendali"
                        else -> "$priorityCount prioritas terdeteksi • ${activeBookings.size} trip aktif"
                    },
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 10.sp
                )
            }
            if (priorityCount > 0) {
                Badge(containerColor = GmuDanger, contentColor = Color.White) {
                    Text(priorityCount.coerceAtMost(99).toString())
                }
                Spacer(Modifier.width(7.dp))
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Buka brief", tint = Color.White)
        }
    }

    if (opened) {
        ModalBottomSheet(
            onDismissRequest = { opened = false },
            containerColor = GmuBg,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Manager Operations Command Brief", fontWeight = FontWeight.Black, fontSize = 19.sp, color = GmuDark)
                        Text("Read-only analysis • tindakan tetap melalui modul & approval", fontSize = 10.sp, color = Color.Gray)
                    }
                    TextButton(onClick = { opened = false }) { Text("Tutup") }
                }
                Spacer(Modifier.height(8.dp))
                ManagerProactiveOpsSection(vm = vm, session = session)
            }
        }
    }
}
