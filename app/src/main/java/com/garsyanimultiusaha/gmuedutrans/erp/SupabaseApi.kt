package com.garsyanimultiusaha.gmuedutrans.erp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SupabaseApi {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun signIn(email: String, password: String): SessionState = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("email", email.trim()).put("password", password).toString()
        sessionFromAuth(JSONObject(request("POST", "/auth/v1/token?grant_type=password", payload, null)))
    }

    suspend fun refresh(refreshToken: String): SessionState = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("refresh_token", refreshToken).toString()
        sessionFromAuth(JSONObject(request("POST", "/auth/v1/token?grant_type=refresh_token", payload, null)))
    }

    private fun sessionFromAuth(auth: JSONObject): SessionState {
        val accessToken = auth.getString("access_token")
        val refreshToken = auth.optString("refresh_token", "")
        val userId = auth.getJSONObject("user").getString("id")
        val profile = fetchProfileBlocking(accessToken, userId)
        if (!profile.active) throw IllegalStateException("Akun staf tidak aktif. Hubungi Owner.")
        return SessionState(accessToken, refreshToken, userId, profile)
    }

    suspend fun fetchProfile(accessToken: String, userId: String): StaffProfile =
        withContext(Dispatchers.IO) { fetchProfileBlocking(accessToken, userId) }

    private fun fetchProfileBlocking(accessToken: String, userId: String): StaffProfile {
        val encodedId = URLEncoder.encode(userId, "UTF-8")
        val body = request(
            "GET",
            "/rest/v1/profiles?select=id,full_name,role,is_active,phone&id=eq." + encodedId + "&limit=1",
            null,
            accessToken
        )
        val arr = JSONArray(body)
        if (arr.length() == 0) throw IllegalStateException("Profil ERP tidak ditemukan.")
        val p = arr.getJSONObject(0)
        return StaffProfile(
            id = p.getString("id"),
            fullName = p.optString("full_name", "Staff GMU"),
            role = p.optString("role", "Sales"),
            active = p.optBoolean("is_active", false),
            phone = p.optString("phone", "")
        )
    }

    suspend fun signOut(accessToken: String) = withContext(Dispatchers.IO) {
        runCatching { request("POST", "/auth/v1/logout", "{}", accessToken) }
        Unit
    }

    suspend fun getCustomers(accessToken: String): List<Customer> = withContext(Dispatchers.IO) {
        val arr = JSONArray(request("GET", "/rest/v1/customers?select=*&order=created_at.desc", null, accessToken))
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(
                    Customer(
                        id = x.getString("id"),
                        code = x.optString("customer_code", "-"),
                        name = x.optString("name", "-"),
                        type = x.optString("customer_type", "-"),
                        pic = x.optString("pic_name", ""),
                        whatsapp = x.optString("whatsapp", ""),
                        email = x.optString("email", ""),
                        address = x.optString("address", ""),
                        notes = x.optString("notes", "")
                    )
                )
            }
        }
    }

    suspend fun getBookings(accessToken: String, customers: List<Customer>): List<Booking> = withContext(Dispatchers.IO) {
        val customerMap = customers.associateBy { it.id }
        val arr = JSONArray(request("GET", "/rest/v1/bookings?select=*&order=trip_date.asc", null, accessToken))
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                val customerId = x.optString("customer_id", "")
                add(
                    Booking(
                        id = x.getString("id"),
                        bookingNo = x.optString("booking_no", "-"),
                        customerId = customerId,
                        customerName = customerMap[customerId]?.name ?: "-",
                        salesId = x.optString("sales_id", ""),
                        programName = x.optString("program_name", "-"),
                        tripDate = x.optString("trip_date", ""),
                        pax = x.optInt("pax", 0),
                        pricePerPax = x.optDouble("price_per_pax", 0.0),
                        status = x.optString("status", "Lead"),
                        participantGroup = x.optString("participant_group", ""),
                        meetingPoint = x.optString("meeting_point", ""),
                        facilities = x.optString("facilities", ""),
                        specialRequirements = x.optString("special_requirements", ""),
                        notes = x.optString("notes", "")
                    )
                )
            }
        }
    }

    suspend fun getRows(accessToken: String, table: String, order: String? = null): List<ErpRow> =
        withContext(Dispatchers.IO) {
            val suffix = if (order.isNullOrBlank()) "" else "&order=$order"
            val arr = JSONArray(request("GET", "/rest/v1/$table?select=*$suffix", null, accessToken))
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(jsonToRow(table, obj))
                }
            }
        }

    suspend fun createCustomer(
        accessToken: String,
        userId: String,
        name: String,
        type: String,
        pic: String,
        whatsapp: String,
        email: String
    ) = withContext(Dispatchers.IO) {
        val code = rpcText(accessToken, "next_customer_code")
        val payload = JSONObject()
            .put("customer_code", code)
            .put("name", name.trim())
            .put("customer_type", type.trim().ifBlank { "Sekolah" })
            .put("pic_name", nullable(pic))
            .put("whatsapp", nullable(whatsapp))
            .put("email", nullable(email))
            .put("created_by", userId)
        insertRowBlocking(accessToken, "customers", payload)
        audit(accessToken, userId, "CREATE_CUSTOMER", "customers", code, "Customer $name dibuat dari Android Native RC")
    }

    suspend fun createBooking(
        accessToken: String,
        userId: String,
        role: String,
        customerId: String,
        program: String,
        tripDate: String,
        pax: Int,
        pricePerPax: Double,
        status: String,
        participantGroup: String,
        meetingPoint: String
    ) = withContext(Dispatchers.IO) {
        val bookingNo = rpcText(accessToken, "next_booking_no")
        val payload = JSONObject()
            .put("booking_no", bookingNo)
            .put("customer_id", customerId)
            .put("program_name", program.trim())
            .put("trip_date", tripDate.trim())
            .put("pax", pax)
            .put("price_per_pax", pricePerPax)
            .put("status", status)
            .put("participant_group", nullable(participantGroup))
            .put("meeting_point", nullable(meetingPoint))
            .put("created_by", userId)
        if (role == "Sales") payload.put("sales_id", userId)
        insertRowBlocking(accessToken, "bookings", payload)
        audit(accessToken, userId, "CREATE_BOOKING", "bookings", bookingNo, "Booking $bookingNo dibuat dari Android Native RC")
    }

    suspend fun insertRow(accessToken: String, table: String, values: Map<String, Any?>): ErpRow? =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
            values.forEach { (k, v) -> payload.put(k, v ?: JSONObject.NULL) }
            val body = insertRowBlocking(accessToken, table, payload)
            val arr = JSONArray(body)
            if (arr.length() == 0) null else jsonToRow(table, arr.getJSONObject(0))
        }

    suspend fun updateRow(accessToken: String, table: String, id: String, values: Map<String, Any?>): ErpRow? =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
            values.forEach { (k, v) -> payload.put(k, v ?: JSONObject.NULL) }
            val encodedId = URLEncoder.encode(id, "UTF-8")
            val body = request(
                "PATCH",
                "/rest/v1/$table?id=eq." + encodedId,
                payload.toString(),
                accessToken,
                preferReturn = true
            )
            val arr = JSONArray(body)
            if (arr.length() == 0) null else jsonToRow(table, arr.getJSONObject(0))
        }

    suspend fun updateProfile(accessToken: String, id: String, role: String? = null, active: Boolean? = null) =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
            if (role != null) payload.put("role", role)
            if (active != null) payload.put("is_active", active)
            val encodedId = URLEncoder.encode(id, "UTF-8")
            request(
                "PATCH",
                "/rest/v1/profiles?id=eq." + encodedId,
                payload.toString(),
                accessToken,
                preferReturn = true
            )
        }

    suspend fun createStaff(
        accessToken: String,
        fullName: String,
        email: String,
        phone: String,
        role: String,
        password: String
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("full_name", fullName.trim())
            .put("email", email.trim())
            .put("phone", phone.trim())
            .put("role", role)
            .put("password", password)
            .toString()
        request("POST", "/functions/v1/create-staff-user", payload, accessToken)
    }

    suspend fun resetStaffPassword(accessToken: String, userId: String, password: String): String =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("user_id", userId).put("password", password).toString()
            request("POST", "/functions/v1/reset-staff-password", payload, accessToken)
        }

    suspend fun approve(
        accessToken: String,
        approvalId: String,
        userId: String,
        approved: Boolean,
        notes: String
    ) = withContext(Dispatchers.IO) {
        updateRow(
            accessToken,
            "approvals",
            approvalId,
            mapOf(
                "status" to if (approved) "Approved" else "Rejected",
                "approved_by" to userId,
                "approved_at" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(java.util.Date()),
                "notes" to notes
            )
        )
    }

    suspend fun audit(
        accessToken: String,
        userId: String,
        action: String,
        table: String,
        recordId: String,
        message: String
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("user_id", userId)
                .put("action", action)
                .put("table_name", table)
                .put("record_id", recordId)
                .put("message", message)
            insertRowBlocking(accessToken, "audit_logs", payload)
        }
        Unit
    }

    private fun insertRowBlocking(accessToken: String, table: String, payload: JSONObject): String =
        request("POST", "/rest/v1/$table", payload.toString(), accessToken, preferReturn = true)

    private fun jsonToRow(table: String, obj: JSONObject): ErpRow {
        val map = linkedMapOf<String, String>()
        obj.keys().forEach { key ->
            val v = obj.opt(key)
            map[key] = if (v == null || v == JSONObject.NULL) "" else v.toString()
        }
        return ErpRow(table, obj.optString("id", obj.optString("code", "")), map)
    }

    private fun nullable(value: String): Any = value.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL

    private fun rpcText(accessToken: String, function: String): String {
        val body = request("POST", "/rest/v1/rpc/$function", "{}", accessToken)
        return body.trim().trim('"')
    }

    private fun request(
        method: String,
        path: String,
        body: String?,
        bearer: String?,
        preferReturn: Boolean = false
    ): String {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("apikey", key)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (preferReturn) setRequestProperty("Prefer", "return=representation")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { input -> BufferedReader(InputStreamReader(input)).readText() }.orEmpty()
        conn.disconnect()
        if (status !in 200..299) {
            val msg = runCatching {
                val j = if (text.trim().startsWith("[")) JSONArray(text).optJSONObject(0) else JSONObject(text)
                j?.optString("message", j.optString("msg", j.optString("error_description", "API gagal ($status)")))
                    ?: "API gagal ($status)"
            }.getOrDefault("API gagal ($status)")
            throw IllegalStateException(msg)
        }
        return text
    }
}
