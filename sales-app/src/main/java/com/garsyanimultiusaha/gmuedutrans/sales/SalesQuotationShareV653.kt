package com.garsyanimultiusaha.gmuedutrans.sales

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

internal data class PreparedQuotationPdf(
    val quotationNo: String,
    val fileUrl: String,
    val fileName: String
)

internal class SalesQuotationShareApi {
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun preparePdf(session: SalesSession, quotationId: String): PreparedQuotationPdf = withContext(Dispatchers.IO) {
        val root = JSONObject(
            request(
                method = "POST",
                path = "/functions/v1/internal-commercial-pdf",
                body = JSONObject()
                    .put("action", "quotation")
                    .put("id", quotationId)
                    .put("mode", "auto")
                    .toString(),
                accessToken = session.accessToken
            )
        )
        if (!root.optBoolean("ok", false)) throw IllegalStateException("PDF quotation belum siap.")
        val doc = root.optJSONObject("document") ?: throw IllegalStateException("Dokumen quotation tidak ditemukan.")
        val fileUrl = doc.optString("file_url", "")
        if (fileUrl.isBlank()) throw IllegalStateException("Link PDF quotation belum tersedia.")
        PreparedQuotationPdf(
            quotationNo = root.optString("quotation_no", "Quotation"),
            fileUrl = fileUrl,
            fileName = doc.optString("file_name", "quotation.pdf").ifBlank { "quotation.pdf" }
        )
    }

    suspend fun launchWhatsAppWithPdf(
        context: Context,
        quotation: SalesQuotation,
        prepared: PreparedQuotationPdf
    ) {
        val pdfFile = downloadToCache(context, prepared)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val phone = normalizeWhatsapp(quotation.whatsapp)
        if (phone.isBlank()) throw IllegalStateException("Nomor WhatsApp customer belum tersedia.")

        val message = buildString {
            append("Halo ")
            append(quotation.picName.ifBlank { "Bapak/Ibu" })
            append(", berikut quotation resmi GMU EduTrans ")
            append(quotation.quotationNo)
            append(" untuk ")
            append(quotation.programName)
            append(", ")
            append(quotation.pax)
            append(" peserta, total ")
            append(formatRupiah(quotation.total))
            if (quotation.validUntil.isNotBlank()) {
                append(". Berlaku sampai ")
                append(quotation.validUntil)
            }
            append(". PDF resmi terlampir. Terima kasih.")
        }

        withContext(Dispatchers.Main) {
            val launched = launchPackage(context, "com.whatsapp", phone, message, uri)
                || launchPackage(context, "com.whatsapp.w4b", phone, message, uri)
            if (!launched) {
                throw IllegalStateException("WhatsApp / WhatsApp Business tidak ditemukan di perangkat.")
            }
        }
    }

    suspend fun markSent(session: SalesSession, quotationId: String): String = withContext(Dispatchers.IO) {
        val arr = JSONArray(
            request(
                method = "POST",
                path = "/rest/v1/rpc/gmu_sales_mark_quotation_sent",
                body = JSONObject().put("p_quotation_id", quotationId).toString(),
                accessToken = session.accessToken
            )
        )
        if (arr.length() == 0) "Quotation"
        else arr.getJSONObject(0).optString("quotation_no", "Quotation")
    }

    private fun downloadToCache(context: Context, prepared: PreparedQuotationPdf): File {
        val safeName = prepared.fileName
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .ifBlank { "quotation.pdf" }
            .let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
        val dir = File(context.cacheDir, "quotations").apply { mkdirs() }
        val target = File(dir, safeName)

        val connection = (URL(prepared.fileUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IllegalStateException("PDF quotation gagal diunduh (HTTP $code).")
        }
        connection.inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        connection.disconnect()
        if (!target.exists() || target.length() <= 0L) {
            throw IllegalStateException("File PDF quotation kosong.")
        }
        return target
    }

    private fun launchPackage(
        context: Context,
        packageName: String,
        phone: String,
        message: String,
        uri: android.net.Uri
    ): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            setPackage(packageName)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra("jid", "$phone@s.whatsapp.net")
            clipData = ClipData.newRawUri("GMU EduTrans Quotation", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun normalizeWhatsapp(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.startsWith("62") -> digits
            digits.startsWith("0") -> "62${digits.drop(1)}"
            else -> digits
        }
    }

    private fun formatRupiah(value: Double): String =
        "Rp" + "%,.0f".format(java.util.Locale.US, value).replace(",", ".")
    
    private fun request(method: String, path: String, body: String?, accessToken: String): String {
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 35_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = if (stream == null) "" else BufferedReader(InputStreamReader(stream)).use { it.readText() }
        connection.disconnect()
        if (code !in 200..299) {
            val message = runCatching {
                val obj = JSONObject(text)
                obj.optString("message")
                    .ifBlank { obj.optString("msg") }
                    .ifBlank { obj.optString("error") }
            }.getOrDefault("")
            throw IllegalStateException(message.ifBlank { "Server GMU merespons HTTP $code" })
        }
        return text.ifBlank { "{}" }
    }
}
