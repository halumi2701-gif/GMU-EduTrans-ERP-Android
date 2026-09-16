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

    var state by mutableStateOf<SalesAppState>(SalesAppState.Splash)
        private set
    var currentPage by mutableStateOf(SalesPage.DASHBOARD)
        private set
    var dashboard by mutableStateOf(SalesDashboard())
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
            } catch (e: Exception) {
                notice = e.message ?: "Data Sales gagal dimuat."
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

    fun consumeNotice() {
        notice = null
    }

    fun logout() {
        val session = (state as? SalesAppState.LoggedIn)?.session
        state = SalesAppState.LoggedOut
        dashboard = SalesDashboard()
        currentPage = SalesPage.DASHBOARD
        if (session != null) viewModelScope.launch { api.signOut(session.accessToken) }
    }

    private fun rupiahNotice(value: Double): String = java.text.NumberFormat
        .getCurrencyInstance(java.util.Locale("id", "ID"))
        .format(value)
        .replace(",00", "")
}
