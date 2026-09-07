package com.garsyanimultiusaha.gmuedutrans.erp

import org.json.JSONArray
import org.json.JSONObject

data class PlanMetrics(
    val revenue: Double = 0.0,
    val bookings: Int = 0,
    val pax: Int = 0,
    val profit: Double = 0.0,
    val marginPct: Double = 0.0,
    val cashIn: Double = 0.0
)

data class PlanAchievement(
    val revenuePct: Double? = null,
    val bookingsPct: Double? = null,
    val paxPct: Double? = null,
    val profitPct: Double? = null,
    val cashInPct: Double? = null,
    val marginGapPct: Double = 0.0
)

data class PlanningTargetRow(
    val id: String,
    val periodMonth: String,
    val scopeType: String,
    val salesId: String,
    val programId: String,
    val targetRevenue: Double,
    val targetBookings: Int,
    val targetPax: Int,
    val targetProfit: Double,
    val targetMarginPct: Double,
    val targetCashIn: Double,
    val notes: String
)

data class PlanningBudgetRow(
    val id: String,
    val periodMonth: String,
    val budgetType: String,
    val category: String,
    val programId: String,
    val amount: Double,
    val notes: String
)

data class PipelineWeight(
    val status: String,
    val probabilityPct: Double,
    val notes: String
)

data class ForecastPeriod(
    val revenue: Double = 0.0,
    val cost: Double = 0.0,
    val profit: Double = 0.0,
    val marginPct: Double = 0.0
)

data class ProgramPerformance(
    val programName: String,
    val bookingCount: Int,
    val paxTotal: Int,
    val revenue: Double,
    val actualCost: Double,
    val grossProfit: Double,
    val marginPct: Double,
    val profitPerPax: Double,
    val averageSellingPrice: Double,
    val averageCostPerPax: Double,
    val flag: String
)

data class PlanningDashboard(
    val version: String = "2.3",
    val periodMonth: String = "",
    val revenue: Double = 0.0,
    val grossProfit: Double = 0.0,
    val marginPct: Double = 0.0,
    val collectionRatePct: Double = 0.0,
    val arOverdue: Double = 0.0,
    val apOverdue: Double = 0.0,
    val cashRunwayDays: Int? = null,
    val budgetVariance: Double = 0.0,
    val closingReadinessPct: Double = 0.0,
    val target: PlanMetrics = PlanMetrics(),
    val actual: PlanMetrics = PlanMetrics(),
    val achievement: PlanAchievement = PlanAchievement(),
    val managementBudgetTotal: Double = 0.0,
    val tripRabTotal: Double = 0.0,
    val tripCommittedTotal: Double = 0.0,
    val tripActualTotal: Double = 0.0,
    val tripVariancePct: Double = 0.0,
    val weightedRevenue30d: Double = 0.0,
    val weightedRevenue60d: Double = 0.0,
    val weightedRevenue90d: Double = 0.0,
    val grossPipeline90d: Double = 0.0,
    val profit30d: ForecastPeriod = ForecastPeriod(),
    val profit60d: ForecastPeriod = ForecastPeriod(),
    val profit90d: ForecastPeriod = ForecastPeriod(),
    val targets: List<PlanningTargetRow> = emptyList(),
    val budgets: List<PlanningBudgetRow> = emptyList(),
    val pipelineWeights: List<PipelineWeight> = emptyList(),
    val programs: List<ProgramPerformance> = emptyList()
) {
    companion object {
        fun fromJson(root: JSONObject): PlanningDashboard {
            val score = root.optJSONObject("scorecard") ?: JSONObject()
            val targetVsActual = score.optJSONObject("target_vs_actual") ?: JSONObject()
            val target = targetVsActual.optJSONObject("target") ?: JSONObject()
            val actual = targetVsActual.optJSONObject("actual") ?: JSONObject()
            val achievement = targetVsActual.optJSONObject("achievement") ?: JSONObject()
            val budget = score.optJSONObject("budget_control") ?: JSONObject()
            val sales = score.optJSONObject("sales_forecast") ?: JSONObject()
            val profit = score.optJSONObject("profit_forecast") ?: JSONObject()

            return PlanningDashboard(
                version = score.optString("version", "2.3"),
                periodMonth = score.optString("period_month", ""),
                revenue = score.num("revenue"),
                grossProfit = score.num("gross_profit"),
                marginPct = score.num("margin_pct"),
                collectionRatePct = score.num("collection_rate_pct"),
                arOverdue = score.num("ar_overdue"),
                apOverdue = score.num("ap_overdue"),
                cashRunwayDays = score.intOrNull("cash_runway_days"),
                budgetVariance = score.num("budget_variance"),
                closingReadinessPct = score.num("closing_readiness_pct"),
                target = PlanMetrics(
                    revenue = target.num("revenue"),
                    bookings = target.optInt("bookings", 0),
                    pax = target.optInt("pax", 0),
                    profit = target.num("profit"),
                    marginPct = target.num("margin_pct"),
                    cashIn = target.num("cash_in")
                ),
                actual = PlanMetrics(
                    revenue = actual.num("revenue"),
                    bookings = actual.optInt("bookings", 0),
                    pax = actual.optInt("pax", 0),
                    profit = actual.num("profit"),
                    marginPct = actual.num("margin_pct"),
                    cashIn = actual.num("cash_in")
                ),
                achievement = PlanAchievement(
                    revenuePct = achievement.doubleOrNull("revenue_pct"),
                    bookingsPct = achievement.doubleOrNull("bookings_pct"),
                    paxPct = achievement.doubleOrNull("pax_pct"),
                    profitPct = achievement.doubleOrNull("profit_pct"),
                    cashInPct = achievement.doubleOrNull("cash_in_pct"),
                    marginGapPct = achievement.num("margin_gap_pct")
                ),
                managementBudgetTotal = budget.num("management_budget_total"),
                tripRabTotal = budget.num("trip_rab_total"),
                tripCommittedTotal = budget.num("trip_committed_total"),
                tripActualTotal = budget.num("trip_actual_total"),
                tripVariancePct = budget.num("trip_variance_pct"),
                weightedRevenue30d = sales.num("weighted_revenue_30d"),
                weightedRevenue60d = sales.num("weighted_revenue_60d"),
                weightedRevenue90d = sales.num("weighted_revenue_90d"),
                grossPipeline90d = sales.num("gross_pipeline_90d"),
                profit30d = parseForecast(profit.optJSONObject("days_30")),
                profit60d = parseForecast(profit.optJSONObject("days_60")),
                profit90d = parseForecast(profit.optJSONObject("days_90")),
                targets = parseTargets(root.optJSONArray("targets")),
                budgets = parseBudgets(root.optJSONArray("budgets")),
                pipelineWeights = parseWeights(root.optJSONArray("pipeline_weights")),
                programs = parsePrograms(root.optJSONArray("program_performance"))
            )
        }

        private fun parseForecast(x: JSONObject?): ForecastPeriod {
            val o = x ?: JSONObject()
            return ForecastPeriod(
                revenue = o.num("projected_revenue"),
                cost = o.num("projected_cost"),
                profit = o.num("projected_profit"),
                marginPct = o.num("projected_margin_pct")
            )
        }

        private fun parseTargets(arr: JSONArray?): List<PlanningTargetRow> = buildList {
            if (arr == null) return@buildList
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                add(
                    PlanningTargetRow(
                        id = x.optString("id", ""),
                        periodMonth = x.optString("period_month", ""),
                        scopeType = x.optString("scope_type", "COMPANY"),
                        salesId = x.optString("sales_id", ""),
                        programId = x.optString("program_id", ""),
                        targetRevenue = x.num("target_revenue"),
                        targetBookings = x.optInt("target_bookings", 0),
                        targetPax = x.optInt("target_pax", 0),
                        targetProfit = x.num("target_profit"),
                        targetMarginPct = x.num("target_margin_pct"),
                        targetCashIn = x.num("target_cash_in"),
                        notes = x.optString("notes", "")
                    )
                )
            }
        }

        private fun parseBudgets(arr: JSONArray?): List<PlanningBudgetRow> = buildList {
            if (arr == null) return@buildList
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                add(
                    PlanningBudgetRow(
                        id = x.optString("id", ""),
                        periodMonth = x.optString("period_month", ""),
                        budgetType = x.optString("budget_type", ""),
                        category = x.optString("category", ""),
                        programId = x.optString("program_id", ""),
                        amount = x.num("amount"),
                        notes = x.optString("notes", "")
                    )
                )
            }
        }

        private fun parseWeights(arr: JSONArray?): List<PipelineWeight> = buildList {
            if (arr == null) return@buildList
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                add(
                    PipelineWeight(
                        status = x.optString("booking_status", ""),
                        probabilityPct = x.num("win_probability_pct"),
                        notes = x.optString("notes", "")
                    )
                )
            }
        }

        private fun parsePrograms(arr: JSONArray?): List<ProgramPerformance> = buildList {
            if (arr == null) return@buildList
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                add(
                    ProgramPerformance(
                        programName = x.optString("program_name", "-"),
                        bookingCount = x.optInt("booking_count", 0),
                        paxTotal = x.optInt("pax_total", 0),
                        revenue = x.num("revenue"),
                        actualCost = x.num("actual_cost"),
                        grossProfit = x.num("gross_profit"),
                        marginPct = x.num("margin_pct"),
                        profitPerPax = x.num("profit_per_pax"),
                        averageSellingPrice = x.num("average_selling_price"),
                        averageCostPerPax = x.num("average_cost_per_pax"),
                        flag = x.optString("performance_flag", "WATCH")
                    )
                )
            }
        }

        private fun JSONObject.num(key: String): Double =
            if (!has(key) || isNull(key)) 0.0 else optDouble(key, 0.0)

        private fun JSONObject.doubleOrNull(key: String): Double? =
            if (!has(key) || isNull(key)) null else optDouble(key)

        private fun JSONObject.intOrNull(key: String): Int? =
            if (!has(key) || isNull(key)) null else optInt(key)
    }
}

data class PlanningScenarioResult(
    val bookingId: String,
    val scenarioPax: Int,
    val pricePerPax: Double,
    val projectedRevenue: Double,
    val projectedCost: Double,
    val projectedProfit: Double,
    val projectedMarginPct: Double,
    val profitPerPax: Double,
    val costPerPax: Double,
    val breakEvenPax: Int?
) {
    companion object {
        fun fromJson(root: JSONObject): PlanningScenarioResult {
            val assumptions = root.optJSONObject("assumptions") ?: JSONObject()
            val scenario = root.optJSONObject("scenario") ?: JSONObject()
            return PlanningScenarioResult(
                bookingId = root.optString("booking_id", ""),
                scenarioPax = assumptions.optInt("scenario_pax", 0),
                pricePerPax = assumptions.optDouble("price_per_pax", 0.0),
                projectedRevenue = scenario.optDouble("projected_revenue", 0.0),
                projectedCost = scenario.optDouble("projected_cost", 0.0),
                projectedProfit = scenario.optDouble("projected_profit", 0.0),
                projectedMarginPct = scenario.optDouble("projected_margin_pct", 0.0),
                profitPerPax = scenario.optDouble("profit_per_pax", 0.0),
                costPerPax = scenario.optDouble("cost_per_pax", 0.0),
                breakEvenPax = if (scenario.has("break_even_pax") && !scenario.isNull("break_even_pax")) scenario.optInt("break_even_pax") else null
            )
        }
    }
}
