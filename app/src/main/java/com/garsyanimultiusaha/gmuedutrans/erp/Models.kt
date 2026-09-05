package com.garsyanimultiusaha.gmuedutrans.erp

data class StaffProfile(
    val id: String,
    val fullName: String,
    val role: String,
    val active: Boolean,
    val phone: String = ""
)

data class SessionState(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val profile: StaffProfile
)

data class Customer(
    val id: String,
    val code: String,
    val name: String,
    val type: String,
    val pic: String,
    val whatsapp: String,
    val email: String,
    val address: String = "",
    val notes: String = ""
)

data class Booking(
    val id: String,
    val bookingNo: String,
    val customerId: String,
    val customerName: String,
    val salesId: String,
    val programName: String,
    val tripDate: String,
    val pax: Int,
    val pricePerPax: Double,
    val status: String,
    val participantGroup: String,
    val meetingPoint: String,
    val facilities: String = "",
    val specialRequirements: String = "",
    val notes: String = ""
) {
    val omzet: Double get() = pax * pricePerPax
}

data class ErpRow(
    val table: String,
    val id: String,
    val data: Map<String, String>
) {
    fun text(key: String): String = data[key].orEmpty()
    fun number(key: String): Double = data[key]?.toDoubleOrNull() ?: 0.0
    fun int(key: String): Int = data[key]?.toIntOrNull() ?: 0
    fun bool(key: String): Boolean = data[key].equals("true", ignoreCase = true)
}

data class DashboardStats(
    val bookingsMonth: Int = 0,
    val customers: Int = 0,
    val pax: Int = 0,
    val omzet: Double = 0.0,
    val paid: Double = 0.0,
    val receivable: Double = 0.0,
    val actualCost: Double = 0.0,
    val profit: Double = 0.0,
    val margin: Double = 0.0,
    val upcoming: Int = 0,
    val topPrograms: List<Pair<String, Double>> = emptyList(),
    val topCustomers: List<Pair<String, Double>> = emptyList(),
    val topSales: List<Pair<String, Double>> = emptyList()
)

sealed interface AppState {
    data object Splash : AppState
    data object Loading : AppState
    data object LoggedOut : AppState
    data class LoggedIn(val session: SessionState) : AppState
    data class Error(val message: String) : AppState
}

enum class AppPage {
    DASHBOARD,
    BOOKINGS,
    CUSTOMERS,
    FINANCE,
    OPERATIONS,
    VENDORS,
    TRIP_FOLDER,
    WORKFLOW,
    SOP,
    REPORTS,
    CLOSING,
    TEAM_HR,
    USERS,
    AUDIT,
    PROFILE
}

enum class MainTab { HOME, BOOKING, TRIP, FINANCE, MORE }

object FinancialAccess {
    fun canView(role: String): Boolean = role == "Owner" || role == "Manager"
}

object RoleAccess {
    fun pages(role: String): Set<AppPage> = when (role) {
        "Owner" -> AppPage.entries.toSet()
        "Manager" -> AppPage.entries.toSet() - AppPage.USERS
        "Admin" -> setOf(
            AppPage.DASHBOARD, AppPage.BOOKINGS, AppPage.CUSTOMERS,
            AppPage.OPERATIONS, AppPage.TRIP_FOLDER, AppPage.WORKFLOW,
            AppPage.SOP, AppPage.REPORTS, AppPage.AUDIT, AppPage.PROFILE
        )
        "Sales" -> setOf(
            AppPage.DASHBOARD, AppPage.BOOKINGS, AppPage.CUSTOMERS, AppPage.PROFILE
        )
        "Finance" -> setOf(
            AppPage.DASHBOARD, AppPage.PROFILE
        )
        "Operation" -> setOf(
            AppPage.DASHBOARD, AppPage.BOOKINGS, AppPage.OPERATIONS,
            AppPage.VENDORS, AppPage.TRIP_FOLDER, AppPage.WORKFLOW,
            AppPage.SOP, AppPage.REPORTS, AppPage.AUDIT, AppPage.PROFILE
        )
        "TL" -> setOf(
            AppPage.DASHBOARD, AppPage.OPERATIONS, AppPage.TRIP_FOLDER,
            AppPage.SOP, AppPage.REPORTS, AppPage.PROFILE
        )
        else -> setOf(AppPage.DASHBOARD, AppPage.PROFILE)
    }
}
