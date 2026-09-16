package com.garsyanimultiusaha.gmuedutrans.sales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SalesQuotationE2EApi {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun loadMyQuotations(session: SalesSession): List<SalesQuotation> = withContext(Dispatchers.IO) {
        val arr = JSONArray(requestRpc("gmu_sales_my_quotations_v2", "{}", session.accessToken))
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(
                    SalesQuotation(
                        id = x.optString("id"),
                        quotationNo = x.optString("quotation_no", "-"),
                        bookingRequestId = x.optString("booking_request_id", ""),
                        bookingCode = x.optString("booking_code", ""),
                        accessToken = x.optString("access_token", ""),
                        institutionName = x.optString("institution_name", "-"),
                        picName = x.optString("pic_name", ""),
                        whatsapp = x.optString("whatsapp", ""),
                        programName = x.optString("program_name", "Program GMU EduTrans"),
                        pax = x.optInt("pax", 0),
                        status = x.optString("status", "DRAFT"),
                        total = x.optDouble("total", 0.0),
                        validUntil = x.optString("valid_until", ""),
                        createdAt = x.optString("created_at", ""),
                        customerDecision = x.optString("customer_decision", ""),
                        decisionStatus = x.optString("decision_status", ""),
                        invoiceNo = x.optString("invoice_no", ""),
                        invoiceStatus = x.optString("invoice_status", ""),
                        invoiceTotal = x.optDouble("invoice_total", 0.0),
                        dpPercent = if (x.isNull("dp_percent")) null else x.optDouble("dp_percent")
                    )
                )
            }
        }
    }

    private fun requestRpc(name: String, body: String, accessToken: String): String {
        val connection = (URL(base.trimEnd('/') + "/rest/v1/rpc/$name").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = if (stream == null) "" else BufferedReader(InputStreamReader(stream)).use { it.readText() }
        connection.disconnect()
        if (code !in 200..299) {
            val message = runCatching {
                val obj = JSONObject(text)
                obj.optString("msg").ifBlank { obj.optString("message") }.ifBlank { obj.optString("error") }
            }.getOrDefault("")
            throw IllegalStateException(message.ifBlank { "Quotation E2E merespons HTTP $code" })
        }
        return text.ifBlank { "[]" }
    }
}
