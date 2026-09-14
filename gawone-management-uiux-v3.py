from pathlib import Path

main = Path('app/src/main/java/site/garsyanimultiusaha/gawone/management/FullMainActivity.kt')
build = Path('app/build.gradle.kts')

s = main.read_text()
dash_marker = '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun DashboardScreen'
detail_marker = '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun DetailScreen'
dash_start = s.index(dash_marker)
detail_start = s.index(detail_marker, dash_start)

new_dash = '''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(ui: ManagementUi, onWorkspace: (String) -> Unit, onOpen: (ManagementRow) -> Unit, onLogout: () -> Unit) {
    val navWorkspaces = ui.workspaces.take(5)
    var query by remember { mutableStateOf("") }
    val filteredRows = remember(ui.rows, query) {
        if (query.isBlank()) ui.rows else ui.rows.filter { row ->
            listOf(row.title, row.subtitle, row.status, row.resource).any { it.contains(query, ignoreCase = true) }
        }
    }
    val attentionCount = remember(ui.rows) {
        ui.rows.count { row ->
            val st = row.status.uppercase()
            st.contains("PENDING") || st.contains("REVIEW") || st.contains("OPEN") || st.contains("FAILED") || st.contains("ESCALATED")
        }
    }
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
                    subtitle = "${ui.backendContract} • Live Operations",
                    trailing = { IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Keluar") } }
                )
            }
            item {
                Surface(
                    color = GawoneManagementTokens.PrimarySoft,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Live Operations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    ui.activeWorkspace.replace('_', ' ').ifBlank { "GAWONE Command Center" },
                                    color = GawoneManagementTokens.Muted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            GawoneManagementStatusPill("SYNC")
                        }
                        Text(
                            "Status Customer, Mitra, order, support, payment, payout, dan audit dibaca dari backend M2.22 yang sama.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GawoneManagementTokens.Ink
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ManagementMetricCard("Antrean", ui.rows.size.toString(), "workspace aktif", Modifier.weight(1f))
                    ManagementMetricCard("Perlu perhatian", attentionCount.toString(), "pending / review", Modifier.weight(1f))
                    ManagementMetricCard("Workspace", ui.workspaces.size.toString(), "role-based", Modifier.weight(1f))
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Hapus") } },
                    placeholder = { Text("Cari antrean, status, order, tiket…") }
                )
            }
            if (ui.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = GawoneManagementTokens.Primary) }
            if (!ui.error.isNullOrBlank()) item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(ui.error, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (!ui.loading && filteredRows.isEmpty()) item {
                GawoneManagementCard {
                    Icon(if (query.isBlank()) Icons.Default.CheckCircle else Icons.Default.SearchOff, null, tint = GawoneManagementTokens.Success)
                    Text(if (query.isBlank()) "Tidak ada antrean" else "Tidak ada hasil", fontWeight = FontWeight.ExtraBold)
                    Text(if (query.isBlank()) "Workspace ini sedang bersih." else "Coba kata kunci lain.", color = GawoneManagementTokens.Muted)
                }
            }
            items(filteredRows, key = { it.id }) { row ->
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
                            Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted, maxLines = 2)
                            if (row.status.isNotBlank()) {
                                Spacer(Modifier.height(5.dp))
                                GawoneManagementStatusPill(row.status)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = GawoneManagementTokens.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagementMetricCard(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, GawoneManagementTokens.Border)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = GawoneManagementTokens.Muted, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(caption, color = GawoneManagementTokens.Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

'''

new_detail = '''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(ui: ManagementUi, onBack: () -> Unit, onAction: (String) -> Unit) {
    var pending by remember { mutableStateOf<String?>(null) }
    var showRaw by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = GawoneManagementTokens.Canvas,
        topBar = {
            TopAppBar(
                title = { Column { Text(ui.selected?.title ?: "Detail", fontWeight = FontWeight.ExtraBold); Text(ui.selected?.resource.orEmpty(), style = MaterialTheme.typography.labelSmall, color = GawoneManagementTokens.Muted) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") } }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Surface(color = GawoneManagementTokens.PrimarySoft, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ui.selected?.subtitle.orEmpty(), fontWeight = FontWeight.ExtraBold)
                                Text("Resource ID • ${ui.selected?.id?.take(12).orEmpty()}", style = MaterialTheme.typography.labelSmall, color = GawoneManagementTokens.Muted)
                            }
                            if (!ui.selected?.status.isNullOrBlank()) GawoneManagementStatusPill(ui.selected!!.status)
                        }
                        Text("Aksi tetap diverifikasi oleh backend: role, capability, idempotency, dan audit trail.", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Ink)
                    }
                }
            }
            if (ui.loading || ui.runningAction) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = GawoneManagementTokens.Primary) }
            if (ui.actions.isNotEmpty()) {
                item { Text("Aksi tersedia", fontWeight = FontWeight.ExtraBold) }
                items(ui.actions, key = { it }) { action ->
                    Button(
                        onClick = { pending = action },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = !ui.runningAction,
                        shape = RoundedCornerShape(15.dp)
                    ) { Text(action.replace('_', ' '), fontWeight = FontWeight.Bold) }
                }
            } else if (!ui.loading) {
                item {
                    GawoneManagementCard {
                        Icon(Icons.Default.Lock, null, tint = GawoneManagementTokens.Muted)
                        Text("Tidak ada aksi untuk role ini", fontWeight = FontWeight.ExtraBold)
                        Text("Backend hanya mengembalikan aksi yang diizinkan untuk akun Management saat ini.", color = GawoneManagementTokens.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showRaw = !showRaw }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                    Icon(if (showRaw) Icons.Default.ExpandLess else Icons.Default.DataObject, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (showRaw) "Sembunyikan data backend" else "Lihat data backend")
                }
            }
            if (showRaw && ui.detail.isNotBlank()) item {
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GawoneManagementTokens.Border), modifier = Modifier.fillMaxWidth()) {
                    Text(ui.detail, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!ui.error.isNullOrBlank()) item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(ui.error, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
    pending?.let { action ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Konfirmasi aksi") },
            text = { Text("Jalankan ${action.replace('_', ' ')}? Backend M2.22 tetap memvalidasi izin dan mencatat audit.") },
            confirmButton = { TextButton(onClick = { pending = null; onAction(action) }) { Text("Jalankan") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Batal") } }
        )
    }
}
'''

s = s[:dash_start] + new_dash + new_detail
main.write_text(s)

b = build.read_text()
b = b.replace('versionCode = 5', 'versionCode = 6', 1)
b = b.replace('versionName = "1.0.3-uiux-v2"', 'versionName = "1.0.4-uiux-v3"', 1)
if 'versionCode = 6' not in b or '1.0.4-uiux-v3' not in b:
    raise SystemExit('Management UIUX V3 version bump failed')
build.write_text(b)
print('GAWONE Management UIUX V3 applied')
