package site.garsyanimultiusaha.gawone.mitra

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class PresenceStart(
    val sessionId: String,
    val heartbeatIntervalSeconds: Int,
    val staleAfterSeconds: Int,
    val maxAccuracyM: Double,
)

internal data class PresenceSnapshot(
    val sessionId: String,
    val status: String,
    val trackingMode: String,
    val heartbeatIntervalSeconds: Int,
    val staleAfterSeconds: Int,
    val fresh: Boolean,
)

internal class Stage4CPresenceClient(context: Context) {
    private val appContext = context.applicationContext
    private val store = SecureSessionStore(appContext)
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val refreshMutex = Mutex()

    suspend fun start(
        serviceCode: String,
        deviceSessionId: String,
        latitude: Double,
        longitude: Double,
        accuracyM: Float,
    ): PresenceStart {
        val raw = rpc(
            "start_partner_presence",
            JSONObject()
                .put("p_service_codes", listOf(serviceCode))
                .put("p_device_session_id", deviceSessionId)
                .put("p_latitude", latitude)
                .put("p_longitude", longitude)
                .put("p_accuracy_m", accuracyM.toDouble())
                .put("p_recorded_at", JSONObject.NULL)
                .put("p_app_version", BuildConfig.VERSION_NAME)
        )
        val j = JSONObject(raw)
        return PresenceStart(
            sessionId = j.getString("presenceSessionId"),
            heartbeatIntervalSeconds = j.optInt("heartbeatIntervalSeconds", 60),
            staleAfterSeconds = j.optInt("staleAfterSeconds", 240),
            maxAccuracyM = j.optDouble("maxAccuracyM", 150.0),
        )
    }

    suspend fun heartbeat(
        sessionId: String,
        latitude: Double?,
        longitude: Double?,
        accuracyM: Float?,
        heading: Float?,
        speedMps: Float?,
        recordedAtIso: String? = null,
    ) {
        rpc(
            "heartbeat_partner_presence",
            JSONObject()
                .put("p_presence_session_id", sessionId)
                .put("p_latitude", latitude ?: JSONObject.NULL)
                .put("p_longitude", longitude ?: JSONObject.NULL)
                .put("p_accuracy_m", accuracyM?.toDouble() ?: JSONObject.NULL)
                .put("p_heading", heading?.toDouble() ?: JSONObject.NULL)
                .put("p_speed_mps", speedMps?.toDouble() ?: JSONObject.NULL)
                .put("p_recorded_at", recordedAtIso ?: JSONObject.NULL)
        )
    }

    suspend fun stop(sessionId: String, reason: String = "USER_OFFLINE") {
        rpc(
            "stop_partner_presence",
            JSONObject()
                .put("p_presence_session_id", sessionId)
                .put("p_reason", reason)
        )
    }

    suspend fun snapshot(): PresenceSnapshot? {
        val raw = rpc("get_my_partner_presence", JSONObject())
        if (raw.isBlank() || raw.trim() == "null") return null
        val j = JSONObject(raw)
        val services = j.optJSONArray("services")
        val first = if (services != null && services.length() > 0) services.getJSONObject(0) else null
        return PresenceSnapshot(
            sessionId = j.optString("presenceSessionId"),
            status = j.optString("status"),
            trackingMode = first?.optString("trackingMode").orEmpty(),
            heartbeatIntervalSeconds = first?.optInt("heartbeatIntervalSeconds", 60) ?: 60,
            staleAfterSeconds = first?.optInt("staleAfterSeconds", 240) ?: 240,
            fresh = first?.optBoolean("fresh", false) ?: false,
        )
    }

    private suspend fun rpc(name: String, body: JSONObject): String {
        val token = validSession().accessToken
        return request(
            path = "/rest/v1/rpc/$name",
            method = "POST",
            body = body.toString(),
            token = token,
        )
    }

    private suspend fun validSession(): Session = refreshMutex.withLock {
        val current = store.read() ?: throw IllegalStateException("Sesi Mitra tidak tersedia.")
        val now = System.currentTimeMillis() / 1000L
        if (current.expiresAt - now > 90L) return@withLock current

        val raw = request(
            path = "/auth/v1/token?grant_type=refresh_token",
            method = "POST",
            body = JSONObject().put("refresh_token", current.refreshToken).toString(),
            token = null,
        )
        val j = JSONObject(raw)
        val user = j.getJSONObject("user")
        val refreshed = Session(
            accessToken = j.getString("access_token"),
            refreshToken = j.getString("refresh_token"),
            expiresAt = j.optLong("expires_at").takeIf { it > 0L }
                ?: (now + j.optLong("expires_in", 3600L)),
            userId = user.getString("id"),
            phone = user.optString("phone", current.phone),
        )
        store.save(refreshed)
        refreshed
    }

    private suspend fun request(
        path: String,
        method: String,
        body: String?,
        token: String?,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 25_000
            doInput = true
            useCaches = false
            setRequestProperty("apikey", key)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }

        try {
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    val j = JSONObject(raw)
                    j.optString("message")
                        .ifBlank { j.optString("msg") }
                        .ifBlank { j.optString("error") }
                }.getOrDefault("").ifBlank { "Presence request gagal (HTTP $code)." }
                throw IllegalStateException(message)
            }
            raw
        } finally {
            connection.disconnect()
        }
    }
}
