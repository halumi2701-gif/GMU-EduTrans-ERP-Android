package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    var tab by remember { mutableStateOf("Trips") }
    var quickAdd by remember { mutableStateOf(false) }
    val tabs = listOf("Trips", "Manifest", "Rundown", "Attendance", "Op Sheet")

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("Trip Operation", "Control center untuk persiapan & pelaksanaan")
            if (session.profile.role in listOf("Owner", "Manager", "Operation", "TL")) {
                TextButton(onClick = { quickAdd = true }) { Text("+ Tambah") }
            }
        }
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = tab == label,
                    onClick = { tab = label },
                    shape = SegmentedButtonDefaults.itemShape(index, tabs.size)
                ) { Text(label, fontSize = 10.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))

        when (tab) {
            "Trips" -> TripList(vm)
            "Manifest" -> GenericOperationList(
                rows = vm.table("manifests"),
                empty = "Belum ada manifest.",
                title = { it.text("participant_name") },
                subtitle = { row ->
                    bookingLabel(vm, row.text("booking_id")) + " • " +
                        listOf(row.text("class_or_age"), row.text("gender"), row.text("phone")).filter { it.isNotBlank() }.joinToString(" • ")
                },
                status = { it.text("special_needs").ifBlank { "Participant" } }
            )
            "Rundown" -> GenericOperationList(
                rows = vm.table("rundown_items"),
                empty = "Belum ada rundown.",
                title = { it.text("activity_time") + "  " + it.text("activity") },
                subtitle = { row -> bookingLabel(vm, row.text("booking_id")) + " • " + row.text("location") + " • PIC " + row.text("pic") },
                status = { "Timeline" }
            )
            "Attendance" -> AttendanceList(vm)
            else -> OperationSheetList(vm)
        }
    }

    if (quickAdd) {
        OperationQuickAddDialog(
            tab = tab,
            bookings = vm.bookings,
            busy = vm.actionBusy,
            userId = session.userId,
            onDismiss = { if (!vm.actionBusy) quickAdd = false },
            onSave = { table, values, message ->
                vm.insert(table, values, message) { ok, msg ->
                    onNotice(msg)
                    if (ok) quickAdd = false
                }
            }
        )
    }
}

@Composable
private fun TripList(vm: MainViewModel) {
    val trips = vm.table("trips")
    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (vm.bookings.isEmpty()) item { EmptyCard("Belum ada booking trip.") }
        items(vm.bookings, key = { it.id }) { booking ->
            val trip = trips.firstOrNull { it.text("booking_id") == booking.id }
            val progress = trip?.int("operational_progress") ?: 0
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(booking.bookingNo, fontWeight = FontWeight.Black, color = GmuDark)
                        StatusChip(booking.status)
                    }
                    Text(booking.programName, fontWeight = FontWeight.Bold)
                    Text(booking.customerName + " • " + booking.tripDate + " • " + booking.pax + " pax", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(10.dp))
                    Text("Operation Readiness " + progress + "%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun AttendanceList(vm: MainViewModel) {
    val attendance = vm.table("attendance")
    val manifests = vm.table("manifests").associateBy { it.id }
    val present = attendance.count { it.bool("present") }
    Column {
        Card(colors = CardDefaults.cardColors(containerColor = GmuSoft), shape = RoundedCornerShape(18.dp)) {
            Text(present.toString() + " / " + attendance.size + " Present", Modifier.fillMaxWidth().padding(16.dp), fontWeight = FontWeight.Black, color = GmuDark)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (attendance.isEmpty()) item { EmptyCard("Absensi belum tersedia.") }
            items(attendance, key = { it.id }) { a ->
                val m = manifests[a.text("manifest_id")]
                Card(shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(m?.text("participant_name").orEmpty().ifBlank { "Participant" }, fontWeight = FontWeight.Bold)
                            Text(bookingLabel(vm, a.text("booking_id")), fontSize = 11.sp, color = Color.Gray)
                        }
                        StatusChip(if (a.bool("present")) "Present" else "Absent")
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationSheetList(vm: MainViewModel) {
    val rows = vm.table("operation_sheets")
    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (rows.isEmpty()) item { EmptyCard("Operation Sheet belum tersedia.") }
        items(rows, key = { it.id }) { r ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(bookingLabel(vm, r.text("booking_id")), fontWeight = FontWeight.Black, color = GmuDark)
                        StatusChip(r.text("readiness_status"))
                    }
                    Text("Meeting " + r.text("meeting_time") + " • " + r.text("transport"), fontSize = 11.sp)
                    Text("PIC: " + r.text("operation_pic") + " • Driver: " + r.text("driver_contact"), fontSize = 11.sp, color = Color.Gray)
                    if (r.text("equipment").isNotBlank()) Text("Equipment: " + r.text("equipment"), fontSize = 11.sp, color = GmuGreen)
                }
            }
        }
    }
}

@Composable
private fun GenericOperationList(
    rows: List<ErpRow>,
    empty: String,
    title: (ErpRow) -> String,
    subtitle: (ErpRow) -> String,
    status: (ErpRow) -> String
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (rows.isEmpty()) item { EmptyCard(empty) }
        items(rows, key = { it.id }) { r ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(title(r), fontWeight = FontWeight.Bold, color = GmuDark, modifier = Modifier.weight(1f))
                        StatusChip(status(r))
                    }
                    Text(subtitle(r), fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun OperationQuickAddDialog(
    tab: String,
    bookings: List<Booking>,
    busy: Boolean,
    userId: String,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, Any?>, String) -> Unit
) {
    var booking by remember { mutableStateOf(bookings.firstOrNull()) }
    var bookingMenu by remember { mutableStateOf(false) }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }

    val labels = when (tab) {
        "Trips" -> listOf("Progress 0-100", "Operation PIC ID", "TL ID", "Catatan")
        "Manifest" -> listOf("Nama Peserta", "Kelas / Usia", "Gender", "No. HP")
        "Rundown" -> listOf("Jam HH:MM", "Aktivitas", "Lokasi", "PIC")
        "Attendance" -> listOf("Manifest ID", "Hadir true/false", "Catatan", "-")
        else -> listOf("Meeting Time HH:MM", "Transport", "Operation PIC", "Equipment")
    }
    val values = listOf(a, b, c, d)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah " + tab) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box {
                    OutlinedButton(onClick = { bookingMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(booking?.bookingNo ?: "Pilih Booking")
                    }
                    DropdownMenu(bookingMenu, onDismissRequest = { bookingMenu = false }) {
                        bookings.forEach { x ->
                            DropdownMenuItem(text = { Text(x.bookingNo + " • " + x.programName) }, onClick = { booking = x; bookingMenu = false })
                        }
                    }
                }
                GmuField(a, { a = it }, labels[0])
                GmuField(b, { b = it }, labels[1])
                GmuField(c, { c = it }, labels[2])
                GmuField(d, { d = it }, labels[3])
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && booking != null,
                onClick = {
                    val id = booking!!.id
                    when (tab) {
                        "Trips" -> onSave(
                            "trips",
                            mapOf(
                                "booking_id" to id,
                                "operational_progress" to (a.toIntOrNull() ?: 0),
                                "operation_pic_id" to b.ifBlank { null },
                                "tl_id" to c.ifBlank { null },
                                "updated_by" to userId
                            ),
                            "Trip operation berhasil dibuat."
                        )
                        "Manifest" -> onSave(
                            "manifests",
                            mapOf(
                                "booking_id" to id,
                                "participant_name" to a,
                                "class_or_age" to b.ifBlank { null },
                                "gender" to c.ifBlank { null },
                                "phone" to d.ifBlank { null }
                            ),
                            "Peserta manifest ditambahkan."
                        )
                        "Rundown" -> onSave(
                            "rundown_items",
                            mapOf(
                                "booking_id" to id,
                                "activity_time" to a.ifBlank { null },
                                "activity" to b,
                                "location" to c.ifBlank { null },
                                "pic" to d.ifBlank { null }
                            ),
                            "Rundown ditambahkan."
                        )
                        "Attendance" -> onSave(
                            "attendance",
                            mapOf(
                                "booking_id" to id,
                                "manifest_id" to a,
                                "present" to b.equals("true", true),
                                "checked_by" to userId
                            ),
                            "Absensi ditambahkan."
                        )
                        else -> onSave(
                            "operation_sheets",
                            mapOf(
                                "booking_id" to id,
                                "meeting_time" to a.ifBlank { null },
                                "transport" to b.ifBlank { null },
                                "operation_pic" to c.ifBlank { null },
                                "equipment" to d.ifBlank { null },
                                "updated_by" to userId
                            ),
                            "Operation Sheet dibuat."
                        )
                    }
                }
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
fun VendorsScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    var tab by remember { mutableStateOf("Vendor") }
    var add by remember { mutableStateOf(false) }
    val vendors = vm.table("vendors")
    val pos = vm.table("vendor_pos")

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("Vendor & PO", "Supplier transport, konsumsi, venue & aktivitas")
            if (session.profile.role in listOf("Owner", "Manager", "Operation")) {
                TextButton(onClick = { add = true }) { Text("+ Tambah") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == "Vendor", onClick = { tab = "Vendor" }, label = { Text("Vendor") })
            FilterChip(selected = tab == "PO", onClick = { tab = "PO" }, label = { Text("PO Vendor") })
        }
        Spacer(Modifier.height(8.dp))
        if (tab == "Vendor") {
            LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (vendors.isEmpty()) item { EmptyCard("Belum ada vendor.") }
                items(vendors, key = { it.id }) { v ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(v.text("name"), fontWeight = FontWeight.Black, color = GmuDark)
                                StatusChip(if (v.text("is_active") == "false") "Inactive" else "Active")
                            }
                            Text(v.text("category") + " • PIC " + v.text("pic_name"), fontSize = 11.sp, color = Color.Gray)
                            Text(v.text("phone"), fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (pos.isEmpty()) item { EmptyCard("Belum ada PO Vendor.") }
                items(pos, key = { it.id }) { po ->
                    val vendor = vendors.firstOrNull { it.id == po.text("vendor_id") }?.text("name").orEmpty()
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(po.text("po_no").ifBlank { "PO Vendor" }, fontWeight = FontWeight.Black, color = GmuDark)
                                StatusChip(po.text("status"))
                            }
                            Text(vendor + " • " + bookingLabel(vm, po.text("booking_id")), fontSize = 11.sp, color = Color.Gray)
                            Text(po.text("description"), fontSize = 12.sp)
                            Text(rupiah(po.number("amount")), fontSize = 17.sp, fontWeight = FontWeight.Black, color = GmuGreen)
                        }
                    }
                }
            }
        }
    }

    if (add) {
        VendorDialog(
            mode = tab,
            bookings = vm.bookings,
            vendors = vendors,
            userId = session.userId,
            busy = vm.actionBusy,
            onDismiss = { if (!vm.actionBusy) add = false },
            onSave = { table, values, msg ->
                vm.insert(table, values, msg) { ok, message ->
                    onNotice(message)
                    if (ok) add = false
                }
            }
        )
    }
}

@Composable
private fun VendorDialog(
    mode: String,
    bookings: List<Booking>,
    vendors: List<ErpRow>,
    userId: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, Any?>, String) -> Unit
) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }
    var booking by remember { mutableStateOf(bookings.firstOrNull()) }
    var vendor by remember { mutableStateOf(vendors.firstOrNull()) }
    var menuBooking by remember { mutableStateOf(false) }
    var menuVendor by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == "Vendor") "Vendor Baru" else "PO Vendor Baru") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (mode == "Vendor") {
                    GmuField(a, { a = it }, "Nama Vendor *")
                    GmuField(b, { b = it }, "Kategori")
                    GmuField(c, { c = it }, "PIC")
                    GmuField(d, { d = it }, "Telepon")
                } else {
                    Box {
                        OutlinedButton(onClick = { menuBooking = true }, modifier = Modifier.fillMaxWidth()) { Text(booking?.bookingNo ?: "Pilih Booking") }
                        DropdownMenu(menuBooking, onDismissRequest = { menuBooking = false }) {
                            bookings.forEach { x -> DropdownMenuItem(text = { Text(x.bookingNo) }, onClick = { booking = x; menuBooking = false }) }
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { menuVendor = true }, modifier = Modifier.fillMaxWidth()) { Text(vendor?.text("name") ?: "Pilih Vendor") }
                        DropdownMenu(menuVendor, onDismissRequest = { menuVendor = false }) {
                            vendors.forEach { x -> DropdownMenuItem(text = { Text(x.text("name")) }, onClick = { vendor = x; menuVendor = false }) }
                        }
                    }
                    GmuField(a, { a = it }, "Deskripsi *")
                    GmuField(b, { b = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Amount")
                    GmuField(c, { c = it }, "Due Date YYYY-MM-DD")
                    GmuField(d, { d = it }, "Status (Draft/Pending)")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && if (mode == "Vendor") a.isNotBlank() else booking != null && vendor != null && a.isNotBlank(),
                onClick = {
                    if (mode == "Vendor") {
                        onSave(
                            "vendors",
                            mapOf("name" to a, "category" to b, "pic_name" to c, "phone" to d, "created_by" to userId, "is_active" to true),
                            "Vendor berhasil dibuat."
                        )
                    } else {
                        onSave(
                            "vendor_pos",
                            mapOf(
                                "booking_id" to booking!!.id,
                                "vendor_id" to vendor!!.id,
                                "description" to a,
                                "amount" to (b.toDoubleOrNull() ?: 0.0),
                                "due_date" to c.ifBlank { null },
                                "status" to d.ifBlank { "Draft" }
                            ),
                            "PO Vendor berhasil dibuat."
                        )
                    }
                }
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
fun TripFolderScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    var selected by remember { mutableStateOf(vm.bookings.firstOrNull()) }
    var menu by remember { mutableStateOf(false) }
    val docs = vm.table("documents")
    val expected = listOf(
        "Booking Form", "Quotation", "Invoice", "PO Vendor", "Manifest",
        "Rundown", "Operation Sheet", "Absensi", "Laporan Trip", "Evaluasi"
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        SectionTitle("GMU Trip Folder", "Semua dokumen administrasi trip dalam satu tempat")
        Spacer(Modifier.height(10.dp))
        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let { it.bookingNo + " • " + it.programName } ?: "Pilih Booking")
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                vm.bookings.forEach { b ->
                    DropdownMenuItem(text = { Text(b.bookingNo + " • " + b.programName) }, onClick = { selected = b; menu = false })
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (selected == null) item { EmptyCard("Pilih booking untuk melihat Trip Folder.") }
            else items(expected) { type ->
                val row = docs.firstOrNull { it.text("booking_id") == selected!!.id && it.text("document_type") == type }
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(type, fontWeight = FontWeight.Bold, color = GmuDark)
                            Text(row?.text("document_no").orEmpty().ifBlank { "Belum dibuat" }, fontSize = 11.sp, color = Color.Gray)
                        }
                        if (row != null) {
                            StatusChip(row.text("status"))
                        } else {
                            TextButton(
                                onClick = {
                                    vm.insert(
                                        "documents",
                                        mapOf(
                                            "booking_id" to selected!!.id,
                                            "document_type" to type,
                                            "status" to "Draft",
                                            "generated_by" to session.userId
                                        ),
                                        type + " dibuat sebagai Draft."
                                    ) { _, msg -> onNotice(msg) }
                                }
                            ) { Text("+ Draft") }
                        }
                    }
                }
            }
        }
    }
}
