package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

fun MainViewModel.openQuotationRequest(
    requestId: String,
    done: (Boolean, String) -> Unit = { _, _ -> }
) {
    val session = activeSession() ?: return
    if (!ErpRolePolicy.canManageQuotationDrafts(session.profile.role)) {
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

fun MainViewModel.createQuotationDraft(
    requestId: String,
    done: (Boolean, String) -> Unit
) {
    val session = activeSession() ?: return
    if (!ErpRolePolicy.canManageQuotationDrafts(session.profile.role)) {
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

fun MainViewModel.saveQuotationDraft(
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

fun MainViewModel.publishQuotation(
    pricingOverrideReason: String,
    done: (Boolean, String) -> Unit
) {
    val session = activeSession() ?: return
    val current = quotationDetail ?: run {
        done(false, "Quotation belum dipilih.")
        return
    }
    if (!ErpRolePolicy.canApproveCommercial(session.profile.role)) {
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

fun MainViewModel.acceptQuotation(done: (Boolean, String) -> Unit) {
    val session = activeSession() ?: return
    val current = quotationDetail ?: run {
        done(false, "Quotation belum dipilih.")
        return
    }
    if (!ErpRolePolicy.canApproveCommercial(session.profile.role)) {
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

fun MainViewModel.rejectQuotation(reason: String, done: (Boolean, String) -> Unit) {
    val session = activeSession() ?: return
    val current = quotationDetail ?: run {
        done(false, "Quotation belum dipilih.")
        return
    }
    if (!ErpRolePolicy.canApproveCommercial(session.profile.role)) {
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

fun MainViewModel.savePricingPolicy(
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
            pricingMaster = runCatching { api.getPricingMasterDashboard(session.accessToken) }.getOrNull()
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

fun MainViewModel.loadQuotationDraftSuggestion(done: (Boolean, String) -> Unit) {
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

fun MainViewModel.applyQuotationDraftSuggestion(done: (Boolean, String) -> Unit) {
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

fun MainViewModel.savePackageDraft(
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
    if (!ErpRolePolicy.canManagePackages(session.profile.role)) {
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

fun MainViewModel.clonePackage(id: String, done: (Boolean, String) -> Unit) {
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

fun MainViewModel.archivePackage(id: String, done: (Boolean, String) -> Unit) {
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

fun MainViewModel.applyRecommendedPackagePrice(id: String, done: (Boolean, String) -> Unit) {
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

fun MainViewModel.activatePackage(id: String, done: (Boolean, String) -> Unit) {
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

fun MainViewModel.saveCostTemplate(
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

fun MainViewModel.setPaymentChannel(
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

fun MainViewModel.refreshCommerce() {
    val session = activeSession() ?: return
    if (actionBusy) return
    actionBusy = true
    viewModelScope.launch {
        try {
            if (ErpRolePolicy.canManagePackages(session.profile.role)) {
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

fun MainViewModel.clearQuotationDetail() {
    quotationDetail = null
    quotationSuggestion = null
    quotationError = null
}
