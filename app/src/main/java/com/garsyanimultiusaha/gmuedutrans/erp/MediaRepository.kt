package com.garsyanimultiusaha.gmuedutrans.erp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object MediaRepository {
    suspend fun load(
        accessToken: String,
        table: String,
        entityId: String
    ): MasterMediaState = withContext(Dispatchers.IO) {
        require(table in setOf("programs", "program_packages"))
        val root = request(
            accessToken,
            JSONObject()
                .put("action", "load")
                .put("table", table)
                .put("entity_id", entityId)
        )
        val media = root.optJSONObject("media") ?: JSONObject()
        MasterMediaState(
            coverImageUrl = media.optString("cover_image_url", ""),
            galleryUrls = media.optJSONArray("gallery_urls").toStringList()
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
        request(
            accessToken,
            JSONObject()
                .put("action", "save")
                .put("table", table)
                .put("entity_id", entityId)
                .put(
                    "media",
                    JSONObject()
                        .put("cover_image_url", clean.coverImageUrl.ifBlank { JSONObject.NULL })
                        .put("gallery_urls", JSONArray(clean.galleryUrls))
                )
        )
        Unit
    }

    private fun request(accessToken: String, payload: JSONObject): JSONObject {
        val endpoint = BuildConfig.SUPABASE_URL + "/functions/v1/internal-media-master"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (status !in 200..299) {
            val detail = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
            error(detail.ifBlank { "Media Master gagal ($status). ${text.take(180)}" })
        }
        return JSONObject(text.ifBlank { "{}" })
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
