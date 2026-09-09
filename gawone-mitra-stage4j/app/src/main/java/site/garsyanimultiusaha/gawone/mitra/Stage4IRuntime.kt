package site.garsyanimultiusaha.gawone.mitra

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class Stage4IOperationalGates(
    val bookingEnabled: Boolean,
    val matchingEnabled: Boolean,
    val payoutEnabled: Boolean,
    val pushEnabled: Boolean
)

internal data class Stage4IRuntimeConfig(
    val maintenanceMode: Boolean,
    val maintenanceMessage: String,
    val minSupportedVersionCode: Int,
    val recommendedVersionCode: Int,
    val forceUpdate: Boolean,
    val backendContractVersion: String,
    val offlineDegradedModeEnabled: Boolean,
    val features: Map<String, Boolean>,
    val gates: Stage4IOperationalGates
) {
    fun enabled(feature: String): Boolean = features[feature] == true
}

internal data class Stage4IBootstrap(
    val runtime: Stage4IRuntimeConfig,
    val session: Session?,
    val authenticated: Boolean,
    val offlineDegraded: Boolean
)

internal class Stage4IRuntimeHttpException(
    val statusCode: Int,
    message: String
) : Exception(message)

internal class Stage4IRuntimeClient(context: Context) {
    private val appContext = context.applicationContext
    private val store = SecureSessionStore(appContext)
    private val runtimePrefs = appContext.getSharedPreferences(
        "gawone_stage4i_runtime_cache",
        Context.MODE_PRIVATE
    )
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun recover(): Stage4IBootstrap {
        val runtime = try {
            retryNetwork { fetchRuntime() }
        } catch (io: IOException) {
            cachedRuntime() ?: throw io
        }
        if (runtime.maintenanceMode || runtime.forceUpdate) {
            return Stage4IBootstrap(runtime, store.read(), store.read() != null, false)
        }

        var session = store.read()
            ?: return Stage4IBootstrap(runtime, null, false, false)

        val now = System.currentTimeMillis() / 1000L
        if (session.expiresAt - now <= 120L) {
            session = tryRefresh(session)
                ?: return Stage4IBootstrap(runtime, null, false, false)
        }

        return try {
            validateBootstrap(session)
            Stage4IBootstrap(runtime, session, true, false)
        } catch (e: Stage4IRuntimeHttpException) {
            if (e.statusCode == 401 || e.statusCode == 403) {
                val refreshed = tryRefresh(session)
                if (refreshed == null) {
                    Stage4IBootstrap(runtime, null, false, false)
                } else {
                    try {
                        validateBootstrap(refreshed)
                        Stage4IBootstrap(runtime, refreshed, true, false)
                    } catch (authAgain: Stage4IRuntimeHttpException) {
                        if (authAgain.statusCode == 401 || authAgain.statusCode == 403) {
                            store.clear()
                            Stage4IBootstrap(runtime, null, false, false)
                        } else {
                            throw authAgain
                        }
                    }
                }
            } else {
                throw e
            }
        } catch (io: IOException) {
            if (runtime.offlineDegradedModeEnabled) {
                Stage4IBootstrap(runtime, session, true, true)
            } else {
                throw io
            }
        }
    }

    suspend fun fetchRuntime(): Stage4IRuntimeConfig {
        val raw = request(
            path = "/rest/v1/rpc/get_mitra_runtime_config",
            body = JSONObject().put("p_version_code", BuildConfig.VERSION_CODE).toString(),
            token = null
        )
        runtimePrefs.edit()
            .putString("runtime_json", raw)
            .putLong("cached_at", System.currentTimeMillis())
            .apply()
        return parseRuntime(JSONObject(raw))
    }

    private fun cachedRuntime(): Stage4IRuntimeConfig? {
        val raw = runtimePrefs.getString("runtime_json", null) ?: return null
        return runCatching { parseRuntime(JSONObject(raw)) }.getOrNull()
    }

    private suspend fun validateBootstrap(session: Session) {
        request(
            path = "/rest/v1/rpc/get_my_mitra_bootstrap",
            body = JSONObject().put("p_version_code", BuildConfig.VERSION_CODE).toString(),
            token = session.accessToken
        )
    }

    private suspend fun tryRefresh(current: Session): Session? {
        return try {
            val raw = request(
                path = "/auth/v1/token?grant_type=refresh_token",
                body = JSONObject().put("refresh_token", current.refreshToken).toString(),
                token = null
            )
            val j = JSONObject(raw)
            val user = j.getJSONObject("user")
            val now = System.currentTimeMillis() / 1000L
            val refreshed = Session(
                accessToken = j.getString("access_token"),
                refreshToken = j.getString("refresh_token"),
                expiresAt = j.optLong("expires_at").takeIf { it > 0L }
                    ?: (now + j.optLong("expires_in", 3600L)),
                userId = user.getString("id"),
                phone = user.optString("phone", current.phone)
            )
            store.save(refreshed)
            refreshed
        } catch (e: Stage4IRuntimeHttpException) {
            if (e.statusCode == 400 || e.statusCode == 401 || e.statusCode == 403) {
                store.clear()
                null
            } else {
                throw e
            }
        }
    }

    private suspend fun request(
        path: String,
        body: String,
        token: String?
    ): String = withContext(Dispatchers.IO) {
        val c = try {
            URL(baseUrl + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw IOException("Tidak dapat membuka koneksi.", e)
        }

        try {
            c.requestMethod = "POST"
            c.connectTimeout = 12_000
            c.readTimeout = 18_000
            c.doInput = true
            c.doOutput = true
            c.useCaches = false
            c.setRequestProperty("apikey", key)
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) {
                c.setRequestProperty("Authorization", "Bearer " + token)
            }
            c.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }

            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    val j = JSONObject(raw)
                    j.optString("message")
                        .ifBlank { j.optString("msg") }
                        .ifBlank { j.optString("error") }
                }.getOrDefault("")
                throw Stage4IRuntimeHttpException(
                    code,
                    message.ifBlank { "Runtime request gagal (HTTP " + code + ")." }
                )
            }
            raw
        } catch (e: Stage4IRuntimeHttpException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Koneksi runtime bermasalah.", e)
        } finally {
            c.disconnect()
        }
    }

    private fun parseRuntime(j: JSONObject): Stage4IRuntimeConfig {
        val featuresJson = j.optJSONObject("features") ?: JSONObject()
        val featureMap = buildMap {
            val keys = featuresJson.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                put(k, featuresJson.optBoolean(k))
            }
        }
        val gates = j.optJSONObject("operationalGates") ?: JSONObject()

        return Stage4IRuntimeConfig(
            maintenanceMode = j.optBoolean("maintenanceMode"),
            maintenanceMessage = j.optString(
                "maintenanceMessage",
                "GAWONE Mitra sedang dalam pemeliharaan."
            ),
            minSupportedVersionCode = j.optInt("minSupportedVersionCode", 1),
            recommendedVersionCode = j.optInt("recommendedVersionCode", 1),
            forceUpdate = j.optBoolean("forceUpdate"),
            backendContractVersion = j.optString("backendContractVersion", "unknown"),
            offlineDegradedModeEnabled = j.optBoolean("offlineDegradedModeEnabled", true),
            features = featureMap,
            gates = Stage4IOperationalGates(
                bookingEnabled = gates.optBoolean("bookingEnabled"),
                matchingEnabled = gates.optBoolean("matchingEnabled"),
                payoutEnabled = gates.optBoolean("payoutEnabled"),
                pushEnabled = gates.optBoolean("pushEnabled")
            )
        )
    }

    private suspend fun <T> retryNetwork(block: suspend () -> T): T {
        var last: Throwable? = null
        val delays = longArrayOf(0L, 500L, 1_500L)
        for (wait in delays) {
            if (wait > 0L) delay(wait)
            try {
                return block()
            } catch (t: Throwable) {
                last = t
                val retryable = t is IOException ||
                    (t is Stage4IRuntimeHttpException && t.statusCode >= 500)
                if (!retryable) throw t
            }
        }
        throw last ?: IOException("Runtime tidak tersedia.")
    }
}
