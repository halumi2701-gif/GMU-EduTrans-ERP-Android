package com.garsyanimultiusaha.gmuedutrans.sales

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

private val E2EGreen = Color(0xFF128000)
private val E2EGreenDark = Color(0xFF07580F)
private val E2EGold = Color(0xFFD5A300)
private val E2ESoftGreen = Color(0xFFEAF6E8)
private val E2ESoftGold = Color(0xFFFFF4D4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesAppV62(vm: SalesViewModel) {
    var showClosing by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        SalesAppV61(vm)
        if (vm.state is SalesAppState.LoggedIn) {
            ExtendedFloatingActionButton(
                onClick = { showClosing = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 88.dp),
                containerColor = E2EGreenDark,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Verified, null) },
                text = { Text("Closing E2E", fontWeight = FontWeight.Black) }
            )
        }
    }

    if (showClosing) {
        ModalBottomSheet(
            onDismissRequest = { showClosing = false },
            containerColor = Color(0xFFF7F9F6)
        ) {
            E2EQuotationClosingSheet(vm)
        }
    }
}

@Composable
private fun E2EQuotationClosingSheet(vm: SalesViewModel) {
    val context = LocalContext.current
    val quotations = vm.dashboard.quotations
    val leads = remember(vm.dashboard.leads) { vm.dashboard.leads.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 44.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text("Quotation → Customer → DP", fontSize = 21.sp, fontWeight = FontWeight.Black, color = E2EGreenDark)
                Text(
                    "DRAFT → kirim ke PIC → tandai SENT → customer Accept/Revisi → invoice DP → pembayaran terverifikasi → WON + handover Ops.",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = Color.Gray
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = E2ESoftGreen)) {
                Text(
                    "SENT bukan WAITING DP. WAITING DP hanya terjadi setelah customer menerima quotation. Sales tidak dapat menandai WON manual.",
                    Modifier.padding(14.dp),
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = E2EGreenDark
                )
            }
        }
        if (quotations.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Text("Belum ada quotation. Buat quotation dari Sales Kit terlebih dahulu.", Modifier.padding(16.dp), fontSize = 11.sp)
                }
            }
        } else {
            items(quotations, key = { it.id }) { q ->
                val lead = leads[q.bookingRequestId]
                val wa = q.whatsapp.ifBlank { lead?.whatsapp.orEmpty() }
                val pic = q.picName.ifBlank { lead?.picName.orEmpty() }
                val confirmationUrl = e2eConfirmationUrl(q)
                val linkReady = q.bookingCode.isNotBlank() && q.accessToken.isNotBlank()
                val message = e2eQuotationMessage(q, pic, confirmationUrl)
                E2EQuotationCard(
                    q = q,
                    linkReady = linkReady,
                    onWhatsApp = { e2eOpenWhatsApp(context, wa, message) },
                    onOpenLink = { if (linkReady) e2eOpenUrl(context, confirmationUrl) },
                    onMarkSent = { vm.markQuotationSent(q) }
                )
            }
        }
        item {
            OutlinedButton(onClick = vm::refresh, enabled = !vm.dataBusy && !vm.actionBusy, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.dataBusy) "Sinkronisasi…" else "Sinkronkan Status Customer / DP")
            }
        }
    }
}

@Composable
private fun E2EQuotationCard(
    q: SalesQuotation,
    linkReady: Boolean,
    onWhatsApp: () -> Unit,
    onOpenLink: () -> Unit,
    onMarkSent: () -> Unit
) {
    val state = e2eStateLabel(q)
    val stateColor = when {
        q.customerDecision == "REVISION" -> E2ESoftGold
        q.status == "ACCEPTED" || q.invoiceNo.isNotBlank() -> E2ESoftGreen
        q.status == "SENT" -> E2ESoftGold
        else -> Color.White
    }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = stateColor)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(q.quotationNo, fontWeight = FontWeight.Black, color = E2EGreenDark)
                    Text(q.institutionName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${q.programName} • ${q.pax} pax", fontSize = 9.sp, color = Color.Gray)
                }
                Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = .85f)) {
                    Text(state, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(e2eRupiah(q.total), fontSize = 18.sp, fontWeight = FontWeight.Black, color = E2EGreen)
            if (q.validUntil.isNotBlank()) Text("Berlaku sampai ${q.validUntil}", fontSize = 9.sp, color = Color.Gray)

            if (q.customerDecision == "REVISION") {
                Spacer(Modifier.height(8.dp))
                Text("Customer meminta revisi. Lead kembali ke NEGOTIATION dan harus ditindaklanjuti.", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (q.status == "SENT" && q.customerDecision.isBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Menunggu keputusan customer. Follow-up otomatis dijadwalkan backend.", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (q.invoiceNo.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(7.dp))
                E2ELine("Invoice DP", q.invoiceNo)
                E2ELine("Status", q.invoiceStatus.ifBlank { "-" })
                q.dpPercent?.let { E2ELine("DP", "${formatPct(it)}%") }
                if (q.invoiceTotal > 0) E2ELine("Tagihan", e2eRupiah(q.invoiceTotal))
            }

            Spacer(Modifier.height(11.dp))
            OutlinedButton(
                onClick = onWhatsApp,
                enabled = linkReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, null)
                Spacer(Modifier.width(7.dp))
                Text(if (q.status == "DRAFT") "1. Buka WhatsApp ke PIC" else "Kirim / Kirim Ulang WhatsApp")
            }
            if (q.status == "DRAFT") {
                Button(
                    onClick = onMarkSent,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = linkReady
                ) { Text("2. Pesan Sudah Terkirim → Tandai SENT") }
                Text(
                    "Tandai SENT hanya setelah pesan benar-benar dikirim. Status tetap QUOTATION sampai customer menerima.",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
            if (q.status in setOf("SENT", "ACCEPTED") || q.customerDecision.isNotBlank()) {
                OutlinedButton(onClick = onOpenLink, enabled = linkReady, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Link, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Buka Link Customer")
                }
            }
            if (!linkReady) {
                Text("Credential customer belum termuat. Tekan Sinkronkan Status.", fontSize = 9.sp, color = Color.Red)
            }
        }
    }
}

@Composable
private fun E2ELine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 9.sp, color = Color.Gray)
        Text(value, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

private fun e2eStateLabel(q: SalesQuotation): String = when {
    q.customerDecision == "REVISION" -> "REVISI DIMINTA"
    q.status == "ACCEPTED" && q.invoiceStatus == "PAID" -> "DP PAID"
    q.status == "ACCEPTED" && q.invoiceNo.isNotBlank() -> "WAITING DP"
    q.status == "ACCEPTED" -> "ACCEPTED"
    q.status == "SENT" -> "MENUNGGU CUSTOMER"
    else -> q.status
}

private fun e2eConfirmationUrl(q: SalesQuotation): String {
    val base = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/public-quotation-decision"
    return "$base?booking=${Uri.encode(q.bookingCode)}&token=${Uri.encode(q.accessToken)}"
}

private fun e2eQuotationMessage(q: SalesQuotation, pic: String, confirmationUrl: String): String = buildString {
    append("Halo")
    if (pic.isNotBlank()) append(" Bapak/Ibu ").append(pic)
    append(",\n\n")
    append("Berikut quotation resmi GMU EduTrans.\n")
    append("No: ").append(q.quotationNo).append("\n")
    append("Program: ").append(q.programName).append("\n")
    append("Peserta: ").append(q.pax).append(" pax\n")
    append("Total: ").append(e2eRupiah(q.total)).append("\n")
    if (q.validUntil.isNotBlank()) append("Berlaku sampai: ").append(q.validUntil).append("\n")
    append("\nSilakan buka link berikut untuk menerima quotation atau meminta revisi:\n")
    append(confirmationUrl)
    append("\n\nSetelah quotation diterima, sistem akan menyiapkan invoice DP dan Customer Portal pembayaran.\n\nGMU EduTrans — PT Garsyani Multi Usaha")
}

private fun e2eOpenWhatsApp(context: Context, raw: String, text: String) {
    val digits = raw.filter(Char::isDigit)
    val number = when {
        digits.startsWith("62") -> digits
        digits.startsWith("0") -> "62${digits.drop(1)}"
        digits.startsWith("8") -> "62$digits"
        else -> digits
    }
    val target = if (number.isBlank()) null else "https://wa.me/$number?text=${Uri.encode(text)}"
    if (target != null) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
            .onFailure { e2eShare(context, text) }
    } else e2eShare(context, text)
}

private fun e2eOpenUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun e2eShare(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan quotation GMU EduTrans"))
}

private fun e2eRupiah(value: Double): String = NumberFormat
    .getCurrencyInstance(Locale("id", "ID"))
    .format(value)
    .replace(",00", "")

private fun formatPct(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
