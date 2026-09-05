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
        val payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()
        val auth = JSONObject(request("POST", "/auth/v1/token?grant_type=password", payload, null))
        sessionFromAuth(auth)
    }

    suspend fun refresh(refreshToken: String): SessionState = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("refresh_token", refreshToken).toString()
        val auth = JSONObject(request("POST", "/auth/v1/token?grant_type=refresh_token", payload, null))
        sessionFromAuth(auth)
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
        val encoded = URLEncoder.encode(userId, "UTF-8")
        val body = request(
            "GET",
            "/rest/v1/profiles?select=id,full_name,role,is_active&id=eq.$encoded&limit=1",
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
            active = p.optBoolean("is_active", false)
        )
    }

    suspend fun signOut(accessToken: String) = withContext(Dispatchers.IO) {
        runCatching { request("POST", "/auth/v1/logout", "{}", accessToken) }
        Unit
    }

    suspend fun getCustomers(accessToken: String): List<Customer> = withContext(Dispatchers.IO) {
        val body = request(
            "GET",
            "/rest/v1/customers?select=id,customer_code,name,customer_type,pic_name,whatsapp,email&order=created_at.desc",
            null,
            accessToken
        )
        val arr = JSONArray(body)
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(Customer(
                    id = x.getString("id"),
                    code = x.optString("customer_code", "-"),
                    name = x.optString("name", "-"),
                    type = x.optString("customer_type", "-"),
                    pic = x.optString("pic_name", ""),
                    whatsapp = x.optString("whatsapp", ""),
                    email = x.optString("email", "")
                ))
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
            .put("pic_name", pic.trim().ifBlank { JSONObject.NULL })
            .put("whatsapp", whatsapp.trim().ifBlank { JSONObject.NULL })
            .put("email", email.trim().ifBlank { JSONObject.NULL })
            .put("created_by", userId)
            .toString()
        request("POST", "/rest/v1/customers", payload, accessToken, preferReturn = true)
        audit(accessToken, userId, "CREATE_CUSTOMER", "customers", code, "Customer $name dibuat dari Android Native v0.2")
    }

    suspend fun getBookings(accessToken: String, customers: List<Customer>): List<Booking> = withContext(Dispatchers.IO) {
        val customerMap = customers.associateBy { it.id }
        val body = request(
            "GET",
            "/rest/v1/bookings?select=id,booking_no,customer_id,program_name,trip_date,pax,price_per_pax,status,participant_group,meeting_point&order=trip_date.asc",
            null,
            accessToken
        )
        val arr = JSONArray(body)
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                val customerId = x.optString("customer_id", "")
                add(Booking(
                    id = x.getString("id"),
                    bookingNo = x.optString("booking_no", "-"),
                    customerId = customerId,
                    customerName = customerMap[customerId]?.name ?: "-",
                    programName = x.optString("program_name", "-"),
                    tripDate = x.optString("trip_date", ""),
                    pax = x.optInt("pax", 0),
                    pricePerPax = x.optDouble("price_per_pax", 0.0),
                    status = x.optString("status", "Lead"),
                    participantGroup = x.optString("participant_group", ""),
                    meetingPoint = x.optString("meeting_point", "")
                ))
            }
        }
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
            .put("participant_group", participantGroup.trim().ifBlank { JSONObject.NULL })
            .put("meeting_point", meetingPoint.trim().ifBlank { JSONObject.NULL })
            .put("created_by", userId)
        if (role == "Sales") payload.put("sales_id", userId)
        request("POST", "/rest/v1/bookings", payload.toString(), accessToken, preferReturn = true)
        audit(accessToken, userId, "CREATE_BOOKING", "bookings", bookingNo, "Booking $bookingNo dibuat dari Android Native v0.2")
    }

    private fun rpcText(accessToken: String, function: String): String {
        val body = request("POST", "/rest/v1/rpc/$function", "{}", accessToken)
        return body.trim().trim('"')
    }

    private fun audit(accessToken: String, userId: String, action: String, table: String, recordId: String, message: String) {
        runCatching {
            val payload = JSONObject()
                .put("user_id", userId)
                .put("action", action)
                .put("table_name", table)
                .put("record_id", recordId)
                .put("message", message)
                .toString()
            request("POST", "/rest/v1/audit_logs", payload, accessToken)
        }
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
            readTimeout = 20000
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
