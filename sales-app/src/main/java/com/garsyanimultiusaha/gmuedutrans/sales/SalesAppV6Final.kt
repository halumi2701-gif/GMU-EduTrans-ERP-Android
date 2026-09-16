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

private val FinalGreen = Color(0xFF128000)
private val FinalGreenDark = Color(0xFF07580F)
private val FinalGold = Color(0xFFD5A300)
private val FinalBg = Color(0xFFF5F7F3)
private val FinalSoftGreen = Color(0xFFEAF6E8)
private val FinalSoftGold = Color(0xFFFFF4D4)

private enum class FinalPage {
    HOME, CRM, ACTIVITY, CLOSING, MORE,
    VISIT, FOLLOW_UP, SALES_KIT, APPROVALS, REPEAT,
    PERFORMANCE, EARNINGS, NOTIFICATIONS, TRAINING, DOCUMENTS, COACH, PROFILE
}

@Composable
fun SalesAppV6Final(vm: SalesViewModel) {
    var page by remember { mutableStateOf(FinalPage.HOME) }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FinalGreen,
            secondary = FinalGold,
            background = FinalBg,
            surface = Color.White,
            onPrimary = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = FinalBg) {
            when (val state = vm.state) {
                SalesAppState.Splash -> FinalSplash()
                SalesAppState.LoggedOut -> FinalLogin(vm)
                SalesAppState.Loading -> FinalLoading()
                is SalesAppState.Error -> FinalError(state.message, vm::backToLogin)
                is SalesAppState.LoggedIn -> FinalShell(vm, state.session, page) { page = it }
            }
        }
    }
}

@Composable
private fun FinalBrand(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.sales_logo), "GMU EduTrans", modifier)
}

@Composable
private fun FinalSplash() {
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FinalBrand(Modifier.width(220.dp).height(132.dp))
            CircularProgressIndicator(color = FinalGreen)
            Spacer(Modifier.height(9.dp))
            Text("Sales App v6", color = FinalGreenDark, fontWeight = FontWeight.Black)
            Text("Complete Sales Operating System", color = Color.Gray, fontSize = 10.sp)
        }
    }
}

@Composable
private fun FinalLogin(vm: SalesViewModel) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp).widthIn(max = 430.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FinalBrand(Modifier.width(190.dp).height(112.dp))
                Text("Sales Operating System v6", fontWeight = FontWeight.Black, color = FinalGreenDark)
                Text("Target • CRM • Activity • Closing • Report • Komisi", fontSize = 9.sp, color = Color.Gray)
                Spacer(Modifier.height(17.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email Sales") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Button(onClick = { vm.login(email,password) }, enabled = email.isNotBlank() && password.isNotBlank() && !vm.actionBusy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Masuk sebagai Sales", fontWeight = FontWeight.Black)
                }
                TextButton(onClick = { finalOpenUrl(context,"https://wa.me/6287783906545?text=" + Uri.encode("Halo Admin GMU EduTrans, saya ingin mengajukan akun Sales App.")) }) { Text("Ajukan / Aktivasi Akun Sales") }
                Text("Akun baru harus disetujui GMU sebelum aktif.", fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun FinalLoading() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color=FinalGreen); Spacer(Modifier.height(8.dp)); Text("Menyinkronkan workspace Sales…",color=Color.Gray) } } }

@Composable
private fun FinalError(message:String,onBack:()->Unit) { Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center) { Card(shape=RoundedCornerShape(22.dp)) { Column(Modifier.padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally) { Text("Belum dapat masuk",fontWeight=FontWeight.Black,fontSize=19.sp); Text(message,color=Color.Gray,fontSize=11.sp); Spacer(Modifier.height(12.dp)); Button(onClick=onBack){Text("Kembali")} } } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinalShell(vm:SalesViewModel,session:SalesSession,page:FinalPage,onPage:(FinalPage)->Unit) {
    val bottom = listOf(
        Triple(FinalPage.HOME,Icons.Default.Home,"Home"),
        Triple(FinalPage.CRM,Icons.Default.Groups,"CRM"),
        Triple(FinalPage.ACTIVITY,Icons.Default.Checklist,"Activity"),
        Triple(FinalPage.CLOSING,Icons.Default.Flag,"Closing"),
        Triple(FinalPage.MORE,Icons.Default.MoreHoriz,"Lainnya")
    )
    Scaffold(
        topBar={ TopAppBar(title={ Column { Text("GMU EduTrans Sales v6",fontWeight=FontWeight.Black,fontSize=16.sp); Text(session.profile.fullName,fontSize=9.sp,color=Color.Gray) } },actions={ IconButton(onClick=vm::refresh,enabled=!vm.dataBusy){Icon(Icons.Default.Sync,"Sync")}; IconButton(onClick={onPage(FinalPage.PROFILE)}){Icon(Icons.Default.AccountCircle,"Profil")} }) },
        bottomBar={ NavigationBar { bottom.forEach { (target,icon,label)-> NavigationBarItem(selected=page==target,onClick={onPage(target)},icon={Icon(icon,label)},label={Text(label,fontSize=8.sp)}) } } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            vm.notice?.let { AssistChip(onClick=vm::consumeNotice,label={Text(it,maxLines=2,overflow=TextOverflow.Ellipsis)},modifier=Modifier.padding(horizontal=14.dp,vertical=3.dp)) }
            when(page) {
                FinalPage.HOME -> FinalHome(vm,onPage)
                FinalPage.CRM -> FinalCrm(vm)
                FinalPage.ACTIVITY -> V6ActivityCenter(vm)
                FinalPage.CLOSING -> FinalClosing(vm,onPage)
                FinalPage.MORE -> FinalMore(onPage)
                FinalPage.VISIT -> V6FieldVisitScreen(vm)
                FinalPage.FOLLOW_UP -> FinalFollowUp(vm)
                FinalPage.SALES_KIT -> MarketingKitScreen(vm)
                FinalPage.APPROVALS -> V6ApprovalCenter(vm)
                FinalPage.REPEAT -> FinalRepeat(vm.dashboard)
                FinalPage.PERFORMANCE -> V6PerformanceCenter(vm.dashboard)
                FinalPage.EARNINGS -> FinalEarnings(vm.dashboard)
                FinalPage.NOTIFICATIONS -> V6NotificationCenter(vm)
                FinalPage.TRAINING -> V6TrainingCenter()
                FinalPage.DOCUMENTS -> V6DocumentsCenter()
                FinalPage.COACH -> FinalCoach(vm.dashboard)
                FinalPage.PROFILE -> FinalProfile(session,vm)
            }
        }
    }
}

@Composable
private fun FinalHome(vm:SalesViewModel,onPage:(FinalPage)->Unit) {
    val d=vm.dashboard; val p=d.portfolio; val f=d.forecast
    val attendance=vm.fieldWorkspace.attendance
    val activeVisit=vm.fieldWorkspace.visits.firstOrNull{it.visitStatus=="CHECKED_IN"}
    val waitDp=d.leads.count{it.stage=="WAITING_DP"}; val draft=d.quotations.count{it.status=="DRAFT"}; val pending=vm.fieldWorkspace.priceRequests.count{it.status=="PENDING"}
    val today=LocalDate.now(ZoneId.of("Asia/Jakarta")).toString(); val newToday=d.leads.count{it.createdAt.startsWith(today)}
    LazyColumn(contentPadding=PaddingValues(16.dp,10.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
        item { Card(shape=RoundedCornerShape(25.dp),colors=CardDefaults.cardColors(containerColor=FinalGreenDark)) { Column(Modifier.padding(20.dp)) { Text("TARGET BULAN INI",color=Color.White.copy(alpha=.7f),fontSize=9.sp,fontWeight=FontWeight.Bold); Text("${p.paidPax} / ${p.targetPaidPax} paid pax",color=Color.White,fontWeight=FontWeight.Black,fontSize=29.sp); Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress=(p.paidPax.toFloat()/p.targetPaidPax.coerceAtLeast(1)).coerceIn(0f,1f),modifier=Modifier.fillMaxWidth().height(8.dp),color=FinalGold,trackColor=Color.White.copy(alpha=.18f)); Spacer(Modifier.height(6.dp)); Text("Sisa ${f.remainingPax} pax • weighted ${f.weightedForecastPax.toInt()} • coverage ${String.format(Locale.US,"%.1fx",f.pipelineCoverage)}",color=Color.White.copy(alpha=.85f),fontSize=9.sp) } } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) { FinalMetric("Lead baru",newToday.toString(),Modifier.weight(1f)); FinalMetric("Follow-up",d.followUpsDue.size.toString(),Modifier.weight(1f)); FinalMetric("WAITING DP",waitDp.toString(),Modifier.weight(1f)) } }
        item { FinalHeader("Status Kerja","Absensi, visit dan sinkronisasi hari ini") }
        item { Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=FinalSoftGreen)) { Column(Modifier.padding(16.dp)) { Text(if(attendance?.checkIn.isNullOrBlank()) "Belum Check-in Kerja" else "${attendance?.status} • ${attendance?.checkIn?.take(8)}${if(!attendance?.checkOut.isNullOrBlank()) "–${attendance?.checkOut?.take(8)}" else ""}",fontWeight=FontWeight.Black,color=FinalGreenDark); Text(if(activeVisit!=null) "Visit aktif: ${activeVisit.institutionName}" else "Tidak ada visit aktif",fontSize=10.sp,color=Color.Gray); Spacer(Modifier.height(8.dp)); Button(onClick={onPage(FinalPage.ACTIVITY)},modifier=Modifier.fillMaxWidth()){Text("Buka Activity Center")} } } }
        item { FinalHeader("Next Best Action","Kerjakan yang paling dekat menghasilkan closing") }
        if(waitDp>0) item { FinalAction(Icons.Default.Payments,"Kejar DP","$waitDp lead menunggu pembayaran"){onPage(FinalPage.CLOSING)} }
        if(draft>0) item { FinalAction(Icons.Default.Description,"Kirim quotation","$draft draft belum ditandai SENT"){onPage(FinalPage.CLOSING)} }
        if(d.followUpsDue.isNotEmpty()) item { FinalAction(Icons.Default.Schedule,"Follow-up overdue","${d.followUpsDue.size} prospek perlu next action"){onPage(FinalPage.FOLLOW_UP)} }
        if(pending>0) item { FinalAction(Icons.Default.Approval,"Cek approval","$pending permintaan harga menunggu keputusan"){onPage(FinalPage.APPROVALS)} }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) { OutlinedButton(onClick={onPage(FinalPage.VISIT)},modifier=Modifier.weight(1f)){Icon(Icons.Default.Place,null);Spacer(Modifier.width(4.dp));Text("Visit")}; Button(onClick={onPage(FinalPage.COACH)},modifier=Modifier.weight(1f)){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(4.dp));Text("Coach")} } }
        item { FinalInfo("Data perusahaan yang sensitif—HPP, RAB internal, margin, laba, saldo kas/bank dan payroll orang lain—tetap tidak ditampilkan di Sales App.") }
    }
}

@Composable
private fun FinalCrm(vm:SalesViewModel) {
    var search by remember{mutableStateOf("")}; var filter by remember{mutableStateOf("ACTIVE")}; var selected by remember{mutableStateOf<SalesLead?>(null)}; var showNew by remember{mutableStateOf(false)}
    val duplicatePhones=vm.dashboard.leads.filter{it.whatsapp.isNotBlank()}.groupBy{it.whatsapp.filter(Char::isDigit)}.filterValues{it.size>1}.size
    val rows=vm.dashboard.leads.filter { l ->
        val q=search.isBlank()||listOf(l.institutionName,l.picName,l.city,l.whatsapp,l.programName,l.bookingCode).any{it.contains(search,true)}
        val f=when(filter){"HOT"->l.stage in setOf("QUOTATION","NEGOTIATION","WAITING_DP");"WON"->l.stage=="WON";"LOST"->l.stage=="LOST";else->l.stage !in setOf("WON","LOST")}; q&&f
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp,9.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("CRM 360°",fontWeight=FontWeight.Black,fontSize=20.sp);Text("Search global sekolah, PIC, WA, program, booking",fontSize=9.sp,color=Color.Gray)};Button(onClick={showNew=true},enabled=!vm.actionBusy){Icon(Icons.Default.Add,null);Text("Lead")}}
        OutlinedTextField(search,{search=it},label={Text("Cari semua data CRM")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp))
        Row(Modifier.padding(horizontal=12.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("ACTIVE","HOT","WON","LOST").forEach{f->FilterChip(selected=filter==f,onClick={filter=f},label={Text(f,fontSize=8.sp)})}}
        if(duplicatePhones>0) Text("Peringatan: $duplicatePhones nomor WhatsApp muncul pada lebih dari satu lead. Periksa kemungkinan duplikat.",fontSize=9.sp,color=FinalGold,modifier=Modifier.padding(horizontal=16.dp))
        LazyColumn(contentPadding=PaddingValues(16.dp,4.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){if(rows.isEmpty())item{FinalInfo("Belum ada lead pada filter ini.")};items(rows,key={it.id}){l->FinalLeadCard(l){selected=l}}}
    }
    if(showNew) NewLeadDialog(vm.dashboard.catalog,vm.actionBusy,{showNew=false}){vm.createLead(it);showNew=false}
    selected?.let{lead->FinalLeadDialog(lead,vm.actionBusy,{selected=null}){stage,next->vm.updateLead(lead,stage,next);selected=null}}
}

@Composable
private fun FinalFollowUp(vm:SalesViewModel) {
    val context=LocalContext.current; var selected by remember{mutableStateOf<SalesLead?>(null)}
    LazyColumn(contentPadding=PaddingValues(16.dp,10.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{FinalHeader("Follow-up Center","Today, overdue, quotation, negotiation dan DP")}
        if(vm.dashboard.followUpsDue.isEmpty()) item{FinalInfo("Tidak ada follow-up jatuh tempo.")}
        else items(vm.dashboard.followUpsDue,key={it.id}){l->Card(shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp)){FinalLeadSummary(l);Spacer(Modifier.height(8.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){Button(onClick={finalOpenWa(context,l.whatsapp,"Halo ${l.picName.ifBlank{"Bapak/Ibu"}}, izin follow-up dari GMU EduTrans terkait kebutuhan ${l.programName} untuk sekitar ${l.pax} peserta. Apakah ada hal yang perlu kami bantu agar rencana kegiatannya dapat dilanjutkan?")},enabled=l.whatsapp.isNotBlank(),modifier=Modifier.weight(1f)){Text("WhatsApp")};OutlinedButton(onClick={selected=l},modifier=Modifier.weight(1f)){Text("Update")}}}}}
    }
    selected?.let{lead->FinalLeadDialog(lead,vm.actionBusy,{selected=null}){stage,next->vm.updateLead(lead,stage,next);selected=null}}
}

@Composable
private fun FinalClosing(vm:SalesViewModel,onPage:(FinalPage)->Unit) {
    val context=LocalContext.current; val leads=vm.dashboard.leads.associateBy{it.id}; val waitDp=vm.dashboard.leads.filter{it.stage=="WAITING_DP"}
    LazyColumn(contentPadding=PaddingValues(16.dp,10.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{FinalHeader("Closing Center","Quotation → Approval → WAITING DP → WON → Handover")}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){FinalMetric("Draft",vm.dashboard.quotations.count{it.status=="DRAFT"}.toString(),Modifier.weight(1f));FinalMetric("Sent",vm.dashboard.quotations.count{it.status=="SENT"}.toString(),Modifier.weight(1f));FinalMetric("Waiting DP",waitDp.size.toString(),Modifier.weight(1f))}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){OutlinedButton(onClick={onPage(FinalPage.SALES_KIT)},modifier=Modifier.weight(1f)){Text("Buat Quotation")};OutlinedButton(onClick={onPage(FinalPage.APPROVALS)},modifier=Modifier.weight(1f)){Text("Approval Harga")}}}
        items(vm.dashboard.quotations,key={it.id}){ q ->
            val l=leads[q.bookingRequestId]
            Card(shape=RoundedCornerShape(19.dp)){
                Column(Modifier.padding(15.dp)){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(q.quotationNo,fontWeight=FontWeight.Black);FinalPill(q.status)}
                    Text(q.institutionName,fontWeight=FontWeight.Bold,fontSize=11.sp)
                    Text("${q.programName} • ${q.pax} pax",fontSize=9.sp,color=Color.Gray)
                    Text(finalRupiah(q.total),color=FinalGreen,fontWeight=FontWeight.Black,fontSize=17.sp)
                    if(q.status=="DRAFT"){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            OutlinedButton(
                                onClick={
                                    if(l!=null){
                                        finalOpenWa(context,l.whatsapp,"Halo ${l.picName.ifBlank{"Bapak/Ibu"}}, berikut penawaran GMU EduTrans ${q.quotationNo} untuk ${q.programName}, ${q.pax} peserta, total ${finalRupiah(q.total)}. Berlaku sampai ${q.validUntil}.")
                                    }
                                },
                                enabled=l?.whatsapp?.isNotBlank()==true,
                                modifier=Modifier.weight(1f)
                            ){Text("Kirim WA")}
                            Button(onClick={vm.markQuotationSent(q)},enabled=!vm.actionBusy,modifier=Modifier.weight(1f)){Text("Tandai SENT")}
                        }
                    }
                }
            }
        }
        item{FinalHeader("Menunggu DP","Sales hanya melihat status; verifikasi tetap Finance/Manager")}
        if(waitDp.isEmpty())item{FinalInfo("Tidak ada lead WAITING DP.")}
        else items(waitDp,key={"dp-${it.id}"}){l->Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=FinalSoftGold)){Column(Modifier.padding(14.dp)){FinalLeadSummary(l);Spacer(Modifier.height(7.dp));Button(onClick={finalOpenWa(context,l.whatsapp,"Halo ${l.picName.ifBlank{"Bapak/Ibu"}}, izin mengingatkan proses DP untuk rencana kegiatan ${l.institutionName}. Setelah pembayaran terverifikasi, booking akan kami teruskan ke tim operasional GMU EduTrans.")},enabled=l.whatsapp.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Reminder DP")}}}}
        item{FinalInfo("Setelah pembayaran tervalidasi backend, lead dapat menjadi WON dan diteruskan ke ERP Manager/Ops. Sales tidak memperoleh akses ke rekening atau saldo perusahaan.")}
    }
}

@Composable
private fun FinalRepeat(data:SalesDashboard){val today=LocalDate.now(ZoneId.of("Asia/Jakarta"));val rows=data.bookings.filter{b->val t=runCatching{LocalDate.parse(b.tripDate)}.getOrNull();t!=null&&!t.isAfter(today)&&b.status.uppercase() in setOf("PAID","COMPLETED","CLOSED")};LazyColumn(contentPadding=PaddingValues(16.dp,10.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){item{FinalHeader("Repeat Order Radar","Customer selesai trip untuk ditawarkan kembali")};if(rows.isEmpty())item{FinalInfo("Belum ada customer pada Repeat Radar.")};items(rows,key={it.id}){b->Card(shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(15.dp)){Text(b.customerName.ifBlank{b.bookingNo},fontWeight=FontWeight.Black);Text(b.programName,fontSize=10.sp,color=Color.Gray);Text("${b.pax} pax • ${b.tripDate}",color=FinalGreenDark,fontWeight=FontWeight.Bold,fontSize=10.sp);Text("Saran: follow-up feedback, referral dan program semester berikutnya.",fontSize=9.sp,color=Color.Gray)}}}}}

@Composable
private fun FinalEarnings(data:SalesDashboard){val p=data.portfolio;LazyColumn(contentPadding=PaddingValues(16.dp,10.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{FinalHeader("My Earnings","Penghasilan Sales pribadi, bukan profit perusahaan")};item{Card(shape=RoundedCornerShape(23.dp),colors=CardDefaults.cardColors(containerColor=FinalGreenDark)){Column(Modifier.padding(19.dp)){Text("MODELED BULAN INI",color=Color.White.copy(alpha=.7f),fontSize=9.sp);Text(finalRupiah(p.modeledSalesIncome),color=Color.White,fontWeight=FontWeight.Black,fontSize=28.sp);Text("${p.paidPax} paid pax",color=Color.White.copy(alpha=.8f),fontSize=10.sp)}}};item{Card(shape=RoundedCornerShape(19.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){FinalMoney("Fixed/retainer",p.salesRetainer);FinalMoney("Komisi paid pax",p.variableSalesFee);FinalMoney("Bonus earned",p.targetBonusEarned);HorizontalDivider();FinalMoney("Total modeled",p.modeledSalesIncome)}}};item{FinalInfo("Eligibility komisi mengikuti paid pax yang tervalidasi dan dapat berubah jika transaksi dibatalkan/refund sesuai kebijakan perusahaan.")}}}

@Composable
private fun FinalCoach(data:SalesDashboard){val f=data.forecast;val best=data.leads.filter{it.stage !in setOf("WON","LOST")}.maxByOrNull{it.probabilityPct};val recommendation=when{data.leads.count{it.stage=="WAITING_DP"}>0->"Prioritas utama: kejar lead WAITING DP sebelum menambah diskon.";data.quotations.any{it.status=="DRAFT"}->"Ada quotation DRAFT. Kirim dulu sebelum membuat penawaran baru.";data.followUpsDue.isNotEmpty()->"Selesaikan follow-up jatuh tempo agar pipeline tidak dingin.";f.pipelineCoverage<3.0->"Pipeline coverage masih di bawah 3x target. Tambah prospek berkualitas.";else->"Pipeline cukup. Fokus lead probabilitas tertinggi dan percepat menuju pembayaran."};LazyColumn(contentPadding=PaddingValues(16.dp,10.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{FinalHeader("Smart Sales Coach","Rekomendasi berbasis pipeline aktual")};item{Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=FinalSoftGreen)){Text(recommendation,Modifier.padding(17.dp),fontWeight=FontWeight.Bold,color=FinalGreenDark)}};best?.let{l->item{Card(shape=RoundedCornerShape(19.dp)){Column(Modifier.padding(15.dp)){Text("Lead terdekat ke closing",fontWeight=FontWeight.Black);Text(l.institutionName,fontWeight=FontWeight.Bold,fontSize=17.sp);Text("${l.stage} • ${l.probabilityPct.toInt()}% • ${l.pax} pax",fontSize=10.sp,color=FinalGreenDark)}}}};item{FinalInfo("Coach tidak mengubah harga atau mengambil keputusan approval. Semua tindakan tetap dikonfirmasi Sales.")}}}

@Composable
private fun FinalMore(onPage:(FinalPage)->Unit){val menus=listOf(
    Triple(Icons.Default.Place,"Visit & Check-in","Kunjungan sekolah dan histori") to FinalPage.VISIT,
    Triple(Icons.Default.Schedule,"Follow-up Center","H+1/H+3/H+7 dan overdue") to FinalPage.FOLLOW_UP,
    Triple(Icons.Default.Campaign,"Marketing & Sales Kit","Program, materi, script, quotation") to FinalPage.SALES_KIT,
    Triple(Icons.Default.Approval,"Approval Harga","Special price request dan status") to FinalPage.APPROVALS,
    Triple(Icons.Default.History,"Repeat Order Radar","Customer completed untuk follow-up ulang") to FinalPage.REPEAT,
    Triple(Icons.Default.Assessment,"Performance Center","Funnel, conversion, simulator") to FinalPage.PERFORMANCE,
    Triple(Icons.Default.AttachMoney,"My Earnings","Paid pax dan komisi pribadi") to FinalPage.EARNINGS,
    Triple(Icons.Default.Notifications,"Notification Center","Peringatan kerja Sales") to FinalPage.NOTIFICATIONS,
    Triple(Icons.Default.School,"Training & SOP","Onboarding dan playbook Sales") to FinalPage.TRAINING,
    Triple(Icons.Default.Folder,"Documents & Help","Company profile, aturan, support") to FinalPage.DOCUMENTS,
    Triple(Icons.Default.AutoAwesome,"Smart Sales Coach","Prioritas dan diagnosis pipeline") to FinalPage.COACH,
    Triple(Icons.Default.AccountCircle,"Profil & Security","Akun kerja dan logout") to FinalPage.PROFILE
);LazyColumn(contentPadding=PaddingValues(16.dp,10.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{FinalHeader("Workspace Lengkap v6","Semua fungsi Sales dari hadir kerja sampai repeat order")};items(menus,key={it.first.second}){entry->val m=entry.first;Card(onClick={onPage(entry.second)},shape=RoundedCornerShape(18.dp)){Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(m.first,null,tint=FinalGreen);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(m.second,fontWeight=FontWeight.Black);Text(m.third,fontSize=9.sp,color=Color.Gray)};Icon(Icons.Default.ArrowForward,null,tint=Color.Gray)}}}}}

@Composable
private fun FinalProfile(session:SalesSession,vm:SalesViewModel){LazyColumn(contentPadding=PaddingValues(16.dp,10.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{FinalHeader("Profil & Security","Akun Sales resmi GMU EduTrans")};item{Card(shape=RoundedCornerShape(21.dp)){Column(Modifier.padding(18.dp)){FinalBrand(Modifier.width(140.dp).height(85.dp));Text(session.profile.fullName,fontWeight=FontWeight.Black,fontSize=20.sp);Text("Sales / Education Partnership",color=FinalGreen,fontWeight=FontWeight.Bold);Text("Akses dibatasi pada data Sales pribadi dan customer yang dialokasikan.",fontSize=10.sp,color=Color.Gray);Spacer(Modifier.height(12.dp));OutlinedButton(onClick=vm::logout,modifier=Modifier.fillMaxWidth()){Text("Logout")}}}};item{FinalInfo("Privacy Guard aktif: HPP, RAB internal, margin, laba, saldo bank/kas dan payroll SDM lain tidak tersedia pada aplikasi ini.")}}}

@Composable
private fun FinalLeadDialog(lead:SalesLead,busy:Boolean,onDismiss:()->Unit,onSave:(String,String?)->Unit){var stage by remember(lead.id){mutableStateOf(lead.stage)};var next by remember(lead.id){mutableStateOf(lead.nextFollowUpAt)};val stages=listOf("NEW","CONTACTED","QUALIFIED","QUOTATION","NEGOTIATION","WAITING_DP","WON","LOST","NURTURE");AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(lead.institutionName,fontWeight=FontWeight.Black)},text={Column{Text("${lead.pax} pax • ${lead.bookingCode}",fontSize=9.sp,color=Color.Gray);stages.chunked(3).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)){row.forEach{s->FilterChip(selected=stage==s,onClick={stage=s},label={Text(s.replace('_',' '),fontSize=7.sp)})}}};Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(1,3,7).forEach{d->AssistChip(onClick={next=finalFuture(d)},label={Text("+$d hari")})}}}},confirmButton={Button(onClick={onSave(stage,next.ifBlank{null})},enabled=!busy){Text("Simpan")}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text("Batal")}})}

@Composable private fun FinalLeadCard(l:SalesLead,onClick:()->Unit){Card(onClick=onClick,shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp)){FinalLeadSummary(l);if(l.nextFollowUpAt.isNotBlank())Text("Next ${l.nextFollowUpAt.take(16).replace('T',' ')}",fontSize=8.sp,color=Color.Gray)}}}
@Composable private fun FinalLeadSummary(l:SalesLead){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(l.institutionName,fontWeight=FontWeight.Black,maxLines=1,overflow=TextOverflow.Ellipsis);Text(listOf(l.picName,l.city).filter{it.isNotBlank()}.joinToString(" • "),fontSize=8.sp,color=Color.Gray)};FinalPill(l.stage)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${l.pax} pax",fontSize=10.sp,color=FinalGreen,fontWeight=FontWeight.Bold);Text("${l.probabilityPct.toInt()}%",fontSize=10.sp,color=FinalGold,fontWeight=FontWeight.Black)}}
@Composable private fun FinalMetric(label:String,value:String,modifier:Modifier=Modifier){Card(modifier,shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(11.dp)){Text(label,fontSize=7.sp,color=Color.Gray);Text(value,fontSize=16.sp,fontWeight=FontWeight.Black)}}}
@Composable private fun FinalHeader(title:String,subtitle:String){Column{Text(title,fontWeight=FontWeight.Black,fontSize=18.sp);Text(subtitle,fontSize=9.sp,color=Color.Gray)}}
@Composable private fun FinalInfo(text:String){Card(shape=RoundedCornerShape(16.dp)){Text(text,Modifier.padding(14.dp),fontSize=10.sp,lineHeight=16.sp,color=Color.Gray)}}
@Composable private fun FinalPill(text:String){Surface(shape=RoundedCornerShape(100.dp),color=if(text.uppercase() in setOf("WON","PAID","COMPLETED","CLOSED","ACCEPTED"))FinalSoftGreen else FinalSoftGold){Text(text.replace('_',' '),Modifier.padding(horizontal=7.dp,vertical=4.dp),fontSize=7.sp,fontWeight=FontWeight.Bold,color=FinalGreenDark)}}
@Composable private fun FinalAction(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,body:String,onClick:()->Unit){Card(onClick=onClick,shape=RoundedCornerShape(18.dp)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=FinalGreen);Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Black);Text(body,fontSize=9.sp,color=Color.Gray)};Icon(Icons.Default.ArrowForward,null,tint=Color.Gray)}}}
@Composable private fun FinalMoney(label:String,value:Double){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,fontSize=11.sp);Text(finalRupiah(value),fontWeight=FontWeight.Black,color=FinalGreen)}}

private fun finalFuture(days:Int)=LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(days.toLong()).atTime(9,0).atZone(ZoneId.of("Asia/Jakarta")).toOffsetDateTime().toString()
private fun finalRupiah(v:Double)=NumberFormat.getCurrencyInstance(Locale("id","ID")).format(v).replace(",00","")
private fun finalOpenUrl(context:Context,url:String){runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}
private fun finalOpenWa(context:Context,raw:String,message:String){val d=raw.filter(Char::isDigit);val n=when{d.startsWith("62")->d;d.startsWith("0")->"62${d.drop(1)}";else->d};if(n.isNotBlank())finalOpenUrl(context,"https://wa.me/$n?text=${Uri.encode(message)}")}
