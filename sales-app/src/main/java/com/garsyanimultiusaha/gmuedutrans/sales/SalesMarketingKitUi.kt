package com.garsyanimultiusaha.gmuedutrans.sales

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val KitGreen = androidx.compose.ui.graphics.Color(0xFF168400)
private val KitGreenDark = androidx.compose.ui.graphics.Color(0xFF0B5F10)
private val KitGold = androidx.compose.ui.graphics.Color(0xFFD7A600)
private val KitSurface = androidx.compose.ui.graphics.Color(0xFFF6F8F4)

@Composable
fun MarketingKitScreen(vm: SalesViewModel) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Program", "Script", "Funnel")
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Marketing & Sales Kit", fontWeight = FontWeight.Black, fontSize = 19.sp)
            Text("Materi kerja resmi Sales GMU EduTrans. Harga selalu mengikuti master aktif.", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.Gray)
        }
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }
        when (tab) {
            0 -> ProgramCatalogTab(vm)
            1 -> ScriptKitTab()
            else -> FunnelKitTab(vm.dashboard)
        }
    }
}

@Composable
private fun ProgramCatalogTab(vm: SalesViewModel) {
    val context = LocalContext.current
    val catalog = vm.dashboard.catalog
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = KitGold.copy(alpha = .12f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Cara pakai", fontWeight = FontWeight.Black)
                    Text("Pilih program → cek paket aktif → bagikan ke calon sekolah. Jangan menjanjikan diskon di luar harga/approval resmi.", fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
        if (catalog.isEmpty()) {
            item { KitEmpty("Belum ada katalog yang dapat dimuat. Tekan refresh setelah login ulang bila diperlukan.") }
        } else {
            items(catalog, key = { it.id }) { program ->
                ProgramCard(program = program, onShare = { shareText(context, programShareText(program)) })
            }
        }
    }
}

@Composable
private fun ProgramCard(program: SalesProgram, onShare: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
        Column(Modifier.padding(18.dp)) {
            Text(program.category.uppercase(), color = KitGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(program.name, fontWeight = FontWeight.Black, fontSize = 18.sp)
            if (program.description.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(program.description, color = androidx.compose.ui.graphics.Color.Gray, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(10.dp))
            val startPrice = program.marketingStartPrice
            if (startPrice != null && startPrice > 0) {
                Text("Mulai ${rupiahKit(startPrice)} / peserta", color = KitGreenDark, fontWeight = FontWeight.Black)
            } else {
                Text("Harga: by quotation", color = KitGreenDark, fontWeight = FontWeight.Black)
            }
            Text("Minimum dasar ${program.minPax} peserta", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.Gray)
            if (program.marketingPriceNote.isNotBlank()) {
                Text(program.marketingPriceNote, fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.Gray)
            }

            if (program.packages.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Paket aktif", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                program.packages.forEach { pack ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = KitSurface
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(pack.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (pack.pricePerPax > 0) Text(rupiahKit(pack.pricePerPax), color = KitGreen, fontWeight = FontWeight.Black)
                            }
                            Text("Min. ${pack.minPax} pax", fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.Gray)
                            if (pack.facilities.isNotEmpty()) {
                                Text(pack.facilities.take(4).joinToString(" • "), fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.Gray, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onShare, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Send, null)
                Spacer(Modifier.width(8.dp))
                Text("Bagikan Program")
            }
        }
    }
}

@Composable
private fun ScriptKitTab() {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { KitEmpty("Gunakan script sebagai panduan. Sesuaikan nama, kebutuhan sekolah, dan konteks percakapan; jangan mengirim spam massal.") }
        items(SalesKitData.templates, key = { it.id }) { template ->
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(template.category.uppercase(), color = KitGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Text(template.title, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(template.body, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(template.body)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Salin")
                        }
                        Button(
                            onClick = { shareText(context, template.body) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Send, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Bagikan")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FunnelKitTab(data: SalesDashboard) {
    val funnel = data.funnel
    val leads = data.leads
    val actualProspects = leads.size
    val actualQualified = leads.count { it.stage in setOf("QUALIFIED", "QUOTATION", "NEGOTIATION", "WAITING_DP", "WON") }
    val actualQuotes = leads.count { it.stage in setOf("QUOTATION", "NEGOTIATION", "WAITING_DP", "WON") }
    val actualWon = leads.count { it.stage == "WON" }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Funnel Target ${funnel.targetPaidPax} Pax", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    KitFunnelRow("Prospek", actualProspects, funnel.requiredProspects)
                    KitFunnelRow("Qualified", actualQualified, funnel.requiredQualifiedLeads)
                    KitFunnelRow("Quotation", actualQuotes, funnel.requiredQuotations)
                    KitFunnelRow("Won booking", actualWon, funnel.requiredWonBookings)
                    KitFunnelRow("Paid pax", data.portfolio.paidPax, funnel.targetPaidPax)
                }
            }
        }
        item {
            KitEmpty("Asumsi saat ini: rata-rata ${String.format(Locale.US, "%.0f", funnel.assumedAveragePaxPerWon)} pax per closing, Prospek→Qualified ${funnel.prospectToQualifiedPct.toInt()}%, Qualified→Quotation ${funnel.qualifiedToQuotationPct.toInt()}%, Quotation→Won ${funnel.quotationToWonPct.toInt()}%.")
        }
    }
}

@Composable
private fun KitFunnelRow(label: String, actual: Int, required: Int) {
    val pct = if (required <= 0) 0f else (actual.toFloat() / required).coerceIn(0f, 1f)
    Column(Modifier.padding(vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Bold)
            Text("$actual / $required", color = KitGreen, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(progress = pct, modifier = Modifier.fillMaxWidth().height(7.dp), color = KitGreen, trackColor = KitGreen.copy(alpha = .12f))
    }
}

@Composable
fun NewLeadDialog(
    catalog: List<SalesProgram>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (NewLeadInput) -> Unit
) {
    var institution by remember { mutableStateOf("") }
    var pic by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Cianjur") }
    var selectedProgram by remember { mutableStateOf<SalesProgram?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var tripDate by remember { mutableStateOf(LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(30).toString()) }
    var pax by remember { mutableStateOf("20") }
    var budget by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val paxValue = pax.toIntOrNull() ?: 0
    val validDate = runCatching { LocalDate.parse(tripDate) }.isSuccess
    val valid = institution.isNotBlank() && pic.isNotBlank() && whatsapp.isNotBlank() && paxValue > 0 && validDate

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Tambah Lead Baru", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.heightIn(max = 520.dp)) {
                item { Text("Data langsung masuk CRM Sales dan tersinkron ke backend GMU.", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.Gray) }
                item { OutlinedTextField(institution, { institution = it }, label = { Text("Nama sekolah/lembaga *") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(pic, { pic = it }, label = { Text("Nama PIC *") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(whatsapp, { whatsapp = it }, label = { Text("WhatsApp PIC *") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(city, { city = it }, label = { Text("Kota/Kabupaten") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Text("Program", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Box {
                        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedProgram?.name ?: "Pilih program / belum ditentukan", modifier = Modifier.weight(1f))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Belum ditentukan") }, onClick = { selectedProgram = null; menuOpen = false })
                            catalog.forEach { p ->
                                DropdownMenuItem(text = { Text(p.name) }, onClick = { selectedProgram = p; menuOpen = false })
                            }
                        }
                    }
                }
                item { OutlinedTextField(tripDate, { tripDate = it }, label = { Text("Tanggal kegiatan/tentatif * (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = !validDate) }
                item { OutlinedTextField(pax, { pax = it.filter(Char::isDigit) }, label = { Text("Estimasi pax *") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(budget, { budget = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Budget/pax (opsional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(notes, { notes = it }, label = { Text("Catatan kebutuhan") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        NewLeadInput(
                            institutionName = institution,
                            picName = pic,
                            whatsapp = whatsapp,
                            city = city,
                            programId = selectedProgram?.id,
                            customProgram = selectedProgram?.name.orEmpty(),
                            tripDate = tripDate,
                            pax = paxValue,
                            budgetPerPax = budget.toDoubleOrNull(),
                            notes = notes
                        )
                    )
                },
                enabled = valid && !busy
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Buat Lead")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun KitEmpty(message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
        Text(message, modifier = Modifier.padding(16.dp), fontSize = 12.sp, lineHeight = 18.sp, color = androidx.compose.ui.graphics.Color.Gray)
    }
}

private fun programShareText(program: SalesProgram): String {
    val price = program.marketingStartPrice?.takeIf { it > 0 }?.let { "Mulai ${rupiahKit(it)} / peserta" } ?: "Harga by quotation"
    val activePackages = if (program.packages.isEmpty()) "" else program.packages.joinToString("\n") { p ->
        val pPrice = if (p.pricePerPax > 0) rupiahKit(p.pricePerPax) else "By quotation"
        "• ${p.name}: $pPrice (min. ${p.minPax} pax)"
    }
    return buildString {
        append("GMU EduTrans — ${program.name}\n\n")
        if (program.description.isNotBlank()) append(program.description).append("\n\n")
        append(price).append("\n")
        append("Minimum dasar ${program.minPax} peserta\n")
        if (activePackages.isNotBlank()) append("\nPilihan paket aktif:\n").append(activePackages).append("\n")
        append("\nUntuk jadwal, kebutuhan sekolah, dan quotation resmi, silakan hubungi tim GMU EduTrans.")
    }
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        setPackage("com.whatsapp")
    }
    runCatching { context.startActivity(intent) }.onFailure {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(fallback, "Bagikan materi GMU EduTrans"))
    }
}

private fun rupiahKit(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")
