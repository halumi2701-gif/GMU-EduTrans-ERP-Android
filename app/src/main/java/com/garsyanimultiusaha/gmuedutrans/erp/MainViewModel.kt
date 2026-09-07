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
    var managementDashboard by mutableStateOf<ManagementDashboard?>(null)
        private set
    var managementError by mutableStateOf<String?>(null)
        private set
    var planningDashboard by mutableStateOf<PlanningDashboard?>(null)
        private set
    var planningScenario by mutableStateOf<PlanningScenarioResult?>(null)
        private set
    var planningError by mutableStateOf<String?>(null)
        private set
    var bookingRequests by mutableStateOf<List<BookingRequestItem>>(emptyList())
        private set
    var bookingRequestError by mutableStateOf<String?>(null)
        private set
    var customerPortalCredential by mutableStateOf<CustomerPortalCredential?>(null)
        private set
    var customerPortalTokenError by mutableStateOf<String?>(null)
        private set
    var quotationQueue by mutableStateOf<List<QuotationQueueItem>>(emptyList())
        private set
    var quotationDetail by mutableStateOf<QuotationDetail?>(null)
        private set
    var quotationError by mutableStateOf<String?>(null)
        private set
    var pricingDashboard by mutableStateOf<PricingDashboard?>(null)
        private set
    var pricingError by mutableStateOf<String?>(null)
        private set
    var packageMaster by mutableStateOf<List<PackageMasterItem>>(emptyList())
        private set
    var packageMasterError by mutableStateOf<String?>(null)
        private set
    var pricingMaster by mutableStateOf<PricingMasterDashboard?>(null)
        private set
    var pricingMasterError by mutableStateOf<String?>(null)
        private set
    var paymentGateway by mutableStateOf<PaymentGatewayDashboard?>(null)
        private set
    var paymentGatewayError by mutableStateOf<String?>(null)
        private set
    var quotationSuggestion by mutableStateOf<QuotationDraftSuggestion?>(null)
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
                if (session.profile.role in listOf("Owner", "Manager", "Sales")) {
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

                if (session.profile.role in listOf("Owner", "Manager", "Admin")) {
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

                if (session.profile.role in listOf("Owner", "Manager", "Admin")) {
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
            wanted += "programs" to "sort_order.asc"
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

    fun updateBookingStatus(
        bookingId: String,
        status: String,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (session.profile.role !in listOf("Owner", "Manager", "Admin", "Sales")) {
            done(false, "Tidak memiliki akses update status Booking.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                val saved = api.updateBookingStatus(session.accessToken, bookingId, status)
                done(true, "Status booking berhasil diperbarui menjadi " + saved + ".")
                loadAll(session)
            } catch (e: Exception) {
                done(false, e.message ?: "Status booking gagal diperbarui.")
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

    fun openQuotationRequest(
        requestId: String,
        done: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val session = activeSession() ?: return
        if (session.profile.role !in listOf("Owner", "Manager", "Admin")) {
            done(false, "Tidak memiliki akses Quotation Workflow.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                quotationDetail = api.getQuotationDetail(session.accessToken, requestId)
                quotationSuggestion = null
                quotationError = null
                done(true, "Quotation berhasil dimuat.")
            } catch (e: Exception) {
                quotationDetail = null
                quotationError = e.message ?: "Quotation gagal dimuat."
                done(false, quotationError ?: "Quotation gagal dimuat.")
            }
            actionBusy = false
        }
    }

    fun createQuotationDraft(
        requestId: String,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (session.profile.role !in listOf("Owner", "Manager", "Admin")) {
            done(false, "Tidak memiliki akses membuat draft quotation.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                quotationDetail = api.createQuotationDraft(session.accessToken, requestId)
                quotationSuggestion = null
                quotationQueue = api.getQuotationQueue(session.accessToken)
                done(true, "Draft quotation berhasil dibuat.")
            } catch (e: Exception) {
                done(false, e.message ?: "Draft quotation gagal dibuat.")
            }
            actionBusy = false
        }
    }

    fun saveQuotationDraft(
        items: List<QuotationLine>,
        discount: Double,
        tax: Double,
        validUntil: String,
        notesCustomer: String,
        terms: String,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        val current = quotationDetail ?: run {
            done(false, "Quotation belum dipilih.")
            return
        }
        if (current.quotationId.isBlank()) {
            done(false, "Draft quotation belum dibuat.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                quotationDetail = api.saveQuotationDraft(
                    session.accessToken,current.requestId,current.quotationId,items,
                    discount,tax,validUntil,notesCustomer,terms
                )
                quotationQueue = api.getQuotationQueue(session.accessToken)
                done(true, "Draft quotation berhasil disimpan.")
            } catch (e: Exception) {
                done(false, e.message ?: "Draft quotation gagal disimpan.")
            }
            actionBusy = false
        }
    }

    fun publishQuotation(
        pricingOverrideReason: String,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        val current = quotationDetail ?: run {
            done(false, "Quotation belum dipilih.")
            return
        }
        if (session.profile.role !in listOf("Owner", "Manager")) {
            done(false, "Hanya Owner / Manager yang dapat publish quotation.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                quotationDetail = api.publishQuotation(
                    session.accessToken,current.requestId,current.quotationId,pricingOverrideReason
                )
                quotationQueue = api.getQuotationQueue(session.accessToken)
                if (FinancialAccess.canView(session.profile.role)) {
                    pricingDashboard = runCatching { api.getPricingDashboard(session.accessToken) }.getOrNull()
                }
                done(true, "Quotation berhasil dipublish dan PDF dibuat.")
            } catch (e: Exception) {
                done(false, e.message ?: "Quotation gagal dipublish.")
            }
            actionBusy = false
        }
    }

    fun acceptQuotation(done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        val current = quotationDetail ?: run {
            done(false, "Quotation belum dipilih.")
            return
        }
        if (session.profile.role !in listOf("Owner", "Manager")) {
            done(false, "Hanya Owner / Manager yang dapat menerima quotation.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                quotationDetail = api.acceptQuotation(session.accessToken,current.requestId,current.quotationId)
                quotationQueue = api.getQuotationQueue(session.accessToken)
                bookingRequests = runCatching { api.getBookingRequests(session.accessToken) }.getOrElse { bookingRequests }
                done(true, "Quotation diterima. Pengajuan masuk Waiting DP.")
            } catch (e: Exception) {
                done(false, e.message ?: "Quotation gagal diterima.")
            }
            actionBusy = false
        }
    }

    fun rejectQuotation(reason: String, done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        val current = quotationDetail ?: run {
            done(false, "Quotation belum dipilih.")
            return
        }
        if (session.profile.role !in listOf("Owner", "Manager")) {
            done(false, "Hanya Owner / Manager yang dapat menolak quotation.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                quotationDetail = api.rejectQuotation(session.accessToken,current.requestId,current.quotationId,reason)
                quotationQueue = api.getQuotationQueue(session.accessToken)
                bookingRequests = runCatching { api.getBookingRequests(session.accessToken) }.getOrElse { bookingRequests }
                done(true, "Quotation ditolak. Pengajuan kembali ke Verifikasi.")
            } catch (e: Exception) {
                done(false, e.message ?: "Quotation gagal ditolak.")
            }
            actionBusy = false
        }
    }

    fun savePricingPolicy(
        policyId: String?,
        scopeType: String,
        programId: String?,
        targetMarginPct: Double?,
        floorMarginPct: Double?,
        maxDiscountPct: Double?,
        contingencyPct: Double,
        roundingIncrement: Double,
        effectiveFrom: String,
        effectiveUntil: String?,
        notes: String,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (!FinancialAccess.canView(session.profile.role)) {
            done(false, "Pricing Policy hanya untuk Owner / Manager.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.savePricingPolicy(
                    session.accessToken,policyId,scopeType,programId,targetMarginPct,
                    floorMarginPct,maxDiscountPct,contingencyPct,roundingIncrement,
                    effectiveFrom,effectiveUntil,notes
                )
                pricingDashboard = api.getPricingDashboard(session.accessToken)
                quotationDetail?.requestId?.takeIf { it.isNotBlank() }?.let {
                    quotationDetail = runCatching { api.getQuotationDetail(session.accessToken,it) }.getOrNull()
                }
                done(true, "Pricing Policy berhasil disimpan.")
            } catch (e: Exception) {
                done(false, e.message ?: "Pricing Policy gagal disimpan.")
            }
            actionBusy = false
        }
    }

    fun loadQuotationDraftSuggestion(done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        val current = quotationDetail ?: run {
            done(false, "Quotation belum dipilih.")
            return
        }
        if (current.quotationId.isBlank()) {
            done(false, "Draft quotation belum tersedia.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                quotationSuggestion = api.getQuotationDraftSuggestion(
                    session.accessToken,
                    current.quotationId
                )
                done(
                    true,
                    if (quotationSuggestion?.ready == true) {
                        "Saran quotation siap diterapkan."
                    } else {
                        "Saran belum siap. Periksa blocker Pricing Master."
                    }
                )
            } catch (e: Exception) {
                quotationSuggestion = null
                done(false, e.message ?: "Saran quotation gagal dimuat.")
            }
            actionBusy = false
        }
    }

    fun applyQuotationDraftSuggestion(done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        val current = quotationDetail ?: run {
            done(false, "Quotation belum dipilih.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.applyQuotationDraftSuggestion(session.accessToken, current.quotationId)
                quotationDetail = api.getQuotationDetail(session.accessToken, current.requestId)
                quotationSuggestion = null
                quotationQueue = api.getQuotationQueue(session.accessToken)
                done(true, "Saran harga berhasil diterapkan ke Draft Quotation.")
            } catch (e: Exception) {
                done(false, e.message ?: "Saran quotation gagal diterapkan.")
            }
            actionBusy = false
        }
    }

    fun savePackageDraft(
        existingId: String?,
        programId: String,
        name: String,
        description: String,
        pricePerPax: Double,
        minPax: Int,
        facilities: List<String>,
        priceNote: String,
        effectiveFrom: String,
        effectiveUntil: String,
        sortOrder: Int,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (session.profile.role !in listOf("Owner", "Manager", "Admin")) {
            done(false, "Tidak memiliki akses Master Paket.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                if (existingId.isNullOrBlank()) {
                    api.createPackageDraft(
                        session.accessToken,programId,name,description,pricePerPax,minPax,
                        facilities,priceNote,effectiveFrom,effectiveUntil,sortOrder
                    )
                } else {
                    api.updatePackageDraft(
                        session.accessToken,existingId,name,description,pricePerPax,minPax,
                        facilities,priceNote,effectiveFrom,effectiveUntil,sortOrder
                    )
                }
                packageMaster = api.getPackageMaster(session.accessToken)
                if (FinancialAccess.canView(session.profile.role)) {
                    pricingMaster = runCatching {
                        api.getPricingMasterDashboard(session.accessToken)
                    }.getOrNull()
                }
                done(true, if (existingId.isNullOrBlank()) "Draft paket berhasil dibuat." else "Draft paket berhasil diperbarui.")
            } catch (e: Exception) {
                done(false, e.message ?: "Master Paket gagal disimpan.")
            }
            actionBusy = false
        }
    }

    fun clonePackage(id: String, done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        actionBusy = true
        viewModelScope.launch {
            try {
                api.clonePackage(session.accessToken,id)
                packageMaster = api.getPackageMaster(session.accessToken)
                done(true, "Paket berhasil di-clone menjadi Draft baru.")
            } catch (e: Exception) {
                done(false, e.message ?: "Clone paket gagal.")
            }
            actionBusy = false
        }
    }

    fun archivePackage(id: String, done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        if (!FinancialAccess.canView(session.profile.role)) {
            done(false, "Hanya Owner / Manager yang dapat mengarsipkan paket.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.archivePackage(session.accessToken,id)
                packageMaster = api.getPackageMaster(session.accessToken)
                pricingMaster = runCatching {
                    api.getPricingMasterDashboard(session.accessToken)
                }.getOrNull()
                done(true, "Paket berhasil diarsipkan.")
            } catch (e: Exception) {
                done(false, e.message ?: "Paket gagal diarsipkan.")
            }
            actionBusy = false
        }
    }

    fun applyRecommendedPackagePrice(id: String, done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        if (!FinancialAccess.canView(session.profile.role)) {
            done(false, "Hanya Owner / Manager yang dapat menerapkan recommended price.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.applyRecommendedPackagePrice(session.accessToken,id)
                packageMaster = api.getPackageMaster(session.accessToken)
                pricingMaster = api.getPricingMasterDashboard(session.accessToken)
                done(true, "Recommended price berhasil diterapkan. Paket tetap DRAFT.")
            } catch (e: Exception) {
                done(false, e.message ?: "Recommended price gagal diterapkan.")
            }
            actionBusy = false
        }
    }

    fun activatePackage(id: String, done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        if (!FinancialAccess.canView(session.profile.role)) {
            done(false, "Hanya Owner / Manager yang dapat mengaktifkan paket.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.activatePackage(session.accessToken,id)
                packageMaster = api.getPackageMaster(session.accessToken)
                pricingMaster = api.getPricingMasterDashboard(session.accessToken)
                done(true, "Paket ACTIVE dan siap tampil di Web Customer.")
            } catch (e: Exception) {
                done(false, e.message ?: "Paket belum dapat diaktifkan.")
            }
            actionBusy = false
        }
    }

    fun saveCostTemplate(
        templateId: String?,
        scopeType: String,
        programId: String?,
        packageId: String?,
        category: String,
        description: String,
        costMode: String,
        amount: Double,
        minPax: Int?,
        maxPax: Int?,
        effectiveFrom: String,
        effectiveUntil: String?,
        notes: String,
        active: Boolean,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (!FinancialAccess.canView(session.profile.role)) {
            done(false, "Cost Template hanya untuk Owner / Manager.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.saveCostTemplate(
                    session.accessToken,templateId,scopeType,programId,packageId,
                    category,description,costMode,amount,minPax,maxPax,
                    effectiveFrom,effectiveUntil,notes,active
                )
                pricingMaster = api.getPricingMasterDashboard(session.accessToken)
                pricingDashboard = runCatching { api.getPricingDashboard(session.accessToken) }.getOrNull()
                done(true, "Cost Template berhasil disimpan.")
            } catch (e: Exception) {
                done(false, e.message ?: "Cost Template gagal disimpan.")
            }
            actionBusy = false
        }
    }

    fun setPaymentChannel(
        code: String,
        enabled: Boolean,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (!FinancialAccess.canView(session.profile.role)) {
            done(false, "Payment Gateway hanya untuk Owner / Manager.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.setPaymentChannel(session.accessToken,code,enabled)
                paymentGateway = api.getPaymentGatewayDashboard(session.accessToken)
                done(true, if (enabled) "$code berhasil diaktifkan." else "$code dinonaktifkan.")
            } catch (e: Exception) {
                paymentGateway = runCatching {
                    api.getPaymentGatewayDashboard(session.accessToken)
                }.getOrNull()
                done(false, e.message ?: "Payment channel gagal diperbarui.")
            }
            actionBusy = false
        }
    }

    fun refreshCommerce() {
        val session = activeSession() ?: return
        if (actionBusy) return
        actionBusy = true
        viewModelScope.launch {
            try {
                if (session.profile.role in listOf("Owner","Manager","Admin")) {
                    packageMaster = api.getPackageMaster(session.accessToken)
                    packageMasterError = null
                }
                if (FinancialAccess.canView(session.profile.role)) {
                    pricingMaster = api.getPricingMasterDashboard(session.accessToken)
                    paymentGateway = api.getPaymentGatewayDashboard(session.accessToken)
                    pricingMasterError = null
                    paymentGatewayError = null
                }
            } catch (e: Exception) {
                dataError = e.message ?: "Refresh commerce gagal."
            }
            actionBusy = false
        }
    }

    fun clearQuotationDetail() {
        quotationDetail = null
        quotationSuggestion = null
        quotationError = null
    }

    fun loadCustomerPortalToken(
        requestId: String,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (session.profile.role !in listOf("Owner", "Manager", "Sales")) {
            done(false, "Hanya Owner / Manager / Sales yang dapat melihat token customer.")
            return
        }
        actionBusy = true
        customerPortalTokenError = null
        viewModelScope.launch {
            try {
                customerPortalCredential = api.getBookingRequestToken(session.accessToken, requestId)
                done(true, "Token customer berhasil dimuat.")
            } catch (e: Exception) {
                customerPortalCredential = null
                customerPortalTokenError = e.message ?: "Token customer gagal dimuat."
                done(false, customerPortalTokenError ?: "Token customer gagal dimuat.")
            }
            actionBusy = false
        }
    }

    fun clearCustomerPortalToken() {
        customerPortalCredential = null
        customerPortalTokenError = null
    }

    fun startBookingRequestQuotation(
        requestId: String,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (session.profile.role !in listOf("Owner", "Manager")) {
            done(false, "Hanya Owner / Manager yang dapat memulai quotation.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                val quotationNo = api.startBookingRequestQuotation(session.accessToken, requestId)
                bookingRequests = api.getBookingRequests(session.accessToken)
                bookingRequestError = null
                done(
                    true,
                    if (quotationNo.isNotBlank()) {
                        "Status Quotation berhasil disimpan. Draft " + quotationNo + " siap dilengkapi."
                    } else {
                        "Status Quotation berhasil disimpan."
                    }
                )
                loadAll(session)
            } catch (e: Exception) {
                done(false, e.message ?: "Status Quotation gagal disimpan.")
            }
            actionBusy = false
        }
    }

    fun reviewBookingRequest(
        requestId: String,
        accepted: Boolean,
        reason: String = "",
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (session.profile.role !in listOf("Owner", "Manager")) {
            done(false, "Hanya Owner / Manager yang dapat memproses pengajuan.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.reviewBookingRequest(session.accessToken, requestId, accepted, reason)
                bookingRequests = api.getBookingRequests(session.accessToken)
                bookingRequestError = null
                val message = if (accepted) {
                    "Pengajuan diterima dan masuk tahap verifikasi."
                } else {
                    "Pengajuan ditolak."
                }
                done(true, message)
                loadAll(session)
            } catch (e: Exception) {
                done(false, e.message ?: "Status pengajuan gagal diperbarui.")
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

    fun savePlanningTarget(
        targetId: String?,
        periodMonth: String,
        scopeType: String,
        salesId: String?,
        programId: String?,
        targetRevenue: Double,
        targetBookings: Int,
        targetPax: Int,
        targetProfit: Double,
        targetMarginPct: Double,
        targetCashIn: Double,
        notes: String,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (!FinancialAccess.canView(session.profile.role)) {
            done(false, "Planning hanya tersedia untuk Owner dan Manager.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.savePlanningTarget(
                    session.accessToken,targetId,periodMonth,scopeType,salesId,programId,
                    targetRevenue,targetBookings,targetPax,targetProfit,targetMarginPct,targetCashIn,notes
                )
                planningDashboard = api.getPlanningDashboard(session.accessToken)
                planningError = null
                done(true, "Target planning berhasil disimpan.")
            } catch (e: Exception) {
                done(false, e.message ?: "Target planning gagal disimpan.")
            }
            actionBusy = false
        }
    }

    fun savePlanningBudget(
        budgetId: String?,
        periodMonth: String,
        budgetType: String,
        category: String,
        programId: String?,
        amount: Double,
        notes: String,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (!FinancialAccess.canView(session.profile.role)) {
            done(false, "Planning hanya tersedia untuk Owner dan Manager.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.savePlanningBudget(session.accessToken,budgetId,periodMonth,budgetType,category,programId,amount,notes)
                planningDashboard = api.getPlanningDashboard(session.accessToken)
                planningError = null
                done(true, "Budget planning berhasil disimpan.")
            } catch (e: Exception) {
                done(false, e.message ?: "Budget planning gagal disimpan.")
            }
            actionBusy = false
        }
    }

    fun deletePlanningBudget(budgetId: String, done: (Boolean, String) -> Unit) {
        val session = activeSession() ?: return
        if (!FinancialAccess.canView(session.profile.role)) {
            done(false, "Planning hanya tersedia untuk Owner dan Manager.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.deletePlanningBudget(session.accessToken,budgetId)
                planningDashboard = api.getPlanningDashboard(session.accessToken)
                done(true, "Budget planning dihapus.")
            } catch (e: Exception) {
                done(false, e.message ?: "Budget planning gagal dihapus.")
            }
            actionBusy = false
        }
    }

    fun setPlanningPipelineWeight(
        status: String,
        probabilityPct: Double,
        notes: String,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (!FinancialAccess.canView(session.profile.role)) {
            done(false, "Planning hanya tersedia untuk Owner dan Manager.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                api.setPlanningPipelineWeight(session.accessToken,status,probabilityPct,notes)
                planningDashboard = api.getPlanningDashboard(session.accessToken)
                done(true, "Bobot pipeline diperbarui.")
            } catch (e: Exception) {
                done(false, e.message ?: "Bobot pipeline gagal diperbarui.")
            }
            actionBusy = false
        }
    }

    fun runPlanningScenario(
        bookingId: String,
        pax: Int?,
        pricePerPax: Double?,
        vendorIncreasePct: Double,
        transportIncreasePct: Double,
        discountPct: Double,
        variableCostSharePct: Double,
        done: (Boolean, String) -> Unit
    ) {
        val session = activeSession() ?: return
        if (!FinancialAccess.canView(session.profile.role)) {
            done(false, "Scenario Simulator hanya tersedia untuk Owner dan Manager.")
            return
        }
        actionBusy = true
        viewModelScope.launch {
            try {
                planningScenario = api.runPlanningScenario(
                    session.accessToken,bookingId,pax,pricePerPax,vendorIncreasePct,
                    transportIncreasePct,discountPct,variableCostSharePct
                )
                done(true, "Scenario berhasil dihitung.")
            } catch (e: Exception) {
                planningScenario = null
                done(false, e.message ?: "Scenario gagal dihitung.")
            }
            actionBusy = false
        }
    }

    fun clearPlanningScenario() {
        planningScenario = null
    }

    fun dashboardStats(): DashboardStats {
        val management = managementDashboard
        if (management != null) {
            val k = management.kpi
            return DashboardStats(
                bookingsMonth = k.bookingCount,
                customers = customers.size,
                pax = k.paxTotal,
                omzet = k.revenue,
                paid = k.cashCollected,
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
