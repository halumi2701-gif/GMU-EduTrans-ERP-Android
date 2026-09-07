package com.garsyanimultiusaha.gmuedutrans.erp

import org.json.JSONArray
import org.json.JSONObject

data class QuotationQueueItem(
    val requestId: String,
    val bookingCode: String,
    val requestStatus: String,
    val institutionName: String,
    val tripDate: String,
    val pax: Int,
    val programName: String,
    val quotationId: String,
    val quotationNo: String,
    val quotationStatus: String,
    val total: Double,
    val validUntil: String
)

data class QuotationLine(
    val id: String = "",
    val description: String = "",
    val qty: Double = 1.0,
    val unit: String = "item",
    val unitPrice: Double = 0.0,
    val amount: Double = 0.0
)

data class PricingSnapshot(
    val status: String = "",
    val confidence: String = "",
    val recommendedNet: Double? = null,
    val recommendedPerPax: Double? = null,
    val floorNet: Double? = null,
    val floorPerPax: Double? = null,
    val candidateMarginPct: Double? = null,
    val effectiveMaxDiscountPct: Double? = null,
    val approvalRequired: Boolean = false,
    val requiredRole: String = "",
    val riskFlags: List<String> = emptyList()
)

data class QuotationDetail(
    val requestId: String = "",
    val bookingCode: String = "",
    val requestStatus: String = "",
    val institutionName: String = "",
    val picName: String = "",
    val programName: String = "",
    val tripDate: String = "",
    val pax: Int = 0,
    val quotationId: String = "",
    val quotationNo: String = "",
    val quotationStatus: String = "",
    val validUntil: String = "",
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val notesCustomer: String = "",
    val terms: String = "",
    val items: List<QuotationLine> = emptyList(),
    val pricing: PricingSnapshot? = null
) {
    companion object {
        fun fromWorkflowJson(root: JSONObject): QuotationDetail {
            val booking = root.optJSONObject("booking") ?: JSONObject()
            val qWrap = root.optJSONObject("quotation")
            val quotation = qWrap?.optJSONObject("quotation")
            val items = qWrap?.optJSONArray("items") ?: JSONArray()
            val programName = booking.optJSONObject("programs")?.optString("name", "").orEmpty()
                .ifBlank { booking.optString("custom_program", "") }

            return QuotationDetail(
                requestId = booking.optString("id", ""),
                bookingCode = booking.optString("booking_code", ""),
                requestStatus = booking.optString("status", ""),
                institutionName = booking.optString("institution_name", ""),
                picName = booking.optString("pic_name", ""),
                programName = programName,
                tripDate = booking.optString("trip_date", ""),
                pax = booking.optInt("pax", 0),
                quotationId = quotation?.optString("id", "").orEmpty(),
                quotationNo = quotation?.optString("quotation_no", "").orEmpty(),
                quotationStatus = quotation?.optString("status", "").orEmpty(),
                validUntil = quotation?.optString("valid_until", "").orEmpty(),
                subtotal = quotation?.optDouble("subtotal", 0.0) ?: 0.0,
                discount = quotation?.optDouble("discount", 0.0) ?: 0.0,
                tax = quotation?.optDouble("tax", 0.0) ?: 0.0,
                total = quotation?.optDouble("total", 0.0) ?: 0.0,
                notesCustomer = quotation?.optString("notes_customer", "").orEmpty(),
                terms = quotation?.optString("terms", "").orEmpty(),
                items = buildList {
                    for (i in 0 until items.length()) {
                        val x = items.optJSONObject(i) ?: continue
                        add(
                            QuotationLine(
                                id = x.optString("id", ""),
                                description = x.optString("description", ""),
                                qty = x.optDouble("qty", 1.0),
                                unit = x.optString("unit", "item"),
                                unitPrice = x.optDouble("unit_price", 0.0),
                                amount = x.optDouble("amount", 0.0)
                            )
                        )
                    }
                },
                pricing = root.optJSONObject("pricing")?.let { parsePricing(it) }
            )
        }

        fun fromSaveJson(root: JSONObject, base: QuotationDetail): QuotationDetail {
            val q = root.optJSONObject("quotation") ?: JSONObject()
            val items = root.optJSONArray("items") ?: JSONArray()
            return base.copy(
                quotationId = q.optString("id", base.quotationId),
                quotationNo = q.optString("quotation_no", base.quotationNo),
                quotationStatus = q.optString("status", base.quotationStatus),
                validUntil = q.optString("valid_until", base.validUntil),
                subtotal = q.optDouble("subtotal", base.subtotal),
                discount = q.optDouble("discount", base.discount),
                tax = q.optDouble("tax", base.tax),
                total = q.optDouble("total", base.total),
                notesCustomer = q.optString("notes_customer", base.notesCustomer),
                terms = q.optString("terms", base.terms),
                items = buildList {
                    for (i in 0 until items.length()) {
                        val x = items.optJSONObject(i) ?: continue
                        add(
                            QuotationLine(
                                id = x.optString("id", ""),
                                description = x.optString("description", ""),
                                qty = x.optDouble("qty", 1.0),
                                unit = x.optString("unit", "item"),
                                unitPrice = x.optDouble("unit_price", 0.0),
                                amount = x.optDouble("amount", 0.0)
                            )
                        )
                    }
                },
                pricing = root.optJSONObject("pricing")?.let { parsePricing(it) } ?: base.pricing
            )
        }

        private fun parsePricing(root: JSONObject): PricingSnapshot {
            val pricing = root.optJSONObject("pricing") ?: JSONObject()
            val candidate = root.optJSONObject("candidate") ?: JSONObject()
            val decision = root.optJSONObject("decision") ?: JSONObject()
            val flags = decision.optJSONArray("risk_flags") ?: JSONArray()
            return PricingSnapshot(
                status = decision.optString("status", ""),
                confidence = decision.optString("confidence", ""),
                recommendedNet = pricing.doubleOrNull("recommended_net_selling_value"),
                recommendedPerPax = pricing.doubleOrNull("recommended_price_per_pax"),
                floorNet = pricing.doubleOrNull("floor_net_selling_value"),
                floorPerPax = pricing.doubleOrNull("floor_price_per_pax"),
                candidateMarginPct = candidate.doubleOrNull("margin_pct"),
                effectiveMaxDiscountPct = pricing.doubleOrNull("effective_max_discount_pct"),
                approvalRequired = decision.optBoolean("approval_required", false),
                requiredRole = decision.optString("required_role", ""),
                riskFlags = buildList {
                    for (i in 0 until flags.length()) add(flags.optString(i))
                }.filter { it.isNotBlank() }
            )
        }

        private fun JSONObject.doubleOrNull(key: String): Double? =
            if (!has(key) || isNull(key)) null else optDouble(key)
    }
}

data class PricingPolicyItem(
    val id: String,
    val scopeType: String,
    val programId: String,
    val targetMarginPct: Double?,
    val floorMarginPct: Double?,
    val maxDiscountPct: Double?,
    val contingencyPct: Double,
    val roundingIncrement: Double,
    val effectiveFrom: String,
    val effectiveUntil: String,
    val isActive: Boolean,
    val notes: String
)

data class PricingWatchItem(
    val bookingId: String,
    val bookingNo: String,
    val programName: String,
    val tripDate: String,
    val pax: Int,
    val confidence: String,
    val status: String,
    val recommendedNet: Double?,
    val floorNet: Double?,
    val candidateNet: Double?,
    val candidateMarginPct: Double?,
    val approvalRequired: Boolean
)

data class PricingDashboard(
    val version: String = "2.4",
    val policies: List<PricingPolicyItem> = emptyList(),
    val watchlist: List<PricingWatchItem> = emptyList()
) {
    companion object {
        fun fromJson(root: JSONObject): PricingDashboard {
            val policies = root.optJSONArray("policies") ?: JSONArray()
            val watch = root.optJSONArray("watchlist") ?: JSONArray()
            return PricingDashboard(
                version = root.optString("version", "2.4"),
                policies = buildList {
                    for (i in 0 until policies.length()) {
                        val x = policies.optJSONObject(i) ?: continue
                        add(
                            PricingPolicyItem(
                                id = x.optString("id", ""),
                                scopeType = x.optString("scope_type", ""),
                                programId = x.optString("program_id", ""),
                                targetMarginPct = x.doubleOrNull("target_margin_pct"),
                                floorMarginPct = x.doubleOrNull("floor_margin_pct"),
                                maxDiscountPct = x.doubleOrNull("max_discount_pct"),
                                contingencyPct = x.optDouble("contingency_pct", 0.0),
                                roundingIncrement = x.optDouble("rounding_increment", 1000.0),
                                effectiveFrom = x.optString("effective_from", ""),
                                effectiveUntil = x.optString("effective_until", ""),
                                isActive = x.optBoolean("is_active", false),
                                notes = x.optString("notes", "")
                            )
                        )
                    }
                },
                watchlist = buildList {
                    for (i in 0 until watch.length()) {
                        val x = watch.optJSONObject(i) ?: continue
                        add(
                            PricingWatchItem(
                                bookingId = x.optString("booking_id", ""),
                                bookingNo = x.optString("booking_no", ""),
                                programName = x.optString("program_name", ""),
                                tripDate = x.optString("trip_date", ""),
                                pax = x.optInt("pax", 0),
                                confidence = x.optString("confidence", ""),
                                status = x.optString("status", ""),
                                recommendedNet = x.doubleOrNull("recommended_net_selling_value"),
                                floorNet = x.doubleOrNull("floor_net_selling_value"),
                                candidateNet = x.doubleOrNull("candidate_net_selling_value"),
                                candidateMarginPct = x.doubleOrNull("candidate_margin_pct"),
                                approvalRequired = x.optBoolean("approval_required", false)
                            )
                        )
                    }
                }
            )
        }

        private fun JSONObject.doubleOrNull(key: String): Double? =
            if (!has(key) || isNull(key)) null else optDouble(key)
    }
}
