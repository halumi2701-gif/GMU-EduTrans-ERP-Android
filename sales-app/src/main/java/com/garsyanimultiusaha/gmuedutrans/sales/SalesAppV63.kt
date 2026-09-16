package com.garsyanimultiusaha.gmuedutrans.sales

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val V63Green = Color(0xFF128000)
private val V63GreenDark = Color(0xFF07580F)
private val V63Gold = Color(0xFFD5A300)
private val V63Background = Color(0xFFF5F8F4)

@Composable
fun SalesAppV63(vm: SalesViewModel) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = V63Green,
            secondary = V63Gold,
            background = V63Background,
            surface = Color.White,
            onPrimary = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = V63Background) {
            when (val state = vm.state) {
                SalesAppState.Splash -> V63Loading("Menyiapkan GMU EduTrans Sales…")
                SalesAppState.Loading -> V63Loading("Mengamankan sesi dan menyinkronkan data…")
                SalesAppState.LoggedOut -> V63Login(vm)
                is SalesAppState.Error -> V63Error(state.message, vm::backToLogin)
                is SalesAppState.LoggedIn -> V63OnboardingGate(vm, state.session)
            }
        }
    }
}

@Composable
private fun V63OnboardingGate(vm: SalesViewModel, session: SalesSession) {
    val api = remember { SalesOnboardingApi() }
    var required by remember(session.userId, session.accessToken) { mutableStateOf<Boolean?>(null) }
    var error by remember(session.userId, session.accessToken) { mutableStateOf<String?>(null) }

    LaunchedEffect(session.userId, session.accessToken) {
        runCatching { api.onboardingRequired(session) }
            .onSuccess { required = it }
            .onFailure {
                error = it.message ?: "Status onboarding belum dapat diverifikasi."
                required = true
            }
    }

    when (required) {
        null -> V63Loading("Memeriksa aktivasi akun Sales…")
        true -> V63FirstPasswordScreen(
            session = session,
            api = api,
            initialError = error,
            onComplete = {
                required = false
                error = null
                vm.refresh()
            },
            onLogout = vm::logout
        )
        false -> SalesAppV62(vm)
    }
}

@Composable
private fun V63Login(vm: SalesViewModel) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val joinUrl = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/public-quotation-decision?mode=sales-join"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.sales_logo),
            contentDescription = "GMU EduTrans",
            modifier = Modifier.width(210.dp).height(126.dp)
        )
        Text("Sales Operating System", fontWeight = FontWeight.Black, fontSize = 22.sp, color = V63GreenDark)
        Text("v${BuildConfig.VERSION_NAME} • Secure End-to-End", color = Color.Gray, fontSize = 10.sp)
        Spacer(Modifier.height(18.dp))

        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(20.dp)) {
                Text("Masuk Sales", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Gunakan akun Sales aktif yang telah disetujui GMU.", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Sales") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { vm.login(email, password) },
                    enabled = !vm.actionBusy && email.isNotBlank() && password.length >= 8,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (vm.actionBusy) "Memproses…" else "Masuk sebagai Sales", fontWeight = FontWeight.Black)
                }
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                OutlinedButton(
                    onClick = { v63OpenUrl(context, joinUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Join / Ajukan Akun Sales") }
                TextButton(
                    onClick = { v63OpenUrl(context, joinUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cek Status Pengajuan") }
                Text(
                    "Pengajuan → review Owner → akun dibuat → password awal → wajib ganti password → ACTIVE.",
                    fontSize = 9.sp,
                    lineHeight = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun V63FirstPasswordScreen(
    session: SalesSession,
    api: SalesOnboardingApi,
    initialError: String?,
    onComplete: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember(initialError) { mutableStateOf(initialError) }
    val passwordValid = password.length >= 10 &&
        password.any(Char::isUpperCase) &&
        password.any(Char::isLowerCase) &&
        password.any(Char::isDigit) &&
        password.any { !it.isLetterOrDigit() }

    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                Modifier.padding(22.dp).widthIn(max = 480.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(painterResource(R.drawable.sales_logo), "GMU EduTrans", Modifier.width(180.dp).height(106.dp))
                Text("Aktivasi Akun Sales", fontWeight = FontWeight.Black, fontSize = 22.sp, color = V63GreenDark)
                Text("Selamat datang, ${session.profile.fullName}.", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D4)), shape = RoundedCornerShape(16.dp)) {
                    Text(
                        "Password yang diberikan Owner/Admin adalah password awal. Anda wajib menggantinya sebelum dapat membuka CRM, customer, quotation, dan data kerja Sales.",
                        Modifier.padding(14.dp),
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password baru") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Ulangi password baru") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Minimal 10 karakter • huruf besar • huruf kecil • angka • simbol",
                    fontSize = 9.sp,
                    color = if (password.isBlank() || passwordValid) Color.Gray else Color.Red
                )
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color.Red, fontSize = 10.sp)
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (password != confirm) {
                            message = "Konfirmasi password belum sama."
                            return@Button
                        }
                        busy = true
                        message = null
                        scope.launch {
                            runCatching { api.completeOnboarding(session, password) }
                                .onSuccess { onComplete() }
                                .onFailure { message = it.message ?: "Aktivasi belum berhasil." }
                            busy = false
                        }
                    },
                    enabled = !busy && passwordValid && password == confirm,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(if (busy) "Mengaktifkan…" else "Ganti Password & Aktifkan", fontWeight = FontWeight.Black) }
                TextButton(onClick = onLogout, enabled = !busy) { Text("Keluar dari akun") }
            }
        }
    }
}

@Composable
private fun V63Loading(text: String) {
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.sales_logo), "GMU EduTrans", Modifier.width(190.dp).height(112.dp))
            CircularProgressIndicator(color = V63Green)
            Spacer(Modifier.height(10.dp))
            Text(text, color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun V63Error(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Login belum berhasil", fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(Modifier.height(6.dp))
                Text(message, color = Color.Gray, fontSize = 11.sp)
                Spacer(Modifier.height(14.dp))
                Button(onClick = onBack) { Text("Kembali ke Login") }
            }
        }
    }
}

private fun v63OpenUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
