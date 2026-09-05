package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.util.Locale

private val GmuGreen = Color(0xFF0A6A3F)
private val GmuDark = Color(0xFF06482F)
private val GmuGold = Color(0xFFD5A300)
private val GmuBackground = Color(0xFFF3F7F5)

private fun rupiah(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")

@Composable
fun GmuNativeApp(vm: MainViewModel = viewModel()) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = GmuGreen,
            secondary = GmuGold,
            background = GmuBackground,
            surface = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = GmuBackground) {
            when (val state = vm.state) {
                AppState.Loading -> LoadingScreen()
                AppState.LoggedOut -> LoginScreen(onLogin = vm::login, busy = vm.actionBusy)
                is AppState.Error -> LoginScreen(
                    onLogin = vm::login,
                    busy = vm.actionBusy,
                    error = state.message,
                    onClearError = vm::backToLogin
                )
                is AppState.LoggedIn -> MainShell(vm, state.session)
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = GmuGreen)
            Spacer(Modifier.height(12.dp))
            Text("GMU EduTrans ERP", fontWeight = FontWeight.Bold)
            Text("Android Native v0.2", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun LoginScreen(
    onLogin: (String, String) -> Unit,
    busy: Boolean,
    error: String? = null,
    onClearError: (() -> Unit)? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("GMU", color = GmuGold, fontWeight = FontWeight.Black, fontSize = 42.sp)
        Text("EduTrans ERP", color = GmuDark, fontWeight = FontWeight.Black, fontSize = 30.sp)
        Text("Android Native v0.2", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(28.dp))

        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Masuk", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("Native Android • Supabase langsung • tanpa WebView", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        onClearError?.invoke()
                        onLogin(email, password)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && email.isNotBlank() && password.length >= 8
                ) {
                    Text(if (busy) "Memproses…" else "Masuk ke ERP")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Brand of PT Garsyani Multi Usaha • More Than a Trip, It’s a Learning Journey.",
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

private fun pagesFor(role: String): List<AppPage> =
    if (role in listOf("Owner", "Manager", "Admin", "Sales")) {
        listOf(AppPage.DASHBOARD, AppPage.BOOKINGS, AppPage.CUSTOMERS)
    } else {
        listOf(AppPage.DASHBOARD)
    }

private fun pageLabel(page: AppPage): String = when (page) {
    AppPage.DASHBOARD -> "Dashboard"
    AppPage.BOOKINGS -> "Booking"
    AppPage.CUSTOMERS -> "Customer"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(vm: MainViewModel, session: SessionState) {
    val pages = remember(session.profile.role) { pagesFor(session.profile.role) }
    var showCustomerDialog by remember { mutableStateOf(false) }
    var showBookingDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm.currentPage, pages) {
        if (vm.currentPage !in pages) vm.navigate(AppPage.DASHBOARD)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GMU EduTrans ERP", fontWeight = FontWeight.Bold)
                        Text(
                            session.profile.fullName + " • " + session.profile.role + " • Native v0.2",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                },
                actions = {
                    TextButton(onClick = vm::logout) { Text("Keluar") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                pages.forEach { page ->
                    NavigationBarItem(
                        selected = vm.currentPage == page,
                        onClick = { vm.navigate(page) },
                        icon = { Text(if (vm.currentPage == page) "●" else "○") },
                        label = { Text(pageLabel(page)) }
                    )
                }
            }
        },
        floatingActionButton = {
            when (vm.currentPage) {
                AppPage.BOOKINGS -> if (session.profile.role in listOf("Owner", "Manager", "Admin", "Sales")) {
                    ExtendedFloatingActionButton(onClick = { showBookingDialog = true }) { Text("+ Booking") }
                }
                AppPage.CUSTOMERS -> if (session.profile.role in listOf("Owner", "Manager", "Admin", "Sales")) {
                    ExtendedFloatingActionButton(onClick = { showCustomerDialog = true }) { Text("+ Customer") }
                }
                else -> Unit
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (vm.currentPage) {
                AppPage.DASHBOARD -> DashboardScreen(vm)
                AppPage.BOOKINGS -> BookingScreen(vm)
                AppPage.CUSTOMERS -> CustomerScreen(vm)
            }

            snackbarMessage?.let { msg ->
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    color = GmuDark,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(msg, color = Color.White, modifier = Modifier.padding(14.dp), fontSize = 12.sp)
                }
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2200)
                    snackbarMessage = null
                }
            }
        }
    }

    if (showCustomerDialog) {
        AddCustomerDialog(
            busy = vm.actionBusy,
            onDismiss = { if (!vm.actionBusy) showCustomerDialog = false },
            onSave = { name, type, pic, wa, email ->
                vm.createCustomer(name, type, pic, wa, email) { ok, message ->
                    snackbarMessage = message
                    if (ok) showCustomerDialog = false
                }
            }
        )
    }

    if (showBookingDialog) {
        AddBookingDialog(
            customers = vm.customers,
            busy = vm.actionBusy,
            onDismiss = { if (!vm.actionBusy) showBookingDialog = false },
            onSave = { customerId, program, date, pax, price, status, group, meeting ->
                vm.createBooking(customerId, program, date, pax, price, status, group, meeting) { ok, message ->
                    snackbarMessage = message
                    if (ok) showBookingDialog = false
                }
            }
        )
    }
}

@Composable
private fun DashboardScreen(vm: MainViewModel) {
    val stats = vm.dashboardStats()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Dashboard", fontSize = 24.sp, fontWeight = FontWeight.Black, color = GmuDark)
            Text("Ringkasan data yang dapat diakses oleh role login.", fontSize = 12.sp, color = Color.Gray)
        }

        if (vm.dataBusy) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        vm.dataError?.let { error ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEAEA))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Gagal memuat data", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(error, fontSize = 12.sp)
                        TextButton(onClick = { vm.loadData() }) { Text("Coba lagi") }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Booking", stats.bookings.toString(), Modifier.weight(1f))
                MetricCard("Customer", stats.customers.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Pax", stats.pax.toString(), Modifier.weight(1f))
                MetricCard("Trip Mendatang", stats.upcoming.toString(), Modifier.weight(1f))
            }
        }
        item { MetricCard("Omzet Booking", rupiah(stats.omzet), Modifier.fillMaxWidth(), highlight = true) }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Top Program", fontWeight = FontWeight.Bold, color = GmuDark)
                    Spacer(Modifier.height(8.dp))
                    if (stats.topPrograms.isEmpty()) {
                        Text("Belum ada data booking.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        stats.topPrograms.forEachIndexed { index, pair ->
                            Text((index + 1).toString() + ". " + pair.first, fontWeight = FontWeight.SemiBold)
                            Text(rupiah(pair.second), fontSize = 12.sp, color = GmuGreen)
                            if (index < stats.topPrograms.lastIndex) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }

        item {
            Button(onClick = { vm.loadData() }, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh Data")
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier, highlight: Boolean = false) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (highlight) Color(0xFFFFF7D8) else Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 11.sp, color = Color.Gray)
            Text(value, fontWeight = FontWeight.Black, fontSize = 20.sp, color = if (highlight) GmuDark else Color.Black)
        }
    }
}

@Composable
private fun BookingScreen(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, vm.bookings) {
        vm.bookings.filter {
            query.isBlank() ||
                it.bookingNo.contains(query, true) ||
                it.programName.contains(query, true) ||
                it.customerName.contains(query, true)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Booking", fontSize = 24.sp, fontWeight = FontWeight.Black, color = GmuDark)
        Text("Lead → Quotation → DP → Confirmed → Preparation → Trip → Completed → Closed", fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Cari booking / program / customer") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        if (vm.dataBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Card {
                        Text("Belum ada booking yang dapat ditampilkan.", Modifier.padding(16.dp), color = Color.Gray)
                    }
                }
            }
            items(filtered, key = { it.id }) { booking ->
                BookingCard(booking)
            }
        }
    }
}

@Composable
private fun BookingCard(booking: Booking) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(booking.bookingNo, fontWeight = FontWeight.Black, color = GmuDark)
                StatusPill(booking.status)
            }
            Spacer(Modifier.height(5.dp))
            Text(booking.programName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(booking.customerName, fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(9.dp))
            Text(booking.tripDate + " • " + booking.pax + " pax", fontSize = 12.sp)
            Text("Harga/Pax " + rupiah(booking.pricePerPax) + " • Omzet " + rupiah(booking.omzet), fontSize = 12.sp, color = GmuGreen)
            if (booking.meetingPoint.isNotBlank()) Text("Titik kumpul: " + booking.meetingPoint, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    Surface(color = Color(0xFFEAF6EF), shape = RoundedCornerShape(50)) {
        Text(status, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 10.sp, color = GmuGreen, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CustomerScreen(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, vm.customers) {
        vm.customers.filter {
            query.isBlank() ||
                it.name.contains(query, true) ||
                it.code.contains(query, true) ||
                it.pic.contains(query, true)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Customer", fontSize = 24.sp, fontWeight = FontWeight.Black, color = GmuDark)
        Text("Database sekolah, instansi, komunitas, partner, dan customer lainnya.", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Cari nama / kode / PIC") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        if (vm.dataBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Card {
                        Text("Belum ada customer yang dapat ditampilkan.", Modifier.padding(16.dp), color = Color.Gray)
                    }
                }
            }
            items(filtered, key = { it.id }) { customer ->
                CustomerCard(customer)
            }
        }
    }
}

@Composable
private fun CustomerCard(customer: Customer) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GmuDark)
                Text(customer.code, fontSize = 10.sp, color = GmuGold, fontWeight = FontWeight.Bold)
            }
            Text(customer.type, fontSize = 11.sp, color = Color.Gray)
            if (customer.pic.isNotBlank()) Text("PIC: " + customer.pic, fontSize = 12.sp)
            if (customer.whatsapp.isNotBlank()) Text("WA: " + customer.whatsapp, fontSize = 12.sp)
            if (customer.email.isNotBlank()) Text(customer.email, fontSize = 11.sp, color = Color.Gray)
        }
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
                OutlinedTextField(name, { name = it }, label = { Text("Nama *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(type, { type = it }, label = { Text("Tipe") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(pic, { pic = it }, label = { Text("PIC") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(wa, { wa = it }, label = { Text("WhatsApp") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, type, pic, wa, email) },
                enabled = !busy && name.isNotBlank()
            ) { Text(if (busy) "Menyimpan…" else "Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") }
        }
    )
}

@Composable
private fun AddBookingDialog(
    customers: List<Customer>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Double, String, String, String) -> Unit
) {
    var selectedCustomer by remember { mutableStateOf<Customer?>(customers.firstOrNull()) }
    var customerMenu by remember { mutableStateOf(false) }
    var program by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var pax by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Lead") }
    var statusMenu by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf("") }
    var meeting by remember { mutableStateOf("") }
    val statuses = listOf("Lead", "Quotation", "DP", "Confirmed", "Preparation", "Trip", "Completed", "Closed")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Booking Baru") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Customer *", fontSize = 11.sp, color = Color.Gray)
                Box {
                    OutlinedButton(onClick = { customerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedCustomer?.name ?: "Pilih customer")
                    }
                    DropdownMenu(expanded = customerMenu, onDismissRequest = { customerMenu = false }) {
                        customers.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = {
                                    selectedCustomer = c
                                    customerMenu = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(program, { program = it }, label = { Text("Program *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(date, { date = it }, label = { Text("Tanggal Trip * (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(pax, { pax = it.filter(Char::isDigit) }, label = { Text("Pax *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(price, { price = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Harga / Pax *") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Status", fontSize = 11.sp, color = Color.Gray)
                Box {
                    OutlinedButton(onClick = { statusMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(status) }
                    DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                        statuses.forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = {
                                status = s
                                statusMenu = false
                            })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(group, { group = it }, label = { Text("Kelas / Usia / Grup") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(meeting, { meeting = it }, label = { Text("Titik Kumpul") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            val paxNumber = pax.toIntOrNull() ?: 0
            val priceNumber = price.toDoubleOrNull() ?: -1.0
            Button(
                onClick = {
                    onSave(
                        selectedCustomer!!.id,
                        program,
                        date,
                        paxNumber,
                        priceNumber,
                        status,
                        group,
                        meeting
                    )
                },
                enabled = !busy &&
                    selectedCustomer != null &&
                    program.isNotBlank() &&
                    date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
                    paxNumber > 0 &&
                    priceNumber >= 0
            ) { Text(if (busy) "Menyimpan…" else "Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") }
        }
    )
}
