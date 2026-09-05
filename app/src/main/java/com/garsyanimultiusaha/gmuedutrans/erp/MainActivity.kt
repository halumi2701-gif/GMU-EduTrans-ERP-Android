package com.garsyanimultiusaha.gmuedutrans.erp

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private val GmuGreen = Color(0xFF0A6A3F)
private val GmuDark = Color(0xFF06482F)
private val GmuGold = Color(0xFFD5A300)
private val GmuBackground = Color(0xFFF3F7F5)

data class StaffProfile(
    val id: String,
    val fullName: String,
    val role: String,
    val active: Boolean
)

data class SessionState(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val profile: StaffProfile
)

sealed interface AppState {
    data object Loading : AppState
    data object LoggedOut : AppState
    data class LoggedIn(val session: SessionState) : AppState
    data class Error(val message: String) : AppState
}

class SupabaseApi {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun signIn(email: String, password: String): SessionState = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()
        val auth = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            body = payload,
            bearer = null
        )
        val authJson = JSONObject(auth)
        val accessToken = authJson.getString("access_token")
        val refreshToken = authJson.optString("refresh_token", "")
        val userId = authJson.getJSONObject("user").getString("id")
        val profile = fetchProfile(accessToken, userId)
        if (!profile.active) throw IllegalStateException("Akun staf tidak aktif. Hubungi Owner.")
        SessionState(accessToken, refreshToken, userId, profile)
    }

    suspend fun fetchProfile(accessToken: String, userId: String): StaffProfile = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(userId, "UTF-8")
        val body = request(
            method = "GET",
            path = "/rest/v1/profiles?select=id,full_name,role,is_active&id=eq.$encoded&limit=1",
            body = null,
            bearer = accessToken
        )
        val arr = JSONArray(body)
        if (arr.length() == 0) throw IllegalStateException("Profil ERP tidak ditemukan.")
        val p = arr.getJSONObject(0)
        StaffProfile(
            id = p.getString("id"),
            fullName = p.optString("full_name", "Staff GMU"),
            role = p.optString("role", "Sales"),
            active = p.optBoolean("is_active", false)
        )
    }

    suspend fun signOut(accessToken: String) = withContext(Dispatchers.IO) {
        runCatching { request("POST", "/auth/v1/logout", "{}", accessToken) }
        Unit
    }

    private fun request(method: String, path: String, body: String?, bearer: String?): String {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", key)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }.orEmpty()
        conn.disconnect()
        if (status !in 200..299) {
            val msg = runCatching {
                val j = JSONObject(text)
                j.optString("msg", j.optString("message", j.optString("error_description", "Login/API gagal ($status)")))
            }.getOrDefault("Login/API gagal ($status)")
            throw IllegalStateException(msg)
        }
        return text
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseApi()
    private val prefs = application.getSharedPreferences("gmu_native_session", Context.MODE_PRIVATE)
    var state by mutableStateOf<AppState>(AppState.Loading)
        private set
    var busy by mutableStateOf(false)
        private set

    init { restoreSession() }

    private fun restoreSession() {
        viewModelScope.launch {
            val access = prefs.getString("access", null)
            val refresh = prefs.getString("refresh", "") ?: ""
            val uid = prefs.getString("uid", null)
            if (access.isNullOrBlank() || uid.isNullOrBlank()) {
                state = AppState.LoggedOut
                return@launch
            }
            state = try {
                val profile = api.fetchProfile(access, uid)
                if (!profile.active) AppState.LoggedOut else AppState.LoggedIn(SessionState(access, refresh, uid, profile))
            } catch (_: Exception) {
                clearSession()
                AppState.LoggedOut
            }
        }
    }

    fun login(email: String, password: String) {
        if (busy) return
        busy = true
        state = AppState.Loading
        viewModelScope.launch {
            state = try {
                val session = api.signIn(email, password)
                prefs.edit()
                    .putString("access", session.accessToken)
                    .putString("refresh", session.refreshToken)
                    .putString("uid", session.userId)
                    .apply()
                AppState.LoggedIn(session)
            } catch (e: Exception) {
                AppState.Error(e.message ?: "Login gagal")
            }
            busy = false
        }
    }

    fun backToLogin() { state = AppState.LoggedOut }

    fun logout() {
        val session = (state as? AppState.LoggedIn)?.session
        clearSession()
        state = AppState.LoggedOut
        if (session != null) viewModelScope.launch { api.signOut(session.accessToken) }
    }

    private fun clearSession() {
        prefs.edit().clear().apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GmuNativeApp() }
    }
}

@Composable
fun GmuNativeApp(vm: MainViewModel = viewModel()) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = GmuGreen,
            secondary = GmuGold,
            background = GmuBackground,
            surface = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = GmuBackground) {
            when (val s = vm.state) {
                AppState.Loading -> LoadingScreen()
                AppState.LoggedOut -> LoginScreen(onLogin = vm::login, busy = vm.busy)
                is AppState.Error -> LoginScreen(onLogin = vm::login, busy = vm.busy, error = s.message, onClearError = vm::backToLogin)
                is AppState.LoggedIn -> DashboardScreen(s.session.profile, vm::logout)
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = GmuGreen)
            Spacer(Modifier.height(12.dp))
            Text("GMU EduTrans ERP", fontWeight = FontWeight.Bold)
            Text("Memuat sesi native…", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun LoginScreen(
    onLogin: (String, String) -> Unit,
    busy: Boolean,
    error: String? = null,
    onClearError: (() -> Unit)? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("GMU", color = GmuGold, fontWeight = FontWeight.Black, fontSize = 42.sp)
        Text("EduTrans ERP", color = GmuDark, fontWeight = FontWeight.Black, fontSize = 30.sp)
        Text("Android Native v0.1", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(28.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(20.dp)) {
                Text("Masuk", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("Login langsung ke Supabase GMU EduTrans. Tidak menggunakan WebView.", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onClearError?.invoke(); onLogin(email, password) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && email.isNotBlank() && password.length >= 8,
                    colors = ButtonDefaults.buttonColors(containerColor = GmuGreen)
                ) { Text(if (busy) "Memproses…" else "Masuk ke ERP") }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Brand of PT Garsyani Multi Usaha • More Than a Trip, It’s a Learning Journey.", fontSize = 11.sp, color = Color.Gray)
    }
}

data class NativeMenu(val title: String, val subtitle: String, val ownerOnly: Boolean = false)

private fun menusFor(role: String): List<NativeMenu> = when (role) {
    "Owner" -> listOf(
        NativeMenu("Dashboard", "Ringkasan ERP native"),
        NativeMenu("Booking", "Lead sampai Closed"),
        NativeMenu("Customer", "Database customer"),
        NativeMenu("Finance", "Pembayaran, piutang & biaya"),
        NativeMenu("Trip Operation", "Persiapan dan pelaksanaan trip"),
        NativeMenu("Vendor", "Master vendor"),
        NativeMenu("Workflow", "Approval & deadline SOP"),
        NativeMenu("User & Role", "Kelola akun staf", ownerOnly = true)
    )
    "Manager" -> listOf(
        NativeMenu("Dashboard", "Ringkasan ERP native"),
        NativeMenu("Booking", "Lead sampai Closed"),
        NativeMenu("Customer", "Database customer"),
        NativeMenu("Trip Operation", "Persiapan dan pelaksanaan trip"),
        NativeMenu("Vendor", "Master vendor"),
        NativeMenu("Workflow", "Approval & deadline SOP")
    )
    "Finance" -> listOf(
        NativeMenu("Dashboard", "Ringkasan ERP native"),
        NativeMenu("Finance", "Pembayaran, piutang & biaya")
    )
    "Operation", "TL" -> listOf(
        NativeMenu("Dashboard", "Ringkasan ERP native"),
        NativeMenu("Trip Operation", "Persiapan dan pelaksanaan trip"),
        NativeMenu("Workflow", "Deadline operasional")
    )
    "Sales", "Admin" -> listOf(
        NativeMenu("Dashboard", "Ringkasan ERP native"),
        NativeMenu("Booking", "Lead sampai Closed"),
        NativeMenu("Customer", "Database customer")
    )
    else -> listOf(NativeMenu("Dashboard", "Ringkasan ERP native"))
}

@Composable
private fun DashboardScreen(profile: StaffProfile, onLogout: () -> Unit) {
    val menus = remember(profile.role) { menusFor(profile.role) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(GmuDark).padding(horizontal = 20.dp, vertical = 18.dp)) {
            Column {
                Text("GMU EduTrans ERP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Native Android • ${profile.role}", color = Color(0xFFDDEBE4), fontSize = 12.sp)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("Halo, ${profile.fullName}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = GmuDark)
            Text("Role: ${profile.role} • Akun aktif", fontSize = 12.sp, color = GmuGreen)
            Spacer(Modifier.height(16.dp))
            if (profile.role != "Owner") {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6EF))) {
                    Text(
                        "User & Role tidak ditampilkan untuk ${profile.role}. Fungsi Owner-only tetap dilindungi backend RLS.",
                        Modifier.padding(14.dp), fontSize = 12.sp, color = GmuDark
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            menus.forEach { item ->
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(item.title, fontWeight = FontWeight.Bold, color = GmuDark)
                        Text(item.subtitle, fontSize = 12.sp, color = Color.Gray)
                        Text("v0.1: modul UI berikutnya", fontSize = 10.sp, color = GmuGold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Keluar") }
            Spacer(Modifier.height(20.dp))
        }
    }
}
