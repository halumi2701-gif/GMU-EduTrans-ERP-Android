package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

fun MainViewModel.insert(table: String, values: Map<String, Any?>, successMessage: String, done: (Boolean, String) -> Unit) {
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

fun MainViewModel.update(table: String, id: String, values: Map<String, Any?>, successMessage: String, done: (Boolean, String) -> Unit) {
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

fun MainViewModel.approve(approvalId: String, approved: Boolean, notes: String, done: (Boolean, String) -> Unit) {
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
