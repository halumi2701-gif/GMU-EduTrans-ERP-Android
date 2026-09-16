package com.garsyanimultiusaha.gmuedutrans.sales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SalesV6Api {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun loadWorkspace(session: SalesSession): V6FieldWorkspace = withContext(Dispatchers.IO) {
        V6FieldWorkspace(
            attendance = getAttendance(session),
            visits = getVisits(session),
            reports = getReports(session)
        )
    }

    suspend fun checkIn(session: SalesSession, notes: String?): V6Attendance = withContext(Dispatchers.IO) {
        val body = JSONObject().put("p_notes", notes?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL).toString()
        parseAttendance(request("POST", "/rest/v1/rpc/gmu_sales_attendance_check_in", body, session.accessToken))
            ?: throw IllegalStateException("Check-in tidak menghasilkan data absensi.")
    }

    suspend fun checkOut(session: SalesSession, notes: String?): V6Attendance = withContext(Dispatchers.IO) {
        val body = JSONObject().put("p_notes", notes?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL).toString()
        parseAttendance(request("POST", "/rest/v1/rpc/gmu_sales_attendance_check_out", body, session.accessToken))
            ?: throw IllegalStateException("Check-out tidak menghasilkan data absensi.")
    }

    suspend fun visitCheckIn(session: SalesSession, leadId: String, notes: String?): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_booking_request_id", leadId)
            .put("p_notes", notes?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .toString()
        val arr = JSONArray(request("POST", "/rest/v1/rpc/gmu_sales_visit_check_in", body, session.accessToken))
        if (arr.length() == 0) throw IllegalStateException("Check-in visit gagal.")
        arr.getJSONObject(0).optString("id")
    }

    suspend fun visitCheckOut(
        session: SalesSession,
        visitId: String,
        stage: String,
        outcome: String?,
        notes: String?,
        nextFollowUpAt: String?
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_visit_id", visitId)
            .put("p_stage", stage)
            .put("p_outcome", outcome?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_notes", notes?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_next_follow_up_at", nextFollowUpAt?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .toString()
        request("POST", "/rest/v1/rpc/gmu_sales_visit_check_out", body, session.accessToken)
        Unit
    }

    suspend fun submitDailyReport(
        session: SalesSession,
        obstacles: String?,
        tomorrowPlan: String?,
        notes: String?
    ): V6DailyReport = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_obstacles", obstacles?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_tomorrow_plan", tomorrowPlan?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_notes", notes?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .toString()
        val arr = JSONArray(request("POST", "/rest/v1/rpc/gmu_sales_daily_report_upsert", body, session.accessToken))
        if (arr.length() == 0) throw IllegalStateException("Laporan harian gagal disimpan.")
        val x = arr.getJSONObject(0)
        V6DailyReport(
            id = x.optString("id"),
            reportDate = x.optString("report_date"),
            attendanceStatus = "",
            checkIn = "",
            checkOut = "",
            newLeads = x.optInt("new_leads"),
            visitsCompleted = x.optInt("visits_completed"),
            followupActivities = x.optInt("followup_activities"),
            quotationsCreated = x.optInt("quotations_created"),
            quotationsSent = x.optInt("quotations_sent"),
            wonLeads = x.optInt("won_leads"),
            obstacles = obstacles.orEmpty(),
            tomorrowPlan = tomorrowPlan.orEmpty(),
            notes = notes.orEmpty(),
            submittedAt = x.optString("submitted_at")
        )
    }

    private fun getAttendance(session: SalesSession): V6Attendance? {
        return parseAttendance(request("POST", "/rest/v1/rpc/gmu_sales_attendance_today", "{}", session.accessToken))
    }

    private fun getVisits(session: SalesSession): List<V6VisitRecord> {
        val body = JSONObject().put("p_limit", 40).toString()
        val arr = JSONArray(request("POST", "/rest/v1/rpc/gmu_sales_my_visits", body, session.accessToken))
        return buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(
                    V6VisitRecord(
                        id = x.optString("id"),
                        bookingRequestId = x.optString("booking_request_id"),
                        institutionName = x.optString("institution_name", "-"),
                        picName = x.optString("pic_name", ""),
                        visitStatus = x.optString("visit_status", ""),
                        checkInAt = x.optString("check_in_at", ""),
                        checkOutAt = x.optString("check_out_at", ""),
                        outcome = x.optString("outcome", ""),
                        notes = x.optString("notes", ""),
                        nextFollowUpAt = x.optString("next_follow_up_at", "")
                    )
                )
            }
        }
    }

    private fun getReports(session: SalesSession): List<V6DailyReport> {
        val body = JSONObject().put("p_limit", 30).toString()
        val arr = JSONArray(request("POST", "/rest/v1/rpc/gmu_sales_my_daily_reports", body, session.accessToken))
        return buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(
                    V6DailyReport(
                        id = x.optString("id"),
                        reportDate = x.optString("report_date", ""),
                        attendanceStatus = x.optString("attendance_status", ""),
                        checkIn = x.optString("check_in", ""),
                        checkOut = x.optString("check_out", ""),
                        newLeads = x.optInt("new_leads"),
                        visitsCompleted = x.optInt("visits_completed"),
                        followupActivities = x.optInt("followup_activities"),
                        quotationsCreated = x.optInt("quotations_created"),
                        quotationsSent = x.optInt("quotations_sent"),
                        wonLeads = x.optInt("won_leads"),
                        obstacles = x.optString("obstacles", ""),
                        tomorrowPlan = x.optString("tomorrow_plan", ""),
                        notes = x.optString("notes", ""),
                        submittedAt = x.optString("submitted_at", "")
                    )
                )
            }
        }
    }

    private fun parseAttendance(body: String): V6Attendance? {
        val arr = JSONArray(body)
        if (arr.length() == 0) return null
        val x = arr.getJSONObject(0)
        return V6Attendance(
            attendanceDate = x.optString("attendance_date", ""),
            status = x.optString("status", "Belum Absen"),
            checkIn = x.optString("check_in", ""),
            checkOut = x.optString("check_out", ""),
            notes = x.optString("notes", "")
        )
    }

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
