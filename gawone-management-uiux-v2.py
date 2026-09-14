from pathlib import Path

main = Path('app/src/main/java/site/garsyanimultiusaha/gawone/management/FullMainActivity.kt')
build = Path('app/build.gradle.kts')

s = main.read_text()
s = s.replace('private val ManagementPurple = Color(0xFF6D28D9)', 'private val ManagementPurple = Color(0xFF8B5CF6)')
s = s.replace('private val ManagementBg = Color(0xFFF7F7FB)', 'private val ManagementBg = Color(0xFFF8FAFC)')

start = s.index('        setContent {')
end = s.index('        boot()', start)
new_root = '''        setContent {
            GawoneManagementTheme {
                Surface(Modifier.fillMaxSize(), color = GawoneManagementTokens.Canvas) {
                    ManagementApp(
                        ui = state.value,
                        onLogin = ::login,
                        onWorkspace = ::loadQueue,
                        onOpen = ::openRow,
                        onBack = ::closeDetail,
                        onAction = ::executeAction,
                        onLogout = ::logout
                    )
                }
            }
        }
'''
s = s[:start] + new_root + s[end:]

login_start = s.index('@Composable\nprivate fun LoginScreen')
dash_marker = '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun DashboardScreen'
login_end = s.index(dash_marker, login_start)
new_login = '''@Composable
private fun LoginScreen(ui: ManagementUi, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        GawoneManagementBrandHeader()
        Spacer(Modifier.height(32.dp))
        Text("Masuk ke Command Center", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text("Gunakan akun internal GAWONE Management.", color = GawoneManagementTokens.Muted)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email Management") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(secret, { secret = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        if (!ui.error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(ui.error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = { onLogin(email, secret) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), enabled = !ui.loading && email.isNotBlank() && secret.isNotBlank()) {
            if (ui.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("Masuk Management", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            GawoneManagementStatusPill("Backend ${BuildConfig.BACKEND_CONTRACT} • Role-based")
        }
    }
}

'''
s = s[:login_start] + new_login + s[login_end:]

# Replace dashboard shell while preserving queue/data behavior.
dash_start = s.index(dash_marker)
detail_marker = '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun DetailScreen'
dash_end = s.index(detail_marker, dash_start)
new_dash = '''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(ui: ManagementUi, onWorkspace: (String) -> Unit, onOpen: (ManagementRow) -> Unit, onLogout: () -> Unit) {
    val navWorkspaces = ui.workspaces.take(5)
    Scaffold(
        containerColor = GawoneManagementTokens.Canvas,
        bottomBar = {
            if (navWorkspaces.isNotEmpty()) {
                NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                    navWorkspaces.forEach { workspace ->
                        val icon = when (workspace) {
                            "PARTNER_REVIEW" -> Icons.Default.Groups
                            "DISPATCH" -> Icons.Default.LocalShipping
                            "SUPPORT" -> Icons.Default.SupportAgent
                            "PAYOUT" -> Icons.Default.AccountBalanceWallet
                            else -> Icons.Default.History
                        }
                        val label = when (workspace) {
                            "PARTNER_REVIEW" -> "Mitra"
                            "DISPATCH" -> "Order"
                            "SUPPORT" -> "Support"
                            "PAYOUT" -> "Keuangan"
                            else -> "Audit"
                        }
                        NavigationBarItem(
                            selected = ui.activeWorkspace == workspace,
                            onClick = { onWorkspace(workspace) },
                            icon = { Icon(icon, label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GawoneManagementTokens.PrimaryStrong,
                                selectedTextColor = GawoneManagementTokens.PrimaryStrong,
                                indicatorColor = GawoneManagementTokens.PrimarySoft,
                                unselectedIconColor = GawoneManagementTokens.Muted,
                                unselectedTextColor = GawoneManagementTokens.Muted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GawoneManagementBrandHeader(
                    subtitle = "${ui.backendContract} • Production",
                    trailing = { IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Keluar") } }
                )
            }
            item {
                Surface(
                    color = GawoneManagementTokens.PrimarySoft,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Command Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    ui.activeWorkspace.replace('_', ' ').ifBlank { "Operasional GAWONE" },
                                    color = GawoneManagementTokens.Muted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            GawoneManagementStatusPill("Realtime")
                        }
                        Text(
                            "Customer, Mitra, order, pembayaran, payout, support, dan audit menggunakan backend GAWONE yang sama.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GawoneManagementTokens.Ink
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Antrean", color = GawoneManagementTokens.Muted, style = MaterialTheme.typography.labelMedium)
                            Text(ui.rows.size.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Workspace", color = GawoneManagementTokens.Muted, style = MaterialTheme.typography.labelMedium)
                            Text(ui.workspaces.size.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
            if (ui.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = GawoneManagementTokens.Primary) }
            if (!ui.error.isNullOrBlank()) item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(ui.error, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (!ui.loading && ui.rows.isEmpty()) item {
                GawoneManagementCard {
                    Icon(Icons.Default.CheckCircle, null, tint = GawoneManagementTokens.Success)
                    Text("Tidak ada antrean", fontWeight = FontWeight.ExtraBold)
                    Text("Workspace ini sedang bersih.", color = GawoneManagementTokens.Muted)
                }
            }
            items(ui.rows, key = { it.id }) { row ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(row) },
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GawoneManagementTokens.Border)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = GawoneManagementTokens.PrimarySoft, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Inbox, null, tint = GawoneManagementTokens.PrimaryStrong) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.title, fontWeight = FontWeight.ExtraBold)
                            Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                            if (row.status.isNotBlank()) Text(row.status, style = MaterialTheme.typography.labelSmall, color = GawoneManagementTokens.PrimaryStrong, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = GawoneManagementTokens.Muted)
                    }
                }
            }
        }
    }
}

'''
s = s[:dash_start] + new_dash + s[dash_end:]
main.write_text(s)

b = build.read_text()
b = b.replace('versionCode = 4', 'versionCode = 5', 1)
b = b.replace('versionName = "1.0.2-m2.22-email-primary"', 'versionName = "1.0.3-uiux-v2"', 1)
if 'versionCode = 5' not in b or '1.0.3-uiux-v2' not in b:
    raise SystemExit('Management UIUX V2 version bump failed')
build.write_text(b)

print('GAWONE Management UIUX V2 applied')
