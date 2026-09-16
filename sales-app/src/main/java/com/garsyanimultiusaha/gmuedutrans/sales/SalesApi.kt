package com.garsyanimultiusaha.gmuedutrans.sales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId

class SalesApi {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun signIn(email: String, password: String): SalesSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()
        val auth = JSONObject(request("POST", "/auth/v1/token?grant_type=password", payload, null))
        val access = auth.getString("access_token")
        val refresh = auth.optString("refresh_token", "")
        val userId = auth.getJSONObject("user").getString("id")
        val profile = getProfileBlocking(access, userId)
        if (!profile.active) throw IllegalStateException("Akun Sales tidak aktif. Hubungi Manager GMU EduTrans.")
        if (profile.role != "Sales") throw IllegalStateException("Aplikasi ini khusus akun role Sales. Gunakan ERP untuk role ${profile.role}.")
        SalesSession(access, refresh, userId, profile)
    }

    suspend fun signOut(accessToken: String) = withContext(Dispatchers.IO) {
        runCatching { request("POST", "/auth/v1/logout", "{}", accessToken) }
        Unit
    }

    suspend fun loadDashboard(session: SalesSession): SalesDashboard = withContext(Dispatchers.IO) {
        val period = LocalDate.now(ZoneId.of("Asia/Jakarta")).withDayOfMonth(1).toString()
        val portfolio = getPortfolioSummaryBlocking(session.accessToken, period)
        val programs = getProgramBreakdownBlocking(session.accessToken, period)
        val leads = getLeadsBlocking(session.accessToken, session.userId)
        val rawBookings = getBookingsBlocking(session.accessToken, session.userId)
        val paymentStates = getBookingPaymentStatesBlocking(session.accessToken)
        val bookings = rawBookings.map { booking ->
            val payment = paymentStates[booking.id]
            if (payment == null) booking
            else booking.copy(paymentState = payment.first, paymentVerifiedAt = payment.second)
        }
        val catalog = getCatalogBlocking(session.accessToken)
        val quotations = getMyQuotationsBlocking(session.accessToken)
        SalesForecastEngine.build(portfolio, leads, bookings, programs).copy(
            catalog = catalog,
            quotations = quotations
        )
    }

    suspend fun createLead(session: SalesSession, input: NewLeadInput): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("p_customer_type", input.customerType)
            .put("p_institution_name", input.institutionName.trim())
            .put("p_pic_name", input.picName.trim())
            .put("p_whatsapp", input.whatsapp.trim())
            .put("p_trip_date", input.tripDate)
            .put("p_pax", input.pax)
            .put("p_city", input.city.trim().ifBlank { JSONObject.NULL })
            .put("p_program_id", input.programId ?: JSONObject.NULL)
            .put("p_custom_program", input.customProgram.trim().ifBlank { JSONObject.NULL })
            .put("p_budget_per_pax", input.budgetPerPax ?: JSONObject.NULL)
            .put("p_notes", input.notes.trim().ifBlank { JSONObject.NULL })
            .toString()

        val arr = JSONArray(
            request(
                "POST",
                "/rest/v1/rpc/gmu_sales_create_lead",
                payload,
                session.accessToken
            )
        )
        if (arr.length() == 0) "Lead baru" else arr.getJSONObject(0).optString("booking_code", "Lead baru")
    }

    suspend fun createQuotationDraft(
        session: SalesSession,
        leadId: String,
        packageId: String,
        notes: String?
    ): QuotationDraftResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("p_booking_request_id", leadId)
            .put("p_package_id", packageId)
            .put("p_valid_days", 7)
            .put("p_notes", notes?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .toString()
        val arr = JSONArray(
            request(
                "POST",
                "/rest/v1/rpc/gmu_sales_create_quotation_draft",
                payload,
                session.accessToken
            )
        )
        if (arr.length() == 0) throw IllegalStateException("Server tidak mengembalikan draft quotation.")
        val x = arr.getJSONObject(0)
        QuotationDraftResult(
            id = x.optString("id"),
            quotationNo = x.optString("quotation_no", "Quotation"),
            total = x.optDouble("total", 0.0),
            unitPrice = x.optDouble("unit_price", 0.0),
            validUntil = x.optString("valid_until", "")
        )
    }

    suspend fun markQuotationSent(session: SalesSession, quotationId: String): String = withContext(Dispatchers.IO) {
        val arr = JSONArray(
            request(
                "POST",
                "/rest/v1/rpc/gmu_sales_mark_quotation_sent",
                JSONObject().put("p_quotation_id", quotationId).toString(),
                session.accessToken
            )
        )
        if (arr.length() == 0) "Quotation" else arr.getJSONObject(0).optString("quotation_no", "Quotation")
    }

    suspend fun updateLead(
        session: SalesSession,
        leadId: String,
        stage: String,
        nextFollowUpAt: String?
    ) = withContext(Dispatchers.IO) {
        val probability = when (stage) {
            "NEW" -> 10
            "CONTACTED" -> 20
            "QUALIFIED" -> 40
            "QUOTATION" -> 55
            "NEGOTIATION" -> 70
            "WAITING_DP" -> 85
            "WON" -> 100
            "LOST" -> 0
            "NURTURE" -> 15
            else -> 10
        }
        val payload = JSONObject()
            .put("stage", stage)
            .put("probability_pct", probability)
            .put("updated_by", session.userId)
        if (nextFollowUpAt.isNullOrBlank()) payload.put("next_follow_up_at", JSONObject.NULL)
        else payload.put("next_follow_up_at", nextFollowUpAt)
        val encodedLead = URLEncoder.encode(leadId, "UTF-8")
        val encodedOwner = URLEncoder.encode(session.userId, "UTF-8")
        request(
            "PATCH",
            "/rest/v1/crm_lead_controls?booking_request_id=eq.$encodedLead&owner_id=eq.$encodedOwner",
            payload.toString(),
            session.accessToken,
            preferReturn = true
        )
        logActivityBlocking(session, leadId, stage, nextFollowUpAt)
    }

    private fun logActivityBlocking(session: SalesSession, leadId: String, stage: String, nextFollowUpAt: String?) {
        val payload = JSONObject()
            .put("booking_request_id", leadId)
            .put("sales_id", session.userId)
            .put("channel", "LAINNYA")
            .put("activity_type", "STATUS_UPDATE")
            .put("outcome", "Tahap lead diperbarui ke $stage")
            .put("lead_source", "Sales App")
            .put("created_by", session.userId)
        if (!nextFollowUpAt.isNullOrBlank()) payload.put("next_follow_up_at", nextFollowUpAt)
        request("POST", "/rest/v1/crm_activities", payload.toString(), session.accessToken)
    }

    private fun getProfileBlocking(accessToken: String, userId: String): SalesProfile {
        val id = URLEncoder.encode(userId, "UTF-8")
        val arr = JSONArray(
            request(
                "GET",
                "/rest/v1/profiles?select=id,full_name,role,is_active&id=eq.$id&limit=1",
                null,
                accessToken
            )
        )
        if (arr.length() == 0) throw IllegalStateException("Profil Sales tidak ditemukan.")
        val x = arr.getJSONObject(0)
        return SalesProfile(
            id = x.getString("id"),
            fullName = x.optString("full_name", "Sales GMU"),
            role = x.optString("role", ""),
            active = x.optBoolean("is_active", false)
        )
    }

    private fun getPortfolioSummaryBlocking(accessToken: String, period: String): SalesPortfolioSummary {
        val body = request(
            "POST",
            "/rest/v1/rpc/gmu_sales_portfolio_summary",
            JSONObject().put("p_period_month", period).toString(),
            accessToken
        )
        val arr = JSONArray(body)
        if (arr.length() == 0) return SalesPortfolioSummary()
        val x = arr.getJSONObject(0)
        return SalesPortfolioSummary(
            targetName = x.optString("target_name", "Target Sales GMU EduTrans"),
            paidBookings = x.optInt("paid_bookings", 0),
            paidPax = x.optInt("paid_pax", 0),
            bepPaidPax = x.optInt("bep_paid_pax", 60),
            productivePaidPax = x.optInt("productive_paid_pax", 200),
            targetPaidPax = x.optInt("target_paid_pax", 400),
            stretchPaidPax = x.optInt("stretch_paid_pax", 600),
            outstandingPaidPax = x.optInt("outstanding_paid_pax", 800),
            achievementPct = x.optDouble("achievement_pct", 0.0),
            salesRetainer = x.optDouble("sales_retainer", 600_000.0),
            salesFeePerPaidPax = x.optDouble("sales_fee_per_paid_pax", 2_500.0),
            variableSalesFee = x.optDouble("variable_sales_fee", 0.0),
            targetBonusEarned = x.optDouble("target_bonus_earned", 0.0),
            modeledSalesIncome = x.optDouble("modeled_sales_income", 600_000.0),
            achievementLevel = x.optString("achievement_level", "DI_BAWAH_BEP")
        )
    }

    private fun getProgramBreakdownBlocking(accessToken: String, period: String): List<ProgramBreakdown> {
        val body = request(
            "POST",
            "/rest/v1/rpc/gmu_sales_portfolio_program_breakdown",
            JSONObject().put("p_period_month", period).toString(),
            accessToken
        )
        val arr = JSONArray(body)
        return buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(ProgramBreakdown(x.optString("program_name", "Program GMU"), x.optInt("paid_bookings", 0), x.optInt("paid_pax", 0)))
            }
        }
    }

    private fun getCatalogBlocking(accessToken: String): List<SalesProgram> {
        val programs = JSONArray(
            request(
                "GET",
                "/rest/v1/programs?select=id,slug,name,category,short_description,min_pax,marketing_start_price,marketing_price_note&is_active=eq.true&order=sort_order.asc,name.asc",
                null,
                accessToken
            )
        )
        val packages = JSONArray(
            request(
                "GET",
                "/rest/v1/program_packages?select=id,program_id,name,description,price_per_pax,min_pax,facilities&is_active=eq.true&status=eq.ACTIVE&order=sort_order.asc,name.asc",
                null,
                accessToken
            )
        )
        val packageMap = mutableMapOf<String, MutableList<SalesPackage>>()
        for (i in 0 until packages.length()) {
            val x = packages.getJSONObject(i)
            val facilitiesJson = x.optJSONArray("facilities")
            val facilities = buildList {
                if (facilitiesJson != null) for (j in 0 until facilitiesJson.length()) add(facilitiesJson.optString(j))
            }
            val programId = x.optString("program_id")
            packageMap.getOrPut(programId) { mutableListOf() }.add(
                SalesPackage(
                    id = x.optString("id"),
                    programId = programId,
                    name = x.optString("name", "Paket"),
                    description = x.optString("description", ""),
                    pricePerPax = x.optDouble("price_per_pax", 0.0),
                    minPax = x.optInt("min_pax", 1),
                    facilities = facilities,
                    priceNote = ""
                )
            )
        }
        return buildList {
            for (i in 0 until programs.length()) {
                val x = programs.getJSONObject(i)
                val id = x.optString("id")
                add(
                    SalesProgram(
                        id = id,
                        slug = x.optString("slug", ""),
                        name = x.optString("name", "Program GMU EduTrans"),
                        category = x.optString("category", "Edukasi"),
                        description = x.optString("short_description", ""),
                        minPax = x.optInt("min_pax", 1),
                        marketingStartPrice = if (x.isNull("marketing_start_price")) null else x.optDouble("marketing_start_price"),
                        marketingPriceNote = x.optString("marketing_price_note", ""),
                        packages = packageMap[id].orEmpty()
                    )
                )
            }
        }
    }

    private fun getLeadsBlocking(accessToken: String, userId: String): List<SalesLead> {
        val uid = URLEncoder.encode(userId, "UTF-8")
        val requests = JSONArray(
            request(
                "GET",
                "/rest/v1/booking_requests?select=id,booking_code,status,institution_name,pic_name,whatsapp,city,program_id,package_id,custom_program,trip_date,pax,source,created_at,updated_at,converted_booking_id,assigned_sales&assigned_sales=eq.$uid&order=updated_at.desc",
                null,
                accessToken
            )
        )
        val controls = JSONArray(
            request(
                "GET",
                "/rest/v1/crm_lead_controls?select=booking_request_id,stage,probability_pct,next_follow_up_at,last_contact_at&owner_id=eq.$uid",
                null,
                accessToken
            )
        )
        val controlMap = mutableMapOf<String, JSONObject>()
        for (i in 0 until controls.length()) {
            val c = controls.getJSONObject(i)
            controlMap[c.optString("booking_request_id")] = c
        }
        return buildList {
            for (i in 0 until requests.length()) {
                val x = requests.getJSONObject(i)
                val c = controlMap[x.optString("id")]
                val fallbackStage = if (x.optString("converted_booking_id").isNotBlank()) "WON" else "NEW"
                add(
                    SalesLead(
                        id = x.optString("id"),
                        bookingCode = x.optString("booking_code", "-"),
                        institutionName = x.optString("institution_name", "-"),
                        picName = x.optString("pic_name", ""),
                        whatsapp = x.optString("whatsapp", ""),
                        city = x.optString("city", ""),
                        programName = x.optString("custom_program", "").ifBlank { "Program GMU EduTrans" },
                        programId = x.optString("program_id", ""),
                        packageId = x.optString("package_id", ""),
                        tripDate = x.optString("trip_date", ""),
                        pax = x.optInt("pax", 0),
                        source = x.optString("source", ""),
                        stage = c?.optString("stage", fallbackStage) ?: fallbackStage,
                        probabilityPct = c?.optDouble("probability_pct", 10.0) ?: 10.0,
                        nextFollowUpAt = c?.optString("next_follow_up_at", "") ?: "",
                        lastContactAt = c?.optString("last_contact_at", "") ?: "",
                        createdAt = x.optString("created_at", ""),
                        convertedBookingId = x.optString("converted_booking_id", "")
                    )
                )
            }
        }
    }

    private fun getBookingsBlocking(accessToken: String, userId: String): List<SalesBooking> {
        val uid = URLEncoder.encode(userId, "UTF-8")
        val arr = JSONArray(
            request(
                "GET",
                "/rest/v1/bookings?select=id,booking_no,program_name,trip_date,pax,status,customers(name)&sales_id=eq.$uid&order=trip_date.desc",
                null,
                accessToken
            )
        )
        return buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(
                    SalesBooking(
                        id = x.optString("id"),
                        bookingNo = x.optString("booking_no", "-"),
                        customerName = x.optJSONObject("customers")?.optString("name", "") ?: "",
                        programName = x.optString("program_name", "Program GMU EduTrans"),
                        tripDate = x.optString("trip_date", ""),
                        pax = x.optInt("pax", 0),
                        status = x.optString("status", "Lead")
                    )
                )
            }
        }
    }

    private fun getBookingPaymentStatesBlocking(accessToken: String): Map<String, Pair<String, String>> {
        val arr = JSONArray(
            request(
                "POST",
                "/rest/v1/rpc/gmu_sales_booking_payment_states",
                "{}",
                accessToken
            )
        )
        return buildMap {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                put(
                    x.optString("booking_id"),
                    x.optString("payment_state", "BELUM_ADA_PEMBAYARAN") to x.optString("verified_at", "")
                )
            }
        }
    }

    private fun getMyQuotationsBlocking(accessToken: String): List<SalesQuotation> {
        val arr = JSONArray(
            request(
                "POST",
                "/rest/v1/rpc/gmu_sales_my_quotations",
                "{}",
                accessToken
            )
        )
        return buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(
                    SalesQuotation(
                        id = x.optString("id"),
                        quotationNo = x.optString("quotation_no", "-"),
                        bookingRequestId = x.optString("booking_request_id", ""),
                        institutionName = x.optString("institution_name", "-"),
                        programName = x.optString("program_name", "Program GMU EduTrans"),
                        pax = x.optInt("pax", 0),
                        status = x.optString("status", "DRAFT"),
                        total = x.optDouble("total", 0.0),
                        validUntil = x.optString("valid_until", ""),
                        createdAt = x.optString("created_at", "")
                    )
                )
            }
        }
    }

    private fun request(
        method: String,
        path: String,
        body: String?,
        accessToken: String?,
        preferReturn: Boolean = false
    ): String {
        val connection = (URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer ${accessToken ?: key}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (preferReturn) setRequestProperty("Prefer", "return=representation")
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
