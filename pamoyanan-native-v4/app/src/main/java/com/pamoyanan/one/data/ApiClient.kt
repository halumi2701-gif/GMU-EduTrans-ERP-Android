package com.pamoyanan.one.data

import android.content.Context
import com.pamoyanan.one.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, override val message: String) : Exception(message)

class ApiClient(
    private val context: Context,
    private val session: SecureSession
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .build()
    private val refreshMutex = Mutex()

    private fun requestBuilder(path: String, token: String? = null): Request.Builder {
        val url = if (path.startsWith("http")) path else BuildConfig.API_BASE_URL.trimEnd('/') + "/" + path.trimStart('/')
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "PAMOYAN-ONE-Native/6.7.1-v4")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer " + token)
        return builder
    }

    private fun errorMessage(body: String, status: Int): String {
        return try {
            val obj = JSONObject(body)
            obj.optString("detail").ifBlank { obj.optString("error").ifBlank { "HTTP " + status } }
        } catch (_: Exception) {
            if (body.isNotBlank()) body.take(180) else "HTTP " + status
        }
    }

    private suspend fun refresh(): Boolean = refreshMutex.withLock {
        if (session.refreshToken.isBlank()) return@withLock false
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("refresh_token", session.refreshToken).toString().toRequestBody(jsonMedia)
            val req = requestBuilder("/auth/refresh").post(body).build()
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    session.clear()
                    return@withContext false
                }
                val obj = JSONObject(raw)
                session.accessToken = obj.optString("access_token")
                session.refreshToken = obj.optString("refresh_token")
                session.accessToken.isNotBlank()
            }
        }
    }

    private suspend fun raw(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        authorized: Boolean = true,
        retry: Boolean = true
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val token = if (authorized) session.accessToken else ""
        val builder = requestBuilder(path, token)
        val requestBody = body?.toString()?.toRequestBody(jsonMedia)
        when (method.uppercase()) {
            "POST" -> builder.post(requestBody ?: ByteArray(0).toRequestBody(null))
            "PATCH" -> builder.patch(requestBody ?: ByteArray(0).toRequestBody(null))
            "PUT" -> builder.put(requestBody ?: ByteArray(0).toRequestBody(null))
            "DELETE" -> if (requestBody != null) builder.delete(requestBody) else builder.delete()
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (res.code == 401 && authorized && retry) {
                if (refresh()) return@withContext raw(path, method, body, authorized, false)
            }
            if (!res.isSuccessful) throw ApiException(res.code, errorMessage(text, res.code))
            res.code to text
        }
    }

    suspend fun login(username: String, password: String): JSONObject {
        val body = JSONObject().put("username", username).put("password", password)
        val (_, text) = raw("/auth/login", "POST", body, false)
        return JSONObject(text)
    }

    suspend fun me(): JSONObject = JSONObject(raw("/auth/me").second)

    suspend fun setInitialPassword(password: String): JSONObject {
        val body = JSONObject().put("new_password", password)
        return JSONObject(raw("/auth/set-initial-password", "POST", body).second)
    }

    suspend fun logout() {
        val refresh = session.refreshToken
        if (refresh.isBlank()) return
        try {
            raw("/auth/logout", "POST", JSONObject().put("refresh_token", refresh))
        } catch (_: Exception) {}
    }

    suspend fun home(): JSONObject = JSONObject(raw("/superapp/home").second)
    suspend fun digitalId(): JSONObject = JSONObject(raw("/digital-id/me").second)
    suspend fun rotateMyQr(): JSONObject = JSONObject(raw("/digital-id/me/rotate-qr", "POST", JSONObject()).second)
    suspend fun residentDigitalId(residentId: String): JSONObject = JSONObject(raw("/digital-id/residents/" + residentId).second)
    suspend fun rotateResidentDigitalId(residentId: String): JSONObject = JSONObject(raw("/digital-id/residents/" + residentId + "/rotate-qr", "POST", JSONObject()).second)
    suspend fun revokeResidentDigitalId(residentId: String): JSONObject = JSONObject(raw("/digital-id/residents/" + residentId + "/revoke", "POST", JSONObject()).second)
    suspend fun letters(): JSONArray = JSONArray(raw("/letters").second)
    suspend fun templates(): JSONArray = JSONArray(raw("/letters/templates").second)

    suspend fun createLetter(
        templateCode: String,
        purpose: String,
        formData: JSONObject,
        requirements: JSONArray,
        priority: String,
        urgentReason: String?
    ): JSONObject {
        val body = JSONObject()
            .put("template_code", templateCode)
            .put("purpose", purpose)
            .put("form_data", formData)
            .put("requirements_confirmed", requirements)
            .put("priority", priority)
        if (!urgentReason.isNullOrBlank()) body.put("urgent_reason", urgentReason)
        return JSONObject(raw("/letters", "POST", body).second)
    }

    suspend fun letterTimeline(id: String): JSONObject = JSONObject(raw("/letters/" + id + "/timeline").second)
    suspend fun letterAction(id: String, action: String): JSONObject =
        JSONObject(raw("/letters/" + id + "/" + action, "POST", JSONObject()).second)

    suspend fun complaints(): JSONArray = JSONArray(raw("/superapp/complaints").second)

    suspend fun createComplaint(
        category: String,
        location: String,
        bodyText: String,
        priority: String,
        rt: String?
    ): JSONObject {
        val body = JSONObject()
            .put("category", category)
            .put("location_text", location)
            .put("body", bodyText)
            .put("priority", priority)
        if (!rt.isNullOrBlank()) body.put("rt", rt)
        return JSONObject(raw("/complaints", "POST", body).second)
    }

    suspend fun complaintAction(id: String, action: String, note: String?): JSONObject {
        val body = JSONObject().put("action", action)
        if (!note.isNullOrBlank()) body.put("note", note)
        return JSONObject(raw("/superapp/complaints/" + id + "/action", "POST", body).second)
    }

    suspend fun residents(query: String = ""): JSONArray {
        val q = if (query.isBlank()) "" else "&q=" + java.net.URLEncoder.encode(query, "UTF-8")
        return JSONArray(raw("/residents?limit=2000" + q).second)
    }

    suspend fun inbox(): JSONObject = JSONObject(raw("/operations/inbox").second)
    suspend fun markAllNotificationsRead(): JSONObject =
        JSONObject(raw("/operations/notifications/read-all", "POST", JSONObject()).second)

    suspend fun modernDashboard(): JSONObject = JSONObject(raw("/superapp/modern-dashboard").second)
    suspend fun globalSearch(query: String): JSONObject =
        JSONObject(raw("/superapp/global-search?q=" + java.net.URLEncoder.encode(query, "UTF-8")).second)

    suspend fun generic(path: String): Any {
        val text = raw(path).second.trim()
        return if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
    }

    suspend fun downloadLetterPdf(letterId: String): File = withContext(Dispatchers.IO) {
        val path = "/letters/" + letterId + "/pdf"
        var token = session.accessToken
        var request = requestBuilder(path, token).get().build()
        var response = client.newCall(request).execute()
        if (response.code == 401 && refresh()) {
            response.close()
            token = session.accessToken
            request = requestBuilder(path, token).get().build()
            response = client.newCall(request).execute()
        }
        response.use { res ->
            if (!res.isSuccessful) {
                val text = res.body?.string().orEmpty()
                throw ApiException(res.code, errorMessage(text, res.code))
            }
            val dir = File(context.cacheDir, "letters").apply { mkdirs() }
            val file = File(dir, "surat-" + letterId + ".pdf")
            file.outputStream().use { out ->
                res.body?.byteStream()?.copyTo(out)
            }
            file
        }
    }
}
