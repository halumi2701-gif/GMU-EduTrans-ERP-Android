package com.garsyanimultiusaha.gmuedutrans.erp

import org.json.JSONArray
import org.json.JSONObject

data class PackageMasterItem(
    val id: String = "",
    val packageCode: String = "",
    val programId: String = "",
    val programName: String = "",
    val name: String = "",
    val description: String = "",
    val pricePerPax: Double = 0.0,
    val minPax: Int = 1,
    val facilities: List<String> = emptyList(),
    val status: String = "DRAFT",
    val active: Boolean = false,
    val priceNote: String = "",
    val effectiveFrom: String = "",
    val effectiveUntil: String = "",
    val sortOrder: Int = 0
) {
    companion object {
        fun fromJson(x: JSONObject): PackageMasterItem {
            val p = x.optJSONObject("programs") ?: JSONObject()
            return PackageMasterItem(
                id = x.optString("id", ""),
                packageCode = x.optString("package_code", ""),
                programId = x.optString("program_id", ""),
                programName = p.optString("name", ""),
                name = x.optString("name", ""),
                description = x.optString("description", ""),
                pricePerPax = x.optDouble("price_per_pax", 0.0),
                minPax = x.optInt("min_pax", 1),
                facilities = x.stringList("facilities"),
                status = x.optString("status", "DRAFT"),
                active = x.optBoolean("is_active", false),
                priceNote = x.optString("price_note", ""),
                effectiveFrom = x.optString("effective_from", ""),
                effectiveUntil = x.optString("effective_until", ""),
                sortOrder = x.optInt("sort_order", 0)
            )
        }
    }
}

data class CostCategoryItem(
    val code: String,
    val label: String,
    val defaultCostMode: String
)

data class CostTemplateItem(
    val id: String = "",
    val scopeType: String = "COMPANY",
    val programId: String = "",
    val packageId: String = "",
    val category: String = "",
    val description: String = "",
    val costMode: String = "FIXED",
    val amount: Double = 0.0,
    val minPax: Int? = null,
    val maxPax: Int? = null,
    val effectiveFrom: String = "",
    val effectiveUntil: String = "",
    val active: Boolean = false,
    val notes: String = ""
)

data class PricingMasterRow(
    val programId: String = "",
    val programName: String = "",
    val packageId: String = "",
    val packageName: String = "",
    val representativePax: Int = 0,
    val packagePricePerPax: Double = 0.0,
    val templateScope: String = "",
    val baseCost: Double = 0.0,
    val policySource: String = "",
    val targetMarginPct: Double? = null,
    val floorMarginPct: Double? = null,
    val recommendedPricePerPax: Double? = null,
    val floorPricePerPax: Double? = null,
    val candidateMarginPct: Double? = null,
    val setupStatus: String = "",
    val blockers: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class PricingMasterAction(
    val priority: Int = 0,
    val programId: String = "",
    val programName: String = "",
    val packageId: String = "",
    val packageName: String = "",
    val actionCode: String = "",
    val severity: String = "",
    val message: String = ""
)

data class PricingSetupStatus(
    val automaticPricingReady: Boolean = false,
    val blockers: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val activeCostTemplates: Int = 0,
    val activeCompanyPolicies: Int = 0,
    val activeProgramPolicies: Int = 0,
    val activePricedPackages: Int = 0,
    val zeroPricePackages: Int = 0
)

data class PricingMasterDashboard(
    val setup: PricingSetupStatus = PricingSetupStatus(),
    val programTotal: Int = 0,
    val programReady: Int = 0,
    val programIncomplete: Int = 0,
    val categories: List<CostCategoryItem> = emptyList(),
    val templates: List<CostTemplateItem> = emptyList(),
    val matrix: List<PricingMasterRow> = emptyList(),
    val actions: List<PricingMasterAction> = emptyList()
) {
    companion object {
        fun fromJson(root: JSONObject): PricingMasterDashboard {
            val setupJson = root.optJSONObject("setup_status") ?: JSONObject()
            val cost = setupJson.optJSONObject("cost_source") ?: JSONObject()
            val pol = setupJson.optJSONObject("pricing_policy") ?: JSONObject()
            val catalog = setupJson.optJSONObject("package_catalog") ?: JSONObject()
            val summary = root.optJSONObject("master_setup_summary") ?: JSONObject()
            val programs = summary.optJSONObject("programs") ?: JSONObject()

            val cats = root.optJSONArray("cost_categories") ?: JSONArray()
            val templates = root.optJSONArray("cost_templates") ?: JSONArray()
            val matrix = root.optJSONArray("master_readiness_matrix") ?: JSONArray()
            val actions = root.optJSONArray("master_action_plan") ?: JSONArray()

            return PricingMasterDashboard(
                setup = PricingSetupStatus(
                    automaticPricingReady = setupJson.optBoolean("ready_for_automatic_pricing", false),
                    blockers = setupJson.stringList("blockers"),
                    warnings = setupJson.stringList("warnings"),
                    activeCostTemplates = cost.optInt("active_templates", 0),
                    activeCompanyPolicies = pol.optInt("company_active", 0),
                    activeProgramPolicies = pol.optInt("program_active", 0),
                    activePricedPackages = catalog.optInt("active_priced_packages", 0),
                    zeroPricePackages = catalog.optInt("zero_price_packages", 0)
                ),
                programTotal = programs.optInt("total", 0),
                programReady = programs.optInt("ready", 0),
                programIncomplete = programs.optInt("incomplete", 0),
                categories = buildList {
                    for (i in 0 until cats.length()) {
                        val x = cats.optJSONObject(i) ?: continue
                        add(
                            CostCategoryItem(
                                code = x.optString("code", ""),
                                label = x.optString("label", ""),
                                defaultCostMode = x.optString("default_cost_mode", "FIXED")
                            )
                        )
                    }
                },
                templates = buildList {
                    for (i in 0 until templates.length()) {
                        val x = templates.optJSONObject(i) ?: continue
                        add(
                            CostTemplateItem(
                                id = x.optString("id", ""),
                                scopeType = x.optString("scope_type", "COMPANY"),
                                programId = x.optNullableString("program_id"),
                                packageId = x.optNullableString("package_id"),
                                category = x.optString("category", ""),
                                description = x.optString("description", ""),
                                costMode = x.optString("cost_mode", "FIXED"),
                                amount = x.optDouble("amount", 0.0),
                                minPax = x.optNullableInt("min_pax"),
                                maxPax = x.optNullableInt("max_pax"),
                                effectiveFrom = x.optString("effective_from", ""),
                                effectiveUntil = x.optNullableString("effective_until"),
                                active = x.optBoolean("is_active", false),
                                notes = x.optString("notes", "")
                            )
                        )
                    }
                },
                matrix = buildList {
                    for (i in 0 until matrix.length()) {
                        val x = matrix.optJSONObject(i) ?: continue
                        add(
                            PricingMasterRow(
                                programId = x.optString("program_id", ""),
                                programName = x.optString("program_name", ""),
                                packageId = x.optNullableString("package_id"),
                                packageName = x.optNullableString("package_name"),
                                representativePax = x.optInt("representative_pax", 0),
                                packagePricePerPax = x.optDouble("package_price_per_pax", 0.0),
                                templateScope = x.optString("template_scope", ""),
                                baseCost = x.optDouble("base_cost", 0.0),
                                policySource = x.optString("policy_source", ""),
                                targetMarginPct = x.optNullableDouble("target_margin_pct"),
                                floorMarginPct = x.optNullableDouble("floor_margin_pct"),
                                recommendedPricePerPax = x.optNullableDouble("recommended_price_per_pax"),
                                floorPricePerPax = x.optNullableDouble("floor_price_per_pax"),
                                candidateMarginPct = x.optNullableDouble("candidate_margin_pct"),
                                setupStatus = x.optString("setup_status", ""),
                                blockers = x.stringList("blockers"),
                                warnings = x.stringList("warnings")
                            )
                        )
                    }
                },
                actions = buildList {
                    for (i in 0 until actions.length()) {
                        val x = actions.optJSONObject(i) ?: continue
                        add(
                            PricingMasterAction(
                                priority = x.optInt("priority", 0),
                                programId = x.optString("program_id", ""),
                                programName = x.optString("program_name", ""),
                                packageId = x.optNullableString("package_id"),
                                packageName = x.optNullableString("package_name"),
                                actionCode = x.optString("action_code", ""),
                                severity = x.optString("severity", ""),
                                message = x.optString("message", "")
                            )
                        )
                    }
                }
            )
        }
    }
}

data class PaymentChannelItem(
    val code: String = "",
    val provider: String = "",
    val displayName: String = "",
    val methodType: String = "",
    val integrationMode: String = "",
    val brandGroup: String = "",
    val enabled: Boolean = false,
    val providerReady: Boolean = false,
    val requiresPhone: Boolean = false,
    val expiryMinutes: Int = 0
)

data class GatewayOrderItem(
    val id: String = "",
    val orderNo: String = "",
    val channelCode: String = "",
    val paymentType: String = "",
    val amount: Double = 0.0,
    val status: String = "",
    val providerStatus: String = "",
    val expiresAt: String = "",
    val paidAt: String = "",
    val createdAt: String = ""
)

data class PaymentGatewayDashboard(
    val providerConfigured: Boolean = false,
    val providerEnvironment: String = "",
    val totalOrders: Int = 0,
    val pending: Int = 0,
    val paid: Int = 0,
    val review: Int = 0,
    val failedOrExpired: Int = 0,
    val channels: List<PaymentChannelItem> = emptyList(),
    val recentOrders: List<GatewayOrderItem> = emptyList()
) {
    companion object {
        fun fromJson(root: JSONObject): PaymentGatewayDashboard {
            val health = root.optJSONObject("provider_health")
                ?.optJSONObject("MIDTRANS") ?: JSONObject()
            val summary = root.optJSONObject("summary") ?: JSONObject()
            val channels = root.optJSONArray("channels") ?: JSONArray()
            val orders = root.optJSONArray("recent_orders") ?: JSONArray()
            return PaymentGatewayDashboard(
                providerConfigured = health.optBoolean("configured", false),
                providerEnvironment = health.optString("environment", ""),
                totalOrders = summary.optInt("total_orders", 0),
                pending = summary.optInt("pending", 0),
                paid = summary.optInt("paid", 0),
                review = summary.optInt("review", 0),
                failedOrExpired = summary.optInt("failed_or_expired", 0),
                channels = buildList {
                    for (i in 0 until channels.length()) {
                        val x = channels.optJSONObject(i) ?: continue
                        add(
                            PaymentChannelItem(
                                code = x.optString("code", ""),
                                provider = x.optString("provider", ""),
                                displayName = x.optString("display_name", ""),
                                methodType = x.optString("method_type", ""),
                                integrationMode = x.optString("integration_mode", ""),
                                brandGroup = x.optString("brand_group", ""),
                                enabled = x.optBoolean("is_enabled", false),
                                providerReady = x.optBoolean("provider_ready", false),
                                requiresPhone = x.optBoolean("requires_phone", false),
                                expiryMinutes = x.optInt("expiry_minutes", 0)
                            )
                        )
                    }
                },
                recentOrders = buildList {
                    for (i in 0 until orders.length()) {
                        val x = orders.optJSONObject(i) ?: continue
                        add(
                            GatewayOrderItem(
                                id = x.optString("id", ""),
                                orderNo = x.optString("order_no", ""),
                                channelCode = x.optString("channel_code", ""),
                                paymentType = x.optString("payment_type", ""),
                                amount = x.optDouble("amount", 0.0),
                                status = x.optString("status", ""),
                                providerStatus = x.optString("provider_status", ""),
                                expiresAt = x.optString("expires_at", ""),
                                paidAt = x.optString("paid_at", ""),
                                createdAt = x.optString("created_at", "")
                            )
                        )
                    }
                }
            )
        }
    }
}

data class QuotationDraftSuggestion(
    val ready: Boolean = false,
    val source: String = "",
    val estimatedSubtotal: Double = 0.0,
    val blockers: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val proposedItems: List<QuotationLine> = emptyList()
) {
    companion object {
        fun fromJson(root: JSONObject): QuotationDraftSuggestion {
            val items = root.optJSONArray("proposed_items") ?: JSONArray()
            return QuotationDraftSuggestion(
                ready = root.optBoolean("ready_to_apply", false),
                source = root.optString("source", ""),
                estimatedSubtotal = root.optDouble("estimated_subtotal", 0.0),
                blockers = root.stringList("blockers"),
                warnings = root.stringList("warnings"),
                proposedItems = buildList {
                    for (i in 0 until items.length()) {
                        val x = items.optJSONObject(i) ?: continue
                        add(
                            QuotationLine(
                                description = x.optString("description", ""),
                                qty = x.optDouble("qty", 1.0),
                                unit = x.optString("unit", "pax"),
                                unitPrice = x.optDouble("unit_price", 0.0),
                                amount = x.optDouble("amount", 0.0)
                            )
                        )
                    }
                }
            )
        }
    }
}

private fun JSONObject.stringList(key: String): List<String> {
    val arr = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val v = arr.optString(i)
            if (v.isNotBlank()) add(v)
        }
    }
}

private fun JSONObject.optNullableString(key: String): String =
    if (!has(key) || isNull(key)) "" else optString(key, "")

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key)

private fun JSONObject.optNullableInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)
