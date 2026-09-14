package site.garsyanimultiusaha.gawone.mitra

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MitraLoginActivity : ComponentActivity() {
    private lateinit var store: SecureSessionStore
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var message: TextView
    private lateinit var progress: ProgressBar
    private lateinit var signIn: Button
    private lateinit var signUp: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureSessionStore(applicationContext)
        if (store.read() != null) {
            openMain()
            return
        }

        email = EditText(this).apply {
            hint = "Email"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        password = EditText(this).apply {
            hint = "Password (min. 6 karakter)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        message = TextView(this)
        progress = ProgressBar(this).apply { visibility = ProgressBar.GONE }
        signIn = Button(this).apply { text = "Masuk" }
        signUp = Button(this).apply { text = "Daftar Mitra" }

        val title = TextView(this).apply {
            text = "GAWONE\nMitra"
            textSize = 30f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val note = TextView(this).apply {
            text = "Gunakan Email + Password. Setelah berhasil, akun langsung disiapkan sebagai Mitra dan masuk onboarding."
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 48, 48, 48)
            addView(title)
            addView(note)
            addView(email)
            addView(password)
            addView(message)
            addView(progress)
            addView(signIn)
            addView(signUp)
        }
        setContentView(root)

        signIn.setOnClickListener { authenticate(signUp = false) }
        signUp.setOnClickListener { authenticate(signUp = true) }
    }

    private fun authenticate(signUp: Boolean) {
        val e = email.text.toString().trim()
        val p = password.text.toString()
        if (!e.contains("@") || p.length < 6) {
            message.text = "Email atau password belum valid."
            return
        }

        setBusy(true)
        lifecycleScope.launch {
            try {
                val auth = authRequest(e, p, signUp)
                val access = auth.optString("access_token")
                val refresh = auth.optString("refresh_token")
                if (access.isBlank() || refresh.isBlank()) {
                    message.text = "Pendaftaran berhasil. Konfirmasi email bila diminta, lalu tekan Masuk."
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
                message.text = t.message ?: "Login gagal."
            } finally {
                setBusy(false)
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

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) ProgressBar.VISIBLE else ProgressBar.GONE
        signIn.isEnabled = !busy
        signUp.isEnabled = !busy
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
