package site.garsyanimultiusaha.gawone.mitra

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class WalletBalances(val pending: Long, val available: Long, val held: Long, val paidTotal: Long)
internal data class PayoutPolicy(val enabled: Boolean, val minimumAmount: Long, val maximumAmount: Long?, val dailyLimitAmount: Long?, val maxRequestsPerDay: Int)
internal data class WalletEarning(
    val earningId: String, val orderNo: String, val serviceName: String, val status: String,
    val grossShare: Long, val commissionAmount: Long, val baseNetAmount: Long,
    val tipAmount: Long, val totalNetAmount: Long
)
internal data class WalletDestination(
    val destinationId: String, val provider: String, val displayLabel: String,
    val holderName: String?, val status: String
)
internal data class WalletPayout(
    val payoutId: String, val amount: Long, val status: String,
    val externalReference: String?, val rejectionReason: String?, val failureReason: String?
)
internal data class WalletSnapshot(
    val walletId: String, val currency: String, val isFrozen: Boolean,
    val balances: WalletBalances, val policy: PayoutPolicy,
    val earnings: List<WalletEarning>, val destinations: List<WalletDestination>,
    val payouts: List<WalletPayout>
)
internal data class PayoutRequestResult(val payoutId: String, val status: String, val amount: Long, val idempotent: Boolean)

internal class Stage4FWalletClient(context: Context) {
    private val store = SecureSessionStore(context.applicationContext)
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val refreshMutex = Mutex()

    suspend fun wallet(): WalletSnapshot {
        val j = JSONObject(rpc("get_my_partner_wallet", JSONObject()))
        val b = j.optJSONObject("balances") ?: JSONObject()
        val p = j.optJSONObject("payoutPolicy") ?: JSONObject()

        return WalletSnapshot(
            walletId = j.optString("walletId"),
            currency = j.optString("currency", "IDR"),
            isFrozen = j.optBoolean("isFrozen"),
            balances = WalletBalances(
                b.money("pending"), b.money("available"), b.money("held"), b.money("paidTotal")
            ),
            policy = PayoutPolicy(
                enabled = p.optBoolean("enabled"),
                minimumAmount = p.money("minimumAmount"),
                maximumAmount = p.nullableMoney("maximumAmount"),
                dailyLimitAmount = p.nullableMoney("dailyLimitAmount"),
                maxRequestsPerDay = p.optInt("maxRequestsPerDay", 0)
            ),
            earnings = j.optJSONArray("earnings").earnings(),
            destinations = j.optJSONArray("destinations").destinations(),
            payouts = j.optJSONArray("payoutRequests").payouts()
        )
    }

    suspend fun requestPayout(destinationId: String, amount: Long): PayoutRequestResult {
        val j = JSONObject(
            rpc(
                "request_partner_payout",
                JSONObject()
                    .put("p_destination_id", destinationId)
                    .put("p_amount", amount)
                    .put("p_idempotency_key", "mitra-" + UUID.randomUUID().toString())
            )
        )
        return PayoutRequestResult(
            payoutId = j.optString("payoutId"),
            status = j.optString("status"),
            amount = j.money("amount"),
            idempotent = j.optBoolean("idempotent")
        )
    }

    private suspend fun rpc(name: String, body: JSONObject): String {
        val session = validSession()
        return request("/rest/v1/rpc/" + name, "POST", body.toString(), session.accessToken)
    }

    private suspend fun validSession(): Session = refreshMutex.withLock {
        val current = store.read()
            ?: throw IllegalStateException("Sesi Mitra tidak tersedia. Silakan login ulang.")
        val now = System.currentTimeMillis() / 1000L
        if (current.expiresAt - now > 90L) return@withLock current

        val raw = request(
            "/auth/v1/token?grant_type=refresh_token",
            "POST",
            JSONObject().put("refresh_token", current.refreshToken).toString(),
            null
        )
        val j = JSONObject(raw)
        val user = j.getJSONObject("user")
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
    }

    private suspend fun request(path: String, method: String, body: String?, token: String?): String =
        withContext(Dispatchers.IO) {
            val c = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 25_000
                doInput = true
                useCaches = false
                setRequestProperty("apikey", key)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer " + token)
            }
            try {
                if (body != null) {
                    c.doOutput = true
                    c.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IllegalStateException(mapError(raw, code))
                raw
            } finally {
                c.disconnect()
            }
        }

    private fun mapError(raw: String, code: Int): String {
        val server = runCatching {
            val j = JSONObject(raw)
            j.optString("message").ifBlank { j.optString("msg") }.ifBlank { j.optString("error") }
        }.getOrDefault("")
        return when (server) {
            "PAYOUT_NOT_ENABLED" -> "Pencairan dana belum diaktifkan."
            "PAYOUT_DESTINATION_NOT_VERIFIED" -> "Rekening pencairan belum diverifikasi."
            "PAYOUT_BELOW_MINIMUM" -> "Nominal pencairan di bawah batas minimum."
            "PAYOUT_ABOVE_MAXIMUM" -> "Nominal pencairan melebihi batas maksimum."
            "INSUFFICIENT_AVAILABLE_BALANCE" -> "Saldo tersedia tidak mencukupi."
            "DAILY_PAYOUT_LIMIT_EXCEEDED" -> "Batas pencairan harian tercapai."
            "DAILY_PAYOUT_REQUEST_LIMIT_EXCEEDED" -> "Batas jumlah payout harian tercapai."
            "WALLET_FROZEN" -> "Wallet sedang dibekukan sementara."
            else -> server.ifBlank { "Permintaan wallet gagal (HTTP " + code + ")." }
        }
    }
}

private fun JSONObject.money(name: String): Long =
    if (!has(name) || isNull(name)) 0L else runCatching { get(name).toString().toBigDecimal().toLong() }.getOrDefault(0L)

private fun JSONObject.nullableMoney(name: String): Long? =
    if (!has(name) || isNull(name)) null else runCatching { get(name).toString().toBigDecimal().toLong() }.getOrNull()

private fun JSONObject.textOrNull(name: String): String? =
    optString(name).takeIf { it.isNotBlank() && it != "null" }

private fun JSONArray?.earnings(): List<WalletEarning> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(
                WalletEarning(
                    j.optString("earningId"), j.optString("orderNo"), j.optString("serviceName"),
                    j.optString("status"), j.money("grossShare"), j.money("commissionAmount"),
                    j.money("baseNetAmount"), j.money("tipAmount"), j.money("totalNetAmount")
                )
            )
        }
    }
}

private fun JSONArray?.destinations(): List<WalletDestination> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(
                WalletDestination(
                    j.optString("destinationId"), j.optString("provider"), j.optString("displayLabel"),
                    j.textOrNull("holderName"), j.optString("status")
                )
            )
        }
    }
}

private fun JSONArray?.payouts(): List<WalletPayout> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(
                WalletPayout(
                    j.optString("payoutId"), j.money("amount"), j.optString("status"),
                    j.textOrNull("externalReference"), j.textOrNull("rejectionReason"),
                    j.textOrNull("failureReason")
                )
            )
        }
    }
}
