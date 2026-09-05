package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen(vm: MainViewModel, session: SessionState) {
    val s = vm.dashboardStats()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GmuGradientHeader {
                Text("Halo, " + session.profile.fullName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Text(session.profile.role + " • GMU EduTrans", color = Color(0xFFDDEBE4), fontSize = 12.sp)
            }
        }
        if (vm.dataBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        vm.dataError?.let { e ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEEEE))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Sebagian data belum termuat", color = GmuDanger, fontWeight = FontWeight.Bold)
                        Text(e, fontSize = 11.sp, color = Color.Gray)
                        TextButton(onClick = { vm.loadAll() }) { Text("Refresh") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Booking bulan ini", s.bookingsMonth.toString(), Modifier.weight(1f))
                MetricCard("Pax", s.pax.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Omzet", rupiah(s.omzet), Modifier.weight(1f), accent = true)
                MetricCard("Piutang", rupiah(s.receivable), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Kas / Terbayar", rupiah(s.paid), Modifier.weight(1f))
                MetricCard("Biaya Aktual", rupiah(s.actualCost), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Laba", rupiah(s.profit), Modifier.weight(1f), accent = true)
                MetricCard("Margin", String.format("%.1f%%", s.margin), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Trip mendatang", s.upcoming.toString(), Modifier.weight(1f))
                MetricCard("Customer", s.customers.toString(), Modifier.weight(1f))
            }
        }
        item { RankingCard("Top Program", s.topPrograms) }
        if (session.profile.role in listOf("Owner", "Manager")) {
            item { RankingCard("Top Customer", s.topCustomers) }
            item { RankingCard("Top Sales", s.topSales) }
        }
        item {
            SectionTitle("Upcoming Trip")
            Spacer(Modifier.height(8.dp))
            val upcoming = vm.bookings.filter { it.status !in listOf("Completed", "Closed") }.take(5)
            if (upcoming.isEmpty()) EmptyCard("Belum ada trip mendatang.")
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                upcoming.forEach { b ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(b.bookingNo, fontWeight = FontWeight.Black, color = GmuDark)
                                StatusChip(b.status)
                            }
                            Text(b.programName, fontWeight = FontWeight.Bold)
                            Text(b.customerName + " • " + b.tripDate + " • " + b.pax + " pax", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = { vm.loadAll() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Refresh Semua Data")
            }
        }
    }
}

@Composable
private fun RankingCard(title: String, values: List<Pair<String, Double>>) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Black, color = GmuDark)
            Spacer(Modifier.height(8.dp))
            if (values.isEmpty()) Text("Belum ada data.", color = Color.Gray, fontSize = 12.sp)
            values.forEachIndexed { i, pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text((i + 1).toString() + ". " + pair.first, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(rupiah(pair.second), fontSize = 11.sp, color = GmuGreen)
                }
                if (i < values.lastIndex) HorizontalDivider(Modifier.padding(vertical = 7.dp))
            }
        }
    }
}

@Composable
fun BookingScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var add by remember { mutableStateOf(false) }
    val statuses = listOf("All", "Lead", "Quotation", "DP", "Confirmed", "Preparation", "Trip", "Completed", "Closed")
    val filtered = vm.bookings.filter {
        (filter == "All" || it.status == filter) &&
            (query.isBlank() || it.bookingNo.contains(query, true) || it.programName.contains(query, true) || it.customerName.contains(query, true))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("Booking", "Lead sampai Closed")
            if (session.profile.role in listOf("Owner", "Manager", "Admin", "Sales")) {
                Button(onClick = { add = true }, shape = RoundedCornerShape(14.dp)) { Text("+ Baru") }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cari booking / program / customer") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            statuses.forEach { s ->
                FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text(s) })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (filtered.isEmpty()) item { EmptyCard("Belum ada booking.") }
            items(filtered, key = { it.id }) { b ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(b.bookingNo, fontWeight = FontWeight.Black, color = GmuDark)
                            StatusChip(b.status)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(b.programName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(b.customerName, fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text(b.tripDate + " • " + b.pax + " pax", fontSize = 12.sp)
                        Text("Harga/Pax " + rupiah(b.pricePerPax) + " • Omzet " + rupiah(b.omzet), fontSize = 12.sp, color = GmuGreen)
                        if (b.meetingPoint.isNotBlank()) Text("Titik kumpul: " + b.meetingPoint, fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }

    if (add) {
        AddBookingDialog(
            customers = vm.customers,
            busy = vm.actionBusy,
            onDismiss = { if (!vm.actionBusy) add = false },
            onSave = { customerId, program, date, pax, price, status, group, meeting ->
                vm.createBooking(customerId, program, date, pax, price, status, group, meeting) { ok, msg ->
                    onNotice(msg)
                    if (ok) add = false
                }
            }
        )
    }
}

@Composable
fun CustomerScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var add by remember { mutableStateOf(false) }
    val filtered = vm.customers.filter {
        query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true) || it.pic.contains(query, true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("Customer", "Sekolah, instansi, komunitas & partner")
            if (session.profile.role in listOf("Owner", "Manager", "Admin", "Sales")) {
                Button(onClick = { add = true }, shape = RoundedCornerShape(14.dp)) { Text("+ Customer") }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cari nama / kode / PIC") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (filtered.isEmpty()) item { EmptyCard("Belum ada customer.") }
            items(filtered, key = { it.id }) { c ->
                val history = vm.bookings.filter { it.customerId == c.id }
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(c.name, fontWeight = FontWeight.Black, color = GmuDark, fontSize = 16.sp)
                            Text(c.code, color = GmuGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(c.type, color = Color.Gray, fontSize = 11.sp)
                        if (c.pic.isNotBlank()) Text("PIC: " + c.pic, fontSize = 12.sp)
                        if (c.whatsapp.isNotBlank()) Text("WA: " + c.whatsapp, fontSize = 12.sp)
                        Text(history.size.toString() + " Booking • " + rupiah(history.sumOf { it.omzet }), color = GmuGreen, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    if (add) {
        AddCustomerDialog(
            busy = vm.actionBusy,
            onDismiss = { if (!vm.actionBusy) add = false },
            onSave = { name, type, pic, wa, email ->
                vm.createCustomer(name, type, pic, wa, email) { ok, msg ->
                    onNotice(msg)
                    if (ok) add = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    var tab by remember { mutableStateOf("Payments") }
    var addPayment by remember { mutableStateOf(false) }
    var addCost by remember { mutableStateOf(false) }
    val payments = vm.table("payments")
    val costs = vm.table("trip_costs")

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("Finance", "Payment, cost & profitability")
            if (session.profile.role in listOf("Owner", "Manager", "Finance")) {
                TextButton(onClick = { if (tab == "Payments") addPayment = true else addCost = true }) { Text("+ Tambah") }
            }
        }
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Payments", "Costs", "Profitability").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = tab == label,
                    onClick = { tab = label },
                    shape = SegmentedButtonDefaults.itemShape(i, 3)
                ) { Text(label, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))

        when (tab) {
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
                            Text(b.bookingNo + " • " + b.programName, fontWeight = FontWeight.Black, color = GmuDark)
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
private fun AddCustomerDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Sekolah") }
    var pic by remember { mutableStateOf("") }
    var wa by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customer Baru") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GmuField(name, { name = it }, "Nama *")
                GmuField(type, { type = it }, "Tipe")
                GmuField(pic, { pic = it }, "PIC")
                GmuField(wa, { wa = it }, "WhatsApp")
                GmuField(email, { email = it }, "Email")
            }
        },
        confirmButton = { Button(onClick = { onSave(name, type, pic, wa, email) }, enabled = !busy && name.isNotBlank()) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun AddBookingDialog(
    customers: List<Customer>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Double, String, String, String) -> Unit
) {
    var selected by remember { mutableStateOf(customers.firstOrNull()) }
    var customerMenu by remember { mutableStateOf(false) }
    var program by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var pax by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Lead") }
    var group by remember { mutableStateOf("") }
    var meeting by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Booking Baru") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box {
                    OutlinedButton(onClick = { customerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selected?.name ?: "Pilih Customer")
                    }
                    DropdownMenu(customerMenu, onDismissRequest = { customerMenu = false }) {
                        customers.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { selected = c; customerMenu = false })
                        }
                    }
                }
                GmuField(program, { program = it }, "Program *")
                GmuField(date, { date = it }, "Tanggal Trip (YYYY-MM-DD) *")
                GmuField(pax, { pax = it.filter(Char::isDigit) }, "Pax *")
                GmuField(price, { price = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Harga/Pax *")
                GmuField(status, { status = it }, "Status")
                GmuField(group, { group = it }, "Kelas / Usia / Grup")
                GmuField(meeting, { meeting = it }, "Titik Kumpul")
            }
        },
        confirmButton = {
            val p = pax.toIntOrNull() ?: 0
            val pr = price.toDoubleOrNull() ?: 0.0
            Button(
                onClick = { onSave(selected!!.id, program, date, p, pr, status, group, meeting) },
                enabled = !busy && selected != null && program.isNotBlank() && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && p > 0
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
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
                    GmuField(values[label].orEmpty(), { values[label] = it }, label)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(booking!!.id, values.toMap()) }, enabled = !busy && booking != null) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
fun GmuField(value: String, onValueChange: (String) -> Unit, label: String) {
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    )
}

