package com.garsyanimultiusaha.gmuedutrans.sales

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

private const val GMU_WEB = "https://edutrans.garsyanimultiusaha.site"
private const val GMU_WA = "6287783906545"

private data class PackageChoice(
    val program: SalesProgram,
    val pack: SalesPackage
)

@Composable
fun MaterialsKitTab(vm: SalesViewModel) {
    val context = LocalContext.current
    val catalogText = remember(vm.dashboard.catalog) { buildCatalogSummary(vm.dashboard.catalog) }
    val companyProfile = remember {
        """GMU EduTrans — PT Garsyani Multi Usaha

Partner program edukasi, edutrip, perjalanan rombongan, dan layanan transportasi untuk sekolah, lembaga pendidikan, komunitas, serta institusi.

Layanan utama mencakup program edukasi berbasis pengalaman, kegiatan outing/field trip, edukasi perkeretaapian, program tematik, serta kebutuhan perjalanan rombongan.

Informasi program dan harga resmi mengikuti master aktif GMU EduTrans.
Website: $GMU_WEB""".trimIndent()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Company Profile Ringkas", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(companyProfile, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { shareKitText(context, companyProfile) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Description, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Bagikan Company Profile")
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Brosur Digital — Program Aktif", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("Ringkasan otomatis dari master program yang sedang aktif.", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(catalogText, fontSize = 11.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { shareKitText(context, catalogText) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = vm.dashboard.catalog.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Send, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Bagikan Brosur Teks")
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Akses Materi Resmi", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("Gunakan kanal resmi untuk profil, program, visual, dan komunikasi calon customer.", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 11.sp)
                    OutlinedButton(
                        onClick = { openKitUrl(context, GMU_WEB) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Language, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Buka Website GMU EduTrans")
                    }
                    Button(
                        onClick = {
                            openKitUrl(
                                context,
                                "https://wa.me/$GMU_WA?text=" + Uri.encode("Halo GMU EduTrans, saya ingin menanyakan program edukasi/perjalanan rombongan.")
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, null)
                        Spacer(Modifier.width(7.dp))
                        Text("WhatsApp Resmi GMU")
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFFF7DB))
            ) {
                Text(
                    "Sales tidak boleh membuat harga, diskon, cashback, atau janji fasilitas di luar master aktif. Jika customer meminta penyesuaian, ajukan approval ke Manager/Director.",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
fun QuotationKitTab(vm: SalesViewModel) {
    val context = LocalContext.current
    var selectedLead by remember { mutableStateOf<SalesLead?>(null) }
    val eligibleStages = setOf("QUALIFIED", "QUOTATION", "NEGOTIATION", "WAITING_DP")
    val eligibleLeads = vm.dashboard.leads.filter { it.stage in eligibleStages && it.pax > 0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFEAF6E8))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Quotation → WhatsApp → DP", fontWeight = FontWeight.Black)
                    Text(
                        "Buat draft dari harga publik resmi, kirim ke WhatsApp PIC, lalu tandai Terkirim. Sistem memindahkan lead ke WAITING DP dan membuat follow-up +3 hari. Setelah Finance memverifikasi pembayaran, lead otomatis menjadi WON/handover.",
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item { Text("Lead Siap Quotation", fontWeight = FontWeight.Black, fontSize = 17.sp) }
        if (eligibleLeads.isEmpty()) {
            item { QuotationEmpty("Belum ada lead QUALIFIED/HOT yang siap dibuatkan quotation.") }
        } else {
            items(eligibleLeads, key = { "quote-lead-${it.id}" }) { lead ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(lead.institutionName, fontWeight = FontWeight.Black)
                                Text("${lead.pax} pax • ${lead.programName}", fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.Gray)
                            }
                            Surface(shape = RoundedCornerShape(100.dp), color = androidx.compose.ui.graphics.Color(0xFFFFF2C6)) {
                                Text(lead.stage.replace('_', ' '), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(11.dp))
                        Button(
                            onClick = { selectedLead = lead },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !vm.actionBusy
                        ) { Text("Buat Draft Quotation") }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("Quotation Saya", fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
        if (vm.dashboard.quotations.isEmpty()) {
            item { QuotationEmpty("Belum ada quotation yang dibuat dari portfolio Sales ini.") }
        } else {
            items(vm.dashboard.quotations, key = { "quote-${it.id}" }) { q ->
                val lead = vm.dashboard.leads.firstOrNull { it.id == q.bookingRequestId }
                val booking = lead?.convertedBookingId?.takeIf { it.isNotBlank() }?.let { bookingId ->
                    vm.dashboard.bookings.firstOrNull { it.id == bookingId }
                }
                val shareText = quotationShareText(q, lead)

                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(q.quotationNo, fontWeight = FontWeight.Black)
                            Text(q.status, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF0B5F10))
                        }
                        Text(q.institutionName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("${q.programName} • ${q.pax} pax", fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.Gray)
                        Spacer(Modifier.height(7.dp))
                        Text(quotationRupiah(q.total), fontWeight = FontWeight.Black, fontSize = 18.sp, color = androidx.compose.ui.graphics.Color(0xFF168400))
                        if (q.validUntil.isNotBlank()) Text("Berlaku sampai ${q.validUntil}", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color.Gray)

                        if (q.status == "SENT" && booking == null) {
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = RoundedCornerShape(12.dp), color = androidx.compose.ui.graphics.Color(0xFFFFF7DB)) {
                                Text("WAITING DP • follow-up otomatis +3 hari", Modifier.padding(10.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        booking?.let { b ->
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (b.paymentState == "DP_TERVERIFIKASI") androidx.compose.ui.graphics.Color(0xFFEAF6E8) else androidx.compose.ui.graphics.Color(0xFFFFF7DB)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(paymentStateLabel(b.paymentState), fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    if (b.paymentState == "DP_TERVERIFIKASI") {
                                        Text("Lead otomatis WON dan siap handover ke Admin/Manager Ops.", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color.Gray)
                                    }
                                }
                            }
                        }

                        if (q.status in setOf("DRAFT", "SENT")) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { openQuotationWhatsApp(context, lead?.whatsapp.orEmpty(), shareText) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !vm.actionBusy
                            ) {
                                Icon(Icons.Default.Send, null)
                                Spacer(Modifier.width(7.dp))
                                Text(if (q.status == "SENT") "Kirim Ulang via WhatsApp" else "Kirim via WhatsApp")
                            }
                            Spacer(Modifier.height(7.dp))
                            Button(
                                onClick = { vm.markQuotationSent(q) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = q.status == "DRAFT" && !vm.actionBusy
                            ) {
                                Text(if (q.status == "DRAFT") "Tandai Sudah Terkirim" else "Sudah Terkirim")
                            }
                            if (q.status == "DRAFT") {
                                Text(
                                    "Buka WhatsApp dulu. Setelah pesan benar-benar dikirim ke PIC, tekan Tandai Sudah Terkirim.",
                                    fontSize = 9.sp,
                                    color = androidx.compose.ui.graphics.Color.Gray,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedLead?.let { lead ->
        QuotationDraftDialog(
            lead = lead,
            catalog = vm.dashboard.catalog,
            busy = vm.actionBusy,
            onDismiss = { selectedLead = null },
            onCreate = { packageId, notes ->
                vm.createQuotationDraft(lead, packageId, notes)
                selectedLead = null
            }
        )
    }
}

@Composable
private fun QuotationDraftDialog(
    lead: SalesLead,
    catalog: List<SalesProgram>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    val choices = remember(lead.id, lead.programId, lead.pax, catalog) {
        val programs = if (lead.programId.isNotBlank()) {
            catalog.filter { it.id == lead.programId }.ifEmpty { catalog }
        } else {
            val byName = catalog.filter { it.name.equals(lead.programName, ignoreCase = true) }
            byName.ifEmpty { catalog }
        }
        programs.flatMap { program ->
            program.packages
                .filter { pack -> pack.pricePerPax > 0 && lead.pax >= pack.minPax }
                .map { PackageChoice(program, it) }
        }
    }
    var selected by remember(lead.id, choices) {
        mutableStateOf(choices.firstOrNull { it.pack.id == lead.packageId } ?: choices.firstOrNull())
    }
    var expanded by remember { mutableStateOf(false) }
    var notes by remember(lead.id) { mutableStateOf("") }
    val total = selected?.let { it.pack.pricePerPax * lead.pax } ?: 0.0

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Draft Quotation", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(lead.institutionName, fontWeight = FontWeight.Bold)
                Text("${lead.pax} pax • ${lead.tripDate}", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.Gray)
                if (choices.isEmpty()) {
                    QuotationEmpty("Tidak ada paket aktif yang cocok dengan jumlah pax lead ini. Cek master program atau minta Manager menyesuaikan paket.")
                } else {
                    Text("Pilih paket resmi", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selected?.let { "${it.program.name} — ${it.pack.name}" } ?: "Pilih paket", modifier = Modifier.weight(1f), maxLines = 2)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            choices.forEach { choice ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("${choice.program.name} — ${choice.pack.name}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Text("${quotationRupiah(choice.pack.pricePerPax)} / pax • min ${choice.pack.minPax}", fontSize = 9.sp)
                                        }
                                    },
                                    onClick = { selected = choice; expanded = false }
                                )
                            }
                        }
                    }
                    Surface(shape = RoundedCornerShape(14.dp), color = androidx.compose.ui.graphics.Color(0xFFEAF6E8), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Harga publik", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color.Gray)
                            Text("${quotationRupiah(selected?.pack?.pricePerPax ?: 0.0)} × ${lead.pax} pax", fontWeight = FontWeight.Bold)
                            Text("Total ${quotationRupiah(total)}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = androidx.compose.ui.graphics.Color(0xFF168400))
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Catatan untuk customer (opsional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { selected?.let { onCreate(it.pack.id, notes.ifBlank { null }) } }, enabled = selected != null && !busy) {
                Text(if (busy) "Memproses…" else "Buat Draft")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun QuotationEmpty(message: String) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
        Text(message, modifier = Modifier.padding(14.dp), fontSize = 11.sp, lineHeight = 17.sp, color = androidx.compose.ui.graphics.Color.Gray)
    }
}

private fun buildCatalogSummary(catalog: List<SalesProgram>): String {
    if (catalog.isEmpty()) return "Katalog program sedang belum tersedia."
    return buildString {
        append("GMU EduTrans — Program Aktif\n\n")
        catalog.forEach { program ->
            append("• ").append(program.name)
            program.marketingStartPrice?.takeIf { it > 0 }?.let { append(" — mulai ").append(quotationRupiah(it)).append("/peserta") }
            append("\n")
        }
        append("\nHarga dan ketersediaan mengikuti master aktif. Hubungi GMU EduTrans untuk quotation resmi.\n")
        append(GMU_WEB)
    }
}

private fun quotationShareText(q: SalesQuotation, lead: SalesLead?): String = buildString {
    append("Halo")
    lead?.picName?.takeIf { it.isNotBlank() }?.let { append(" Bapak/Ibu ").append(it) }
    append(",\n\n")
    append("Berikut penawaran resmi GMU EduTrans untuk ").append(q.institutionName).append(".\n\n")
    append("No. Quotation: ").append(q.quotationNo).append("\n")
    append("Program: ").append(q.programName).append("\n")
    append("Peserta: ").append(q.pax).append(" pax\n")
    append("Total: ").append(quotationRupiah(q.total)).append("\n")
    if (q.validUntil.isNotBlank()) append("Berlaku sampai: ").append(q.validUntil).append("\n")
    append("\nSilakan konfirmasi bila penawaran sudah sesuai. Setelah DP/pembayaran diverifikasi Finance GMU EduTrans, booking akan diteruskan ke tim operasional.\n\n")
    append("GMU EduTrans — PT Garsyani Multi Usaha\n")
    append(GMU_WEB)
}

private fun paymentStateLabel(state: String): String = when (state) {
    "DP_TERVERIFIKASI" -> "DP TERVERIFIKASI"
    "MENUNGGU_VERIFIKASI" -> "PEMBAYARAN MENUNGGU VERIFIKASI FINANCE"
    else -> "BELUM ADA PEMBAYARAN TERVERIFIKASI"
}

private fun normalizeWhatsApp(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return when {
        digits.startsWith("62") -> digits
        digits.startsWith("0") -> "62${digits.drop(1)}"
        digits.startsWith("8") -> "62$digits"
        else -> digits
    }
}

private fun openQuotationWhatsApp(context: Context, whatsapp: String, text: String) {
    val number = normalizeWhatsApp(whatsapp)
    if (number.isBlank()) {
        shareKitText(context, text)
        return
    }
    val url = "https://wa.me/$number?text=${Uri.encode(text)}"
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { shareKitText(context, text) }
}

private fun shareKitText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan materi GMU EduTrans"))
}

private fun openKitUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun quotationRupiah(value: Double): String = NumberFormat
    .getCurrencyInstance(Locale("id", "ID"))
    .format(value)
    .replace(",00", "")
