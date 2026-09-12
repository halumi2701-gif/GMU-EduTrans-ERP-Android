package com.garsyanimultiusaha.gmuedutrans.erp

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** ERP -> Supabase Storage -> Program/Package Master -> Web Customer media pipeline. */
object GmuMediaSync {
    const val BUCKET = "edutrans-media"
    const val MAX_FILE_BYTES = 8 * 1024 * 1024
    const val MAX_GALLERY_IMAGES = 5

    private val allowedMime = setOf("image/jpeg", "image/png", "image/webp")

    suspend fun upload(
        context: Context,
        accessToken: String,
        source: Uri,
        entityType: String,
        entityId: String
    ): String = withContext(Dispatchers.IO) {
        require(entityType in setOf("program", "package")) { "Jenis media tidak valid." }
        require(entityId.isNotBlank()) { "ID master belum tersedia." }

        val resolver = context.contentResolver
        val mime = resolver.getType(source)?.lowercase().orEmpty()
        require(mime in allowedMime) { "Format gambar harus JPG, PNG, atau WebP." }

        val bytes = resolver.openInputStream(source)?.use { input ->
            val buffer = ByteArray(MAX_FILE_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            require(total <= MAX_FILE_BYTES) { "Ukuran gambar maksimal 8 MB." }
            buffer.copyOf(total)
        } ?: error("Gambar tidak dapat dibaca.")

        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val objectPath = "$entityType/$entityId/${UUID.randomUUID()}.$ext"
        val encodedPath = objectPath.split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val endpoint = "${BuildConfig.SUPABASE_URL}/storage/v1/object/$BUCKET/$encodedPath"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Content-Type", mime)
            setRequestProperty("x-upsert", "false")
        }

        conn.outputStream.use { it.write(bytes) }
        val status = conn.responseCode
        if (status !in 200..299) {
            val detail = runCatching {
                (conn.errorStream ?: conn.inputStream)?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            conn.disconnect()
            error("Upload gambar gagal ($status). ${detail.take(180)}")
        }
        conn.inputStream?.close()
        conn.disconnect()

        "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$BUCKET/$objectPath"
    }
}

data class MasterMediaState(
    val coverImageUrl: String = "",
    val galleryUrls: List<String> = emptyList()
) {
    fun normalized(): MasterMediaState = copy(
        coverImageUrl = coverImageUrl.trim(),
        galleryUrls = galleryUrls.map { it.trim() }
            .filter { it.startsWith("https://") }
            .distinct()
            .take(GmuMediaSync.MAX_GALLERY_IMAGES)
    )
}
