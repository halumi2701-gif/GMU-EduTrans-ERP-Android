package site.garsyanimultiusaha.gawone.mitra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GawoneMitraTrial() }
    }
}

private val GawoneGreen = Color(0xFF0A6B47)
private val GawoneSoft = Color(0xFFE8F4EE)
private val GawoneBg = Color(0xFFF7FAF8)

@Composable
fun GawoneMitraTrial() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = GawoneGreen,
            primaryContainer = GawoneSoft,
            background = GawoneBg,
            surface = Color.White
        )
    ) {
        var screen by remember { mutableStateOf("welcome") }
        when (screen) {
            "welcome" -> Welcome { screen = "login" }
            "login" -> Login { screen = "onboarding" }
            "onboarding" -> Onboarding { screen = "main" }
            else -> MainShell()
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = GawoneGreen,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("G", color = Color.White, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("GAWONE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Mitra", color = GawoneGreen, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Scaffold(containerColor = GawoneBg) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Brand()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Kerja lebih mudah dari satu aplikasi.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("GAWONE Mitra untuk driver, cleaner, helper, teknisi, tukang, crew, dan tenaga operasional lainnya.")
                Surface(color = GawoneSoft, shape = RoundedCornerShape(18.dp)) {
                    Text("APK Uji Coba Alpha 0.1 — fokus install, stabilitas UI, dan navigasi.", Modifier.padding(16.dp))
                }
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Mulai")
            }
        }
    }
}

@Composable
private fun Login(onNext: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    Scaffold(containerColor = GawoneBg) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Brand()
            Spacer(Modifier.height(20.dp))
            Text("Masuk / Daftar Mitra", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.take(16) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nomor HP") },
                leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, null) },
                singleLine = true
            )
            Text("Untuk APK uji coba pertama, OTP backend belum dijalankan. Tombol ini hanya menguji alur UI native.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth(), enabled = phone.length >= 8) {
                Text("Lanjut Uji Coba")
            }
        }
    }
}

@Composable
private fun Onboarding(onDone: () -> Unit) {
    var selected by remember { mutableStateOf("RIDE") }
    val services = listOf("RIDE", "CAR", "KIRIM", "DRIVER", "CLEANING", "HELPER", "TUKANG", "TEKNISI")
    Scaffold(containerColor = GawoneBg) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Brand()
            Text("Pilih layanan utama", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            services.forEach { service ->
                OutlinedButton(
                    onClick = { selected = service },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selected == service) "✓ $service" else service)
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Masuk Beranda Mitra")
            }
        }
    }
}

private data class NavItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun MainShell() {
    val items = listOf(
        NavItem("Beranda", Icons.Outlined.Home),
        NavItem("Order", Icons.Outlined.Inventory2),
        NavItem("Jadwal", Icons.Outlined.CalendarMonth),
        NavItem("Pendapatan", Icons.Outlined.AccountBalanceWallet),
        NavItem("Akun", Icons.Outlined.Person)
    )
    var selected by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = GawoneBg,
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(item.icon, item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Brand()
            when (selected) {
                0 -> {
                    Text("Beranda Mitra", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Surface(color = GawoneSoft, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Status", fontWeight = FontWeight.Bold)
                            Text("Offline — mode uji coba")
                            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                Text("Mulai Online — tahap berikutnya")
                            }
                        }
                    }
                    TrialCard("Order hari ini", "Belum ada order.")
                    TrialCard("Performa", "Rating dan penyelesaian pekerjaan akan tampil di sini.")
                }
                1 -> TrialCard("Order", "Offer baru dan order aktif akan masuk setelah koneksi backend diaktifkan.")
                2 -> TrialCard("Jadwal", "Jadwal kerja dan shift.")
                3 -> TrialCard("Pendapatan", "Saldo, komisi, tip, dan payout.")
                else -> TrialCard("Akun", "Profil Mitra, layanan, KYC, kendaraan, dan keamanan akun.")
            }
        }
    }
}

@Composable
private fun TrialCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
