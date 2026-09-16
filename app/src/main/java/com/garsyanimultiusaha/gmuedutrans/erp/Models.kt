package com.garsyanimultiusaha.gmuedutrans.erp

data class StaffProfile(
    val id: String,
    val fullName: String,
    var role: String,
    val active: Boolean,
    val phone: String = ""
) {
    init {
        // Compatibility bridge while older Android screens are migrated away from literal "Manager".
        role = ErpRoles.compatibilityAccessRole(role)
    }

    val canonicalRole: String get() = ErpRoles.canonical(role)
    val roleLabel: String get() = ErpRoles.displayName(role)
}

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
    SALES_FORECAST,
    BOOKINGS,
    BOOKING_REQUESTS,
    QUOTATIONS,
    PACKAGE_MASTER,
    PRICING_MASTER,
    PAYMENT_GATEWAY,
    CUSTOMERS,
    FINANCE,
    PLANNING,
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

object ErpRoles {
    const val OWNER = "Owner"
    const val DIRECTOR = "Director"
    const val DIRECTOR_ID = "Direktur"
    const val MANAGER_EDUTRANS = "Manager EduTrans"
    const val LEGACY_MANAGER = "Manager"

    fun isDirector(role: String): Boolean = role == DIRECTOR || role == DIRECTOR_ID
    fun isManagerEduTrans(role: String): Boolean = role == MANAGER_EDUTRANS || role == LEGACY_MANAGER

    // Keep canonical identifiers backward-compatible with existing workflow checks.
    fun canonical(role: String): String = when {
        isDirector(role) -> DIRECTOR
        isManagerEduTrans(role) -> MANAGER_EDUTRANS
        else -> role
    }

    fun compatibilityAccessRole(role: String): String = when (role) {
        MANAGER_EDUTRANS -> LEGACY_MANAGER
        else -> role
    }

    // Visible labels use Indonesian without changing internal identifiers.
    fun displayName(role: String): String = when (canonical(role)) {
        DIRECTOR -> "Direktur"
        MANAGER_EDUTRANS -> "Manager EduTrans"
        "Operation" -> "Operasional"
        "Finance" -> "Keuangan"
        "Admin" -> "Admin / Operasional"
        "Sales" -> "Sales / Pengembangan Bisnis"
        "TL" -> "Tour Leader"
        else -> canonical(role)
    }
}

object FinancialAccess {
    /**
     * Keuangan operasional unit dapat dilihat Owner/Direktur, Manager EduTrans, dan Finance.
     * Data strategis PT, kredensial bank, dan kontrol lintas-unit tetap untuk Owner/Direktur.
     */
    fun canView(role: String): Boolean =
        role == ErpRoles.OWNER ||
            ErpRoles.isDirector(role) ||
            ErpRoles.isManagerEduTrans(role) ||
            role == "Finance"

    fun canViewStrategic(role: String): Boolean =
        role == ErpRoles.OWNER || ErpRoles.isDirector(role)
}

object ManagerEduTransPolicy {
    const val plannedRabLimitIdr: Long = 1_000_000L
    const val unplannedLimitIdr: Long = 250_000L
    const val emergencyTripLimitIdr: Long = 500_000L
    const val maxDiscountPct: Double = 5.0
    const val healthyMarginPct: Double = 25.0
    const val criticalMarginPct: Double = 20.0

    // Compatibility alias: approval generik dianggap biaya yang sudah berada dalam RAB.
    const val approvalLimitIdr: Long = plannedRabLimitIdr

    val operationalPages: Set<AppPage> = setOf(
        AppPage.DASHBOARD,
        AppPage.SALES_FORECAST,
        AppPage.BOOKINGS,
        AppPage.BOOKING_REQUESTS,
        AppPage.QUOTATIONS,
        AppPage.PACKAGE_MASTER,
        AppPage.CUSTOMERS,
        AppPage.FINANCE,
        AppPage.PLANNING,
        AppPage.OPERATIONS,
        AppPage.VENDORS,
        AppPage.TRIP_FOLDER,
        AppPage.WORKFLOW,
        AppPage.SOP,
        AppPage.REPORTS,
        AppPage.CLOSING,
        AppPage.TEAM_HR,
        AppPage.AUDIT,
        AppPage.PROFILE
    )

    fun requiresDirectorApproval(amountIdr: Long): Boolean = amountIdr > plannedRabLimitIdr

    fun requiresDirectorForUnplanned(amountIdr: Long): Boolean = amountIdr > unplannedLimitIdr

    fun requiresDirectorForEmergency(amountIdr: Long): Boolean = amountIdr > emergencyTripLimitIdr

    fun requiresDirectorForDiscount(discountPct: Double): Boolean = discountPct > maxDiscountPct

    fun marginAuthority(marginPct: Double): String = when {
        marginPct < criticalMarginPct -> "DIREKTUR"
        marginPct < healthyMarginPct -> "MANAGER"
        else -> "NORMAL"
    }
}

object RoleAccess {
    fun pages(role: String): Set<AppPage> = when {
        role == ErpRoles.OWNER -> AppPage.entries.toSet()
        ErpRoles.isDirector(role) -> AppPage.entries.toSet() - AppPage.USERS
        ErpRoles.isManagerEduTrans(role) -> ManagerEduTransPolicy.operationalPages
        role == "Admin" -> setOf(
            AppPage.DASHBOARD, AppPage.BOOKINGS, AppPage.BOOKING_REQUESTS, AppPage.QUOTATIONS,
            AppPage.PACKAGE_MASTER, AppPage.CUSTOMERS, AppPage.OPERATIONS, AppPage.VENDORS,
            AppPage.TRIP_FOLDER, AppPage.WORKFLOW, AppPage.SOP, AppPage.REPORTS,
            AppPage.AUDIT, AppPage.PROFILE
        )
        role == "Sales" -> setOf(
            AppPage.DASHBOARD, AppPage.SALES_FORECAST, AppPage.BOOKINGS, AppPage.BOOKING_REQUESTS,
            AppPage.QUOTATIONS, AppPage.CUSTOMERS, AppPage.PROFILE
        )
        role == "Finance" -> setOf(
            AppPage.DASHBOARD, AppPage.BOOKINGS, AppPage.FINANCE, AppPage.PAYMENT_GATEWAY,
            AppPage.PLANNING, AppPage.WORKFLOW, AppPage.REPORTS, AppPage.CLOSING,
            AppPage.AUDIT, AppPage.PROFILE
        )
        role == "Operation" -> setOf(
            AppPage.DASHBOARD, AppPage.BOOKINGS, AppPage.OPERATIONS,
            AppPage.VENDORS, AppPage.TRIP_FOLDER, AppPage.WORKFLOW,
            AppPage.SOP, AppPage.REPORTS, AppPage.AUDIT, AppPage.PROFILE
        )
        role == "TL" -> setOf(
            AppPage.DASHBOARD, AppPage.OPERATIONS, AppPage.TRIP_FOLDER,
            AppPage.SOP, AppPage.REPORTS, AppPage.PROFILE
        )
        else -> setOf(AppPage.DASHBOARD, AppPage.PROFILE)
    }
}
