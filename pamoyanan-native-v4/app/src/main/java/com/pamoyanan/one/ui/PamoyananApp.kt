@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pamoyanan.one.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalActivity
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pamoyanan.one.R
import com.pamoyanan.one.ui.theme.Brand
import com.pamoyanan.one.ui.theme.BrandDark
import com.pamoyanan.one.ui.theme.Danger
import com.pamoyanan.one.ui.theme.Gold
import com.pamoyanan.one.ui.theme.Hairline
import com.pamoyanan.one.ui.theme.Ink
import com.pamoyanan.one.ui.theme.Mint
import com.pamoyanan.one.ui.theme.Muted
import com.pamoyanan.one.ui.theme.Page
import com.pamoyanan.one.ui.theme.Warning
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val RadiusXL = RoundedCornerShape(28.dp)
private val RadiusLG = RoundedCornerShape(20.dp)
private val RadiusMD = RoundedCornerShape(14.dp)

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray.strings(): List<String> =
    (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

private fun JSONObject.text(key: String, fallback: String = "-"): String {
    val value = optString(key)
    return if (value.isBlank() || value == "null") fallback else value
}

private fun maskNumber(value: String, head: Int = 4, tail: Int = 4): String {
    if (value.length <= head + tail) return value
    return value.take(head) + "••••••" + value.takeLast(tail)
}

private fun statusColor(status: String): Color = when {
    status.contains("ISSUED", true) || status.contains("RESOLVED", true) || status.contains("ACTIVE", true) -> Brand
    status.contains("URGENT", true) || status.contains("REJECT", true) -> Danger
    status.contains("PROGRESS", true) || status.contains("VERIFIED", true) || status.contains("APPROVED", true) -> Color(0xFF2D6F9D)
    else -> Warning
}

private fun decodeDataImage(dataUrl: String): ImageBitmap? {
    return try {
        val raw = dataUrl.substringAfter(",", "")
        if (raw.isBlank()) return null
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun PamoyananApp(vm: AppViewModel = viewModel()) {
    val stage by vm.authStage
    val loading by vm.loading
    val notice by vm.notice
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        val current = notice
        if (current != null) {
            snackbar.showSnackbar(current.message)
            vm.clearNotice()
        }
    }

    Box(Modifier.fillMaxSize().background(Page)) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "auth-stage"
        ) { target ->
            when (target) {
                AuthStage.BOOT -> StartupSplash()
                AuthStage.LOGIN -> LoginScreen(vm)
                AuthStage.INITIAL_PASSWORD -> InitialPasswordScreen(vm)
                AuthStage.MAIN -> V20MainShell(vm, snackbar)
            }
        }
        if (loading && stage != AuthStage.BOOT) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = Brand,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun StartupSplash() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFF8FBF9), Page))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(84.dp)
            Spacer(Modifier.height(20.dp))
            Text("PAMOYANAN ONE", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Ink)
            Text("RW 01 Pamoyanan · Native Super App", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(30.dp))
            CircularProgressIndicator(color = Brand, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp = 48.dp) {
    Box(
        Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        SafeBrandLogo(size = size, modifier = Modifier.size(size))
    }
}

@Composable
private fun LoginScreen(vm: AppViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Card(
                shape = RadiusXL,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(58.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("PAMOYANAN ONE", fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Text("Native Startup Experience · V4", color = Muted, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("Sampurasun.", fontWeight = FontWeight.Black, fontSize = 34.sp, color = Ink)
                    Text(
                        "Masuk ke layanan RW 01 dengan pengalaman aplikasi native.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(22.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("NIK / Username") },
                        leadingIcon = { Icon(Icons.Outlined.AccountCircle, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RadiusMD,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Outlined.Info, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RadiusMD,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { vm.login(username, password) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RadiusMD,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandDark)
                    ) {
                        Text("MASUK", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Surface(color = Mint, shape = RadiusMD) {
                        Text(
                            "Warga memakai NIK 16 digit. Pengurus memakai akun resmi RT/RW. Password awal wajib diganti pada login pertama.",
                            modifier = Modifier.padding(13.dp),
                            color = BrandDark,
                            fontSize = 10.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InitialPasswordScreen(vm: AppViewModel) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), contentAlignment = Alignment.Center) {
        Card(shape = RadiusXL, colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(24.dp)) {
                BrandMark(54.dp)
                Spacer(Modifier.height(20.dp))
                Text("Buat password pribadi", fontWeight = FontWeight.Black, fontSize = 26.sp)
                Text(
                    "Login awal berhasil. Sebelum membuka aplikasi, ganti password sementara.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password baru") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RadiusMD,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Ulangi password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RadiusMD,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Minimal 12 karakter, huruf besar, huruf kecil, angka, dan simbol.",
                    color = Muted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.setInitialPassword(password, confirm) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RadiusMD,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandDark)
                ) {
                    Text("SIMPAN PASSWORD BARU", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacyMainShell(vm: AppViewModel, snackbar: SnackbarHostState) {
    val me by vm.me
    val route by vm.route
    var showSearch by remember { mutableStateOf(false) }

    val role = me?.role ?: "WARGA"
    val nav = when (role) {
        "RW", "ADMIN" -> listOf(
            NavItem("home", "Beranda", Icons.Outlined.Home),
            NavItem("command", "Command", Icons.Outlined.Dashboard),
            NavItem("inbox", "Inbox", Icons.Outlined.Notifications),
            NavItem("digitalStaff", "ID Digital", Icons.Outlined.QrCode2),
            NavItem("more", "Lainnya", Icons.Outlined.GridView)
        )
        "RT" -> listOf(
            NavItem("home", "Beranda", Icons.Outlined.Home),
            NavItem("complaints", "Laporan", Icons.Outlined.Campaign),
            NavItem("digitalStaff", "ID Digital", Icons.Outlined.QrCode2),
            NavItem("letters", "Surat", Icons.Outlined.Assignment),
            NavItem("more", "Lainnya", Icons.Outlined.GridView)
        )
        else -> listOf(
            NavItem("home", "Beranda", Icons.Outlined.Home),
            NavItem("digital", "ID Saya", Icons.Outlined.QrCode2),
            NavItem("letters", "Surat", Icons.Outlined.Assignment),
            NavItem("complaints", "Lapor", Icons.Outlined.Campaign),
            NavItem("more", "Lainnya", Icons.Outlined.GridView)
        )
    }

    Scaffold(
        containerColor = Page,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandMark(38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("PAMOYANAN ONE", fontWeight = FontWeight.Black, fontSize = 15.sp)
                                Text(roleLabel(me), color = Muted, fontSize = 9.sp)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Outlined.Search, "Cari")
                        }
                        IconButton(onClick = { vm.logout() }) {
                            Icon(Icons.Outlined.Logout, "Keluar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                HorizontalDivider(color = Hairline)
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                nav.forEach { item ->
                    val selected = if (item.route == "more") route == "more" || route == "generic" else route == item.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { vm.navigate(item.route) },
                        icon = { Icon(item.icon, item.label) },
                        label = { Text(item.label, fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = BrandDark,
                            indicatorColor = BrandDark,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = route,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(110)) },
            modifier = Modifier.padding(padding),
            label = "route"
        ) { target ->
            when (target) {
                "home" -> HomeScreen(vm)
                "digital" -> DigitalIdScreen(vm)
                "digitalStaff" -> StaffDigitalIdScreen(vm)
                "letters" -> LettersScreen(vm)
                "complaints" -> ComplaintsScreen(vm)
                "residents" -> ResidentsScreen(vm)
                "inbox" -> InboxScreen(vm)
                "command" -> CommandScreen(vm)
                "more" -> MoreScreen(vm)
                "generic" -> GenericModuleScreen(vm)
                else -> HomeScreen(vm)
            }
        }
    }

    if (showSearch) {
        NativeSearchSheet(vm, onDismiss = { showSearch = false })
    }
}

private fun roleLabel(me: UserMe?): String {
    if (me == null) return ""
    return when (me.role) {
        "RW" -> "Ketua RW 01 · Executive Workspace"
        "ADMIN" -> "Admin RW 01 · Executive Workspace"
        "RT" -> "Ketua RT " + (me.rtScope?.toIntOrNull() ?: 0) + " · Operational Workspace"
        else -> "Warga RW 01 · Super App"
    }
}

@Composable
private fun HomeScreen(vm: AppViewModel) {
    val home by vm.home
    val me by vm.me

    LaunchedEffect(Unit) { vm.loadHome() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { RoleHero(home, me) }
        item { NativePulse(home, me, vm) }
        item {
            Text("Akses cepat", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Ink)
        }
        item { QuickActions(me, vm) }
        item {
            Text("Pengumuman RW", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Ink)
        }
        val announcements = home?.optJSONArray("announcements")?.objects().orEmpty()
        if (announcements.isEmpty()) {
            item { EmptyCard("Belum ada pengumuman terbaru.") }
        } else {
            items(announcements) { AnnouncementCard(it) }
        }
        val events = home?.optJSONArray("events")?.objects().orEmpty()
        if (events.isNotEmpty()) {
            item {
                Text("Agenda terdekat", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Ink)
            }
            items(events) { EventCard(it) }
        }
    }
}

@Composable
private fun RoleHero(home: JSONObject?, me: UserMe?) {
    val resident = home?.optJSONObject("resident")
    val role = me?.role ?: "WARGA"
    val colors = when (role) {
        "RW", "ADMIN" -> listOf(Color(0xFF071F18), Color(0xFF0B5C3E))
        "RT" -> listOf(Color(0xFF0B3833), Color(0xFF147167))
        else -> listOf(Color(0xFF075140), Color(0xFF149269))
    }
    val title = when (role) {
        "RW", "ADMIN" -> "RW 01 dalam satu kendali"
        "RT" -> "RT " + (me?.rtScope?.toIntOrNull() ?: 0) + " siap beroperasi"
        else -> if (resident != null) "Sampurasun, " + resident.text("name") else "Sampurasun"
    }
    val subtitle = when (role) {
        "RW", "ADMIN" -> "Pantau pelayanan, warga, laporan, dan kondisi wilayah dari satu cockpit native."
        "RT" -> "Laporan, data warga, dan pelayanan wilayah tersedia dalam satu workspace."
        else -> resident?.let { "RT " + it.text("rt") + " / RW " + it.text("rw") + " · " + it.text("address_line") }
            ?: "Semua layanan warga RW 01 dalam satu aplikasi."
    }

    Card(shape = RadiusXL, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(
            Modifier.fillMaxWidth().background(Brush.linearGradient(colors), RadiusXL).padding(22.dp)
        ) {
            Column {
                Surface(color = Color.White.copy(alpha = .12f), shape = CircleShape) {
                    Text(
                        if (role == "WARGA") "LIVE · WARGA RW 01" else "LIVE · OPERATING SYSTEM",
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(title, color = Color.White, fontSize = 29.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(subtitle, color = Color.White.copy(alpha = .72f), fontSize = 11.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(20.dp))
                Text("PAMOYANAN ONE · NATIVE V4", color = Color.White.copy(alpha = .46f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NativePulse(home: JSONObject?, me: UserMe?, vm: AppViewModel) {
    val quick = home?.optJSONObject("quick")
    val role = me?.role ?: "WARGA"
    val cards = when (role) {
        "RW", "ADMIN" -> listOf(
            Triple("Surat", quick?.optInt("letters")?.toString() ?: "0", "letters"),
            Triple("Laporan", quick?.optInt("complaints")?.toString() ?: "0", "complaints"),
            Triple("Scope", "RT 01 + 02", "command")
        )
        "RT" -> listOf(
            Triple("Laporan", quick?.optInt("complaints")?.toString() ?: "0", "complaints"),
            Triple("Surat", quick?.optInt("letters")?.toString() ?: "0", "letters"),
            Triple("Wilayah", "RT " + (me?.rtScope?.toIntOrNull() ?: 0), "residents")
        )
        else -> listOf(
            Triple("Surat Saya", quick?.optInt("letters")?.toString() ?: "0", "letters"),
            Triple("Laporan Saya", quick?.optInt("complaints")?.toString() ?: "0", "complaints"),
            Triple("Akun", "Aktif", "digital")
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.forEach { card ->
            Card(
                modifier = Modifier.weight(1f).clickable { vm.navigate(card.third) },
                shape = RadiusMD,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(card.first.uppercase(), color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    Text(card.second, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
    }
}

data class QuickAction(val title: String, val subtitle: String, val icon: ImageVector, val route: String)

@Composable
private fun QuickActions(me: UserMe?, vm: AppViewModel) {
    val actions = when (me?.role) {
        "RW", "ADMIN" -> listOf(
            QuickAction("Command Center", "Executive cockpit", Icons.Outlined.Dashboard, "command"),
            QuickAction("Inbox", "Prioritas operasional", Icons.Outlined.Notifications, "inbox"),
            QuickAction("Pelayanan", "Approve & terbitkan", Icons.Outlined.Assignment, "letters"),
            QuickAction("Master Warga", "RT 01 + RT 02", Icons.Outlined.People, "residents")
        )
        "RT" -> listOf(
            QuickAction("Laporan", "Tangani aduan", Icons.Outlined.Campaign, "complaints"),
            QuickAction("Warga RT", "Master wilayah", Icons.Outlined.People, "residents"),
            QuickAction("Pelayanan", "Verifikasi surat", Icons.Outlined.Assignment, "letters"),
            QuickAction("Lainnya", "Modul wilayah", Icons.Outlined.GridView, "more")
        )
        else -> listOf(
            QuickAction("Digital ID", "Identitas warga", Icons.Outlined.QrCode2, "digital"),
            QuickAction("Ajukan Surat", "Administrasi", Icons.Outlined.Assignment, "letters"),
            QuickAction("Lapor RT", "Aduan wilayah", Icons.Outlined.Campaign, "complaints"),
            QuickAction("Semua Layanan", "Jelajahi modul", Icons.Outlined.GridView, "more")
        )
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height(210.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        gridItems(actions) { action ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { vm.navigate(action.route) },
                shape = RadiusLG,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Mint), contentAlignment = Alignment.Center) {
                        Icon(action.icon, null, tint = Brand, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(action.title, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Ink)
                    Text(action.subtitle, color = Muted, fontSize = 8.sp)
                }
            }
        }
    }
}

@Composable
private fun AnnouncementCard(obj: JSONObject) {
    Card(shape = RadiusLG, colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(15.dp)) {
            Text(obj.text("category").uppercase(), color = Brand, fontSize = 8.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text(obj.text("title"), color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(obj.text("body"), color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun EventCard(obj: JSONObject) {
    Card(shape = RadiusLG, colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFF9F2DD)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.LocalActivity, null, tint = Warning)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(obj.text("title"), fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text(obj.text("location_text"), color = Muted, fontSize = 9.sp)
                Text(obj.text("starts_at"), color = Muted, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Surface(shape = RadiusLG, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Hairline)) {
        Text(text, Modifier.padding(20.dp), color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun DigitalIdScreen(vm: AppViewModel) {
    val data by vm.digitalId
    var reveal by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadDigitalId() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Digital ID", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Ink)
            Text("Identitas digital resmi warga RW 01.", color = Muted, fontSize = 11.sp)
        }
        item {
            val obj = data
            if (obj == null) {
                EmptyCard("Digital ID sedang dimuat.")
            } else {
                DigitalIdCard(obj, reveal, onReveal = { reveal = !reveal }, onRotate = { vm.rotateQr() })
            }
        }
    }
}

@Composable
private fun DigitalIdCard(obj: JSONObject, reveal: Boolean, onReveal: () -> Unit, onRotate: () -> Unit) {
    Card(shape = RadiusXL, colors = CardDefaults.cardColors(containerColor = BrandDark)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("PAMOYANAN ONE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text("DIGITAL ID · RW 01 PAMOYANAN", color = Color.White.copy(alpha = .55f), fontSize = 8.sp)
                }
                Surface(color = Color.White.copy(alpha = .12f), shape = CircleShape) {
                    Text(obj.text("qr_status"), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, fontSize = 7.sp)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(obj.text("name"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 23.sp)
            Text(obj.text("member_no"), color = Color.White.copy(alpha = .64f), fontSize = 10.sp)
            Spacer(Modifier.height(16.dp))
            InfoLine("NIK", if (reveal) obj.text("nik") else maskNumber(obj.text("nik")))
            InfoLine("No. KK", if (reveal) obj.text("family_no") else maskNumber(obj.text("family_no")))
            InfoLine("Wilayah", "RT " + obj.text("rt") + " / RW " + obj.text("rw"))
            InfoLine("Alamat", obj.text("address_line"))
            val desil = obj.optInt("desil", 0)
            if (desil > 0) InfoLine("Desil", "DESIL " + desil)
            Spacer(Modifier.height(18.dp))
            val bitmap = remember(obj.optString("qr_data_url")) { decodeDataImage(obj.optString("qr_data_url")) }
            if (bitmap != null) {
                Surface(shape = RadiusLG, color = Color.White) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = "QR Digital ID",
                        modifier = Modifier.fillMaxWidth().height(210.dp).padding(14.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReveal, shape = RadiusMD) {
                    Text(if (reveal) "SEMBUNYIKAN DATA" else "LIHAT DATA", fontSize = 9.sp)
                }
                Button(onClick = onRotate, shape = RadiusMD, colors = ButtonDefaults.buttonColors(containerColor = Brand)) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PERBARUI QR", fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = Color.White.copy(alpha = .48f), fontSize = 8.sp, modifier = Modifier.width(70.dp))
        Text(value, color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LettersScreen(vm: AppViewModel) {
    val rows by vm.letters
    val templates by vm.templates
    val me by vm.me
    var showCreate by remember { mutableStateOf(false) }
    var timeline by remember { mutableStateOf<JSONObject?>(null) }
    var filter by remember { mutableStateOf("SEMUA") }

    LaunchedEffect(Unit) { vm.loadLetters() }

    val all = rows?.objects().orEmpty()
    val filtered = if (filter == "SEMUA") all else all.filter { it.text("status") == filter }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pelayanan Surat", fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Text("Native tracking · RT → RW → PDF + QR", color = Muted, fontSize = 10.sp)
                    }
                    IconButton(onClick = { vm.loadLetters() }) { Icon(Icons.Outlined.Refresh, null) }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("SEMUA", "SUBMITTED", "RT_VERIFIED", "RW_APPROVED", "ISSUED").forEach { status ->
                        FilterChip(
                            selected = filter == status,
                            onClick = { filter = status },
                            label = { Text(status.replace("_", " "), fontSize = 8.sp) }
                        )
                    }
                }
            }
            if (filtered.isEmpty()) item { EmptyCard("Belum ada data surat untuk filter ini.") }
            items(filtered, key = { it.text("id") }) { row ->
                LetterCard(
                    row = row,
                    role = me?.role ?: "WARGA",
                    onTimeline = {
                        vm.letterTimeline(row.text("id")) { timeline = it }
                    },
                    onAction = { action -> vm.letterAction(row.text("id"), action) },
                    onPdf = { vm.openLetterPdf(row.text("id")) }
                )
            }
        }

        if (me?.role == "WARGA") {
            Button(
                onClick = { showCreate = true },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(18.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandDark)
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(7.dp))
                Text("AJUKAN SURAT", fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
    }

    if (showCreate) {
        CreateLetterSheet(
            templates = templates?.objects().orEmpty(),
            onDismiss = { showCreate = false },
            onSubmit = { code, purpose, fields, req, priority, urgent ->
                showCreate = false
                vm.createLetter(code, purpose, fields, req, priority, urgent)
            }
        )
    }

    if (timeline != null) {
        TimelineSheet(timeline!!, onDismiss = { timeline = null })
    }
}

@Composable
private fun LetterCard(
    row: JSONObject,
    role: String,
    onTimeline: () -> Unit,
    onAction: (String) -> Unit,
    onPdf: () -> Unit
) {
    val status = row.text("status")
    val priority = row.text("priority", "NORMAL")
    Card(shape = RadiusLG, colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.text("letter_type"), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Text(row.text("request_no"), color = Muted, fontSize = 8.sp)
                }
                StatusPill(status)
            }
            Spacer(Modifier.height(9.dp))
            Text(row.text("purpose"), color = Ink, fontSize = 10.sp, lineHeight = 15.sp)
            if (priority == "URGENT") {
                Spacer(Modifier.height(7.dp))
                Surface(color = Color(0xFFFFEEEB), shape = RadiusMD) {
                    Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = Danger, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("URGENT · " + row.text("urgent_reason"), color = Danger, fontSize = 8.sp)
                    }
                }
            }
            if (row.text("official_no") != "-") {
                Spacer(Modifier.height(8.dp))
                Text("No. resmi: " + row.text("official_no"), color = Brand, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = onTimeline, shape = RadiusMD) { Text("TRACKING", fontSize = 8.sp) }
                if ((role == "RT" || role == "RW" || role == "ADMIN") && status == "SUBMITTED") {
                    FilledTonalButton(onClick = { onAction("rt-verify") }, shape = RadiusMD) { Text("VERIFIKASI RT", fontSize = 8.sp) }
                }
                if ((role == "RW" || role == "ADMIN") && status == "RT_VERIFIED") {
                    FilledTonalButton(onClick = { onAction("rw-approve") }, shape = RadiusMD) { Text("APPROVE RW", fontSize = 8.sp) }
                }
                if ((role == "RW" || role == "ADMIN") && status == "RW_APPROVED") {
                    Button(onClick = { onAction("issue") }, shape = RadiusMD, colors = ButtonDefaults.buttonColors(containerColor = BrandDark)) {
                        Text("TERBITKAN", fontSize = 8.sp)
                    }
                }
                if (status == "ISSUED" && row.optBoolean("pdf_available")) {
                    Button(onClick = onPdf, shape = RadiusMD, colors = ButtonDefaults.buttonColors(containerColor = Brand)) {
                        Icon(Icons.Outlined.Download, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("PDF", fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    Surface(color = statusColor(status).copy(alpha = .11f), shape = CircleShape) {
        Text(
            status.replace("_", " "),
            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            color = statusColor(status),
            fontSize = 7.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateLetterSheet(
    templates: List<JSONObject>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, JSONObject, JSONArray, String, String?) -> Unit
) {
    var selected by remember { mutableStateOf(templates.firstOrNull()?.text("code") ?: "") }
    var purpose by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("NORMAL") }
    var urgent by remember { mutableStateOf("") }
    val fields = remember { mutableStateMapOf<String, String>() }
    val checked = remember { mutableStateListOf<String>() }
    val template = templates.firstOrNull { it.text("code") == selected } ?: templates.firstOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(.88f),
            contentPadding = PaddingValues(18.dp, 0.dp, 18.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Ajukan Surat", fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("Form native PAMOYANAN ONE", color = Muted, fontSize = 10.sp)
            }
            item {
                Text("Jenis surat", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    templates.take(12).forEach { t ->
                        FilterChip(
                            selected = selected == t.text("code"),
                            onClick = {
                                selected = t.text("code")
                                fields.clear()
                                checked.clear()
                            },
                            label = { Text(t.text("short", t.text("title")), fontSize = 7.sp) }
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Keperluan / tujuan surat") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RadiusMD
                )
            }
            val requiredFields = template?.optJSONArray("required_fields")?.strings().orEmpty()
            requiredFields.forEach { key ->
                item {
                    OutlinedTextField(
                        value = fields[key].orEmpty(),
                        onValueChange = { fields[key] = it },
                        label = { Text(fieldLabel(key)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RadiusMD
                    )
                }
            }
            val requirements = template?.optJSONArray("requirements")?.objects().orEmpty()
            if (requirements.isNotEmpty()) {
                item { Text("Checklist persyaratan", fontWeight = FontWeight.Black, fontSize = 12.sp) }
                items(requirements) { req ->
                    val id = req.text("id")
                    Row(
                        Modifier.fillMaxWidth().clip(RadiusMD).background(Color.White).clickable {
                            if (checked.contains(id)) checked.remove(id) else checked.add(id)
                        }.padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked.contains(id), onCheckedChange = {
                            if (it) { if (!checked.contains(id)) checked.add(id) } else checked.remove(id)
                        })
                        Text(req.text("label"), fontSize = 10.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(selected = priority == "NORMAL", onClick = { priority = "NORMAL" }, label = { Text("NORMAL") })
                    FilterChip(selected = priority == "URGENT", onClick = { priority = "URGENT" }, label = { Text("URGENT") })
                }
            }
            if (priority == "URGENT") {
                item {
                    OutlinedTextField(
                        value = urgent,
                        onValueChange = { urgent = it },
                        label = { Text("Alasan urgent") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RadiusMD
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        val form = JSONObject()
                        fields.forEach { (k, v) -> form.put(k, v) }
                        val req = JSONArray()
                        checked.forEach { req.put(it) }
                        onSubmit(selected, purpose, form, req, priority, urgent.takeIf { it.isNotBlank() })
                    },
                    enabled = selected.isNotBlank() && purpose.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RadiusMD,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandDark)
                ) {
                    Text("KIRIM PENGAJUAN", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun fieldLabel(key: String): String = when (key) {
    "business_name" -> "Nama usaha"
    "destination_address" -> "Alamat tujuan pindah"
    "institution_name" -> "Nama lembaga / institusi"
    "object_address" -> "Alamat objek"
    else -> key.replace("_", " ").replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineSheet(data: JSONObject, onDismiss: () -> Unit) {
    val events = data.optJSONArray("events")?.objects().orEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(18.dp, 0.dp, 18.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Tracking Surat", fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(data.text("request_no"), color = Muted, fontSize = 10.sp)
            }
            items(events) { e ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(if (e.optBoolean("done")) Brand else Hairline),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = if (e.optBoolean("done")) Color.White else Muted, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(e.text("label"), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(
                            if (e.optBoolean("done")) e.text("at", "Selesai") else "Menunggu",
                            color = Muted,
                            fontSize = 8.sp
                        )
                        if (e.text("actor") != "-") Text(e.text("actor"), color = Muted, fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComplaintsScreen(vm: AppViewModel) {
    val rows by vm.complaints
    val me by vm.me
    var showCreate by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<JSONObject?>(null) }
    var actionName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadComplaints() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (me?.role == "WARGA") "Lapor RT" else "Laporan Wilayah", fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Text(
                            if (me?.role == "WARGA") "RT menangani · RW memantau" else "Operational complaint workflow",
                            color = Muted,
                            fontSize = 10.sp
                        )
                    }
                    IconButton(onClick = { vm.loadComplaints() }) { Icon(Icons.Outlined.Refresh, null) }
                }
            }
            val list = rows?.objects().orEmpty()
            if (list.isEmpty()) item { EmptyCard("Belum ada laporan.") }
            items(list, key = { it.text("id") }) { row ->
                ComplaintCard(
                    row = row,
                    role = me?.role ?: "WARGA",
                    onAction = { action ->
                        actionTarget = row
                        actionName = action
                    }
                )
            }
        }

        Button(
            onClick = { showCreate = true },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(18.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = BrandDark)
        ) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(7.dp))
            Text("BUAT LAPORAN", fontSize = 10.sp)
        }
    }

    if (showCreate) {
        CreateComplaintSheet(
            role = me?.role ?: "WARGA",
            rtScope = me?.rtScope,
            onDismiss = { showCreate = false },
            onSubmit = { category, location, body, priority, rt ->
                showCreate = false
                vm.createComplaint(category, location, body, priority, rt)
            }
        )
    }

    if (actionTarget != null) {
        ComplaintActionSheet(
            row = actionTarget!!,
            action = actionName,
            onDismiss = { actionTarget = null },
            onSubmit = { note ->
                val id = actionTarget!!.text("id")
                actionTarget = null
                vm.complaintAction(id, actionName, note)
            }
        )
    }
}

@Composable
private fun ComplaintCard(row: JSONObject, role: String, onAction: (String) -> Unit) {
    val status = row.text("status")
    val priority = row.text("priority", "NORMAL")
    Card(shape = RadiusLG, colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.text("category"), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Text(row.text("complaint_no"), color = Muted, fontSize = 8.sp)
                }
                StatusPill(if (priority == "URGENT") "URGENT" else status)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, tint = Muted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(row.text("location_text"), color = Muted, fontSize = 9.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(row.text("body"), color = Ink, fontSize = 10.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(8.dp))
            Text("Routing: " + row.text("routing_state") + " · Handler: " + row.text("current_handler"), color = Muted, fontSize = 8.sp)
            if (role == "RT") {
                Spacer(Modifier.height(9.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ACK" to "TERIMA", "START" to "MULAI", "ESCALATE_RW" to "ESKALASI", "RESOLVE" to "SELESAI").forEach { (a, label) ->
                        OutlinedButton(onClick = { onAction(a) }, shape = RadiusMD) { Text(label, fontSize = 7.sp) }
                    }
                }
            }
            if (role == "RW" || role == "ADMIN") {
                Spacer(Modifier.height(9.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("RW_NOTE" to "CATATAN", "RW_TAKEOVER" to "AMBIL ALIH", "RESOLVE" to "SELESAI", "REJECT" to "TUTUP").forEach { (a, label) ->
                        OutlinedButton(onClick = { onAction(a) }, shape = RadiusMD) { Text(label, fontSize = 7.sp) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateComplaintSheet(
    role: String,
    rtScope: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String?) -> Unit
) {
    var category by remember { mutableStateOf("Kebersihan") }
    var location by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("NORMAL") }
    var rt by remember { mutableStateOf(rtScope ?: "001") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(18.dp, 0.dp, 18.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Buat Laporan", fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("Laporan masuk ke RT terlebih dahulu dan RW memantau.", color = Muted, fontSize = 10.sp)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Kebersihan", "Sampah", "PJU", "Keamanan", "Drainase", "Lainnya").forEach {
                        FilterChip(selected = category == it, onClick = { category = it }, label = { Text(it, fontSize = 8.sp) })
                    }
                }
            }
            if (role != "WARGA" && rtScope == null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = rt == "001", onClick = { rt = "001" }, label = { Text("RT 01") })
                        FilterChip(selected = rt == "002", onClick = { rt = "002" }, label = { Text("RT 02") })
                    }
                }
            }
            item {
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Lokasi") }, modifier = Modifier.fillMaxWidth(), shape = RadiusMD)
            }
            item {
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Isi laporan") }, modifier = Modifier.fillMaxWidth(), minLines = 4, shape = RadiusMD)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("LOW", "NORMAL", "HIGH", "URGENT").forEach {
                        FilterChip(selected = priority == it, onClick = { priority = it }, label = { Text(it, fontSize = 8.sp) })
                    }
                }
            }
            item {
                Button(
                    onClick = { onSubmit(category, location, body, priority, if (role == "WARGA") null else rt) },
                    enabled = location.isNotBlank() && body.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RadiusMD,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandDark)
                ) {
                    Icon(Icons.Outlined.Send, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("KIRIM KE RT", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComplaintActionSheet(row: JSONObject, action: String, onDismiss: () -> Unit, onSubmit: (String?) -> Unit) {
    var note by remember { mutableStateOf("") }
    val noteRequired = action in listOf("ESCALATE_RW", "RESOLVE", "REJECT")
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(18.dp).navigationBarsPadding()) {
            Text("Tindak Lanjut", fontWeight = FontWeight.Black, fontSize = 21.sp)
            Text(row.text("complaint_no") + " · " + action.replace("_", " "), color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(if (noteRequired) "Catatan wajib" else "Catatan (opsional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RadiusMD
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { onSubmit(note.takeIf { it.isNotBlank() }) },
                enabled = !noteRequired || note.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RadiusMD,
                colors = ButtonDefaults.buttonColors(containerColor = BrandDark)
            ) {
                Text("SIMPAN TINDAK LANJUT")
            }
        }
    }
}

@Composable
private fun ResidentsScreen(vm: AppViewModel) {
    val rows by vm.residents
    val me by vm.me
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadResidents() }

    LazyColumn(
        contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Text("Master Warga", fontWeight = FontWeight.Black, fontSize = 24.sp)
            Text(
                if (me?.role == "RT") "Scope RT " + (me?.rtScope?.toIntOrNull() ?: 0) else "Scope RW 01 · RT 01 + RT 02",
                color = Muted,
                fontSize = 10.sp
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isBlank() || it.length >= 2) vm.loadResidents(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cari nama / nomor anggota / NIK") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RadiusMD
            )
        }
        val list = rows?.objects().orEmpty()
        item {
            Text(list.size.toString() + " warga ditampilkan", color = Muted, fontSize = 9.sp)
        }
        items(list, key = { it.text("id") }) { row ->
            ResidentCard(row)
        }
    }
}

@Composable
private fun ResidentCard(row: JSONObject) {
    Card(shape = RadiusLG, colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) {
                Text(row.text("name").take(1).uppercase(), color = Brand, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(row.text("name"), fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text(row.text("member_no"), color = Muted, fontSize = 8.sp)
                Text("RT " + row.text("rt") + " / RW " + row.text("rw"), color = Muted, fontSize = 8.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                val desil = row.optInt("desil", 0)
                if (desil > 0) {
                    Surface(color = Mint, shape = CircleShape) {
                        Text("D" + desil, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Brand, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(maskNumber(row.text("nik")), color = Muted, fontSize = 7.sp)
            }
        }
    }
}

@Composable
private fun InboxScreen(vm: AppViewModel) {
    val data by vm.inbox
    LaunchedEffect(Unit) { vm.loadInbox() }
    val notifications = data?.optJSONArray("notifications")?.objects().orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Inbox Operasional", fontWeight = FontWeight.Black, fontSize = 24.sp)
                    Text((data?.optInt("unread") ?: 0).toString() + " belum dibaca", color = Muted, fontSize = 10.sp)
                }
                TextButton(onClick = { vm.markAllRead() }) { Text("BACA SEMUA", fontSize = 8.sp) }
            }
        }
        if (notifications.isEmpty()) item { EmptyCard("Inbox bersih.") }
        items(notifications, key = { it.text("id") }) { n ->
            Card(
                shape = RadiusLG,
                colors = CardDefaults.cardColors(containerColor = if (n.optBoolean("is_read")) Color.White else Mint),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Notifications, null, tint = Brand, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(n.text("title"), fontWeight = FontWeight.Black, fontSize = 10.sp)
                        Text(n.text("body"), color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
                        Text(n.text("created_at"), color = Muted, fontSize = 7.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandScreen(vm: AppViewModel) {
    val data by vm.command
    LaunchedEffect(Unit) { vm.loadCommand() }

    LazyColumn(
        contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Command Center", fontWeight = FontWeight.Black, fontSize = 24.sp)
            Text("Executive operating cockpit · RW 01", color = Muted, fontSize = 10.sp)
        }
        item {
            Card(shape = RadiusXL, colors = CardDefaults.cardColors(containerColor = BrandDark)) {
                Column(Modifier.padding(20.dp)) {
                    Text("RW 01 LIVE", color = Color.White.copy(alpha = .56f), fontSize = 8.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text("Keputusan berbasis data operasional.", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 27.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Pantau demografi, layanan, laporan, sosial, dan alert wilayah secara native.", color = Color.White.copy(alpha = .68f), fontSize = 10.sp, lineHeight = 15.sp)
                }
            }
        }
        val obj = data
        if (obj == null) {
            item { EmptyCard("Command Center sedang dimuat.") }
        } else {
            val rtCounts = obj.optJSONObject("rt_counts")
            if (rtCounts != null) {
                item { SectionHeader("Warga per RT", "Master aktif RW 01") }
                item { ObjectMetricRow(rtCounts, prefix = "RT ") }
            }
            val desil = obj.optJSONObject("desil_counts")
            if (desil != null) {
                item { SectionHeader("Distribusi Desil", "Ringkasan sosial DTSEN") }
                item { ObjectMetricGrid(desil, prefix = "D") }
            }
            val alerts = obj.optJSONArray("smart_alerts") ?: obj.optJSONArray("alerts")
            if (alerts != null && alerts.length() > 0) {
                item { SectionHeader("Smart Alert", "Perlu perhatian") }
                items(alerts.objects().take(8)) { a ->
                    GenericObjectCard(a)
                }
            }
            item { SectionHeader("Data Command", "Snapshot API") }
            item { GenericObjectCard(obj) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(subtitle, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun ObjectMetricRow(obj: JSONObject, prefix: String = "") {
    val keys = obj.keys().asSequence().toList().take(4)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        keys.forEach { key ->
            Card(
                modifier = Modifier.weight(1f),
                shape = RadiusMD,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(prefix + key, color = Muted, fontSize = 7.sp)
                    Text(obj.opt(key).toString(), fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun ObjectMetricGrid(obj: JSONObject, prefix: String = "") {
    val keys = obj.keys().asSequence().toList().take(10)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { key ->
            AssistChip(onClick = {}, label = { Text(prefix + key + " · " + obj.opt(key).toString(), fontSize = 8.sp) })
        }
    }
}

data class ModuleDef(val id: String, val title: String, val subtitle: String, val icon: ImageVector, val path: String?)

@Composable
private fun MoreScreen(vm: AppViewModel) {
    val me by vm.me
    val base = mutableListOf(
        ModuleDef("business", "Usaha & UMKM", "Jelajah ekonomi RW 01", Icons.Outlined.Storefront, "/superapp/businesses"),
        ModuleDef("social", "Sosial", "Desil & bantuan", Icons.Outlined.People, "/superapp/social"),
        ModuleDef("events", "Event", "Agenda wilayah", Icons.Outlined.LocalActivity, "/superapp/events"),
        ModuleDef("pbb", "PBB", "Objek pajak wilayah", Icons.Outlined.Apartment, "/superapp/pbb"),
        ModuleDef("security", "Siaga", "Keamanan wilayah", Icons.Outlined.WarningAmber, "/superapp/security"),
        ModuleDef("environment", "Lingkungan", "Kebersihan & aset", Icons.Outlined.LocationOn, "/superapp/environment"),
        ModuleDef("health", "Kesehatan", "Aktivitas kesehatan", Icons.Outlined.Info, "/superapp/health"),
        ModuleDef("finance", "Keuangan", "Ringkasan keuangan", Icons.Outlined.MailOutline, "/superapp/finance"),
        ModuleDef("qris", "QRIS", "Pembayaran digital", Icons.Outlined.QrCode2, "/superapp/qris"),
        ModuleDef("map", "Peta RW", "Bangunan & wilayah", Icons.Outlined.LocationOn, "/superapp/map")
    )
    if (me?.role != "WARGA") {
        base.add(ModuleDef("register", "Register Surat", "Buku register", Icons.Outlined.Assignment, "/letters/register"))
        base.add(ModuleDef("notify", "Notifikasi", "Smart notification center", Icons.Outlined.Notifications, "/superapp/smart-notification-center"))
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("Semua Layanan", fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text("Native module launcher", color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            gridItems(base) { module ->
                Card(
                    modifier = Modifier.fillMaxWidth().height(126.dp).clickable {
                        if (module.path != null) vm.loadGeneric(module.title, module.path)
                    },
                    shape = RadiusLG,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Mint), contentAlignment = Alignment.Center) {
                            Icon(module.icon, null, tint = Brand, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(module.title, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Text(module.subtitle, color = Muted, fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GenericModuleScreen(vm: AppViewModel) {
    val title by vm.genericTitle
    val data by vm.genericData
    LazyColumn(
        contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.navigate("more") }) { Icon(Icons.Outlined.ArrowBack, null) }
                Column {
                    Text(title, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text("Native data module", color = Muted, fontSize = 9.sp)
                }
            }
        }
        when (val current = data) {
            is JSONArray -> {
                val list = current.objects()
                if (list.isEmpty()) item { EmptyCard("Belum ada data.") }
                items(list.take(120)) { GenericObjectCard(it) }
            }
            is JSONObject -> item { GenericObjectCard(current) }
            else -> item { EmptyCard("Memuat data...") }
        }
    }
}

@Composable
private fun GenericObjectCard(obj: JSONObject) {
    val keys = obj.keys().asSequence().toList()
        .filterNot { it.contains("phone", true) || it.contains("nik", true) || it.contains("family_no", true) }
        .take(9)
    Card(shape = RadiusLG, colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(14.dp)) {
            val titleKey = listOf("title", "name", "complaint_no", "official_no", "request_no", "category").firstOrNull { obj.has(it) }
            if (titleKey != null) {
                Text(obj.text(titleKey), fontWeight = FontWeight.Black, fontSize = 11.sp)
                Spacer(Modifier.height(5.dp))
            }
            keys.filterNot { it == titleKey }.forEach { key ->
                val value = obj.opt(key)
                if (value !is JSONObject && value !is JSONArray) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(key.replace("_", " "), color = Muted, fontSize = 8.sp, modifier = Modifier.width(100.dp))
                        Text(value?.toString() ?: "-", color = Ink, fontSize = 8.sp, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val nestedObjects = keys.mapNotNull { key -> (obj.opt(key) as? JSONObject)?.let { key to it } }
            nestedObjects.take(2).forEach { pair ->
                Spacer(Modifier.height(6.dp))
                Text(pair.first.replace("_", " ").uppercase(), color = Brand, fontSize = 7.sp, fontWeight = FontWeight.Black)
                ObjectMetricGrid(pair.second)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeSearchSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val me by vm.me
    val results by vm.searchResults
    var query by remember { mutableStateOf("") }

    val local = listOf(
        Triple("Digital ID", "Identitas warga", "digital"),
        Triple("Pelayanan Surat", "Ajukan dan tracking", "letters"),
        Triple("Lapor RT", "Laporan wilayah", "complaints"),
        Triple("Usaha & UMKM", "Jelajah ekonomi", "business"),
        Triple("Event", "Agenda RW", "events"),
        Triple("PBB", "Informasi pajak", "pbb")
    ).filter { (it.first + " " + it.second).contains(query, true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(.82f).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = Brand)
                Spacer(Modifier.width(8.dp))
                Text("Spotlight", fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    vm.search(it)
                },
                placeholder = { Text(if (me?.role == "WARGA") "Cari layanan..." else "Cari warga, surat, laporan...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RadiusLG,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, null) }
                }
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (me?.role == "WARGA") {
                    items(local) { item ->
                        SearchRow(item.first, item.second, Icons.Outlined.ChevronRight) {
                            onDismiss()
                            when (item.third) {
                                "business" -> vm.loadGeneric("Usaha & UMKM", "/superapp/businesses")
                                "events" -> vm.loadGeneric("Event", "/superapp/events")
                                "pbb" -> vm.loadGeneric("PBB", "/superapp/pbb")
                                else -> vm.navigate(item.third)
                            }
                        }
                    }
                } else {
                    val arr = (results as? JSONObject)?.optJSONArray("results")?.objects().orEmpty()
                    if (query.length < 2) {
                        item { EmptyCard("Ketik minimal 2 karakter untuk mencari data operasional.") }
                    } else if (arr.isEmpty()) {
                        item { EmptyCard("Tidak ada hasil.") }
                    } else {
                        items(arr) { r ->
                            SearchRow(r.text("title"), r.text("subtitle"), Icons.Outlined.ChevronRight) {
                                onDismiss()
                                val view = r.text("view", "home")
                                vm.navigate(
                                    when (view) {
                                        "warga" -> "residents"
                                        "pelayanan" -> "letters"
                                        "lapor" -> "complaints"
                                        "inbox" -> "inbox"
                                        "command" -> "command"
                                        else -> "home"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RadiusMD,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 10.sp)
                Text(subtitle, color = Muted, fontSize = 8.sp)
            }
            Icon(icon, null, tint = Muted)
        }
    }
}
