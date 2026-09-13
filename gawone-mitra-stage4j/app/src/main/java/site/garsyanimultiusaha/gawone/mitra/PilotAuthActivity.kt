package site.garsyanimultiusaha.gawone.mitra

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

class PilotAuthActivity : ComponentActivity() {
    private lateinit var store: SecureSessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureSessionStore(applicationContext)
        if (store.read() != null) {
            openMain()
            return
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PilotAuthScreen(
                        onSignIn = { email, password, done ->
                            authenticate(email, password, signUp = false, done = done)
                        },
                        onSignUp = { email, password, done ->
                            authenticate(email, password, signUp = true, done = done)
                        },
                        onPhoneOtp = { openMain() }
                    )
                }
            }
        }
    }

    private fun authenticate(
        email: String,
        password: String,
        signUp: Boolean,
        done: (String?) -> Unit
    ) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val path = if (signUp) {
                        "/auth/v1/signup"
                    } else {
                        "/auth/v1/token?grant_type=password"
                    }
                    val body = JSONObject()
                        .put("email", email.trim())
                        .put("password", password)
                        .toString()
                    val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
                        .openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 20_000
                        readTimeout = 25_000
                        doInput = true
                        doOutput = true
                        useCaches = false
                        setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Content-Type", "application/json")
                    }
                    try {
                        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                        val code = connection.responseCode
                        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                        val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        if (code !in 200..299) {
                            val json = runCatching { JSONObject(raw) }.getOrNull()
                            val message = json?.optString("msg")
                                ?.ifBlank { json.optString("message") }
                                ?.ifBlank { json.optString("error_description") }
                                ?.ifBlank { json.optString("error") }
                                .orEmpty()
                            error(message.ifBlank { "Autentikasi gagal (HTTP $code)." })
                        }
                        JSONObject(raw)
                    } finally {
                        connection.disconnect()
                    }
                }
            }.onSuccess { json ->
                val access = json.optString("access_token")
                val refresh = json.optString("refresh_token")
                if (access.isBlank() || refresh.isBlank()) {
                    done("Pendaftaran berhasil. Cek email konfirmasi, lalu kembali dan tekan Masuk.")
                    return@onSuccess
                }
                val user = json.optJSONObject("user")
                val userId = user?.optString("id").orEmpty()
                if (userId.isBlank()) {
                    done("Sesi tidak lengkap. Silakan coba Masuk kembali.")
                    return@onSuccess
                }
                val now = System.currentTimeMillis() / 1000L
                store.save(
                    Session(
                        accessToken = access,
                        refreshToken = refresh,
                        expiresAt = json.optLong("expires_at").takeIf { it > 0L }
                            ?: (now + json.optLong("expires_in", 3600L)),
                        userId = userId,
                        phone = user?.optString("phone").orEmpty()
                    )
                )
                done(null)
                openMain()
            }.onFailure { throwable ->
                done(throwable.message ?: "Autentikasi gagal.")
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@Composable
private fun PilotAuthScreen(
    onSignIn: (String, String, (String?) -> Unit) -> Unit,
    onSignUp: (String, String, (String?) -> Unit) -> Unit,
    onPhoneOtp: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun valid(): Boolean = email.contains("@") && password.length >= 6
    fun submit(signUp: Boolean) {
        if (!valid() || loading) return
        loading = true
        message = null
        val callback: (String?) -> Unit = { result ->
            message = result
            loading = false
        }
        if (signUp) onSignUp(email, password, callback) else onSignIn(email, password, callback)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("GAWONE", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Text("Mitra", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Pilot login: Email + Password. Phone OTP tetap tersedia setelah provider SMS diaktifkan.")
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (min. 6 karakter)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(message!!, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { submit(signUp = false) },
            enabled = valid() && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Masuk")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { submit(signUp = true) },
            enabled = valid() && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Daftar Mitra")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onPhoneOtp, modifier = Modifier.fillMaxWidth()) {
            Text("Gunakan Phone OTP")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Jika Phone OTP menampilkan unsupported phone provider, gunakan Email + Password untuk pilot sampai SMS provider production diaktifkan.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
