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

@Composable
fun BookingScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var viewMode by remember { mutableStateOf("Pipeline") }
    var add by remember { mutableStateOf(false) }
    var selectedBooking by remember { mutableStateOf<Booking?>(null) }
    val stages = listOf("Lead", "Quotation", "DP", "Confirmed", "Preparation", "Trip", "Completed", "Closed")
    val filtered = vm.bookings.filter {
        (filter == "All" || it.status == filter) &&
            (query.isBlank() || it.bookingNo.contains(query, true) || it.programName.contains(query, true) || it.customerName.contains(query, true))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("Booking Pipeline", "Lead → Quotation → DP → Trip → Closed")
            if (session.profile.role in listOf("Owner", "Manager", "Admin", "Sales")) {
                Button(onClick = { add = true }, shape = RoundedCornerShape(14.dp)) { Text("+ Baru") }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = viewMode == "Pipeline", onClick = { viewMode = "Pipeline" }, label = { Text("Pipeline") })
            FilterChip(selected = viewMode == "List", onClick = { viewMode = "List" }, label = { Text("List") })
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cari booking, program, customer") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(10.dp))

        if (viewMode == "Pipeline") {
            Column {
                Text("Sales pipeline", fontWeight = FontWeight.Black, fontSize = 15.sp, color = GmuDark)
                Text("Tap stage untuk membuka daftar booking pada tahap tersebut.", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    stages.forEach { stage ->
                        val stageBookings = vm.bookings.filter { it.status == stage }
                        Card(
                            onClick = {
                                filter = stage
                                viewMode = "List"
                            },
                            modifier = Modifier.width(150.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (stage == "Closed") GmuSoft else Color.White
                            )
                        ) {
                            Column(Modifier.padding(15.dp)) {
                                StatusChip(stage)
                                Spacer(Modifier.height(14.dp))
                                Text(stageBookings.size.toString(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = GmuDark)
                                Text("booking", fontSize = 11.sp, color = Color.Gray)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stageBookings.sumOf { it.pax }.toString() + " pax",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GmuGreen
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Recent pipeline", fontWeight = FontWeight.Black, fontSize = 15.sp, color = GmuDark)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val recent = vm.bookings
                        .filter { query.isBlank() || it.bookingNo.contains(query, true) || it.programName.contains(query, true) || it.customerName.contains(query, true) }
                        .take(12)
                    if (recent.isEmpty()) item { EmptyCard("Belum ada booking pada pipeline.") }
                    items(recent, key = { it.id }) { b ->
                        StartupBookingCard(b, session, onClick = { selectedBooking = b })
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("All") .plus(stages).forEach { stage ->
                    FilterChip(
                        selected = filter == stage,
                        onClick = { filter = stage },
                        label = { Text(stage) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (filtered.isEmpty()) item { EmptyCard("Belum ada booking.") }
                items(filtered, key = { it.id }) { b ->
                    StartupBookingCard(b, session, onClick = { selectedBooking = b })
                }
            }
        }
    }

    if (add) {
        AddBookingDialog(
            customers = vm.customers,
            canSeeFinancials = FinancialAccess.canView(session.profile.role),
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

    selectedBooking?.let { booking ->
        BookingDetailDialog(
            vm = vm,
            booking = booking,
            session = session,
            busy = vm.actionBusy,
            onDismiss = { selectedBooking = null },
            onNotice = onNotice
        )
    }
}

@Composable
private fun StartupBookingCard(booking: Booking, session: SessionState, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(booking.bookingNo, fontWeight = FontWeight.Black, color = GmuDark)
                StatusChip(booking.status)
            }
            Spacer(Modifier.height(5.dp))
            Text(booking.programName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(booking.customerName, fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(booking.tripDate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(booking.pax.toString() + " pax", fontSize = 12.sp, color = GmuGreen, fontWeight = FontWeight.Bold)
            }
            if (FinancialAccess.canView(session.profile.role)) {
                Spacer(Modifier.height(6.dp))
                Text("Omzet " + rupiah(booking.omzet), fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun CustomerScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var add by remember { mutableStateOf(false) }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
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
                Card(
                    onClick = { selectedCustomer = c },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(c.name, fontWeight = FontWeight.Black, color = GmuDark, fontSize = 16.sp)
                            Text(c.code, color = GmuGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(c.type, color = Color.Gray, fontSize = 11.sp)
                        if (c.pic.isNotBlank()) Text("PIC: " + c.pic, fontSize = 12.sp)
                        if (c.whatsapp.isNotBlank()) Text("WA: " + c.whatsapp, fontSize = 12.sp)
                        Text(
                            if (FinancialAccess.canView(session.profile.role)) {
                                history.size.toString() + " Booking • " + rupiah(history.sumOf { it.omzet })
                            } else {
                                history.size.toString() + " Booking"
                            },
                            color = GmuGreen,
                            fontSize = 11.sp
                        )
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

    selectedCustomer?.let { customer ->
        CustomerDetailDialog(
            vm = vm,
            customer = customer,
            canSeeFinancials = FinancialAccess.canView(session.profile.role),
            onDismiss = { selectedCustomer = null }
        )
    }
}

@Composable
private fun BookingDetailDialog(
    vm: MainViewModel,
    booking: Booking,
    session: SessionState,
    busy: Boolean,
    onDismiss: () -> Unit,
    onNotice: (String) -> Unit
) {
    var tab by remember(booking.id) { mutableStateOf("Overview") }
    var status by remember(booking.id, booking.status) { mutableStateOf(booking.status) }
    val tabs = buildList {
        add("Overview")
        if (FinancialAccess.canView(session.profile.role)) add("Finance")
        add("Operation")
        add("Documents")
        add("Activity")
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Column {
                Text(booking.bookingNo, fontWeight = FontWeight.Black, color = GmuDark)
                Text(booking.programName, fontSize = 13.sp, color = Color.Gray)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabs.forEach { item ->
                        FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item) })
                    }
                }
                Spacer(Modifier.height(10.dp))

                when (tab) {
                    "Overview" -> {
                        StatusChip(booking.status)
                        Spacer(Modifier.height(10.dp))
                        DetailLine("Customer", booking.customerName)
                        DetailLine("Tanggal Trip", booking.tripDate)
                        DetailLine("Pax", booking.pax.toString())
                        if (FinancialAccess.canView(session.profile.role)) {
                            DetailLine("Harga / Pax", rupiah(booking.pricePerPax))
                            DetailLine("Omzet", rupiah(booking.omzet))
                        }
                        DetailLine("Grup Peserta", booking.participantGroup)
                        DetailLine("Titik Kumpul", booking.meetingPoint)
                        DetailLine("Fasilitas", booking.facilities)
                        DetailLine("Kebutuhan Khusus", booking.specialRequirements)

                        if (session.profile.role in listOf("Owner", "Manager", "Admin", "Sales")) {
                            Spacer(Modifier.height(12.dp))
                            GmuSelect(
                                value = status,
                                label = "Update Status",
                                options = listOf("Lead", "Quotation", "DP", "Confirmed", "Preparation", "Trip", "Completed", "Closed"),
                                onSelect = { status = it }
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    vm.updateBookingStatus(
                                        booking.id,
                                        status
                                    ) { ok, msg ->
                                        onNotice(msg)
                                        if (ok) onDismiss()
                                    }
                                },
                                enabled = !busy && status != booking.status,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Simpan Status") }
                        }
                    }

                    "Finance" -> {
                        val paid = vm.paidForBooking(booking.id)
                        val rab = vm.rabForBooking(booking.id)
                        val actual = vm.actualCostForBooking(booking.id)
                        val profit = booking.omzet - actual
                        val margin = if (booking.omzet > 0) profit / booking.omzet * 100 else 0.0
                        DetailLine("Omzet", rupiah(booking.omzet))
                        DetailLine("Terbayar", rupiah(paid))
                        DetailLine("Piutang", rupiah((booking.omzet - paid).coerceAtLeast(0.0)))
                        DetailLine("RAB", rupiah(rab))
                        DetailLine("Biaya Aktual", rupiah(actual))
                        DetailLine("Laba Bersih", rupiah(profit))
                        DetailLine("Margin", String.format("%.1f%%", margin))
                        DetailLine("Laba / Pax", rupiah(if (booking.pax > 0) profit / booking.pax else 0.0))
                        DetailLine("Selisih RAB", rupiah(rab - actual))
                    }

                    "Operation" -> {
                        val trip = vm.table("trips").firstOrNull { it.text("booking_id") == booking.id }
                        val sheet = vm.table("operation_sheets").firstOrNull { it.text("booking_id") == booking.id }
                        val manifests = vm.table("manifests").filter { it.text("booking_id") == booking.id }
                        val attendance = vm.table("attendance").filter { it.text("booking_id") == booking.id }
                        val rundown = vm.table("rundown_items").filter { it.text("booking_id") == booking.id }
                        DetailLine("Readiness", (trip?.int("operational_progress") ?: 0).toString() + "%")
                        DetailLine("Operation Sheet", sheet?.text("readiness_status").orEmpty().ifBlank { "Belum dibuat" })
                        DetailLine("Manifest", manifests.size.toString() + " peserta")
                        DetailLine("Attendance", attendance.count { it.bool("present") }.toString() + " / " + attendance.size)
                        DetailLine("Rundown", rundown.size.toString() + " item")
                    }

                    "Documents" -> {
                        val docs = vm.table("documents").filter { it.text("booking_id") == booking.id }
                        val expected = listOf("Booking Form", "Quotation", "Invoice", "PO Vendor", "Manifest", "Rundown", "Operation Sheet", "Absensi", "Laporan Trip", "Evaluasi")
                        expected.forEach { type ->
                            val row = docs.firstOrNull { it.text("document_type") == type }
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(type, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                StatusChip(row?.text("status").orEmpty().ifBlank { "Belum" })
                            }
                        }
                    }

                    else -> {
                        val logs = vm.table("audit_logs").filter {
                            it.text("record_id") == booking.id ||
                                it.text("record_id") == booking.bookingNo ||
                                it.text("message").contains(booking.bookingNo, true)
                        }
                        if (logs.isEmpty()) {
                            Text("Belum ada activity untuk booking ini.", color = Color.Gray, fontSize = 12.sp)
                        } else {
                            logs.take(20).forEach { log ->
                                Text(log.text("action"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GmuDark)
                                Text(log.text("message"), fontSize = 11.sp, color = Color.Gray)
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Tutup") } }
    )
}

@Composable
private fun CustomerDetailDialog(
    vm: MainViewModel,
    customer: Customer,
    canSeeFinancials: Boolean,
    onDismiss: () -> Unit
) {
    val history = vm.bookings.filter { it.customerId == customer.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(customer.name, fontWeight = FontWeight.Black, color = GmuDark)
                Text(customer.code + " • " + customer.type, fontSize = 12.sp, color = Color.Gray)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DetailLine("PIC", customer.pic)
                DetailLine("WhatsApp", customer.whatsapp)
                DetailLine("Email", customer.email)
                DetailLine("Alamat", customer.address)
                DetailLine("Catatan", customer.notes)
                Spacer(Modifier.height(10.dp))
                Text("Histori Booking", fontWeight = FontWeight.Black, color = GmuDark)
                Text(
                    if (canSeeFinancials) {
                        history.size.toString() + " booking • " + rupiah(history.sumOf { it.omzet })
                    } else {
                        history.size.toString() + " booking"
                    },
                    fontSize = 11.sp,
                    color = GmuGreen
                )
                Spacer(Modifier.height(6.dp))
                if (history.isEmpty()) {
                    Text("Belum ada histori booking.", color = Color.Gray, fontSize = 12.sp)
                } else {
                    history.take(12).forEach { b ->
                        Text(b.bookingNo + " • " + b.programName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            if (canSeeFinancials) {
                                b.tripDate + " • " + b.pax + " pax • " + rupiah(b.omzet)
                            } else {
                                b.tripDate + " • " + b.pax + " pax"
                            },
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
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
                Spacer(Modifier.height(8.dp))
                GmuSelect(
                    value = type,
                    label = "Tipe Customer",
                    options = listOf("Sekolah", "Instansi", "Komunitas", "Partner", "Other"),
                    onSelect = { type = it }
                )
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
    canSeeFinancials: Boolean,
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
                if (canSeeFinancials) {
                    GmuField(price, { price = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Harga/Pax")
                }
                Spacer(Modifier.height(8.dp))
                GmuSelect(
                    value = status,
                    label = "Status",
                    options = listOf("Lead", "Quotation", "DP", "Confirmed", "Preparation", "Trip", "Completed", "Closed"),
                    onSelect = { status = it }
                )
                GmuField(group, { group = it }, "Kelas / Usia / Grup")
                GmuField(meeting, { meeting = it }, "Titik Kumpul")
            }
        },
        confirmButton = {
            val p = pax.toIntOrNull() ?: 0
            val pr = if (canSeeFinancials) (price.toDoubleOrNull() ?: 0.0) else 0.0
            Button(
                onClick = { onSave(selected!!.id, program, date, p, pr, status, group, meeting) },
                enabled = !busy && selected != null && program.isNotBlank() && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && p > 0
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}
