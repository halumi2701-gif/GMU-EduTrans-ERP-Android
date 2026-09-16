package com.garsyanimultiusaha.gmuedutrans.sales

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val SuiteGreen = Color(0xFF128000)
private val SuiteGreenDark = Color(0xFF07580F)
private val SuiteGold = Color(0xFFD5A300)
private val SuiteSoftGreen = Color(0xFFEAF6E8)
private val SuiteSoftGold = Color(0xFFFFF4D4)

@Composable
fun V6ActivityCenter(vm: SalesViewModel) {
    var showReport by remember { mutableStateOf(false) }
    val ws = vm.fieldWorkspace
    val attendance = ws.attendance
    val activeVisit = ws.visits.firstOrNull { it.visitStatus == "CHECKED_IN" }
    val upcoming = vm.dashboard.leads
        .filter { it.nextFollowUpAt.isNotBlank() && it.stage !in setOf("WON", "LOST") }
        .sortedBy { it.nextFollowUpAt }
        .take(6)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item { SuiteHeader("Activity Center", "Absen kerja, agenda, visit, follow-up dan laporan harian") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SuiteSoftGreen)) {
                Column(Modifier.padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Absensi Hari Ini", fontWeight = FontWeight.Black, fontSize = 17.sp)
                            Text(attendance?.status ?: "Belum check-in", fontSize = 11.sp, color = Color.Gray)
                        }
                        Icon(Icons.Default.Fingerprint, null, tint = SuiteGreen, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Masuk: ${attendance?.checkIn?.take(8)?.ifBlank { "-" } ?: "-"}   •   Pulang: ${attendance?.checkOut?.take(8)?.ifBlank { "-" } ?: "-"}", fontWeight = FontWeight.Bold, color = SuiteGreenDark)
                    Spacer(Modifier.height(12.dp))
                    when {
                        attendance == null || attendance.checkIn.isBlank() -> Button(
                            onClick = { vm.attendanceCheckIn() }, enabled = !vm.actionBusy, modifier = Modifier.fillMaxWidth()
                        ) { Icon(Icons.Default.Login, null); Spacer(Modifier.width(6.dp)); Text("Check-in Kerja") }
                        attendance.checkOut.isBlank() -> Button(
                            onClick = { vm.attendanceCheckOut() }, enabled = !vm.actionBusy, modifier = Modifier.fillMaxWidth()
                        ) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(6.dp)); Text("Check-out Kerja") }
                        else -> AssistChip(onClick = {}, label = { Text("Absensi hari ini lengkap") }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) })
                    }
                }
            }
        }
        if (activeVisit != null) {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SuiteSoftGold)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Kunjungan Sedang Aktif", fontWeight = FontWeight.Black)
                        Text(activeVisit.institutionName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Check-in ${activeVisit.checkInAt.take(16).replace('T', ' ')}", fontSize = 10.sp, color = Color.Gray)
                        Text("Selesaikan dari menu Visit setelah pertemuan selesai.", fontSize = 10.sp, color = SuiteGreenDark)
                    }
                }
            }
        }
        item { SuiteHeader("Agenda & Kalender", "Next action terdekat dari CRM") }
        if (upcoming.isEmpty()) item { SuiteInfo("Belum ada agenda follow-up terjadwal.") }
        else items(upcoming, key = { "agenda-${it.id}" }) { lead ->
            Card(shape = RoundedCornerShape(17.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, null, tint = SuiteGreen)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(lead.institutionName, fontWeight = FontWeight.Bold)
                        Text("${lead.stage.replace('_',' ')} • ${lead.nextFollowUpAt.take(16).replace('T',' ')}", fontSize = 9.sp, color = Color.Gray)
                    }
                }
            }
        }
        item { SuiteHeader("Laporan Harian Otomatis", "Angka aktivitas dihitung dari sistem; Sales hanya menambahkan konteks") }
        item {
            Button(onClick = { showReport = true }, modifier = Modifier.fillMaxWidth(), enabled = !vm.actionBusy) {
                Icon(Icons.Default.Assignment, null); Spacer(Modifier.width(7.dp)); Text("Buat / Update Laporan Hari Ini")
            }
        }
        if (ws.reports.isEmpty()) item { SuiteInfo("Belum ada laporan harian tersimpan.") }
        else items(ws.reports.take(7), key = { it.id }) { r ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.reportDate, fontWeight = FontWeight.Black)
                        Text(r.attendanceStatus.ifBlank { "-" }, color = SuiteGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Lead ${r.newLeads} • Visit ${r.visitsCompleted} • Aktivitas ${r.followupActivities} • Quote ${r.quotationsCreated}/${r.quotationsSent} • WON ${r.wonLeads}", fontSize = 10.sp, color = Color.Gray)
                    if (r.tomorrowPlan.isNotBlank()) Text("Besok: ${r.tomorrowPlan}", fontSize = 10.sp, color = SuiteGreenDark)
                }
            }
        }
        item { SuiteInfo("Sync: ${if (vm.dataBusy) "sedang menyinkron" else "siap"}. Data Sales tersimpan di backend GMU dan dapat dipantau sesuai hak akses ERP.") }
    }
    if (showReport) {
        V6ReportDialog(vm.actionBusy, onDismiss = { showReport = false }) { obstacle, plan, notes ->
            vm.submitDailyReport(obstacle, plan, notes)
            showReport = false
        }
    }
}

@Composable
fun V6FieldVisitScreen(vm: SalesViewModel) {
    var checkout by remember { mutableStateOf<V6VisitRecord?>(null) }
    val activeVisit = vm.fieldWorkspace.visits.firstOrNull { it.visitStatus == "CHECKED_IN" }
    val activeLeads = vm.dashboard.leads
        .filter { it.stage !in setOf("WON", "LOST") }
        .sortedByDescending { it.probabilityPct }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SuiteHeader("Visit Mode", "Check-in saat tiba, check-out setelah pertemuan, lalu CRM diperbarui") }
        if (activeVisit != null) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SuiteSoftGold)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("VISIT AKTIF", color = SuiteGreenDark, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        Text(activeVisit.institutionName, fontWeight = FontWeight.Black, fontSize = 19.sp)
                        if (activeVisit.picName.isNotBlank()) Text("PIC ${activeVisit.picName}", fontSize = 10.sp, color = Color.Gray)
                        Text("Mulai ${activeVisit.checkInAt.take(16).replace('T',' ')}", fontSize = 10.sp, color = Color.Gray)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { checkout = activeVisit }, modifier = Modifier.fillMaxWidth(), enabled = !vm.actionBusy) {
                            Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("Check-out & Catat Hasil")
                        }
                    }
                }
            }
            item { SuiteInfo("Satu Sales hanya dapat memiliki satu visit aktif agar histori kunjungan tidak tumpang tindih.") }
        } else {
            item { SuiteInfo("Pilih lead tujuan kunjungan. GPS tidak direkam otomatis; verifikasi lokasi hanya boleh ditambahkan bila perusahaan mengaktifkannya dan pengguna memberi izin perangkat.") }
            items(activeLeads, key = { "visit-lead-${it.id}" }) { lead ->
                Card(shape = RoundedCornerShape(19.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(lead.institutionName, fontWeight = FontWeight.Black)
                                Text(listOf(lead.picName, lead.city).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 9.sp, color = Color.Gray)
                            }
                            Text("${lead.probabilityPct.toInt()}%", color = SuiteGold, fontWeight = FontWeight.Black)
                        }
                        Text("${lead.pax} pax • ${lead.stage.replace('_',' ')}", fontSize = 10.sp, color = SuiteGreenDark)
                        Spacer(Modifier.height(9.dp))
                        Button(onClick = { vm.visitCheckIn(lead) }, modifier = Modifier.fillMaxWidth(), enabled = !vm.actionBusy) {
                            Icon(Icons.Default.Place, null); Spacer(Modifier.width(6.dp)); Text("Check-in Kunjungan")
                        }
                    }
                }
            }
        }
        item { SuiteHeader("Riwayat Visit", "Aktivitas lapangan pribadi") }
        if (vm.fieldWorkspace.visits.isEmpty()) item { SuiteInfo("Belum ada histori kunjungan.") }
        else items(vm.fieldWorkspace.visits.take(10), key = { "visit-history-${it.id}" }) { visit ->
            Card(shape = RoundedCornerShape(17.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(visit.institutionName, fontWeight = FontWeight.Bold)
                        Text(visit.visitStatus.replace('_',' '), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SuiteGreen)
                    }
                    Text("${visit.checkInAt.take(16).replace('T',' ')}${if (visit.checkOutAt.isNotBlank()) " → ${visit.checkOutAt.take(16).replace('T',' ')}" else ""}", fontSize = 9.sp, color = Color.Gray)
                    if (visit.outcome.isNotBlank()) Text(visit.outcome, fontSize = 10.sp)
                }
            }
        }
    }
    checkout?.let { visit ->
        V6VisitCheckoutDialog(visit, vm.actionBusy, { checkout = null }) { stage, outcome, notes, next ->
            vm.visitCheckOut(visit, stage, outcome, notes, next)
            checkout = null
        }
    }
}

@Composable
fun V6PerformanceCenter(data: SalesDashboard) {
    var extraPax by remember { mutableStateOf("40") }
    val total = data.leads.size
    val contacted = data.leads.count { it.stage !in setOf("NEW") }
    val qualified = data.leads.count { it.stage in setOf("QUALIFIED","QUOTATION","NEGOTIATION","WAITING_DP","WON") }
    val quotes = data.quotations.size
    val won = data.leads.count { it.stage == "WON" }
    val lost = data.leads.count { it.stage == "LOST" }
    val closeRate = if (quotes == 0) 0.0 else won * 100.0 / quotes
    val avgPax = if (won == 0) 0.0 else data.leads.filter { it.stage == "WON" }.sumOf { it.pax }.toDouble() / won
    val sources = data.leads.groupingBy { it.source.ifBlank { "Tidak tercatat" } }.eachCount().entries.sortedByDescending { it.value }
    val add = extraPax.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val modeledIncome = data.portfolio.modeledSalesIncome + add * data.portfolio.salesFeePerPaidPax

    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { SuiteHeader("Performance Center", "Funnel, conversion, target simulator dan diagnosis pribadi") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuiteMetric("Lead", total.toString(), Modifier.weight(1f))
                SuiteMetric("Qualified", qualified.toString(), Modifier.weight(1f))
                SuiteMetric("WON", won.toString(), Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Funnel Pribadi", fontWeight = FontWeight.Black)
                    SuiteLine("Contacted", contacted.toString())
                    SuiteLine("Qualified", qualified.toString())
                    SuiteLine("Quotation", quotes.toString())
                    SuiteLine("Won", won.toString())
                    SuiteLine("Lost", lost.toString())
                    SuiteLine("Closing rate", String.format(Locale.US,"%.1f%%",closeRate))
                    SuiteLine("Rata-rata pax / WON", String.format(Locale.US,"%.1f",avgPax))
                    SuiteLine("Pipeline coverage", String.format(Locale.US,"%.1fx",data.forecast.pipelineCoverage))
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SuiteSoftGreen)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Target Simulator", fontWeight = FontWeight.Black)
                    Text("Simulasi penghasilan jika ada tambahan paid pax. Ini bukan laba perusahaan.", fontSize = 10.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(extraPax, { extraPax = it.filter(Char::isDigit) }, label = { Text("Tambahan paid pax") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(7.dp))
                    Text("Modeled income: ${suiteRupiah(modeledIncome)}", color = SuiteGreenDark, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }
        }
        item { SuiteHeader("Lead Source", "Pantau sumber prospek yang benar-benar menghasilkan pipeline") }
        if (sources.isEmpty()) item { SuiteInfo("Belum ada data sumber lead.") }
        else items(sources.take(8), key = { it.key }) { SuiteLine(it.key, it.value.toString()) }
    }
}

@Composable
fun V6ApprovalCenter(vm: SalesViewModel) {
    var showRequest by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SuiteHeader("Approval & Special Price", "Sales mengajukan; Manager/Director yang memutuskan") }
        item { SuiteInfo("Harga publik tetap menjadi default. Permintaan harga di bawah master harus memiliki alasan dan tidak membuka HPP/margin internal kepada Sales.") }
        item {
            Button(onClick = { showRequest = true }, modifier = Modifier.fillMaxWidth(), enabled = !vm.actionBusy) {
                Icon(Icons.Default.RequestQuote, null); Spacer(Modifier.width(6.dp)); Text("Ajukan Harga Khusus")
            }
        }
        if (vm.fieldWorkspace.priceRequests.isEmpty()) item { SuiteInfo("Belum ada permintaan harga khusus.") }
        else items(vm.fieldWorkspace.priceRequests, key = { it.id }) { r ->
            Card(shape = RoundedCornerShape(19.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.institutionName, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Text(r.status, fontSize = 9.sp, color = SuiteGreen, fontWeight = FontWeight.Black)
                    }
                    Text(r.packageName, fontSize = 10.sp, color = Color.Gray)
                    Text("Publik ${suiteRupiah(r.publicPricePerPax)} → diminta ${suiteRupiah(r.requestedPricePerPax)} / pax", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Alasan: ${r.reason}", fontSize = 10.sp, color = Color.Gray)
                    if (r.decisionNote.isNotBlank()) Text("Catatan keputusan: ${r.decisionNote}", fontSize = 10.sp, color = SuiteGreenDark)
                }
            }
        }
    }
    if (showRequest) V6SpecialPriceDialog(vm, onDismiss = { showRequest = false })
}

@Composable
fun V6NotificationCenter(vm: SalesViewModel) {
    val data = vm.dashboard
    val ws = vm.fieldWorkspace
    val draft = data.quotations.count { it.status == "DRAFT" }
    val waitDp = data.leads.count { it.stage == "WAITING_DP" }
    val pendingApproval = ws.priceRequests.count { it.status == "PENDING" }
    val noAttendance = ws.attendance?.checkIn.isNullOrBlank()
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { SuiteHeader("Notification Center", "Peringatan kerja yang membutuhkan tindakan") }
        if (noAttendance) item { SuiteAlert(Icons.Default.Fingerprint, "Belum check-in kerja", "Catat kehadiran dari Activity Center.") }
        if (data.followUpsDue.isNotEmpty()) item { SuiteAlert(Icons.Default.Schedule, "${data.followUpsDue.size} follow-up jatuh tempo", "Hubungi prospek dan tentukan next action.") }
        if (draft > 0) item { SuiteAlert(Icons.Default.Description, "$draft quotation masih DRAFT", "Kirim ke customer lalu tandai SENT.") }
        if (waitDp > 0) item { SuiteAlert(Icons.Default.Payments, "$waitDp lead WAITING DP", "Prioritaskan reminder dan validasi pembayaran.") }
        if (pendingApproval > 0) item { SuiteAlert(Icons.Default.Approval, "$pendingApproval approval harga menunggu", "Pantau keputusan Manager/Director.") }
        if (data.forecast.remainingPax > 0) item { SuiteAlert(Icons.Default.TrendingUp, "Sisa target ${data.forecast.remainingPax} pax", data.forecast.message) }
        if (!noAttendance && data.followUpsDue.isEmpty() && draft == 0 && waitDp == 0 && pendingApproval == 0) item { SuiteInfo("Tidak ada alert kritis saat ini. Fokus menambah pipeline dan menjaga follow-up.") }
    }
}

@Composable
fun V6TrainingCenter() {
    val modules = listOf(
        Triple("01 • Product Knowledge", "Kuasai program, fasilitas, minimum pax dan harga publik dari Sales Kit.", "Jangan menghafal HPP atau menjanjikan fasilitas yang tidak ada di master."),
        Triple("02 • Prospecting", "Target baseline: prospek baru setiap hari dan prioritaskan sekolah/lembaga yang relevan.", "Catat semua prospek di CRM agar pipeline terukur."),
        Triple("03 • Qualification", "Tanya pax, jenjang, tanggal, tujuan kegiatan, budget dan decision maker.", "Lead yang jelas kebutuhannya lebih cepat menuju quotation."),
        Triple("04 • Follow-up", "Setiap lead aktif wajib punya next action H+1/H+3/H+7 sesuai konteks.", "Hindari spam; sesuaikan pesan dengan histori customer."),
        Triple("05 • Quotation & Approval", "Gunakan harga master. Diskon/harga khusus diajukan melalui Approval Center.", "Sales tidak memiliki hak approve harga sendiri."),
        Triple("06 • Closing", "Pastikan program, tanggal, pax, harga, fasilitas dan DP jelas sebelum handover.", "Closing valid mengikuti pembayaran yang diverifikasi."),
        Triple("07 • Visit", "Check-in ketika tiba dan check-out setelah pertemuan, lalu catat hasil.", "Visit tanpa hasil/next action tidak dianggap selesai secara CRM."),
        Triple("08 • Repeat Order", "Setelah trip, minta feedback dan jadwalkan peluang semester berikutnya.", "Customer lama adalah sumber pipeline penting.")
    )
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { SuiteHeader("Training & SOP Center", "Onboarding singkat Sales Education Partnership") }
        items(modules, key = { it.first }) { m ->
            Card(shape = RoundedCornerShape(19.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(m.first, fontWeight = FontWeight.Black, color = SuiteGreenDark)
                    Text(m.second, fontSize = 11.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(m.third, fontSize = 10.sp, color = Color.Gray, lineHeight = 15.sp)
                }
            }
        }
        item { SuiteInfo("Training Center V6 berfungsi sebagai SOP cepat. Materi program dan harga selalu mengikuti master Sales Kit yang aktif.") }
    }
}

@Composable
fun V6DocumentsCenter() {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SuiteHeader("Documents, Help & App", "Dokumen yang diizinkan untuk Sales serta dukungan aplikasi") }
        item { SuiteDoc(Icons.Default.Business, "Company Profile", "Profil singkat PT Garsyani Multi Usaha / GMU EduTrans") { suiteShare(context, "GMU EduTrans — PT Garsyani Multi Usaha\nPartner program edukasi, edutrip, perjalanan rombongan dan layanan transportasi untuk sekolah/lembaga.\nhttps://edutrans.garsyanimultiusaha.site") } }
        item { SuiteDoc(Icons.Default.Rule, "Pricing Guardrail", "Harga publik dari master; special price wajib approval") { suiteShare(context, "Aturan Sales GMU EduTrans: gunakan harga publik/master aktif. Diskon, cashback, harga khusus atau fasilitas tambahan wajib approval Manager/Director.") } }
        item { SuiteDoc(Icons.Default.Language, "Website GMU EduTrans", "Buka kanal publik resmi") { suiteOpenUrl(context, "https://edutrans.garsyanimultiusaha.site") } }
        item { SuiteDoc(Icons.Default.SupportAgent, "Laporkan Bug / Minta Bantuan", "Hubungi kanal resmi GMU") { suiteOpenUrl(context, "https://wa.me/6287783906545?text=" + Uri.encode("Halo Admin GMU EduTrans, saya perlu bantuan terkait Sales App v${BuildConfig.VERSION_NAME}.")) } }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SuiteSoftGreen)) {
                Column(Modifier.padding(15.dp)) {
                    Text("Versi Aplikasi", fontWeight = FontWeight.Black)
                    Text("GMU EduTrans Sales App v${BuildConfig.VERSION_NAME}", color = SuiteGreenDark, fontWeight = FontWeight.Bold)
                    Text("Jika Admin mengumumkan build lebih baru, gunakan APK resmi terbaru agar fungsi backend tetap kompatibel.", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
        item { SuiteInfo("Dokumen HPP, RAB internal, profit, saldo bank/kas, payroll orang lain dan data keuangan perusahaan tidak tersedia di Sales App.") }
    }
}

@Composable
private fun V6ReportDialog(busy: Boolean, onDismiss: () -> Unit, onSave: (String?, String?, String?) -> Unit) {
    var obstacles by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Laporan Harian", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lead, visit, follow-up dan quotation dihitung otomatis.", fontSize = 10.sp, color = Color.Gray)
                OutlinedTextField(obstacles, { obstacles = it }, label = { Text("Kendala hari ini") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(plan, { plan = it }, label = { Text("Rencana besok") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(notes, { notes = it }, label = { Text("Catatan tambahan") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { onSave(obstacles.ifBlank { null }, plan.ifBlank { null }, notes.ifBlank { null }) }, enabled = !busy) { Text("Simpan Laporan") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun V6VisitCheckoutDialog(visit: V6VisitRecord, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String?, String?, String?) -> Unit) {
    var stage by remember { mutableStateOf("QUALIFIED") }
    var outcome by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var next by remember { mutableStateOf(suiteFuture(3)) }
    val stages = listOf("CONTACTED","QUALIFIED","NEGOTIATION","NURTURE","LOST")
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Selesaikan Visit", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(visit.institutionName, fontWeight = FontWeight.Bold)
                Text("Hasil pertemuan wajib dicatat sebelum check-out.", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                stages.forEach { s -> FilterChip(selected = stage == s, onClick = { stage = s }, label = { Text(s) }) }
                OutlinedTextField(outcome, { outcome = it }, label = { Text("Hasil pertemuan") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(notes, { notes = it }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1,3,7).forEach { d -> AssistChip(onClick = { next = suiteFuture(d) }, label = { Text("+$d hari") }) }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(stage, outcome.ifBlank { null }, notes.ifBlank { null }, if (stage == "LOST") null else next) }, enabled = outcome.isNotBlank() && !busy) { Text("Check-out") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun V6SpecialPriceDialog(vm: SalesViewModel, onDismiss: () -> Unit) {
    val session = (vm.state as? SalesAppState.LoggedIn)?.session
    val scope = rememberCoroutineScope()
    val api = remember { SalesV6Api() }
    val leads = vm.dashboard.leads.filter { it.stage !in setOf("WON","LOST") && it.pax > 0 }
    var lead by remember { mutableStateOf<SalesLead?>(null) }
    var pack by remember { mutableStateOf<SalesPackage?>(null) }
    var leadOpen by remember { mutableStateOf(false) }
    var packOpen by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val packages = remember(lead, vm.dashboard.catalog) {
        val l = lead ?: return@remember emptyList<SalesPackage>()
        vm.dashboard.catalog
            .filter { l.programId.isBlank() || it.id == l.programId }
            .flatMap { it.packages }
            .filter { it.pricePerPax > 0 && l.pax >= it.minPax }
    }
    val requestValue = requested.toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Ajukan Harga Khusus", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { leadOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(lead?.institutionName ?: "Pilih lead", modifier = Modifier.weight(1f)) }
                    DropdownMenu(expanded = leadOpen, onDismissRequest = { leadOpen = false }) {
                        leads.forEach { l -> DropdownMenuItem(text = { Text("${l.institutionName} • ${l.pax} pax") }, onClick = { lead = l; pack = null; leadOpen = false }) }
                    }
                }
                Box {
                    OutlinedButton(onClick = { packOpen = true }, enabled = lead != null, modifier = Modifier.fillMaxWidth()) { Text(pack?.name ?: "Pilih paket", modifier = Modifier.weight(1f)) }
                    DropdownMenu(expanded = packOpen, onDismissRequest = { packOpen = false }) {
                        packages.forEach { p -> DropdownMenuItem(text = { Text("${p.name} • ${suiteRupiah(p.pricePerPax)}/pax") }, onClick = { pack = p; packOpen = false }) }
                    }
                }
                pack?.let { Text("Harga publik ${suiteRupiah(it.pricePerPax)} / pax", color = SuiteGreenDark, fontWeight = FontWeight.Bold) }
                OutlinedTextField(requested, { requested = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Harga yang diminta / pax") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reason, { reason = it }, label = { Text("Alasan customer meminta harga khusus") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = session ?: return@Button
                    val l = lead ?: return@Button
                    val p = pack ?: return@Button
                    busy = true
                    scope.launch {
                        runCatching { api.requestSpecialPrice(s, l.id, p.id, requestValue, reason) }
                            .onSuccess { vm.refreshFieldWorkspace(); onDismiss() }
                        busy = false
                    }
                },
                enabled = session != null && lead != null && pack != null && requestValue > 0 && requestValue < (pack?.pricePerPax ?: 0.0) && reason.isNotBlank() && !busy
            ) { Text(if (busy) "Mengirim…" else "Ajukan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun SuiteHeader(title: String, subtitle: String) { Column { Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp); Text(subtitle, fontSize = 10.sp, color = Color.Gray) } }
@Composable
private fun SuiteInfo(text: String) { Card(shape = RoundedCornerShape(17.dp)) { Text(text, Modifier.padding(15.dp), fontSize = 11.sp, lineHeight = 17.sp, color = Color.Gray) } }
@Composable
private fun SuiteMetric(label: String, value: String, modifier: Modifier = Modifier) { Card(modifier, shape = RoundedCornerShape(17.dp)) { Column(Modifier.padding(12.dp)) { Text(label, fontSize = 8.sp, color = Color.Gray); Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black) } } }
@Composable
private fun SuiteLine(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontSize = 11.sp, color = Color.Gray); Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
@Composable
private fun SuiteAlert(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) { Card(shape = RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon,null,tint=SuiteGreen); Spacer(Modifier.width(10.dp)); Column { Text(title,fontWeight=FontWeight.Black); Text(body,fontSize=10.sp,color=Color.Gray) } } } }
@Composable
private fun SuiteDoc(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) { Card(onClick=onClick,shape=RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically) { Icon(icon,null,tint=SuiteGreen); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(title,fontWeight=FontWeight.Black); Text(subtitle,fontSize=9.sp,color=Color.Gray) }; Icon(Icons.Default.ArrowForward,null,tint=Color.Gray) } } }

private fun suiteFuture(days: Int): String = LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(days.toLong()).atTime(9,0).atZone(ZoneId.of("Asia/Jakarta")).toOffsetDateTime().toString()
private fun suiteRupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id","ID")).format(value).replace(",00","")
private fun suiteOpenUrl(context: Context, url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
private fun suiteShare(context: Context, text: String) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,text) },"Bagikan GMU EduTrans")) }
