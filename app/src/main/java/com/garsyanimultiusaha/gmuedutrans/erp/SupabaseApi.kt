package com.garsyanimultiusaha.gmuedutrans.erp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SupabaseApi {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun signIn(email: String, password: String): SessionState = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("email", email.trim()).put("password", password).toString()
        sessionFromAuth(JSONObject(request("POST", "/auth/v1/token?grant_type=password", payload, null)))
    }

    suspend fun refresh(refreshToken: String): SessionState = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("refresh_token", refreshToken).toString()
        sessionFromAuth(JSONObject(request("POST", "/auth/v1/token?grant_type=refresh_token", payload, null)))
    }

    private fun sessionFromAuth(auth: JSONObject): SessionState {
        val accessToken = auth.getString("access_token")
        val refreshToken = auth.optString("refresh_token", "")
        val userId = auth.getJSONObject("user").getString("id")
        val profile = fetchProfileBlocking(accessToken, userId)
        if (!profile.active) throw IllegalStateException("Akun staf tidak aktif. Hubungi Owner.")
        return SessionState(accessToken, refreshToken, userId, profile)
    }

    suspend fun fetchProfile(accessToken: String, userId: String): StaffProfile =
        withContext(Dispatchers.IO) { fetchProfileBlocking(accessToken, userId) }

    private fun fetchProfileBlocking(accessToken: String, userId: String): StaffProfile {
        val encodedId = URLEncoder.encode(userId, "UTF-8")
        val body = request(
            "GET",
            "/rest/v1/profiles?select=id,full_name,role,is_active,phone&id=eq." + encodedId + "&limit=1",
            null,
            accessToken
        )
        val arr = JSONArray(body)
        if (arr.length() == 0) throw IllegalStateException("Profil ERP tidak ditemukan.")
        val p = arr.getJSONObject(0)
        return StaffProfile(
            id = p.getString("id"),
            fullName = p.optString("full_name", "Staff GMU"),
            role = p.optString("role", "Sales"),
            active = p.optBoolean("is_active", false),
            phone = p.optString("phone", "")
        )
    }

    suspend fun signOut(accessToken: String) = withContext(Dispatchers.IO) {
        runCatching { request("POST", "/auth/v1/logout", "{}", accessToken) }
        Unit
    }

    suspend fun getManagementDashboard(accessToken: String): ManagementDashboard = withContext(Dispatchers.IO) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val month = today.substring(0, 7)
        val payload = JSONObject()
            .put("action", "dashboard")
            .put("start_date", month + "-01")
            .put("end_date", today)
            .put("as_of", today)
            .put("horizon_days", 30)
            .toString()
        val body = request("POST", "/functions/v1/internal-management-control", payload, accessToken)
        val root = JSONObject(body)
        val result = root.optJSONObject("result")
            ?: throw IllegalStateException(root.optString("error", "Management Control gagal dimuat."))
        ManagementDashboard.fromJson(result)
    }


    suspend fun getPlanningDashboard(accessToken: String): PlanningDashboard = withContext(Dispatchers.IO) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val month = today.substring(0, 7)
        val payload = JSONObject()
            .put("action", "dashboard")
            .put("month", month + "-01")
            .put("start_date", month + "-01")
            .put("end_date", today)
            .toString()
        val body = request("POST", "/functions/v1/internal-planning-control", payload, accessToken)
        PlanningDashboard.fromJson(JSONObject(body))
    }

    suspend fun savePlanningTarget(
        accessToken: String,
        targetId: String?,
        periodMonth: String,
        scopeType: String,
        salesId: String?,
        programId: String?,
        targetRevenue: Double,
        targetBookings: Int,
        targetPax: Int,
        targetProfit: Double,
        targetMarginPct: Double,
        targetCashIn: Double,
        notes: String
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "save_target")
            .put("target_id", targetId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("period_month", periodMonth)
            .put("scope_type", scopeType)
            .put("sales_id", salesId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("program_id", programId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("target_revenue", targetRevenue)
            .put("target_bookings", targetBookings)
            .put("target_pax", targetPax)
            .put("target_profit", targetProfit)
            .put("target_margin_pct", targetMarginPct)
            .put("target_cash_in", targetCashIn)
            .put("notes", notes)
            .toString()
        request("POST", "/functions/v1/internal-planning-control", payload, accessToken)
    }

    suspend fun savePlanningBudget(
        accessToken: String,
        budgetId: String?,
        periodMonth: String,
        budgetType: String,
        category: String,
        programId: String?,
        amount: Double,
        notes: String
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "save_budget")
            .put("budget_id", budgetId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("period_month", periodMonth)
            .put("budget_type", budgetType)
            .put("category", category)
            .put("program_id", programId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("amount", amount)
            .put("notes", notes)
            .toString()
        request("POST", "/functions/v1/internal-planning-control", payload, accessToken)
    }

    suspend fun deletePlanningBudget(accessToken: String, budgetId: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "delete_budget")
            .put("budget_id", budgetId)
            .toString()
        request("POST", "/functions/v1/internal-planning-control", payload, accessToken)
    }

    suspend fun setPlanningPipelineWeight(
        accessToken: String,
        status: String,
        probabilityPct: Double,
        notes: String
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "set_pipeline_weight")
            .put("booking_status", status)
            .put("win_probability_pct", probabilityPct)
            .put("notes", notes)
            .toString()
        request("POST", "/functions/v1/internal-planning-control", payload, accessToken)
    }

    suspend fun runPlanningScenario(
        accessToken: String,
        bookingId: String,
        pax: Int?,
        pricePerPax: Double?,
        vendorIncreasePct: Double,
        transportIncreasePct: Double,
        discountPct: Double,
        variableCostSharePct: Double
    ): PlanningScenarioResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "scenario")
            .put("booking_id", bookingId)
            .put("pax", pax ?: JSONObject.NULL)
            .put("price_per_pax", pricePerPax ?: JSONObject.NULL)
            .put("vendor_increase_pct", vendorIncreasePct)
            .put("transport_increase_pct", transportIncreasePct)
            .put("discount_pct", discountPct)
            .put("variable_cost_share_pct", variableCostSharePct)
            .toString()
        val body = JSONObject(request("POST", "/functions/v1/internal-planning-control", payload, accessToken))
        val result = body.optJSONObject("result")
            ?: throw IllegalStateException(body.optString("error", "Scenario Simulator gagal."))
        PlanningScenarioResult.fromJson(result)
    }

    suspend fun getCustomers(accessToken: String): List<Customer> = withContext(Dispatchers.IO) {
        val arr = JSONArray(request("GET", "/rest/v1/customers?select=*&order=created_at.desc", null, accessToken))
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(
                    Customer(
                        id = x.getString("id"),
                        code = x.optString("customer_code", "-"),
                        name = x.optString("name", "-"),
                        type = x.optString("customer_type", "-"),
                        pic = x.optString("pic_name", ""),
                        whatsapp = x.optString("whatsapp", ""),
                        email = x.optString("email", ""),
                        address = x.optString("address", ""),
                        notes = x.optString("notes", "")
                    )
                )
            }
        }
    }

    suspend fun getBookings(accessToken: String, customers: List<Customer>): List<Booking> = withContext(Dispatchers.IO) {
        val customerMap = customers.associateBy { it.id }
        val arr = JSONArray(request("GET", "/rest/v1/bookings?select=*&order=trip_date.asc", null, accessToken))
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                val customerId = x.optString("customer_id", "")
                add(
                    Booking(
                        id = x.getString("id"),
                        bookingNo = x.optString("booking_no", "-"),
                        customerId = customerId,
                        customerName = customerMap[customerId]?.name ?: "-",
                        salesId = x.optString("sales_id", ""),
                        programName = x.optString("program_name", "-"),
                        tripDate = x.optString("trip_date", ""),
                        pax = x.optInt("pax", 0),
                        pricePerPax = x.optDouble("price_per_pax", 0.0),
                        status = x.optString("status", "Lead"),
                        participantGroup = x.optString("participant_group", ""),
                        meetingPoint = x.optString("meeting_point", ""),
                        facilities = x.optString("facilities", ""),
                        specialRequirements = x.optString("special_requirements", ""),
                        notes = x.optString("notes", "")
                    )
                )
            }
        }
    }

    suspend fun getRows(accessToken: String, table: String, order: String? = null): List<ErpRow> =
        withContext(Dispatchers.IO) {
            val suffix = if (order.isNullOrBlank()) "" else "&order=$order"
            val arr = JSONArray(request("GET", "/rest/v1/$table?select=*$suffix", null, accessToken))
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(jsonToRow(table, obj))
                }
            }
        }

    suspend fun createCustomer(
        accessToken: String,
        userId: String,
        name: String,
        type: String,
        pic: String,
        whatsapp: String,
        email: String
    ) = withContext(Dispatchers.IO) {
        val code = rpcText(accessToken, "next_customer_code")
        val payload = JSONObject()
            .put("customer_code", code)
            .put("name", name.trim())
            .put("customer_type", type.trim().ifBlank { "Sekolah" })
            .put("pic_name", nullable(pic))
            .put("whatsapp", nullable(whatsapp))
            .put("email", nullable(email))
            .put("created_by", userId)
        insertRowBlocking(accessToken, "customers", payload)
        audit(accessToken, userId, "CREATE_CUSTOMER", "customers", code, "Customer $name dibuat dari Android Native RC")
    }

    suspend fun createBooking(
        accessToken: String,
        userId: String,
        role: String,
        customerId: String,
        program: String,
        tripDate: String,
        pax: Int,
        pricePerPax: Double,
        status: String,
        participantGroup: String,
        meetingPoint: String
    ) = withContext(Dispatchers.IO) {
        val bookingNo = rpcText(accessToken, "next_booking_no")
        val payload = JSONObject()
            .put("booking_no", bookingNo)
            .put("customer_id", customerId)
            .put("program_name", program.trim())
            .put("trip_date", tripDate.trim())
            .put("pax", pax)
            .put("price_per_pax", pricePerPax)
            .put("status", status)
            .put("participant_group", nullable(participantGroup))
            .put("meeting_point", nullable(meetingPoint))
            .put("created_by", userId)
        if (role == "Sales") payload.put("sales_id", userId)
        insertRowBlocking(accessToken, "bookings", payload)
        audit(accessToken, userId, "CREATE_BOOKING", "bookings", bookingNo, "Booking $bookingNo dibuat dari Android Native RC")
    }

    suspend fun insertRow(accessToken: String, table: String, values: Map<String, Any?>): ErpRow? =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
            values.forEach { (k, v) -> payload.put(k, v ?: JSONObject.NULL) }
            val body = insertRowBlocking(accessToken, table, payload)
            val arr = JSONArray(body)
            if (arr.length() == 0) null else jsonToRow(table, arr.getJSONObject(0))
        }

    suspend fun updateBookingStatus(
        accessToken: String,
        bookingId: String,
        status: String
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "update_status")
            .put("booking_id", bookingId)
            .put("status", status)
            .toString()
        val root = JSONObject(request("POST", "/functions/v1/internal-booking-control", payload, accessToken))
        root.optJSONObject("item")?.optString("status", status) ?: status
    }


    suspend fun updateRow(accessToken: String, table: String, id: String, values: Map<String, Any?>): ErpRow? =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
            values.forEach { (k, v) -> payload.put(k, v ?: JSONObject.NULL) }
            val encodedId = URLEncoder.encode(id, "UTF-8")
            val body = request(
                "PATCH",
                "/rest/v1/$table?id=eq." + encodedId,
                payload.toString(),
                accessToken,
                preferReturn = true
            )
            val arr = JSONArray(body)
            if (arr.length() == 0) null else jsonToRow(table, arr.getJSONObject(0))
        }

    suspend fun updateProfile(accessToken: String, id: String, role: String? = null, active: Boolean? = null) =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
            if (role != null) payload.put("role", role)
            if (active != null) payload.put("is_active", active)
            val encodedId = URLEncoder.encode(id, "UTF-8")
            request(
                "PATCH",
                "/rest/v1/profiles?id=eq." + encodedId,
                payload.toString(),
                accessToken,
                preferReturn = true
            )
        }

    suspend fun createStaff(
        accessToken: String,
        fullName: String,
        email: String,
        phone: String,
        role: String,
        password: String
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("full_name", fullName.trim())
            .put("email", email.trim())
            .put("phone", phone.trim())
            .put("role", role)
            .put("password", password)
            .toString()
        request("POST", "/functions/v1/create-staff-user", payload, accessToken)
    }

    suspend fun resetStaffPassword(accessToken: String, userId: String, password: String): String =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("user_id", userId).put("password", password).toString()
            request("POST", "/functions/v1/reset-staff-password", payload, accessToken)
        }

    suspend fun approve(
        accessToken: String,
        approvalId: String,
        userId: String,
        approved: Boolean,
        notes: String
    ) = withContext(Dispatchers.IO) {
        updateRow(
            accessToken,
            "approvals",
            approvalId,
            mapOf(
                "status" to if (approved) "Approved" else "Rejected",
                "approved_by" to userId,
                "approved_at" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(java.util.Date()),
                "notes" to notes
            )
        )
    }


    suspend fun reviewBookingRequest(
        accessToken: String,
        requestId: String,
        accepted: Boolean,
        reason: String = ""
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", if (accepted) "start_verification" else "reject")
            .put("id", requestId)
            .put("reason", reason.trim())
            .toString()
        request("POST", "/functions/v1/internal-booking-inbox", payload, accessToken)
    }


    suspend fun startBookingRequestQuotation(
        accessToken: String,
        requestId: String
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "start_quotation")
            .put("id", requestId)
            .toString()
        val root = JSONObject(request("POST", "/functions/v1/internal-booking-inbox", payload, accessToken))
        root.optJSONObject("quotation")?.optString("quotation_no", "").orEmpty()
    }


    suspend fun getBookingRequestToken(
        accessToken: String,
        requestId: String
    ): CustomerPortalCredential = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "get_token")
            .put("id", requestId)
            .toString()
        val root = JSONObject(request("POST", "/functions/v1/internal-booking-inbox", payload, accessToken))
        val item = root.optJSONObject("item")
            ?: throw IllegalStateException(root.optString("error", "Token customer tidak dapat dimuat."))
        CustomerPortalCredential(
            requestId = item.optString("id", ""),
            bookingCode = item.optString("booking_code", ""),
            accessToken = item.optString("access_token", ""),
            institutionName = item.optString("institution_name", ""),
            picName = item.optString("pic_name", ""),
            status = item.optString("status", "")
        )
    }


    suspend fun getBookingRequests(accessToken: String): List<BookingRequestItem> = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "list")
            .put("status", "ALL")
            .toString()
        val root = JSONObject(request("POST", "/functions/v1/internal-booking-inbox", payload, accessToken))
        val arr = root.optJSONArray("items") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                val program = x.optJSONObject("programs")?.optString("name", "").orEmpty()
                add(
                    BookingRequestItem(
                        id = x.optString("id", ""),
                        bookingCode = x.optString("booking_code", ""),
                        status = x.optString("status", ""),
                        institutionName = x.optString("institution_name", ""),
                        picName = x.optString("pic_name", ""),
                        whatsapp = x.optString("whatsapp", ""),
                        email = x.optString("email", ""),
                        city = x.optString("city", ""),
                        programName = program,
                        customProgram = x.optString("custom_program", ""),
                        tripDate = x.optString("trip_date", ""),
                        pax = x.optInt("pax", 0),
                        companionPax = x.optInt("companion_pax", 0),
                        participantGroup = x.optString("participant_group", ""),
                        meetingPoint = x.optString("meeting_point", ""),
                        source = x.optString("source", ""),
                        createdAt = x.optString("created_at", ""),
                        updatedAt = x.optString("updated_at", ""),
                        convertedBookingId = x.optString("converted_booking_id", "")
                    )
                )
            }
        }
    }

    suspend fun getQuotationQueue(accessToken: String): List<QuotationQueueItem> = withContext(Dispatchers.IO) {
        val root = JSONObject(
            request(
                "POST",
                "/functions/v1/internal-quotation-workflow",
                JSONObject().put("action", "list_queue").toString(),
                accessToken
            )
        )
        val arr = root.optJSONArray("items") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                val q = x.optJSONObject("quotation")
                val program = x.optJSONObject("programs")?.optString("name", "").orEmpty()
                add(
                    QuotationQueueItem(
                        requestId = x.optString("id", ""),
                        bookingCode = x.optString("booking_code", ""),
                        requestStatus = x.optString("status", ""),
                        institutionName = x.optString("institution_name", ""),
                        tripDate = x.optString("trip_date", ""),
                        pax = x.optInt("pax", 0),
                        programName = program,
                        quotationId = q?.optString("id", "").orEmpty(),
                        quotationNo = q?.optString("quotation_no", "").orEmpty(),
                        quotationStatus = q?.optString("status", "").orEmpty(),
                        total = q?.optDouble("total", 0.0) ?: 0.0,
                        validUntil = q?.optString("valid_until", "").orEmpty()
                    )
                )
            }
        }
    }

    suspend fun getQuotationDetail(accessToken: String, requestId: String): QuotationDetail =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("action", "get")
                .put("booking_request_id", requestId)
                .toString()
            QuotationDetail.fromWorkflowJson(
                JSONObject(
                    request(
                        "POST",
                        "/functions/v1/internal-quotation-workflow",
                        payload,
                        accessToken
                    )
                )
            )
        }

    suspend fun createQuotationDraft(accessToken: String, requestId: String): QuotationDetail =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("action", "create_draft")
                .put("booking_request_id", requestId)
                .toString()
            request("POST", "/functions/v1/internal-quotation-workflow", payload, accessToken)
            getQuotationDetail(accessToken, requestId)
        }

    suspend fun saveQuotationDraft(
        accessToken: String,
        requestId: String,
        quotationId: String,
        items: List<QuotationLine>,
        discount: Double,
        tax: Double,
        validUntil: String,
        notesCustomer: String,
        terms: String
    ): QuotationDetail = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("description", item.description)
                    .put("qty", item.qty)
                    .put("unit", item.unit)
                    .put("unit_price", item.unitPrice)
            )
        }
        val payload = JSONObject()
            .put("action", "save_draft")
            .put("quotation_id", quotationId)
            .put("items", arr)
            .put("discount", discount)
            .put("tax", tax)
            .put("valid_until", validUntil.ifBlank { JSONObject.NULL })
            .put("notes_customer", notesCustomer)
            .put("terms", terms)
            .toString()
        request("POST", "/functions/v1/internal-quotation-workflow", payload, accessToken)
        getQuotationDetail(accessToken, requestId)
    }

    suspend fun publishQuotation(
        accessToken: String,
        requestId: String,
        quotationId: String,
        pricingOverrideReason: String
    ): QuotationDetail = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "publish")
            .put("quotation_id", quotationId)
            .put("pricing_override_reason", pricingOverrideReason)
            .toString()
        request("POST", "/functions/v1/internal-quotation-workflow", payload, accessToken)
        getQuotationDetail(accessToken, requestId)
    }

    suspend fun acceptQuotation(
        accessToken: String,
        requestId: String,
        quotationId: String
    ): QuotationDetail = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "accept")
            .put("quotation_id", quotationId)
            .toString()
        request("POST", "/functions/v1/internal-quotation-workflow", payload, accessToken)
        getQuotationDetail(accessToken, requestId)
    }

    suspend fun rejectQuotation(
        accessToken: String,
        requestId: String,
        quotationId: String,
        reason: String
    ): QuotationDetail = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "reject")
            .put("quotation_id", quotationId)
            .put("reason", reason)
            .toString()
        request("POST", "/functions/v1/internal-quotation-workflow", payload, accessToken)
        getQuotationDetail(accessToken, requestId)
    }

    suspend fun getPricingDashboard(accessToken: String): PricingDashboard = withContext(Dispatchers.IO) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val end = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(
            java.util.Date(System.currentTimeMillis() + 90L * 24L * 60L * 60L * 1000L)
        )
        val payload = JSONObject()
            .put("action", "dashboard")
            .put("start_date", today)
            .put("end_date", end)
            .toString()
        PricingDashboard.fromJson(
            JSONObject(
                request(
                    "POST",
                    "/functions/v1/internal-pricing-control",
                    payload,
                    accessToken
                )
            )
        )
    }

    suspend fun savePricingPolicy(
        accessToken: String,
        policyId: String?,
        scopeType: String,
        programId: String?,
        targetMarginPct: Double?,
        floorMarginPct: Double?,
        maxDiscountPct: Double?,
        contingencyPct: Double,
        roundingIncrement: Double,
        effectiveFrom: String,
        effectiveUntil: String?,
        notes: String
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("action", "save_policy")
            .put("policy_id", policyId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("scope_type", scopeType)
            .put("program_id", programId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("target_margin_pct", targetMarginPct ?: JSONObject.NULL)
            .put("floor_margin_pct", floorMarginPct ?: JSONObject.NULL)
            .put("max_discount_pct", maxDiscountPct ?: JSONObject.NULL)
            .put("contingency_pct", contingencyPct)
            .put("rounding_increment", roundingIncrement)
            .put("effective_from", effectiveFrom)
            .put("effective_until", effectiveUntil?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("is_active", true)
            .put("notes", notes)
            .toString()
        request("POST", "/functions/v1/internal-pricing-control", payload, accessToken)
    }


    suspend fun audit(
        accessToken: String,
        userId: String,
        action: String,
        table: String,
        recordId: String,
        message: String
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("user_id", userId)
                .put("action", action)
                .put("table_name", table)
                .put("record_id", recordId)
                .put("message", message)
            insertRowBlocking(accessToken, "audit_logs", payload)
        }
        Unit
    }

    private fun insertRowBlocking(accessToken: String, table: String, payload: JSONObject): String =
        request("POST", "/rest/v1/$table", payload.toString(), accessToken, preferReturn = true)

    private fun jsonToRow(table: String, obj: JSONObject): ErpRow {
        val map = linkedMapOf<String, String>()
        obj.keys().forEach { key ->
            val v = obj.opt(key)
            map[key] = if (v == null || v == JSONObject.NULL) "" else v.toString()
        }
        return ErpRow(table, obj.optString("id", obj.optString("code", "")), map)
    }

    private fun nullable(value: String): Any = value.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL

    private fun rpcText(accessToken: String, function: String): String {
        val body = request("POST", "/rest/v1/rpc/$function", "{}", accessToken)
        return body.trim().trim('"')
    }

    private fun request(
        method: String,
        path: String,
        body: String?,
        bearer: String?,
        preferReturn: Boolean = false
    ): String {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("apikey", key)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (preferReturn) setRequestProperty("Prefer", "return=representation")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { input -> BufferedReader(InputStreamReader(input)).readText() }.orEmpty()
        conn.disconnect()
        if (status !in 200..299) {
            val msg = runCatching {
                val j = if (text.trim().startsWith("[")) JSONArray(text).optJSONObject(0) else JSONObject(text)
                j?.optString("message", j.optString("msg", j.optString("error_description", "API gagal ($status)")))
                    ?: "API gagal ($status)"
            }.getOrDefault("API gagal ($status)")
            throw IllegalStateException(msg)
        }
        return text
    }
}
