package com.garsyanimultiusaha.gmuedutrans.sales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class SalesXenditCheckoutResult(
    val ready: Boolean,
    val checkoutUrl: String = "",
    val orderNo: String = "",
    val status: String = "",
    val expiresAt: String = "",
    val reason: String = ""
)

data class SalesCustomerDecisionResult(
    val decision: String,
    val message: String,
    val quotationNo: String,
    val quotationStatus: String,
    val revisionQuotationNo: String = "",
    val revisionStatus: String = "",
    val invoiceNo: String = "",
    val invoiceStatus: String = "",
    val invoiceTotal: Double = 0.0,
    val dpPercent: Double? = null
)

class SalesQuotationE2EApi {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun loadMyQuotations(session: SalesSession): List<SalesQuotation> = withContext(Dispatchers.IO) {
        val arr = JSONArray(requestRpc("gmu_sales_my_quotations_v3", "{}", session.accessToken))
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
                        invoiceId = x.optString("invoice_id", ""),
                        invoiceNo = x.optString("invoice_no", ""),
                        invoiceStatus = x.optString("invoice_status", ""),
                        invoiceTotal = x.optDouble("invoice_total", 0.0),
                        dpPercent = if (x.isNull("dp_percent")) null else x.optDouble("dp_percent"),
                        paymentOrderNo = x.optString("payment_order_no", ""),
                        paymentStatus = x.optString("payment_status", ""),
                        paymentCheckoutUrl = x.optString("payment_checkout_url", ""),
                        paymentExpiresAt = x.optString("payment_expires_at", "")
                    )
                )
            }
        }
    }


    suspend fun prepareXenditCheckout(
        session: SalesSession,
        invoiceId: String
    ): SalesXenditCheckoutResult = withContext(Dispatchers.IO) {
        val connection = (URL(base.trimEnd('/') + "/functions/v1/public-customer-portal").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 35_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            outputStream.use {
                it.write(
                    JSONObject()
                        .put("action", "sales_xendit_checkout")
                        .put("invoice_id", invoiceId)
                        .toString()
                        .toByteArray(Charsets.UTF_8)
                )
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = if (stream == null) "" else BufferedReader(InputStreamReader(stream)).use { it.readText() }
        connection.disconnect()
        val x = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrElse { JSONObject() }
        if (code !in 200..299) {
            val reason = x.optString("reason")
                .ifBlank { x.optString("error") }
                .ifBlank { "Xendit checkout merespons HTTP $code" }
            return@withContext SalesXenditCheckoutResult(ready = false, reason = reason)
        }
        val order = x.optJSONObject("order")
        SalesXenditCheckoutResult(
            ready = x.optBoolean("ready", x.optBoolean("ok", false)),
            checkoutUrl = x.optString("checkout_url", ""),
            orderNo = order?.optString("order_no", "") ?: "",
            status = order?.optString("status", "") ?: "",
            expiresAt = x.optString("expires_at", ""),
            reason = x.optString("reason", "")
        )
    }

    suspend fun recordCustomerDecision(
        session: SalesSession,
        quotationId: String,
        decision: String,
        note: String?
    ): SalesCustomerDecisionResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_quotation_id", quotationId)
            .put("p_decision", decision.uppercase())
            .put("p_note", note?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        val x = JSONObject(
            requestRpc(
                "gmu_sales_record_customer_quotation_decision",
                body.toString(),
                session.accessToken
            )
        )
        if (!x.optBoolean("ok", false)) {
            throw IllegalStateException(x.optString("message", "Keputusan customer gagal diproses."))
        }
        SalesCustomerDecisionResult(
            decision = x.optString("decision", decision.uppercase()),
            message = x.optString("message", ""),
            quotationNo = x.optString("quotation_no", ""),
            quotationStatus = x.optString("quotation_status", ""),
            revisionQuotationNo = x.optString("revision_quotation_no", ""),
            revisionStatus = x.optString("revision_status", ""),
            invoiceNo = x.optString("invoice_no", ""),
            invoiceStatus = x.optString("invoice_status", ""),
            invoiceTotal = x.optDouble("invoice_total", 0.0),
            dpPercent = if (x.isNull("dp_percent")) null else x.optDouble("dp_percent")
        )
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
