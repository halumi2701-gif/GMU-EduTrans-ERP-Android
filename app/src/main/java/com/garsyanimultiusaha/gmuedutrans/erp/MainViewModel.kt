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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    // Session/loading coordinator only; business actions are split by domain extension files.
    internal val api = SupabaseApi()
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
        internal set
    var currentPage by mutableStateOf(AppPage.DASHBOARD)
        internal set
    var customers by mutableStateOf<List<Customer>>(emptyList())
        internal set
    var bookings by mutableStateOf<List<Booking>>(emptyList())
        internal set
    var rows by mutableStateOf<Map<String, List<ErpRow>>>(emptyMap())
        internal set
    var dataBusy by mutableStateOf(false)
        internal set
    var actionBusy by mutableStateOf(false)
        internal set
    var dataError by mutableStateOf<String?>(null)
        internal set
    var managementDashboard by mutableStateOf<ManagementDashboard?>(null)
        internal set
    var managementError by mutableStateOf<String?>(null)
        internal set
    var planningDashboard by mutableStateOf<PlanningDashboard?>(null)
        internal set
    var planningScenario by mutableStateOf<PlanningScenarioResult?>(null)
        internal set
    var planningError by mutableStateOf<String?>(null)
        internal set
    var bookingRequests by mutableStateOf<List<BookingRequestItem>>(emptyList())
        internal set
    var bookingRequestError by mutableStateOf<String?>(null)
        internal set
    var customerPortalCredential by mutableStateOf<CustomerPortalCredential?>(null)
        internal set
    var customerPortalTokenError by mutableStateOf<String?>(null)
        internal set
    var quotationQueue by mutableStateOf<List<QuotationQueueItem>>(emptyList())
        internal set
    var quotationDetail by mutableStateOf<QuotationDetail?>(null)
        internal set
    var quotationError by mutableStateOf<String?>(null)
        internal set
    var pricingDashboard by mutableStateOf<PricingDashboard?>(null)
        internal set
    var pricingError by mutableStateOf<String?>(null)
        internal set
    var packageMaster by mutableStateOf<List<PackageMasterItem>>(emptyList())
        internal set
    var packageMasterError by mutableStateOf<String?>(null)
        internal set
    var pricingMaster by mutableStateOf<PricingMasterDashboard?>(null)
        internal set
    var pricingMasterError by mutableStateOf<String?>(null)
        internal set
    var paymentGateway by mutableStateOf<PaymentGatewayDashboard?>(null)
        internal set
    var paymentGatewayError by mutableStateOf<String?>(null)
        internal set
    var quotationSuggestion by mutableStateOf<QuotationDraftSuggestion?>(null)
        internal set

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

                val wanted = ErpDataRegistry.tablesForRole(session.profile.role)
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
                if (ErpRolePolicy.canHandleBookingIntake(session.profile.role)) {
                    try {
                        bookingRequests = api.getBookingRequests(session.accessToken)
                        bookingRequestError = null
                    } catch (e: Exception) {
                        bookingRequests = emptyList()
                        bookingRequestError = e.message ?: "Pengajuan Website gagal dimuat."
                    }
                } else {
                    bookingRequests = emptyList()
                    bookingRequestError = null
                }

                if (ErpRolePolicy.canManageQuotationDrafts(session.profile.role)) {
                    try {
                        quotationQueue = api.getQuotationQueue(session.accessToken)
                        quotationError = null
                    } catch (e: Exception) {
                        quotationQueue = emptyList()
                        quotationError = e.message ?: "Quotation Workflow gagal dimuat."
                    }
                } else {
                    quotationQueue = emptyList()
                    quotationDetail = null
                    quotationError = null
                }

                if (ErpRolePolicy.canManagePackages(session.profile.role)) {
                    try {
                        packageMaster = api.getPackageMaster(session.accessToken)
                        packageMasterError = null
                    } catch (e: Exception) {
                        packageMaster = emptyList()
                        packageMasterError = e.message ?: "Master Paket gagal dimuat."
                    }
                } else {
                    packageMaster = emptyList()
                    packageMasterError = null
                }

                if (FinancialAccess.canView(session.profile.role)) {
                    try {
                        pricingDashboard = api.getPricingDashboard(session.accessToken)
                        pricingError = null
                    } catch (e: Exception) {
                        pricingDashboard = null
                        pricingError = e.message ?: "Pricing Intelligence gagal dimuat."
                    }
                    try {
                        pricingMaster = api.getPricingMasterDashboard(session.accessToken)
                        pricingMasterError = null
                    } catch (e: Exception) {
                        pricingMaster = null
                        pricingMasterError = e.message ?: "Pricing Master gagal dimuat."
                    }
                    try {
                        paymentGateway = api.getPaymentGatewayDashboard(session.accessToken)
                        paymentGatewayError = null
                    } catch (e: Exception) {
                        paymentGateway = null
                        paymentGatewayError = e.message ?: "Payment Gateway gagal dimuat."
                    }
                    try {
                        managementDashboard = api.getManagementDashboard(session.accessToken)
                        managementError = null
                    } catch (e: Exception) {
                        managementDashboard = null
                        managementError = e.message ?: "Management Control gagal dimuat."
                    }
                    try {
                        planningDashboard = api.getPlanningDashboard(session.accessToken)
                        planningError = null
                    } catch (e: Exception) {
                        planningDashboard = null
                        planningError = e.message ?: "Planning & Business Control gagal dimuat."
                    }
                } else {
                    pricingDashboard = null
                    pricingError = null
                    pricingMaster = null
                    pricingMasterError = null
                    paymentGateway = null
                    paymentGatewayError = null
                    managementDashboard = null
                    managementError = null
                    planningDashboard = null
                    planningScenario = null
                    planningError = null
                }
            } catch (e: Exception) {
                dataError = e.message ?: "Gagal memuat data ERP."
            }
            dataBusy = false
        }
    }







































    fun logout() {
        val session = activeSession()
        clearSession()
        customers = emptyList()
        bookings = emptyList()
        rows = emptyMap()
        bookingRequests = emptyList()
        bookingRequestError = null
        customerPortalCredential = null
        customerPortalTokenError = null
        quotationQueue = emptyList()
        quotationDetail = null
        quotationError = null
        quotationSuggestion = null
        pricingDashboard = null
        pricingError = null
        packageMaster = emptyList()
        packageMasterError = null
        pricingMaster = null
        pricingMasterError = null
        paymentGateway = null
        paymentGatewayError = null
        managementDashboard = null
        managementError = null
        planningDashboard = null
        planningScenario = null
        planningError = null
        state = AppState.LoggedOut
        if (session != null) viewModelScope.launch { api.signOut(session.accessToken) }
    }

    internal fun activeSession(): SessionState? = (state as? AppState.LoggedIn)?.session

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
