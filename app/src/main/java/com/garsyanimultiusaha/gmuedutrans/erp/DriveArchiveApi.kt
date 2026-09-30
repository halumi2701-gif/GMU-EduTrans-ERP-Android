package com.garsyanimultiusaha.gmuedutrans.erp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class DriveArchiveFolder(
    val folderId: String,
    val folderUrl: String,
    val folderName: String
)

class DriveArchiveApi {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun ensureOrderFolder(
        accessToken: String,
        bookingNo: String,
        customerName: String,
        activityDate: String
    ): DriveArchiveFolder = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "ensure_order_folder")
            .put("entity_id", bookingNo)
            .put("booking_code", bookingNo)
            .put("customer_name", customerName)
            .put("activity_date", activityDate)

        val root = JSONObject(request(payload.toString(), accessToken))
        if (!root.optBoolean("ok", false)) {
            throw IllegalStateException(root.optString("error", "Folder Drive gagal dibuat."))
        }
        val folder = root.getJSONObject("folder")
        DriveArchiveFolder(
            folderId = folder.optString("drive_folder_id", ""),
            folderUrl = folder.optString("drive_folder_url", ""),
            folderName = folder.optString("folder_name", "")
        )
    }

    suspend fun health(accessToken: String): Boolean = withContext(Dispatchers.IO) {
        val root = JSONObject(
            request(JSONObject().put("action", "health").toString(), accessToken)
        )
        root.optBoolean("ok", false)
    }

    private fun request(body: String, accessToken: String): String {
        val conn = (URL("$base/functions/v1/gmu-drive-archive").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = BufferedReader(
            InputStreamReader(stream ?: throw IllegalStateException("Response Drive kosong"))
        ).use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) {
            val message = runCatching {
                val root = JSONObject(response)
                root.optString("error", response)
            }.getOrDefault(response)
            throw IllegalStateException(message.ifBlank { "Drive HTTP $code" })
        }
        return response
    }
}
