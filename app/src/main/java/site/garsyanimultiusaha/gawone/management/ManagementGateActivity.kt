package site.garsyanimultiusaha.gawone.management

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
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

class ManagementGateActivity : ComponentActivity() {
    private val api = SupabaseManagementClient()
    private lateinit var store: SecureTokenStore
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var info: TextView
    private lateinit var login: Button
    private lateinit var resend: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureTokenStore(applicationContext)

        email = EditText(this).apply {
            hint = "Email Management"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        password = EditText(this).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        info = TextView(this)
        login = Button(this).apply { text = "Masuk Management" }
        resend = Button(this).apply { text = "Kirim Ulang Verifikasi Email" }
        progress = ProgressBar(this).apply { visibility = View.GONE }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 48, 48, 48)
            addView(TextView(this@ManagementGateActivity).apply {
                text = "GAWONE\nManagement"
                textSize = 30f
                gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(TextView(this@ManagementGateActivity).apply {
                text = "Email + Password • Backend M2.22"
                gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(email)
            addView(password)
            addView(info)
            addView(progress)
            addView(login)
            addView(resend)
        }
        setContentView(root)

        login.setOnClickListener { doLogin() }
        resend.setOnClickListener { doResend() }
    }

    private fun doLogin() {
        val e = email.text.toString().trim()
        val p = password.text.toString()
        if (!e.contains("@") || p.length < 6) {
            info.text = "Email atau password belum valid."
            return
        }
        busy(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.signIn(e, p)
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
                info.text = friendly(t.message.orEmpty())
            } finally {
                busy(false)
            }
        }
    }

    private fun doResend() {
        val e = email.text.toString().trim()
        if (!e.contains("@")) {
            info.text = "Isi email Management lebih dulu."
            return
        }
        busy(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { api.resendSignupConfirmation(e) }
                info.text = "Verifikasi dikirim ulang. Cek Inbox/Spam, konfirmasi, lalu login lagi."
            } catch (t: Throwable) {
                info.text = friendly(t.message.orEmpty())
            } finally {
                busy(false)
            }
        }
    }

    private fun friendly(raw: String): String = when {
        raw.contains("email_not_confirmed", true) || raw.contains("Email not confirmed", true) ->
            "Email belum dikonfirmasi. Tekan Kirim Ulang Verifikasi Email."
        raw.contains("invalid login credentials", true) -> "Email atau password salah."
        raw.contains("MANAGEMENT_ACCESS_DENIED", true) -> "Login berhasil, tetapi role Management belum aktif."
        raw.contains("AUTH_REQUIRED", true) -> "Sesi tidak valid. Silakan login kembali."
        raw.contains("CONTRACT_", true) || raw.contains("NAV_CONTRACT_", true) -> "Versi backend Management tidak cocok."
        raw.isBlank() -> "Login Management gagal."
        else -> raw.take(450)
    }

    private fun busy(value: Boolean) {
        progress.visibility = if (value) View.VISIBLE else View.GONE
        login.isEnabled = !value
        resend.isEnabled = !value
    }
}
