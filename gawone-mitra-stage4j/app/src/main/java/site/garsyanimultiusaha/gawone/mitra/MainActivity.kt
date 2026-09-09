package site.garsyanimultiusaha.gawone.mitra

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val api = GawoneApi()
    private lateinit var secure: SecureSessionStore
    private var session by mutableStateOf<AuthSession?>(null)
    private var restoring by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler { _, _ ->
            runCatching { secure.clear() }
            finish()
        }
        secure = SecureSessionStore(this)
        lifecycleScope.launch {
            val refresh = withContext(Dispatchers.IO) { secure.loadRefreshToken() }
            session = if (refresh.isNullOrBlank()) null else runCatching {
                withContext(Dispatchers.IO) { api.refresh(refresh) }
            }.getOrNull()?.also { secure.saveRefreshToken(it.refreshToken) }
            restoring = false
        }
        setContent { GawoneTheme { AppRoot() } }
    }

    @Composable
    private fun AppRoot() {
        when {
            BuildConfig.MAINTENANCE_MODE -> GateScreen("Pemeliharaan", "Layanan sedang dalam pemeliharaan.")
            BuildConfig.VERSION_CODE < BuildConfig.MIN_SUPPORTED_VERSION_CODE ->
                GateScreen("Perbarui aplikasi", "Versi ini sudah tidak didukung.")
            restoring -> CenterMessage("Memulihkan sesi aman…")
            session == null -> LoginScreen()
            else -> MainShell()
        }
    }

    @Composable
    private fun LoginScreen() {
        var phone by remember { mutableStateOf("+62") }
        var otp by remember { mutableStateOf("") }
        var otpSent by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("gawone", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = BrandGreen)
                Text("Mitra", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Peluang kerja dan penghasilan dalam satu aplikasi.", color = Color.DarkGray)
                Spacer(Modifier.height(28.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text("Nomor HP") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                if (otpSent) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = otp,
                        onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                        label = { Text("Kode OTP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = !busy && phone.length >= 8 && (!otpSent || otp.length == 6),
                    onClick = {
                        busy = true; error = null
                        lifecycleScope.launch {
                            runCatching {
                                if (!otpSent) {
                                    withContext(Dispatchers.IO) { api.sendPhoneOtp(phone) }
                                    otpSent = true
                                } else {
                                    val s = withContext(Dispatchers.IO) { api.verifyPhoneOtp(phone, otp) }
                                    secure.saveRefreshToken(s.refreshToken)
                                    session = s
                                }
                            }.onFailure { error = it.message ?: "Tidak dapat melanjutkan" }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (otpSent) "Masuk" else "Kirim OTP") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                Spacer(Modifier.height(20.dp))
                Text("RC1 • sesi dipulihkan melalui Android Keystore", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }

    @Composable
    private fun MainShell() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val online = rememberConnectivity()
        val labels = listOf("Beranda", "Order", "Jadwal", "Pendapatan", "Akun")
        val icons = listOf(Icons.Default.Home, Icons.Default.ReceiptLong, Icons.Default.CalendarMonth, Icons.Default.AccountBalanceWallet, Icons.Default.Person)

        Scaffold(
            bottomBar = {
                NavigationBar {
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(icons[i], contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (!online) {
                    Surface(color = Color(0xFFFFF3CD), modifier = Modifier.fillMaxWidth()) {
                        Text("Mode terbatas • koneksi tidak tersedia", Modifier.padding(12.dp))
                    }
                }
                when (tab) {
                    0 -> HomeScreen()
                    1 -> ModuleScreen("Order", "Penawaran, order aktif, chat, dan progres pekerjaan.", listOf("Accept / reject server-authoritative", "EN_ROUTE → ARRIVED → CHECKED_IN → WORKING → FINISHED", "Proof & issue blocker"))
                    2 -> ModuleScreen("Jadwal", "Jadwal diturunkan dari assignment aktif.", listOf("Hari ini", "Mendatang", "Riwayat assignment"))
                    3 -> ModuleScreen("Pendapatan", "Ringkasan penghasilan dan payout Mitra.", listOf("Saldo tersedia", "Riwayat transaksi", "Status payout / hold"))
                    else -> AccountScreen()
                }
            }
        }
    }

    @Composable
    private fun HomeScreen() {
        val context = LocalContext.current
        var partnerOnline by remember { mutableStateOf(false) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true || granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                ContextCompat.startForegroundService(context, Intent(context, PartnerLocationService::class.java))
                partnerOnline = true
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("GAWONE Mitra", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Works for a Better You", color = Color.Gray)
            Spacer(Modifier.height(18.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(if (partnerOnline) "ONLINE" else "OFFLINE", fontWeight = FontWeight.Bold, color = if (partnerOnline) BrandGreen else Color.Gray)
                    Text("Lokasi hanya diminta saat Anda memilih Online.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        if (partnerOnline) {
                            context.stopService(Intent(context, PartnerLocationService::class.java))
                            partnerOnline = false
                        } else if (
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        ) {
                            ContextCompat.startForegroundService(context, Intent(context, PartnerLocationService::class.java))
                            partnerOnline = true
                        } else {
                            launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        }
                    }) { Text(if (partnerOnline) "Offline" else "Online") }
                }
            }
            Spacer(Modifier.height(14.dp))
            StatusCard("Verifikasi", "KYC private • KTP / Selfie / SIM / STNK")
            StatusCard("Order aktif", "Lifecycle dan check-in dikendalikan backend")
            StatusCard("Realtime", "Assignment • wallet • KYC • chat • notifikasi")
            StatusCard("Keamanan RC1", "Keystore • cleartext OFF • backup OFF • recovery")
        }
    }

    @Composable
    private fun StatusCard(title: String, body: String) {
        Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body, color = Color.DarkGray, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    @Composable
    private fun ModuleScreen(title: String, subtitle: String, bullets: List<String>) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.DarkGray)
            Spacer(Modifier.height(18.dp))
            bullets.forEach { StatusCard(it, "Terhubung ke kontrak Stage 4 cumulative.") }
        }
    }

    @Composable
    private fun AccountScreen() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("Akun", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            StatusCard("Performa & rating", "Metrik authoritative dari backend assignment/rating.")
            StatusCard("Layanan, kendaraan & dokumen", "Kelola kesiapan Mitra dan KYC.")
            StatusCard("Restriction & appeal", "Suspend/restriction tetap server-side.")
            StatusCard("Bantuan", "Support ticket dan pesan.")
            StatusCard("Privasi", "Deaktivasi, ekspor, dan penghapusan dengan retention review.")
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                secure.clear()
                session = null
            }, modifier = Modifier.fillMaxWidth()) { Text("Keluar") }
        }
    }

    @Composable
    private fun rememberConnectivity(): Boolean {
        val context = LocalContext.current
        val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
        var online by remember {
            mutableStateOf(cm.activeNetwork != null)
        }
        DisposableEffect(cm) {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { online = true }
                override fun onLost(network: Network) { online = cm.activeNetwork != null }
            }
            cm.registerDefaultNetworkCallback(callback)
            onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
        }
        return online
    }

    @Composable
    private fun GateScreen(title: String, message: String) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Build, contentDescription = null, tint = BrandGreen)
                Spacer(Modifier.height(10.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(message, color = Color.DarkGray)
            }
        }
    }

    @Composable
    private fun CenterMessage(message: String) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
    }
}

private val BrandGreen = Color(0xFF00A676)

@Composable
private fun GawoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BrandGreen,
            secondary = Color(0xFF2E3D32),
            background = Color(0xFFF8FAF9),
            surface = Color.White
        ),
        content = content
    )
}
