package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BookingRequestScreen(
    vm: MainViewModel,
    session: SessionState,
    onNotice: (String) -> Unit
) {
    if (session.profile.role !in listOf("Owner", "Manager", "Sales")) {
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            EmptyCard("Pengajuan Website hanya tersedia untuk Owner / Manager / Sales.")
        }
        return
    }

    val canReview = session.profile.role in listOf("Owner", "Manager")
    var filter by remember { mutableStateOf("NEW_REQUEST") }
    var rejectRow by remember { mutableStateOf<BookingRequestItem?>(null) }

    val all = vm.bookingRequests
    val rows = all.filter { filter == "ALL" || it.status == filter }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        SectionTitle(
            "Pengajuan Website",
            if (canReview) {
                "Owner / Manager • Terima, Tolak & Token Customer"
            } else {
                "Sales • Lihat pengajuan & Token Customer"
            }
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(
                "NEW_REQUEST" to "Baru",
                "VERIFICATION" to "Diterima",
                "QUOTATION" to "Quotation",
                "REJECTED" to "Ditolak",
                "ALL" to "Semua"
            ).forEach { (value, label) ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (rows.isEmpty()) {
                item { EmptyCard("Tidak ada pengajuan pada status ini.") }
            }

            items(rows, key = { it.id }) { r ->
                val program = requestProgramName(r)
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    r.institutionName.ifBlank { "Pengajuan Website" },
                                    fontWeight = FontWeight.Black,
                                    color = GmuDark
                                )
                                Text(
                                    r.bookingCode,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            StatusChip(requestStatusLabel(r.status))
                        }

                        Spacer(Modifier.height(8.dp))
                        RequestLine("Program", program)
                        RequestLine("Tanggal", r.tripDate)
                        RequestLine("Peserta", r.pax.toString() + " pax")
                        RequestLine("PIC", r.picName)
                        if (r.whatsapp.isNotBlank()) {
                            RequestLine("WhatsApp", r.whatsapp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    vm.loadCustomerPortalToken(r.id) { ok, msg ->
                                        if (!ok) onNotice(msg)
                                    }
                                },
                                enabled = !vm.actionBusy,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Token Customer")
                            }

                            if (canReview && r.status == "NEW_REQUEST") {
                                Spacer(Modifier.width(6.dp))
                                TextButton(
                                    onClick = { rejectRow = r },
                                    enabled = !vm.actionBusy
                                ) {
                                    Text("Tolak", color = GmuDanger)
                                }
                                Spacer(Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        vm.reviewBookingRequest(
                                            requestId = r.id,
                                            accepted = true
                                        ) { _, msg -> onNotice(msg) }
                                    },
                                    enabled = !vm.actionBusy,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Terima")
                                }
                            }

                            if (canReview && r.status == "VERIFICATION") {
                                Spacer(Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        vm.startBookingRequestQuotation(r.id) { _, msg -> onNotice(msg) }
                                    },
                                    enabled = !vm.actionBusy,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Mulai Quotation")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    vm.customerPortalCredential?.let { credential ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { vm.clearCustomerPortalToken() },
            title = { Text("Token Customer") },
            text = {
                Column {
                    Text(
                        credential.institutionName.ifBlank { credential.picName.ifBlank { "Customer" } },
                        fontWeight = FontWeight.Black,
                        color = GmuDark
                    )
                    Spacer(Modifier.height(8.dp))
                    RequestLine("Booking Code", credential.bookingCode)
                    Spacer(Modifier.height(8.dp))
                    Text("Token Akses", fontSize = 11.sp, color = Color.Gray)
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF3F6F4)
                    ) {
                        Text(
                            credential.accessToken,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GmuDark
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Token ini sudah tersimpan di server. Gunakan hanya untuk membantu customer mengakses portal booking.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setText(
                            AnnotatedString(
                                "Booking Code: " + credential.bookingCode +
                                    "\nToken Akses: " + credential.accessToken
                            )
                        )
                        onNotice("Booking Code + Token berhasil disalin.")
                    }
                ) {
                    Text("Salin Semua")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(credential.accessToken))
                            onNotice("Token berhasil disalin.")
                        }
                    ) {
                        Text("Salin Token")
                    }
                    TextButton(onClick = { vm.clearCustomerPortalToken() }) {
                        Text("Tutup")
                    }
                }
            }
        )
    }

    rejectRow?.let { row ->
        var reason by remember(row.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!vm.actionBusy) rejectRow = null },
            title = { Text("Tolak Pengajuan") },
            text = {
                Column {
                    Text(
                        row.institutionName.ifBlank { row.bookingCode },
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Alasan penolakan") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.reviewBookingRequest(
                            requestId = row.id,
                            accepted = false,
                            reason = reason
                        ) { ok, msg ->
                            onNotice(msg)
                            if (ok) rejectRow = null
                        }
                    },
                    enabled = !vm.actionBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = GmuDanger)
                ) {
                    Text("Tolak Pengajuan")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { rejectRow = null },
                    enabled = !vm.actionBusy
                ) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun RequestLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun requestProgramName(r: BookingRequestItem): String =
    r.programName.ifBlank { r.customProgram }.ifBlank { "Program belum ditentukan" }

private fun requestStatusLabel(status: String): String = when (status) {
    "NEW_REQUEST" -> "Baru"
    "VERIFICATION" -> "Diterima / Verifikasi"
    "QUOTATION" -> "Quotation"
    "WAITING_DP" -> "Menunggu DP"
    "CONFIRMED" -> "Confirmed"
    "REJECTED" -> "Ditolak"
    else -> status
}
