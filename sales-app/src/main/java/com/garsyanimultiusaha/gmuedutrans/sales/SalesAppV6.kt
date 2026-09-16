package com.garsyanimultiusaha.gmuedutrans.sales

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

private val V6Green = Color(0xFF128000)
private val V6GreenDark = Color(0xFF07580F)
private val V6Gold = Color(0xFFD5A300)
private val V6Bg = Color(0xFFF5F7F3)
private val V6Ink = Color(0xFF17301D)
private val V6SoftGreen = Color(0xFFEAF6E8)
private val V6SoftGold = Color(0xFFFFF4D4)

private enum class V6Page { HOME, CRM, VISIT, CLOSING, MORE, FOLLOW_UP, REPEAT, SALES_KIT, EARNINGS, COACH, PROFILE }

@Composable
fun SalesAppV6(vm: SalesViewModel) {
    var page by remember { mutableStateOf(V6Page.HOME) }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = V6Green,
            secondary = V6Gold,
            background = V6Bg,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = V6Ink,
            onSurface = V6Ink
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = V6Bg) {
            when (val state = vm.state) {
                SalesAppState.Splash -> V6Splash()
                SalesAppState.LoggedOut -> V6Login(vm)
                SalesAppState.Loading -> V6Loading()
                is SalesAppState.Error -> V6Error(state.message, vm::backToLogin)
                is SalesAppState.LoggedIn -> V6Shell(vm, state.session, page) { page = it }
            }
        }
    }
}

@Composable
private fun V6Brand(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.sales_logo),
        contentDescription = "GMU EduTrans",
        modifier = modifier
    )
}

@Composable
private fun V6Splash() {
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            V6Brand(Modifier.width(220.dp).height(132.dp))
            CircularProgressIndicator(color = V6Green, strokeWidth = 3.dp)
            Spacer(Modifier.height(10.dp))
            Text("Sales App v6", fontWeight = FontWeight.Black, color = V6GreenDark)
            Text("Field Sales & Closing OS", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun V6Login(vm: SalesViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp).widthIn(max = 430.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                V6Brand(Modifier.width(190.dp).height(112.dp))
                Text("Field Sales & Closing OS", fontWeight = FontWeight.Black, color = V6GreenDark)
                Text("CRM • Visit • Follow-up • Closing • Repeat", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email Sales") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(15.dp))
                Button(
                    onClick = { vm.login(email, password) },
                    enabled = email.isNotBlank() && password.isNotBlank() && !vm.actionBusy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp)
                ) { Text("Masuk ke Sales v6", fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(10.dp))
                Text("Target utama 400 paid pax / bulan", fontSize = 10.sp, color = V6GreenDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun V6Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = V6Green)
            Spacer(Modifier.height(9.dp))
            Text("Menyiapkan Sales v6…", color = Color.Gray)
        }
    }
}

@Composable
private fun V6Error(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Belum dapat masuk", fontWeight = FontWeight.Black, fontSize = 19.sp)
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
private fun V6Shell(vm: SalesViewModel, session: SalesSession, page: V6Page, onPage: (V6Page) -> Unit) {
    val bottom = listOf(
        Triple(V6Page.HOME, Icons.Default.Home, "Home"),
        Triple(V6Page.CRM, Icons.Default.Groups, "CRM"),
        Triple(V6Page.VISIT, Icons.Default.Place, "Visit"),
        Triple(V6Page.CLOSING, Icons.Default.Flag, "Closing"),
        Triple(V6Page.MORE, Icons.Default.MoreHoriz, "Lainnya")
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GMU EduTrans Sales v6", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text(session.profile.fullName, fontSize = 10.sp, color = Color.Gray)
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !vm.dataBusy) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { onPage(V6Page.PROFILE) }) { Icon(Icons.Default.AccountCircle, "Profil") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                bottom.forEach { (target, icon, label) ->
                    NavigationBarItem(
                        selected = page == target,
                        onClick = { onPage(target) },
                        icon = { Icon(icon, label) },
                        label = { Text(label, fontSize = 8.sp) }
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
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)
                )
            }
            when (page) {
                V6Page.HOME -> V6Home(vm, onPage)
                V6Page.CRM -> V6Crm(vm)
                V6Page.VISIT -> V6Visit(vm)
                V6Page.CLOSING -> V6Closing(vm)
                V6Page.MORE -> V6More(onPage)
                V6Page.FOLLOW_UP -> V6FollowUp(vm)
                V6Page.REPEAT -> V6Repeat(vm.dashboard)
                V6Page.SALES_KIT -> MarketingKitScreen(vm)
                V6Page.EARNINGS -> V6Earnings(vm.dashboard)
                V6Page.COACH -> V6Coach(vm.dashboard)
                V6Page.PROFILE -> V6Profile(session, vm)
            }
        }
    }
}

@Composable
private fun V6Home(vm: SalesViewModel, onPage: (V6Page) -> Unit) {
    val data = vm.dashboard
    val p = data.portfolio
    val f = data.forecast
    val today = LocalDate.now(ZoneId.of("Asia/Jakarta")).toString()
    val newToday = data.leads.count { it.createdAt.startsWith(today) }
    val active = data.leads.count { it.stage !in setOf("WON", "LOST") }
    val hot = data.leads.count { it.stage in setOf("QUOTATION", "NEGOTIATION", "WAITING_DP") }
    val waitingDp = data.leads.count { it.stage == "WAITING_DP" }
    val draftQuotes = data.quotations.count { it.status == "DRAFT" }
    val coverageScore = (f.pipelineCoverage * 35.0).coerceAtMost(35.0)
    val hotScore = (hot * 7.0).coerceAtMost(28.0)
    val activityScore = (newToday * 2.5).coerceAtMost(25.0)
    val overduePenalty = (data.followUpsDue.size * 3.0).coerceAtMost(18.0)
    val health = (12.0 + coverageScore + hotScore + activityScore - overduePenalty).toInt().coerceIn(0, 100)

    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(25.dp), colors = CardDefaults.cardColors(containerColor = V6GreenDark)) {
                Column(Modifier.padding(20.dp)) {
                    Text("TARGET BULAN INI", color = Color.White.copy(alpha = .7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("${p.paidPax} / ${p.targetPaidPax} paid pax", color = Color.White, fontWeight = FontWeight.Black, fontSize = 29.sp)
                    Spacer(Modifier.height(9.dp))
                    LinearProgressIndicator(
                        progress = (p.paidPax.toFloat() / p.targetPaidPax.coerceAtLeast(1)).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = V6Gold,
                        trackColor = Color.White.copy(alpha = .18f)
                    )
                    Spacer(Modifier.height(7.dp))
                    Text("Sisa ${f.remainingPax} pax • forecast ${f.weightedForecastPax.toInt()} pax • ${f.remainingDaysInMonth} hari", color = Color.White.copy(alpha = .85f), fontSize = 10.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V6Metric("Sales Health", "$health/100", Modifier.weight(1f))
                V6Metric("Lead aktif", active.toString(), Modifier.weight(1f))
                V6Metric("HOT", hot.toString(), Modifier.weight(1f))
            }
        }
        item { V6Section("Daily Mission", "Jaga aktivitas harian agar target 400 pax tetap realistis") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V6Mission("Prospek", newToday, 10, Modifier.weight(1f))
                V6Mission("Follow-up due", data.followUpsDue.size, 5, Modifier.weight(1f))
                V6Mission("HOT lead", hot, 4, Modifier.weight(1f))
            }
        }
        item { V6Section("Next Best Action", "Urutkan pekerjaan berdasarkan peluang closing") }
        if (waitingDp > 0) item { V6ActionCard("Kejar DP", "$waitingDp lead sudah WAITING DP", Icons.Default.Payments) { onPage(V6Page.CLOSING) } }
        if (draftQuotes > 0) item { V6ActionCard("Kirim quotation", "$draftQuotes quotation masih DRAFT", Icons.Default.Description) { onPage(V6Page.CLOSING) } }
        if (data.followUpsDue.isNotEmpty()) item { V6ActionCard("Follow-up jatuh tempo", "${data.followUpsDue.size} prospek harus dihubungi", Icons.Default.Schedule) { onPage(V6Page.FOLLOW_UP) } }
        if (active < 20) item { V6ActionCard("Tambah pipeline", "Lead aktif baru $active. Tambah sekolah/lembaga baru hari ini.", Icons.Default.Groups) { onPage(V6Page.CRM) } }
        item { V6Section("Command Center", "Akses cepat kerja lapangan dan closing") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onPage(V6Page.VISIT) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Place, null); Spacer(Modifier.width(5.dp)); Text("Visit") }
                Button(onClick = { onPage(V6Page.COACH) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.TrendingUp, null); Spacer(Modifier.width(5.dp)); Text("Sales Coach") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    V6Notice(Icons.Default.Notifications, "${data.followUpsDue.size} follow-up jatuh tempo")
                    V6Notice(Icons.Default.Description, "$draftQuotes quotation belum dikirim")
                    V6Notice(Icons.Default.Payments, "$waitingDp lead menunggu DP")
                    V6Notice(Icons.Default.TrendingUp, f.message)
                }
            }
        }
    }
}

@Composable
private fun V6Crm(vm: SalesViewModel) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ACTIVE") }
    var selected by remember { mutableStateOf<SalesLead?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val rows = vm.dashboard.leads.filter { lead ->
        val matchesSearch = search.isBlank() || lead.institutionName.contains(search, true) || lead.picName.contains(search, true) || lead.city.contains(search, true)
        val matchesFilter = when (filter) {
            "HOT" -> lead.stage in setOf("QUOTATION", "NEGOTIATION", "WAITING_DP")
            "WON" -> lead.stage == "WON"
            "LOST" -> lead.stage == "LOST"
            else -> lead.stage !in setOf("WON", "LOST")
        }
        matchesSearch && matchesFilter
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CRM 360°", fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("Sekolah, PIC, kebutuhan, pipeline dan next action", fontSize = 10.sp, color = Color.Gray)
            }
            Button(onClick = { showNew = true }, enabled = !vm.actionBusy) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Lead") }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Cari sekolah / PIC / kota") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("ACTIVE", "HOT", "WON", "LOST").forEach { value ->
                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value, fontSize = 9.sp) })
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (rows.isEmpty()) item { V6Info("Belum ada lead pada filter ini.") }
            items(rows, key = { it.id }) { lead -> V6LeadCard(lead) { selected = lead } }
        }
    }
    if (showNew) {
        NewLeadDialog(vm.dashboard.catalog, vm.actionBusy, { showNew = false }) { input -> vm.createLead(input); showNew = false }
    }
    selected?.let { lead -> V6LeadUpdateDialog(lead, vm.actionBusy, { selected = null }) { stage, next -> vm.updateLead(lead, stage, next); selected = null } }
}

@Composable
private fun V6Visit(vm: SalesViewModel) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<SalesLead?>(null) }
    val rows = vm.dashboard.leads.filter {
        it.stage !in setOf("WON", "LOST") && (search.isBlank() || it.institutionName.contains(search, true) || it.city.contains(search, true))
    }.sortedByDescending { it.probabilityPct }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp, 9.dp)) {
            Text("Visit Mode", fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("Mode kerja lapangan untuk kunjungan sekolah/lembaga", fontSize = 10.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(search, { search = it }, label = { Text("Cari tujuan kunjungan") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { V6Info("Setelah kunjungan, catat hasil tahap lead dan jadwalkan follow-up berikutnya agar prospek tidak hilang.") }
            if (rows.isEmpty()) item { V6Info("Belum ada lead aktif untuk kunjungan.") }
            items(rows, key = { "visit-${it.id}" }) { lead ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(lead.institutionName, fontWeight = FontWeight.Black)
                                Text(listOf(lead.picName, lead.city).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 10.sp, color = Color.Gray)
                            }
                            V6Stage(lead.stage)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${lead.pax} pax • peluang ${lead.probabilityPct.toInt()}%", color = V6GreenDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { openWa(context, lead.whatsapp, visitMessage(lead)) }, enabled = lead.whatsapp.isNotBlank(), modifier = Modifier.weight(1f)) { Icon(Icons.Default.Chat, null); Spacer(Modifier.width(4.dp)); Text("WA") }
                            OutlinedButton(onClick = { openPhone(context, lead.whatsapp) }, enabled = lead.whatsapp.isNotBlank(), modifier = Modifier.weight(1f)) { Icon(Icons.Default.Phone, null); Spacer(Modifier.width(4.dp)); Text("Telepon") }
                            Button(onClick = { selected = lead }, modifier = Modifier.weight(1.2f)) { Text("Catat Visit") }
                        }
                    }
                }
            }
        }
    }
    selected?.let { lead -> V6VisitDialog(lead, vm.actionBusy, { selected = null }) { stage, next -> vm.updateLead(lead, stage, next); selected = null } }
}

@Composable
private fun V6FollowUp(vm: SalesViewModel) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<SalesLead?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { V6Section("Follow-up Center", "Prioritaskan overdue, quotation, negosiasi dan waiting DP") }
        if (vm.dashboard.followUpsDue.isEmpty()) item { V6Info("Tidak ada follow-up jatuh tempo saat ini.") }
        else items(vm.dashboard.followUpsDue, key = { it.id }) { lead ->
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(15.dp)) {
                    V6LeadSummary(lead)
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(onClick = { openWa(context, lead.whatsapp, followUpMessage(lead)) }, enabled = lead.whatsapp.isNotBlank(), modifier = Modifier.weight(1f)) { Icon(Icons.Default.Chat, null); Spacer(Modifier.width(4.dp)); Text("WhatsApp") }
                        OutlinedButton(onClick = { selected = lead }, modifier = Modifier.weight(1f)) { Text("Update") }
                    }
                }
            }
        }
    }
    selected?.let { lead -> V6LeadUpdateDialog(lead, vm.actionBusy, { selected = null }) { stage, next -> vm.updateLead(lead, stage, next); selected = null } }
}

@Composable
private fun V6Closing(vm: SalesViewModel) {
    val context = LocalContext.current
    val quotes = vm.dashboard.quotations
    val leadsById = vm.dashboard.leads.associateBy { it.id }
    val waitingDp = vm.dashboard.leads.filter { it.stage == "WAITING_DP" }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { V6Section("Closing Center", "Quotation → WhatsApp → WAITING DP → WON → Handover") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V6Metric("Draft", quotes.count { it.status == "DRAFT" }.toString(), Modifier.weight(1f))
                V6Metric("Sent", quotes.count { it.status == "SENT" }.toString(), Modifier.weight(1f))
                V6Metric("Waiting DP", waitingDp.size.toString(), Modifier.weight(1f))
            }
        }
        item { Text("Quotation Saya", fontWeight = FontWeight.Black, fontSize = 17.sp) }
        if (quotes.isEmpty()) item { V6Info("Belum ada quotation. Buat dari Sales Kit → Quotation.") }
        else items(quotes, key = { it.id }) { q ->
            val lead = leadsById[q.bookingRequestId]
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(q.quotationNo, fontWeight = FontWeight.Black)
                        V6Stage(q.status)
                    }
                    Text(q.institutionName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("${q.programName} • ${q.pax} pax", color = Color.Gray, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(v6Rupiah(q.total), color = V6Green, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    if (q.validUntil.isNotBlank()) Text("Berlaku s.d. ${q.validUntil}", color = Color.Gray, fontSize = 9.sp)
                    if (q.status == "DRAFT") {
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { if (lead != null) openWa(context, lead.whatsapp, quotationMessage(q, lead)) },
                                enabled = lead?.whatsapp?.isNotBlank() == true,
                                modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Default.Chat, null); Spacer(Modifier.width(4.dp)); Text("Kirim WA") }
                            Button(onClick = { vm.markQuotationSent(q) }, enabled = !vm.actionBusy, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(4.dp)); Text("Tandai Sent") }
                        }
                    }
                }
            }
        }
        item { Text("Menunggu DP", fontWeight = FontWeight.Black, fontSize = 17.sp) }
        if (waitingDp.isEmpty()) item { V6Info("Tidak ada lead WAITING DP.") }
        else items(waitingDp, key = { "dp-${it.id}" }) { lead ->
            Card(shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = V6SoftGold)) {
                Column(Modifier.padding(15.dp)) {
                    V6LeadSummary(lead)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { openWa(context, lead.whatsapp, dpMessage(lead)) }, enabled = lead.whatsapp.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Chat, null); Spacer(Modifier.width(6.dp)); Text("Reminder DP via WhatsApp") }
                }
            }
        }
    }
}

@Composable
private fun V6Repeat(data: SalesDashboard) {
    val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))
    val repeat = data.bookings.filter { b ->
        val trip = runCatching { LocalDate.parse(b.tripDate) }.getOrNull()
        trip != null && !trip.isAfter(today) && b.status.uppercase() in setOf("PAID", "COMPLETED", "CLOSED")
    }.sortedBy { it.tripDate }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { V6Section("Repeat Order Radar", "Customer lama lebih murah di-follow-up daripada mencari lead dari nol") }
        item { V6Info("Gunakan daftar ini untuk menghubungi kembali sekolah/lembaga yang sudah pernah selesai kegiatan dan menawarkan program berikutnya.") }
        if (repeat.isEmpty()) item { V6Info("Belum ada booking selesai yang masuk Repeat Radar.") }
        else items(repeat, key = { it.id }) { b ->
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(b.customerName.ifBlank { b.bookingNo }, fontWeight = FontWeight.Black)
                    Text(b.programName, fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(5.dp))
                    Text("${b.pax} pax • trip ${b.tripDate}", color = V6GreenDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Saran: tawarkan program berbeda atau jadwal semester berikutnya.", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun V6Earnings(data: SalesDashboard) {
    val p = data.portfolio
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { V6Section("My Earnings", "Hanya penghasilan Sales; tidak menampilkan HPP/laba perusahaan") }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = V6GreenDark)) {
                Column(Modifier.padding(20.dp)) {
                    Text("ESTIMASI BULAN INI", fontSize = 9.sp, color = Color.White.copy(alpha = .7f))
                    Text(v6Rupiah(p.modeledSalesIncome), color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
                    Text("${p.paidPax} paid pax • ${p.achievementLevel.replace('_', ' ')}", color = Color.White.copy(alpha = .82f), fontSize = 10.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    V6Money("Fixed / retainer", p.salesRetainer)
                    V6Money("Komisi paid pax", p.variableSalesFee)
                    V6Money("Bonus earned", p.targetBonusEarned)
                    HorizontalDivider()
                    V6Money("Total modeled", p.modeledSalesIncome, true)
                }
            }
        }
        item { V6Info("Komisi mengikuti paid pax yang valid dan kebijakan perusahaan. Pembatalan/refund dapat mengubah eligibility.") }
    }
}

@Composable
private fun V6Coach(data: SalesDashboard) {
    val f = data.forecast
    val active = data.leads.filter { it.stage !in setOf("WON", "LOST") }
    val best = active.maxByOrNull { it.probabilityPct }
    val waiting = active.filter { it.stage == "WAITING_DP" }
    val quotes = data.quotations.filter { it.status == "DRAFT" }
    val recommendation = when {
        waiting.isNotEmpty() -> "Prioritas pertama: follow-up ${waiting.size} lead WAITING DP. Mereka paling dekat menjadi paid pax."
        quotes.isNotEmpty() -> "Ada ${quotes.size} quotation DRAFT. Kirim dan tandai SENT sebelum menambah pekerjaan baru."
        data.followUpsDue.isNotEmpty() -> "Selesaikan ${data.followUpsDue.size} follow-up jatuh tempo hari ini agar pipeline tidak dingin."
        f.pipelineCoverage < 1.0 -> "Pipeline belum menutup target. Tambah prospek sekolah/lembaga baru dan dorong minimal ke QUALIFIED."
        else -> "Pipeline cukup. Fokus pada lead dengan probabilitas tertinggi dan kurangi waktu menuju DP."
    }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { V6Section("Smart Sales Coach", "Analisis lokal berbasis data pipeline Sales saat ini") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = V6SoftGreen)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Rekomendasi utama", fontWeight = FontWeight.Black, color = V6GreenDark)
                    Spacer(Modifier.height(6.dp))
                    Text(recommendation, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Diagnosis Pipeline", fontWeight = FontWeight.Black)
                    V6CoachLine("Paid pax", "${data.portfolio.paidPax} / ${data.portfolio.targetPaidPax}")
                    V6CoachLine("Open pipeline", "${f.rawOpenPipelinePax} pax")
                    V6CoachLine("Weighted forecast", "${f.weightedForecastPax.toInt()} pax")
                    V6CoachLine("Coverage", String.format(Locale.US, "%.1fx", f.pipelineCoverage))
                    V6CoachLine("Follow-up due", data.followUpsDue.size.toString())
                }
            }
        }
        best?.let { lead ->
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = V6SoftGold)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Lead terdekat ke closing", fontWeight = FontWeight.Black)
                        Text(lead.institutionName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${lead.stage.replace('_', ' ')} • ${lead.probabilityPct.toInt()}% • ${lead.pax} pax", fontSize = 11.sp, color = V6GreenDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun V6More(onPage: (V6Page) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { V6Section("Workspace Lainnya", "Semua alat bantu Sales dalam satu aplikasi") }
        item { V6Menu(Icons.Default.Schedule, "Follow-up Center", "Daftar prospek jatuh tempo") { onPage(V6Page.FOLLOW_UP) } }
        item { V6Menu(Icons.Default.History, "Repeat Order Radar", "Customer lama yang siap ditawarkan kembali") { onPage(V6Page.REPEAT) } }
        item { V6Menu(Icons.Default.Campaign, "Marketing & Sales Kit", "Program, materi, script dan quotation") { onPage(V6Page.SALES_KIT) } }
        item { V6Menu(Icons.Default.AttachMoney, "My Earnings", "Paid pax dan estimasi komisi pribadi") { onPage(V6Page.EARNINGS) } }
        item { V6Menu(Icons.Default.TrendingUp, "Smart Sales Coach", "Prioritas berbasis kondisi pipeline") { onPage(V6Page.COACH) } }
        item { V6Menu(Icons.Default.AccountCircle, "Profil", "Akun dan akses Sales") { onPage(V6Page.PROFILE) } }
    }
}

@Composable
private fun V6Profile(session: SalesSession, vm: SalesViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { V6Section("Profil Sales", "Akun kerja resmi GMU EduTrans") }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(20.dp)) {
                    V6Brand(Modifier.width(150.dp).height(90.dp))
                    Text(session.profile.fullName, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    Text("Sales / Education Partnership", color = V6Green, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(9.dp))
                    Text("Sales v6 hanya menampilkan CRM, target, sales kit, booking pribadi dan penghasilan Sales. HPP, laba, saldo kas/bank dan keuangan perusahaan tetap dibatasi.", fontSize = 11.sp, color = Color.Gray, lineHeight = 17.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Text("Keluar") }
                }
            }
        }
    }
}

@Composable
private fun V6LeadCard(lead: SalesLead, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(19.dp)) {
        Column(Modifier.padding(15.dp)) {
            V6LeadSummary(lead)
            if (lead.nextFollowUpAt.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text("Next: ${lead.nextFollowUpAt.take(16).replace('T', ' ')}", fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun V6LeadSummary(lead: SalesLead) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(lead.institutionName, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (lead.picName.isNotBlank()) Text("PIC ${lead.picName}${if (lead.city.isNotBlank()) " • ${lead.city}" else ""}", fontSize = 9.sp, color = Color.Gray)
        }
        Spacer(Modifier.width(7.dp))
        V6Stage(lead.stage)
    }
    Spacer(Modifier.height(5.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${lead.pax} pax", color = V6Green, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("${lead.probabilityPct.toInt()}%", color = V6Gold, fontWeight = FontWeight.Black, fontSize = 11.sp)
    }
}

@Composable
private fun V6LeadUpdateDialog(lead: SalesLead, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var stage by remember(lead.id) { mutableStateOf(lead.stage) }
    var next by remember(lead.id) { mutableStateOf(lead.nextFollowUpAt) }
    val stages = listOf("NEW", "CONTACTED", "QUALIFIED", "QUOTATION", "NEGOTIATION", "WAITING_DP", "WON", "LOST", "NURTURE")
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(lead.institutionName, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("${lead.pax} pax • ${lead.bookingCode}", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.height(9.dp))
                stages.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { s -> FilterChip(selected = stage == s, onClick = { stage = s }, label = { Text(s.replace('_', ' '), fontSize = 8.sp) }) }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text("Next follow-up", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(1, 3, 7).forEach { days -> AssistChip(onClick = { next = futureFollowUp(days) }, label = { Text("+$days hari") }) }
                }
                if (next.isNotBlank()) Text(next.take(16).replace('T', ' '), fontSize = 10.sp, color = V6GreenDark)
            }
        },
        confirmButton = { Button(onClick = { onSave(stage, next.ifBlank { null }) }, enabled = !busy) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun V6VisitDialog(lead: SalesLead, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var stage by remember(lead.id) { mutableStateOf(if (lead.stage == "NEW") "CONTACTED" else lead.stage) }
    var next by remember(lead.id) { mutableStateOf(futureFollowUp(3)) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Catat hasil kunjungan", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(lead.institutionName, fontWeight = FontWeight.Bold)
                Text("Pilih hasil pertemuan dan next follow-up.", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.height(9.dp))
                listOf("CONTACTED", "QUALIFIED", "NEGOTIATION", "NURTURE").forEach { s ->
                    FilterChip(selected = stage == s, onClick = { stage = s }, label = { Text(s) })
                }
                Text("Jadwal berikutnya", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(1, 3, 7).forEach { d -> AssistChip(onClick = { next = futureFollowUp(d) }, label = { Text("+$d hari") }) }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(stage, next) }, enabled = !busy) { Text("Simpan Visit") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun V6Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(12.dp)) { Text(label, fontSize = 8.sp, color = Color.Gray); Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun V6Mission(label: String, actual: Int, target: Int, modifier: Modifier = Modifier) {
    val progress = (actual.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
    Card(modifier = modifier, shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(11.dp)) {
            Text(label, fontSize = 8.sp, color = Color.Gray)
            Text("$actual/$target", fontWeight = FontWeight.Black, color = V6GreenDark)
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(5.dp), color = V6Green, trackColor = V6Green.copy(alpha = .12f))
        }
    }
}

@Composable
private fun V6Stage(stage: String) {
    val good = stage.uppercase() in setOf("WON", "PAID", "COMPLETED", "CLOSED", "ACCEPTED")
    Surface(shape = RoundedCornerShape(100.dp), color = if (good) V6SoftGreen else V6SoftGold) {
        Text(stage.replace('_', ' '), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = V6GreenDark)
    }
}

@Composable
private fun V6Section(title: String, subtitle: String) {
    Column { Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp); Text(subtitle, fontSize = 10.sp, color = Color.Gray) }
}

@Composable
private fun V6Info(text: String) {
    Card(shape = RoundedCornerShape(17.dp)) { Text(text, Modifier.padding(15.dp), fontSize = 11.sp, lineHeight = 17.sp, color = Color.Gray) }
}

@Composable
private fun V6ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(13.dp), color = V6SoftGreen) { Icon(icon, null, tint = V6GreenDark, modifier = Modifier.padding(10.dp).size(21.dp)) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(subtitle, fontSize = 10.sp, color = Color.Gray) }
            Icon(Icons.Default.ArrowForward, null, tint = Color.Gray)
        }
    }
}

@Composable
private fun V6Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = V6Green, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(text, fontSize = 11.sp, modifier = Modifier.weight(1f)) }
}

@Composable
private fun V6Menu(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(19.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = V6Green, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(subtitle, fontSize = 9.sp, color = Color.Gray) }
            Icon(Icons.Default.ArrowForward, null, tint = Color.Gray)
        }
    }
}

@Composable
private fun V6Money(label: String, value: Double, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (strong) FontWeight.Black else FontWeight.Normal, fontSize = 12.sp)
        Text(v6Rupiah(value), fontWeight = FontWeight.Black, color = if (strong) V6Green else V6Ink)
    }
}

@Composable
private fun V6CoachLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontSize = 11.sp, color = Color.Gray); Text(value, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
}

private fun futureFollowUp(days: Int): String = LocalDate.now(ZoneId.of("Asia/Jakarta"))
    .plusDays(days.toLong()).atTime(9, 0).atZone(ZoneId.of("Asia/Jakarta")).toOffsetDateTime().toString()

private fun normalizedWa(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return when {
        digits.startsWith("62") -> digits
        digits.startsWith("0") -> "62${digits.drop(1)}"
        else -> digits
    }
}

private fun openWa(context: Context, phone: String, message: String) {
    val number = normalizedWa(phone)
    if (number.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=${Uri.encode(message)}"))) }
}

private fun openPhone(context: Context, phone: String) {
    val number = phone.filter { it.isDigit() || it == '+' }
    if (number.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
}

private fun visitMessage(lead: SalesLead): String = "Halo ${lead.picName.ifBlank { "Bapak/Ibu" }}, saya dari GMU EduTrans. Saya ingin menindaklanjuti kebutuhan program untuk ${lead.institutionName}. Apakah hari ini memungkinkan untuk berdiskusi atau kunjungan singkat?"

private fun followUpMessage(lead: SalesLead): String = "Halo ${lead.picName.ifBlank { "Bapak/Ibu" }}, izin follow-up dari GMU EduTrans terkait kebutuhan ${lead.programName} untuk sekitar ${lead.pax} peserta. Apakah ada hal yang perlu kami bantu agar rencana kegiatannya bisa dilanjutkan?"

private fun dpMessage(lead: SalesLead): String = "Halo ${lead.picName.ifBlank { "Bapak/Ibu" }}, izin follow-up GMU EduTrans terkait proses DP untuk rencana kegiatan ${lead.institutionName}. Jika ada kendala administrasi atau informasi yang perlu dibantu, silakan kabari kami."

private fun quotationMessage(q: SalesQuotation, lead: SalesLead): String = "Halo ${lead.picName.ifBlank { "Bapak/Ibu" }}, berikut informasi quotation ${q.quotationNo} dari GMU EduTrans untuk ${q.institutionName}, ${q.pax} peserta. Total ${v6Rupiah(q.total)} dan berlaku sampai ${q.validUntil}. Mohon informasikan bila ada yang perlu kami jelaskan."

private fun v6Rupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")
