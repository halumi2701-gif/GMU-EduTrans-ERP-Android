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
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val C61Green = Color(0xFF128000)
private val C61GreenDark = Color(0xFF07580F)
private val C61Gold = Color(0xFFD5A300)
private val C61Bg = Color(0xFFF5F7F3)
private val C61SoftGreen = Color(0xFFEAF6E8)
private val C61SoftGold = Color(0xFFFFF4D4)
private val C61SoftRed = Color(0xFFFFE9E7)

private enum class C61Page {
    HOME, CRM, ACTIVITY, CLOSING, MORE,
    VISIT, FOLLOWUP, KIT, APPROVAL, REPEAT, PERFORMANCE,
    EARNINGS, NOTIFICATIONS, TRAINING, DOCUMENTS, COACH, PROFILE, INTEGRATION
}

@Composable
fun SalesAppV61(vm: SalesViewModel) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C61Green,
            secondary = C61Gold,
            background = C61Bg,
            surface = Color.White,
            onPrimary = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = C61Bg) {
            when (val state = vm.state) {
                SalesAppState.Splash -> C61Splash()
                SalesAppState.LoggedOut -> C61Login(vm)
                SalesAppState.Loading -> C61Loading()
                is SalesAppState.Error -> C61Error(state.message, vm::backToLogin)
                is SalesAppState.LoggedIn -> C61ConnectedRoot(vm, state.session)
            }
        }
    }
}

@Composable
private fun C61ConnectedRoot(vm: SalesViewModel, session: SalesSession) {
    val api = remember { SalesV61Api() }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(C61Page.HOME) }
    var connected by remember(session.userId) { mutableStateOf(V61ConnectedWorkspace()) }
    var connectedBusy by remember { mutableStateOf(true) }
    var remoteBusy by remember { mutableStateOf(false) }
    var localNotice by remember { mutableStateOf<String?>(null) }

    fun refreshConnected() {
        if (connectedBusy) return
        connectedBusy = true
        scope.launch {
            connected = api.loadWorkspace(session)
            connectedBusy = false
        }
    }

    LaunchedEffect(session.accessToken) {
        connectedBusy = true
        connected = api.loadWorkspace(session)
        connectedBusy = false
    }
    LaunchedEffect(vm.dashboard, vm.fieldWorkspace) {
        if (!connectedBusy) {
            connectedBusy = true
            connected = api.loadWorkspace(session)
            connectedBusy = false
        }
    }

    C61Shell(
        vm = vm,
        session = session,
        page = page,
        connected = connected,
        connectedBusy = connectedBusy,
        remoteBusy = remoteBusy,
        localNotice = localNotice,
        onDismissLocalNotice = { localNotice = null },
        onPage = { page = it },
        onRefresh = {
            vm.refresh()
            refreshConnected()
        },
        onUpdateLead = { lead, stage, next, lost ->
            if (!remoteBusy) {
                remoteBusy = true
                scope.launch {
                    runCatching { api.updateLead(session, lead.id, stage, next, lost) }
                        .onSuccess {
                            localNotice = "${lead.institutionName} diperbarui ke $stage."
                            vm.refresh()
                        }
                        .onFailure { localNotice = it.message ?: "Lead gagal diperbarui." }
                    connected = api.loadWorkspace(session)
                    remoteBusy = false
                }
            }
        },
        onReadNotice = { key ->
            scope.launch {
                runCatching { api.markNotificationRead(session, key) }
                connected = api.loadWorkspace(session)
            }
        },
        onCompleteLearning = { moduleId ->
            if (!remoteBusy) {
                remoteBusy = true
                scope.launch {
                    runCatching { api.markLearningComplete(session, moduleId) }
                        .onSuccess { localNotice = "Modul training ditandai selesai." }
                        .onFailure { localNotice = it.message ?: "Progress training gagal disimpan." }
                    connected = api.loadWorkspace(session)
                    remoteBusy = false
                }
            }
        },
        onSubmitFeedback = { row, score, feedback, consent, respondent ->
            if (!remoteBusy) {
                remoteBusy = true
                scope.launch {
                    runCatching { api.submitFeedback(session, row.bookingId, score, feedback, consent, respondent) }
                        .onSuccess { localNotice = "Feedback ${row.customerName} tersimpan ke backend GMU." }
                        .onFailure { localNotice = it.message ?: "Feedback gagal disimpan." }
                    connected = api.loadWorkspace(session)
                    remoteBusy = false
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun C61Shell(
    vm: SalesViewModel,
    session: SalesSession,
    page: C61Page,
    connected: V61ConnectedWorkspace,
    connectedBusy: Boolean,
    remoteBusy: Boolean,
    localNotice: String?,
    onDismissLocalNotice: () -> Unit,
    onPage: (C61Page) -> Unit,
    onRefresh: () -> Unit,
    onUpdateLead: (SalesLead, String, String?, String?) -> Unit,
    onReadNotice: (String) -> Unit,
    onCompleteLearning: (String) -> Unit,
    onSubmitFeedback: (V61RepeatOpportunity, Int, String?, Boolean, String?) -> Unit
) {
    val bottom = listOf(
        Triple(C61Page.HOME, Icons.Default.Home, "Home"),
        Triple(C61Page.CRM, Icons.Default.Groups, "CRM"),
        Triple(C61Page.ACTIVITY, Icons.Default.Checklist, "Activity"),
        Triple(C61Page.CLOSING, Icons.Default.Flag, "Closing"),
        Triple(C61Page.MORE, Icons.Default.MoreHoriz, "Lainnya")
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GMU EduTrans Sales v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text(session.profile.fullName, fontSize = 9.sp, color = Color.Gray)
                    }
                },
                actions = {
                    if (connectedBusy || vm.dataBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    IconButton(onClick = onRefresh, enabled = !connectedBusy && !vm.dataBusy) { Icon(Icons.Default.Sync, "Sync") }
                    IconButton(onClick = { onPage(C61Page.PROFILE) }) { Icon(Icons.Default.AccountCircle, "Profil") }
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
            localNotice?.let {
                AssistChip(onClick = onDismissLocalNotice, label = { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            }
            vm.notice?.let {
                AssistChip(onClick = vm::consumeNotice, label = { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            }
            when (page) {
                C61Page.HOME -> C61Home(vm, connected, onPage)
                C61Page.CRM -> C61Crm(vm, remoteBusy, onUpdateLead)
                C61Page.ACTIVITY -> V6ActivityCenter(vm)
                C61Page.CLOSING -> C61Closing(vm, connected, onPage)
                C61Page.MORE -> C61More(onPage)
                C61Page.VISIT -> V6FieldVisitScreen(vm)
                C61Page.FOLLOWUP -> C61FollowUp(vm, remoteBusy, onUpdateLead)
                C61Page.KIT -> MarketingKitScreen(vm)
                C61Page.APPROVAL -> V6ApprovalCenter(vm)
                C61Page.REPEAT -> C61Repeat(connected, remoteBusy, onSubmitFeedback)
                C61Page.PERFORMANCE -> V6PerformanceCenter(vm.dashboard)
                C61Page.EARNINGS -> C61Earnings(vm.dashboard)
                C61Page.NOTIFICATIONS -> C61Notifications(connected, onReadNotice)
                C61Page.TRAINING -> C61Training(connected, remoteBusy, onCompleteLearning)
                C61Page.DOCUMENTS -> C61Documents(connected)
                C61Page.COACH -> C61Coach(vm.dashboard)
                C61Page.PROFILE -> C61Profile(session, vm, connected)
                C61Page.INTEGRATION -> C61Integration(vm, connected)
            }
        }
    }
}

@Composable
private fun C61Home(vm: SalesViewModel, connected: V61ConnectedWorkspace, onPage: (C61Page) -> Unit) {
    val p = vm.dashboard.portfolio
    val f = vm.dashboard.forecast
    val unread = connected.notifications.count { !it.isRead }
    val critical = connected.notifications.count { it.severity == "HIGH" }
    val integrationOk = connected.integrationErrors.isEmpty()
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Card(shape = RoundedCornerShape(25.dp), colors = CardDefaults.cardColors(containerColor = C61GreenDark)) {
                Column(Modifier.padding(20.dp)) {
                    Text("TARGET BULAN INI", color = Color.White.copy(alpha = .7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("${p.paidPax} / ${p.targetPaidPax} paid pax", color = Color.White, fontWeight = FontWeight.Black, fontSize = 29.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = (p.paidPax.toFloat() / p.targetPaidPax.coerceAtLeast(1)).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth().height(8.dp), color = C61Gold, trackColor = Color.White.copy(alpha = .18f))
                    Spacer(Modifier.height(7.dp))
                    Text("Sisa ${f.remainingPax} pax • forecast ${f.weightedForecastPax.toInt()} • coverage ${String.format(Locale.US, "%.1fx", f.pipelineCoverage)}", color = Color.White.copy(alpha = .85f), fontSize = 9.sp)
                }
            }
        }
        item {
            Card(onClick = { onPage(C61Page.INTEGRATION) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (integrationOk) C61SoftGreen else C61SoftRed)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (integrationOk) Icons.Default.CloudDone else Icons.Default.CloudOff, null, tint = if (integrationOk) C61Green else Color.Red)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (integrationOk) "Backend End-to-End terhubung" else "Ada koneksi modul yang gagal", fontWeight = FontWeight.Black)
                        Text(if (integrationOk) "CRM • Finance state • Handover • Docs • Training • Repeat" else connected.integrationErrors.joinToString(" • "), fontSize = 9.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.ArrowForward, null)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                C61Metric("Alert High", critical.toString(), Modifier.weight(1f))
                C61Metric("Unread", unread.toString(), Modifier.weight(1f))
                C61Metric("Handover", connected.handovers.count { it.handoverStatus != "REVOKED" }.toString(), Modifier.weight(1f))
            }
        }
        item { C61Header("Next Best Action", "Feed langsung dari backend GMU") }
        if (connected.notifications.isEmpty()) item { C61Info("Tidak ada action feed aktif. Fokus tambah pipeline berkualitas.") }
        else items(connected.notifications.take(5), key = { it.key }) { n ->
            Card(onClick = {
                when (n.kind) {
                    "FOLLOW_UP" -> onPage(C61Page.FOLLOWUP)
                    "QUOTATION", "WAITING_DP", "HANDOVER" -> onPage(C61Page.CLOSING)
                    "APPROVAL" -> onPage(C61Page.APPROVAL)
                    "REPORT" -> onPage(C61Page.ACTIVITY)
                    else -> onPage(C61Page.NOTIFICATIONS)
                }
            }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (n.severity == "HIGH") C61SoftGold else Color.White)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = C61Green)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) { Text(n.title, fontWeight = FontWeight.Black); Text(n.message, fontSize = 9.sp, color = Color.Gray) }
                    Icon(Icons.Default.ArrowForward, null, tint = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun C61Crm(vm: SalesViewModel, busy: Boolean, onUpdate: (SalesLead, String, String?, String?) -> Unit) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ACTIVE") }
    var selected by remember { mutableStateOf<SalesLead?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val duplicatePhones = vm.dashboard.leads.filter { it.whatsapp.isNotBlank() }.groupBy { it.whatsapp.filter(Char::isDigit) }.count { it.value.size > 1 }
    val rows = vm.dashboard.leads.filter { l ->
        val q = search.isBlank() || listOf(l.institutionName, l.picName, l.city, l.whatsapp, l.programName, l.bookingCode).any { it.contains(search, true) }
        val f = when (filter) {
            "HOT" -> l.stage in setOf("QUOTATION", "NEGOTIATION", "WAITING_DP")
            "WON" -> l.stage == "WON"
            "LOST" -> l.stage == "LOST"
            else -> l.stage !in setOf("WON", "LOST")
        }
        q && f
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("CRM 360°", fontWeight = FontWeight.Black, fontSize = 20.sp); Text("Lead pribadi • guarded backend", fontSize = 9.sp, color = Color.Gray) }
            Button(onClick = { showNew = true }, enabled = !vm.actionBusy) { Icon(Icons.Default.Add, null); Text("Lead") }
        }
        OutlinedTextField(search, { search = it }, label = { Text("Cari sekolah, PIC, WA, program") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("ACTIVE", "HOT", "WON", "LOST").forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f, fontSize = 8.sp) }) }
        }
        if (duplicatePhones > 0) Text("$duplicatePhones nomor WhatsApp terdeteksi duplikat.", fontSize = 9.sp, color = C61Gold, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rows.isEmpty()) item { C61Info("Belum ada lead pada filter ini.") }
            items(rows, key = { it.id }) { l -> C61LeadCard(l) { selected = l } }
        }
    }
    if (showNew) C61NewLeadDialog(vm, onDismiss = { showNew = false }) { input -> vm.createLead(input); showNew = false }
    selected?.let { lead -> C61LeadUpdateDialog(lead, busy, { selected = null }) { stage, next, lost -> onUpdate(lead, stage, next, lost); selected = null } }
}

@Composable
private fun C61FollowUp(vm: SalesViewModel, busy: Boolean, onUpdate: (SalesLead, String, String?, String?) -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<SalesLead?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { C61Header("Follow-up Center", "Due dan overdue dari CRM backend") }
        if (vm.dashboard.followUpsDue.isEmpty()) item { C61Info("Tidak ada follow-up jatuh tempo.") }
        else items(vm.dashboard.followUpsDue, key = { it.id }) { l ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    C61LeadSummary(l)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { c61OpenWa(context, l.whatsapp, "Halo ${l.picName.ifBlank { "Bapak/Ibu" }}, izin follow-up dari GMU EduTrans terkait kebutuhan ${l.programName} untuk sekitar ${l.pax} peserta. Apakah ada hal yang perlu kami bantu agar rencana kegiatannya dapat dilanjutkan?") }, enabled = l.whatsapp.isNotBlank(), modifier = Modifier.weight(1f)) { Text("WhatsApp") }
                        OutlinedButton(onClick = { selected = l }, modifier = Modifier.weight(1f)) { Text("Update CRM") }
                    }
                }
            }
        }
    }
    selected?.let { lead -> C61LeadUpdateDialog(lead, busy, { selected = null }) { stage, next, lost -> onUpdate(lead, stage, next, lost); selected = null } }
}

@Composable
private fun C61Closing(vm: SalesViewModel, connected: V61ConnectedWorkspace, onPage: (C61Page) -> Unit) {
    val context = LocalContext.current
    val leads = vm.dashboard.leads.associateBy { it.id }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { C61Header("Closing Center", "Quotation → DP terverifikasi → WON → Handover Ops") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                C61Metric("Draft", vm.dashboard.quotations.count { it.status == "DRAFT" }.toString(), Modifier.weight(1f))
                C61Metric("Sent", vm.dashboard.quotations.count { it.status == "SENT" }.toString(), Modifier.weight(1f))
                C61Metric("DP Valid", vm.dashboard.bookings.count { it.paymentState == "DP_TERVERIFIKASI" }.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(onClick = { onPage(C61Page.KIT) }, modifier = Modifier.weight(1f)) { Text("Buat Quotation") }
                OutlinedButton(onClick = { onPage(C61Page.APPROVAL) }, modifier = Modifier.weight(1f)) { Text("Approval Harga") }
            }
        }
        item { C61Header("Quotation", "Harga berasal dari master aktif") }
        if (vm.dashboard.quotations.isEmpty()) item { C61Info("Belum ada quotation Sales.") }
        else items(vm.dashboard.quotations, key = { it.id }) { q ->
            val l = leads[q.bookingRequestId]
            Card(shape = RoundedCornerShape(19.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(q.quotationNo, fontWeight = FontWeight.Black); C61Pill(q.status) }
                    Text(q.institutionName, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("${q.programName} • ${q.pax} pax", fontSize = 9.sp, color = Color.Gray)
                    Text(c61Rupiah(q.total), color = C61Green, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    if (q.status == "DRAFT") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { if (l != null) c61OpenWa(context, l.whatsapp, "Halo ${l.picName.ifBlank { "Bapak/Ibu" }}, berikut penawaran GMU EduTrans ${q.quotationNo} untuk ${q.programName}, ${q.pax} peserta, total ${c61Rupiah(q.total)}. Berlaku sampai ${q.validUntil}.") }, enabled = l?.whatsapp?.isNotBlank() == true, modifier = Modifier.weight(1f)) { Text("Kirim WA") }
                            Button(onClick = { vm.markQuotationSent(q) }, enabled = !vm.actionBusy, modifier = Modifier.weight(1f)) { Text("Tandai SENT") }
                        }
                    }
                }
            }
        }
        item { C61Header("Payment State", "Status berasal dari pembayaran yang tercatat/terverifikasi Finance") }
        if (vm.dashboard.bookings.isEmpty()) item { C61Info("Belum ada booking terkonversi.") }
        else items(vm.dashboard.bookings.take(20), key = { "pay-${it.id}" }) { b ->
            val label = when (b.paymentState) {
                "DP_TERVERIFIKASI" -> "DP TERVERIFIKASI"
                "MENUNGGU_VERIFIKASI" -> "MENUNGGU VERIFIKASI"
                else -> "BELUM ADA PEMBAYARAN"
            }
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (b.paymentState == "DP_TERVERIFIKASI") C61SoftGreen else Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(b.customerName.ifBlank { b.bookingNo }, fontWeight = FontWeight.Black); C61Pill(label) }
                    Text("${b.bookingNo} • ${b.programName} • ${b.pax} pax", fontSize = 9.sp, color = Color.Gray)
                    if (b.paymentVerifiedAt.isNotBlank()) Text("Verified ${b.paymentVerifiedAt.take(16).replace('T', ' ')}", fontSize = 9.sp, color = C61GreenDark)
                }
            }
        }
        item { C61Header("Handover Ops", "Dibuat otomatis saat invoice DP menjadi PAID") }
        if (connected.handovers.isEmpty()) item { C61Info("Belum ada handover otomatis.") }
        else items(connected.handovers, key = { it.id }) { h ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (h.handoverStatus == "REVOKED") C61SoftRed else C61SoftGreen)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(h.institutionName, fontWeight = FontWeight.Black); C61Pill(h.handoverStatus) }
                    Text("${h.bookingNo} • Booking ${h.bookingStatus} • Request ${h.requestStatus}", fontSize = 9.sp, color = Color.Gray)
                    if (h.tripStatus.isNotBlank()) Text("Ops/Trip: ${h.tripStatus}", fontSize = 9.sp, color = C61GreenDark)
                }
            }
        }
        item { C61Info("Sales tidak dapat menandai WON manual. WON dan handover dibuat backend hanya setelah DP terverifikasi. Jika DP dibalik/refund, handover otomatis dapat direvoke.") }
    }
}

@Composable
private fun C61Notifications(connected: V61ConnectedWorkspace, onRead: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { C61Header("Notification Center", "Action feed dihitung langsung dari backend") }
        if (connected.notifications.isEmpty()) item { C61Info("Tidak ada notifikasi kerja aktif.") }
        else items(connected.notifications, key = { it.key }) { n ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (n.severity == "HIGH") C61SoftGold else Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(n.title, fontWeight = FontWeight.Black); C61Pill(n.severity) }
                    Text(n.message, fontSize = 10.sp, color = Color.Gray)
                    if (!n.isRead) TextButton(onClick = { onRead(n.key) }) { Text("Tandai dibaca") }
                    else Text("Sudah dibaca", fontSize = 8.sp, color = C61Green)
                }
            }
        }
    }
}

@Composable
private fun C61Training(connected: V61ConnectedWorkspace, busy: Boolean, onComplete: (String) -> Unit) {
    val done = connected.learningModules.count { it.completedAt.isNotBlank() }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { C61Header("Training & SOP", "$done/${connected.learningModules.size} modul selesai • konten dari backend") }
        if (connected.learningModules.isEmpty()) item { C61Info("Modul training belum termuat dari backend.") }
        else items(connected.learningModules, key = { it.id }) { m ->
            Card(shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = if (m.completedAt.isNotBlank()) C61SoftGreen else Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Text(m.title, fontWeight = FontWeight.Black, color = C61GreenDark)
                    Text(m.summary, fontSize = 11.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(m.content, fontSize = 10.sp, color = Color.Gray, lineHeight = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    if (m.completedAt.isBlank()) Button(onClick = { onComplete(m.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Tandai Selesai") }
                    else Text("✓ Selesai ${m.completedAt.take(10)}", color = C61Green, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun C61Documents(connected: V61ConnectedWorkspace) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { C61Header("Documents & Resources", "Resource pusat + dokumen customer dari backend") }
        items(connected.resources, key = { "res-${it.id}" }) { r ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text(r.title, fontWeight = FontWeight.Black)
                    Text(r.description, fontSize = 9.sp, color = Color.Gray)
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (r.url.isNotBlank()) OutlinedButton(onClick = { c61OpenUrl(context, r.url) }) { Text("Buka") }
                        if (r.shareText.isNotBlank()) Button(onClick = { c61Share(context, r.shareText) }) { Text("Bagikan") }
                    }
                }
            }
        }
        item { C61Header("Dokumen Booking Saya", "Hanya customer-visible document pada booking milik Sales") }
        if (connected.documents.isEmpty()) item { C61Info("Belum ada dokumen customer-visible.") }
        else items(connected.documents, key = { "doc-${it.id}" }) { d ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, tint = C61Green)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) { Text(d.title, fontWeight = FontWeight.Black); Text("${d.bookingNo} • ${d.status}", fontSize = 9.sp, color = Color.Gray) }
                    if (d.fileUrl.isNotBlank()) IconButton(onClick = { c61OpenUrl(context, d.fileUrl) }) { Icon(Icons.Default.OpenInNew, null) }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = C61SoftGreen)) {
                Column(Modifier.padding(15.dp)) {
                    Text("App Release Policy", fontWeight = FontWeight.Black)
                    Text("Terpasang ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • terbaru ${connected.releasePolicy.latestVersionName} (${connected.releasePolicy.latestVersionCode})", fontSize = 10.sp)
                    Text(if (connected.releasePolicy.updateRequired) "UPDATE WAJIB" else if (connected.releasePolicy.updateAvailable) "Update tersedia" else "Versi sesuai kebijakan backend", color = if (connected.releasePolicy.updateRequired) Color.Red else C61Green, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    if (connected.releasePolicy.releaseNotes.isNotBlank()) Text(connected.releasePolicy.releaseNotes, fontSize = 9.sp, color = Color.Gray)
                    if (connected.releasePolicy.releaseUrl.isNotBlank()) Button(onClick = { c61OpenUrl(context, connected.releasePolicy.releaseUrl) }, modifier = Modifier.fillMaxWidth()) { Text("Buka Update Resmi") }
                }
            }
        }
    }
}

@Composable
private fun C61Repeat(connected: V61ConnectedWorkspace, busy: Boolean, onSubmit: (V61RepeatOpportunity, Int, String?, Boolean, String?) -> Unit) {
    val context = LocalContext.current
    var feedbackRow by remember { mutableStateOf<V61RepeatOpportunity?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { C61Header("Repeat Order & Feedback", "Customer Completed/Closed dari ERP") }
        if (connected.repeatOpportunities.isEmpty()) item { C61Info("Belum ada customer completed/closed untuk repeat order.") }
        else items(connected.repeatOpportunities, key = { it.bookingId }) { r ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text(r.customerName, fontWeight = FontWeight.Black)
                    Text("${r.programName} • ${r.pax} pax • ${r.tripDate}", fontSize = 9.sp, color = Color.Gray)
                    if (r.overallScore != null) Text("Feedback ${r.overallScore}/5${if (r.testimonialConsent) " • izin testimonial" else ""}", color = C61GreenDark, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Spacer(Modifier.height(7.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { c61OpenWa(context, r.whatsapp, "Halo ${r.picName.ifBlank { "Bapak/Ibu" }}, terima kasih sudah berkegiatan bersama GMU EduTrans. Kami ingin memastikan pengalaman kegiatannya baik dan sekaligus membantu bila ada agenda semester berikutnya.") }, enabled = r.whatsapp.isNotBlank(), modifier = Modifier.weight(1f)) { Text("WhatsApp") }
                        Button(onClick = { feedbackRow = r }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(if (r.overallScore == null) "Catat Feedback" else "Update Feedback") }
                    }
                }
            }
        }
    }
    feedbackRow?.let { r -> C61FeedbackDialog(r, busy, { feedbackRow = null }) { score, text, consent, respondent -> onSubmit(r, score, text, consent, respondent); feedbackRow = null } }
}

@Composable
private fun C61Integration(vm: SalesViewModel, connected: V61ConnectedWorkspace) {
    val rows = listOf(
        "CRM/Lead" to vm.dashboard.leads.size,
        "Quotation" to vm.dashboard.quotations.size,
        "Booking/Payment" to vm.dashboard.bookings.size,
        "Attendance/Visit/Report" to (vm.fieldWorkspace.visits.size + vm.fieldWorkspace.reports.size + if (vm.fieldWorkspace.attendance != null) 1 else 0),
        "Approval Harga" to vm.fieldWorkspace.priceRequests.size,
        "Handover Ops" to connected.handovers.size,
        "Action Feed" to connected.notifications.size,
        "Training" to connected.learningModules.size,
        "Resources/Documents" to (connected.resources.size + connected.documents.size),
        "Repeat/Feedback" to connected.repeatOpportunities.size
    )
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { C61Header("Integration Health", "Audit koneksi end-to-end Sales App ↔ Supabase ↔ ERP") }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (connected.integrationErrors.isEmpty()) C61SoftGreen else C61SoftRed)) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (connected.integrationErrors.isEmpty()) "SEMUA MODUL BACKEND MERESPONS" else "ADA MODUL BERMASALAH", fontWeight = FontWeight.Black, color = if (connected.integrationErrors.isEmpty()) C61GreenDark else Color.Red)
                    if (connected.integrationErrors.isEmpty()) Text("Tidak ada kegagalan RPC pada sinkronisasi terakhir.", fontSize = 10.sp, color = Color.Gray)
                    else connected.integrationErrors.forEach { Text("• $it", fontSize = 10.sp, color = Color.Red) }
                }
            }
        }
        items(rows, key = { it.first }) { row -> C61Line(row.first, row.second.toString()) }
        item { C61Info("Koneksi dinyatakan sehat berdasarkan respons RPC/data saat sync terakhir. Jika satu modul gagal, aplikasi tetap login dan menampilkan modul mana yang bermasalah agar error tidak menyamar sebagai gagal login.") }
    }
}

@Composable
private fun C61Earnings(data: SalesDashboard) {
    val p = data.portfolio
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { C61Header("My Earnings", "Penghasilan Sales pribadi • berbasis paid pax tervalidasi") }
        item { Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = C61GreenDark)) { Column(Modifier.padding(18.dp)) { Text("MODELED BULAN INI", color = Color.White.copy(alpha = .7f), fontSize = 9.sp); Text(c61Rupiah(p.modeledSalesIncome), color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp); Text("${p.paidPax} paid pax", color = Color.White.copy(alpha = .8f)) } } }
        item { Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(15.dp)) { C61Money("Fixed/retainer", p.salesRetainer); C61Money("Komisi paid pax", p.variableSalesFee); C61Money("Bonus earned", p.targetBonusEarned); HorizontalDivider(); C61Money("Total modeled", p.modeledSalesIncome) } } }
        item { C61Info("Komisi mengikuti paid pax yang tervalidasi. Status WON tidak dapat dibuat manual oleh Sales.") }
    }
}

@Composable
private fun C61Coach(data: SalesDashboard) {
    val f = data.forecast
    val best = data.leads.filter { it.stage !in setOf("WON", "LOST") }.maxByOrNull { it.probabilityPct }
    val recommendation = when {
        data.leads.any { it.stage == "WAITING_DP" } -> "Prioritas: kejar WAITING DP. Jangan menambah diskon tanpa approval."
        data.quotations.any { it.status == "DRAFT" } -> "Kirim quotation DRAFT yang sudah siap sebelum membuat penawaran baru."
        data.followUpsDue.isNotEmpty() -> "Selesaikan follow-up jatuh tempo agar pipeline tidak dingin."
        f.pipelineCoverage < 3.0 -> "Pipeline coverage di bawah 3x target. Tambah prospek berkualitas."
        else -> "Pipeline cukup. Fokus pada lead probabilitas tertinggi dan percepat pembayaran."
    }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { C61Header("Smart Sales Coach", "Rule engine berbasis pipeline aktual") }
        item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = C61SoftGreen)) { Text(recommendation, Modifier.padding(17.dp), fontWeight = FontWeight.Bold, color = C61GreenDark) } }
        best?.let { l -> item { Card(shape = RoundedCornerShape(19.dp)) { Column(Modifier.padding(15.dp)) { Text("Lead terdekat ke closing", fontWeight = FontWeight.Black); Text(l.institutionName, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("${l.stage} • ${l.probabilityPct.toInt()}% • ${l.pax} pax", fontSize = 10.sp, color = C61GreenDark) } } } }
        item { C61Info("Coach hanya memberi prioritas. Harga, approval, pembayaran dan status WON tetap dikontrol backend.") }
    }
}

@Composable
private fun C61More(onPage: (C61Page) -> Unit) {
    val menus = listOf(
        Triple(Icons.Default.Place, "Visit & Check-in", "Kunjungan lapangan") to C61Page.VISIT,
        Triple(Icons.Default.Schedule, "Follow-up Center", "Due dan overdue") to C61Page.FOLLOWUP,
        Triple(Icons.Default.Campaign, "Marketing & Sales Kit", "Program, materi, script, quotation") to C61Page.KIT,
        Triple(Icons.Default.Approval, "Approval Harga", "Special price request") to C61Page.APPROVAL,
        Triple(Icons.Default.History, "Repeat & Feedback", "Post-trip, testimonial, repeat") to C61Page.REPEAT,
        Triple(Icons.Default.Assessment, "Performance", "Funnel dan simulator") to C61Page.PERFORMANCE,
        Triple(Icons.Default.AttachMoney, "My Earnings", "Paid pax dan komisi") to C61Page.EARNINGS,
        Triple(Icons.Default.Notifications, "Notifications", "Action feed backend") to C61Page.NOTIFICATIONS,
        Triple(Icons.Default.School, "Training & SOP", "Progress tersimpan") to C61Page.TRAINING,
        Triple(Icons.Default.Folder, "Documents & Resources", "Dokumen customer dan resource pusat") to C61Page.DOCUMENTS,
        Triple(Icons.Default.AutoAwesome, "Smart Coach", "Prioritas pipeline") to C61Page.COACH,
        Triple(Icons.Default.Hub, "Integration Health", "Audit koneksi end-to-end") to C61Page.INTEGRATION,
        Triple(Icons.Default.AccountCircle, "Profil & Security", "Akun Sales") to C61Page.PROFILE
    )
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { C61Header("Workspace v6.1", "Fitur operasional yang benar-benar terhubung") }
        items(menus, key = { it.first.second }) { entry ->
            val m = entry.first
            Card(onClick = { onPage(entry.second) }, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(m.first, null, tint = C61Green); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(m.second, fontWeight = FontWeight.Black); Text(m.third, fontSize = 9.sp, color = Color.Gray) }; Icon(Icons.Default.ArrowForward, null, tint = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun C61Profile(session: SalesSession, vm: SalesViewModel, connected: V61ConnectedWorkspace) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { C61Header("Profil & Security", "Akun kerja resmi GMU EduTrans") }
        item { Card(shape = RoundedCornerShape(21.dp)) { Column(Modifier.padding(18.dp)) { C61Brand(Modifier.width(140.dp).height(85.dp)); Text(session.profile.fullName, fontWeight = FontWeight.Black, fontSize = 20.sp); Text("Sales / Education Partnership", color = C61Green, fontWeight = FontWeight.Bold); Text("Role ${session.profile.role} • active ${session.profile.active}", fontSize = 9.sp, color = Color.Gray); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Text("Logout") } } } }
        item { C61Info("Privacy Guard aktif. HPP, RAB internal, margin, laba, saldo bank/kas dan payroll SDM lain tidak tersedia di Sales App.") }
        if (connected.releasePolicy.updateRequired) item { Card(colors = CardDefaults.cardColors(containerColor = C61SoftRed)) { Text("Versi aplikasi ini sudah di bawah minimum yang didukung. Segera gunakan build resmi terbaru.", Modifier.padding(14.dp), color = Color.Red, fontWeight = FontWeight.Bold) } }
    }
}

@Composable
private fun C61NewLeadDialog(vm: SalesViewModel, onDismiss: () -> Unit, onSave: (NewLeadInput) -> Unit) {
    var institution by remember { mutableStateOf("") }
    var pic by remember { mutableStateOf("") }
    var wa by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var pax by remember { mutableStateOf("20") }
    var program by remember { mutableStateOf<SalesProgram?>(null) }
    var programOpen by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(30).toString()) }
    AlertDialog(
        onDismissRequest = { if (!vm.actionBusy) onDismiss() },
        title = { Text("Tambah Lead", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(institution, { institution = it }, label = { Text("Sekolah / lembaga") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pic, { pic = it }, label = { Text("PIC") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(wa, { wa = it.filter { ch -> ch.isDigit() || ch == '+' } }, label = { Text("WhatsApp") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(city, { city = it }, label = { Text("Kota/Kabupaten") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pax, { pax = it.filter(Char::isDigit) }, label = { Text("Perkiraan pax") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("Rencana tanggal YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { programOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(program?.name ?: "Pilih program (opsional)", modifier = Modifier.weight(1f)) }
                    DropdownMenu(expanded = programOpen, onDismissRequest = { programOpen = false }) {
                        vm.dashboard.catalog.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { program = p; programOpen = false }) }
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(NewLeadInput(institutionName = institution, picName = pic, whatsapp = wa, city = city, programId = program?.id, customProgram = program?.name.orEmpty(), tripDate = date, pax = pax.toIntOrNull() ?: 0, budgetPerPax = null, notes = notes))
            }, enabled = institution.isNotBlank() && (pax.toIntOrNull() ?: 0) > 0 && runCatching { LocalDate.parse(date) }.isSuccess && !vm.actionBusy) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.actionBusy) { Text("Batal") } }
    )
}

@Composable
private fun C61LeadUpdateDialog(lead: SalesLead, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String?, String?) -> Unit) {
    var stage by remember(lead.id) { mutableStateOf(if (lead.stage in setOf("WON", "WAITING_DP")) "NEGOTIATION" else lead.stage) }
    var next by remember(lead.id) { mutableStateOf(lead.nextFollowUpAt) }
    var lostReason by remember { mutableStateOf("") }
    val stages = listOf("NEW", "CONTACTED", "QUALIFIED", "QUOTATION", "NEGOTIATION", "NURTURE", "LOST")
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(lead.institutionName, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("WON dan WAITING_DP tidak dapat dipilih manual. Status tersebut dikontrol workflow backend.", fontSize = 9.sp, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                stages.forEach { s -> FilterChip(selected = stage == s, onClick = { stage = s }, label = { Text(s.replace('_', ' '), fontSize = 8.sp) }) }
                if (stage == "LOST") OutlinedTextField(lostReason, { lostReason = it }, label = { Text("Alasan LOST (wajib)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf(1, 3, 7).forEach { d -> AssistChip(onClick = { next = c61Future(d) }, label = { Text("+$d hari") }) } }
            }
        },
        confirmButton = { Button(onClick = { onSave(stage, if (stage == "LOST") null else next.ifBlank { null }, lostReason.ifBlank { null }) }, enabled = !busy && (stage != "LOST" || lostReason.isNotBlank())) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun C61FeedbackDialog(row: V61RepeatOpportunity, busy: Boolean, onDismiss: () -> Unit, onSave: (Int, String?, Boolean, String?) -> Unit) {
    var score by remember { mutableStateOf(row.overallScore ?: 5) }
    var feedback by remember { mutableStateOf(row.feedback) }
    var consent by remember { mutableStateOf(row.testimonialConsent) }
    var respondent by remember { mutableStateOf(row.picName) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Feedback ${row.customerName}", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Nilai keseluruhan", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { (1..5).forEach { s -> FilterChip(selected = score == s, onClick = { score = s }, label = { Text("$s") }) } }
                OutlinedTextField(respondent, { respondent = it }, label = { Text("Nama responden/PIC") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(feedback, { feedback = it }, label = { Text("Feedback") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(consent, { consent = it }); Text("Customer mengizinkan testimoni dipublikasikan", fontSize = 10.sp) }
            }
        },
        confirmButton = { Button(onClick = { onSave(score, feedback.ifBlank { null }, consent, respondent.ifBlank { null }) }, enabled = !busy) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable private fun C61LeadCard(l: SalesLead, onClick: () -> Unit) { Card(onClick = onClick, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp)) { C61LeadSummary(l); if (l.nextFollowUpAt.isNotBlank()) Text("Next ${l.nextFollowUpAt.take(16).replace('T', ' ')}", fontSize = 8.sp, color = Color.Gray) } } }
@Composable private fun C61LeadSummary(l: SalesLead) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(l.institutionName, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(listOf(l.picName, l.city).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 8.sp, color = Color.Gray) }; C61Pill(l.stage) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${l.pax} pax", fontSize = 10.sp, color = C61Green, fontWeight = FontWeight.Bold); Text("${l.probabilityPct.toInt()}%", fontSize = 10.sp, color = C61Gold, fontWeight = FontWeight.Black) } }
@Composable private fun C61Metric(label: String, value: String, modifier: Modifier = Modifier) { Card(modifier, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(11.dp)) { Text(label, fontSize = 7.sp, color = Color.Gray); Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black) } } }
@Composable private fun C61Header(title: String, subtitle: String) { Column { Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp); Text(subtitle, fontSize = 9.sp, color = Color.Gray) } }
@Composable private fun C61Info(text: String) { Card(shape = RoundedCornerShape(16.dp)) { Text(text, Modifier.padding(14.dp), fontSize = 10.sp, lineHeight = 16.sp, color = Color.Gray) } }
@Composable private fun C61Pill(text: String) { Surface(shape = RoundedCornerShape(100.dp), color = if (text.uppercase() in setOf("WON", "PAID", "COMPLETED", "CLOSED", "ACCEPTED", "DP TERVERIFIKASI", "READY_FOR_OPS", "ACKNOWLEDGED", "IN_PROGRESS")) C61SoftGreen else C61SoftGold) { Text(text.replace('_', ' '), Modifier.padding(horizontal = 7.dp, vertical = 4.dp), fontSize = 7.sp, fontWeight = FontWeight.Bold, color = C61GreenDark) } }
@Composable private fun C61Line(label: String, value: String) { Card(shape = RoundedCornerShape(15.dp)) { Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontSize = 11.sp); Text(value, fontWeight = FontWeight.Black, color = C61Green) } } }
@Composable private fun C61Money(label: String, value: Double) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontSize = 11.sp); Text(c61Rupiah(value), fontWeight = FontWeight.Black, color = C61Green) } }

@Composable
private fun C61Brand(modifier: Modifier = Modifier) { Image(painterResource(R.drawable.sales_logo), "GMU EduTrans", modifier) }
@Composable private fun C61Splash() { Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { C61Brand(Modifier.width(220.dp).height(132.dp)); CircularProgressIndicator(color = C61Green); Spacer(Modifier.height(8.dp)); Text("Sales App v${BuildConfig.VERSION_NAME}", color = C61GreenDark, fontWeight = FontWeight.Black); Text("End-to-End Sales Operating System", color = Color.Gray, fontSize = 10.sp) } } }
@Composable private fun C61Loading() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = C61Green); Spacer(Modifier.height(8.dp)); Text("Menyinkronkan workspace Sales…", color = Color.Gray) } } }
@Composable private fun C61Error(message: String, onBack: () -> Unit) { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Card(shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Belum dapat masuk", fontWeight = FontWeight.Black, fontSize = 19.sp); Text(message, color = Color.Gray, fontSize = 11.sp); Spacer(Modifier.height(12.dp)); Button(onClick = onBack) { Text("Kembali") } } } } }

@Composable
private fun C61Login(vm: SalesViewModel) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp).widthIn(max = 430.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                C61Brand(Modifier.width(190.dp).height(112.dp))
                Text("Sales Operating System v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Black, color = C61GreenDark)
                Text("CRM • Activity • Closing • Handover • Report • Repeat", fontSize = 9.sp, color = Color.Gray)
                Spacer(Modifier.height(17.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email Sales") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Button(onClick = { vm.login(email, password) }, enabled = email.isNotBlank() && password.isNotBlank() && !vm.actionBusy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Masuk sebagai Sales", fontWeight = FontWeight.Black) }
                TextButton(onClick = { c61OpenUrl(context, "https://wa.me/6287783906545?text=" + Uri.encode("Halo Admin GMU EduTrans, saya ingin mengajukan/aktivasi akun Sales App.")) }) { Text("Ajukan / Aktivasi Akun Sales") }
                Text("Pembuatan akun tetap melalui approval Admin/Owner GMU.", fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}

private fun c61Future(days: Int) = LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(days.toLong()).atTime(9, 0).atZone(ZoneId.of("Asia/Jakarta")).toOffsetDateTime().toString()
private fun c61Rupiah(v: Double) = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(v).replace(",00", "")
private fun c61OpenUrl(context: Context, url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
private fun c61OpenWa(context: Context, raw: String, message: String) { val d = raw.filter(Char::isDigit); val n = when { d.startsWith("62") -> d; d.startsWith("0") -> "62${d.drop(1)}"; else -> d }; if (n.isNotBlank()) c61OpenUrl(context, "https://wa.me/$n?text=${Uri.encode(message)}") }
private fun c61Share(context: Context, text: String) { runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Bagikan")) } }
