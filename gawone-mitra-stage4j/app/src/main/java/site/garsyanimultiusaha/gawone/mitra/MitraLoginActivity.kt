package site.garsyanimultiusaha.gawone.mitra

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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MitraLoginActivity : ComponentActivity() {
    private lateinit var store: SecureSessionStore
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureSessionStore(applicationContext)
        if (store.read() != null) {
            openMain()
            return
        }

        setContent {
            GawoneMitraTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = GawoneMitraTokens.Canvas) {
                    MitraLoginScreen(
                        busy = busy,
                        message = message,
                        onSubmit = ::authenticate
                    )
                }
            }
        }
    }

    private fun authenticate(email: String, password: String, signUp: Boolean) {
        val e = email.trim()
        if (!e.contains("@") || password.length < 6) {
            message = "Email atau password belum valid."
            return
        }

        busy = true
        message = ""
        lifecycleScope.launch {
            try {
                val auth = authRequest(e, password, signUp)
                val access = auth.optString("access_token")
                val refresh = auth.optString("refresh_token")
                if (access.isBlank() || refresh.isBlank()) {
                    message = "Pendaftaran berhasil. Konfirmasi email bila diminta, lalu tekan Masuk."
                    return@launch
                }

                val user = auth.optJSONObject("user")
                val userId = user?.optString("id").orEmpty()
                if (userId.isBlank()) error("Sesi pengguna tidak lengkap.")

                ensurePartner(access)

                val now = System.currentTimeMillis() / 1000L
                store.save(
                    Session(
                        accessToken = access,
                        refreshToken = refresh,
                        expiresAt = auth.optLong("expires_at").takeIf { it > 0L }
                            ?: (now + auth.optLong("expires_in", 3600L)),
                        userId = userId,
                        phone = user?.optString("phone").orEmpty()
                    )
                )
                openMain()
            } catch (t: Throwable) {
                message = friendly(t.message.orEmpty())
            } finally {
                busy = false
            }
        }
    }

    private suspend fun authRequest(e: String, p: String, signUp: Boolean): JSONObject =
        post(
            if (signUp) "/auth/v1/signup" else "/auth/v1/token?grant_type=password",
            JSONObject().put("email", e).put("password", p),
            null
        )

    private suspend fun ensurePartner(token: String) {
        post(
            "/rest/v1/rpc/register_as_partner",
            JSONObject().put("p_partner_type", "INDIVIDUAL_PARTNER"),
            token
        )
    }

    private suspend fun post(path: String, body: JSONObject, token: String?): JSONObject =
        withContext(Dispatchers.IO) {
            val c = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 25_000
                doInput = true
                doOutput = true
                useCaches = false
                setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                c.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val j = runCatching { JSONObject(raw) }.getOrNull()
                    val m = j?.optString("message")
                        ?.ifBlank { j.optString("msg") }
                        ?.ifBlank { j.optString("error_description") }
                        .orEmpty()
                    error(m.ifBlank { "Permintaan gagal (HTTP $code)." })
                }
                if (raw.isBlank()) JSONObject() else runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
            } finally {
                c.disconnect()
            }
        }

    private fun friendly(raw: String): String = when {
        raw.contains("email_not_confirmed", true) || raw.contains("email not confirmed", true) ->
            "Email belum dikonfirmasi. Buka email verifikasi lalu masuk kembali."
        raw.contains("invalid login credentials", true) -> "Email atau password salah."
        raw.contains("unsupported phone provider", true) -> "Phone OTP belum aktif. Gunakan Email + Password."
        raw.isBlank() -> "Login Mitra gagal."
        else -> raw.take(420)
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@Composable
private fun MitraLoginScreen(
    busy: Boolean,
    message: String,
    onSubmit: (String, String, Boolean) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        GawoneMitraBrandHeader("UNTUK PELUANG KERJA & PENGHASILAN")
        Spacer(Modifier.height(34.dp))

        Text(
            "Masuk sebagai Mitra",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Kelola order, jadwal, pekerjaan, pendapatan, dan akun dalam satu aplikasi.",
            style = MaterialTheme.typography.bodyMedium,
            color = GawoneMitraTokens.Muted
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
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

        if (message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = GawoneMitraTokens.PrimarySoft,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = GawoneMitraTokens.Ink
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onSubmit(email, password, false) },
            enabled = !busy && email.contains("@") && password.length >= 6,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Masuk", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { onSubmit(email, password, true) },
            enabled = !busy && email.contains("@") && password.length >= 6,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Daftar Mitra", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            GawoneMitraStatusPill("Email + Password • Phone OTP opsional nanti")
        }
    }
}
