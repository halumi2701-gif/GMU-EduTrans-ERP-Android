package com.garsyanimultiusaha.gmuedutrans.erp

data class StaffProfile(
    val id: String,
    val fullName: String,
    val role: String,
    val active: Boolean
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
    val email: String
)

data class Booking(
    val id: String,
    val bookingNo: String,
    val customerId: String,
    val customerName: String,
    val programName: String,
    val tripDate: String,
    val pax: Int,
    val pricePerPax: Double,
    val status: String,
    val participantGroup: String,
    val meetingPoint: String
) {
    val omzet: Double get() = pax * pricePerPax
}

data class DashboardStats(
    val bookings: Int = 0,
    val customers: Int = 0,
    val pax: Int = 0,
    val omzet: Double = 0.0,
    val upcoming: Int = 0,
    val topPrograms: List<Pair<String, Double>> = emptyList()
)

sealed interface AppState {
    data object Loading : AppState
    data object LoggedOut : AppState
    data class LoggedIn(val session: SessionState) : AppState
    data class Error(val message: String) : AppState
}

enum class AppPage { DASHBOARD, BOOKINGS, CUSTOMERS }
