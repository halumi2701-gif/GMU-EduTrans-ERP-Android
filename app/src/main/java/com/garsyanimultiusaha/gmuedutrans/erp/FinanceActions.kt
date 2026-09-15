package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

fun MainViewModel.savePlanningTarget(
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

fun MainViewModel.savePlanningBudget(
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

fun MainViewModel.deletePlanningBudget(budgetId: String, done: (Boolean, String) -> Unit) {
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

fun MainViewModel.setPlanningPipelineWeight(
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

fun MainViewModel.runPlanningScenario(
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

fun MainViewModel.clearPlanningScenario() {
    planningScenario = null
}
