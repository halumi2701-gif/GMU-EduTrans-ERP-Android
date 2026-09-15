package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen(vm: MainViewModel, session: SessionState) {
    val s = vm.dashboardStats()
    val financeVisible = FinancialAccess.canView(session.profile.role)
    val management = if (financeVisible) vm.managementDashboard else null
    val needsAttention = buildList {
        val approvalCount = vm.table("approvals").count { it.text("status") == "Pending" }
        if (approvalCount > 0) add(approvalCount.toString() + " approval menunggu")
        val receivableTrips = vm.bookings.count { b ->
            b.omzet > vm.paidForBooking(b.id) && b.status !in listOf("Lead", "Quotation", "Closed")
        }
        if (financeVisible && receivableTrips > 0) add(receivableTrips.toString() + " booking masih memiliki piutang")
        val missingDocs = vm.bookings.count { b ->
            val count = vm.table("documents").count { it.text("booking_id") == b.id }
            b.status in listOf("Confirmed", "Preparation", "Trip") && count < 5
        }
        if (missingDocs > 0) add(missingDocs.toString() + " trip perlu kelengkapan dokumen")
        if (financeVisible && management != null) {
            if (management.criticalTotal > 0) add(management.criticalTotal.toString() + " management exception kritis")
            if (management.warningTotal > 0) add(management.warningTotal.toString() + " management warning")
        }
    }
    val nextTrip = vm.bookings
        .filter { it.status !in listOf("Completed", "Closed") }
        .sortedBy { it.tripDate }
        .firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(GmuDark, GmuGreen))
                    )
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                Text(
                    "Good " + greetingLabel() + ", " + session.profile.fullName.substringBefore(" ") + " 👋",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    session.profile.role + " • GMU EduTrans",
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(24.dp))
                if (financeVisible) {
                    Text("Omzet bulan ini", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
                    Text(rupiah(s.omzet), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StartupStatPill(s.bookingsMonth.toString() + " booking")
                        StartupStatPill(s.pax.toString() + " pax")
                        StartupStatPill(String.format("%.1f%% margin", s.margin))
                    }
                } else {
                    Text("Operasional hari ini", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
                    Text(s.upcoming.toString() + " trip mendatang", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StartupStatPill(s.bookingsMonth.toString() + " booking")
                        StartupStatPill(s.pax.toString() + " pax")
                    }
                }
            }
        }

        if (vm.dataBusy) {
            item {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    color = GmuGreen
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Business snapshot", fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Booking", s.bookingsMonth.toString(), Modifier.weight(1f))
                    MetricCard("Pax", s.pax.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Upcoming", s.upcoming.toString(), Modifier.weight(1f))
                    MetricCard("Customer", s.customers.toString(), Modifier.weight(1f))
                }
                if (financeVisible) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("Piutang", rupiah(s.receivable), Modifier.weight(1f))
                        MetricCard("Laba", rupiah(s.profit), Modifier.weight(1f), accent = true)
                    }
                }
            }
        }

        if (financeVisible && management != null) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Management control", fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark)
                    Text("v" + management.version + " • authoritative management data", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("Committed", rupiah(management.kpi.committedCost), Modifier.weight(1f))
                        MetricCard("Actual", rupiah(management.kpi.actualCost), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("Cash 30D", rupiah(management.cashForecast.projected30d), Modifier.weight(1f))
                        MetricCard("Exception", management.exceptionTotal.toString(), Modifier.weight(1f), accent = management.criticalTotal > 0)
                    }
                    Spacer(Modifier.height(10.dp))
                    Card(
                        onClick = { vm.navigate(AppPage.FINANCE) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (management.criticalTotal > 0) Color(0xFFFFEEEE) else Color(0xFFEAF7EF)
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("Budget & cash control", fontWeight = FontWeight.Black, color = GmuDark)
                                    Text(
                                        "RAB " + rupiah(management.kpi.rab) +
                                            " • Actual " + rupiah(management.kpi.actualCost),
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                StatusChip(if (management.criticalTotal > 0) "Attention" else "Healthy")
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "AR " + rupiah(management.kpi.receivable) +
                                    " • Cash forecast 30D " + rupiah(management.cashForecast.projected30d),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (management.exceptionTotal > 0) {
                                Spacer(Modifier.height(8.dp))
                                management.exceptions.take(3).forEach { ex ->
                                    Text(
                                        "• " + ex.message,
                                        fontSize = 11.sp,
                                        color = if (ex.severity == "CRITICAL") GmuWarn else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (financeVisible && vm.managementError != null) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E8))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Management Control belum termuat", fontWeight = FontWeight.Black, color = GmuDark)
                            Text(vm.managementError.orEmpty(), fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Needs attention", fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark)
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (needsAttention.isEmpty()) Color(0xFFEAF7EF) else Color(0xFFFFF7E8)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (needsAttention.isEmpty()) {
                            Text("All clear", fontWeight = FontWeight.Black, color = GmuGreen)
                            Text("Tidak ada item kritis yang perlu tindakan saat ini.", fontSize = 12.sp, color = Color.Gray)
                        } else {
                            needsAttention.take(4).forEachIndexed { index, item ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text("•", color = GmuWarn, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.width(8.dp))
                                    Text(item, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (index < needsAttention.take(4).lastIndex) {
                                    HorizontalDivider(color = Color.Black.copy(alpha = .05f))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Next trip", fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark)
                Spacer(Modifier.height(8.dp))
                if (nextTrip == null) {
                    EmptyCard("Belum ada trip mendatang.")
                } else {
                    val trip = vm.table("trips").firstOrNull { it.text("booking_id") == nextTrip.id }
                    val progress = trip?.int("operational_progress") ?: 0
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(nextTrip.programName, fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark)
                                    Text(nextTrip.customerName, fontSize = 12.sp, color = Color.Gray)
                                }
                                StatusChip(nextTrip.status)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(nextTrip.tripDate + " • " + nextTrip.pax + " pax", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Operation readiness", fontSize = 11.sp, color = Color.Gray)
                                Text(progress.toString() + "%", fontWeight = FontWeight.Black, color = GmuGreen)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0, 100) / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = GmuGreen,
                                trackColor = GmuSoft
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Quick actions", fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StartupActionCard(
                        title = "Booking",
                        subtitle = "Pipeline & order",
                        modifier = Modifier.weight(1f),
                        onClick = { vm.navigate(AppPage.BOOKINGS) }
                    )
                    if (AppPage.OPERATIONS in RoleAccess.pages(session.profile.role)) {
                        StartupActionCard(
                            title = "Trip Control",
                            subtitle = "Readiness & TL",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.navigate(AppPage.OPERATIONS) }
                        )
                    } else {
                        StartupActionCard(
                            title = "Customer",
                            subtitle = "CRM & history",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.navigate(AppPage.CUSTOMERS) }
                        )
                    }
                }
            }
        }

        if (session.profile.role in listOf("Owner", "Manager")) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Performance insights", fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark)
                    Spacer(Modifier.height(8.dp))
                    RankingCard("Top Program", s.topPrograms)
                    Spacer(Modifier.height(10.dp))
                    RankingCard("Top Customer", s.topCustomers)
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { vm.loadAll() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Refresh dashboard")
            }
        }
    }
}

@Composable
private fun StartupStatPill(text: String) {
    Surface(
        color = Color.White.copy(alpha = .14f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun StartupActionCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = GmuDark, fontWeight = FontWeight.Black)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(12.dp))
            Text("Open  →", color = GmuGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun greetingLabel(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..10 -> "Morning"
        in 11..15 -> "Afternoon"
        else -> "Evening"
    }
}

@Composable
private fun RankingCard(title: String, values: List<Pair<String, Double>>) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Black, color = GmuDark)
            Spacer(Modifier.height(8.dp))
            if (values.isEmpty()) Text("Belum ada data.", color = Color.Gray, fontSize = 12.sp)
            values.forEachIndexed { i, pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text((i + 1).toString() + ". " + pair.first, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(rupiah(pair.second), fontSize = 11.sp, color = GmuGreen)
                }
                if (i < values.lastIndex) HorizontalDivider(Modifier.padding(vertical = 7.dp))
            }
        }
    }
}
