package site.garsyanimultiusaha.gawone.mitra

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class Stage4JTab { HOME, ORDER, SCHEDULE, EARNINGS, ACCOUNT }

@Composable
internal fun Stage4JHomeShell(
    dashboard: Dashboard?,
    plan: KycPlan?,
    loading: Boolean,
    error: String?,
    message: String?,
    onRefresh: () -> Unit,
    onDocuments: () -> Unit,
    onLogout: () -> Unit
) {
    var tab by remember { mutableStateOf(Stage4JTab.HOME) }
    val runtime = LocalStage4IRuntime.current

    Scaffold(
        containerColor = Color(0xFFF7FAF8),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Stage4JTab.HOME,
                    onClick = { tab = Stage4JTab.HOME },
                    icon = { Icon(Icons.Outlined.Home, null) },
                    label = { Text("Beranda") }
                )
                NavigationBarItem(
                    selected = tab == Stage4JTab.ORDER,
                    onClick = { tab = Stage4JTab.ORDER },
                    icon = { Icon(Icons.Outlined.WorkOutline, null) },
                    label = { Text("Order") }
                )
                NavigationBarItem(
                    selected = tab == Stage4JTab.SCHEDULE,
                    onClick = { tab = Stage4JTab.SCHEDULE },
                    icon = { Icon(Icons.Outlined.CalendarMonth, null) },
                    label = { Text("Jadwal") }
                )
                NavigationBarItem(
                    selected = tab == Stage4JTab.EARNINGS,
                    onClick = { tab = Stage4JTab.EARNINGS },
                    icon = { Icon(Icons.Outlined.Payments, null) },
                    label = { Text("Pendapatan") }
                )
                NavigationBarItem(
                    selected = tab == Stage4JTab.ACCOUNT,
                    onClick = { tab = Stage4JTab.ACCOUNT },
                    icon = { Icon(Icons.Outlined.AccountCircle, null) },
                    label = { Text("Akun") }
                )
            }
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Stage4JTopBrand()
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            message?.let {
                Surface(
                    color = Color(0xFFE8F4EE),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(it, Modifier.padding(12.dp))
                }
            }

            when (tab) {
                Stage4JTab.HOME -> {
                    Text("Beranda Mitra", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Stage4JStatusSummary(dashboard, plan)
                    if (runtime?.enabled("PRESENCE") != false) {
                        Stage4CPresencePanel(plan?.serviceCode)
                    } else {
                        Stage4IFeatureUnavailable("Online & GPS")
                    }
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Refresh Status") }
                }

                Stage4JTab.ORDER -> {
                    Text("Order", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (runtime?.enabled("OFFER") == true && runtime.gates.matchingEnabled) {
                        Stage4DOfferPanel(plan?.serviceCode)
                    } else {
                        Stage4IFeatureUnavailable("Offer & Matching")
                    }
                    if (runtime?.enabled("JOB_EXECUTION") == true) {
                        Stage4EExecutionPanel()
                    } else {
                        Stage4IFeatureUnavailable("Lifecycle Pekerjaan")
                    }
                    if (runtime?.enabled("CHAT") != false) {
                        Stage4GCommunicationPanel()
                    }
                }

                Stage4JTab.SCHEDULE -> {
                    Text("Jadwal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Stage4JSchedulePanel()
                }

                Stage4JTab.EARNINGS -> {
                    Text("Pendapatan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (runtime?.enabled("WALLET_READ") != false) {
                        Stage4FWalletPanel()
                    } else {
                        Stage4IFeatureUnavailable("Pendapatan")
                    }
                }

                Stage4JTab.ACCOUNT -> {
                    Text("Akun", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (runtime?.enabled("ACCOUNT_CENTER") != false) {
                        Stage4HAccountPanel()
                    }
                    OutlinedButton(onClick = onDocuments, modifier = Modifier.fillMaxWidth()) {
                        Text("Kelola Dokumen KYC")
                    }
                    TextButton(
                        onClick = onLogout,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Keluar")
                    }
                }
            }
        }
    }
}

@Composable
private fun Stage4JTopBrand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = Color(0xFF0A6B47),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("G", color = Color.White, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("GAWONE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Mitra • v1.0 RC1", color = Color(0xFF0A6B47), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Stage4JStatusSummary(dashboard: Dashboard?, plan: KycPlan?) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Stage4JLine("Akun", dashboard?.accountStatus ?: "-")
            Stage4JLine("Onboarding", dashboard?.onboardingStatus ?: "-")
            Stage4JLine("Layanan", plan?.serviceName ?: plan?.serviceCode ?: "-")
            Stage4JLine("Ketersediaan", dashboard?.availabilityStatus ?: "-")
        }
    }
}

@Composable
private fun Stage4JLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Stage4JSchedulePanel() {
    val context = LocalContext.current
    val client = remember { Stage4HAccountClient(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var center by remember { mutableStateOf<Stage4HCenter?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        center = client.center()
    }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { refresh() }.onFailure { error = it.message ?: "Gagal memuat jadwal." }
        loading = false
    }

    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    val schedule = center?.schedule.orEmpty()
    if (!loading && schedule.isEmpty()) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Belum ada jadwal aktif.", Modifier.padding(16.dp))
        }
    }

    schedule.forEach { s ->
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.serviceName, fontWeight = FontWeight.Bold)
                    Text(s.status, color = Color(0xFF0A6B47), fontWeight = FontWeight.Bold)
                }
                Text(s.orderNo, style = MaterialTheme.typography.bodySmall)
                s.scheduledStart?.let { Text("Mulai: " + it, style = MaterialTheme.typography.bodySmall) }
                s.scheduledEnd?.let { Text("Selesai: " + it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    OutlinedButton(
        onClick = {
            scope.launch {
                loading = true
                error = null
                runCatching { refresh() }.onFailure { error = it.message ?: "Gagal refresh jadwal." }
                loading = false
            }
        },
        enabled = !loading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Refresh Jadwal")
    }
}
