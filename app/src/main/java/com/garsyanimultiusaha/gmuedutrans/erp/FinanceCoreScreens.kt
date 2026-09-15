package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    if (!FinancialAccess.canView(session.profile.role)) {
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            EmptyCard("Financial Health hanya tersedia untuk Owner dan Manager.")
        }
        return
    }

    var tab by remember { mutableStateOf("Overview") }
    var addPayment by remember { mutableStateOf(false) }
    var addCost by remember { mutableStateOf(false) }
    val payments = vm.table("payments")
    val costs = vm.table("trip_costs")
    val stats = vm.dashboardStats()
    val collectionRate = if (stats.omzet > 0) stats.paid / stats.omzet * 100.0 else 0.0
    val health = when {
        stats.margin >= 25 && collectionRate >= 80 -> "Healthy"
        stats.margin >= 15 && collectionRate >= 60 -> "Watch"
        else -> "Attention"
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("Financial Health", "Cash, receivable, cost & profitability")
            if (tab == "Payments" || tab == "Costs") {
                TextButton(onClick = { if (tab == "Payments") addPayment = true else addCost = true }) { Text("+ Tambah") }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Omzet", rupiah(stats.omzet), Modifier.weight(1f), accent = true)
            MetricCard("Net Profit", rupiah(stats.profit), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Receivable", rupiah(stats.receivable), Modifier.weight(1f))
            MetricCard("Margin", String.format("%.1f%%", stats.margin), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (health) {
                    "Healthy" -> Color(0xFFEAF7EF)
                    "Watch" -> Color(0xFFFFF7E8)
                    else -> Color(0xFFFFEEEE)
                }
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Financial health", fontSize = 11.sp, color = Color.Gray)
                        Text(health, fontSize = 20.sp, fontWeight = FontWeight.Black, color = GmuDark)
                    }
                    StatusChip(health)
                }
                Spacer(Modifier.height(12.dp))
                Text("Collection rate " + String.format("%.1f%%", collectionRate), fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { (collectionRate.coerceIn(0.0, 100.0) / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = GmuGreen,
                    trackColor = Color.White.copy(alpha = .7f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf("Overview", "Payments", "Costs", "Profitability").forEach { label ->
                FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(10.dp))

        when (tab) {
            "Overview" -> LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Outstanding receivables", fontWeight = FontWeight.Black, color = GmuDark)
                }
                val outstanding = vm.bookings.map { b ->
                    val paid = vm.paidForBooking(b.id)
                    b to (b.omzet - paid).coerceAtLeast(0.0)
                }.filter { it.second > 0 }.sortedByDescending { it.second }
                if (outstanding.isEmpty()) item { EmptyCard("Tidak ada piutang aktif.") }
                items(outstanding.take(10), key = { it.first.id }) { (b, due) ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(b.bookingNo, fontWeight = FontWeight.Black, color = GmuDark)
                                StatusChip(b.status)
                            }
                            Text(b.customerName + " • " + b.programName, fontSize = 11.sp, color = Color.Gray)
                            Spacer(Modifier.height(6.dp))
                            Text(rupiah(due), fontSize = 18.sp, fontWeight = FontWeight.Black, color = GmuWarn)
                            Text("Outstanding", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }

            "Payments" -> LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (payments.isEmpty()) item { EmptyCard("Belum ada pembayaran.") }
                items(payments, key = { it.id }) { p ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(bookingLabel(vm, p.text("booking_id")), fontWeight = FontWeight.Bold, color = GmuDark)
                                StatusChip(p.text("payment_type"))
                            }
                            Text(rupiah(p.number("amount")), fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text(p.text("payment_date") + " • " + p.text("method"), fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }

            "Costs" -> LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (costs.isEmpty()) item { EmptyCard("Belum ada biaya trip.") }
                items(costs, key = { it.id }) { c ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Text(bookingLabel(vm, c.text("booking_id")), fontWeight = FontWeight.Bold, color = GmuDark)
                            Text(c.text("cost_category") + " • " + c.text("description"), fontSize = 12.sp)
                            Text("RAB " + rupiah(c.number("rab_amount")) + " • Aktual " + rupiah(c.number("actual_amount")), fontSize = 11.sp, color = GmuGreen)
                        }
                    }
                }
            }

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (vm.bookings.isEmpty()) item { EmptyCard("Belum ada booking untuk dihitung.") }
                items(vm.bookings, key = { it.id }) { b ->
                    val paid = vm.paidForBooking(b.id)
                    val actual = vm.actualCostForBooking(b.id)
                    val rab = vm.rabForBooking(b.id)
                    val profit = b.omzet - actual
                    val margin = if (b.omzet > 0) profit / b.omzet * 100 else 0.0
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(b.bookingNo + " • " + b.programName, fontWeight = FontWeight.Black, color = GmuDark, modifier = Modifier.weight(1f))
                                StatusChip(if (margin >= 25) "Healthy" else "Low Margin")
                            }
                            Text("Omzet " + rupiah(b.omzet) + " • Terbayar " + rupiah(paid), fontSize = 11.sp)
                            Text("RAB " + rupiah(rab) + " • Aktual " + rupiah(actual), fontSize = 11.sp)
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("Laba " + rupiah(profit) + " • Margin " + String.format("%.1f%%", margin), fontWeight = FontWeight.Bold, color = if (margin >= 25) GmuGreen else GmuWarn)
                            Text("Laba/Pax " + rupiah(if (b.pax > 0) profit / b.pax else 0.0) + " • Selisih RAB " + rupiah(rab - actual), fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }

    if (addPayment) {
        FinanceEntryDialog(
            title = "Tambah Payment",
            bookings = vm.bookings,
            fields = listOf("Jenis" to "DP", "Jumlah" to "", "Tanggal" to "", "Metode" to "Transfer"),
            busy = vm.actionBusy,
            onDismiss = { addPayment = false },
            onSave = { bookingId, v ->
                val amount = v["Jumlah"]?.toDoubleOrNull() ?: 0.0
                vm.insert(
                    "payments",
                    mapOf(
                        "booking_id" to bookingId,
                        "payment_type" to (v["Jenis"] ?: "DP"),
                        "amount" to amount,
                        "payment_date" to (v["Tanggal"] ?: ""),
                        "method" to (v["Metode"] ?: ""),
                        "verified_by" to session.userId
                    ),
                    "Payment berhasil ditambahkan."
                ) { ok, msg ->
                    onNotice(msg)
                    if (ok) addPayment = false
                }
            }
        )
    }

    if (addCost) {
        FinanceEntryDialog(
            title = "Tambah Biaya Trip",
            bookings = vm.bookings,
            fields = listOf("Kategori" to "", "Deskripsi" to "", "RAB" to "0", "Aktual" to "0"),
            busy = vm.actionBusy,
            onDismiss = { addCost = false },
            onSave = { bookingId, v ->
                vm.insert(
                    "trip_costs",
                    mapOf(
                        "booking_id" to bookingId,
                        "cost_category" to (v["Kategori"] ?: ""),
                        "description" to (v["Deskripsi"] ?: ""),
                        "rab_amount" to (v["RAB"]?.toDoubleOrNull() ?: 0.0),
                        "actual_amount" to (v["Aktual"]?.toDoubleOrNull() ?: 0.0)
                    ),
                    "Biaya trip berhasil ditambahkan."
                ) { ok, msg ->
                    onNotice(msg)
                    if (ok) addCost = false
                }
            }
        )
    }
}

@Composable
private fun FinanceEntryDialog(
    title: String,
    bookings: List<Booking>,
    fields: List<Pair<String, String>>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, String>) -> Unit
) {
    var booking by remember { mutableStateOf(bookings.firstOrNull()) }
    var menu by remember { mutableStateOf(false) }
    val values = remember { mutableStateMapOf<String, String>().apply { fields.forEach { put(it.first, it.second) } } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(booking?.bookingNo ?: "Pilih Booking")
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        bookings.forEach { b ->
                            DropdownMenuItem(text = { Text(b.bookingNo + " • " + b.programName) }, onClick = { booking = b; menu = false })
                        }
                    }
                }
                fields.forEach { (label, _) ->
                    if (title.contains("Payment", ignoreCase = true) && label == "Jenis") {
                        Spacer(Modifier.height(8.dp))
                        GmuSelect(
                            value = values[label].orEmpty().ifBlank { "DP" },
                            label = label,
                            options = listOf("DP", "Pelunasan", "Tambahan", "Refund"),
                            onSelect = { values[label] = it }
                        )
                    } else {
                        GmuField(values[label].orEmpty(), { values[label] = it }, label)
                    }
                }
            }
        },
        confirmButton = {
            val isPayment = title.contains("Payment", ignoreCase = true)
            val valid = if (isPayment) {
                (values["Jumlah"]?.toDoubleOrNull() ?: 0.0) > 0.0 &&
                    values["Tanggal"].orEmpty().matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
            } else {
                values["Kategori"].orEmpty().isNotBlank() &&
                    values["Deskripsi"].orEmpty().isNotBlank()
            }
            Button(
                onClick = { onSave(booking!!.id, values.toMap()) },
                enabled = !busy && booking != null && valid
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}
