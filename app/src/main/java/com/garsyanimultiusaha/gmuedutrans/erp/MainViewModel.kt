package com.garsyanimultiusaha.gmuedutrans.erp

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseApi()
    private val masterKey = MasterKey.Builder(application)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val prefs = EncryptedSharedPreferences.create(
        application,
        "gmu_native_session_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var state by mutableStateOf<AppState>(AppState.Splash)
        private set
    var currentPage by mutableStateOf(AppPage.DASHBOARD)
        private set
    var customers by mutableStateOf<List<Customer>>(emptyList())
        private set
    var bookings by mutableStateOf<List<Booking>>(emptyList())
        private set
    var rows by mutableStateOf<Map<String, List<ErpRow>>>(emptyMap())
        private set
    var dataBusy by mutableStateOf(false)
        private set
    var actionBusy by mutableStateOf(false)
        private set
    var dataError by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            delay(1100)
            restoreSession()
        }
    }

    private suspend fun restoreSession() {
        val access = prefs.getString("access", null)
        val refresh = prefs.getString("refresh", "") ?: ""
        val uid = prefs.getString("uid", null)
        if (uid.isNullOrBlank() || (access.isNullOrBlank() && refresh.isBlank())) {
            state = AppState.LoggedOut
            return
        }
        state = AppState.Loading
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
            loadAll(session)
        } catch (_: Exception) {
            clearSession()
            state = AppState.LoggedOut
        }
    }

    fun login(email: String, password: String) {
        if (actionBusy) return
        actionBusy = true
        state = AppState.Loading
        viewModelScope.launch {
            state = try {
                val session = api.signIn(email, password)
                persist(session)
                currentPage = AppPage.DASHBOARD
                loadAll(session)
                AppState.LoggedIn(session)
            } catch (e: Exception) {
                AppState.Error(e.message ?: "Login gagal")
            }
            actionBusy = false
        }
    }

    fun backToLogin() {
        state = AppState.LoggedOut
    }

    fun navigate(page: AppPage) {
        val role = (state as? AppState.LoggedIn)?.session?.profile?.role ?: return
        currentPage = if (page in RoleAccess.pages(role)) page else AppPage.DASHBOARD
    }

    fun table(name: String): List<ErpRow> = rows[name].orEmpty()

    fun loadAll(sessionOverride: SessionState? = null) {
        val session = sessionOverride ?: (state as? AppState.LoggedIn)?.session ?: return
        if (dataBusy) return
        dataBusy = true
        dataError = null
        viewModelScope.launch {
            try {
                val cs = runCatching { api.getCustomers(session.accessToken) }.getOrElse { emptyList() }
                val rawBookings = runCatching { api.getBookings(session.accessToken, cs) }.getOrElse { emptyList() }
                val canSeeFinancials = FinancialAccess.canView(session.profile.role)
                val bs = if (canSeeFinancials) rawBookings else rawBookings.map { it.copy(pricePerPax = 0.0) }
                customers = cs
                bookings = bs

                val wanted = tablesForRole(session.profile.role)
                val loaded = linkedMapOf<String, List<ErpRow>>()
                var firstError: String? = null
                for ((name, order) in wanted) {
                    try {
                        val loadedRows = api.getRows(session.accessToken, name, order)
                        loaded[name] = if (!FinancialAccess.canView(session.profile.role) && name == "vendor_pos") {
                            loadedRows.map { row ->
                                row.copy(data = row.data - "amount" - "approved_by" - "approved_at")
                            }
                        } else loadedRows
                    } catch (e: Exception) {
                        loaded[name] = emptyList()
                        if (firstError == null) firstError = e.message
                    }
                }
                rows = loaded
                dataError = firstError
            } catch (e: Exception) {
                dataError = e.message ?: "Gagal memuat data ERP."
            }
            dataBusy = false
        }
    }

    private fun tablesForRole(role: String): List<Pair<String, String?>> {
        val wanted = mutableListOf<Pair<String, String?>>()

        if (FinancialAccess.canView(role)) {
            wanted += "payments" to "payment_date.desc"
            wanted += "trip_costs" to "created_at.desc"
        }

        if (role in listOf("Owner", "Manager", "Admin", "Operation", "TL")) {
            wanted += "trips" to "updated_at.desc"
            wanted += "operation_sheets" to "updated_at.desc"
            wanted += "manifests" to null
            wanted += "attendance" to null
            wanted += "rundown_items" to null
            wanted += "documents" to "generated_at.desc"
            wanted += "trip_reports" to "created_at.desc"
            wanted += "evaluations" to "created_at.desc"
            wanted += "sop_deadlines" to null
        }

        if (role in listOf("Owner", "Manager", "Operation")) {
            wanted += "vendors" to "created_at.desc"
            wanted += "vendor_pos" to "created_at.desc"
        }

        if (role in listOf("Owner", "Manager", "Operation", "Admin")) {
            wanted += "approvals" to "requested_at.desc"
        }

        if (FinancialAccess.canView(role)) {
            wanted += "trip_closings" to "closed_at.desc"
        }

        if (role in listOf("Owner", "Manager", "Admin", "Finance", "Operation")) {
            wanted += "audit_logs" to "created_at.desc"
        }

        if (role in listOf("Owner", "Manager", "Operation")) {
            wanted += "profiles" to "created_at.desc"
        }

        if (role in listOf("Owner", "Manager")) {
            wanted += "staff_attendance" to "attendance_date.desc"
            wanted += "staff_assignments" to "created_at.desc"
            wanted += "staff_kpis" to "created_at.desc"
            wanted += "staff_reviews" to "reviewed_at.desc"
            wanted += "staff_leave" to "created_at.desc"
            wanted += "staff_warnings" to "created_at.desc"
            wanted += "staff_training" to "training_date.desc"
            wanted += "staff_contracts" to "created_at.desc"
            wanted += "staff_offboarding" to "created_at.desc"
        }

        return wanted.distinctBy { it.first }
    }

    fun createCustomer(name: String, type: String, pic: String, wa: String, email: String, done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        actionBusy = true
        viewModelScope.launch {
            try {
                api.createCustomer(session.accessToken, session.userId, name, type, pic, wa, email)
                done(true, "Customer berhasil dibuat.")
                loadAll(session)
            } catch (e: Exception) {
                done(false, e.message ?: "Gagal membuat customer.")
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
        val session = activeSession() ?: return
        actionBusy = true
        viewModelScope.launch {
            try {
                api.createBooking(
                    session.accessToken,
                    session.userId,
                    session.profile.role,
                    customerId,
                    program,
                    tripDate,
                    pax,
                    price,
                    status,
                    group,
                    meeting
                )
                done(true, "Booking berhasil dibuat.")
                loadAll(session)
            } catch (e: Exception) {
                done(false, e.message ?: "Gagal membuat booking.")
            }
            actionBusy = false
        }
    }

    fun insert(table: String, values: Map<String, Any?>, successMessage: String, done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        actionBusy = true
        viewModelScope.launch {
            try {
                api.insertRow(session.accessToken, table, values)
                api.audit(session.accessToken, session.userId, "CREATE", table, "", successMessage)
                done(true, successMessage)
                loadAll(session)
            } catch (e: Exception) {
                done(false, e.message ?: "Gagal menyimpan data.")
            }
            actionBusy = false
        }
    }

    fun update(table: String, id: String, values: Map<String, Any?>, successMessage: String, done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        actionBusy = true
        viewModelScope.launch {
            try {
                api.updateRow(session.accessToken, table, id, values)
                api.audit(session.accessToken, session.userId, "UPDATE", table, id, successMessage)
                done(true, successMessage)
                loadAll(session)
            } catch (e: Exception) {
                done(false, e.message ?: "Gagal memperbarui data.")
            }
            actionBusy = false
        }
    }

    fun approve(approvalId: String, approved: Boolean, notes: String, done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        actionBusy = true
        viewModelScope.launch {
            try {
                api.approve(session.accessToken, approvalId, session.userId, approved, notes)
                val message = if (approved) "Approval disetujui." else "Approval ditolak."
                api.audit(session.accessToken, session.userId, if (approved) "APPROVE" else "REJECT", "approvals", approvalId, message)
                done(true, message)
                loadAll(session)
            } catch (e: Exception) {
                done(false, e.message ?: "Approval gagal.")
            }
            actionBusy = false
        }
    }

    fun createStaff(
        fullName: String,
        email: String,
        phone: String,
        role: String,
        password: String,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (session.profile.role != "Owner") {
            done(false, "Hanya Owner yang dapat membuat akun staf.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.createStaff(session.accessToken, fullName, email, phone, role, password)
                done(true, "Akun staf berhasil dibuat.")
                loadAll(session)
            } catch (e: Exception) {
                done(false, e.message ?: "Gagal membuat akun staf.")
            }
            actionBusy = false
        }
    }

    fun setStaffActive(id: String, active: Boolean, done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        if (session.profile.role != "Owner") {
            done(false, "Hanya Owner yang dapat mengubah akun staf.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.updateProfile(session.accessToken, id, active = active)
                done(true, if (active) "Akun diaktifkan." else "Akun dinonaktifkan.")
                loadAll(session)
            } catch (e: Exception) {
                done(false, e.message ?: "Gagal mengubah akun.")
            }
            actionBusy = false
        }
    }

    fun setStaffRole(id: String, role: String, done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        if (session.profile.role != "Owner") {
            done(false, "Hanya Owner yang dapat mengubah role.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.updateProfile(session.accessToken, id, role = role)
                done(true, "Role staf berhasil diperbarui.")
                loadAll(session)
            } catch (e: Exception) {
                done(false, e.message ?: "Gagal mengubah role.")
            }
            actionBusy = false
        }
    }

    fun resetStaffPassword(id: String, password: String, done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        if (session.profile.role != "Owner") {
            done(false, "Hanya Owner yang dapat reset password staf.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.resetStaffPassword(session.accessToken, id, password)
                done(true, "Password staf berhasil direset.")
            } catch (e: Exception) {
                done(false, e.message ?: "Reset password gagal.")
            }
            actionBusy = false
        }
    }

    fun dashboardStats(): DashboardStats {
        val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val monthBookings = bookings.filter { it.tripDate.startsWith(month) }
        val omzet = monthBookings.sumOf { it.omzet }
        val pax = monthBookings.sumOf { it.pax }

        val payments = table("payments")
        val paidByBooking = payments.groupBy { it.text("booking_id") }.mapValues { (_, list) ->
            list.sumOf { row ->
                val amount = row.number("amount")
                if (row.text("payment_type") == "Refund") -amount else amount
            }
        }
        val paid = monthBookings.sumOf { paidByBooking[it.id] ?: 0.0 }
        val receivable = (omzet - paid).coerceAtLeast(0.0)

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

    fun paidForBooking(bookingId: String): Double =
        table("payments").filter { it.text("booking_id") == bookingId }.sumOf {
            if (it.text("payment_type") == "Refund") -it.number("amount") else it.number("amount")
        }

    fun actualCostForBooking(bookingId: String): Double =
        table("trip_costs").filter { it.text("booking_id") == bookingId }.sumOf { it.number("actual_amount") }

    fun rabForBooking(bookingId: String): Double =
        table("trip_costs").filter { it.text("booking_id") == bookingId }.sumOf { it.number("rab_amount") }

    fun bookingById(id: String): Booking? = bookings.firstOrNull { it.id == id }

    fun logout() {
        val session = activeSession()
        clearSession()
        customers = emptyList()
        bookings = emptyList()
        rows = emptyMap()
        state = AppState.LoggedOut
        if (session != null) viewModelScope.launch { api.signOut(session.accessToken) }
    }

    private fun activeSession(): SessionState? = (state as? AppState.LoggedIn)?.session

    private fun persist(session: SessionState) {
        prefs.edit()
            .putString("access", session.accessToken)
            .putString("refresh", session.refreshToken)
            .putString("uid", session.userId)
            .apply()
    }

    private fun clearSession() {
        prefs.edit().clear().apply()
    }
}
