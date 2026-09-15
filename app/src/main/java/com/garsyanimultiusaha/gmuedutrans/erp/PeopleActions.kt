package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

fun MainViewModel.createStaff(
    fullName: String,
    email: String,
    phone: String,
    role: String,
    password: String,
    done: (Boolean, String) -> Unit
) {
    val session = activeSession() ?: return
    if (!ErpRolePolicy.isOwner(session.profile.role)) {
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

fun MainViewModel.setStaffActive(id: String, active: Boolean, done: (Boolean, String) -> Unit) {
    val session = activeSession() ?: return
    if (!ErpRolePolicy.isOwner(session.profile.role)) {
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

fun MainViewModel.setStaffRole(id: String, role: String, done: (Boolean, String) -> Unit) {
    val session = activeSession() ?: return
    if (!ErpRolePolicy.isOwner(session.profile.role)) {
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

fun MainViewModel.resetStaffPassword(id: String, password: String, done: (Boolean, String) -> Unit) {
    val session = activeSession() ?: return
    if (!ErpRolePolicy.isOwner(session.profile.role)) {
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
