package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GmuNativeApp(vm: MainViewModel = viewModel()) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = GmuGreen,
            secondary = GmuGold,
            background = GmuBg,
            surface = Color.White,
            error = GmuDanger
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = GmuBg) {
            when (val state = vm.state) {
                AppState.Splash -> SplashScreen()
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
private fun SplashScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GmuDark, GmuGreen))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_gmu),
                    contentDescription = "GMU EduTrans",
                    modifier = Modifier.size(152.dp).padding(20.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("GMU EduTrans ERP", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("More Than a Trip, It’s a Learning Journey.", color = Color(0xFFDDEBE4), fontSize = 13.sp)
            Spacer(Modifier.height(42.dp))
            Text("PT Garsyani Multi Usaha", color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = GmuGreen)
            Spacer(Modifier.height(14.dp))
            Text("GMU EduTrans ERP", fontWeight = FontWeight.Bold, color = GmuDark)
            Text("Sinkronisasi data…", fontSize = 12.sp, color = Color.Gray)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 6.dp) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_gmu),
                    contentDescription = "GMU",
                    modifier = Modifier.size(84.dp).padding(10.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Welcome Back", color = GmuDark, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text("GMU EduTrans ERP", color = GmuGreen, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(28.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(20.dp)) {
                Text("Sign in", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Kelola operasional GMU EduTrans dalam satu aplikasi native.", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                AnimatedVisibility(visible = !error.isNullOrBlank()) {
                    Text(error.orEmpty(), color = GmuDanger, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        onClearError?.invoke()
                        onLogin(email, password)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !busy && email.isNotBlank() && password.length >= 8,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (busy) "Memproses…" else "Masuk ke ERP", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Native Android • Role-based access • Supabase secured", fontSize = 11.sp, color = Color.Gray)
    }
}

private fun mainTabFor(page: AppPage): MainTab = when (page) {
    AppPage.DASHBOARD -> MainTab.HOME
    AppPage.BOOKINGS, AppPage.CUSTOMERS -> MainTab.BOOKING
    AppPage.OPERATIONS, AppPage.TRIP_FOLDER -> MainTab.TRIP
    AppPage.FINANCE -> MainTab.FINANCE
    else -> MainTab.MORE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(vm: MainViewModel, session: SessionState) {
    val allowed = remember(session.profile.role) { RoleAccess.pages(session.profile.role) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showNotifications by remember { mutableStateOf(false) }

    if (vm.currentPage !in allowed) {
        LaunchedEffect(allowed) { vm.navigate(AppPage.DASHBOARD) }
    }

    val pendingApprovals = vm.table("approvals").count { it.text("status") == "Pending" }
    val currentTab = mainTabFor(vm.currentPage)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GMU EduTrans ERP", fontWeight = FontWeight.Black, color = GmuDark)
                        Text(
                            session.profile.fullName + " • " + session.profile.role + " • " + BuildConfig.VERSION_NAME,
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (pendingApprovals > 0) {
                                Badge { Text(pendingApprovals.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = { showNotifications = true }) {
                            Icon(Icons.Rounded.Notifications, contentDescription = "Notifications", tint = GmuDark)
                        }
                    }
                    IconButton(onClick = { vm.navigate(AppPage.PROFILE) }) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = "Profile", tint = GmuDark)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                BottomTab(
                    selected = currentTab == MainTab.HOME,
                    icon = Icons.Rounded.Home,
                    label = "Home",
                    onClick = { vm.navigate(AppPage.DASHBOARD) }
                )
                BottomTab(
                    selected = currentTab == MainTab.BOOKING,
                    icon = Icons.Rounded.EventNote,
                    label = "Booking",
                    enabled = AppPage.BOOKINGS in allowed || AppPage.CUSTOMERS in allowed,
                    onClick = {
                        if (AppPage.BOOKINGS in allowed) vm.navigate(AppPage.BOOKINGS)
                        else if (AppPage.CUSTOMERS in allowed) vm.navigate(AppPage.CUSTOMERS)
                    }
                )
                BottomTab(
                    selected = currentTab == MainTab.TRIP,
                    icon = Icons.Rounded.Luggage,
                    label = "Trip",
                    enabled = AppPage.OPERATIONS in allowed || AppPage.TRIP_FOLDER in allowed,
                    onClick = {
                        if (AppPage.OPERATIONS in allowed) vm.navigate(AppPage.OPERATIONS)
                        else if (AppPage.TRIP_FOLDER in allowed) vm.navigate(AppPage.TRIP_FOLDER)
                    }
                )
                BottomTab(
                    selected = currentTab == MainTab.FINANCE,
                    icon = Icons.Rounded.AccountBalanceWallet,
                    label = "Finance",
                    enabled = AppPage.FINANCE in allowed,
                    onClick = { vm.navigate(AppPage.FINANCE) }
                )
                BottomTab(
                    selected = currentTab == MainTab.MORE,
                    icon = Icons.Rounded.GridView,
                    label = "More",
                    onClick = { vm.navigate(AppPage.PROFILE) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (vm.currentPage) {
                AppPage.DASHBOARD -> DashboardScreen(vm, session)
                AppPage.BOOKINGS -> BookingScreen(vm, session, onNotice = { notice = it })
                AppPage.CUSTOMERS -> CustomerScreen(vm, session, onNotice = { notice = it })
                AppPage.FINANCE -> FinanceScreen(vm, session, onNotice = { notice = it })
                AppPage.OPERATIONS -> OperationsScreen(vm, session, onNotice = { notice = it })
                AppPage.VENDORS -> VendorsScreen(vm, session, onNotice = { notice = it })
                AppPage.TRIP_FOLDER -> TripFolderScreen(vm, session, onNotice = { notice = it })
                AppPage.WORKFLOW -> WorkflowScreen(vm, session, onNotice = { notice = it })
                AppPage.SOP -> SopScreen(vm)
                AppPage.REPORTS -> ReportsScreen(vm, session, onNotice = { notice = it })
                AppPage.CLOSING -> ClosingScreen(vm, session, onNotice = { notice = it })
                AppPage.TEAM_HR -> HrScreen(vm, session, onNotice = { notice = it })
                AppPage.USERS -> UsersScreen(vm, session, onNotice = { notice = it })
                AppPage.AUDIT -> AuditScreen(vm)
                AppPage.PROFILE -> MoreProfileScreen(vm, session)
            }

            notice?.let { msg ->
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
                    color = GmuDark,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 8.dp
                ) {
                    Text(msg, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 12.sp)
                }
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2400)
                    notice = null
                }
            }
        }
    }

    if (showNotifications) {
        NotificationDialog(vm = vm, onDismiss = { showNotifications = false })
    }
}

@Composable
private fun RowScope.BottomTab(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) }
    )
}

@Composable
private fun NotificationDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val pending = vm.table("approvals").filter { it.text("status") == "Pending" }
    val due = vm.table("sop_deadlines").filter { it.text("is_active") != "false" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification Center") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (pending.isEmpty()) {
                    Text("Tidak ada approval pending.", color = Color.Gray, fontSize = 12.sp)
                } else {
                    pending.take(6).forEach {
                        Text("• " + it.text("approval_type") + " — " + bookingLabel(vm, it.text("booking_id")), fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                }
                if (due.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("SOP aktif: " + due.size, color = GmuGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun MoreProfileScreen(vm: MainViewModel, session: SessionState) {
    val allowed = RoleAccess.pages(session.profile.role)
    val menus = listOf(
        AppPage.CUSTOMERS to "Customer",
        AppPage.VENDORS to "Vendor & PO",
        AppPage.TRIP_FOLDER to "Trip Folder & Documents",
        AppPage.WORKFLOW to "Workflow & Approval",
        AppPage.SOP to "SOP Deadline",
        AppPage.REPORTS to "Report & Evaluation",
        AppPage.CLOSING to "Trip Closing",
        AppPage.TEAM_HR to "Team & HR",
        AppPage.USERS to "User & Role",
        AppPage.AUDIT to "Audit Trail"
    ).filter { it.first in allowed }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        GmuGradientHeader {
            Text(session.profile.fullName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(session.profile.role + " • GMU EduTrans", color = Color(0xFFDDEBE4), fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        SectionTitle("More", "Semua modul sesuai hak akses " + session.profile.role)
        Spacer(Modifier.height(12.dp))

        menus.forEach { (page, label) ->
            Card(
                onClick = { vm.navigate(page) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = GmuDark)
                    Text("›", fontSize = 24.sp, color = GmuGold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = GmuSoft), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("GMU EduTrans ERP", fontWeight = FontWeight.Black, color = GmuDark)
                Text("Native All-in-One • " + BuildConfig.VERSION_NAME, fontSize = 12.sp, color = Color.Gray)
                if (session.profile.phone.isNotBlank()) Text(session.profile.phone, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("Keluar")
        }
        Spacer(Modifier.height(100.dp))
    }
}
