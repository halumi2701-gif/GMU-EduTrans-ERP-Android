package com.garsyanimultiusaha.gmuedutrans.ops

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Green = Color(0xFF1C8202)
private val Dark = Color(0xFF125214)
private val Gold = Color(0xFFCE9603)
private val Ink = Color(0xFF203426)
private val Muted = Color(0xFF728276)
private val Border = Color(0xFFE0E9DF)
private val Pale = Color(0xFFEAF6EA)
private val Canvas = Color(0xFFF6F8F5)
private val Alert = Color(0xFFB42318)
private val Amber = Color(0xFFAE771D)
private val R20 = RoundedCornerShape(20.dp)
private val R14 = RoundedCornerShape(14.dp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary=Green, onPrimary=Color.White,
                background=Canvas, surface=Color.White, secondary=Gold, onSurface=Ink,
                outline=Border, error=Alert)) {
                Surface(Modifier.fillMaxSize(), color=Canvas) { OpsApp() }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Beranda", Icons.Rounded.Home),
    ORDERS("Kegiatan", Icons.Rounded.Event),
    READY("Checklist", Icons.Rounded.CheckCircle),
    FIELD("Hari-H", Icons.Rounded.People),
    REPORT("Laporan", Icons.Rounded.Description)
}

@Composable
private fun OpsApp(vm: OpsViewModel = viewModel()) {
    if (vm.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) {
            CircularProgressIndicator(color=Green)
        }
        return
    }
    if (vm.session == null) {
        Login(vm)
        return
    }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var showMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.error) {
        vm.error?.let { message ->
            snackbar.showSnackbar(message)
            vm.clearError()
        }
    }
    Scaffold(
        containerColor=Canvas,
        snackbarHost={ SnackbarHost(snackbar) },
        topBar={
            if (tab != Tab.HOME) {
                Surface(shadowElevation=2.dp, color=Color.White) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().height(62.dp).padding(horizontal=18.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tab.label, fontSize=20.sp, fontWeight=FontWeight.ExtraBold, color=Ink)
                            Text("GMU EduTrans Ops", fontSize=11.sp, color=Muted)
                        }
                        IconButton(onClick=vm::reload, enabled=!vm.busy) {
                            Icon(Icons.Rounded.Refresh, "Sinkron ulang", tint=Green)
                        }
                        Box {
                            IconButton(onClick={showMenu=true}) {
                                Icon(Icons.Rounded.MoreVert, "Menu", tint=Dark)
                            }
                            DropdownMenu(expanded=showMenu, onDismissRequest={showMenu=false}) {
                                DropdownMenuItem(text={Text(vm.session?.name ?: "Akun ERP")}, onClick={showMenu=false})
                                DropdownMenuItem(text={Text("Keluar dari Ops")}, onClick={
                                    showMenu=false
                                    vm.logout()
                                })
                            }
                        }
                    }
                }
            }
        },
        bottomBar={
            NavigationBar(modifier=Modifier.navigationBarsPadding(), containerColor=Color.White) {
                Tab.entries.forEach { dest ->
                    NavigationBarItem(
                        selected=tab==dest, onClick={tab=dest},
                        icon={Icon(dest.icon, dest.label)},
                        label={Text(dest.label, fontSize=10.sp)},
                        colors=NavigationBarItemDefaults.colors(
                            indicatorColor=Pale, selectedIconColor=Dark, selectedTextColor=Dark)
                    )
                }
            }
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when (tab) {
                Tab.HOME -> Home(vm) { tab=it }
                Tab.ORDERS -> Orders(vm) { tab=Tab.READY }
                Tab.READY -> Ready(vm)
                Tab.FIELD -> Field(vm)
                Tab.REPORT -> Report(vm)
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color=Gold)
        }
    }
}

@Composable
private fun BrandLogo(size: Int=64) {
    Surface(shape=RoundedCornerShape(16.dp), color=Color.White, shadowElevation=3.dp) {
        Image(painterResource(R.drawable.ic_launcher_gmu), "GMU EduTrans",
            Modifier.size(size.dp).padding(6.dp), contentScale=ContentScale.Fit)
    }
}

@Composable
private fun Login(vm: OpsViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(23.dp),
        verticalArrangement=Arrangement.Center) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            BrandLogo(76)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("GMU EduTrans", fontWeight=FontWeight.ExtraBold, color=Dark, fontSize=23.sp)
                Text("OPS • FIELD OPERATIONS", color=Gold, fontWeight=FontWeight.ExtraBold, fontSize=12.sp)
            }
        }
        Spacer(Modifier.height(26.dp))
        Text("Selamat datang", fontWeight=FontWeight.ExtraBold, fontSize=27.sp, color=Ink)
        Text("Masuk dengan akun ERP Anda.", fontSize=13.sp, color=Muted)
        Spacer(Modifier.height(18.dp))
        WhiteCard {
            Text("Login Staf Operasional", fontSize=17.sp, fontWeight=FontWeight.ExtraBold)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(email, onValueChange={email=it}, label={Text("Email ERP")},
                modifier=Modifier.fillMaxWidth(), shape=R14, singleLine=true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(password, onValueChange={password=it}, label={Text("Password")},
                modifier=Modifier.fillMaxWidth(), shape=R14, singleLine=true,
                visualTransformation=PasswordVisualTransformation())
            vm.error?.let { Text(it, modifier=Modifier.padding(top=10.dp), color=Alert, fontSize=11.sp) }
            Spacer(Modifier.height(17.dp))
            Button(onClick={vm.login(email, password)}, modifier=Modifier.fillMaxWidth().height(50.dp),
                shape=R14, enabled=!vm.busy && email.isNotBlank() && password.isNotBlank()) {
                Text(if (vm.busy) "Memproses…" else "Masuk ke Ops", fontWeight=FontWeight.ExtraBold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ArrowForward, null)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint=Green, modifier=Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Aplikasi Android native • Role-based access", fontSize=10.sp, color=Muted)
        }
    }
}

@Composable
private fun Home(vm: OpsViewModel, navigate: (Tab)->Unit) {
    val order=vm.selected
    val checks=order?.let { vm.evidence.readiness(it.id) }.orEmpty()
    val done=checks.count { it.second }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Dark,Green)),
            RoundedCornerShape(bottomStart=30.dp,bottomEnd=30.dp)
        ).statusBarsPadding().padding(start=18.dp,end=18.dp,top=18.dp,bottom=40.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                BrandLogo(56)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("GMU EduTrans Ops", fontSize=17.sp, color=Color.White, fontWeight=FontWeight.ExtraBold)
                    Text("FIELD OPERATIONS", color=Color.White.copy(alpha=.77f), fontSize=10.sp)
                }
                IconButton(onClick=vm::reload, enabled=!vm.busy) {
                    Icon(Icons.Rounded.Refresh, "Sinkron", tint=Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Halo, ${vm.session?.name?.substringBefore(" ") ?: "Staf Ops"} 👋",
                color=Color.White.copy(alpha=.8f), fontSize=12.sp)
            Text("Kontrol kegiatan hari ini", color=Color.White,
                fontSize=24.sp, fontWeight=FontWeight.ExtraBold)
            Text("Booking • Crew • Kesiapan • Pelaksanaan", color=Color.White.copy(alpha=.8f), fontSize=12.sp)
        }
        Column(Modifier.padding(horizontal=14.dp).offset(y=(-19).dp)) {
            WhiteCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("KEGIATAN TERPILIH", fontSize=10.sp, color=Green, fontWeight=FontWeight.Bold)
                        Text(order?.number ?: "Belum ada kegiatan", fontSize=18.sp,
                            color=Ink, fontWeight=FontWeight.ExtraBold)
                    }
                    Badge(order?.status ?: "Menunggu ERP")
                }
                Spacer(Modifier.height(8.dp))
                Text(order?.program ?: "Pilih order yang sudah dikonfirmasi dari ERP",
                    fontWeight=FontWeight.Bold, fontSize=14.sp)
                Text(order?.customer ?: "Tidak ada data contoh.", color=Muted, fontSize=11.sp)
                if (order!=null) {
                    Spacer(Modifier.height(9.dp))
                    Text("${order.date} • ${order.pax} pax", fontSize=12.sp, color=Muted)
                    Spacer(Modifier.height(12.dp))
                    Text("Bukti readiness: $done / ${checks.size}", fontSize=11.sp, fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    LinearProgressIndicator(
                        progress={ if (checks.isEmpty()) 0f else done.toFloat()/checks.size },
                        modifier=Modifier.fillMaxWidth().height(8.dp),
                        color=Green, trackColor=Pale)
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick={navigate(if(order==null)Tab.ORDERS else Tab.READY)},
                    modifier=Modifier.align(Alignment.End)) {
                    Text(if(order==null)"Pilih kegiatan" else "Cek kesiapan", color=Green)
                    Icon(Icons.Rounded.ArrowForward, null, tint=Green, modifier=Modifier.size(17.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Stat("${vm.orders.size}", "Order aktif", Icons.Rounded.Event, Modifier.weight(1f))
                Stat("${vm.evidence.people.count { it.bookingId==order?.id }}", "Manifest", Icons.Rounded.People, Modifier.weight(1f))
                Stat("${vm.evidence.documentCounts[order?.id] ?: 0}", "Dokumen", Icons.Rounded.Description, Modifier.weight(1f))
            }
            Heading("Aksi cepat", "Pekerjaan staf operasional")
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Tile("Kegiatan", "Order ERP", Icons.Rounded.Event, Modifier.weight(1f)) {navigate(Tab.ORDERS)}
                Tile("Persiapan H-1", "Checklist", Icons.Rounded.CheckCircle, Modifier.weight(1f)) {navigate(Tab.READY)}
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Tile("Kontrol Hari-H", "Peserta & Rundown", Icons.Rounded.People, Modifier.weight(1f)) {navigate(Tab.FIELD)}
                Tile("Laporan", "Bukti & Insiden", Icons.Rounded.Description, Modifier.weight(1f)) {navigate(Tab.REPORT)}
            }
            if(vm.evidence.warnings.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Notice("Sebagian data ERP belum dapat dimuat. Jangan menyatakan kegiatan siap sebelum verifikasi Manager.",true)
            }
            Spacer(Modifier.height(20.dp))
            Text("Satu Order ID • Satu sumber data ERP", fontSize=10.sp, color=Muted)
        }
    }
}

@Composable
private fun Stat(value: String,label: String, icon: ImageVector, modifier: Modifier) {
    Card(modifier,shape=R14,colors=CardDefaults.cardColors(containerColor=Color.White),border=BorderStroke(1.dp,Border)) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon,null,tint=Green,modifier=Modifier.size(20.dp))
            Spacer(Modifier.height(9.dp))
            Text(value,color=Ink,fontWeight=FontWeight.ExtraBold,fontSize=21.sp)
            Text(label,color=Muted,fontSize=10.sp)
        }
    }
}

@Composable
private fun Tile(title:String,subtitle:String,icon:ImageVector,modifier:Modifier,onClick:()->Unit) {
    Card(modifier.clickable(onClick=onClick),shape=R14,
        border=BorderStroke(1.dp,Border),colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.padding(15.dp)) {
            Box(Modifier.size(37.dp).background(Pale,R14),contentAlignment=Alignment.Center) {
                Icon(icon,null,tint=Dark,modifier=Modifier.size(21.dp))
            }
            Spacer(Modifier.height(11.dp))
            Text(title,fontWeight=FontWeight.Bold,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(subtitle,color=Muted,fontSize=10.sp)
        }
    }
}

@Composable
private fun Orders(vm:OpsViewModel,onPick:()->Unit) {
    var search by remember {mutableStateOf("")}
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Heading("Daftar kegiatan","Hanya booking operasional dari ERP")
        OutlinedTextField(search,onValueChange={search=it},
            modifier=Modifier.fillMaxWidth(),singleLine=true,shape=R14,
            label={Text("Cari sekolah, program, Order ID")},
            leadingIcon={Icon(Icons.Rounded.Search,null)})
        Spacer(Modifier.height(12.dp))
        val visible=vm.orders.filter {
            search.isBlank() || it.number.contains(search,true) ||
            it.program.contains(search,true) || it.customer.contains(search,true)
        }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),
            contentPadding=PaddingValues(bottom=20.dp)) {
            if(visible.isEmpty()) item {Notice("Belum ada order. Booking berstatus Confirmed, Preparation, Trip, Completed atau Closed akan tampil setelah sinkronisasi.")}
            items(visible,key={it.id}) {order->
                Card(Modifier.fillMaxWidth().clickable {vm.selectOrder(order.id);onPick()},
                    shape=R20,border=BorderStroke(1.dp,Border),
                    colors=CardDefaults.cardColors(containerColor=if(order.id==vm.selected?.id)Pale else Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(order.number,fontSize=12.sp,color=Green,fontWeight=FontWeight.ExtraBold)
                            Badge(order.status)
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(order.program,fontSize=16.sp,fontWeight=FontWeight.Bold)
                        Text(order.customer,fontSize=12.sp,color=Muted)
                        Spacer(Modifier.height(9.dp))
                        Text("${order.date} • ${order.pax} pax",fontSize=11.sp,color=Ink,fontWeight=FontWeight.Bold)
                        if(order.meetingPoint.isNotBlank()) Text(order.meetingPoint,fontSize=11.sp,color=Muted)
                        Spacer(Modifier.height(8.dp))
                        Text("Buka kontrol operasional →",fontSize=11.sp,fontWeight=FontWeight.Bold,color=Green)
                    }
                }
            }
        }
    }
}

@Composable
private fun Ready(vm:OpsViewModel) {
    val order=vm.selected
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Heading("Readiness H-3 / H-1","Berdasarkan bukti aktual di ERP")
        if(order==null) {Notice("Pilih order di tab Kegiatan.");return@Column}
        OrderHeader(order)
        val checks=vm.evidence.readiness(order.id)
        val done=checks.count {it.second}
        WhiteCard {
            Text("Pemeriksaan persiapan",fontWeight=FontWeight.ExtraBold,fontSize=16.sp)
            Text("$done / ${checks.size} bukti ditemukan",color=Muted,fontSize=11.sp)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress={done.toFloat()/checks.size},
                color=Green,trackColor=Pale,modifier=Modifier.fillMaxWidth().height(8.dp))
            Spacer(Modifier.height(13.dp))
            checks.forEach {(label,ok)->
                Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(if(ok)Icons.Rounded.CheckCircle else Icons.Rounded.Warning,null,
                        tint=if(ok)Green else Amber,modifier=Modifier.size(20.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(label,modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=12.sp)
                    Text(if(ok)"Ada" else "Belum",fontSize=11.sp,color=if(ok)Green else Amber)
                }
                HorizontalDivider(color=Border)
            }
            Spacer(Modifier.height(13.dp))
            Button(onClick=vm::reload,enabled=!vm.busy,modifier=Modifier.fillMaxWidth(),shape=R14) {
                Icon(Icons.Rounded.Refresh,null,modifier=Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sinkron dari ERP")
            }
        }
        Spacer(Modifier.height(11.dp))
        Notice("Indikator dokumen bukan keputusan GO. Keselamatan, tiket, crew, kebutuhan peserta, dan persetujuan Manager harus diverifikasi sebelum keberangkatan.",true)
        if(vm.evidence.warnings.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Notice("Data belum lengkap: ${vm.evidence.warnings.joinToString()}",true)
        }
    }
}

@Composable
private fun Field(vm:OpsViewModel) {
    val order=vm.selected
    var segment by remember {mutableStateOf("Rundown")}
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Heading("Kontrol Hari-H","Rundown, manifest, dan crew")
        if(order==null) {Notice("Pilih order di tab Kegiatan.");return@Column}
        OrderHeader(order)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Rundown","Absensi","Crew").forEach {item->
                FilterChip(selected=segment==item,onClick={segment=item},
                    label={Text(item,fontSize=11.sp)})
            }
        }
        Spacer(Modifier.height(7.dp))
        when(segment) {
            "Rundown" -> {
                val agenda=vm.evidence.agenda.filter{it.bookingId==order.id}.sortedBy{it.time}
                LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(agenda.isEmpty()) item {Notice("Rundown belum tersedia pada ERP.")}
                    items(agenda) {a->
                        WhiteCard {
                            Row(verticalAlignment=Alignment.Top) {
                                Surface(color=Pale,shape=R14) {
                                    Text(a.time.ifBlank{"--:--"},Modifier.padding(10.dp),color=Dark,fontSize=12.sp,fontWeight=FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(a.activity,fontSize=13.sp,fontWeight=FontWeight.Bold)
                                    if(a.location.isNotBlank()) Text(a.location,fontSize=11.sp,color=Muted)
                                    if(a.pic.isNotBlank()) Text("PIC: ${a.pic}",fontSize=11.sp,color=Green)
                                }
                            }
                        }
                    }
                }
            }
            "Absensi" -> {
                val people=vm.evidence.people.filter{it.bookingId==order.id}
                val present=people.count{p->vm.evidence.attendance.any{it.manifestId==p.id&&it.present}}
                Text("$present / ${people.size} peserta hadir",fontSize=13.sp,color=Dark,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(people.isEmpty()) item {Notice("Manifest peserta belum tersedia di ERP.")}
                    items(people,key={it.id}) {person->
                        val att=vm.evidence.attendance.firstOrNull{it.manifestId==person.id}
                        WhiteCard {
                            Text(person.name,fontSize=13.sp,fontWeight=FontWeight.Bold)
                            if(person.group.isNotBlank()) Text(person.group,fontSize=10.sp,color=Muted)
                            Spacer(Modifier.height(9.dp))
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={vm.setPresent(person,false)},enabled=!vm.busy,
                                    shape=R14,modifier=Modifier.weight(1f)) {
                                    Text(if(att?.present==false)"✓ Absen" else "Absen",fontSize=11.sp,color=Alert)
                                }
                                Button(onClick={vm.setPresent(person,true)},enabled=!vm.busy,
                                    shape=R14,modifier=Modifier.weight(1f)) {
                                    Text(if(att?.present==true)"✓ Hadir" else "Hadir",fontSize=11.sp)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                val sheet=vm.evidence.sheetRows[order.id]
                WhiteCard {
                    Text("Operation Sheet",fontSize=15.sp,fontWeight=FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("PIC Ops",sheet?.optString("operation_pic") ?: "Belum diisi")
                    InfoRow("Meeting time",sheet?.optString("meeting_time") ?: "Belum diisi")
                    InfoRow("Transportasi",sheet?.optString("transport") ?: "Belum diisi")
                    InfoRow("Kontak driver",sheet?.optString("driver_contact") ?: "Belum diisi")
                    InfoRow("Perlengkapan",sheet?.optString("equipment") ?: "Belum diisi")
                    InfoRow("Status kesiapan",sheet?.optString("readiness_status") ?: "Belum diisi")
                }
                Spacer(Modifier.height(10.dp))
                Notice("Penugasan crew dan jadwal resmi tetap ditetapkan melalui ERP.")
            }
        }
    }
}

@Composable
private fun Report(vm:OpsViewModel) {
    val order=vm.selected
    val context=LocalContext.current
    var level by remember(order?.id){mutableStateOf("Level 1 — Minor")}
    var story by remember(order?.id){mutableStateOf("")}
    var action by remember(order?.id){mutableStateOf("")}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Heading("Laporan & Bukti","Dokumentasi, evaluasi, dan eskalasi")
        if(order==null){Notice("Pilih order di tab Kegiatan.");return@Column}
        OrderHeader(order)
        WhiteCard {
            Text("Status laporan ERP",fontSize=15.sp,fontWeight=FontWeight.ExtraBold)
            Spacer(Modifier.height(9.dp))
            InfoRow("Jumlah dokumen","${vm.evidence.documentCounts[order.id]?:0}")
            InfoRow("Laporan kegiatan",if(order.id in vm.evidence.reportBookings)"Tersedia" else "Belum")
            InfoRow("Evaluasi kegiatan",if(order.id in vm.evidence.evaluationBookings)"Tersedia" else "Belum")
            Spacer(Modifier.height(9.dp))
            Text("OPS COMPLETED memerlukan pengembalian aset, bukti biaya, laporan insiden bila ada, serta approval Manager.",color=Muted,fontSize=11.sp)
        }
        Spacer(Modifier.height(11.dp))
        WhiteCard {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning,null,tint=Alert)
                Spacer(Modifier.width(8.dp))
                Text("Eskalasi insiden",fontWeight=FontWeight.ExtraBold,fontSize=15.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("Utamakan keselamatan. Hentikan kegiatan bila berisiko dan hubungi bantuan darurat bila diperlukan.",fontSize=11.sp,color=Alert)
            Spacer(Modifier.height(9.dp))
            listOf("Level 1 — Minor","Level 2 — Significant","Level 3 — Critical").forEach {value->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=level==value,onClick={level=value})
                    Text(value,Modifier.clickable{level=value},fontSize=11.sp)
                }
            }
            OutlinedTextField(story,onValueChange={story=it},label={Text("Kronologi, lokasi, dan waktu")},
                modifier=Modifier.fillMaxWidth(),minLines=3,shape=R14)
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(action,onValueChange={action=it},label={Text("Tindakan dan pihak yang dihubungi")},
                modifier=Modifier.fillMaxWidth(),minLines=2,shape=R14)
            Spacer(Modifier.height(11.dp))
            Button(onClick={
                val message="""
                    LAPORAN INSIDEN GMU EDUTRANS OPS
                    Order: ${order.number}
                    Program: ${order.program}
                    Sekolah: ${order.customer}
                    Level: $level
                    Kronologi: $story
                    Tindakan: $action
                    Mohon diteruskan ke Manager dan dicatat pada ERP.
                """.trimIndent()
                val share=Intent(Intent.ACTION_SEND).apply {
                    type="text/plain"
                    putExtra(Intent.EXTRA_TEXT,message)
                }
                context.startActivity(Intent.createChooser(share,"Bagikan laporan ke Manager"))
            },modifier=Modifier.fillMaxWidth(),enabled=story.isNotBlank()&&action.isNotBlank(),
                colors=ButtonDefaults.buttonColors(containerColor=Alert),shape=R14) {
                Icon(Icons.Rounded.Share,null,modifier=Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bagikan laporan ke Manager")
            }
            Spacer(Modifier.height(8.dp))
            Text("Form ini membuka aplikasi pesan. Belum otomatis tercatat dalam ERP sebelum ditindaklanjuti Manager.",fontSize=10.sp,color=Muted)
        }
    }
}

@Composable
private fun OrderHeader(order:OpsOrder) {
    WhiteCard {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(order.number,fontWeight=FontWeight.ExtraBold,fontSize=12.sp,color=Green)
                Text(order.program,fontWeight=FontWeight.ExtraBold,fontSize=16.sp,color=Ink)
                Text(order.customer,color=Muted,fontSize=11.sp)
            }
            Badge(order.status)
        }
        Spacer(Modifier.height(8.dp))
        Text("${order.date} • ${order.pax} pax",color=Muted,fontSize=11.sp)
        if(order.meetingPoint.isNotBlank()) Text("Meeting: ${order.meetingPoint}",color=Muted,fontSize=11.sp)
        if(order.notes.isNotBlank()) Text("Catatan: ${order.notes}",color=Amber,fontSize=11.sp)
    }
    Spacer(Modifier.height(11.dp))
}

@Composable
private fun Badge(label:String) {
    Surface(color=Pale,shape=CircleShape) {
        Text(label,Modifier.padding(horizontal=10.dp,vertical=6.dp),
            color=Dark,fontWeight=FontWeight.Bold,fontSize=10.sp)
    }
}
@Composable
private fun Heading(title:String, subtitle:String) {
    Column(Modifier.padding(vertical=12.dp)) {
        Text(title,fontSize=17.sp,fontWeight=FontWeight.ExtraBold,color=Ink)
        Text(subtitle,fontSize=11.sp,color=Muted)
    }
}
@Composable
private fun WhiteCard(content:@Composable ColumnScope.()->Unit) {
    Card(modifier=Modifier.fillMaxWidth(),shape=R20,
        border=BorderStroke(1.dp,Border),
        colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.padding(16.dp),content=content)
    }
}
@Composable
private fun Notice(message:String,warning:Boolean=false) {
    Card(shape=R14,colors=CardDefaults.cardColors(
        containerColor=if(warning)Color(0xFFFFF8EA)else Pale)) {
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.Top) {
            Icon(if(warning)Icons.Rounded.Warning else Icons.Rounded.Info,
                null,tint=if(warning)Amber else Green,modifier=Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
            Text(message,modifier=Modifier.weight(1f),fontSize=11.sp,
                color=if(warning)Amber else Dark,lineHeight=16.sp)
        }
    }
}
@Composable
private fun InfoRow(label:String,value:String) {
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp)) {
        Text(label,modifier=Modifier.weight(.42f),fontSize=11.sp,color=Muted)
        Text(value,modifier=Modifier.weight(.58f),fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=Ink)
    }
}
