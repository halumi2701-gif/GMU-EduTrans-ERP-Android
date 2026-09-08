package site.garsyanimultiusaha.gawone.mitra

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal class SecureSessionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("gawone_mitra_stage4b_secure_session", Context.MODE_PRIVATE)

    fun read(): Session? {
        val encrypted = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val j = JSONObject(decrypt(encrypted))
            Session(
                accessToken = j.getString("access"),
                refreshToken = j.getString("refresh"),
                expiresAt = j.getLong("expiresAt"),
                userId = j.getString("userId"),
                phone = j.optString("phone")
            )
        }.getOrElse {
            clear()
            null
        }
    }

    fun save(session: Session) {
        val raw = JSONObject()
            .put("access", session.accessToken)
            .put("refresh", session.refreshToken)
            .put("expiresAt", session.expiresAt)
            .put("userId", session.userId)
            .put("phone", session.phone)
            .toString()
        prefs.edit().putString(KEY, encrypt(raw)).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        return String(
            cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
            StandardCharsets.UTF_8
        )
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY = "session"
        const val KEY_ALIAS = "gawone_mitra_stage4b_session_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
