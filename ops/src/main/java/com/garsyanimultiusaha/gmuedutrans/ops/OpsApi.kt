package com.garsyanimultiusaha.gmuedutrans.ops

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OpsSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val name: String,
    val role: String
)

data class OpsOrder(
    val id: String,
    val number: String,
    val customer: String,
    val program: String,
    val date: String,
    val pax: Int,
    val status: String,
    val meetingPoint: String,
    val notes: String
)

data class OpsPerson(
    val id: String,
    val bookingId: String,
    val name: String,
    val group: String
)

data class OpsAgenda(
    val bookingId: String,
    val time: String,
    val activity: String,
    val location: String,
    val pic: String
)

data class OpsAttendance(
    val id: String,
    val bookingId: String,
    val manifestId: String,
    val present: Boolean
)

data class OpsEvidence(
    val tripIds: Set<String> = emptySet(),
    val sheetRows: Map<String, JSONObject> = emptyMap(),
    val people: List<OpsPerson> = emptyList(),
    val agenda: List<OpsAgenda> = emptyList(),
    val attendance: List<OpsAttendance> = emptyList(),
    val vendorBookings: Set<String> = emptySet(),
    val documentCounts: Map<String, Int> = emptyMap(),
    val reportBookings: Set<String> = emptySet(),
    val evaluationBookings: Set<String> = emptySet(),
    val warnings: List<String> = emptyList()
) {
    fun readiness(orderId: String): List<Pair<String, Boolean>> = listOf(
        "Operation sheet" to sheetRows.containsKey(orderId),
        "Manifest peserta" to people.any { it.bookingId == orderId },
        "Rundown final" to agenda.any { it.bookingId == orderId },
        "Vendor / PO" to vendorBookings.contains(orderId),
        "Dokumentasi (≥5)" to ((documentCounts[orderId] ?: 0) >= 5)
    )
}

private fun JSONObject.str(key: String): String =
    if (!has(key) || isNull(key)) "" else optString(key, "")

class OpsApi {
    private val baseUrl = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    private fun request(
        method: String,
        path: String,
        token: String? = null,
        payload: JSONObject? = null
    ): String {
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection)
        return try {
            conn.requestMethod = method
            conn.connectTimeout = 12000
            conn.readTimeout = 18000
            conn.setRequestProperty("apikey", key)
            conn.setRequestProperty("Authorization", "Bearer " + (token ?: key))
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/json")
            if (payload != null) {
                conn.doOutput = true
                conn.setRequestProperty("Prefer", "return=representation")
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val error = runCatching { JSONObject(text) }.getOrNull()
                val reason = error?.str("msg").orEmpty().ifBlank {
                    error?.str("message").orEmpty().ifBlank {
                        error?.str("error_description").orEmpty().ifBlank { "HTTP $code" }
                    }
                }
                throw IllegalStateException(reason)
            }
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun authSession(auth: JSONObject): OpsSession {
        val token = auth.getString("access_token")
        val refresh = auth.str("refresh_token")
        val uid = auth.getJSONObject("user").getString("id")
        val profile = loadProfile(token, uid)
        return OpsSession(token, refresh, uid, profile.first, profile.second)
    }

    private fun loadProfile(token: String, uid: String): Pair<String, String> {
        val id = URLEncoder.encode(uid, "UTF-8")
        val profiles = JSONArray(
            request("GET", "/rest/v1/profiles?select=full_name,role,is_active&id=eq.$id&limit=1", token)
        )
        if (profiles.length() == 0) throw IllegalStateException("Profil ERP tidak ditemukan.")
        val p = profiles.getJSONObject(0)
        if (!p.optBoolean("is_active", false)) throw IllegalStateException("Akun ERP tidak aktif.")
        val role = p.str("role")
        if (!role.equals("Operation", true) && !role.equals("Operasional", true) &&
            !role.equals("Staf Operasional", true) &&
            role !in listOf("Owner", "Director", "Direktur", "Manager", "Manager EduTrans", "Admin")) {
            throw IllegalStateException("Akun ini tidak memiliki akses aplikasi Ops.")
        }
        return p.str("full_name").ifBlank { "Staf GMU" } to role
    }

    suspend fun signIn(email: String, password: String): OpsSession = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        authSession(JSONObject(request("POST", "/auth/v1/token?grant_type=password", payload = body)))
    }

    suspend fun refresh(refreshToken: String): OpsSession = withContext(Dispatchers.IO) {
        val body = JSONObject().put("refresh_token", refreshToken)
        authSession(JSONObject(request("POST", "/auth/v1/token?grant_type=refresh_token", payload = body)))
    }

    suspend fun loadOrders(session: OpsSession): List<OpsOrder> = withContext(Dispatchers.IO) {
        val customerArray = JSONArray(
            request("GET", "/rest/v1/customers?select=id,name&limit=2000", session.accessToken)
        )
        val customers = buildMap<String, String> {
            for (i in 0 until customerArray.length()) {
                val c = customerArray.getJSONObject(i)
                put(c.str("id"), c.str("name"))
            }
        }
        val data = JSONArray(
            request(
                "GET",
                "/rest/v1/bookings?select=id,booking_no,customer_id,program_name,trip_date,pax,status,meeting_point,special_requirements&order=trip_date.asc&limit=2000",
                session.accessToken
            )
        )
        buildList {
            for (i in 0 until data.length()) {
                val b = data.getJSONObject(i)
                if (b.str("status").lowercase() !in setOf("confirmed", "preparation", "trip", "completed", "closed")) continue
                add(OpsOrder(
                    id = b.str("id"),
                    number = b.str("booking_no"),
                    customer = customers[b.str("customer_id")] ?: "Customer belum tersedia",
                    program = b.str("program_name"),
                    date = b.str("trip_date"),
                    pax = b.optInt("pax", 0),
                    status = b.str("status"),
                    meetingPoint = b.str("meeting_point"),
                    notes = b.str("special_requirements")
                ))
            }
        }
    }

    suspend fun loadEvidence(session: OpsSession): OpsEvidence = withContext(Dispatchers.IO) {
        val token = session.accessToken
        val warnings = mutableListOf<String>()
        fun rows(name: String, columns: String): JSONArray =
            runCatching {
                JSONArray(request("GET", "/rest/v1/$name?select=$columns&limit=2000", token))
            }.getOrElse {
                warnings += "$name: tidak dapat dimuat"
                JSONArray()
            }
        val trips = rows("trips", "booking_id")
        val sheets = rows("operation_sheets", "booking_id,meeting_time,operation_pic,driver_contact,transport,equipment,readiness_status")
        val manifests = rows("manifests", "id,booking_id,participant_name,class_or_age")
        val rundowns = rows("rundown_items", "booking_id,activity_time,activity,location,pic")
        val attendance = rows("attendance", "id,booking_id,manifest_id,present")
        val vendors = rows("vendor_pos", "booking_id")
        val documents = rows("documents", "booking_id")
        val reports = rows("trip_reports", "booking_id")
        val evaluations = rows("evaluations", "booking_id")
        fun ids(array: JSONArray): Set<String> = buildSet {
            for (i in 0 until array.length()) add(array.getJSONObject(i).str("booking_id"))
        }
        val sheetsByBooking = buildMap<String, JSONObject> {
            for (i in 0 until sheets.length()) {
                val row = sheets.getJSONObject(i)
                put(row.str("booking_id"), row)
            }
        }
        val people = buildList {
            for (i in 0 until manifests.length()) {
                val p = manifests.getJSONObject(i)
                add(OpsPerson(p.str("id"), p.str("booking_id"), p.str("participant_name"), p.str("class_or_age")))
            }
        }
        val agenda = buildList {
            for (i in 0 until rundowns.length()) {
                val a = rundowns.getJSONObject(i)
                add(OpsAgenda(a.str("booking_id"), a.str("activity_time"), a.str("activity"), a.str("location"), a.str("pic")))
            }
        }
        val checkins = buildList {
            for (i in 0 until attendance.length()) {
                val a = attendance.getJSONObject(i)
                add(OpsAttendance(a.str("id"), a.str("booking_id"), a.str("manifest_id"), a.optBoolean("present", false)))
            }
        }
        val docCounts = mutableMapOf<String, Int>()
        for (i in 0 until documents.length()) {
            val id = documents.getJSONObject(i).str("booking_id")
            docCounts[id] = (docCounts[id] ?: 0) + 1
        }
        OpsEvidence(
            tripIds = ids(trips),
            sheetRows = sheetsByBooking,
            people = people,
            agenda = agenda,
            attendance = checkins,
            vendorBookings = ids(vendors),
            documentCounts = docCounts,
            reportBookings = ids(reports),
            evaluationBookings = ids(evaluations),
            warnings = warnings
        )
    }

    suspend fun setAttendance(
        session: OpsSession,
        orderId: String,
        personId: String,
        existingAttendanceId: String?,
        present: Boolean
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("booking_id", orderId)
            .put("manifest_id", personId)
            .put("present", present)
            .put("checked_by", session.userId)
        if (existingAttendanceId.isNullOrBlank()) {
            request("POST", "/rest/v1/attendance", session.accessToken, body)
        } else {
            val id = URLEncoder.encode(existingAttendanceId, "UTF-8")
            request("PATCH", "/rest/v1/attendance?id=eq.$id", session.accessToken, body)
        }
        Unit
    }
}
