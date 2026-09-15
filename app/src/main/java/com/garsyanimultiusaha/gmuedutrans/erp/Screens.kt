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
    GmuV20Theme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(GmuSpacing.xl)) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_gmu),
                    contentDescription = "GMU EduTrans",
                    modifier = Modifier.size(152.dp).padding(GmuSpacing.lg),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(GmuSpacing.xl))
            Text("GMU EduTrans ERP", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text("More Than a Trip, It’s a Learning Journey.", color = Color(0xFFDDEBE4), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(42.dp))
            Text("ERP v${BuildConfig.VERSION_NAME} • PT Garsyani Multi Usaha", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = GmuGreen)
            Spacer(Modifier.height(GmuSpacing.sm))
            Text("GMU EduTrans ERP", style = MaterialTheme.typography.titleMedium, color = GmuDark)
            Text("Sinkronisasi data…", style = MaterialTheme.typography.bodyMedium, color = GmuMuted)
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(GmuSpacing.xl),
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
            Spacer(Modifier.width(GmuSpacing.sm))
            Column {
                Text("Welcome Back", color = GmuDark, style = MaterialTheme.typography.headlineMedium)
                Text("GMU EduTrans ERP v${BuildConfig.VERSION_NAME}", color = GmuGreen, style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(GmuSpacing.xl))
        Card(
            shape = RoundedCornerShape(GmuRadii.hero),
            border = androidx.compose.foundation.BorderStroke(1.dp, GmuLine),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(GmuSpacing.lg)) {
                Text("Sign in", style = MaterialTheme.typography.titleLarge)
                Text("Kelola booking, trip, keuangan, tim, dan kontrol perusahaan dalam satu workspace.", style = MaterialTheme.typography.bodyMedium, color = GmuMuted)
                Spacer(Modifier.height(GmuSpacing.md))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(GmuRadii.field)
                )
                Spacer(Modifier.height(GmuSpacing.xs))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(GmuRadii.field)
                )
                AnimatedVisibility(visible = !error.isNullOrBlank()) {
                    Text(error.orEmpty(), color = GmuDanger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = GmuSpacing.xs))
                }
                Spacer(Modifier.height(GmuSpacing.md))
                Button(
                    onClick = {
                        onClearError?.invoke()
                        onLogin(email, password)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !busy && email.isNotBlank() && password.length >= 8,
                    shape = RoundedCornerShape(GmuRadii.field)
                ) {
                    Text(if (busy) "Memproses…" else "Masuk ke ERP", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(GmuSpacing.md))
        Text("Native Android • Role-based access • Supabase secured", style = MaterialTheme.typography.labelMedium, color = GmuMuted)
    }
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
    val currentTab = ErpNavigation.mainTabFor(vm.currentPage)
    val currentDestination = ErpNavigation.destination(vm.currentPage)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(currentDestination.label, style = MaterialTheme.typography.titleLarge, color = GmuDark)
                        Text(
                            "${session.profile.role} • ERP v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelMedium,
                            color = GmuMuted
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
                        Icon(Icons.Rounded.AccountCircle, contentDescription = "Workspace", tint = GmuDark)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 3.dp) {
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
                    label = "Workspace",
                    onClick = { vm.navigate(AppPage.PROFILE) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (vm.currentPage) {
                AppPage.DASHBOARD -> DashboardScreen(vm, session)
                AppPage.BOOKINGS -> BookingScreen(vm, session, onNotice = { notice = it })
                AppPage.BOOKING_REQUESTS -> BookingRequestScreen(vm, session, onNotice = { notice = it })
                AppPage.QUOTATIONS -> QuotationPricingScreen(vm, session, onNotice = { notice = it })
                AppPage.PACKAGE_MASTER -> PackageMasterHubScreen(vm, session, onNotice = { notice = it })
                AppPage.PRICING_MASTER -> PricingMasterScreen(vm, session, onNotice = { notice = it })
                AppPage.PAYMENT_GATEWAY -> PaymentGatewayScreen(vm, session, onNotice = { notice = it })
                AppPage.CUSTOMERS -> CustomerScreen(vm, session, onNotice = { notice = it })
                AppPage.FINANCE -> FinanceScreen(vm, session, onNotice = { notice = it })
                AppPage.PLANNING -> PlanningScreen(vm, session, onNotice = { notice = it })
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
                    modifier = Modifier.align(Alignment.BottomCenter).padding(GmuSpacing.md),
                    color = GmuDark,
                    shape = RoundedCornerShape(GmuRadii.field),
                    shadowElevation = 8.dp
                ) {
                    Text(msg, color = Color.White, modifier = Modifier.padding(horizontal = GmuSpacing.md, vertical = GmuSpacing.sm), style = MaterialTheme.typography.bodyMedium)
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
        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
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
                    Text("Tidak ada approval pending.", color = GmuMuted, style = MaterialTheme.typography.bodyMedium)
                } else {
                    pending.take(6).forEach {
                        Text("• " + it.text("approval_type") + " — " + bookingLabel(vm, it.text("booking_id")), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(GmuSpacing.xs))
                    }
                }
                if (due.isNotEmpty()) {
                    Spacer(Modifier.height(GmuSpacing.sm))
                    Text("SOP aktif: " + due.size, color = GmuGreen, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

private fun workspaceIcon(page: AppPage): ImageVector = when (page) {
    AppPage.BOOKING_REQUESTS -> Icons.Rounded.Inbox
    AppPage.QUOTATIONS -> Icons.Rounded.RequestQuote
    AppPage.PACKAGE_MASTER -> Icons.Rounded.Inventory2
    AppPage.PRICING_MASTER -> Icons.Rounded.PriceCheck
    AppPage.PAYMENT_GATEWAY -> Icons.Rounded.Payments
    AppPage.CUSTOMERS -> Icons.Rounded.Groups
    AppPage.PLANNING, AppPage.REPORTS -> Icons.Rounded.Assessment
    AppPage.VENDORS -> Icons.Rounded.Storefront
    AppPage.TRIP_FOLDER -> Icons.Rounded.Folder
    AppPage.WORKFLOW -> Icons.Rounded.Approval
    AppPage.SOP -> Icons.Rounded.Schedule
    AppPage.CLOSING -> Icons.Rounded.TaskAlt
    AppPage.TEAM_HR -> Icons.Rounded.Badge
    AppPage.USERS -> Icons.Rounded.AdminPanelSettings
    AppPage.AUDIT -> Icons.Rounded.History
    AppPage.OPERATIONS -> Icons.Rounded.Luggage
    AppPage.BOOKINGS -> Icons.Rounded.EventNote
    AppPage.FINANCE -> Icons.Rounded.AccountBalanceWallet
    else -> Icons.Rounded.GridView
}

@Composable
fun MoreProfileScreen(vm: MainViewModel, session: SessionState) {
    val groups = remember(session.profile.role) { ErpNavigation.visibleGroups(session.profile.role) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GmuSpacing.md, vertical = GmuSpacing.sm)
    ) {
        Card(
            shape = RoundedCornerShape(GmuRadii.hero),
            colors = CardDefaults.cardColors(containerColor = GmuDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(GmuSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .12f)) {
                    Icon(
                        Icons.Rounded.AccountCircle,
                        contentDescription = "Profile",
                        tint = Color.White,
                        modifier = Modifier.size(60.dp).padding(10.dp)
                    )
                }
                Spacer(Modifier.width(GmuSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(session.profile.fullName, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(session.profile.role + " • GMU EduTrans", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(2.dp))
                    Text("ERP v${BuildConfig.VERSION_NAME}", color = GmuGold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(Modifier.height(GmuSpacing.lg))
        SectionTitle("Workspace", "Modul dikelompokkan berdasarkan proses kerja dan hak akses Anda.")
        Spacer(Modifier.height(GmuSpacing.md))

        WorkspaceGroup.values().forEach { group ->
            val items = groups[group].orEmpty()
            if (items.isNotEmpty()) {
                Text(
                    group.label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = GmuMuted,
                    modifier = Modifier.padding(start = 2.dp, bottom = GmuSpacing.xs, top = GmuSpacing.xs)
                )
                items.forEach { destination ->
                    Card(
                        onClick = { vm.navigate(destination.page) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = GmuSpacing.xs),
                        shape = RoundedCornerShape(GmuRadii.card),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GmuLine),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(GmuSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = RoundedCornerShape(14.dp), color = GmuSoft) {
                                Icon(
                                    workspaceIcon(destination.page),
                                    contentDescription = destination.label,
                                    tint = GmuGreen,
                                    modifier = Modifier.size(42.dp).padding(9.dp)
                                )
                            }
                            Spacer(Modifier.width(GmuSpacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(destination.label, style = MaterialTheme.typography.titleMedium, color = GmuDark)
                                Text(destination.description, style = MaterialTheme.typography.bodyMedium, color = GmuMuted)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = GmuMuted)
                        }
                    }
                }
                Spacer(Modifier.height(GmuSpacing.xs))
            }
        }

        if (FinancialAccess.canView(session.profile.role)) {
            GmuSectionCard(accent = true) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
                        Icon(
                            Icons.Rounded.AccountBalanceWallet,
                            contentDescription = "Financial Health",
                            tint = GmuDark,
                            modifier = Modifier.size(44.dp).padding(10.dp)
                        )
                    }
                    Spacer(Modifier.width(GmuSpacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text("Financial Health", style = MaterialTheme.typography.titleMedium, color = GmuDark)
                        Text("Omzet, piutang, biaya, margin, profit dan posisi kas.", style = MaterialTheme.typography.bodyMedium, color = GmuMuted)
                    }
                    IconButton(onClick = { vm.navigate(AppPage.FINANCE) }) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Buka Finance", tint = GmuDark)
                    }
                }
            }
            Spacer(Modifier.height(GmuSpacing.md))
        }

        OutlinedButton(
            onClick = vm::logout,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(GmuRadii.field)
        ) {
            Icon(Icons.Rounded.Logout, contentDescription = null)
            Spacer(Modifier.width(GmuSpacing.xs))
            Text("Keluar")
        }
        Spacer(Modifier.height(100.dp))
    }
}
