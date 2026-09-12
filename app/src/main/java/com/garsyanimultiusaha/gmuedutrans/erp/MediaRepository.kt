package com.garsyanimultiusaha.gmuedutrans.erp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MediaRepository {
    suspend fun load(
        accessToken: String,
        table: String,
        entityId: String
    ): MasterMediaState = withContext(Dispatchers.IO) {
        require(table in setOf("programs", "program_packages"))
        val id = URLEncoder.encode(entityId, "UTF-8")
        val path = "/rest/v1/$table?select=cover_image_url,gallery_urls&id=eq.$id&limit=1"
        val arr = JSONArray(request("GET", path, accessToken, null))
        if (arr.length() == 0) return@withContext MasterMediaState()
        val row = arr.optJSONObject(0) ?: JSONObject()
        MasterMediaState(
            coverImageUrl = row.optString("cover_image_url", ""),
            galleryUrls = row.optJSONArray("gallery_urls").toStringList()
        ).normalized()
    }

    suspend fun save(
        accessToken: String,
        table: String,
        entityId: String,
        media: MasterMediaState
    ) = withContext(Dispatchers.IO) {
        require(table in setOf("programs", "program_packages"))
        val clean = media.normalized()
        val payload = JSONObject()
            .put("cover_image_url", clean.coverImageUrl.ifBlank { JSONObject.NULL })
            .put("gallery_urls", JSONArray(clean.galleryUrls))
            .toString()
        val id = URLEncoder.encode(entityId, "UTF-8")
        request("PATCH", "/rest/v1/$table?id=eq.$id", accessToken, payload)
    }

    private fun request(
        method: String,
        path: String,
        accessToken: String,
        body: String?
    ): String {
        val conn = (URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (method == "PATCH") setRequestProperty("Prefer", "return=minimal")
            if (body != null) doOutput = true
        }
        if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (status !in 200..299) error("Media Master gagal ($status). ${text.take(180)}")
        return text
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val value = optString(i).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }
}
