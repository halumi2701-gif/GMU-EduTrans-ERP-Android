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

class ManagementLoginActivity : ComponentActivity() {
    private val api = SupabaseManagementClient()
    private lateinit var store: SecureTokenStore
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var message: TextView
    private lateinit var progress: ProgressBar
    private lateinit var signIn: Button
    private lateinit var signUp: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureTokenStore(applicationContext)
        if (!store.accessToken().isNullOrBlank()) {
            openManagement()
            return
        }

        email = EditText(this).apply {
            hint = "Email Management"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        password = EditText(this).apply {
            hint = "Password (minimal 6 karakter)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        message = TextView(this)
        progress = ProgressBar(this).apply { visibility = View.GONE }
        signIn = Button(this).apply { text = "Masuk Management" }
        signUp = Button(this).apply { text = "Daftar Management" }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 48, 48, 48)
            addView(TextView(this@ManagementLoginActivity).apply {
                text = "GAWONE\nManagement"
                textSize = 30f
                gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(TextView(this@ManagementLoginActivity).apply {
                text = "Email + Password. Akun baru tidak mendapat akses operasional sampai role internal diaktifkan."
            })
            addView(email)
            addView(password)
            addView(message)
            addView(progress)
            addView(signIn)
            addView(signUp)
        }
        setContentView(root)

        signIn.setOnClickListener { authenticate(false) }
        signUp.setOnClickListener { authenticate(true) }
    }

    private fun authenticate(signUpMode: Boolean) {
        val e = email.text.toString().trim()
        val p = password.text.toString()
        if (!e.contains("@") || p.length < 6) {
            message.text = "Email atau password belum valid."
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            try {
                if (signUpMode) {
                    withContext(Dispatchers.IO) { api.signUp(e, p) }
                    message.text = "Akun berhasil dibuat. Setelah role OWNER diaktifkan, tekan Masuk Management."
                } else {
                    withContext(Dispatchers.IO) {
                        api.signIn(e, p)
                        store.save(api.accessToken(), api.refreshToken())
                    }
                    openManagement()
                }
            } catch (t: Throwable) {
                message.text = t.message ?: "Autentikasi gagal."
            } finally {
                setBusy(false)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        signIn.isEnabled = !busy
        signUp.isEnabled = !busy
    }

    private fun openManagement() {
        startActivity(Intent(this, FullMainActivity::class.java))
        finish()
    }
}
