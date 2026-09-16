package com.garsyanimultiusaha.gmuedutrans.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val GmuGreen = Color(0xFF168400)
private val GmuGreenDark = Color(0xFF0B5F10)
private val GmuGold = Color(0xFFD7A600)
private val GmuSurface = Color(0xFFF6F8F4)
private val GmuInk = Color(0xFF17301D)

@Composable
fun SalesApp(vm: SalesViewModel) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = GmuGreen,
            secondary = GmuGold,
            background = GmuSurface,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = GmuInk,
            onSurface = GmuInk
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val state = vm.state) {
                SalesAppState.Splash -> SplashScreen()
                SalesAppState.LoggedOut -> LoginScreen(vm)
                SalesAppState.Loading -> LoadingScreen()
                is SalesAppState.Error -> ErrorScreen(state.message, vm::backToLogin)
                is SalesAppState.LoggedIn -> MainSalesShell(vm, state.session)
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark()
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(color = GmuGreen, strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text("Sales App", fontWeight = FontWeight.Bold, color = GmuGreenDark)
        }
    }
}

@Composable
private fun BrandMark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("GMU", fontSize = 44.sp, fontWeight = FontWeight.Black, color = GmuGreen)
        Text("EduTrans", fontSize = 25.sp, fontWeight = FontWeight.Black, color = GmuGold)
    }
}

@Composable
private fun LoginScreen(vm: SalesViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(26.dp).widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark()
                Spacer(Modifier.height(8.dp))
                Text("Aplikasi khusus tim Sales", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email Sales") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { vm.login(email, password) },
                    enabled = email.isNotBlank() && password.isNotBlank() && !vm.actionBusy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Masuk sebagai Sales", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(12.dp))
                Text("Target utama: 400 paid pax / bulan", color = GmuGreenDark, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = GmuGreen)
            Spacer(Modifier.height(12.dp))
            Text("Memuat data Sales GMU EduTrans…", color = Color.Gray)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Login belum berhasil", fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text(message, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Kembali") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSalesShell(vm: SalesViewModel, session: SalesSession) {
    val tabs = listOf(
        SalesPage.DASHBOARD to Pair(Icons.Default.Dashboard, "Home"),
        SalesPage.LEADS to Pair(Icons.Default.Groups, "Lead"),
        SalesPage.FOLLOW_UP to Pair(Icons.Default.Schedule, "Follow-up"),
        SalesPage.FUNNEL to Pair(Icons.Default.Assessment, "Funnel"),
        SalesPage.EARNINGS to Pair(Icons.Default.AttachMoney, "Komisi")
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GMU EduTrans Sales", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(session.profile.fullName, fontSize = 11.sp, color = Color.Gray)
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !vm.dataBusy) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { vm.navigate(SalesPage.PROFILE) }) { Icon(Icons.Default.AccountCircle, "Profil") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { (page, item) ->
                    NavigationBarItem(
                        selected = vm.currentPage == page,
                        onClick = { vm.navigate(page) },
                        icon = { Icon(item.first, item.second) },
                        label = { Text(item.second, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            vm.notice?.let {
                AssistChip(
                    onClick = vm::consumeNotice,
                    label = { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            when (vm.currentPage) {
                SalesPage.DASHBOARD -> DashboardScreen(vm.dashboard)
                SalesPage.LEADS -> LeadsScreen(vm)
                SalesPage.FOLLOW_UP -> FollowUpScreen(vm)
                SalesPage.FUNNEL -> FunnelScreen(vm.dashboard)
                SalesPage.BOOKINGS -> BookingScreen(vm.dashboard)
                SalesPage.EARNINGS -> EarningsScreen(vm.dashboard)
                SalesPage.PROFILE -> ProfileScreen(session, vm)
            }
        }
    }
}

@Composable
private fun DashboardScreen(data: SalesDashboard) {
    val p = data.portfolio
    val f = data.forecast
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = GmuGreenDark)) {
                Column(Modifier.padding(20.dp)) {
                    Text("TARGET BULAN INI", color = Color.White.copy(alpha = .72f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${p.paidPax} / ${p.targetPaidPax} paid pax", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = (p.paidPax.toFloat() / p.targetPaidPax.coerceAtLeast(1)).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().height(9.dp),
                        color = GmuGold,
                        trackColor = Color.White.copy(alpha = .18f)
                    )
                    Spacer(Modifier.height(9.dp))
                    Text("Sisa ${f.remainingPax} pax • ${f.remainingDaysInMonth} hari kalender tersisa", color = Color.White.copy(alpha = .86f), fontSize = 12.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Paid booking", p.paidBookings.toString(), Modifier.weight(1f))
                MetricCard("Pipeline", "${f.rawOpenPipelinePax} pax", Modifier.weight(1f))
                MetricCard("HOT lead", data.hotLeads.size.toString(), Modifier.weight(1f))
            }
        }
        item { ForecastCard(f) }
        item {
            SectionHeader("Prioritas hari ini", "Follow-up yang jatuh tempo dan lead terdekat ke closing")
        }
        if (data.followUpsDue.isEmpty() && data.hotLeads.isEmpty()) {
            item { EmptyCard("Belum ada follow-up overdue atau HOT lead. Tambahkan prospek baru agar pipeline tetap terisi.") }
        } else {
            items((data.followUpsDue + data.hotLeads).distinctBy { it.id }.take(6), key = { it.id }) { lead -> LeadCard(lead, null) }
        }
        item {
            SectionHeader("Paid pax per program", "Hanya performa Sales sendiri, tanpa laba dan keuangan perusahaan")
        }
        if (data.programs.isEmpty()) item { EmptyCard("Belum ada paid pax bulan ini.") }
        else items(data.programs, key = { it.programName }) { row ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(row.programName, fontWeight = FontWeight.Bold)
                        Text("${row.paidBookings} booking", fontSize = 11.sp, color = Color.Gray)
                    }
                    Text("${row.paidPax} pax", color = GmuGreen, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ForecastCard(f: SalesForecast) {
    val label = when (f.status) {
        ForecastStatus.TARGET_REACHED -> "TARGET TERCAPAI"
        ForecastStatus.ON_TRACK -> "ON TRACK"
        ForecastStatus.PIPELINE_CAN_COVER -> "PIPELINE CUKUP"
        ForecastStatus.AT_RISK -> "PERLU PERCEPATAN"
        ForecastStatus.PIPELINE_INSUFFICIENT -> "PIPELINE BELUM CUKUP"
    }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Forecast Target", fontWeight = FontWeight.Black, fontSize = 17.sp)
                Surface(shape = RoundedCornerShape(100.dp), color = GmuGold.copy(alpha = .16f)) {
                    Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = GmuGreenDark, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(f.message, color = Color.Gray, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("Weighted forecast", String.format(Locale.US, "%.0f pax", f.weightedForecastPax), Modifier.weight(1f))
                MiniStat("Pace projection", String.format(Locale.US, "%.0f pax", f.paceProjectionPax), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text("Kebutuhan rata-rata: ${String.format(Locale.US, "%.1f", f.requiredPaidPaxPerDay)} paid pax/hari sampai akhir bulan.", fontSize = 11.sp, color = GmuGreenDark, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LeadsScreen(vm: SalesViewModel) {
    var filter by remember { mutableStateOf("ALL") }
    var selected by remember { mutableStateOf<SalesLead?>(null) }
    val stages = listOf("ALL", "NEW", "CONTACTED", "QUALIFIED", "QUOTATION", "NEGOTIATION", "WAITING_DP", "WON", "LOST")
    val rows = vm.dashboard.leads.filter { filter == "ALL" || it.stage == filter }
    Column(Modifier.fillMaxSize()) {
        SectionHeader("CRM Lead", "Semua prospek milik Sales ini", Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        ScrollableTabRow(selectedTabIndex = stages.indexOf(filter).coerceAtLeast(0), edgePadding = 12.dp) {
            stages.forEach { stage -> Tab(selected = filter == stage, onClick = { filter = stage }, text = { Text(stage.replace('_', ' '), fontSize = 10.sp) }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rows.isEmpty()) item { EmptyCard("Belum ada lead pada tahap ini.") }
            items(rows, key = { it.id }) { lead -> LeadCard(lead) { selected = lead } }
        }
    }
    selected?.let { lead ->
        LeadUpdateDialog(lead, vm.actionBusy, onDismiss = { selected = null }) { stage, date ->
            vm.updateLead(lead, stage, date)
            selected = null
        }
    }
}

@Composable
private fun FollowUpScreen(vm: SalesViewModel) {
    var selected by remember { mutableStateOf<SalesLead?>(null) }
    val due = vm.dashboard.followUpsDue
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeader("Follow-up Hari Ini", "Jangan biarkan prospek aktif tanpa next action") }
        if (due.isEmpty()) item { EmptyCard("Tidak ada follow-up jatuh tempo saat ini.") }
        else items(due, key = { it.id }) { lead -> LeadCard(lead) { selected = lead } }
        item { SectionHeader("HOT Lead", "Quotation, negosiasi, dan menunggu DP") }
        if (vm.dashboard.hotLeads.isEmpty()) item { EmptyCard("Belum ada HOT lead. Dorong qualified lead menuju quotation.") }
        else items(vm.dashboard.hotLeads, key = { "hot-${it.id}" }) { lead -> LeadCard(lead) { selected = lead } }
    }
    selected?.let { lead ->
        LeadUpdateDialog(lead, vm.actionBusy, onDismiss = { selected = null }) { stage, date ->
            vm.updateLead(lead, stage, date)
            selected = null
        }
    }
}

@Composable
private fun FunnelScreen(data: SalesDashboard) {
    val funnel = data.funnel
    val leads = data.leads
    val actualProspects = leads.size
    val actualQualified = leads.count { it.stage in setOf("QUALIFIED", "QUOTATION", "NEGOTIATION", "WAITING_DP", "WON") }
    val actualQuotes = leads.count { it.stage in setOf("QUOTATION", "NEGOTIATION", "WAITING_DP", "WON") }
    val actualWon = leads.count { it.stage == "WON" }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("Sales Funnel 400 Pax", "Kebutuhan dihitung mundur dari target paid pax") }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    FunnelRow("Prospek", actualProspects, funnel.requiredProspects)
                    FunnelRow("Qualified", actualQualified, funnel.requiredQualifiedLeads)
                    FunnelRow("Quotation", actualQuotes, funnel.requiredQuotations)
                    FunnelRow("Won booking", actualWon, funnel.requiredWonBookings)
                    FunnelRow("Paid pax", data.portfolio.paidPax, funnel.targetPaidPax)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = GmuGold.copy(alpha = .11f))) {
                Column(Modifier.padding(18.dp)) {
                    Text("Asumsi funnel", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(7.dp))
                    Text("Rata-rata ${String.format(Locale.US, "%.0f", funnel.assumedAveragePaxPerWon)} pax per sekolah closing.", fontSize = 12.sp)
                    Text("Prospek → Qualified ${funnel.prospectToQualifiedPct.toInt()}%", fontSize = 12.sp)
                    Text("Qualified → Quotation ${funnel.qualifiedToQuotationPct.toInt()}%", fontSize = 12.sp)
                    Text("Quotation → Won ${funnel.quotationToWonPct.toInt()}%", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Dengan asumsi ini, target 400 pax membutuhkan sekitar ${funnel.requiredProspects} prospek → ${funnel.requiredQualifiedLeads} qualified → ${funnel.requiredQuotations} quotation → ${funnel.requiredWonBookings} sekolah closing.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GmuGreenDark)
                }
            }
        }
    }
}

@Composable
private fun BookingScreen(data: SalesDashboard) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeader("Booking Saya", "Booking yang teratribusi ke Sales ini") }
        if (data.bookings.isEmpty()) item { EmptyCard("Belum ada booking.") }
        else items(data.bookings, key = { it.id }) { b ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(b.bookingNo, fontWeight = FontWeight.Black)
                        StageChip(b.status.uppercase())
                    }
                    Text(b.programName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (b.customerName.isNotBlank()) Text(b.customerName, fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(6.dp))
                    Text("${b.pax} pax • ${b.tripDate}", color = GmuGreen, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EarningsScreen(data: SalesDashboard) {
    val p = data.portfolio
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("Komisi Saya", "Tidak menampilkan laba, HPP, saldo, atau keuangan perusahaan") }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = GmuGreenDark)) {
                Column(Modifier.padding(20.dp)) {
                    Text("ESTIMASI PENGHASILAN BULAN INI", color = Color.White.copy(alpha = .72f), fontSize = 10.sp)
                    Text(rupiah(p.modeledSalesIncome), color = Color.White, fontWeight = FontWeight.Black, fontSize = 29.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("${p.paidPax} paid pax • ${p.achievementLevel.replace('_', ' ')}", color = Color.White.copy(alpha = .84f), fontSize = 12.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    EarningsLine("Fixed / retainer", p.salesRetainer)
                    EarningsLine("Komisi ${p.paidPax} pax × ${rupiah(p.salesFeePerPaidPax)}", p.variableSalesFee)
                    EarningsLine("Bonus target earned", p.targetBonusEarned)
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total modeled", fontWeight = FontWeight.Black)
                        Text(rupiah(p.modeledSalesIncome), fontWeight = FontWeight.Black, color = GmuGreen)
                    }
                }
            }
        }
        item { EmptyCard("Komisi dihitung dari paid pax yang sudah teratribusi ke Sales. Angka ini bukan laporan keuangan perusahaan.") }
    }
}

@Composable
private fun ProfileScreen(session: SalesSession, vm: SalesViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("Profil Sales", "Akun kerja GMU EduTrans") }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text(session.profile.fullName, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("Sales / Pengembangan Bisnis", color = GmuGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("Akses aplikasi dibatasi pada target, CRM, follow-up, funnel, booking sendiri dan komisi Sales.", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(18.dp))
                    OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Text("Keluar dari Sales App") }
                }
            }
        }
    }
}

@Composable
private fun LeadCard(lead: SalesLead, onClick: (() -> Unit)?) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(lead.institutionName, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                StageChip(lead.stage)
            }
            if (lead.picName.isNotBlank()) Text("PIC: ${lead.picName}", fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${lead.pax} pax", color = GmuGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${lead.probabilityPct.toInt()}%", color = GmuGold, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            if (lead.nextFollowUpAt.isNotBlank()) Text("Follow-up: ${lead.nextFollowUpAt.take(16).replace('T', ' ')}", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun LeadUpdateDialog(
    lead: SalesLead,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    var stage by remember(lead.id) { mutableStateOf(lead.stage) }
    var next by remember(lead.id) { mutableStateOf(lead.nextFollowUpAt) }
    val stages = listOf("NEW", "CONTACTED", "QUALIFIED", "QUOTATION", "NEGOTIATION", "WAITING_DP", "WON", "LOST", "NURTURE")
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(lead.institutionName, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("${lead.pax} pax • ${lead.bookingCode}", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text("Tahap lead", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                stages.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { value ->
                            FilterChip(selected = stage == value, onClick = { stage = value }, label = { Text(value.replace('_', ' '), fontSize = 9.sp) })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Next follow-up", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 7).forEach { days ->
                        AssistChip(
                            onClick = {
                                next = LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(days.toLong()).atTime(9, 0).atZone(ZoneId.of("Asia/Jakarta")).toOffsetDateTime().toString()
                            },
                            label = { Text("+$days hari") }
                        )
                    }
                }
                if (next.isNotBlank()) Text(next.take(16).replace('T', ' '), color = GmuGreenDark, fontSize = 11.sp)
            }
        },
        confirmButton = { Button(onClick = { onSave(stage, next.ifBlank { null }) }, enabled = !busy) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun FunnelRow(label: String, actual: Int, required: Int) {
    val pct = if (required <= 0) 0f else (actual.toFloat() / required).coerceIn(0f, 1f)
    Column(Modifier.padding(vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Bold)
            Text("$actual / $required", color = GmuGreen, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(progress = pct, modifier = Modifier.fillMaxWidth().height(7.dp), color = GmuGreen, trackColor = GmuGreen.copy(alpha = .12f))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text(label, fontSize = 9.sp, color = Color.Gray)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = GmuInk)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = GmuSurface) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 9.sp, color = Color.Gray)
            Text(value, fontWeight = FontWeight.Black, color = GmuGreenDark)
        }
    }
}

@Composable
private fun StageChip(stage: String) {
    Surface(shape = RoundedCornerShape(100.dp), color = if (stage in setOf("WON", "CLOSED", "COMPLETED")) GmuGreen.copy(alpha = .13f) else GmuGold.copy(alpha = .14f)) {
        Text(stage.replace('_', ' '), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GmuGreenDark)
    }
}

@Composable
private fun EarningsLine(label: String, value: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(rupiah(value), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 19.sp, color = GmuInk)
        Text(subtitle, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Text(message, modifier = Modifier.padding(16.dp), color = Color.Gray, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

private fun rupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")
