package com.garsyanimultiusaha.gmuedutrans.sales

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SalesViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SalesApi()
    private val v6Api = SalesV6Api()
    private val v61Api = SalesV61Api()
    private val quotationE2EApi = SalesQuotationE2EApi()
    private val restoreApi = SalesSessionRestoreApi()

    var state by mutableStateOf<SalesAppState>(SalesAppState.Splash)
        private set
    var currentPage by mutableStateOf(SalesPage.DASHBOARD)
        private set
    var dashboard by mutableStateOf(SalesDashboard())
        private set
    var fieldWorkspace by mutableStateOf(V6FieldWorkspace())
        private set
    var dataBusy by mutableStateOf(false)
        private set
    var actionBusy by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            delay(500)
            val saved = SalesSessionStore.read(getApplication())
            if (saved.isBlank()) {
                state = SalesAppState.LoggedOut
                return@launch
            }
            state = SalesAppState.Loading
            runCatching { restoreApi.refresh(saved) }
                .onSuccess { session ->
                    SalesSessionStore.save(getApplication(), session.refreshToken)
                    currentPage = SalesPage.DASHBOARD
                    state = SalesAppState.LoggedIn(session)
                    runCatching { dashboard = loadDashboardE2E(session) }
                        .onFailure { notice = "Session dipulihkan, tetapi dashboard belum dapat dimuat: ${it.message ?: "server data error"}." }
                    runCatching { fieldWorkspace = v6Api.loadWorkspace(session) }
                }
                .onFailure {
                    SalesSessionStore.clear(getApplication())
                    state = SalesAppState.LoggedOut
                }
        }
    }

    fun login(email: String, password: String) {
        if (actionBusy) return
        actionBusy = true
        state = SalesAppState.Loading
        notice = null
        viewModelScope.launch {
            try {
                val session = api.signIn(email, password)
                SalesSessionStore.save(getApplication(), session.refreshToken)
                currentPage = SalesPage.DASHBOARD
                state = SalesAppState.LoggedIn(session)
                try {
                    dashboard = loadDashboardE2E(session)
                } catch (dataError: Exception) {
                    dashboard = SalesDashboard()
                    notice = "Login berhasil, tetapi sebagian data dashboard belum dapat dimuat: ${dataError.message ?: "server data error"}. Tekan refresh untuk mencoba lagi."
                }
                runCatching { fieldWorkspace = v6Api.loadWorkspace(session) }
                    .onFailure { if (notice == null) notice = "Workspace lapangan belum termuat: ${it.message ?: "server data error"}." }
            } catch (authError: Exception) {
                SalesSessionStore.clear(getApplication())
                state = SalesAppState.Error(authError.message ?: "Login gagal")
            } finally {
                actionBusy = false
            }
        }
    }

    fun backToLogin() {
        state = SalesAppState.LoggedOut
        notice = null
    }

    fun navigate(page: SalesPage) {
        currentPage = page
    }

    fun refresh() {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (dataBusy) return
        dataBusy = true
        notice = null
        viewModelScope.launch {
            try {
                dashboard = loadDashboardE2E(session)
                fieldWorkspace = v6Api.loadWorkspace(session)
            } catch (e: Exception) {
                notice = e.message ?: "Data Sales gagal dimuat."
            } finally {
                dataBusy = false
            }
        }
    }

    fun refreshFieldWorkspace() {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (dataBusy) return
        dataBusy = true
        viewModelScope.launch {
            try {
                fieldWorkspace = v6Api.loadWorkspace(session)
            } catch (e: Exception) {
                notice = e.message ?: "Aktivitas lapangan gagal dimuat."
            } finally {
                dataBusy = false
            }
        }
    }

    fun createLead(input: NewLeadInput) {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (actionBusy) return
        actionBusy = true
        notice = null
        viewModelScope.launch {
            try {
                val bookingCode = api.createLead(session, input)
                dashboard = loadDashboardE2E(session)
                currentPage = SalesPage.LEADS
                notice = "$bookingCode berhasil dibuat dan masuk CRM Sales."
            } catch (e: Exception) {
                notice = e.message ?: "Lead baru gagal dibuat."
            } finally {
                actionBusy = false
            }
        }
    }

    fun createQuotationDraft(lead: SalesLead, packageId: String, notes: String?) {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (actionBusy) return
        actionBusy = true
        notice = null
        viewModelScope.launch {
            try {
                val draft = api.createQuotationDraft(session, lead.id, packageId, notes)
                dashboard = loadDashboardE2E(session)
                currentPage = SalesPage.FUNNEL
                notice = "${draft.quotationNo} dibuat untuk ${lead.institutionName}. Total ${rupiahNotice(draft.total)} dan berlaku sampai ${draft.validUntil}."
            } catch (e: Exception) {
                notice = e.message ?: "Draft quotation gagal dibuat."
            } finally {
                actionBusy = false
            }
        }
    }

    fun markQuotationSent(quotation: SalesQuotation) {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (actionBusy) return
        actionBusy = true
        notice = null
        viewModelScope.launch {
            try {
                val quotationNo = api.markQuotationSent(session, quotation.id)
                dashboard = loadDashboardE2E(session)
                currentPage = SalesPage.FUNNEL
                notice = "$quotationNo ditandai terkirim. Menunggu keputusan customer; follow-up otomatis dijadwalkan +2 hari."
            } catch (e: Exception) {
                notice = e.message ?: "Quotation gagal ditandai terkirim."
            } finally {
                actionBusy = false
            }
        }
    }

    fun updateLead(lead: SalesLead, stage: String, nextFollowUpAt: String?) {
        updateLead(lead, stage, nextFollowUpAt, null)
    }

    fun updateLead(lead: SalesLead, stage: String, nextFollowUpAt: String?, lostReason: String?) {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (actionBusy) return
        actionBusy = true
        viewModelScope.launch {
            try {
                v61Api.updateLead(session, lead.id, stage, nextFollowUpAt, lostReason)
                dashboard = loadDashboardE2E(session)
                notice = "${lead.institutionName} diperbarui ke $stage dan aktivitas tercatat."
            } catch (e: Exception) {
                notice = e.message ?: "Lead gagal diperbarui."
            } finally {
                actionBusy = false
            }
        }
    }

    fun attendanceCheckIn(notes: String? = null) {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (actionBusy) return
        actionBusy = true
        viewModelScope.launch {
            try {
                val attendance = v6Api.checkIn(session, notes)
                fieldWorkspace = fieldWorkspace.copy(attendance = attendance)
                notice = "Check-in kerja tercatat pukul ${attendance.checkIn.take(8)}."
            } catch (e: Exception) {
                notice = e.message ?: "Check-in kerja gagal."
            } finally {
                actionBusy = false
            }
        }
    }

    fun attendanceCheckOut(notes: String? = null) {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (actionBusy) return
        actionBusy = true
        viewModelScope.launch {
            try {
                val attendance = v6Api.checkOut(session, notes)
                fieldWorkspace = fieldWorkspace.copy(attendance = attendance)
                notice = "Check-out kerja tercatat pukul ${attendance.checkOut.take(8)}."
            } catch (e: Exception) {
                notice = e.message ?: "Check-out kerja gagal."
            } finally {
                actionBusy = false
            }
        }
    }

    fun visitCheckIn(lead: SalesLead, notes: String? = null) {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (actionBusy) return
        actionBusy = true
        viewModelScope.launch {
            try {
                v6Api.visitCheckIn(session, lead.id, notes)
                fieldWorkspace = v6Api.loadWorkspace(session)
                notice = "Check-in kunjungan ${lead.institutionName} berhasil."
            } catch (e: Exception) {
                notice = e.message ?: "Check-in kunjungan gagal."
            } finally {
                actionBusy = false
            }
        }
    }

    fun visitCheckOut(visit: V6VisitRecord, stage: String, outcome: String?, notes: String?, nextFollowUpAt: String?) {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (actionBusy) return
        actionBusy = true
        viewModelScope.launch {
            try {
                v6Api.visitCheckOut(session, visit.id, stage, outcome, notes, nextFollowUpAt)
                dashboard = loadDashboardE2E(session)
                fieldWorkspace = v6Api.loadWorkspace(session)
                notice = "Kunjungan ${visit.institutionName} selesai dan CRM diperbarui ke $stage."
            } catch (e: Exception) {
                notice = e.message ?: "Check-out kunjungan gagal."
            } finally {
                actionBusy = false
            }
        }
    }

    fun submitDailyReport(obstacles: String?, tomorrowPlan: String?, notes: String?) {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (actionBusy) return
        actionBusy = true
        viewModelScope.launch {
            try {
                val report = v6Api.submitDailyReport(session, obstacles, tomorrowPlan, notes)
                fieldWorkspace = v6Api.loadWorkspace(session)
                notice = "Laporan harian ${report.reportDate} tersimpan. Aktivitas dihitung otomatis dari sistem."
            } catch (e: Exception) {
                notice = e.message ?: "Laporan harian gagal disimpan."
            } finally {
                actionBusy = false
            }
        }
    }

    fun requestSpecialPrice(lead: SalesLead, packageId: String, requestedPrice: Double, reason: String) {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (actionBusy) return
        actionBusy = true
        viewModelScope.launch {
            try {
                v6Api.requestSpecialPrice(session, lead.id, packageId, requestedPrice, reason)
                fieldWorkspace = v6Api.loadWorkspace(session)
                notice = "Permintaan harga khusus ${lead.institutionName} sudah dikirim ke approval Manager/Director."
            } catch (e: Exception) {
                notice = e.message ?: "Permintaan harga khusus gagal dikirim."
            } finally {
                actionBusy = false
            }
        }
    }

    fun consumeNotice() { notice = null }

    fun logout() {
        val session = (state as? SalesAppState.LoggedIn)?.session
        SalesSessionStore.clear(getApplication())
        state = SalesAppState.LoggedOut
        dashboard = SalesDashboard()
        fieldWorkspace = V6FieldWorkspace()
        currentPage = SalesPage.DASHBOARD
        if (session != null) viewModelScope.launch { api.signOut(session.accessToken) }
    }

    private suspend fun loadDashboardE2E(session: SalesSession): SalesDashboard {
        val baseDashboard = api.loadDashboard(session)
        val enrichedQuotations = runCatching { quotationE2EApi.loadMyQuotations(session) }
            .getOrElse { baseDashboard.quotations }
        return baseDashboard.copy(quotations = enrichedQuotations)
    }

    private fun rupiahNotice(value: Double): String = java.text.NumberFormat
        .getCurrencyInstance(java.util.Locale("id", "ID"))
        .format(value)
        .replace(",00", "")
}
