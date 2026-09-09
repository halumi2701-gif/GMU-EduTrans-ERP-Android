package site.garsyanimultiusaha.gawone.mitra

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class PartnerOffer(
    val offerId: String,
    val orderId: String,
    val orderNo: String,
    val orderType: String,
    val slotNo: Int,
    val serviceCode: String,
    val serviceName: String,
    val distanceKm: Double?,
    val estimatedPartnerNet: Long,
    val offeredAt: String?,
    val expiresAt: String,
    val expiresAtEpochMs: Long,
    val scheduledStart: String?,
    val pickupAddress: String?,
    val destinationAddress: String?,
)

internal data class PartnerOfferDetail(
    val offerId: String,
    val status: String,
    val expiresAt: String,
    val expiresAtEpochMs: Long,
    val distanceKm: Double?,
    val estimatedPartnerNet: Long,
    val orderId: String,
    val orderNo: String,
    val orderType: String,
    val serviceCode: String,
    val serviceName: String,
    val scheduledStart: String?,
    val scheduledEnd: String?,
    val requiredWorkers: Int,
    val notes: String?,
    val pickupAddress: String?,
    val destinationAddress: String?,
)

internal data class PartnerAssignment(
    val assignmentId: String,
    val orderId: String,
    val orderNo: String,
    val slotNo: Int,
    val serviceCode: String,
    val serviceName: String,
    val assignmentStatus: String,
    val orderStatus: String,
    val scheduledStart: String?,
    val scheduledEnd: String?,
    val customerDisplayName: String,
    val pickupAddress: String?,
    val destinationAddress: String?,
    val assignedAt: String?,
)

internal data class AcceptOfferResult(
    val assignmentId: String,
    val orderId: String,
    val orderNo: String,
    val slotNo: Int,
    val status: String,
    val assignedCount: Int,
    val requiredCount: Int,
)

internal class Stage4DOfferClient(context: Context) {
    private val store = SecureSessionStore(context.applicationContext)
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val refreshMutex = Mutex()

    suspend fun offers(): List<PartnerOffer> {
        // Server-side cleanup makes expired batches release their slot before the next read.
        runCatching { rpc("expire_my_partner_offers", JSONObject()) }

        val raw = rpc("get_my_partner_offers", JSONObject())
        val array = raw.toJsonArray()
        return buildList {
            for (i in 0 until array.length()) {
                val j = array.getJSONObject(i)
                val expiresAt = j.optString("expiresAt")
                add(
                    PartnerOffer(
                        offerId = j.optString("offerId"),
                        orderId = j.optString("orderId"),
                        orderNo = j.optString("orderNo"),
                        orderType = j.optString("orderType"),
                        slotNo = j.optInt("slotNo"),
                        serviceCode = j.optString("serviceCode"),
                        serviceName = j.optString("serviceName"),
                        distanceKm = j.optDoubleOrNull("distanceKm"),
                        estimatedPartnerNet = j.optLong("estimatedPartnerNet"),
                        offeredAt = j.optNullableString("offeredAt"),
                        expiresAt = expiresAt,
                        expiresAtEpochMs = parseEpochMillis(expiresAt),
                        scheduledStart = j.optNullableString("scheduledStart"),
                        pickupAddress = j.optNullableString("pickupAddress"),
                        destinationAddress = j.optNullableString("destinationAddress"),
                    )
                )
            }
        }
    }

    suspend fun detail(offerId: String): PartnerOfferDetail {
        val raw = rpc(
            "get_my_partner_offer_detail",
            JSONObject().put("p_offer_id", offerId)
        )
        if (raw.isBlank() || raw.trim() == "null") {
            throw IllegalStateException("Offer tidak ditemukan.")
        }

        val j = JSONObject(raw)
        val expiresAt = j.optString("expiresAt")
        return PartnerOfferDetail(
            offerId = j.optString("offerId"),
            status = j.optString("status"),
            expiresAt = expiresAt,
            expiresAtEpochMs = parseEpochMillis(expiresAt),
            distanceKm = j.optDoubleOrNull("distanceKm"),
            estimatedPartnerNet = j.optLong("estimatedPartnerNet"),
            orderId = j.optString("orderId"),
            orderNo = j.optString("orderNo"),
            orderType = j.optString("orderType"),
            serviceCode = j.optString("serviceCode"),
            serviceName = j.optString("serviceName"),
            scheduledStart = j.optNullableString("scheduledStart"),
            scheduledEnd = j.optNullableString("scheduledEnd"),
            requiredWorkers = j.optInt("requiredWorkers", 1),
            notes = j.optNullableString("notes"),
            pickupAddress = j.optNullableString("pickupAddress"),
            destinationAddress = j.optNullableString("destinationAddress"),
        )
    }

    suspend fun accept(offerId: String): AcceptOfferResult {
        val raw = rpc(
            "accept_assignment_offer",
            JSONObject().put("p_offer_id", offerId)
        )
        val j = JSONObject(raw)
        return AcceptOfferResult(
            assignmentId = j.optString("assignmentId"),
            orderId = j.optString("orderId"),
            orderNo = j.optString("orderNo"),
            slotNo = j.optInt("slotNo"),
            status = j.optString("status"),
            assignedCount = j.optInt("assignedCount"),
            requiredCount = j.optInt("requiredCount"),
        )
    }

    suspend fun reject(offerId: String, reason: String): String {
        val raw = rpc(
            "reject_assignment_offer",
            JSONObject()
                .put("p_offer_id", offerId)
                .put("p_reason", reason)
        )
        return JSONObject(raw).optString("status", "REJECTED")
    }

    suspend fun assignments(): List<PartnerAssignment> {
        val raw = rpc("get_my_partner_assignments", JSONObject())
        val array = raw.toJsonArray()
        return buildList {
            for (i in 0 until array.length()) {
                val j = array.getJSONObject(i)
                add(
                    PartnerAssignment(
                        assignmentId = j.optString("assignmentId"),
                        orderId = j.optString("orderId"),
                        orderNo = j.optString("orderNo"),
                        slotNo = j.optInt("slotNo"),
                        serviceCode = j.optString("serviceCode"),
                        serviceName = j.optString("serviceName"),
                        assignmentStatus = j.optString("assignmentStatus"),
                        orderStatus = j.optString("orderStatus"),
                        scheduledStart = j.optNullableString("scheduledStart"),
                        scheduledEnd = j.optNullableString("scheduledEnd"),
                        customerDisplayName = j.optString("customerDisplayName", "Customer"),
                        pickupAddress = j.optNullableString("pickupAddress"),
                        destinationAddress = j.optNullableString("destinationAddress"),
                        assignedAt = j.optNullableString("assignedAt"),
                    )
                )
            }
        }
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
        val current = store.read()
            ?: throw IllegalStateException("Sesi Mitra tidak tersedia. Silakan login ulang.")

        val now = System.currentTimeMillis() / 1000L
        if (current.expiresAt - now > 90L) {
            return@withLock current
        }

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
                throw IllegalStateException(mapOfferError(raw, code))
            }
            raw
        } finally {
            connection.disconnect()
        }
    }

    private fun mapOfferError(raw: String, code: Int): String {
        val server = runCatching {
            val j = JSONObject(raw)
            j.optString("message")
                .ifBlank { j.optString("msg") }
                .ifBlank { j.optString("error") }
        }.getOrDefault("")

        return when (server) {
            "OFFER_EXPIRED" -> "Waktu menerima order sudah habis."
            "OFFER_NOT_ACTIVE" -> "Offer ini sudah tidak aktif."
            "SLOT_ALREADY_ASSIGNED" -> "Order sudah diterima Mitra lain."
            "ORDER_NOT_ACCEPTING_ASSIGNMENT" -> "Order sudah tidak menerima Mitra."
            "PARTNER_ALREADY_BUSY" -> "Anda sedang memiliki pekerjaan aktif."
            "PARTNER_PRESENCE_STALE" -> "Status Online/GPS Anda tidak lagi fresh."
            "PARTNER_SERVICE_NOT_ELIGIBLE" -> "Verifikasi layanan Anda tidak lagi memenuhi syarat."
            else -> server.ifBlank { "Permintaan offer gagal (HTTP $code)." }
        }
    }
}

private fun String.toJsonArray(): JSONArray {
    val value = trim()
    if (value.isBlank() || value == "null") return JSONArray()
    return JSONArray(value)
}

private fun JSONObject.optNullableString(name: String): String? =
    optString(name)
        .takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

private fun parseEpochMillis(value: String): Long =
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .getOrElse { 0L }
