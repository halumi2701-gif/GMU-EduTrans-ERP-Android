package com.pamoyanan.one.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pamoyanan.one.R
import org.json.JSONObject

private val V20Deep = Color(0xFF0B3D2E)
private val V20Forest = Color(0xFF166534)
private val V20Gold = Color(0xFFFACC15)
private val V20Page = Color(0xFFF8FAF4)
private val V20Ink = Color(0xFF102019)
private val V20Muted = Color(0xFF65756D)
private val V20Danger = Color(0xFFC9343A)

private enum class V20Tab(val label:String,val iconKey:String) {
    HOME("Beranda","beranda"),
    SERVICES("Layanan","layanan"),
    ACTIVITY("Aktivitas","aktivitas"),
    ACCOUNT("Akun","akun")
}

private data class V20Service(
    val title:String,
    val subtitle:String,
    val iconKey:String,
    val legacyRoute:String?=null,
    val genericPath:String?=null,
    val roles:Set<String> = setOf("WARGA","RT","RW","ADMIN"),
    val group:String = "Layanan"
)

private val V20Services = listOf(
    V20Service("Digital ID","Identitas & QR warga","digital_id","digital",group="Identitas"),
    V20Service("Digital ID Warga","Kelola ID warga sesuai scope","staff_digital_id","digitalStaff",roles=setOf("RT","RW","ADMIN"),group="Identitas"),
    V20Service("Pelayanan Surat","Ajukan, verifikasi, tracking & PDF","pelayanan_surat","letters",group="Administrasi"),
    V20Service("Pengaduan","Lapor dan tindak lanjut wilayah","lapor","complaints",group="Administrasi"),
    V20Service("Master Warga","Data warga sesuai scope","warga","residents",roles=setOf("RT","RW","ADMIN"),group="Kependudukan"),
    V20Service("Keluarga","Data household / keluarga","keluarga",genericPath="/households/families",group="Kependudukan"),
    V20Service("Mutasi Warga","Perpindahan dan perubahan data","mutasi",genericPath="/households/mutations",group="Kependudukan"),
    V20Service("Petugas Household","Pengurus/petugas keluarga","pengurus",genericPath="/households/officers",roles=setOf("RT","RW","ADMIN"),group="Kependudukan"),
    V20Service("Coklit","Status provenance Coklit","coklit",genericPath="/superapp/coklit-status",roles=setOf("RT","RW","ADMIN"),group="Operasional"),
    V20Service("Inbox Operasional","Pekerjaan & notifikasi prioritas","inbox_operasional","inbox",roles=setOf("RT","RW","ADMIN"),group="Operasional"),
    V20Service("Command Center","Dashboard operasi RW/RT","approval","command",roles=setOf("RT","RW","ADMIN"),group="Operasional"),
    V20Service("Smart Notification","Pusat notifikasi operasional","notifikasi",genericPath="/superapp/smart-notification-center",roles=setOf("RT","RW","ADMIN"),group="Operasional"),
    V20Service("Pengumuman RW","Informasi resmi wilayah","info_rw",genericPath="/superapp/announcements",group="Komunitas"),
    V20Service("Agenda & Kegiatan","Event dan aktivitas warga","agenda",genericPath="/superapp/events",group="Komunitas"),
    V20Service("Kesehatan","Aktivitas kesehatan wilayah","kesehatan",genericPath="/superapp/health",group="Tematik"),
    V20Service("Keamanan","Keamanan lingkungan","keamanan",genericPath="/superapp/security",group="Tematik"),
    V20Service("Security Readiness","Kesiapsiagaan keamanan","satlinmas",genericPath="/operations/security-readiness",roles=setOf("RT","RW","ADMIN"),group="Operasional"),
    V20Service("Lingkungan","Kebersihan & lingkungan hidup","lingkungan",genericPath="/superapp/environment",group="Tematik"),
    V20Service("Sosial","Bantuan & sosial warga","sosial",genericPath="/superapp/social",group="Tematik"),
    V20Service("Keuangan","Informasi dan ringkasan keuangan","iuran_kas",genericPath="/superapp/finance",group="Ekonomi"),
    V20Service("Usaha & UMKM","Ekonomi lokal RW 01","umkm",genericPath="/superapp/businesses",group="Ekonomi"),
    V20Service("PBB","Objek pajak bumi & bangunan","pbb",genericPath="/superapp/pbb",group="Ekonomi"),
    V20Service("QRIS","Pembayaran digital","qris_payment",genericPath="/superapp/qris",group="Ekonomi"),
    V20Service("Bangunan","Master bangunan wilayah","bangunan",genericPath="/buildings",roles=setOf("RT","RW","ADMIN"),group="Wilayah"),
    V20Service("Peta RW","Peta & area operations","wilayah",genericPath="/superapp/map",group="Wilayah"),
    V20Service("Register Surat","Buku register pelayanan","arsip",genericPath="/letters/register",roles=setOf("RT","RW","ADMIN"),group="Administrasi"),
    V20Service("Kontak Pengaduan","Kontak penanganan aduan","forum_warga",genericPath="/superapp/complaint-contacts",group="Administrasi"),
    V20Service("WhatsApp Gateway","Channel & template WhatsApp","whatsapp_gateway",genericPath="/superapp/whatsapp-settings",roles=setOf("RW","ADMIN"),group="Admin"),
    V20Service("Import Data","Batch import operasional","import_data",genericPath="/imports/batches",roles=setOf("RW","ADMIN"),group="Admin"),
    V20Service("Data Health","Status real-data backend","data_health",genericPath="/superapp/real-data-status",roles=setOf("RW","ADMIN"),group="Admin"),
    V20Service("Akun Pengguna","Akun warga/pengurus","admin_center",genericPath="/auth/accounts",roles=setOf("RW","ADMIN"),group="Admin"),
    V20Service("Reset Password","Permintaan reset password","password_reset",genericPath="/auth/password-reset-requests",roles=setOf("RW","ADMIN"),group="Admin")
)

private fun v20Vector(key:String): ImageVector = when(key) {
    "beranda" -> Icons.Filled.Home
    "layanan" -> Icons.Filled.Apps
    "aktivitas" -> Icons.Filled.History
    "akun" -> Icons.Filled.Person
    "digital_id","staff_digital_id" -> Icons.Filled.Badge
    "pelayanan_surat","arsip","timeline_tracking","pdf_qr" -> Icons.Filled.Description
    "lapor" -> Icons.Filled.Campaign
    "warga","keluarga","pengurus" -> Icons.Filled.Groups
    "mutasi" -> Icons.Filled.SyncAlt
    "coklit","data_health","audit_receipt" -> Icons.Filled.FactCheck
    "inbox_operasional","notifikasi" -> Icons.Filled.Notifications
    "approval" -> Icons.Filled.TaskAlt
    "info_rw" -> Icons.Filled.Info
    "agenda" -> Icons.Filled.Event
    "kesehatan" -> Icons.Filled.HealthAndSafety
    "keamanan","satlinmas","device_trust","privacy_shield" -> Icons.Filled.Security
    "lingkungan" -> Icons.Filled.Eco
    "sosial" -> Icons.Filled.VolunteerActivism
    "iuran_kas" -> Icons.Filled.AccountBalanceWallet
    "umkm" -> Icons.Filled.Storefront
    "pbb","bangunan" -> Icons.Filled.Apartment
    "qris_payment","qr_akses","qr_rotate" -> Icons.Filled.QrCode2
    "wilayah" -> Icons.Filled.Map
    "whatsapp_gateway" -> Icons.Filled.Chat
    "import_data" -> Icons.Filled.UploadFile
    "admin_center","pengaturan","password_reset" -> Icons.Filled.AdminPanelSettings
    "one_command" -> Icons.Filled.AutoAwesome
    "darurat" -> Icons.Filled.Sos
    "forum_warga" -> Icons.Filled.Forum
    "pamoyanan_one" -> Icons.Filled.AccountBalance
    else -> Icons.Filled.Apps
}

@Composable
private fun V20SpriteIcon(key:String, modifier:Modifier = Modifier.size(44.dp)) {
    val danger = key == "darurat"
    val gold = key in setOf("qris_payment","iuran_kas","pbb","agenda")
    val bg = when {
        danger -> Color(0xFFFFE9EA)
        gold -> Color(0xFFFFF5CF)
        else -> Color(0xFFE8F4EA)
    }
    val fg = when {
        danger -> V20Danger
        gold -> Color(0xFF8A6500)
        else -> V20Deep
    }
    Surface(shape = RoundedCornerShape(15.dp), color = bg) {
        Icon(v20Vector(key), contentDescription = null, tint = fg, modifier = modifier.padding(8.dp))
    }
}

@Composable
fun V20MainShell(vm: AppViewModel, snackbar: SnackbarHostState) {
    val me by vm.me
    val home by vm.home
    val role = me?.role ?: "WARGA"
    var tabName by rememberSaveable { mutableStateOf(V20Tab.HOME.name) }
    var legacyOpen by rememberSaveable { mutableStateOf(false) }
    var legacyLabel by rememberSaveable { mutableStateOf("") }
    val tab = V20Tab.valueOf(tabName)

    BackHandler(enabled = legacyOpen) { legacyOpen = false }

    fun openLegacy(service: V20Service) {
        if (service.legacyRoute != null) vm.navigate(service.legacyRoute)
        else if (service.genericPath != null) vm.loadGeneric(service.title, service.genericPath)
        legacyLabel = service.title
        legacyOpen = true
    }

    if (legacyOpen) {
        Box(Modifier.fillMaxSize()) {
            LegacyMainShell(vm, snackbar)
            Surface(
                modifier = Modifier.statusBarsPadding().padding(top = 58.dp, start = 10.dp).clickable { legacyOpen = false },
                shape = RoundedCornerShape(18.dp),
                color = V20Deep,
                shadowElevation = 6.dp
            ) {
                Text("‹ V20  " + legacyLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        return
    }

    Scaffold(
        containerColor = V20Page,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 10.dp) {
                V20Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tabName = item.name },
                        icon = { V20SpriteIcon(item.iconKey, Modifier.size(28.dp)) },
                        label = { Text(item.label, fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color(0xFFE6F2E9),
                            selectedTextColor = V20Deep,
                            unselectedTextColor = V20Muted
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openLegacy(V20Service("Darurat & Pengaduan","Laporkan kondisi mendesak","darurat","complaints")) },
                containerColor = V20Danger,
                contentColor = Color.White,
                icon = { V20SpriteIcon("darurat", Modifier.size(27.dp)) },
                text = { Text("Darurat", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                V20Tab.HOME -> V20Home(vm, home, role, me, ::openLegacy, onServices = { tabName = V20Tab.SERVICES.name })
                V20Tab.SERVICES -> V20ServiceHub(role, ::openLegacy)
                V20Tab.ACTIVITY -> V20ActivityCenter(role, ::openLegacy)
                V20Tab.ACCOUNT -> V20Account(vm, role, me, ::openLegacy)
            }
        }
    }
}

@Composable
private fun V20Home(
    vm: AppViewModel,
    home: JSONObject?,
    role:String,
    me:UserMe?,
    openLegacy:(V20Service)->Unit,
    onServices:()->Unit
) {
    LaunchedEffect(Unit) { vm.loadHome() }
    val resident = home?.optJSONObject("resident")
    val displayName = resident?.optString("name")?.takeIf { it.isNotBlank() } ?: me?.username.orEmpty().ifBlank { "Warga" }
    val scope = when(role) {
        "RT" -> "RT " + (me?.rtScope?.toIntOrNull() ?: 0).toString().padStart(2,'0') + " · RW 01"
        "RW","ADMIN" -> "RW 01 Pamoyanan · Cianjur"
        else -> {
            val rt = resident?.optString("rt")?.ifBlank { "01" } ?: "01"
            val rw = resident?.optString("rw")?.ifBlank { "01" } ?: "01"
            "RT $rt · RW $rw · Pamoyanan"
        }
    }
    val available = V20Services.filter { role in it.roles }
    val recommended = when(role) {
        "RW","ADMIN" -> listOf("Command Center","Inbox Operasional","Pelayanan Surat","Master Warga")
        "RT" -> listOf("Inbox Operasional","Pengaduan","Pelayanan Surat","Master Warga")
        else -> listOf("Digital ID","Pelayanan Surat","Pengaduan","Agenda & Kegiatan")
    }.mapNotNull { name -> available.firstOrNull { it.title == name } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.logo_rw01), "Logo RW 01", tint = Color.Unspecified, modifier = Modifier.size(50.dp))
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("PAMOYANAN ONE", fontWeight = FontWeight.Black, fontSize = 18.sp, color = V20Ink)
                    Text(if (role == "WARGA") "Civic Super-App V20" else "Civic Operating System V20", color = V20Muted, fontSize = 9.sp)
                }
                Surface(shape = CircleShape, color = Color(0xFFEAF4E7)) {
                    V20SpriteIcon("notifikasi", Modifier.padding(8.dp).size(28.dp))
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF082E23), V20Deep, V20Forest)), RoundedCornerShape(30.dp))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color.White.copy(alpha = .13f), shape = CircleShape) {
                            Text(
                                when(role) {"RW","ADMIN" -> "● RW COMMAND LIVE"; "RT" -> "● RT WORKSPACE LIVE"; else -> "● CITIZEN LIVE"},
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text("V20.2.1", color = V20Gold, fontWeight = FontWeight.Black, fontSize = 9.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        when(role) {"RW","ADMIN" -> "RW 01 dalam satu kendali."; "RT" -> "Operasional RT, tanpa dashboard desktop."; else -> "Sampurasun, $displayName."},
                        color = Color.White,
                        fontSize = 27.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(scope, color = Color.White.copy(alpha=.7f), fontSize = 10.sp)
                    Spacer(Modifier.height(15.dp))
                    Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .11f)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            V20SpriteIcon("one_command", Modifier.size(34.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("ONE Command", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(
                                    if (role == "WARGA") "Cari surat, Digital ID, laporan, PBB, UMKM..."
                                    else "Cari warga, approval, laporan, operasi, data...",
                                    color = Color.White.copy(alpha=.65f), fontSize = 8.sp
                                )
                            }
                            Icon(Icons.Outlined.Search, null, tint = Color.White)
                        }
                    }
                }
            }
        }

        item { Text(if(role=="WARGA") "Citizen Wallet" else "Identity & Access", fontWeight = FontWeight.Black, fontSize = 17.sp, color = V20Ink) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    openLegacy(if(role=="WARGA") available.first { it.title=="Digital ID" } else available.first { it.title=="Digital ID Warga" })
                },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF123D31))
            ) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    V20SpriteIcon(if(role=="WARGA")"digital_id" else "staff_digital_id", Modifier.size(70.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if(role=="WARGA")"Digital ID Warga" else "Digital ID & QR Management", color=Color.White,fontWeight=FontWeight.Black,fontSize=15.sp)
                        Text(if(role=="WARGA")displayName + " · " + scope else "Identity sesuai scope pengurus", color=Color.White.copy(alpha=.66f),fontSize=9.sp,maxLines=2)
                        Spacer(Modifier.height(9.dp))
                        Text("TAP UNTUK BUKA  →", color=V20Gold,fontSize=8.sp,fontWeight=FontWeight.Black)
                    }
                }
            }
        }

        item { Text(if(role=="WARGA")"Untuk Anda" else "Prioritas sekarang", fontWeight=FontWeight.Black,fontSize=17.sp,color=V20Ink) }
        items(recommended.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { service -> V20QuickCard(service, Modifier.weight(1f)) { openLegacy(service) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            Card(
                modifier=Modifier.fillMaxWidth().clickable(onClick=onServices),
                shape=RoundedCornerShape(22.dp),
                colors=CardDefaults.cardColors(containerColor=Color.White)
            ) {
                Row(Modifier.padding(15.dp), verticalAlignment=Alignment.CenterVertically) {
                    V20SpriteIcon("layanan", Modifier.size(43.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Semua layanan V20",fontWeight=FontWeight.Black,color=V20Ink)
                        Text(available.size.toString() + " pintu layanan + seluruh action lama di dalamnya",color=V20Muted,fontSize=9.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight,null,tint=V20Muted)
                }
            }
        }

        val announcements = home?.optJSONArray("announcements")
        if (announcements != null && announcements.length() > 0) {
            item { Text("Pengumuman RW", fontWeight=FontWeight.Black,fontSize=17.sp,color=V20Ink) }
            items((0 until minOf(announcements.length(),3)).mapNotNull { announcements.optJSONObject(it) }) { a ->
                Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                    Row(Modifier.padding(14.dp)) {
                        V20SpriteIcon("info_rw", Modifier.size(42.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(a.optString("title","Informasi RW"),fontWeight=FontWeight.Black,fontSize=11.sp)
                            Text(a.optString("body",""),color=V20Muted,fontSize=9.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V20QuickCard(service:V20Service, modifier:Modifier=Modifier, onClick:()->Unit) {
    Card(modifier=modifier.clickable(onClick=onClick),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.padding(14.dp)) {
            V20SpriteIcon(service.iconKey, Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text(service.title,fontWeight=FontWeight.Black,fontSize=11.sp,color=V20Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(service.subtitle,color=V20Muted,fontSize=8.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun V20ServiceHub(role:String, openLegacy:(V20Service)->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = V20Services.filter { role in it.roles && (query.isBlank() || (it.title+" "+it.subtitle+" "+it.group).contains(query,true)) }
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Layanan",fontWeight=FontWeight.Black,fontSize=26.sp,color=V20Ink)
        Text("Semua fungsi v6.7.1 tetap tersedia dalam arsitektur V20.",color=V20Muted,fontSize=9.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value=query,
            onValueChange={query=it},
            modifier=Modifier.fillMaxWidth(),
            placeholder={Text("Cari Coklit, PBB, QRIS, Surat, UMKM...")},
            leadingIcon={V20SpriteIcon("one_command",Modifier.size(28.dp))},
            singleLine=true,
            shape=RoundedCornerShape(20.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns=GridCells.Fixed(2),
            contentPadding=PaddingValues(bottom=130.dp),
            horizontalArrangement=Arrangement.spacedBy(9.dp),
            verticalArrangement=Arrangement.spacedBy(9.dp)
        ) {
            items(filtered,key={it.title}) { service ->
                Card(
                    modifier=Modifier.fillMaxWidth().height(132.dp).clickable { openLegacy(service) },
                    shape=RoundedCornerShape(22.dp),
                    colors=CardDefaults.cardColors(containerColor=Color.White)
                ) {
                    Column(Modifier.padding(13.dp)) {
                        V20SpriteIcon(service.iconKey,Modifier.size(45.dp))
                        Spacer(Modifier.height(7.dp))
                        Text(service.title,fontWeight=FontWeight.Black,fontSize=10.sp,color=V20Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(service.subtitle,color=V20Muted,fontSize=8.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Spacer(Modifier.weight(1f))
                        Text(service.group.uppercase(),color=V20Forest,fontWeight=FontWeight.Bold,fontSize=7.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun V20ActivityCenter(role:String, openLegacy:(V20Service)->Unit) {
    val available = V20Services.filter { role in it.roles }
    val cards = listOfNotNull(
        available.firstOrNull{it.title=="Pelayanan Surat"},
        available.firstOrNull{it.title=="Pengaduan"},
        available.firstOrNull{it.title=="Inbox Operasional"},
        available.firstOrNull{it.title=="Agenda & Kegiatan"}
    )
    LazyColumn(contentPadding=PaddingValues(16.dp,14.dp,16.dp,130.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text("Aktivitas",fontWeight=FontWeight.Black,fontSize=26.sp,color=V20Ink)
            Text("Tracking surat, pengaduan, pekerjaan dan aktivitas wilayah.",color=V20Muted,fontSize=9.sp)
        }
        items(cards) { service ->
            Card(modifier=Modifier.fillMaxWidth().clickable{openLegacy(service)},shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                    V20SpriteIcon(
                        when(service.title){"Pelayanan Surat"->"timeline_tracking";"Pengaduan"->"lapor";"Inbox Operasional"->"inbox_operasional";else->service.iconKey},
                        Modifier.size(48.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(service.title,fontWeight=FontWeight.Black,fontSize=12.sp)
                        Text(
                            when(service.title){"Pelayanan Surat"->"Status, timeline, PDF + QR";"Pengaduan"->"Status dan tindak lanjut";"Inbox Operasional"->"Action Center pengurus";else->service.subtitle},
                            color=V20Muted,fontSize=9.sp
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight,null,tint=V20Muted)
                }
            }
        }
    }
}

@Composable
private fun V20Account(vm:AppViewModel, role:String, me:UserMe?, openLegacy:(V20Service)->Unit) {
    val available=V20Services.filter{role in it.roles}
    LazyColumn(contentPadding=PaddingValues(16.dp,14.dp,16.dp,130.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text("Akun",fontWeight=FontWeight.Black,fontSize=26.sp,color=V20Ink)
            Text("Identity, keamanan, preferensi dan administrasi role.",color=V20Muted,fontSize=9.sp)
        }
        item {
            Card(shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    V20SpriteIcon("akun",Modifier.size(58.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(me?.username ?: "-",fontWeight=FontWeight.Black,fontSize=14.sp)
                        Text("Role " + role + " · " + (me?.rtScope?.let{"RT " + it + " · "}.orEmpty()) + "RW " + (me?.rwScope ?: "01"),color=V20Muted,fontSize=9.sp)
                    }
                    AssistChip(onClick={},label={Text("AKTIF",fontSize=7.sp)})
                }
            }
        }
        val accountEntries = buildList {
            available.firstOrNull{it.title=="Digital ID"}?.let(::add)
            available.firstOrNull{it.title=="Digital ID Warga"}?.let(::add)
            available.firstOrNull{it.title=="Akun Pengguna"}?.let(::add)
            available.firstOrNull{it.title=="Reset Password"}?.let(::add)
            available.firstOrNull{it.title=="Import Data"}?.let(::add)
            available.firstOrNull{it.title=="Data Health"}?.let(::add)
            available.firstOrNull{it.title=="WhatsApp Gateway"}?.let(::add)
        }
        items(accountEntries) { service ->
            Card(modifier=Modifier.fillMaxWidth().clickable{openLegacy(service)},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
                    V20SpriteIcon(service.iconKey,Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)){Text(service.title,fontWeight=FontWeight.Bold,fontSize=11.sp);Text(service.subtitle,color=V20Muted,fontSize=8.sp)}
                    Icon(Icons.Outlined.ChevronRight,null,tint=V20Muted)
                }
            }
        }
        item {
            OutlinedButton(onClick={vm.logout()},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
                Icon(Icons.Outlined.Logout,null); Spacer(Modifier.width(8.dp)); Text("Keluar dari PAMOYANAN ONE")
            }
        }
    }
}
