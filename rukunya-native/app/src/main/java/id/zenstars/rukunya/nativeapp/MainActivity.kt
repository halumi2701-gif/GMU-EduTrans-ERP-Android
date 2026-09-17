package id.zenstars.rukunya.nativeapp

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.zenstars.rukunya.nativeapp.billing.Feature
import id.zenstars.rukunya.nativeapp.billing.Plan
import id.zenstars.rukunya.nativeapp.billing.PlanSpec
import id.zenstars.rukunya.nativeapp.billing.Plans
import id.zenstars.rukunya.nativeapp.data.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val Green = Color(0xFF0F6B4F)
private val GreenSoft = Color(0xFFEAF5F0)
private val Amber = Color(0xFFFBC02D)
private val Canvas = Color(0xFFF6F8F7)
private val Ink = Color(0xFF202823)
private val Muted = Color(0xFF6E7973)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Green,
                    secondary = Color(0xFF16A085),
                    tertiary = Amber,
                    background = Canvas,
                    surface = Color.White,
                    onPrimary = Color.White,
                    onBackground = Ink,
                    onSurface = Ink
                )
            ) {
                val vm: RukunyaViewModel = viewModel()
                RukunyaApp(vm)
            }
        }
    }
}

private enum class Screen(val title: String) {
    HOME("Beranda"),
    WARGA("Warga"),
    IURAN("Iuran"),
    REPORT("Laporan"),
    MORE("Lainnya"),
    CASH("Kas"),
    INFO("Informasi"),
    LETTERS("Surat"),
    PLANS("Paket RUKUNYA"),
    ADMINS("Pengurus"),
    CLOUD("Cloud & Sinkronisasi")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RukunyaApp(vm: RukunyaViewModel) {
    var screenName by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val screen = runCatching { Screen.valueOf(screenName) }.getOrDefault(Screen.HOME)
    val plan by vm.activePlan.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(vm) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = Canvas,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (screen == Screen.HOME) "RUKUNYA" else screen.title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text(if (screen == Screen.HOME) "Urus Warga Jadi Mudah." else "${Plans.get(plan).title} • Native Android", fontSize = 10.sp, color = Muted)
                    }
                },
                navigationIcon = {
                    if (screen !in listOf(Screen.HOME, Screen.WARGA, Screen.IURAN, Screen.REPORT, Screen.MORE)) {
                        IconButton(onClick = { screenName = Screen.HOME.name }) { Icon(Icons.Default.ArrowBack, contentDescription = "Kembali") }
                    }
                },
                actions = {
                    AssistChip(
                        onClick = { screenName = Screen.PLANS.name },
                        label = { Text(Plans.get(plan).title, fontSize = 10.sp) },
                        leadingIcon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            )
        },
        bottomBar = {
            if (screen in listOf(Screen.HOME, Screen.WARGA, Screen.IURAN, Screen.REPORT, Screen.MORE)) {
                NavigationBar(containerColor = Color.White) {
                    listOf(
                        Triple(Screen.HOME, Icons.Default.Home, "Beranda"),
                        Triple(Screen.WARGA, Icons.Default.Groups, "Warga"),
                        Triple(Screen.IURAN, Icons.Default.ReceiptLong, "Iuran"),
                        Triple(Screen.REPORT, Icons.Default.Assessment, "Laporan"),
                        Triple(Screen.MORE, Icons.Default.GridView, "Lainnya")
                    ).forEach { (target, icon, label) ->
                        NavigationBarItem(
                            selected = screen == target,
                            onClick = { screenName = target.name },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 9.sp) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                Screen.HOME -> HomeScreen(vm) { screenName = it.name }
                Screen.WARGA -> WargaScreen(vm) { screenName = Screen.PLANS.name }
                Screen.IURAN -> IuranScreen(vm) { screenName = Screen.PLANS.name }
                Screen.REPORT -> ReportScreen(vm) { screenName = Screen.PLANS.name }
                Screen.MORE -> MoreScreen(vm) { screenName = it.name }
                Screen.CASH -> CashScreen(vm)
                Screen.INFO -> InfoScreen(vm)
                Screen.LETTERS -> LetterScreen(vm) { screenName = Screen.PLANS.name }
                Screen.PLANS -> PlansScreen(vm)
                Screen.ADMINS -> AdminScreen(vm) { screenName = Screen.PLANS.name }
                Screen.CLOUD -> CloudScreen(vm) { screenName = Screen.PLANS.name }
            }
        }
    }
}

@Composable
private fun HomeScreen(vm: RukunyaViewModel, navigate: (Screen) -> Unit) {
    val households by vm.households.collectAsStateWithLifecycle()
    val residents by vm.residentCount.collectAsStateWithLifecycle()
    val cash by vm.cash.collectAsStateWithLifecycle()
    val contributions by vm.contributions.collectAsStateWithLifecycle()
    val letters by vm.letters.collectAsStateWithLifecycle()
    val announcements by vm.announcements.collectAsStateWithLifecycle()
    val plan by vm.activePlan.collectAsStateWithLifecycle()
    val spec = Plans.get(plan)

    val saldo = cash.sumOf { if (it.type == "IN") it.amount else -it.amount }
    val paidKk = contributions.filter { it.period == vm.currentPeriod() }.map { it.householdId }.distinct().size
    val pct = if (households.isEmpty()) 0 else (paidKk * 100 / households.size)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Halo, Pengurus 👋", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Text("Ringkasan lingkungan hari ini", color = Muted, fontSize = 12.sp)
                }
                PlanBadge(spec)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Saldo Kas", color = Color(0xFFD6EAE2), fontSize = 11.sp)
                    Text(rupiah(saldo), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAction("Kas", Icons.Default.AccountBalanceWallet) { navigate(Screen.CASH) }
                        QuickAction("Surat", Icons.Default.Description) { navigate(Screen.LETTERS) }
                        QuickAction("Paket", Icons.Default.WorkspacePremium) { navigate(Screen.PLANS) }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallMetric("Warga", residents.toString(), Modifier.weight(1f))
                SmallMetric("KK", households.size.toString(), Modifier.weight(1f))
                SmallMetric("Iuran", "$pct%", Modifier.weight(1f))
            }
        }
        item {
            Text("Layanan", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text("Akses cepat administrasi lingkungan", color = Muted, fontSize = 10.sp)
        }
        item {
            val services = listOf(
                Triple("Warga", Icons.Default.Groups, Screen.WARGA),
                Triple("Iuran", Icons.Default.ReceiptLong, Screen.IURAN),
                Triple("Kas", Icons.Default.AccountBalanceWallet, Screen.CASH),
                Triple("Surat", Icons.Default.Description, Screen.LETTERS),
                Triple("Info", Icons.Default.Campaign, Screen.INFO),
                Triple("Laporan", Icons.Default.Assessment, Screen.REPORT),
                Triple("Lainnya", Icons.Default.GridView, Screen.MORE)
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                services.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, icon, target) ->
                            ServiceTile(label, icon, Modifier.weight(1f)) { navigate(target) }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            SectionTitle("Perlu diperhatikan", "Status yang butuh tindak lanjut")
            Card(shape = RoundedCornerShape(16.dp)) {
                Column {
                    StatusRow(Icons.Default.Payments, "${(households.size - paidKk).coerceAtLeast(0)} KK belum lunas", "Iuran bulan ini") { navigate(Screen.IURAN) }
                    HorizontalDivider()
                    StatusRow(Icons.Default.Description, "${letters.count { it.status != "Selesai" }} surat aktif", "Permohonan berjalan") { navigate(Screen.LETTERS) }
                    if (spec.has(Feature.CLOUD_SYNC)) {
                        HorizontalDivider()
                        StatusRow(Icons.Default.CloudOff, "Cloud belum terhubung", "Hubungkan untuk sinkronisasi") { navigate(Screen.CLOUD) }
                    }
                }
            }
        }
        item {
            SectionTitle("Aktivitas terbaru", "Kas dan informasi lingkungan")
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cash.take(3).forEach { item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(item.note.ifBlank { item.category }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                            Text((if (item.type == "IN") "+" else "-") + rupiah(item.amount), color = if (item.type == "IN") Green else Color(0xFFB42318), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    if (cash.isEmpty()) Text("Belum ada transaksi kas.", color = Muted, fontSize = 11.sp)
                    if (announcements.isNotEmpty()) {
                        HorizontalDivider()
                        Text(announcements.first().title, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(announcements.first().body, color = Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun WargaScreen(vm: RukunyaViewModel, upgrade: () -> Unit) {
    val households by vm.households.collectAsStateWithLifecycle()
    val plan by vm.activePlan.collectAsStateWithLifecycle()
    val spec = Plans.get(plan)
    var showAdd by remember { mutableStateOf(false) }
    var addMemberTo by remember { mutableStateOf<HouseholdWithResidents?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 92.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Data Warga & KK", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text(if (spec.maxHouseholds == null) "KK tanpa batas" else "${households.size}/${spec.maxHouseholds} KK pada paket FREE", color = Muted, fontSize = 11.sp)
                    }
                    if (spec.maxHouseholds != null) TextButton(onClick = upgrade) { Text("Upgrade") }
                }
            }
            if (households.isEmpty()) item { EmptyState("Belum ada data keluarga", "Tambahkan KK pertama untuk memulai.") }
            items(households, key = { it.household.id }) { item ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).background(GreenSoft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Home, contentDescription = null, tint = Green)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.household.headName, fontWeight = FontWeight.Bold)
                                Text("KK ${item.household.kkNumber} • ${item.residents.size} anggota", color = Muted, fontSize = 10.sp)
                                Text(item.household.address.ifBlank { "Alamat belum diisi" }, color = Muted, fontSize = 10.sp)
                            }
                            IconButton(onClick = { addMemberTo = item }) { Icon(Icons.Default.PersonAdd, contentDescription = "Tambah anggota") }
                        }
                        if (item.residents.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp)); HorizontalDivider(); Spacer(Modifier.height(6.dp))
                            item.residents.take(4).forEach { resident ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(resident.name, Modifier.weight(1f), fontSize = 11.sp)
                                    Text(resident.relation, color = Muted, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                if (spec.maxHouseholds != null && households.size >= spec.maxHouseholds!!) upgrade() else showAdd = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = Green,
            contentColor = Color.White
        ) { Icon(Icons.Default.Add, contentDescription = "Tambah KK") }
    }

    if (showAdd) AddHouseholdDialog(onDismiss = { showAdd = false }) { kk, head, address, rt, phone ->
        vm.addHousehold(kk, head, address, rt, phone); showAdd = false
    }
    addMemberTo?.let { household ->
        AddResidentDialog(household.household.headName, onDismiss = { addMemberTo = null }) { name, nik, relation ->
            vm.addResident(household.household.id, name, nik, relation); addMemberTo = null
        }
    }
}

@Composable
private fun IuranScreen(vm: RukunyaViewModel, upgrade: () -> Unit) {
    val households by vm.households.collectAsStateWithLifecycle()
    val contributions by vm.contributions.collectAsStateWithLifecycle()
    val spec = vm.currentPlanSpec()
    var showAdd by remember { mutableStateOf(false) }
    val monthRows = contributions.filter { it.period == vm.currentPeriod() }
    val householdName = households.associate { it.household.id to it.household.headName }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 92.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Iuran Bulanan", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text("Periode ${vm.currentPeriod()} • ${monthRows.map { it.householdId }.distinct().size}/${households.size} KK lunas", color = Muted, fontSize = 11.sp)
            }
            if (!spec.has(Feature.CUSTOM_IURAN)) item {
                UpgradeBanner("Jenis iuran tambahan", "Basic membuka iuran keamanan, kebersihan, kegiatan, dan kategori khusus.", upgrade)
            }
            if (monthRows.isEmpty()) item { EmptyState("Belum ada pembayaran", "Catat pembayaran iuran warga bulan ini.") }
            items(monthRows, key = { it.id }) { item ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Green)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(householdName[item.householdId] ?: "KK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(item.category, color = Muted, fontSize = 10.sp)
                        }
                        Text(rupiah(item.amount), fontWeight = FontWeight.Bold, color = Green, fontSize = 12.sp)
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = Green, contentColor = Color.White) {
            Icon(Icons.Default.Add, contentDescription = "Catat iuran")
        }
    }

    if (showAdd) AddContributionDialog(households, spec.has(Feature.CUSTOM_IURAN), onDismiss = { showAdd = false }, onUpgrade = upgrade) { id, amount, category ->
        vm.addContribution(id, amount, category); showAdd = false
    }
}

@Composable
private fun CashScreen(vm: RukunyaViewModel) {
    val cash by vm.cash.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    val saldo = cash.sumOf { if (it.type == "IN") it.amount else -it.amount }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 92.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Saldo berjalan", color = Color(0xFFD6EAE2), fontSize = 10.sp)
                        Text(rupiah(saldo), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            if (cash.isEmpty()) item { EmptyState("Kas masih kosong", "Tambahkan pemasukan atau pengeluaran.") }
            items(cash, key = { it.id }) { item ->
                Card(shape = RoundedCornerShape(13.dp)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (item.type == "IN") Icons.Default.SouthWest else Icons.Default.NorthEast, contentDescription = null, tint = if (item.type == "IN") Green else Color(0xFFB42318))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.note.ifBlank { item.category }, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(item.category + " • " + formatDate(item.date), color = Muted, fontSize = 9.sp)
                        }
                        Text((if (item.type == "IN") "+" else "-") + rupiah(item.amount), color = if (item.type == "IN") Green else Color(0xFFB42318), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = Green, contentColor = Color.White) { Icon(Icons.Default.Add, null) }
    }
    if (showAdd) AddCashDialog(onDismiss = { showAdd = false }) { type, amount, category, note -> vm.addCash(type, amount, category, note); showAdd = false }
}

@Composable
private fun InfoScreen(vm: RukunyaViewModel) {
    val announcements by vm.announcements.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 92.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Pengumuman & Informasi", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
            if (announcements.isEmpty()) item { EmptyState("Belum ada pengumuman", "Terbitkan informasi untuk warga.") }
            items(announcements, key = { it.id }) { item ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(item.title, fontWeight = FontWeight.Bold)
                        Text(formatDate(item.createdAt), color = Muted, fontSize = 9.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(item.body, fontSize = 11.sp, color = Color(0xFF46524C))
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = Green, contentColor = Color.White) { Icon(Icons.Default.Add, null) }
    }
    if (showAdd) TwoFieldDialog("Buat Pengumuman", "Judul", "Isi pengumuman", onDismiss = { showAdd = false }) { a, b -> vm.addAnnouncement(a, b); showAdd = false }
}

@Composable
private fun LetterScreen(vm: RukunyaViewModel, upgrade: () -> Unit) {
    val letters by vm.letters.collectAsStateWithLifecycle()
    val spec = vm.currentPlanSpec()
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 92.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Surat Pengantar", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(if (spec.has(Feature.LETTER_APPROVAL)) "Approval digital aktif" else "Alur sederhana • approval tersedia di Pro", color = Muted, fontSize = 10.sp)
            }
            if (!spec.has(Feature.LETTER_APPROVAL)) item { UpgradeBanner("Approval surat", "Paket Pro menambahkan status Diajukan → Diproses → Disetujui → Selesai.", upgrade) }
            if (letters.isEmpty()) item { EmptyState("Belum ada surat", "Buat permohonan surat warga.") }
            items(letters, key = { it.id }) { item ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(item.residentName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(item.type, color = Muted, fontSize = 10.sp)
                            }
                            StatusPill(item.status)
                        }
                        Text(item.number, color = Muted, fontSize = 9.sp)
                        if (item.purpose.isNotBlank()) Text(item.purpose, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (item.status == "Diajukan") TextButton(onClick = { vm.setLetterStatus(item.id, "Diproses") }) { Text("Proses", fontSize = 10.sp) }
                            if (item.status == "Diproses" && spec.has(Feature.LETTER_APPROVAL)) TextButton(onClick = { vm.setLetterStatus(item.id, "Disetujui") }) { Text("Setujui", fontSize = 10.sp) }
                            if (item.status != "Selesai") TextButton(onClick = { vm.setLetterStatus(item.id, "Selesai") }) { Text("Selesai", fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = Green, contentColor = Color.White) { Icon(Icons.Default.Add, null) }
    }
    if (showAdd) AddLetterDialog(onDismiss = { showAdd = false }) { name, type, purpose -> vm.addLetter(name, type, purpose); showAdd = false }
}

@Composable
private fun ReportScreen(vm: RukunyaViewModel, upgrade: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val households by vm.households.collectAsStateWithLifecycle()
    val residents by vm.residentCount.collectAsStateWithLifecycle()
    val cash by vm.cash.collectAsStateWithLifecycle()
    val contributions by vm.contributions.collectAsStateWithLifecycle()
    val letters by vm.letters.collectAsStateWithLifecycle()
    val spec = vm.currentPlanSpec()
    val saldo = cash.sumOf { if (it.type == "IN") it.amount else -it.amount }
    val monthIncome = contributions.filter { it.period == vm.currentPeriod() }.sumOf { it.amount }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val csv = buildString {
                appendLine("RUKUNYA Native v2 - Laporan ${vm.currentPeriod()}")
                appendLine("Metrik,Nilai")
                appendLine("Warga,$residents")
                appendLine("KK,${households.size}")
                appendLine("Saldo Kas,$saldo")
                appendLine("Iuran Bulan Ini,$monthIncome")
                appendLine("Jumlah Surat,${letters.size}")
                appendLine()
                appendLine("Kas,Tipe,Nominal,Kategori,Keterangan")
                cash.forEach { appendLine("${formatDate(it.date)},${it.type},${it.amount},\"${it.category}\",\"${it.note}\"") }
            }
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) writeSimpleReportPdf(context, uri, residents, households.size, saldo, monthIncome, letters.size)
    }

    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Laporan ${vm.currentPeriod()}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text("Ringkasan administrasi lingkungan", color = Muted, fontSize = 11.sp)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportRow("Total warga", residents.toString())
                ReportRow("Total KK", households.size.toString())
                ReportRow("Saldo kas", rupiah(saldo))
                ReportRow("Iuran bulan ini", rupiah(monthIncome))
                ReportRow("Surat tercatat", letters.size.toString())
            }
        }
        item {
            if (spec.has(Feature.ADVANCED_REPORT)) {
                Card(shape = RoundedCornerShape(15.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Export Laporan", fontWeight = FontWeight.Bold)
                        Text("Simpan rekap untuk arsip atau dibagikan ke pengurus.", color = Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { csvLauncher.launch("RUKUNYA-${vm.currentPeriod()}.csv") }, Modifier.weight(1f)) { Icon(Icons.Default.TableView, null); Spacer(Modifier.width(5.dp)); Text("CSV") }
                            Button(onClick = { pdfLauncher.launch("RUKUNYA-${vm.currentPeriod()}.pdf") }, Modifier.weight(1f)) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(5.dp)); Text("PDF") }
                        }
                    }
                }
            } else {
                UpgradeBanner("Laporan & export lengkap", "Basic membuka export CSV/PDF dan histori penuh.", upgrade)
            }
        }
        if (spec.has(Feature.RW_DASHBOARD)) item {
            Card(colors = CardDefaults.cardColors(containerColor = GreenSoft), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Dashboard RW • Pro", color = Green, fontWeight = FontWeight.ExtraBold)
                    Text("Siap untuk konsolidasi beberapa RT saat struktur wilayah diaktifkan.", fontSize = 10.sp, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(vm: RukunyaViewModel, navigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = vm.currentPlanSpec()
    val plan by vm.activePlan.collectAsStateWithLifecycle()

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val json = vm.exportBackupJson()
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("File kosong")
                vm.importBackupJson(raw)
            }
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Paket ${Plans.get(plan).title}", color = Color.White, fontWeight = FontWeight.ExtraBold)
                        Text(Plans.get(plan).description, color = Color(0xFFD6EAE2), fontSize = 10.sp)
                    }
                    TextButton(onClick = { navigate(Screen.PLANS) }) { Text("Lihat Paket", color = Color.White) }
                }
            }
        }
        item { MenuRow(Icons.Default.AccountBalanceWallet, "Kas", "Pemasukan dan pengeluaran") { navigate(Screen.CASH) } }
        item { MenuRow(Icons.Default.Description, "Surat", "Permohonan dan status surat") { navigate(Screen.LETTERS) } }
        item { MenuRow(Icons.Default.Campaign, "Informasi", "Pengumuman lingkungan") { navigate(Screen.INFO) } }
        item { MenuRow(Icons.Default.GroupAdd, "Pengurus", if (spec.has(Feature.MULTI_ADMIN)) "Maksimal ${spec.maxAdmins} profil pengurus" else "Tersedia mulai Plus") { if (spec.has(Feature.MULTI_ADMIN)) navigate(Screen.ADMINS) else navigate(Screen.PLANS) } }
        item { MenuRow(Icons.Default.Cloud, "Cloud & Sinkronisasi", if (spec.has(Feature.CLOUD_SYNC)) "Fitur Plus/Pro" else "Tersedia mulai Plus") { if (spec.has(Feature.CLOUD_SYNC)) navigate(Screen.CLOUD) else navigate(Screen.PLANS) } }
        item {
            Card(shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Backup & Restore", fontWeight = FontWeight.Bold)
                    Text("Backup lokal tersedia di semua paket.", color = Muted, fontSize = 10.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { backupLauncher.launch("RUKUNYA-backup.json") }, Modifier.weight(1f)) { Text("Backup") }
                        Button(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain")) }, Modifier.weight(1f)) { Text("Restore") }
                    }
                }
            }
        }
        item {
            if (spec.has(Feature.WHATSAPP_REMINDER)) {
                MenuRow(Icons.Default.Whatsapp, "Pengingat WhatsApp", "Kirim template pengingat iuran") { shareWhatsappReminder(context, vm.currentPeriod()) }
            } else {
                UpgradeBanner("Pengingat WhatsApp otomatis", "Fitur Pro untuk mempercepat penagihan iuran.") { navigate(Screen.PLANS) }
            }
        }
    }
}

@Composable
private fun PlansScreen(vm: RukunyaViewModel) {
    val active by vm.activePlan.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Pilih sesuai kebutuhan", fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Text("Core administrasi tetap bisa dipakai gratis. Upgrade hanya saat membutuhkan fitur lanjutan.", color = Muted, fontSize = 11.sp)
        }
        items(Plans.all) { spec ->
            PlanCard(spec, active == spec.plan) {
                if (BuildConfig.DEBUG) vm.applyVerifiedPlan(spec.plan)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7DF)), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(13.dp)) {
                    Text("Aktivasi langganan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Struktur paket dan penguncian fitur sudah aktif. Validasi pembayaran/licensing akan memakai verifier terpisah sebelum versi komersial release.", color = Color(0xFF6E5A24), fontSize = 10.sp)
                    if (BuildConfig.DEBUG) Text("Build pengujian: tombol paket dapat dipakai untuk preview entitlement.", color = Color(0xFF8A6A13), fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun AdminScreen(vm: RukunyaViewModel, upgrade: () -> Unit) {
    val admins by vm.admins.collectAsStateWithLifecycle()
    val spec = vm.currentPlanSpec()
    var showAdd by remember { mutableStateOf(false) }
    if (!spec.has(Feature.MULTI_ADMIN)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { UpgradeBanner("Multi-admin", "Tersedia mulai paket Plus.", upgrade) }
        return
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 92.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("${admins.size}/${spec.maxAdmins} profil pengurus", color = Muted, fontSize = 11.sp) }
            if (admins.isEmpty()) item { EmptyState("Belum ada pengurus tambahan", "Tambahkan sekretaris, bendahara, atau operator.") }
            items(admins, key = { it.id }) { item ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, tint = Green)
                        Spacer(Modifier.width(10.dp))
                        Column { Text(item.name, fontWeight = FontWeight.Bold); Text(item.role, color = Muted, fontSize = 10.sp) }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { if (admins.size < spec.maxAdmins) showAdd = true else upgrade() }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = Green, contentColor = Color.White) { Icon(Icons.Default.PersonAdd, null) }
    }
    if (showAdd) AddAdminDialog(onDismiss = { showAdd = false }) { name, role, phone -> vm.addAdmin(name, role, phone); showAdd = false }
}

@Composable
private fun CloudScreen(vm: RukunyaViewModel, upgrade: () -> Unit) {
    val spec = vm.currentPlanSpec()
    if (!spec.has(Feature.CLOUD_SYNC)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { UpgradeBanner("RUKUNYA Cloud", "Cloud backup dan sinkronisasi tersedia mulai Plus.", upgrade) }
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = GreenSoft), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, null, tint = Green)
                    Spacer(Modifier.width(10.dp))
                    Column { Text("Cloud belum terhubung", fontWeight = FontWeight.Bold); Text("Data lokal tetap aman dan dapat dipakai offline.", color = Muted, fontSize = 10.sp) }
                }
            }
        }
        Text("Modul native sudah menyiapkan entitlement Cloud Sync dan multi-admin. Endpoint cloud/account belum diaktifkan pada build offline-first ini sehingga aplikasi tidak berpura-pura melakukan sinkronisasi.", color = Muted, fontSize = 11.sp)
        if (spec.has(Feature.RESIDENT_ACCESS)) FeatureLine("Akses warga", "Entitlement aktif; menunggu akun cloud")
        FeatureLine("Cloud backup", "Entitlement aktif; menunggu koneksi backend")
        FeatureLine("Sinkron antar perangkat", "Entitlement aktif; menunggu koneksi backend")
    }
}

@Composable
private fun AddHouseholdDialog(onDismiss: () -> Unit, save: (String, String, String, String, String) -> Unit) {
    var kk by remember { mutableStateOf("") }; var head by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var rt by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Tambah Kartu Keluarga") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(kk, { kk = it }, label = { Text("Nomor KK") }, singleLine = true)
            OutlinedTextField(head, { head = it }, label = { Text("Kepala Keluarga") }, singleLine = true)
            OutlinedTextField(address, { address = it }, label = { Text("Alamat") })
            OutlinedTextField(rt, { rt = it }, label = { Text("RT") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("No. HP") }, singleLine = true)
        }
    }, confirmButton = { Button(onClick = { save(kk, head, address, rt, phone) }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun AddResidentDialog(head: String, onDismiss: () -> Unit, save: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var nik by remember { mutableStateOf("") }; var relation by remember { mutableStateOf("Anak") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Tambah Anggota") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Keluarga $head", color = Muted, fontSize = 10.sp)
            OutlinedTextField(name, { name = it }, label = { Text("Nama") }, singleLine = true)
            OutlinedTextField(nik, { nik = it }, label = { Text("NIK") }, singleLine = true)
            OutlinedTextField(relation, { relation = it }, label = { Text("Hubungan") }, singleLine = true)
        }
    }, confirmButton = { Button(onClick = { save(name, nik, relation) }) { Text("Tambah") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun AddContributionDialog(households: List<HouseholdWithResidents>, customCategory: Boolean, onDismiss: () -> Unit, onUpgrade: () -> Unit, save: (Long, String, String) -> Unit) {
    var selected by remember { mutableLongStateOf(0L) }; var amount by remember { mutableStateOf("20000") }; var category by remember { mutableStateOf("Iuran Bulanan") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Catat Iuran") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pilih KK", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            households.forEach { item ->
                Row(Modifier.fillMaxWidth().clickable { selected = item.household.id }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == item.household.id, onClick = { selected = item.household.id })
                    Text(item.household.headName, fontSize = 11.sp)
                }
            }
            OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Nominal") }, singleLine = true)
            if (customCategory) OutlinedTextField(category, { category = it }, label = { Text("Jenis iuran") }, singleLine = true)
            else TextButton(onClick = onUpgrade) { Text("Butuh jenis iuran lain? Upgrade Basic") }
        }
    }, confirmButton = { Button(onClick = { save(selected, amount, category) }, enabled = selected > 0) { Text("Catat") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun AddCashDialog(onDismiss: () -> Unit, save: (String, String, String, String) -> Unit) {
    var type by remember { mutableStateOf("IN") }; var amount by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Umum") }; var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Tambah Transaksi Kas") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "IN", onClick = { type = "IN" }, label = { Text("Pemasukan") })
                FilterChip(selected = type == "OUT", onClick = { type = "OUT" }, label = { Text("Pengeluaran") })
            }
            OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Nominal") }, singleLine = true)
            OutlinedTextField(category, { category = it }, label = { Text("Kategori") }, singleLine = true)
            OutlinedTextField(note, { note = it }, label = { Text("Keterangan") })
        }
    }, confirmButton = { Button(onClick = { save(type, amount, category, note) }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun AddLetterDialog(onDismiss: () -> Unit, save: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var type by remember { mutableStateOf("Surat Pengantar") }; var purpose by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Buat Surat") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nama warga") }, singleLine = true)
            OutlinedTextField(type, { type = it }, label = { Text("Jenis surat") }, singleLine = true)
            OutlinedTextField(purpose, { purpose = it }, label = { Text("Keperluan") })
        }
    }, confirmButton = { Button(onClick = { save(name, type, purpose) }) { Text("Buat") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun AddAdminDialog(onDismiss: () -> Unit, save: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var role by remember { mutableStateOf("Pengurus") }; var phone by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Tambah Pengurus") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nama") }, singleLine = true)
            OutlinedTextField(role, { role = it }, label = { Text("Jabatan") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("No. HP") }, singleLine = true)
        }
    }, confirmButton = { Button(onClick = { save(name, role, phone) }) { Text("Tambah") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun TwoFieldDialog(title: String, firstLabel: String, secondLabel: String, onDismiss: () -> Unit, save: (String, String) -> Unit) {
    var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(a, { a = it }, label = { Text(firstLabel) }); OutlinedTextField(b, { b = it }, label = { Text(secondLabel) })
    } }, confirmButton = { Button(onClick = { save(a, b) }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun PlanCard(spec: PlanSpec, active: Boolean, onPreview: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (active) GreenSoft else Color.White),
        border = if (active) CardDefaults.outlinedCardBorder().copy(width = 1.dp) else null,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(spec.title, fontWeight = FontWeight.ExtraBold, color = if (active) Green else Ink); Text(spec.description, color = Muted, fontSize = 10.sp) }
                Text(spec.priceLabel, fontWeight = FontWeight.ExtraBold, color = Green)
            }
            Spacer(Modifier.height(10.dp))
            val lines = when (spec.plan) {
                Plan.FREE -> listOf("Maks. 30 KK", "Warga, iuran, kas, surat, info", "Backup lokal")
                Plan.BASIC -> listOf("KK tanpa batas", "Multi jenis iuran", "Laporan lengkap + CSV/PDF")
                Plan.PLUS -> listOf("Semua Basic", "Maks. 3 pengurus", "Cloud & akses warga (cloud-ready)")
                Plan.PRO -> listOf("Semua Plus", "WhatsApp reminder", "Approval surat + dashboard RW")
            }
            lines.forEach { FeatureLine("✓", it) }
            if (BuildConfig.DEBUG && !active) {
                Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = onPreview, Modifier.fillMaxWidth()) { Text("Preview paket (debug)") }
            } else if (active) {
                Spacer(Modifier.height(8.dp)); Text("Paket aktif", color = Green, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
    }
}

@Composable private fun PlanBadge(spec: PlanSpec) { Surface(color = GreenSoft, shape = RoundedCornerShape(10.dp)) { Text(spec.title, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = Green, fontWeight = FontWeight.Bold, fontSize = 9.sp) } }

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.White.copy(alpha = .14f), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.size(64.dp).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(21.dp)); Text(label, color = Color.White, fontSize = 9.sp)
        }
    }
}

@Composable
private fun SmallMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(12.dp)) { Text(label, color = Muted, fontSize = 9.sp); Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) } }
}

@Composable
private fun ServiceTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = Color.White, shape = RoundedCornerShape(16.dp), tonalElevation = 0.dp) { Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Green, modifier = Modifier.size(27.dp)) } }
        Spacer(Modifier.height(5.dp)); Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun SectionTitle(title: String, subtitle: String) { Column(Modifier.padding(vertical = 3.dp)) { Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp); Text(subtitle, color = Muted, fontSize = 9.sp) } }

@Composable
private fun StatusRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Green); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp); Text(subtitle, color = Muted, fontSize = 9.sp) }; Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(GreenSoft, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Green) }
            Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(subtitle, color = Muted, fontSize = 9.sp) }; Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun UpgradeBanner(title: String, text: String, onUpgrade: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7DF)), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = Color(0xFFA86B00)); Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp); Text(text, fontSize = 9.sp, color = Color(0xFF7A632B)) }; TextButton(onClick = onUpgrade) { Text("Upgrade", fontSize = 9.sp) }
        }
    }
}

@Composable
private fun EmptyState(title: String, text: String) {
    Card(shape = RoundedCornerShape(15.dp)) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Inbox, null, tint = Muted); Spacer(Modifier.height(8.dp)); Text(title, fontWeight = FontWeight.Bold); Text(text, color = Muted, fontSize = 10.sp) } }
}

@Composable private fun ReportRow(label: String, value: String) { Card(shape = RoundedCornerShape(13.dp)) { Row(Modifier.fillMaxWidth().padding(13.dp)) { Text(label, Modifier.weight(1f), color = Muted, fontSize = 11.sp); Text(value, fontWeight = FontWeight.Bold, fontSize = 11.sp) } } }

@Composable private fun StatusPill(status: String) { Surface(color = if (status == "Selesai") GreenSoft else Color(0xFFFFF7DF), shape = RoundedCornerShape(9.dp)) { Text(status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = if (status == "Selesai") Green else Color(0xFF8A6500), fontSize = 9.sp, fontWeight = FontWeight.Bold) } }

@Composable private fun FeatureLine(title: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(title, color = Green, fontWeight = FontWeight.Bold, fontSize = 10.sp); Spacer(Modifier.width(7.dp)); Text(value, color = Muted, fontSize = 10.sp) } }

private fun rupiah(value: Long): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")
private fun formatDate(value: Long): String = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(value))

private fun shareWhatsappReminder(context: Context, period: String) {
    val text = "Halo Bapak/Ibu, ini pengingat iuran lingkungan periode $period. Terima kasih atas partisipasinya. — RUKUNYA"
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); setPackage("com.whatsapp") }
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Kirim pengingat"))
    }
}

private fun writeSimpleReportPdf(context: Context, uri: Uri, residents: Int, households: Int, saldo: Long, iuran: Long, letters: Int) {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val canvas = page.canvas
    val title = Paint().apply { textSize = 20f; isFakeBoldText = true; color = android.graphics.Color.rgb(15, 107, 79) }
    val text = Paint().apply { textSize = 12f; color = android.graphics.Color.rgb(32, 40, 35) }
    canvas.drawText("RUKUNYA — Laporan Bulanan", 45f, 65f, title)
    var y = 105f
    listOf(
        "Total warga: $residents",
        "Total KK: $households",
        "Saldo kas: ${rupiah(saldo)}",
        "Iuran bulan ini: ${rupiah(iuran)}",
        "Jumlah surat: $letters",
        "Dibuat: ${formatDate(System.currentTimeMillis())}"
    ).forEach { line -> canvas.drawText(line, 45f, y, text); y += 28f }
    doc.finishPage(page)
    context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
    doc.close()
}
