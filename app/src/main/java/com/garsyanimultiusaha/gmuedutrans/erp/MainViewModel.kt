package com.garsyanimultiusaha.gmuedutrans.erp

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseApi()
    private val prefs = application.getSharedPreferences("gmu_native_session", Context.MODE_PRIVATE)

    var state by mutableStateOf<AppState>(AppState.Loading)
        private set
    var currentPage by mutableStateOf(AppPage.DASHBOARD)
        private set
    var customers by mutableStateOf<List<Customer>>(emptyList())
        private set
    var bookings by mutableStateOf<List<Booking>>(emptyList())
        private set
    var dataBusy by mutableStateOf(false)
        private set
    var actionBusy by mutableStateOf(false)
        private set
    var dataError by mutableStateOf<String?>(null)
        private set

    init { restoreSession() }

    private fun restoreSession() {
        viewModelScope.launch {
            val access = prefs.getString("access", null)
            val refresh = prefs.getString("refresh", "") ?: ""
            val uid = prefs.getString("uid", null)
            if (uid.isNullOrBlank() || (access.isNullOrBlank() && refresh.isBlank())) {
                state = AppState.LoggedOut
                return@launch
            }
            try {
                val session = if (!access.isNullOrBlank()) {
                    runCatching {
                        val profile = api.fetchProfile(access, uid)
                        SessionState(access, refresh, uid, profile)
                    }.getOrElse {
                        if (refresh.isBlank()) throw it else api.refresh(refresh)
                    }
                } else api.refresh(refresh)
                persist(session)
                state = AppState.LoggedIn(session)
                loadData()
            } catch (_: Exception) {
                clearSession()
                state = AppState.LoggedOut
            }
        }
    }

    fun login(email: String, password: String) {
        actionBusy = true
        state = AppState.Loading
        viewModelScope.launch {
            state = try {
                val session = api.signIn(email, password)
                persist(session)
                currentPage = AppPage.DASHBOARD
                loadData(session)
                AppState.LoggedIn(session)
            } catch (e: Exception) {
                AppState.Error(e.message ?: "Login gagal")
            }
            actionBusy = false
        }
    }

    fun backToLogin() { state = AppState.LoggedOut }

    fun navigate(page: AppPage) { currentPage = page }

    fun loadData(sessionOverride: SessionState? = null) {
        val session = sessionOverride ?: (state as? AppState.LoggedIn)?.session ?: return
        dataBusy = true
        dataError = null
        viewModelScope.launch {
            try {
                val cs = api.getCustomers(session.accessToken)
                val bs = api.getBookings(session.accessToken, cs)
                customers = cs
                bookings = bs
            } catch (e: Exception) {
                dataError = e.message ?: "Gagal memuat data"
            }
            dataBusy = false
        }
    }

    fun createCustomer(name: String, type: String, pic: String, wa: String, email: String, done: (Boolean, String) -> Unit) {
        val session = (state as? AppState.LoggedIn)?.session ?: return
        actionBusy = true
        viewModelScope.launch {
            try {
                api.createCustomer(session.accessToken, session.userId, name, type, pic, wa, email)
                loadData(session)
                done(true, "Customer berhasil dibuat.")
            } catch (e: Exception) {
                done(false, e.message ?: "Gagal membuat customer")
            }
            actionBusy = false
        }
    }

    fun createBooking(
        customerId: String,
        program: String,
        tripDate: String,
        pax: Int,
        price: Double,
        status: String,
        group: String,
        meeting: String,
        done: (Boolean, String) -> Unit
    ) {
        val session = (state as? AppState.LoggedIn)?.session ?: return
        actionBusy = true
        viewModelScope.launch {
            try {
                api.createBooking(
                    session.accessToken, session.userId, session.profile.role,
                    customerId, program, tripDate, pax, price, status, group, meeting
                )
                loadData(session)
                done(true, "Booking berhasil dibuat.")
            } catch (e: Exception) {
                done(false, e.message ?: "Gagal membuat booking")
            }
            actionBusy = false
        }
    }

    fun dashboardStats(): DashboardStats {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val pax = bookings.sumOf { it.pax }
        val omzet = bookings.sumOf { it.omzet }
        val upcoming = bookings.count { it.tripDate >= today && it.status !in listOf("Completed", "Closed") }
        val top = bookings.groupBy { it.programName }
            .mapValues { (_, v) -> v.sumOf { it.omzet } }
            .entries.sortedByDescending { it.value }
            .take(3).map { it.key to it.value }
        return DashboardStats(bookings.size, customers.size, pax, omzet, upcoming, top)
    }

    fun logout() {
        val session = (state as? AppState.LoggedIn)?.session
        clearSession()
        customers = emptyList()
        bookings = emptyList()
        state = AppState.LoggedOut
        if (session != null) viewModelScope.launch { api.signOut(session.accessToken) }
    }

    private fun persist(session: SessionState) {
        prefs.edit()
            .putString("access", session.accessToken)
            .putString("refresh", session.refreshToken)
            .putString("uid", session.userId)
            .apply()
    }

    private fun clearSession() { prefs.edit().clear().apply() }
}
