package com.garsyanimultiusaha.gmuedutrans.sales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SalesV61Api {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun loadWorkspace(session: SalesSession): V61ConnectedWorkspace = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        fun <T> safe(label: String, default: T, block: () -> T): T = try {
            block()
        } catch (e: Exception) {
            errors += "$label: ${e.message ?: "gagal terhubung"}"
            default
        }
        V61ConnectedWorkspace(
            handovers = safe("Handover", emptyList()) { getHandovers(session) },
            notifications = safe("Notification", emptyList()) { getNotifications(session) },
            learningModules = safe("Training", emptyList()) { getLearning(session) },
            resources = safe("Resources", emptyList()) { getResources(session) },
            documents = safe("Documents", emptyList()) { getDocuments(session) },
            repeatOpportunities = safe("Repeat Order", emptyList()) { getRepeat(session) },
            releasePolicy = safe("App Update", V61ReleasePolicy()) { getReleasePolicy(session) },
            integrationErrors = errors
        )
    }

    suspend fun updateLead(
        session: SalesSession,
        leadId: String,
        stage: String,
        nextFollowUpAt: String?,
        lostReason: String?
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_booking_request_id", leadId)
            .put("p_stage", stage)
            .put("p_next_follow_up_at", nextFollowUpAt?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_lost_reason", lostReason?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .toString()
        request("POST", "/rest/v1/rpc/gmu_sales_update_lead", body, session.accessToken)
        Unit
    }

    suspend fun markNotificationRead(session: SalesSession, notificationKey: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("p_notification_key", notificationKey).toString()
        request("POST", "/rest/v1/rpc/gmu_sales_mark_notification_read", body, session.accessToken)
        Unit
    }

    suspend fun markLearningComplete(session: SalesSession, moduleId: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("p_module_id", moduleId).toString()
        request("POST", "/rest/v1/rpc/gmu_sales_mark_learning_complete", body, session.accessToken)
        Unit
    }

    suspend fun submitFeedback(
        session: SalesSession,
        bookingId: String,
        score: Int,
        feedback: String?,
        testimonialConsent: Boolean,
        respondentName: String?
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_booking_id", bookingId)
            .put("p_overall_score", score)
            .put("p_feedback", feedback?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_testimonial_consent", testimonialConsent)
            .put("p_respondent_name", respondentName?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .toString()
        request("POST", "/rest/v1/rpc/gmu_sales_submit_feedback", body, session.accessToken)
        Unit
    }

    private fun getHandovers(session: SalesSession): List<V61Handover> {
        val arr = rpc(session, "gmu_sales_my_handovers", JSONObject())
        return buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(V61Handover(
                    id = x.optString("id"),
                    bookingRequestId = x.optString("booking_request_id"),
                    bookingId = x.optString("booking_id"),
                    institutionName = x.optString("institution_name", "-"),
                    bookingNo = x.optString("booking_no", "-"),
                    handoverStatus = x.optString("handover_status", "READY_FOR_OPS"),
                    bookingStatus = x.optString("booking_status", ""),
                    requestStatus = x.optString("request_status", ""),
                    tripStatus = x.optString("trip_status", ""),
                    createdAt = x.optString("created_at", ""),
                    updatedAt = x.optString("updated_at", "")
                ))
            }
        }
    }

    private fun getNotifications(session: SalesSession): List<V61ActionNotice> {
        val arr = rpc(session, "gmu_sales_action_feed", JSONObject())
        return buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(V61ActionNotice(
                    key = x.optString("notification_key"),
                    kind = x.optString("kind", "INFO"),
                    severity = x.optString("severity", "MEDIUM"),
                    title = x.optString("title", "Notifikasi"),
                    message = x.optString("message", ""),
                    targetId = x.optString("target_id", ""),
                    isRead = x.optBoolean("is_read", false),
                    occurredAt = x.optString("occurred_at", "")
                ))
            }
        }
    }

    private fun getLearning(session: SalesSession): List<V61LearningModule> {
        val arr = rpc(session, "gmu_sales_learning_modules", JSONObject())
        return buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(V61LearningModule(
                    id = x.optString("id"),
                    title = x.optString("title", "Modul Sales"),
                    summary = x.optString("summary", ""),
                    content = x.optString("content", ""),
                    sortOrder = x.optInt("sort_order", 100),
                    completedAt = nullableString(x, "completed_at")
                ))
            }
        }
    }

    private fun getResources(session: SalesSession): List<V61Resource> {
        val arr = rpc(session, "gmu_sales_resources", JSONObject())
        return buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(V61Resource(
                    id = x.optString("id"),
                    resourceType = x.optString("resource_type", "TEXT"),
                    title = x.optString("title", "Resource"),
                    description = x.optString("description", ""),
                    url = nullableString(x, "url"),
                    shareText = nullableString(x, "share_text"),
                    sortOrder = x.optInt("sort_order", 100)
                ))
            }
        }
    }

    private fun getDocuments(session: SalesSession): List<V61Document> {
        val arr = rpc(session, "gmu_sales_my_documents", JSONObject())
        return buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(V61Document(
                    id = x.optString("id"),
                    bookingId = x.optString("booking_id", ""),
                    bookingNo = x.optString("booking_no", "-"),
                    title = x.optString("title", "Dokumen"),
                    documentType = x.optString("document_type", ""),
                    status = x.optString("status", ""),
                    fileUrl = x.optString("file_url", ""),
                    fileName = x.optString("file_name", ""),
                    generatedAt = x.optString("generated_at", "")
                ))
            }
        }
    }

    private fun getRepeat(session: SalesSession): List<V61RepeatOpportunity> {
        val arr = rpc(session, "gmu_sales_my_repeat_opportunities", JSONObject())
        return buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(V61RepeatOpportunity(
                    bookingId = x.optString("booking_id"),
                    bookingNo = x.optString("booking_no", "-"),
                    bookingRequestId = nullableString(x, "booking_request_id"),
                    customerName = x.optString("customer_name", "-"),
                    picName = x.optString("pic_name", ""),
                    whatsapp = x.optString("whatsapp", ""),
                    programName = x.optString("program_name", ""),
                    tripDate = x.optString("trip_date", ""),
                    pax = x.optInt("pax", 0),
                    bookingStatus = x.optString("booking_status", ""),
                    overallScore = if (x.isNull("overall_score")) null else x.optInt("overall_score"),
                    feedback = x.optString("feedback", ""),
                    testimonialConsent = x.optBoolean("testimonial_consent", false),
                    feedbackSubmittedAt = nullableString(x, "feedback_submitted_at")
                ))
            }
        }
    }

    private fun getReleasePolicy(session: SalesSession): V61ReleasePolicy {
        val arr = rpc(session, "gmu_sales_release_policy", JSONObject().put("p_version_code", BuildConfig.VERSION_CODE))
        if (arr.length() == 0) return V61ReleasePolicy()
        val x = arr.getJSONObject(0)
        return V61ReleasePolicy(
            latestVersionCode = x.optInt("latest_version_code", BuildConfig.VERSION_CODE),
            latestVersionName = x.optString("latest_version_name", BuildConfig.VERSION_NAME),
            minSupportedCode = x.optInt("min_supported_code", BuildConfig.VERSION_CODE),
            releaseNotes = x.optString("release_notes", ""),
            releaseUrl = x.optString("release_url", ""),
            updateAvailable = x.optBoolean("update_available", false),
            updateRequired = x.optBoolean("update_required", false)
        )
    }

    private fun rpc(session: SalesSession, name: String, body: JSONObject): JSONArray =
        JSONArray(request("POST", "/rest/v1/rpc/$name", body.toString(), session.accessToken))

    private fun nullableString(x: JSONObject, key: String): String =
        if (x.isNull(key)) "" else x.optString(key, "")

    private fun request(method: String, path: String, body: String?, accessToken: String?): String {
        val connection = (URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer ${accessToken ?: key}")
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
                obj.optString("msg").ifBlank { obj.optString("message") }.ifBlank { obj.optString("error") }
            }.getOrDefault("")
            throw IllegalStateException(message.ifBlank { "Server GMU merespons HTTP $code" })
        }
        return text.ifBlank { "[]" }
    }
}
