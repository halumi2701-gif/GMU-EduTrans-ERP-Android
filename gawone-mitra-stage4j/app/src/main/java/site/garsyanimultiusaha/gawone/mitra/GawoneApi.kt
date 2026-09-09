package site.garsyanimultiusaha.gawone.mitra

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthSession(val accessToken: String, val refreshToken: String)

class GawoneApi(
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY
) {
    private fun post(path: String, body: JSONObject, bearer: String? = null): JSONObject {
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", publishableKey)
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(raw).optString("msg").ifBlank { JSONObject(raw).optString("message") } }.getOrNull()
            throw IllegalStateException(msg?.ifBlank { "HTTP $code" } ?: "HTTP $code")
        }
        return if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    fun sendPhoneOtp(phone: String) {
        post("/auth/v1/otp", JSONObject().put("phone", phone))
    }

    fun verifyPhoneOtp(phone: String, otp: String): AuthSession {
        val json = post(
            "/auth/v1/verify",
            JSONObject().put("type", "sms").put("phone", phone).put("token", otp)
        )
        return json.toSession()
    }

    fun refresh(refreshToken: String): AuthSession {
        val json = post(
            "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", refreshToken)
        )
        return json.toSession()
    }

    fun rpc(name: String, payload: JSONObject, accessToken: String): JSONObject =
        post("/rest/v1/rpc/$name", payload, accessToken)

    private fun JSONObject.toSession(): AuthSession {
        val access = optString("access_token")
        val refresh = optString("refresh_token")
        require(access.isNotBlank() && refresh.isNotBlank()) { "Session tidak lengkap" }
        return AuthSession(access, refresh)
    }
}
