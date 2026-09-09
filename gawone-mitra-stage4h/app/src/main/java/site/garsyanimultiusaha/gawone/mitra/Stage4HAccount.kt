package site.garsyanimultiusaha.gawone.mitra

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class Stage4HAccountSummary(
    val fullName: String,
    val phone: String,
    val accountStatus: String,
    val availabilityStatus: String,
    val onboardingStatus: String,
    val canGoOnline: Boolean
)

internal data class Stage4HPerformance(
    val ratingAverage: Double,
    val ratingCount: Int,
    val completedJobs: Int,
    val offersReceived: Int,
    val offersAccepted: Int,
    val offersRejected: Int,
    val acceptanceRate: Double?,
    val finishedAssignments: Int,
    val cancelledAssignments: Int,
    val noShowCount: Int
)

internal data class Stage4HRating(
    val orderNo: String,
    val serviceName: String,
    val score: Int,
    val comment: String?
)

internal data class Stage4HSchedule(
    val assignmentId: String,
    val orderId: String,
    val orderNo: String,
    val serviceName: String,
    val status: String,
    val scheduledStart: String?,
    val scheduledEnd: String?
)

internal data class Stage4HService(
    val code: String,
    val name: String,
    val verificationStatus: String,
    val isActive: Boolean,
    val suspendedReason: String?
)

internal data class Stage4HVehicle(
    val vehicleId: String,
    val type: String,
    val plate: String?,
    val brand: String?,
    val model: String?,
    val verificationStatus: String,
    val isActive: Boolean
)

internal data class Stage4HDocument(
    val documentId: String,
    val type: String,
    val status: String,
    val expiresAt: String?,
    val isExpired: Boolean,
    val rejectionReason: String?
)

internal data class Stage4HRestriction(
    val restrictionId: String,
    val type: String,
    val status: String,
    val reasonCode: String,
    val note: String?,
    val effectiveUntil: String?
)

internal data class Stage4HAppeal(
    val appealId: String,
    val restrictionId: String,
    val status: String,
    val statement: String,
    val resolutionNote: String?
)

internal data class Stage4HSupportTicket(
    val ticketId: String,
    val ticketNo: String,
    val category: String,
    val subject: String,
    val status: String,
    val priority: String,
    val lastMessage: String?
)

internal data class Stage4HSupportMessage(
    val senderType: String,
    val mine: Boolean,
    val body: String,
    val createdAt: String?
)

internal data class Stage4HSupportDetail(
    val ticketId: String,
    val ticketNo: String,
    val subject: String,
    val status: String,
    val priority: String,
    val messages: List<Stage4HSupportMessage>
)

internal data class Stage4HAccountRequest(
    val requestId: String,
    val type: String,
    val status: String,
    val reason: String?,
    val resolutionNote: String?
)

internal data class Stage4HCenter(
    val account: Stage4HAccountSummary,
    val performance: Stage4HPerformance,
    val ratings: List<Stage4HRating>,
    val schedule: List<Stage4HSchedule>,
    val services: List<Stage4HService>,
    val vehicles: List<Stage4HVehicle>,
    val documents: List<Stage4HDocument>,
    val restrictions: List<Stage4HRestriction>,
    val appeals: List<Stage4HAppeal>,
    val supportTickets: List<Stage4HSupportTicket>,
    val accountRequests: List<Stage4HAccountRequest>
)

internal class Stage4HAccountClient(context: Context) {
    private val store = SecureSessionStore(context.applicationContext)
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val refreshMutex = Mutex()

    suspend fun center(): Stage4HCenter {
        val j = JSONObject(rpc("get_my_partner_account_center", JSONObject()))
        val a = j.optJSONObject("account") ?: JSONObject()
        val p = j.optJSONObject("performance") ?: JSONObject()
        val p30 = p.optJSONObject("last30Days") ?: JSONObject()

        return Stage4HCenter(
            account = Stage4HAccountSummary(
                fullName = a.optString("fullName"),
                phone = a.optString("phone"),
                accountStatus = a.optString("accountStatus"),
                availabilityStatus = a.optString("availabilityStatus"),
                onboardingStatus = a.optString("onboardingStatus"),
                canGoOnline = a.optBoolean("canGoOnline")
            ),
            performance = Stage4HPerformance(
                ratingAverage = p.optDouble("ratingAverage", 0.0),
                ratingCount = p.optInt("ratingCount"),
                completedJobs = p.optInt("completedJobsLifetime"),
                offersReceived = p30.optInt("offersReceived"),
                offersAccepted = p30.optInt("offersAccepted"),
                offersRejected = p30.optInt("offersRejected"),
                acceptanceRate = if (!p30.has("acceptanceRate") || p30.isNull("acceptanceRate")) null
                    else p30.optDouble("acceptanceRate"),
                finishedAssignments = p30.optInt("finishedAssignments"),
                cancelledAssignments = p30.optInt("cancelledAssignments"),
                noShowCount = p30.optInt("noShowCount")
            ),
            ratings = j.optJSONArray("performance")
                ?.let { emptyList() } ?: emptyList(),
            schedule = j.optJSONArray("schedule").toSchedule4H(),
            services = j.optJSONArray("services").toServices4H(),
            vehicles = j.optJSONArray("vehicles").toVehicles4H(),
            documents = j.optJSONArray("documents").toDocuments4H(),
            restrictions = j.optJSONArray("restrictions").toRestrictions4H(),
            appeals = j.optJSONArray("appeals").toAppeals4H(),
            supportTickets = j.optJSONArray("supportTickets").toTickets4H(),
            accountRequests = j.optJSONArray("accountRequests").toAccountRequests4H()
        ).copy(
            ratings = p.optJSONArray("recentRatings").toRatings4H()
        )
    }

    suspend fun submitAppeal(restrictionId: String, statement: String): String {
        val j = JSONObject(
            rpc(
                "submit_my_partner_appeal",
                JSONObject()
                    .put("p_restriction_id", restrictionId)
                    .put("p_statement", statement)
            )
        )
        return j.optString("appealId")
    }

    suspend fun createSupportTicket(
        category: String,
        subject: String,
        message: String
    ): String {
        val j = JSONObject(
            rpc(
                "create_my_support_ticket",
                JSONObject()
                    .put("p_category", category)
                    .put("p_subject", subject)
                    .put("p_message", message)
                    .put("p_order_id", JSONObject.NULL)
                    .put("p_assignment_id", JSONObject.NULL)
            )
        )
        return j.optString("ticketNo")
    }

    suspend fun supportDetail(ticketId: String): Stage4HSupportDetail {
        val j = JSONObject(
            rpc(
                "get_my_support_ticket_detail",
                JSONObject().put("p_ticket_id", ticketId)
            )
        )
        val messages = j.optJSONArray("messages") ?: JSONArray()
        val parsed = buildList {
            for (i in 0 until messages.length()) {
                val m = messages.getJSONObject(i)
                add(
                    Stage4HSupportMessage(
                        senderType = m.optString("senderType"),
                        mine = m.optBoolean("mine"),
                        body = m.optString("body"),
                        createdAt = m.textOrNull4H("createdAt")
                    )
                )
            }
        }
        return Stage4HSupportDetail(
            ticketId = j.optString("ticketId"),
            ticketNo = j.optString("ticketNo"),
            subject = j.optString("subject"),
            status = j.optString("status"),
            priority = j.optString("priority"),
            messages = parsed
        )
    }

    suspend fun addSupportMessage(ticketId: String, message: String) {
        rpc(
            "add_my_support_ticket_message",
            JSONObject()
                .put("p_ticket_id", ticketId)
                .put("p_message", message)
        )
    }

    suspend fun createAccountRequest(type: String, reason: String?): String {
        val j = JSONObject(
            rpc(
                "create_my_account_request",
                JSONObject()
                    .put("p_request_type", type)
                    .put("p_reason", reason?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            )
        )
        return j.optString("requestId")
    }

    suspend fun cancelAccountRequest(requestId: String) {
        rpc(
            "cancel_my_account_request",
            JSONObject().put("p_request_id", requestId)
        )
    }

    private suspend fun rpc(name: String, body: JSONObject): String {
        val session = validSession()
        return request(
            "/rest/v1/rpc/" + name,
            "POST",
            body.toString(),
            session.accessToken
        )
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
                ?: now + j.optLong("expires_in", 3600L),
            userId = user.getString("id"),
            phone = user.optString("phone", current.phone)
        )
        store.save(refreshed)
        refreshed
    }

    private suspend fun request(
        path: String,
        method: String,
        body: String?,
        token: String?
    ): String = withContext(Dispatchers.IO) {
        val c = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.doInput = true
            c.useCaches = false
            c.setRequestProperty("apikey", key)
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) c.setRequestProperty("Authorization", "Bearer " + token)

            if (body != null) {
                c.doOutput = true
                c.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }

            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val server = runCatching {
                    val e = JSONObject(raw)
                    e.optString("message")
                        .ifBlank { e.optString("msg") }
                        .ifBlank { e.optString("error") }
                }.getOrDefault("")
                throw IllegalStateException(mapStage4HError(server, code))
            }
            raw
        } finally {
            c.disconnect()
        }
    }

    private fun mapStage4HError(server: String, code: Int): String = when (server) {
        "OPEN_APPEAL_ALREADY_EXISTS" -> "Banding untuk pembatasan ini masih diproses."
        "APPEAL_STATEMENT_TOO_SHORT" -> "Penjelasan banding minimal 20 karakter."
        "RESTRICTION_NOT_APPEALABLE" -> "Pembatasan ini sudah tidak dapat dibandingi."
        "SUPPORT_SUBJECT_TOO_SHORT" -> "Judul tiket terlalu pendek."
        "SUPPORT_MESSAGE_TOO_SHORT" -> "Pesan bantuan terlalu pendek."
        "SUPPORT_TICKET_CLOSED" -> "Tiket sudah ditutup."
        "OPEN_ACCOUNT_REQUEST_ALREADY_EXISTS" -> "Permintaan sejenis masih diproses."
        "ACCOUNT_REQUEST_REASON_TOO_SHORT" -> "Alasan minimal 10 karakter."
        "ACCOUNT_REQUEST_NOT_CANCELLABLE" -> "Permintaan ini sudah diproses dan tidak dapat dibatalkan dari aplikasi."
        else -> server.ifBlank { "Permintaan akun gagal (HTTP " + code + ")." }
    }
}

private fun JSONObject.textOrNull4H(name: String): String? =
    optString(name).takeIf { it.isNotBlank() && it != "null" }

private fun JSONArray?.toRatings4H(): List<Stage4HRating> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(Stage4HRating(
                orderNo = j.optString("orderNo"),
                serviceName = j.optString("serviceName"),
                score = j.optInt("score"),
                comment = j.textOrNull4H("comment")
            ))
        }
    }
}

private fun JSONArray?.toSchedule4H(): List<Stage4HSchedule> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(Stage4HSchedule(
                assignmentId = j.optString("assignmentId"),
                orderId = j.optString("orderId"),
                orderNo = j.optString("orderNo"),
                serviceName = j.optString("serviceName"),
                status = j.optString("assignmentStatus"),
                scheduledStart = j.textOrNull4H("scheduledStart"),
                scheduledEnd = j.textOrNull4H("scheduledEnd")
            ))
        }
    }
}

private fun JSONArray?.toServices4H(): List<Stage4HService> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(Stage4HService(
                code = j.optString("code"),
                name = j.optString("name"),
                verificationStatus = j.optString("verificationStatus"),
                isActive = j.optBoolean("isActive"),
                suspendedReason = j.textOrNull4H("suspendedReason")
            ))
        }
    }
}

private fun JSONArray?.toVehicles4H(): List<Stage4HVehicle> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(Stage4HVehicle(
                vehicleId = j.optString("vehicleId"),
                type = j.optString("type"),
                plate = j.textOrNull4H("plate"),
                brand = j.textOrNull4H("brand"),
                model = j.textOrNull4H("model"),
                verificationStatus = j.optString("verificationStatus"),
                isActive = j.optBoolean("isActive")
            ))
        }
    }
}

private fun JSONArray?.toDocuments4H(): List<Stage4HDocument> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(Stage4HDocument(
                documentId = j.optString("documentId"),
                type = j.optString("type"),
                status = j.optString("status"),
                expiresAt = j.textOrNull4H("expiresAt"),
                isExpired = j.optBoolean("isExpired"),
                rejectionReason = j.textOrNull4H("rejectionReason")
            ))
        }
    }
}

private fun JSONArray?.toRestrictions4H(): List<Stage4HRestriction> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(Stage4HRestriction(
                restrictionId = j.optString("restrictionId"),
                type = j.optString("type"),
                status = j.optString("status"),
                reasonCode = j.optString("reasonCode"),
                note = j.textOrNull4H("note"),
                effectiveUntil = j.textOrNull4H("effectiveUntil")
            ))
        }
    }
}

private fun JSONArray?.toAppeals4H(): List<Stage4HAppeal> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(Stage4HAppeal(
                appealId = j.optString("appealId"),
                restrictionId = j.optString("restrictionId"),
                status = j.optString("status"),
                statement = j.optString("statement"),
                resolutionNote = j.textOrNull4H("resolutionNote")
            ))
        }
    }
}

private fun JSONArray?.toTickets4H(): List<Stage4HSupportTicket> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(Stage4HSupportTicket(
                ticketId = j.optString("ticketId"),
                ticketNo = j.optString("ticketNo"),
                category = j.optString("category"),
                subject = j.optString("subject"),
                status = j.optString("status"),
                priority = j.optString("priority"),
                lastMessage = j.textOrNull4H("lastMessage")
            ))
        }
    }
}

private fun JSONArray?.toAccountRequests4H(): List<Stage4HAccountRequest> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(Stage4HAccountRequest(
                requestId = j.optString("requestId"),
                type = j.optString("type"),
                status = j.optString("status"),
                reason = j.textOrNull4H("reason"),
                resolutionNote = j.textOrNull4H("resolutionNote")
            ))
        }
    }
}
