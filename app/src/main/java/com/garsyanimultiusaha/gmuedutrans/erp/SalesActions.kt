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
    if (actionBusy) {
        done(false, "Booking sedang dikirim. Tunggu proses selesai agar tidak membuat booking ganda.")
        return
    }
    actionBusy = true
    viewModelScope.launch {
        try {
            val bookingNo = api.createBooking(
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
            val customerName = customers.firstOrNull { it.id == customerId }?.name ?: "CUSTOMER"
            val driveStatus = runCatching {
                DriveArchiveApi().ensureOrderFolder(
                    accessToken = session.accessToken,
                    bookingNo = bookingNo,
                    customerName = customerName,
                    activityDate = tripDate
                )
            }
            done(
                true,
                if (driveStatus.isSuccess) {
                    "$bookingNo berhasil dibuat dan folder Google Drive + 9 subfolder sudah disiapkan."
                } else {
                    "$bookingNo berhasil dibuat. Sinkronisasi Google Drive tertunda: " +
                        (driveStatus.exceptionOrNull()?.message ?: "kesalahan tidak diketahui.")
                }
            )
            loadAll(session)
        } catch (e: Exception) {
            done(false, e.message ?: "Gagal membuat booking.")
        } finally {
            actionBusy = false
        }
    }
}

fun MainViewModel.updateBooking(
    bookingId: String,
    program: String,
    tripDate: String,
    pax: Int,
    pricePerPax: Double?,
    participantGroup: String,
    meetingPoint: String,
    done: (Boolean, String) -> Unit
) {
    val session = activeSession() ?: return
    val role = session.profile.role
    val canEditDetails = role in listOf("Owner", "Manager", "Admin", "Finance", "Operation") ||
        ErpRoles.isDirector(role) || ErpRoles.isManagerEduTrans(role)
    if (!canEditDetails) {
        done(false, "Role Anda tidak memiliki akses edit detail booking.")
        return
    }
    if (actionBusy) {
        done(false, "Booking lain sedang diproses. Tunggu sebentar.")
        return
    }
    actionBusy = true
    viewModelScope.launch {
        try {
            val values = linkedMapOf<String, Any?>(
                "program_name" to program.trim(),
                "trip_date" to tripDate.trim(),
                "pax" to pax,
                "participant_group" to participantGroup.trim().ifBlank { null },
                "meeting_point" to meetingPoint.trim().ifBlank { null }
            )
            if (pricePerPax != null && FinancialAccess.canView(session.profile.role)) {
                values["price_per_pax"] = pricePerPax
            }
            val saved = api.updateRow(session.accessToken, "bookings", bookingId, values)
                ?: throw IllegalStateException("Booking tidak ditemukan atau akses edit ditolak.")
            done(true, "Booking berhasil diperbarui.")
            loadAll(session)
        } catch (e: Exception) {
            done(false, e.message ?: "Booking gagal diperbarui.")
        } finally {
            actionBusy = false
        }
    }
}

fun MainViewModel.openBookingDocument(
    bucket: String,
    objectPath: String,
    done: (String?, String?) -> Unit
) {
    val session = activeSession() ?: return
    if (bucket.isBlank() || objectPath.isBlank()) {
        done(null, "Lokasi dokumen belum tersedia.")
        return
    }
    viewModelScope.launch {
        try {
            val url = api.createSignedStorageUrl(session.accessToken, bucket, objectPath)
            done(url, null)
        } catch (e: Exception) {
            done(null, e.message ?: "Dokumen gagal dibuka.")
        }
    }
}

fun MainViewModel.deleteBooking(
    booking: Booking,
    done: (Boolean, String) -> Unit
) {
    val session = activeSession() ?: return
    if (session.profile.role !in listOf("Owner", "Manager", "Admin")) {
        done(false, "Hanya Owner, Manager, atau Admin yang dapat menghapus booking.")
        return
    }
    if (actionBusy) {
        done(false, "Ada proses lain yang sedang berjalan. Tunggu sebentar.")
        return
    }
    actionBusy = true
    viewModelScope.launch {
        try {
            val payments = api.getRows(session.accessToken, "payments")
            if (payments.any { it.text("booking_id") == booking.id }) {
                done(false, "Booking tidak dapat dihapus karena sudah memiliki transaksi pembayaran. Pertahankan booking untuk audit dan rekonsiliasi.")
                return@launch
            }
            val invoices = api.getRows(session.accessToken, "invoices")
            if (invoices.any { it.text("booking_id") == booking.id }) {
                done(false, "Booking tidak dapat dihapus karena sudah memiliki invoice. Batalkan atau arsipkan invoice melalui alur Finance terlebih dahulu.")
                return@launch
            }
            val deleted = api.deleteRow(session.accessToken, "bookings", booking.id)
            if (!deleted) throw IllegalStateException("Booking tidak ditemukan atau penghapusan ditolak oleh database.")
            runCatching {
                api.audit(
                    session.accessToken,
                    session.userId,
                    "DELETE_BOOKING",
                    "bookings",
                    booking.bookingNo,
                    "Booking ${booking.bookingNo} dihapus oleh pengguna ERP."
                )
            }
            done(true, "Booking ${booking.bookingNo} berhasil dihapus.")
            loadAll(session)
        } catch (e: Exception) {
            done(false, e.message ?: "Booking gagal dihapus.")
        } finally {
            actionBusy = false
        }
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
