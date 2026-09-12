package com.garsyanimultiusaha.gmuedutrans.erp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight catalog used only by the Media tab.
 * It intentionally loads public-content master fields only and does not load HPP, margin,
 * internal fees, pricing policy, or other financial data.
 */
data class MediaProgramItem(
    val id: String,
    val name: String,
    val category: String,
    val active: Boolean,
    val coverImageUrl: String = "",
    val galleryCount: Int = 0
)

data class MediaPackageItem(
    val id: String,
    val packageCode: String,
    val programId: String,
    val name: String,
    val status: String,
    val active: Boolean,
    val coverImageUrl: String = "",
    val galleryCount: Int = 0
)

data class MediaMasterCatalog(
    val programs: List<MediaProgramItem> = emptyList(),
    val packages: List<MediaPackageItem> = emptyList()
)

object MediaMasterCatalogRepository {
    suspend fun load(accessToken: String): MediaMasterCatalog = withContext(Dispatchers.IO) {
        val root = postCatalog(accessToken)
        val programArray = root.optJSONArray("programs") ?: JSONArray()
        val packageArray = root.optJSONArray("packages") ?: JSONArray()

        val programs = buildList {
            for (i in 0 until programArray.length()) {
                val x = programArray.optJSONObject(i) ?: continue
                add(
                    MediaProgramItem(
                        id = x.optString("id", ""),
                        name = x.optString("name", "Program GMU EduTrans"),
                        category = x.optString("category", "Program Edukasi"),
                        active = x.optBoolean("is_active", false),
                        coverImageUrl = x.optString("cover_image_url", "").takeIf { it != "null" }.orEmpty(),
                        galleryCount = x.optJSONArray("gallery_urls")?.length()?.coerceIn(0, 5) ?: 0
                    )
                )
            }
        }

        val packages = buildList {
            for (i in 0 until packageArray.length()) {
                val x = packageArray.optJSONObject(i) ?: continue
                add(
                    MediaPackageItem(
                        id = x.optString("id", ""),
                        packageCode = x.optString("package_code", ""),
                        programId = x.optString("program_id", ""),
                        name = x.optString("name", "Paket GMU EduTrans"),
                        status = x.optString("status", "DRAFT"),
                        active = x.optBoolean("is_active", false),
                        coverImageUrl = x.optString("cover_image_url", "").takeIf { it != "null" }.orEmpty(),
                        galleryCount = x.optJSONArray("gallery_urls")?.length()?.coerceIn(0, 5) ?: 0
                    )
                )
            }
        }

        MediaMasterCatalog(programs = programs, packages = packages)
    }

    private fun postCatalog(accessToken: String): JSONObject {
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
        val payload = JSONObject().put("action", "catalog").toString()
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (status !in 200..299) {
            val detail = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
            error(detail.ifBlank { "Media Master gagal dimuat ($status). ${body.take(180)}" })
        }
        return JSONObject(body.ifBlank { "{}" })
    }
}
