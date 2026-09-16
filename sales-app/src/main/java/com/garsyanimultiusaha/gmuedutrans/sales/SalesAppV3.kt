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

private val V3Green = Color(0xFF138000)
private val V3GreenDark = Color(0xFF075A11)
private val V3Gold = Color(0xFFD6A400)
private val V3Bg = Color(0xFFF5F7F3)
private val V3Ink = Color(0xFF18301E)
private val V3SoftGreen = Color(0xFFE9F5E7)
private val V3SoftGold = Color(0xFFFFF5D7)

private enum class V3Page { HOME, CRM, FOLLOW_UP, CLOSING, MORE, SALES_KIT, EARNINGS, ASSISTANT, PROFILE }

@Composable
fun SalesAppV3(vm: SalesViewModel) {
    var page by remember { mutableStateOf(V3Page.HOME) }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = V3Green,
            secondary = V3Gold,
            background = V3Bg,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = V3Ink,
            onSurface = V3Ink
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = V3Bg) {
            when (val state = vm.state) {
                SalesAppState.Splash -> V3Splash()
                SalesAppState.LoggedOut -> V3Login(vm)
                SalesAppState.Loading -> V3Loading()
                is SalesAppState.Error -> V3Error(state.message, vm::backToLogin)
                is SalesAppState.LoggedIn -> V3Shell(vm, state.session, page) { page = it }
            }
        }
    }
}

@Composable
private fun V3Brand(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.sales_logo),
        contentDescription = "GMU EduTrans",
        modifier = modifier
    )
}

@Composable
private fun V3Splash() {
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            V3Brand(Modifier.width(220.dp).height(135.dp))
            CircularProgressIndicator(color = V3Green, strokeWidth = 3.dp)
            Spacer(Modifier.height(10.dp))
            Text("Sales App v3", fontWeight = FontWeight.Black, color = V3GreenDark)
            Text("Sales Operating System", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun V3Login(vm: SalesViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(24.dp).widthIn(max = 430.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                V3Brand(Modifier.width(190.dp).height(115.dp))
                Text("Sales Operating System v3", color = V3GreenDark, fontWeight = FontWeight.Black)
                Text("CRM • Follow-up • Closing • Komisi", color = Color.Gray, fontSize = 10.sp)
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
                ) { Text("Masuk ke Workspace Sales", fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(10.dp))
                Text("Target utama 400 paid pax / bulan", fontSize = 10.sp, color = V3GreenDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun V3Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = V3Green)
            Spacer(Modifier.height(9.dp))
            Text("Menyiapkan workspace Sales v3…", color = Color.Gray)
        }
    }
}

@Composable
private fun V3Error(message: String, onBack: () -> Unit) {
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
private fun V3Shell(vm: SalesViewModel, session: SalesSession, page: V3Page, onPage: (V3Page) -> Unit) {
    val bottom = listOf(
        Triple(V3Page.HOME, Icons.Default.Home, "Home"),
        Triple(V3Page.CRM, Icons.Default.Groups, "CRM"),
        Triple(V3Page.FOLLOW_UP, Icons.Default.Schedule, "Follow-up"),
        Triple(V3Page.CLOSING, Icons.Default.Flag, "Closing"),
        Triple(V3Page.MORE, Icons.Default.MoreHoriz, "Lainnya")
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GMU EduTrans Sales v3", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text(session.profile.fullName, fontSize = 10.sp, color = Color.Gray)
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !vm.dataBusy) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { onPage(V3Page.PROFILE) }) { Icon(Icons.Default.AccountCircle, "Profil") }
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
                V3Page.HOME -> V3Home(vm, onPage)
                V3Page.CRM -> V3Crm(vm)
                V3Page.FOLLOW_UP -> V3FollowUp(vm)
                V3Page.CLOSING -> V3Closing(vm, onPage)
                V3Page.MORE -> V3More(onPage)
                V3Page.SALES_KIT -> MarketingKitScreen(vm)
                V3Page.EARNINGS -> V3Earnings(vm.dashboard)
                V3Page.ASSISTANT -> V3Assistant(vm.dashboard)
                V3Page.PROFILE -> V3Profile(session, vm)
            }
        }
    }
}

@Composable
private fun V3Home(vm: SalesViewModel, onPage: (V3Page) -> Unit) {
    val data = vm.dashboard
    val p = data.portfolio
    val f = data.forecast
    val today = LocalDate.now(ZoneId.of("Asia/Jakarta")).toString()
    val newToday = data.leads.count { it.createdAt.startsWith(today) }
    val followToday = data.followUpsDue.size
    val quoteToday = data.quotations.count { it.createdAt.startsWith(today) }
    val waitingDp = data.leads.count { it.stage == "WAITING_DP" }
    val draftQuotes = data.quotations.count { it.status == "DRAFT" }

    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(25.dp), colors = CardDefaults.cardColors(containerColor = V3GreenDark)) {
                Column(Modifier.padding(20.dp)) {
                    Text("TARGET BULAN INI", color = Color.White.copy(alpha = .7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("${p.paidPax} / ${p.targetPaidPax} paid pax", color = Color.White, fontWeight = FontWeight.Black, fontSize = 29.sp)
                    Spacer(Modifier.height(9.dp))
                    LinearProgressIndicator(
                        progress = (p.paidPax.toFloat() / p.targetPaidPax.coerceAtLeast(1)).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = V3Gold,
                        trackColor = Color.White.copy(alpha = .18f)
                    )
                    Spacer(Modifier.height(7.dp))
                    Text("Sisa ${f.remainingPax} pax • forecast ${f.weightedForecastPax.toInt()} pax", color = Color.White.copy(alpha = .85f), fontSize = 10.sp)
                }
            }
        }
        item { V3SectionTitle("Daily Mission", "Aktivitas minimum untuk menjaga mesin penjualan bergerak") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V3Mission("Prospek", newToday, 10, Modifier.weight(1f))
                V3Mission("Follow-up", followToday, 5, Modifier.weight(1f))
                V3Mission("Quotation", quoteToday, 2, Modifier.weight(1f))
            }
        }
        item { V3SectionTitle("Prioritas Hari Ini", "Kerjakan yang paling dekat dengan closing terlebih dahulu") }
        if (waitingDp > 0) item { V3Priority("Kejar DP", "$waitingDp lead sedang WAITING DP", Icons.Default.Payments) { onPage(V3Page.CLOSING) } }
        if (draftQuotes > 0) item { V3Priority("Kirim quotation", "$draftQuotes draft belum ditandai terkirim", Icons.Default.Description) { onPage(V3Page.CLOSING) } }
        if (data.followUpsDue.isNotEmpty()) item { V3Priority("Follow-up jatuh tempo", "${data.followUpsDue.size} prospek perlu dihubungi", Icons.Default.Schedule) { onPage(V3Page.FOLLOW_UP) } }
        if (waitingDp == 0 && draftQuotes == 0 && data.followUpsDue.isEmpty()) item { V3Info("Tidak ada urgensi tercatat. Fokus tambah prospek baru dan dorong lead menuju QUALIFIED.") }

        item { V3SectionTitle("Notification Center", "Peringatan kerja, bukan data keuangan perusahaan") }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    V3NoticeLine(Icons.Default.Notifications, "${data.followUpsDue.size} follow-up jatuh tempo")
                    V3NoticeLine(Icons.Default.Description, "$draftQuotes quotation masih DRAFT")
                    V3NoticeLine(Icons.Default.HourglassTop, "$waitingDp lead menunggu DP")
                    V3NoticeLine(Icons.Default.TrendingUp, f.message)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPage(V3Page.ASSISTANT) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Sales Assistant")
                }
                OutlinedButton(onClick = { onPage(V3Page.SALES_KIT) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Campaign, null); Spacer(Modifier.width(6.dp)); Text("Sales Kit")
                }
            }
        }
    }
}

@Composable
private fun V3Crm(vm: SalesViewModel) {
    var showNew by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SalesLead?>(null) }
    var filter by remember { mutableStateOf("ACTIVE") }
    val rows = vm.dashboard.leads.filter {
        when (filter) {
            "HOT" -> it.stage in setOf("QUOTATION", "NEGOTIATION", "WAITING_DP")
            "WON" -> it.stage == "WON"
            "LOST" -> it.stage == "LOST"
            else -> it.stage !in setOf("WON", "LOST")
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CRM 360°", fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("Sekolah, PIC, kebutuhan, pipeline dan next action", fontSize = 10.sp, color = Color.Gray)
            }
            Button(onClick = { showNew = true }, enabled = !vm.actionBusy) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Lead") }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("ACTIVE", "HOT", "WON", "LOST").forEach { value ->
                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value, fontSize = 9.sp) })
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (rows.isEmpty()) item { V3Info("Belum ada lead pada kelompok ini.") }
            items(rows, key = { it.id }) { lead -> V3LeadCard(lead) { selected = lead } }
        }
    }
    if (showNew) {
        NewLeadDialog(vm.dashboard.catalog, vm.actionBusy, { showNew = false }) {
            vm.createLead(it); showNew = false
        }
    }
    selected?.let { lead ->
        V3LeadDialog(lead, vm.actionBusy, { selected = null }) { stage, next ->
            vm.updateLead(lead, stage, next); selected = null
        }
    }
}

@Composable
private fun V3FollowUp(vm: SalesViewModel) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<SalesLead?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { V3SectionTitle("Follow-up Center", "Jaga setiap prospek aktif selalu punya next action") }
        if (vm.dashboard.followUpsDue.isEmpty()) item { V3Info("Tidak ada follow-up jatuh tempo saat ini.") }
        else items(vm.dashboard.followUpsDue, key = { it.id }) { lead ->
            Card(shape = RoundedCornerShape(19.dp)) {
                Column(Modifier.padding(15.dp)) {
                    V3LeadSummary(lead)
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(onClick = { openV3WhatsApp(context, lead.whatsapp, followUpMessage(lead)) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Send, null); Spacer(Modifier.width(5.dp)); Text("WhatsApp")
                        }
                        Button(onClick = { selected = lead }, modifier = Modifier.weight(1f)) { Text("Update") }
                    }
                }
            }
        }
        item { V3SectionTitle("HOT Lead", "Quotation, negosiasi dan menunggu DP") }
        if (vm.dashboard.hotLeads.isEmpty()) item { V3Info("Belum ada HOT lead. Dorong qualified lead menuju quotation.") }
        else items(vm.dashboard.hotLeads, key = { "hot-${it.id}" }) { V3LeadCard(it) { selected = it } }
    }
    selected?.let { lead ->
        V3LeadDialog(lead, vm.actionBusy, { selected = null }) { stage, next ->
            vm.updateLead(lead, stage, next); selected = null
        }
    }
}

@Composable
private fun V3Closing(vm: SalesViewModel, onPage: (V3Page) -> Unit) {
    val context = LocalContext.current
    val quotes = vm.dashboard.quotations
    val waiting = vm.dashboard.leads.count { it.stage == "WAITING_DP" }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { V3SectionTitle("Closing Center", "Quotation → WhatsApp → DP → WON → Handover") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V3Metric("Quotation", quotes.size.toString(), Modifier.weight(1f))
                V3Metric("Waiting DP", waiting.toString(), Modifier.weight(1f))
                V3Metric("Paid booking", vm.dashboard.portfolio.paidBookings.toString(), Modifier.weight(1f))
            }
        }
        if (quotes.isEmpty()) item {
            V3Info("Belum ada quotation. Buat quotation dari Sales Kit setelah lead QUALIFIED.")
            Button(onClick = { onPage(V3Page.SALES_KIT) }, modifier = Modifier.fillMaxWidth()) { Text("Buka Sales Kit") }
        } else items(quotes, key = { it.id }) { q ->
            val lead = vm.dashboard.leads.firstOrNull { it.id == q.bookingRequestId }
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(q.quotationNo, fontWeight = FontWeight.Black)
                        V3Status(q.status)
                    }
                    Text(q.institutionName, fontWeight = FontWeight.Bold)
                    Text("${q.programName} • ${q.pax} pax", fontSize = 10.sp, color = Color.Gray)
                    Spacer(Modifier.height(5.dp))
                    Text(v3Rupiah(q.total), fontWeight = FontWeight.Black, color = V3Green, fontSize = 18.sp)
                    if (q.validUntil.isNotBlank()) Text("Berlaku sampai ${q.validUntil}", fontSize = 9.sp, color = Color.Gray)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { openV3WhatsApp(context, lead?.whatsapp.orEmpty(), quotationMessage(q, lead)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text(if (q.status == "SENT") "Kirim Ulang WhatsApp" else "Kirim via WhatsApp") }
                    if (q.status == "DRAFT") {
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { vm.markQuotationSent(q) }, enabled = !vm.actionBusy, modifier = Modifier.fillMaxWidth()) { Text("Tandai Sudah Terkirim") }
                    }
                    if (q.status == "SENT") {
                        Spacer(Modifier.height(7.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = V3SoftGold) {
                            Text("WAITING DP • Finance yang memvalidasi pembayaran", Modifier.padding(10.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item { V3SectionTitle("Booking & Status DP", "Sales hanya melihat status pembayaran, bukan saldo/kas perusahaan") }
        if (vm.dashboard.bookings.isEmpty()) item { V3Info("Belum ada booking yang teratribusi ke akun Sales ini.") }
        else items(vm.dashboard.bookings, key = { "booking-${it.id}" }) { b ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(b.bookingNo, fontWeight = FontWeight.Black)
                        V3Status(b.status.uppercase())
                    }
                    Text(b.programName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("${b.pax} pax • ${b.tripDate}", fontSize = 10.sp, color = Color.Gray)
                    Spacer(Modifier.height(7.dp))
                    Text(paymentLabelV3(b.paymentState), fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (b.paymentState == "DP_TERVERIFIKASI") V3Green else V3Gold)
                    if (b.paymentState == "DP_TERVERIFIKASI") Text("Siap handover ke Admin/Manager Ops.", fontSize = 9.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun V3More(onPage: (V3Page) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { V3SectionTitle("Workspace Lainnya", "Tools pendukung untuk closing dan produktivitas") }
        item { V3MenuCard(Icons.Default.Campaign, "Marketing & Sales Kit", "Program, materi, script, quotation dan funnel") { onPage(V3Page.SALES_KIT) } }
        item { V3MenuCard(Icons.Default.AttachMoney, "Komisi Saya", "Paid pax, komisi dan estimasi penghasilan") { onPage(V3Page.EARNINGS) } }
        item { V3MenuCard(Icons.Default.AutoAwesome, "Sales Assistant", "Prioritas lead, target dan draft chat berbasis pipeline") { onPage(V3Page.ASSISTANT) } }
        item { V3MenuCard(Icons.Default.AccountCircle, "Profil & Security", "Akun Sales dan logout") { onPage(V3Page.PROFILE) } }
        item { V3Info("Data HPP, laba perusahaan, saldo bank/kas, RAB internal dan payroll SDM lain tidak tersedia di Sales App.") }
    }
}

@Composable
private fun V3Earnings(data: SalesDashboard) {
    val p = data.portfolio
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { V3SectionTitle("My Earnings v3", "Penghasilan pribadi Sales dari paid pax teratribusi") }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = V3GreenDark)) {
                Column(Modifier.padding(20.dp)) {
                    Text("ESTIMASI BULAN INI", color = Color.White.copy(alpha = .7f), fontSize = 9.sp)
                    Text(v3Rupiah(p.modeledSalesIncome), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("${p.paidPax} paid pax • ${p.achievementLevel.replace('_', ' ')}", color = Color.White.copy(alpha = .82f), fontSize = 10.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(19.dp)) {
                Column(Modifier.padding(16.dp)) {
                    V3MoneyLine("Fixed / retainer", p.salesRetainer)
                    V3MoneyLine("Komisi paid pax", p.variableSalesFee)
                    V3MoneyLine("Bonus earned", p.targetBonusEarned)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    V3MoneyLine("Total modeled", p.modeledSalesIncome, true)
                }
            }
        }
        item { V3Info("Komisi tetap mengikuti validasi paid pax dan kebijakan perusahaan. Pembatalan/refund dapat mengubah eligibility.") }
    }
}

@Composable
private fun V3Assistant(data: SalesDashboard) {
    var answer by remember { mutableStateOf("Pilih pertanyaan di bawah. Assistant membaca pipeline Sales saat ini dan memberi prioritas kerja.") }
    val topLead = data.leads.filter { it.stage !in setOf("WON", "LOST") }.maxByOrNull { it.probabilityPct }
    val waiting = data.leads.filter { it.stage == "WAITING_DP" }
    val draft = data.quotations.filter { it.status == "DRAFT" }
    val f = data.forecast

    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { V3SectionTitle("Sales Assistant v3", "Smart assistant berbasis CRM dan pipeline aktif") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = V3SoftGreen)) {
                Column(Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = V3Green); Spacer(Modifier.width(7.dp)); Text("Rekomendasi", fontWeight = FontWeight.Black) }
                    Spacer(Modifier.height(8.dp))
                    Text(answer, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
        item {
            V3AssistantButton("Siapa yang harus saya follow-up?", Icons.Default.Schedule) {
                answer = when {
                    waiting.isNotEmpty() -> "Prioritas pertama: ${waiting.first().institutionName} (${waiting.first().pax} pax) sedang WAITING DP. Follow-up konfirmasi pembayaran tanpa menjanjikan status sebelum Finance memverifikasi."
                    data.followUpsDue.isNotEmpty() -> "Hubungi ${data.followUpsDue.first().institutionName} lebih dulu. Ada ${data.followUpsDue.size} follow-up jatuh tempo hari ini."
                    topLead != null -> "Belum ada follow-up overdue. Dorong ${topLead.institutionName} dari tahap ${topLead.stage} menuju langkah berikutnya."
                    else -> "Pipeline masih kosong. Tambahkan prospek sekolah/lembaga baru hari ini."
                }
            }
        }
        item {
            V3AssistantButton("Berapa target saya yang kurang?", Icons.Default.TrendingUp) {
                answer = "Paid pax saat ini ${data.portfolio.paidPax} dari target ${data.portfolio.targetPaidPax}. Sisa ${f.remainingPax} pax. Weighted forecast ${f.weightedForecastPax.toInt()} pax dan pipeline terbuka ${f.rawOpenPipelinePax} pax. ${f.message}"
            }
        }
        item {
            V3AssistantButton("Apa pekerjaan paling penting sekarang?", Icons.Default.Flag) {
                answer = when {
                    waiting.isNotEmpty() -> "Kejar ${waiting.size} lead WAITING DP terlebih dahulu karena paling dekat menjadi paid pax."
                    draft.isNotEmpty() -> "Ada ${draft.size} quotation DRAFT. Kirim ke PIC dan tandai terkirim setelah benar-benar terkirim."
                    data.followUpsDue.isNotEmpty() -> "Selesaikan ${data.followUpsDue.size} follow-up jatuh tempo sebelum prospecting baru."
                    else -> "Tambah prospek baru, lalu pastikan setiap lead memiliki next follow-up."
                }
            }
        }
        item {
            V3AssistantButton("Buat contoh chat untuk lead teratas", Icons.Default.Chat) {
                answer = if (topLead == null) "Belum ada lead aktif untuk dibuatkan chat." else followUpMessage(topLead)
            }
        }
        item { V3Info("Assistant v3 saat ini memakai data CRM dan aturan Sales GMU secara lokal. Ia tidak mengubah harga, diskon, status pembayaran, atau data ERP tanpa aksi pengguna.") }
    }
}

@Composable
private fun V3Profile(session: SalesSession, vm: SalesViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { V3SectionTitle("Profil & Security", "Akun kerja GMU EduTrans") }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(20.dp)) {
                    V3Brand(Modifier.width(150.dp).height(90.dp))
                    Text(session.profile.fullName, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    Text("Sales / Education Partnership", color = V3Green, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Akses: CRM pribadi, follow-up, Sales Kit, quotation, booking sendiri, status DP dan komisi pribadi.", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Text("Keluar dari Sales App") }
                }
            }
        }
    }
}

@Composable
private fun V3LeadCard(lead: SalesLead, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(19.dp)) {
        Column(Modifier.padding(15.dp)) { V3LeadSummary(lead) }
    }
}

@Composable
private fun V3LeadSummary(lead: SalesLead) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(lead.institutionName, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (lead.picName.isNotBlank()) Text("PIC ${lead.picName}", fontSize = 9.sp, color = Color.Gray)
        }
        V3Status(lead.stage)
    }
    Spacer(Modifier.height(5.dp))
    Text("${lead.pax} pax • ${lead.programName}", color = V3GreenDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    if (lead.nextFollowUpAt.isNotBlank()) Text("Next: ${lead.nextFollowUpAt.take(16).replace('T', ' ')}", fontSize = 9.sp, color = Color.Gray)
    Text("Probability ${lead.probabilityPct.toInt()}%", fontSize = 9.sp, color = V3Gold, fontWeight = FontWeight.Bold)
}

@Composable
private fun V3LeadDialog(lead: SalesLead, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var stage by remember(lead.id) { mutableStateOf(lead.stage) }
    var next by remember(lead.id) { mutableStateOf(lead.nextFollowUpAt) }
    val stages = listOf("CONTACTED", "QUALIFIED", "QUOTATION", "NEGOTIATION", "WAITING_DP", "WON", "LOST", "NURTURE")
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(lead.institutionName, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("${lead.pax} pax • ${lead.bookingCode}", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                stages.forEach { value ->
                    FilterChip(selected = stage == value, onClick = { stage = value }, label = { Text(value.replace('_', ' '), fontSize = 9.sp) })
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(1, 3, 7).forEach { days ->
                        AssistChip(onClick = {
                            next = LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(days.toLong()).atTime(9, 0).atZone(ZoneId.of("Asia/Jakarta")).toOffsetDateTime().toString()
                        }, label = { Text("+$days hari") })
                    }
                }
                if (next.isNotBlank()) Text("Follow-up ${next.take(16).replace('T', ' ')}", fontSize = 9.sp, color = V3GreenDark)
            }
        },
        confirmButton = { Button(onClick = { onSave(stage, next.ifBlank { null }) }, enabled = !busy) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun V3Mission(label: String, actual: Int, target: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(11.dp)) {
            Text(label, fontSize = 8.sp, color = Color.Gray)
            Text("$actual/$target", fontSize = 17.sp, fontWeight = FontWeight.Black)
            LinearProgressIndicator(progress = (actual.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth().height(5.dp), color = V3Green, trackColor = V3Green.copy(alpha = .12f))
        }
    }
}

@Composable
private fun V3Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(11.dp)) { Text(label, fontSize = 8.sp, color = Color.Gray); Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun V3SectionTitle(title: String, subtitle: String) {
    Column { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Black); Text(subtitle, fontSize = 10.sp, color = Color.Gray) }
}

@Composable
private fun V3Priority(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(13.dp), color = V3SoftGold) { Icon(icon, null, tint = V3GreenDark, modifier = Modifier.padding(10.dp)) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(subtitle, fontSize = 10.sp, color = Color.Gray) }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun V3MenuCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) = V3Priority(title, subtitle, icon, onClick)

@Composable
private fun V3Info(message: String) {
    Card(shape = RoundedCornerShape(17.dp)) { Text(message, Modifier.padding(14.dp), fontSize = 11.sp, lineHeight = 17.sp, color = Color.Gray) }
}

@Composable
private fun V3NoticeLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = V3Green, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(text, fontSize = 11.sp, modifier = Modifier.weight(1f)) }
}

@Composable
private fun V3AssistantButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(icon, null); Spacer(Modifier.width(7.dp)); Text(label, modifier = Modifier.weight(1f)) }
}

@Composable
private fun V3MoneyLine(label: String, value: Double, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (strong) FontWeight.Black else FontWeight.Normal)
        Text(v3Rupiah(value), fontWeight = FontWeight.Black, color = if (strong) V3Green else V3Ink)
    }
}

@Composable
private fun V3Status(status: String) {
    val good = status in setOf("WON", "PAID", "COMPLETED", "CONFIRMED", "SENT")
    Surface(shape = RoundedCornerShape(100.dp), color = if (good) V3SoftGreen else V3SoftGold) {
        Text(status.replace('_', ' '), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = V3GreenDark)
    }
}

private fun paymentLabelV3(state: String): String = when (state) {
    "DP_TERVERIFIKASI" -> "DP TERVERIFIKASI"
    "MENUNGGU_VERIFIKASI" -> "MENUNGGU VERIFIKASI FINANCE"
    else -> "BELUM ADA PEMBAYARAN TERVERIFIKASI"
}

private fun followUpMessage(lead: SalesLead): String = buildString {
    append("Halo Bapak/Ibu")
    if (lead.picName.isNotBlank()) append(" ").append(lead.picName)
    append(", saya dari GMU EduTrans. Saya follow-up terkait rencana kegiatan ")
    append(lead.institutionName).append(" untuk sekitar ").append(lead.pax).append(" peserta")
    if (lead.programName.isNotBlank()) append(" pada program ").append(lead.programName)
    append(". Apakah ada informasi yang bisa kami bantu agar rencana kegiatan dapat dilanjutkan?")
}

private fun quotationMessage(q: SalesQuotation, lead: SalesLead?): String = buildString {
    append("Halo")
    lead?.picName?.takeIf { it.isNotBlank() }?.let { append(" Bapak/Ibu ").append(it) }
    append(",\n\nBerikut ringkasan penawaran resmi GMU EduTrans.\n")
    append("No: ").append(q.quotationNo).append("\n")
    append("Program: ").append(q.programName).append("\n")
    append("Peserta: ").append(q.pax).append(" pax\n")
    append("Total: ").append(v3Rupiah(q.total)).append("\n")
    if (q.validUntil.isNotBlank()) append("Berlaku sampai: ").append(q.validUntil).append("\n")
    append("\nSilakan konfirmasi bila sudah sesuai. Status pembayaran baru dianggap valid setelah diverifikasi Finance GMU EduTrans.")
}

private fun normalizeV3WhatsApp(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return when {
        digits.startsWith("62") -> digits
        digits.startsWith("0") -> "62${digits.drop(1)}"
        digits.startsWith("8") -> "62$digits"
        else -> digits
    }
}

private fun openV3WhatsApp(context: Context, rawNumber: String, text: String) {
    val number = normalizeV3WhatsApp(rawNumber)
    if (number.isBlank()) {
        val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        context.startActivity(Intent.createChooser(share, "Bagikan pesan"))
        return
    }
    val url = "https://wa.me/$number?text=${Uri.encode(text)}"
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun v3Rupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")
