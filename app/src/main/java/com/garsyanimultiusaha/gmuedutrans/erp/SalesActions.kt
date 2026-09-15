package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

fun MainViewModel.createCustomer(name: String, type: String, pic: String, wa: String, email: String, done: (Boolean, String) -> Unit) {
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

fun MainViewModel.createBooking(
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

fun MainViewModel.updateBookingStatus(
    bookingId: String,
    status: String,
    done: (Boolean, String) -> Unit
) {
    val session = activeSession() ?: return
    if (!ErpRolePolicy.canEditBookings(session.profile.role)) {
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

fun MainViewModel.loadCustomerPortalToken(
    requestId: String,
    done: (Boolean, String) -> Unit
) {
    val session = activeSession() ?: return
    if (!ErpRolePolicy.canHandleBookingIntake(session.profile.role)) {
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

fun MainViewModel.clearCustomerPortalToken() {
    customerPortalCredential = null
    customerPortalTokenError = null
}

fun MainViewModel.startBookingRequestQuotation(
    requestId: String,
    done: (Boolean, String) -> Unit
) {
    val session = activeSession() ?: return
    if (!ErpRolePolicy.canApproveCommercial(session.profile.role)) {
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

fun MainViewModel.reviewBookingRequest(
    requestId: String,
    accepted: Boolean,
    reason: String = "",
    done: (Boolean, String) -> Unit
) {
    val session = activeSession() ?: return
    if (!ErpRolePolicy.canApproveCommercial(session.profile.role)) {
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
