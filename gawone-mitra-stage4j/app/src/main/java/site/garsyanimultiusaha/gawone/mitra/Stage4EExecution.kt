package site.garsyanimultiusaha.gawone.mitra

import android.content.Context
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class ExecutionProof(
    val proofId: String,
    val proofType: String,
    val note: String?,
    val createdAt: String?,
)

internal data class ExecutionIssue(
    val issueId: String,
    val issueType: String,
    val severity: String,
    val status: String,
    val note: String?,
    val createdAt: String?,
)

internal data class AssignmentExecution(
    val assignmentId: String,
    val orderId: String,
    val orderNo: String,
    val serviceCode: String,
    val serviceName: String,
    val assignmentStatus: String,
    val orderStatus: String,
    val customerDisplayName: String,
    val pickupAddress: String?,
    val pickupLatitude: Double?,
    val pickupLongitude: Double?,
    val checkinRadiusM: Int,
    val maxCheckinAccuracyM: Int,
    val requiredFinishProofTypes: List<String>,
    val minimumFinishProofs: Int,
    val proofs: List<ExecutionProof>,
    val issues: List<ExecutionIssue>,
    val nextAction: String?,
)

internal class Stage4EExecutionClient(context: Context) {
    private val appContext = context.applicationContext
    private val store = SecureSessionStore(appContext)
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val refreshMutex = Mutex()

    suspend fun detail(assignmentId: String): AssignmentExecution {
        val raw = rpc(
            "get_my_assignment_execution",
            JSONObject().put("p_assignment_id", assignmentId)
        )
        if (raw.isBlank() || raw.trim() == "null") {
            throw IllegalStateException("Assignment tidak ditemukan.")
        }

        val j = JSONObject(raw)
        return AssignmentExecution(
            assignmentId = j.optString("assignmentId"),
            orderId = j.optString("orderId"),
            orderNo = j.optString("orderNo"),
            serviceCode = j.optString("serviceCode"),
            serviceName = j.optString("serviceName"),
            assignmentStatus = j.optString("assignmentStatus"),
            orderStatus = j.optString("orderStatus"),
            customerDisplayName = j.optString("customerDisplayName", "Customer"),
            pickupAddress = j.optNullableString4E("pickupAddress"),
            pickupLatitude = j.optDoubleOrNull4E("pickupLatitude"),
            pickupLongitude = j.optDoubleOrNull4E("pickupLongitude"),
            checkinRadiusM = j.optInt("checkinRadiusM", 300),
            maxCheckinAccuracyM = j.optInt("maxCheckinAccuracyM", 150),
            requiredFinishProofTypes = j.optJSONArray("requiredFinishProofTypes").toStringList4E(),
            minimumFinishProofs = j.optInt("minimumFinishProofs", 0),
            proofs = j.optJSONArray("proofs").toProofs(),
            issues = j.optJSONArray("issues").toIssues(),
            nextAction = j.optNullableString4E("nextAction"),
        )
    }

    suspend fun action(
        assignmentId: String,
        action: String,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracyM: Float? = null,
    ): AssignmentExecution {
        rpc(
            "partner_assignment_action",
            JSONObject()
                .put("p_assignment_id", assignmentId)
                .put("p_action", action)
                .put("p_latitude", latitude ?: JSONObject.NULL)
                .put("p_longitude", longitude ?: JSONObject.NULL)
                .put("p_accuracy_m", accuracyM?.toDouble() ?: JSONObject.NULL)
                .put("p_metadata", JSONObject().put("source", "MITRA_STAGE4E"))
        )
        return detail(assignmentId)
    }

    suspend fun uploadProof(
        assignmentId: String,
        uri: Uri,
        proofType: String,
        note: String? = null,
    ): AssignmentExecution {
        val session = validSession()
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"

        require(mime in setOf("image/jpeg", "image/png", "application/pdf")) {
            "Bukti harus JPG, PNG, atau PDF."
        }

        val bytes = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("File bukti tidak dapat dibaca.")
        }

        require(bytes.isNotEmpty()) { "File bukti kosong." }
        require(bytes.size <= 10 * 1024 * 1024) { "Ukuran bukti maksimum 10 MB." }

        val ext = when (mime) {
            "image/png" -> "png"
            "application/pdf" -> "pdf"
            else -> "jpg"
        }

        val path = "${session.userId}/$assignmentId/$proofType/${UUID.randomUUID()}.$ext"

        requestBytes(
            path = "/storage/v1/object/assignment-proof-private/$path",
            bytes = bytes,
            mime = mime,
            token = session.accessToken,
        )

        try {
            rpc(
                "register_assignment_proof",
                JSONObject()
                    .put("p_assignment_id", assignmentId)
                    .put("p_proof_type", proofType)
                    .put("p_storage_path", path)
                    .put("p_mime_type", mime)
                    .put("p_note", note ?: JSONObject.NULL)
            )
        } catch (error: Throwable) {
            runCatching {
                request(
                    path = "/storage/v1/object/assignment-proof-private/$path",
                    method = "DELETE",
                    body = null,
                    token = session.accessToken,
                )
            }
            throw error
        }

        return detail(assignmentId)
    }

    suspend fun reportIssue(
        assignmentId: String,
        issueType: String,
        severity: String,
        note: String?,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracyM: Float? = null,
    ): AssignmentExecution {
        rpc(
            "report_assignment_issue",
            JSONObject()
                .put("p_assignment_id", assignmentId)
                .put("p_issue_type", issueType)
                .put("p_severity", severity)
                .put("p_note", note ?: JSONObject.NULL)
                .put("p_latitude", latitude ?: JSONObject.NULL)
                .put("p_longitude", longitude ?: JSONObject.NULL)
                .put("p_accuracy_m", accuracyM?.toDouble() ?: JSONObject.NULL)
        )
        return detail(assignmentId)
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
            readTimeout = 30_000
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
            if (code !in 200..299) throw IllegalStateException(mapExecutionError(raw, code))
            raw
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun requestBytes(
        path: String,
        bytes: ByteArray,
        mime: String,
        token: String,
    ) = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 25_000
            readTimeout = 45_000
            doInput = true
            doOutput = true
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", mime)
            setRequestProperty("x-upsert", "false")
            setFixedLengthStreamingMode(bytes.size)
        }

        try {
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException(mapExecutionError(raw, code))
        } finally {
            connection.disconnect()
        }
    }

    private fun mapExecutionError(raw: String, code: Int): String {
        val server = runCatching {
            val j = JSONObject(raw)
            j.optString("message")
                .ifBlank { j.optString("msg") }
                .ifBlank { j.optString("error") }
        }.getOrDefault("")

        return when (server) {
            "PARTNER_PRESENCE_STALE" -> "Aktifkan status Online/GPS sebelum berangkat."
            "CHECKIN_GPS_REQUIRED" -> "GPS presisi diperlukan untuk check-in."
            "CHECKIN_ACCURACY_TOO_LOW" -> "Akurasi GPS belum cukup. Tunggu sinyal lokasi lebih baik."
            "ORDER_CHECKIN_LOCATION_MISSING" -> "Koordinat lokasi order belum tersedia."
            "CHECKIN_TOO_FAR" -> "Anda masih terlalu jauh dari lokasi untuk check-in."
            "COMPLETION_PROOF_REQUIRED" -> "Upload bukti penyelesaian sebelum menyelesaikan pekerjaan."
            "OPEN_BLOCKING_ISSUE" -> "Ada kendala BLOCKING/EMERGENCY yang belum diselesaikan operasional."
            "INVALID_ASSIGNMENT_TRANSITION" -> "Urutan status pekerjaan tidak valid."
            "PROOF_NOT_ALLOWED_IN_CURRENT_STATUS" -> "Bukti belum dapat diunggah pada status pekerjaan ini."
            else -> server.ifBlank { "Permintaan pekerjaan gagal (HTTP $code)." }
        }
    }
}

private fun JSONObject.optNullableString4E(name: String): String? =
    optString(name).takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.optDoubleOrNull4E(name: String): Double? =
    if (isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

private fun JSONArray?.toStringList4E(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) add(optString(i))
    }
}

private fun JSONArray?.toProofs(): List<ExecutionProof> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(
                ExecutionProof(
                    proofId = j.optString("proofId"),
                    proofType = j.optString("proofType"),
                    note = j.optNullableString4E("note"),
                    createdAt = j.optNullableString4E("createdAt"),
                )
            )
        }
    }
}

private fun JSONArray?.toIssues(): List<ExecutionIssue> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(
                ExecutionIssue(
                    issueId = j.optString("issueId"),
                    issueType = j.optString("issueType"),
                    severity = j.optString("severity"),
                    status = j.optString("status"),
                    note = j.optNullableString4E("note"),
                    createdAt = j.optNullableString4E("createdAt"),
                )
            )
        }
    }
}
