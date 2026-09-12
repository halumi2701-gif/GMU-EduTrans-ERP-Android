package com.garsyanimultiusaha.gmuedutrans.erp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    val active: Boolean
)

data class MediaPackageItem(
    val id: String,
    val packageCode: String,
    val programId: String,
    val name: String,
    val status: String,
    val active: Boolean
)

data class MediaMasterCatalog(
    val programs: List<MediaProgramItem> = emptyList(),
    val packages: List<MediaPackageItem> = emptyList()
)

object MediaMasterCatalogRepository {
    suspend fun load(accessToken: String): MediaMasterCatalog = withContext(Dispatchers.IO) {
        val programs = JSONArray(
            get(
                "/rest/v1/programs?select=id,name,category,is_active&is_active=eq.true&order=sort_order.asc",
                accessToken
            )
        ).let { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val x = arr.optJSONObject(i) ?: continue
                    add(
                        MediaProgramItem(
                            id = x.optString("id", ""),
                            name = x.optString("name", "Program GMU EduTrans"),
                            category = x.optString("category", "Program Edukasi"),
                            active = x.optBoolean("is_active", false)
                        )
                    )
                }
            }
        }

        val packages = JSONArray(
            get(
                "/rest/v1/program_packages?select=id,package_code,program_id,name,status,is_active&order=sort_order.asc",
                accessToken
            )
        ).let { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val x = arr.optJSONObject(i) ?: continue
                    add(
                        MediaPackageItem(
                            id = x.optString("id", ""),
                            packageCode = x.optString("package_code", ""),
                            programId = x.optString("program_id", ""),
                            name = x.optString("name", "Paket GMU EduTrans"),
                            status = x.optString("status", "DRAFT"),
                            active = x.optBoolean("is_active", false)
                        )
                    )
                }
            }
        }

        MediaMasterCatalog(programs = programs, packages = packages)
    }

    private fun get(path: String, accessToken: String): String {
        val conn = (URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (status !in 200..299) error("Media Master gagal dimuat ($status). ${body.take(180)}")
        return body
    }
}
