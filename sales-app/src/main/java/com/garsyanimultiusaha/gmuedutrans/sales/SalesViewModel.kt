package com.garsyanimultiusaha.gmuedutrans.sales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SalesViewModel : ViewModel() {
    private val api = SalesApi()
    private val v6Api = SalesV6Api()

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
            delay(850)
            state = SalesAppState.LoggedOut
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
                currentPage = SalesPage.DASHBOARD
                state = SalesAppState.LoggedIn(session)

                try {
                    dashboard = api.loadDashboard(session)
                } catch (dataError: Exception) {
                    dashboard = SalesDashboard()
                    notice = "Login berhasil, tetapi sebagian data dashboard belum dapat dimuat: ${dataError.message ?: "server data error"}. Tekan refresh untuk mencoba lagi."
                }
                runCatching { fieldWorkspace = v6Api.loadWorkspace(session) }
                    .onFailure { if (notice == null) notice = "Workspace lapangan belum termuat: ${it.message ?: "server data error"}." }
            } catch (authError: Exception) {
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
                dashboard = api.loadDashboard(session)
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
                dashboard = api.loadDashboard(session)
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
                dashboard = api.loadDashboard(session)
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
                dashboard = api.loadDashboard(session)
                currentPage = SalesPage.FUNNEL
                notice = "$quotationNo ditandai terkirim. Lead masuk WAITING DP dan follow-up otomatis dijadwalkan +3 hari."
            } catch (e: Exception) {
                notice = e.message ?: "Quotation gagal ditandai terkirim."
            } finally {
                actionBusy = false
            }
        }
    }

    fun updateLead(lead: SalesLead, stage: String, nextFollowUpAt: String?) {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (actionBusy) return
        actionBusy = true
        viewModelScope.launch {
            try {
                api.updateLead(session, lead.id, stage, nextFollowUpAt)
                dashboard = api.loadDashboard(session)
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

    fun visitCheckOut(
        visit: V6VisitRecord,
        stage: String,
        outcome: String?,
        notes: String?,
        nextFollowUpAt: String?
    ) {
        val session = (state as? SalesAppState.LoggedIn)?.session ?: return
        if (actionBusy) return
        actionBusy = true
        viewModelScope.launch {
            try {
                v6Api.visitCheckOut(session, visit.id, stage, outcome, notes, nextFollowUpAt)
                dashboard = api.loadDashboard(session)
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

    fun consumeNotice() {
        notice = null
    }

    fun logout() {
        val session = (state as? SalesAppState.LoggedIn)?.session
        state = SalesAppState.LoggedOut
        dashboard = SalesDashboard()
        fieldWorkspace = V6FieldWorkspace()
        currentPage = SalesPage.DASHBOARD
        if (session != null) viewModelScope.launch { api.signOut(session.accessToken) }
    }

    private fun rupiahNotice(value: Double): String = java.text.NumberFormat
        .getCurrencyInstance(java.util.Locale("id", "ID"))
        .format(value)
        .replace(",00", "")
}
