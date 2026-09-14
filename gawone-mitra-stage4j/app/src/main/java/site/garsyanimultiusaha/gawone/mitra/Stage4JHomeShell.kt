package site.garsyanimultiusaha.gawone.mitra

import androidx.compose.foundation.BorderStroke
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
        containerColor = GawoneMitraTokens.Canvas,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                MitraNavItem(tab == Stage4JTab.HOME, { tab = Stage4JTab.HOME }, Icons.Outlined.Home, "Beranda")
                MitraNavItem(tab == Stage4JTab.ORDER, { tab = Stage4JTab.ORDER }, Icons.Outlined.WorkOutline, "Order")
                MitraNavItem(tab == Stage4JTab.SCHEDULE, { tab = Stage4JTab.SCHEDULE }, Icons.Outlined.CalendarMonth, "Jadwal")
                MitraNavItem(tab == Stage4JTab.EARNINGS, { tab = Stage4JTab.EARNINGS }, Icons.Outlined.Payments, "Pendapatan")
                MitraNavItem(tab == Stage4JTab.ACCOUNT, { tab = Stage4JTab.ACCOUNT }, Icons.Outlined.AccountCircle, "Akun")
            }
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GawoneMitraBrandHeader("WORKS FOR A BETTER YOU")

            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = GawoneMitraTokens.Primary)
            error?.let { MitraMessageCard(it, error = true) }
            message?.let { MitraMessageCard(it, error = false) }

            when (tab) {
                Stage4JTab.HOME -> {
                    GawoneMitraSectionTitle("Beranda Mitra", "Status kerja, peluang order, dan kesiapan akun Anda")
                    Stage4JHero(dashboard, plan)
                    Stage4JStatusSummary(dashboard, plan)
                    if (runtime?.enabled("PRESENCE") != false) {
                        Stage4CPresencePanel(plan?.serviceCode)
                    } else {
                        Stage4IFeatureUnavailable("Online & GPS")
                    }
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Refresh Status", fontWeight = FontWeight.Bold) }
                }

                Stage4JTab.ORDER -> {
                    GawoneMitraSectionTitle("Order", "Terima pekerjaan, jalankan lifecycle, dan komunikasi dengan Customer")
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
                    GawoneMitraSectionTitle("Jadwal", "Pekerjaan aktif dan waktu layanan yang perlu Anda siapkan")
                    Stage4JSchedulePanel()
                }

                Stage4JTab.EARNINGS -> {
                    GawoneMitraSectionTitle("Pendapatan", "Ringkasan penghasilan, wallet, settlement, dan payout")
                    if (runtime?.enabled("WALLET_READ") != false) {
                        Stage4FWalletPanel()
                    } else {
                        Stage4IFeatureUnavailable("Pendapatan")
                    }
                }

                Stage4JTab.ACCOUNT -> {
                    GawoneMitraSectionTitle("Akun", "Profil, KYC, keamanan akun, dan preferensi Mitra")
                    if (runtime?.enabled("ACCOUNT_CENTER") != false) {
                        Stage4HAccountPanel()
                    }
                    OutlinedButton(
                        onClick = onDocuments,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Kelola Dokumen KYC", fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = onLogout,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Keluar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun MitraNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = GawoneMitraTokens.PrimaryStrong,
            selectedTextColor = GawoneMitraTokens.PrimaryStrong,
            indicatorColor = GawoneMitraTokens.PrimarySoft,
            unselectedIconColor = GawoneMitraTokens.Muted,
            unselectedTextColor = GawoneMitraTokens.Muted
        )
    )
}

@Composable
private fun Stage4JHero(dashboard: Dashboard?, plan: KycPlan?) {
    Surface(
        color = GawoneMitraTokens.PrimarySoft,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Siap menerima peluang?", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    Text(
                        plan?.serviceName ?: plan?.serviceCode ?: "Lengkapi layanan Anda",
                        color = GawoneMitraTokens.Muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                GawoneMitraStatusPill(
                    dashboard?.availabilityStatus ?: "OFFLINE",
                    positive = dashboard?.availabilityStatus.equals("ONLINE", true)
                )
            }
            HorizontalDivider(color = Color(0xFFDBEAFE))
            Text(
                "Order Customer, status pekerjaan, chat, pendapatan, dan payout memakai backend GAWONE yang sama dengan Management.",
                style = MaterialTheme.typography.bodySmall,
                color = GawoneMitraTokens.Ink
            )
        }
    }
}

@Composable
private fun Stage4JStatusSummary(dashboard: Dashboard?, plan: KycPlan?) {
    GawoneMitraCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Status akun", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            GawoneMitraStatusPill(dashboard?.accountStatus ?: "-")
        }
        HorizontalDivider(color = GawoneMitraTokens.Border)
        Stage4JLine("Onboarding", dashboard?.onboardingStatus ?: "-")
        Stage4JLine("Layanan", plan?.serviceName ?: plan?.serviceCode ?: "-")
        Stage4JLine("Ketersediaan", dashboard?.availabilityStatus ?: "-")
    }
}

@Composable
private fun Stage4JLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = GawoneMitraTokens.Muted)
        Text(value, fontWeight = FontWeight.Bold, color = GawoneMitraTokens.Ink)
    }
}

@Composable
private fun MitraMessageCard(text: String, error: Boolean) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else GawoneMitraTokens.PrimarySoft,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else GawoneMitraTokens.Ink,
            style = MaterialTheme.typography.bodySmall
        )
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

    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = GawoneMitraTokens.Primary)
    error?.let { MitraMessageCard(it, error = true) }

    val schedule = center?.schedule.orEmpty()
    if (!loading && schedule.isEmpty()) {
        GawoneMitraCard {
            Text("Belum ada jadwal aktif.", fontWeight = FontWeight.Bold)
            Text("Order yang sudah terjadwal akan muncul di sini.", color = GawoneMitraTokens.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }

    schedule.forEach { s ->
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, GawoneMitraTokens.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(s.serviceName, fontWeight = FontWeight.ExtraBold)
                    GawoneMitraStatusPill(s.status)
                }
                Text(s.orderNo, style = MaterialTheme.typography.bodySmall, color = GawoneMitraTokens.Muted)
                s.scheduledStart?.let { Text("Mulai: $it", style = MaterialTheme.typography.bodySmall) }
                s.scheduledEnd?.let { Text("Selesai: $it", style = MaterialTheme.typography.bodySmall) }
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
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Refresh Jadwal", fontWeight = FontWeight.Bold)
    }
}
