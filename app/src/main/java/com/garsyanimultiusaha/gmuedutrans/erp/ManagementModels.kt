package com.garsyanimultiusaha.gmuedutrans.erp

import org.json.JSONArray
import org.json.JSONObject

data class ManagementKpi(
    val bookingCount: Int = 0,
    val paxTotal: Int = 0,
    val revenue: Double = 0.0,
    val cashCollected: Double = 0.0,
    val receivable: Double = 0.0,
    val rab: Double = 0.0,
    val committedCost: Double = 0.0,
    val actualCost: Double = 0.0,
    val grossProfit: Double = 0.0,
    val marginPct: Double = 0.0,
    val overBudgetBookingCount: Int = 0,
    val lowMarginBookingCount: Int = 0,
    val financialCloseReadyCount: Int = 0
)

data class ManagementCashForecast(
    val currentCash: Double = 0.0,
    val minimumReserve: Double = 0.0,
    val projected7d: Double = 0.0,
    val projected14d: Double = 0.0,
    val projected30d: Double = 0.0,
    val projectedAtHorizon: Double = 0.0,
    val lowestProjectedCash: Double = 0.0,
    val lowestProjectedDate: String = "",
    val reserveGapAtLowest: Double = 0.0
)

data class ManagementAgingBucket(
    val bucket: String,
    val itemCount: Int,
    val outstanding: Double
)

data class ManagementException(
    val severity: String,
    val code: String,
    val bookingId: String,
    val date: String,
    val amount: Double,
    val message: String
)

data class ManagementTrip(
    val bookingId: String,
    val bookingNo: String,
    val bookingCode: String,
    val programName: String,
    val tripDate: String,
    val pax: Int,
    val revenue: Double,
    val actualCost: Double,
    val grossProfit: Double,
    val marginPct: Double
)

data class ManagementDashboard(
    val version: String = "2.2",
    val kpi: ManagementKpi = ManagementKpi(),
    val cashForecast: ManagementCashForecast = ManagementCashForecast(),
    val arAging: List<ManagementAgingBucket> = emptyList(),
    val apAging: List<ManagementAgingBucket> = emptyList(),
    val exceptions: List<ManagementException> = emptyList(),
    val exceptionTotal: Int = 0,
    val criticalTotal: Int = 0,
    val warningTotal: Int = 0,
    val topProfitableTrips: List<ManagementTrip> = emptyList(),
    val lowestMarginTrips: List<ManagementTrip> = emptyList(),
    val topPrograms: List<Pair<String, Double>> = emptyList(),
    val topCustomers: List<Pair<String, Double>> = emptyList(),
    val topSales: List<Pair<String, Double>> = emptyList()
) {
    companion object {
        fun fromJson(root: JSONObject): ManagementDashboard {
            val k = root.optJSONObject("kpi") ?: JSONObject()
            val c = root.optJSONObject("cash_forecast") ?: JSONObject()
            val ex = root.optJSONObject("exceptions") ?: JSONObject()

            return ManagementDashboard(
                version = root.optString("version", "2.2"),
                kpi = ManagementKpi(
                    bookingCount = k.optInt("booking_count", 0),
                    paxTotal = k.optInt("pax_total", 0),
                    revenue = k.num("revenue"),
                    cashCollected = k.num("cash_collected"),
                    receivable = k.num("receivable"),
                    rab = k.num("rab"),
                    committedCost = k.num("committed_cost"),
                    actualCost = k.num("actual_cost"),
                    grossProfit = k.num("gross_profit"),
                    marginPct = k.num("margin_pct"),
                    overBudgetBookingCount = k.optInt("over_budget_booking_count", 0),
                    lowMarginBookingCount = k.optInt("low_margin_booking_count", 0),
                    financialCloseReadyCount = k.optInt("financial_close_ready_count", 0)
                ),
                cashForecast = ManagementCashForecast(
                    currentCash = c.num("current_cash"),
                    minimumReserve = c.num("minimum_reserve"),
                    projected7d = c.num("projected_cash_7d"),
                    projected14d = c.num("projected_cash_14d"),
                    projected30d = c.num("projected_cash_30d"),
                    projectedAtHorizon = c.num("projected_cash_at_horizon"),
                    lowestProjectedCash = c.num("lowest_projected_cash"),
                    lowestProjectedDate = c.optString("lowest_projected_date", ""),
                    reserveGapAtLowest = c.num("reserve_gap_at_lowest")
                ),
                arAging = parseAging(root.optJSONArray("ar_aging")),
                apAging = parseAging(root.optJSONArray("ap_aging")),
                exceptions = parseExceptions(ex.optJSONArray("items")),
                exceptionTotal = ex.optInt("total", 0),
                criticalTotal = ex.optInt("critical", 0),
                warningTotal = ex.optInt("warning", 0),
                topProfitableTrips = parseTrips(root.optJSONArray("top_profitable_trips")),
                lowestMarginTrips = parseTrips(root.optJSONArray("lowest_margin_trips")),
                topPrograms = parseRanking(root.optJSONArray("top_programs"), "program_name", "revenue"),
                topCustomers = parseRanking(root.optJSONArray("top_customers"), "customer_name", "revenue"),
                topSales = parseRanking(root.optJSONArray("sales_performance"), "sales_name", "revenue")
            )
        }

        private fun parseAging(arr: JSONArray?): List<ManagementAgingBucket> = buildList {
            if (arr == null) return@buildList
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                add(
                    ManagementAgingBucket(
                        bucket = x.optString("bucket", "-"),
                        itemCount = x.optInt("item_count", 0),
                        outstanding = x.num("outstanding")
                    )
                )
            }
        }

        private fun parseExceptions(arr: JSONArray?): List<ManagementException> = buildList {
            if (arr == null) return@buildList
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                add(
                    ManagementException(
                        severity = x.optString("severity", "WARN"),
                        code = x.optString("exception_code", ""),
                        bookingId = x.optString("booking_id", ""),
                        date = x.optString("exception_date", ""),
                        amount = x.num("amount"),
                        message = x.optString("message", "")
                    )
                )
            }
        }

        private fun parseTrips(arr: JSONArray?): List<ManagementTrip> = buildList {
            if (arr == null) return@buildList
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                add(
                    ManagementTrip(
                        bookingId = x.optString("booking_id", ""),
                        bookingNo = x.optString("booking_no", "-"),
                        bookingCode = x.optString("booking_code", ""),
                        programName = x.optString("program_name", "-"),
                        tripDate = x.optString("trip_date", ""),
                        pax = x.optInt("pax", 0),
                        revenue = x.num("contract_revenue"),
                        actualCost = x.num("actual_cost"),
                        grossProfit = x.num("gross_profit"),
                        marginPct = x.num("margin_pct")
                    )
                )
            }
        }

        private fun parseRanking(
            arr: JSONArray?,
            labelKey: String,
            valueKey: String
        ): List<Pair<String, Double>> = buildList {
            if (arr == null) return@buildList
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                add(x.optString(labelKey, "-") to x.num(valueKey))
            }
        }

        private fun JSONObject.num(key: String): Double =
            if (!has(key) || isNull(key)) 0.0 else optDouble(key, 0.0)
    }
}
