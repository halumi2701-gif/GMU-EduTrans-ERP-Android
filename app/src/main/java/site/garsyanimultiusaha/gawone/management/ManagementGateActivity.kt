package site.garsyanimultiusaha.gawone.management

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManagementGateActivity : ComponentActivity() {
    private val api = SupabaseManagementClient()
    private lateinit var store: SecureTokenStore
    private var busy by mutableStateOf(false)
    private var info by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureTokenStore(applicationContext)

        setContent {
            GawoneManagementTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = GawoneManagementTokens.Canvas) {
                    ManagementGateScreen(
                        busy = busy,
                        info = info,
                        onLogin = ::doLogin,
                        onResend = ::doResend
                    )
                }
            }
        }
    }

    private fun doLogin(email: String, password: String) {
        val e = email.trim()
        if (!e.contains("@") || password.length < 6) {
            info = "Email atau password belum valid."
            return
        }
        busy = true
        info = ""
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.signIn(e, password)
                    val runtime = api.runtime()
                    val contract = runtime.optString("backendContractVersion", runtime.optString("backend_contract_version", ""))
                    if (contract.isNotBlank() && contract != BuildConfig.BACKEND_CONTRACT) error("CONTRACT_$contract")
                    api.manifest()
                    api.bootstrap()
                    val nav = api.navigation()
                    val navContract = nav.optString("contractVersion", BuildConfig.BACKEND_CONTRACT)
                    if (navContract != BuildConfig.BACKEND_CONTRACT) error("NAV_CONTRACT_$navContract")
                    store.save(api.accessToken(), api.refreshToken())
                }
                startActivity(Intent(this@ManagementGateActivity, FullMainActivity::class.java))
                finish()
            } catch (t: Throwable) {
                info = friendly(t.message.orEmpty())
            } finally {
                busy = false
            }
        }
    }

    private fun doResend(email: String) {
        val e = email.trim()
        if (!e.contains("@")) {
            info = "Isi email Management lebih dulu."
            return
        }
        busy = true
        info = ""
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { api.resendSignupConfirmation(e) }
                info = "Verifikasi dikirim ulang. Cek Inbox/Spam, konfirmasi, lalu login lagi."
            } catch (t: Throwable) {
                info = friendly(t.message.orEmpty())
            } finally {
                busy = false
            }
        }
    }

    private fun friendly(raw: String): String = when {
        raw.contains("email_not_confirmed", true) || raw.contains("Email not confirmed", true) ->
            "Email belum dikonfirmasi. Kirim ulang verifikasi lalu cek Inbox/Spam."
        raw.contains("invalid login credentials", true) -> "Email atau password salah."
        raw.contains("MANAGEMENT_ACCESS_DENIED", true) -> "Login berhasil, tetapi role Management belum aktif."
        raw.contains("AUTH_REQUIRED", true) -> "Sesi tidak valid. Silakan login kembali."
        raw.contains("CONTRACT_", true) || raw.contains("NAV_CONTRACT_", true) -> "Versi backend Management tidak cocok."
        raw.isBlank() -> "Login Management gagal."
        else -> raw.take(450)
    }
}

@Composable
private fun ManagementGateScreen(
    busy: Boolean,
    info: String,
    onLogin: (String, String) -> Unit,
    onResend: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        GawoneManagementBrandHeader()
        Spacer(Modifier.height(34.dp))

        Text(
            "Command Center GAWONE",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Kelola Mitra, order, dispatch, support, payout, dan audit dari satu pusat operasional.",
            style = MaterialTheme.typography.bodyMedium,
            color = GawoneManagementTokens.Muted
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Management") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (info.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = GawoneManagementTokens.PrimarySoft,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(info, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onLogin(email, password) },
            enabled = !busy && email.contains("@") && password.length >= 6,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("Masuk Management", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { onResend(email) },
            enabled = !busy && email.contains("@"),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Kirim Ulang Verifikasi Email")
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            GawoneManagementStatusPill("Backend ${BuildConfig.BACKEND_CONTRACT} • Role-based")
        }
    }
}
