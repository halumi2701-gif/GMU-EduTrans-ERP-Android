package com.garsyanimultiusaha.gmuedutrans.sales

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val V2Green = Color(0xFF168400)
private val V2GreenDark = Color(0xFF0B5F10)
private val V2Gold = Color(0xFFD7A600)
private val V2Surface = Color(0xFFF6F8F4)
private val V2Ink = Color(0xFF17301D)

@Composable
fun SalesAppV2(vm: SalesViewModel) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = V2Green,
            secondary = V2Gold,
            background = V2Surface,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = V2Ink,
            onSurface = V2Ink
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val state = vm.state) {
                SalesAppState.Splash -> V2Splash()
                SalesAppState.LoggedOut -> V2Login(vm)
                SalesAppState.Loading -> V2Loading()
                is SalesAppState.Error -> V2Error(state.message, vm::backToLogin)
                is SalesAppState.LoggedIn -> V2Shell(vm, state.session)
            }
        }
    }
}

@Composable
private fun V2Brand(modifier: Modifier = Modifier) {
    // Use the raster logo directly. Compose painterResource does not support
    // arbitrary XML LayerDrawable/layer-list resources and could crash at startup.
    Image(
        painter = painterResource(R.drawable.sales_logo),
        contentDescription = "GMU EduTrans",
        modifier = modifier.widthIn(max = 235.dp).heightIn(max = 145.dp)
    )
}

@Composable
private fun V2Splash() {
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            V2Brand(Modifier.width(220.dp).height(140.dp))
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(color = V2Green, strokeWidth = 3.dp)
            Spacer(Modifier.height(10.dp))
            Text("Sales App", fontWeight = FontWeight.Black, color = V2GreenDark)
        }
    }
}

@Composable
private fun V2Login(vm: SalesViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(24.dp).widthIn(max = 430.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                V2Brand(Modifier.width(190.dp).height(120.dp))
                Text("Aplikasi kerja khusus tim Sales", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Sales") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.login(email, password) },
                    enabled = email.isNotBlank() && password.isNotBlank() && !vm.actionBusy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp)
                ) { Text("Masuk sebagai Sales", fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(12.dp))
                Text("Target utama 400 paid pax / bulan", color = V2GreenDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun V2Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = V2Green)
            Spacer(Modifier.height(10.dp))
            Text("Memuat workspace Sales…", color = Color.Gray)
        }
    }
}

@Composable
private fun V2Error(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Login belum berhasil", fontWeight = FontWeight.Black, fontSize = 19.sp)
                Spacer(Modifier.height(7.dp))
                Text(message, color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Button(onClick = onBack) { Text("Kembali") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V2Shell(vm: SalesViewModel, session: SalesSession) {
    val tabs = listOf(
        SalesPage.DASHBOARD to Pair(Icons.Default.Dashboard, "Home"),
        SalesPage.LEADS to Pair(Icons.Default.Groups, "Lead"),
        SalesPage.FOLLOW_UP to Pair(Icons.Default.Schedule, "Follow-up"),
        SalesPage.FUNNEL to Pair(Icons.Default.Assessment, "Sales Kit"),
        SalesPage.EARNINGS to Pair(Icons.Default.AttachMoney, "Komisi")
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GMU EduTrans Sales", fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Text(session.profile.fullName, fontSize = 10.sp, color = Color.Gray)
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
                        label = { Text(item.second, fontSize = 9.sp) }
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
                SalesPage.DASHBOARD -> V2Dashboard(vm)
                SalesPage.LEADS -> V2Leads(vm)
                SalesPage.FOLLOW_UP -> V2FollowUp(vm)
                SalesPage.FUNNEL -> MarketingKitScreen(vm)
                SalesPage.BOOKINGS -> V2Bookings(vm.dashboard)
                SalesPage.EARNINGS -> V2Earnings(vm.dashboard)
                SalesPage.PROFILE -> V2Profile(session, vm)
            }
        }
    }
}

@Composable
private fun V2Dashboard(vm: SalesViewModel) {
    val data = vm.dashboard
    val p = data.portfolio
    val f = data.forecast
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = V2GreenDark)) {
                Column(Modifier.padding(20.dp)) {
                    Text("TARGET BULAN INI", color = Color.White.copy(alpha = .72f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("${p.paidPax} / ${p.targetPaidPax} paid pax", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = (p.paidPax.toFloat() / p.targetPaidPax.coerceAtLeast(1)).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = V2Gold,
                        trackColor = Color.White.copy(alpha = .18f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Sisa ${f.remainingPax} pax • ${f.remainingDaysInMonth} hari tersisa", color = Color.White.copy(alpha = .84f), fontSize = 11.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                V2Metric("Paid booking", p.paidBookings.toString(), Modifier.weight(1f))
                V2Metric("Pipeline", "${f.rawOpenPipelinePax}", Modifier.weight(1f))
                V2Metric("HOT lead", data.hotLeads.size.toString(), Modifier.weight(1f))
            }
        }
        item { V2Forecast(f) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.navigate(SalesPage.BOOKINGS) }, modifier = Modifier.weight(1f)) { Text("Booking Saya") }
                Button(onClick = { vm.navigate(SalesPage.FUNNEL) }, modifier = Modifier.weight(1f)) { Text("Buka Sales Kit") }
            }
        }
        item { V2Section("Prioritas Hari Ini", "Follow-up jatuh tempo dan prospek terdekat ke closing") }
        if (data.followUpsDue.isEmpty() && data.hotLeads.isEmpty()) {
            item { V2Empty("Belum ada prioritas follow-up. Tambahkan prospek baru untuk menjaga pipeline.") }
        } else {
            items((data.followUpsDue + data.hotLeads).distinctBy { it.id }.take(6), key = { it.id }) { lead -> V2LeadCard(lead, null) }
        }
        item { V2Section("Paid Pax per Program", "Performa penjualan pribadi tanpa menampilkan HPP/laba perusahaan") }
        if (data.programs.isEmpty()) item { V2Empty("Belum ada paid pax bulan ini.") }
        else items(data.programs, key = { it.programName }) { row ->
            Card(shape = RoundedCornerShape(17.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(row.programName, fontWeight = FontWeight.Bold)
                        Text("${row.paidBookings} booking", fontSize = 10.sp, color = Color.Gray)
                    }
                    Text("${row.paidPax} pax", color = V2Green, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun V2Forecast(f: SalesForecast) {
    val label = when (f.status) {
        ForecastStatus.TARGET_REACHED -> "TARGET TERCAPAI"
        ForecastStatus.ON_TRACK -> "ON TRACK"
        ForecastStatus.PIPELINE_CAN_COVER -> "PIPELINE CUKUP"
        ForecastStatus.AT_RISK -> "PERLU PERCEPATAN"
        ForecastStatus.PIPELINE_INSUFFICIENT -> "PIPELINE BELUM CUKUP"
    }
    Card(shape = RoundedCornerShape(21.dp)) {
        Column(Modifier.padding(17.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Forecast Target", fontWeight = FontWeight.Black, fontSize = 16.sp)
                Surface(shape = RoundedCornerShape(100.dp), color = V2Gold.copy(alpha = .15f)) {
                    Text(label, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = V2GreenDark, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(f.message, color = Color.Gray, fontSize = 11.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                V2Mini("Weighted", String.format(Locale.US, "%.0f pax", f.weightedForecastPax), Modifier.weight(1f))
                V2Mini("Pace", String.format(Locale.US, "%.0f pax", f.paceProjectionPax), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("Butuh rata-rata ${String.format(Locale.US, "%.1f", f.requiredPaidPaxPerDay)} paid pax/hari.", color = V2GreenDark, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun V2Leads(vm: SalesViewModel) {
    var filter by remember { mutableStateOf("ALL") }
    var selected by remember { mutableStateOf<SalesLead?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val stages = listOf("ALL", "NEW", "CONTACTED", "QUALIFIED", "QUOTATION", "NEGOTIATION", "WAITING_DP", "WON", "LOST")
    val rows = vm.dashboard.leads.filter { filter == "ALL" || it.stage == filter }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CRM Lead", fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text("Prospek milik Sales ini", fontSize = 11.sp, color = Color.Gray)
            }
            Button(onClick = { showNew = true }, enabled = !vm.actionBusy) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(5.dp))
                Text("Lead")
            }
        }
        ScrollableTabRow(selectedTabIndex = stages.indexOf(filter).coerceAtLeast(0), edgePadding = 12.dp) {
            stages.forEach { stage ->
                Tab(selected = filter == stage, onClick = { filter = stage }, text = { Text(stage.replace('_', ' '), fontSize = 9.sp) })
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rows.isEmpty()) item { V2Empty("Belum ada lead pada tahap ini.") }
            items(rows, key = { it.id }) { lead -> V2LeadCard(lead) { selected = lead } }
        }
    }

    if (showNew) {
        NewLeadDialog(
            catalog = vm.dashboard.catalog,
            busy = vm.actionBusy,
            onDismiss = { showNew = false },
            onCreate = {
                vm.createLead(it)
                showNew = false
            }
        )
    }
    selected?.let { lead ->
        V2LeadUpdateDialog(lead, vm.actionBusy, onDismiss = { selected = null }) { stage, next ->
            vm.updateLead(lead, stage, next)
            selected = null
        }
    }
}

@Composable
private fun V2FollowUp(vm: SalesViewModel) {
    var selected by remember { mutableStateOf<SalesLead?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { V2Section("Follow-up Hari Ini", "Prospek aktif harus memiliki next action") }
        if (vm.dashboard.followUpsDue.isEmpty()) item { V2Empty("Tidak ada follow-up jatuh tempo saat ini.") }
        else items(vm.dashboard.followUpsDue, key = { it.id }) { lead -> V2LeadCard(lead) { selected = lead } }
        item { V2Section("HOT Lead", "Quotation, negosiasi, dan menunggu DP") }
        if (vm.dashboard.hotLeads.isEmpty()) item { V2Empty("Belum ada HOT lead. Dorong qualified lead menuju quotation.") }
        else items(vm.dashboard.hotLeads, key = { "hot-${it.id}" }) { lead -> V2LeadCard(lead) { selected = lead } }
    }
    selected?.let { lead ->
        V2LeadUpdateDialog(lead, vm.actionBusy, onDismiss = { selected = null }) { stage, next ->
            vm.updateLead(lead, stage, next)
            selected = null
        }
    }
}

@Composable
private fun V2Bookings(data: SalesDashboard) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { V2Section("Booking Saya", "Booking yang teratribusi ke akun Sales") }
        if (data.bookings.isEmpty()) item { V2Empty("Belum ada booking yang teratribusi.") }
        else items(data.bookings, key = { it.id }) { b ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(b.bookingNo, fontWeight = FontWeight.Black)
                        V2Stage(b.status.uppercase())
                    }
                    Text(b.programName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (b.customerName.isNotBlank()) Text(b.customerName, fontSize = 10.sp, color = Color.Gray)
                    Spacer(Modifier.height(5.dp))
                    Text("${b.pax} pax • ${b.tripDate}", color = V2Green, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun V2Earnings(data: SalesDashboard) {
    val p = data.portfolio
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { V2Section("Komisi Saya", "Hanya penghasilan Sales; tidak menampilkan HPP atau laporan perusahaan") }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = V2GreenDark)) {
                Column(Modifier.padding(20.dp)) {
                    Text("ESTIMASI PENGHASILAN BULAN INI", color = Color.White.copy(alpha = .72f), fontSize = 9.sp)
                    Text(v2Rupiah(p.modeledSalesIncome), color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
                    Text("${p.paidPax} paid pax • ${p.achievementLevel.replace('_', ' ')}", color = Color.White.copy(alpha = .82f), fontSize = 11.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(17.dp)) {
                    V2EarningLine("Fixed / retainer", p.salesRetainer)
                    V2EarningLine("Komisi ${p.paidPax} pax × ${v2Rupiah(p.salesFeePerPaidPax)}", p.variableSalesFee)
                    V2EarningLine("Bonus target earned", p.targetBonusEarned)
                    HorizontalDivider(Modifier.padding(vertical = 9.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total modeled", fontWeight = FontWeight.Black)
                        Text(v2Rupiah(p.modeledSalesIncome), fontWeight = FontWeight.Black, color = V2Green)
                    }
                }
            }
        }
        item { V2Empty("Komisi dihitung dari paid pax yang teratribusi ke Sales. Bonus mengikuti kebijakan dan guardrail perusahaan.") }
    }
}

@Composable
private fun V2Profile(session: SalesSession, vm: SalesViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { V2Section("Profil Sales", "Akun kerja GMU EduTrans") }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(20.dp)) {
                    V2Brand(Modifier.width(150.dp).height(95.dp))
                    Text(session.profile.fullName, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    Text("Sales / Education Partnership", color = V2Green, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("Akses dibatasi pada target, CRM, follow-up, sales kit, booking sendiri, dan komisi.", color = Color.Gray, fontSize = 11.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Text("Keluar dari Sales App") }
                }
            }
        }
    }
}

@Composable
private fun V2LeadCard(lead: SalesLead, onClick: (() -> Unit)?) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(lead.institutionName, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(7.dp))
                V2Stage(lead.stage)
            }
            if (lead.picName.isNotBlank()) Text("PIC: ${lead.picName}", fontSize = 10.sp, color = Color.Gray)
            if (lead.programName.isNotBlank()) Text(lead.programName, fontSize = 10.sp, color = Color.Gray)
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${lead.pax} pax", color = V2Green, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("${lead.probabilityPct.toInt()}%", color = V2Gold, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
            if (lead.nextFollowUpAt.isNotBlank()) Text("Follow-up: ${lead.nextFollowUpAt.take(16).replace('T', ' ')}", fontSize = 9.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun V2LeadUpdateDialog(
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
                Text("${lead.pax} pax • ${lead.bookingCode}", color = Color.Gray, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Text("Tahap lead", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                stages.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { value ->
                            FilterChip(selected = stage == value, onClick = { stage = value }, label = { Text(value.replace('_', ' '), fontSize = 8.sp) })
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Text("Next follow-up", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(1, 3, 7).forEach { days ->
                        AssistChip(
                            onClick = {
                                next = LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(days.toLong()).atTime(9, 0).atZone(ZoneId.of("Asia/Jakarta")).toOffsetDateTime().toString()
                            },
                            label = { Text("+$days hari") }
                        )
                    }
                }
                if (next.isNotBlank()) Text(next.take(16).replace('T', ' '), color = V2GreenDark, fontSize = 10.sp)
            }
        },
        confirmButton = { Button(onClick = { onSave(stage, next.ifBlank { null }) }, enabled = !busy) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun V2Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 8.sp, color = Color.Gray)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = V2Ink)
        }
    }
}

@Composable
private fun V2Mini(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(13.dp), color = V2Surface) {
        Column(Modifier.padding(11.dp)) {
            Text(label, fontSize = 8.sp, color = Color.Gray)
            Text(value, fontWeight = FontWeight.Black, color = V2GreenDark)
        }
    }
}

@Composable
private fun V2Stage(stage: String) {
    val success = stage in setOf("WON", "CLOSED", "COMPLETED", "PAID")
    Surface(shape = RoundedCornerShape(100.dp), color = if (success) V2Green.copy(alpha = .13f) else V2Gold.copy(alpha = .14f)) {
        Text(stage.replace('_', ' '), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = V2GreenDark)
    }
}

@Composable
private fun V2Section(title: String, subtitle: String) {
    Column {
        Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp, color = V2Ink)
        Text(subtitle, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
private fun V2Empty(message: String) {
    Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Text(message, modifier = Modifier.padding(15.dp), color = Color.Gray, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun V2EarningLine(label: String, value: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(v2Rupiah(value), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun v2Rupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")