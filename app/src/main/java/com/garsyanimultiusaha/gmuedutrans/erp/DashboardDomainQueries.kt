package com.garsyanimultiusaha.gmuedutrans.erp

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only domain queries used by Dashboard, Booking detail, and Finance UI.
 * Keeping these calculations outside MainViewModel prevents the session/action
 * coordinator from becoming the owner of presentation analytics as well.
 */
private fun MainViewModel.cashFlowForMonth(month: String): Pair<Double, Double> {
    // Treasury ledger is the single source of truth for real cash movement.
    // Booking revenue and trip costs are not cash movements by themselves.
    val rows = table("treasury_ledger").filter { row ->
        row.text("status") == "POSTED" && row.text("transaction_date").startsWith(month)
    }
    val cashIn = rows.filter { it.text("direction") == "INFLOW" }.sumOf { it.number("amount") }
    val cashOut = rows.filter { it.text("direction") == "OUTFLOW" }.sumOf { it.number("amount") }
    return cashIn to cashOut
}

fun MainViewModel.dashboardStats(): DashboardStats {
    val management = managementDashboard
    val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    val (cashInThisMonth, cashOutThisMonth) = cashFlowForMonth(month)
    if (management != null) {
        val k = management.kpi
        return DashboardStats(
            bookingsMonth = k.bookingCount,
            customers = customers.size,
            pax = k.paxTotal,
            omzet = k.revenue,
            paid = cashInThisMonth,
            cashOut = cashOutThisMonth,
            receivable = k.receivable,
            actualCost = k.actualCost,
            profit = k.grossProfit,
            margin = k.marginPct,
            upcoming = bookings.count {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                it.tripDate >= today && it.status !in listOf("Completed", "Closed")
            },
            topPrograms = management.topPrograms.take(3),
            topCustomers = management.topCustomers.take(3),
            topSales = management.topSales.take(3)
        )
    }

    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val monthBookings = bookings.filter { it.tripDate.startsWith(month) }
    val omzet = monthBookings.sumOf { it.omzet }
    val pax = monthBookings.sumOf { it.pax }

    val payments = table("payments")
    val paidByBooking = payments.groupBy { it.text("booking_id") }.mapValues { (_, list) ->
        list.sumOf { row ->
            val amount = row.number("amount")
            if (row.text("payment_type").equals("Refund", ignoreCase = true)) -amount else amount
        }
    }
    val paid = cashInThisMonth
    val paidForTripBookings = monthBookings.sumOf { booking ->
        if (booking.status.equals("Confirmed", ignoreCase = true)) booking.omzet
        else paidByBooking[booking.id] ?: 0.0
    }
    val receivable = (omzet - paidForTripBookings).coerceAtLeast(0.0)

    val costs = table("trip_costs")
    val actualByBooking = costs.groupBy { it.text("booking_id") }
        .mapValues { (_, list) -> list.sumOf { it.number("actual_amount") } }
    val actual = monthBookings.sumOf { actualByBooking[it.id] ?: 0.0 }
    val profit = omzet - actual
    val margin = if (omzet > 0) profit / omzet * 100.0 else 0.0
    val upcoming = bookings.count { it.tripDate >= today && it.status !in listOf("Completed", "Closed") }

    val topPrograms = monthBookings.groupBy { it.programName }
        .mapValues { (_, list) -> list.sumOf { it.omzet } }
        .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }

    val topCustomers = monthBookings.groupBy { it.customerName }
        .mapValues { (_, list) -> list.sumOf { it.omzet } }
        .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }

    val profiles = table("profiles").associate { it.id to it.text("full_name") }
    val topSales = monthBookings.filter { it.salesId.isNotBlank() }
        .groupBy { profiles[it.salesId] ?: "Sales" }
        .mapValues { (_, list) -> list.sumOf { it.omzet } }
        .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }

    return DashboardStats(
        bookingsMonth = monthBookings.size,
        customers = customers.size,
        pax = pax,
        omzet = omzet,
        paid = paid,
        cashOut = cashOutThisMonth,
        receivable = receivable,
        actualCost = actual,
        profit = profit,
        margin = margin,
        upcoming = upcoming,
        topPrograms = topPrograms,
        topCustomers = topCustomers,
        topSales = topSales
    )
}

fun MainViewModel.paidForBooking(bookingId: String): Double =
    table("payments").filter { it.text("booking_id") == bookingId }.sumOf {
        if (it.text("payment_type").equals("Refund", ignoreCase = true)) -it.number("amount") else it.number("amount")
    }

fun MainViewModel.actualCostForBooking(bookingId: String): Double =
    table("trip_costs").filter { it.text("booking_id") == bookingId }.sumOf { it.number("actual_amount") }

fun MainViewModel.rabForBooking(bookingId: String): Double =
    table("trip_costs").filter { it.text("booking_id") == bookingId }.sumOf { it.number("rab_amount") }

fun MainViewModel.bookingById(id: String): Booking? = bookings.firstOrNull { it.id == id }
