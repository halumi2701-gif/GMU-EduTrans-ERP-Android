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
fun DashboardScreen(vm: MainViewModel, session: SessionState) {
    val s = vm.dashboardStats()
    val financeVisible = session.profile.role in listOf("Owner", "Manager", "Finance")
    val needsAttention = buildList {
        val approvalCount = vm.table("approvals").count { it.text("status") == "Pending" }
        if (approvalCount > 0) add(approvalCount.toString() + " approval menunggu")
        val receivableTrips = vm.bookings.count { b ->
            b.omzet > vm.paidForBooking(b.id) && b.status !in listOf("Lead", "Quotation", "Closed")
        }
        if (financeVisible && receivableTrips > 0) add(receivableTrips.toString() + " booking masih memiliki piutang")
        val missingDocs = vm.bookings.count { b ->
            val count = vm.table("documents").count { it.text("booking_id") == b.id }
            b.status in listOf("Confirmed", "Preparation", "Trip") && count < 5
        }
        if (missingDocs > 0) add(missingDocs.toString() + " trip perlu kelengkapan dokumen")
    }
    val nextTrip = vm.bookings
        .filter { it.status !in listOf("Completed", "Closed") }
        .sortedBy { it.tripDate }
        .firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(GmuDark, GmuGreen))
                    )
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                Text(
                    "Good " + greetingLabel() + ", " + session.profile.fullName.substringBefore(" ") + " 👋",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    session.profile.role + " • GMU EduTrans",
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(24.dp))
                if (financeVisible) {
                    Text("Omzet bulan ini", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
                    Text(rupiah(s.omzet), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StartupStatPill(s.bookingsMonth.toString() + " booking")
                        StartupStatPill(s.pax.toString() + " pax")
                        StartupStatPill(String.format("%.1f%% margin", s.margin))
                    }
                } else {
                    Text("Operasional hari ini", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
                    Text(s.upcoming.toString() + " trip mendatang", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StartupStatPill(s.bookingsMonth.toString() + " booking")
                        StartupStatPill(s.pax.toString() + " pax")
                    }
                }
            }
        }

        if (vm.dataBusy) {
            item {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    color = GmuGreen
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Business snapshot", fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Booking", s.bookingsMonth.toString(), Modifier.weight(1f))
                    MetricCard("Pax", s.pax.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Upcoming", s.upcoming.toString(), Modifier.weight(1f))
                    MetricCard("Customer", s.customers.toString(), Modifier.weight(1f))
                }
                if (financeVisible) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("Piutang", rupiah(s.receivable), Modifier.weight(1f))
                        MetricCard("Laba", rupiah(s.profit), Modifier.weight(1f), accent = true)
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Needs attention", fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark)
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (needsAttention.isEmpty()) Color(0xFFEAF7EF) else Color(0xFFFFF7E8)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (needsAttention.isEmpty()) {
                            Text("All clear", fontWeight = FontWeight.Black, color = GmuGreen)
                            Text("Tidak ada item kritis yang perlu tindakan saat ini.", fontSize = 12.sp, color = Color.Gray)
                        } else {
                            needsAttention.take(4).forEachIndexed { index, item ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text("•", color = GmuWarn, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.width(8.dp))
                                    Text(item, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (index < needsAttention.take(4).lastIndex) {
                                    HorizontalDivider(color = Color.Black.copy(alpha = .05f))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Next trip", fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark)
                Spacer(Modifier.height(8.dp))
                if (nextTrip == null) {
                    EmptyCard("Belum ada trip mendatang.")
                } else {
                    val trip = vm.table("trips").firstOrNull { it.text("booking_id") == nextTrip.id }
                    val progress = trip?.int("operational_progress") ?: 0
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(nextTrip.programName, fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark)
                                    Text(nextTrip.customerName, fontSize = 12.sp, color = Color.Gray)
                                }
                                StatusChip(nextTrip.status)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(nextTrip.tripDate + " • " + nextTrip.pax + " pax", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Operation readiness", fontSize = 11.sp, color = Color.Gray)
                                Text(progress.toString() + "%", fontWeight = FontWeight.Black, color = GmuGreen)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0, 100) / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = GmuGreen,
                                trackColor = GmuSoft
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Quick actions", fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StartupActionCard(
                        title = "Booking",
                        subtitle = "Pipeline & order",
                        modifier = Modifier.weight(1f),
                        onClick = { vm.navigate(AppPage.BOOKINGS) }
                    )
                    if (AppPage.OPERATIONS in RoleAccess.pages(session.profile.role)) {
                        StartupActionCard(
                            title = "Trip Control",
                            subtitle = "Readiness & TL",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.navigate(AppPage.OPERATIONS) }
                        )
                    } else {
                        StartupActionCard(
                            title = "Customer",
                            subtitle = "CRM & history",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.navigate(AppPage.CUSTOMERS) }
                        )
                    }
                }
            }
        }

        if (session.profile.role in listOf("Owner", "Manager")) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Performance insights", fontWeight = FontWeight.Black, fontSize = 17.sp, color = GmuDark)
                    Spacer(Modifier.height(8.dp))
                    RankingCard("Top Program", s.topPrograms)
                    Spacer(Modifier.height(10.dp))
                    RankingCard("Top Customer", s.topCustomers)
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { vm.loadAll() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Refresh dashboard")
            }
        }
    }
}

@Composable
private fun StartupStatPill(text: String) {
    Surface(
        color = Color.White.copy(alpha = .14f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun StartupActionCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = GmuDark, fontWeight = FontWeight.Black)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(12.dp))
            Text("Open  →", color = GmuGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun greetingLabel(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..10 -> "Morning"
        in 11..15 -> "Afternoon"
        else -> "Evening"
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
    var selectedBooking by remember { mutableStateOf<Booking?>(null) }
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
                Card(
                    onClick = { selectedBooking = b },
                    shape = RoundedCornerShape(18.dp)
                ) {
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

    selectedCustomer?.let { customer ->
        CustomerDetailDialog(
            vm = vm,
            customer = customer,
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
    val tabs = listOf("Overview", "Finance", "Operation", "Documents", "Activity")

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
                        DetailLine("Harga / Pax", rupiah(booking.pricePerPax))
                        DetailLine("Omzet", rupiah(booking.omzet))
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
                                    vm.update(
                                        "bookings",
                                        booking.id,
                                        mapOf("status" to status),
                                        "Status booking diperbarui menjadi " + status
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
                Text(history.size.toString() + " booking • " + rupiah(history.sumOf { it.omzet }), fontSize = 11.sp, color = GmuGreen)
                Spacer(Modifier.height(6.dp))
                if (history.isEmpty()) {
                    Text("Belum ada histori booking.", color = Color.Gray, fontSize = 12.sp)
                } else {
                    history.take(12).forEach { b ->
                        Text(b.bookingNo + " • " + b.programName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(b.tripDate + " • " + b.pax + " pax • " + rupiah(b.omzet), fontSize = 11.sp, color = Color.Gray)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(.42f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(.58f))
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

