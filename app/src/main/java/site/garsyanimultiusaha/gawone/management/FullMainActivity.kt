package site.garsyanimultiusaha.gawone.management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val ManagementPurple = Color(0xFF6D28D9)
private val ManagementInk = Color(0xFF1F2937)
private val ManagementBg = Color(0xFFF7F7FB)

data class ManagementRow(
    val id: String,
    val resource: String,
    val title: String,
    val subtitle: String,
    val status: String,
    val raw: String
)

data class ManagementUi(
    val loading: Boolean = true,
    val loggedIn: Boolean = false,
    val backendContract: String = "M2.22",
    val workspaces: List<String> = emptyList(),
    val activeWorkspace: String = "",
    val rows: List<ManagementRow> = emptyList(),
    val selected: ManagementRow? = null,
    val detail: String = "",
    val actions: List<String> = emptyList(),
    val runningAction: Boolean = false,
    val error: String? = null
)

class FullMainActivity : ComponentActivity() {
    private val api = SupabaseManagementClient()
    private lateinit var secureStore: SecureTokenStore
    private val state = mutableStateOf(ManagementUi())
    private var polling = false
    private var cursor = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStore = SecureTokenStore(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = ManagementPurple,
                    onPrimary = Color.White,
                    background = ManagementBg,
                    surface = Color.White,
                    onSurface = ManagementInk
                )
            ) {
                Surface(Modifier.fillMaxSize()) {
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
        boot()
    }

    private fun boot() {
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                Pair(secureStore.accessToken(), secureStore.refreshToken())
            }
            if (saved.first.isNullOrBlank()) {
                state.value = ManagementUi(loading = false)
            } else {
                api.setSession(saved.first, saved.second)
                openSession(restoring = true)
            }
        }
    }

    private fun login(email: String, secret: String) {
        if (email.isBlank() || secret.isBlank()) return
        state.value = state.value.copy(loading = true, error = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.signIn(email.trim(), secret)
                    secureStore.save(api.accessToken(), api.refreshToken())
                }
            }.onSuccess {
                openSession(restoring = false)
            }.onFailure {
                state.value = ManagementUi(loading = false, error = friendly(it))
            }
        }
    }

    private suspend fun openSession(restoring: Boolean) {
        state.value = state.value.copy(loading = true, error = null)
        runCatching {
            withContext(Dispatchers.IO) {
                val runtime = api.runtime()
                val maintenance = runtime.optBoolean("maintenance_mode", runtime.optBoolean("maintenanceMode", false))
                if (maintenance) error(runtime.optString("maintenance_message", "Maintenance aktif"))
                val contract = runtime.optString("backend_contract_version", runtime.optString("backendContractVersion", BuildConfig.BACKEND_CONTRACT))
                if (contract != BuildConfig.BACKEND_CONTRACT) error("Backend contract berubah: $contract")
                api.manifest()
                api.bootstrap()
                val nav = api.navigation()
                secureStore.save(api.accessToken(), api.refreshToken())
                Pair(contract, parseWorkspaces(nav))
            }
        }.onSuccess { result ->
            val allowed = result.second
            if (allowed.isEmpty()) {
                state.value = ManagementUi(loading = false, loggedIn = true, backendContract = result.first, error = "Akun ini belum memiliki workspace Management.")
            } else {
                state.value = ManagementUi(loading = false, loggedIn = true, backendContract = result.first, workspaces = allowed, activeWorkspace = allowed.first())
                loadQueue(allowed.first())
                startPolling()
            }
        }.onFailure {
            if (restoring) {
                withContext(Dispatchers.IO) { secureStore.clear(); api.setSession(null, null) }
                state.value = ManagementUi(loading = false, error = "Sesi berakhir. Silakan masuk kembali.")
            } else {
                state.value = state.value.copy(loading = false, error = friendly(it))
            }
        }
    }

    private fun parseWorkspaces(nav: JSONObject): List<String> {
        val raw = nav.toString().uppercase()
        return listOf("PARTNER_REVIEW", "DISPATCH", "SUPPORT", "PAYOUT", "AUDIT").filter { raw.contains(it) }
    }

    private fun loadQueue(workspace: String) {
        if (workspace.isBlank()) return
        state.value = state.value.copy(loading = true, activeWorkspace = workspace, selected = null, detail = "", actions = emptyList(), error = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val result = api.queue(workspace, null, 50, 0)
                    secureStore.save(api.accessToken(), api.refreshToken())
                    parseRows(result, workspace)
                }
            }.onSuccess {
                state.value = state.value.copy(loading = false, rows = it)
            }.onFailure {
                state.value = state.value.copy(loading = false, error = friendly(it))
            }
        }
    }

    private fun parseRows(result: JSONObject, workspace: String): List<ManagementRow> {
        val arr = listOf("items", "data", "queue", "rows", "results").firstNotNullOfOrNull { key -> result.optJSONArray(key) } ?: JSONArray()
        return (0 until arr.length()).mapNotNull { index ->
            val item = arr.optJSONObject(index) ?: return@mapNotNull null
            val id = first(item, "resource_id", "id", "partner_id", "order_id", "ticket_id", "payout_id")
            if (id.isBlank()) return@mapNotNull null
            val resource = first(item, "resource", "resource_type", "detail_resource", "type")
            val title = first(item, "title", "name", "full_name", "ticket_no", "order_no", "status").ifBlank { "${resource.ifBlank { workspace }} • ${id.take(8)}" }
            val subtitle = first(item, "subtitle", "subject", "service_name", "reason", "priority").ifBlank { workspace.replace('_', ' ') }
            val status = first(item, "status", "state")
            ManagementRow(id, resource, title, subtitle, status, item.toString())
        }
    }

    private fun openRow(row: ManagementRow) {
        if (row.resource.isBlank()) {
            state.value = state.value.copy(selected = row, detail = row.raw, actions = emptyList(), error = "Resource detail tidak diberikan backend; aksi dinonaktifkan.")
            return
        }
        state.value = state.value.copy(selected = row, loading = true, detail = "", actions = emptyList(), error = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val detailObject = api.detail(row.resource, row.id)
                    secureStore.save(api.accessToken(), api.refreshToken())
                    Pair(detailObject.toString(2), SupabaseManagementClient.extractActions(detailObject))
                }
            }.onSuccess {
                state.value = state.value.copy(loading = false, detail = it.first, actions = it.second)
            }.onFailure {
                state.value = state.value.copy(loading = false, error = friendly(it))
            }
        }
    }

    private fun executeAction(action: String) {
        val row = state.value.selected ?: return
        state.value = state.value.copy(runningAction = true, error = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.action(action, row.id, JSONObject())
                    val detailObject = api.detail(row.resource, row.id)
                    secureStore.save(api.accessToken(), api.refreshToken())
                    Pair(detailObject.toString(2), SupabaseManagementClient.extractActions(detailObject))
                }
            }.onSuccess {
                state.value = state.value.copy(runningAction = false, detail = it.first, actions = it.second)
            }.onFailure {
                state.value = state.value.copy(runningAction = false, error = friendly(it))
            }
        }
    }

    private fun closeDetail() {
        state.value = state.value.copy(selected = null, detail = "", actions = emptyList(), error = null)
    }

    private fun logout() {
        polling = false
        lifecycleScope.launch(Dispatchers.IO) {
            secureStore.clear()
            api.setSession(null, null)
        }
        state.value = ManagementUi(loading = false)
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        lifecycleScope.launch {
            while (isActive && polling) {
                delay(8000)
                if (!state.value.loggedIn) continue
                runCatching {
                    withContext(Dispatchers.IO) { api.changes(cursor) }
                }.onSuccess { events ->
                    if (events.length() > 0) {
                        for (i in 0 until events.length()) cursor = maxOf(cursor, events.optJSONObject(i)?.optLong("event_id", cursor) ?: cursor)
                        if (state.value.selected == null) loadQueue(state.value.activeWorkspace) else state.value.selected?.let(::openRow)
                    }
                }
            }
        }
    }

    private fun first(o: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = o.opt(key)
            if (value != null && value != JSONObject.NULL && value !is JSONObject && value !is JSONArray) {
                val text = value.toString()
                if (text.isNotBlank()) return text
            }
        }
        return ""
    }

    private fun friendly(t: Throwable): String = (t.message ?: "Terjadi kesalahan.").take(500)
}

@Composable
private fun ManagementApp(
    ui: ManagementUi,
    onLogin: (String, String) -> Unit,
    onWorkspace: (String) -> Unit,
    onOpen: (ManagementRow) -> Unit,
    onBack: () -> Unit,
    onAction: (String) -> Unit,
    onLogout: () -> Unit
) {
    when {
        !ui.loggedIn -> LoginScreen(ui, onLogin)
        ui.selected != null -> DetailScreen(ui, onBack, onAction)
        else -> DashboardScreen(ui, onWorkspace, onOpen, onLogout)
    }
}

@Composable
private fun LoginScreen(ui: ManagementUi, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("gawone", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = ManagementInk)
        Text("Management", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = ManagementPurple)
        Text("WORKS FOR A BETTER YOU", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Spacer(Modifier.height(30.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email internal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(secret, { secret = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        if (!ui.error.isNullOrBlank()) { Spacer(Modifier.height(12.dp)); Text(ui.error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(18.dp))
        Button(onClick = { onLogin(email, secret) }, modifier = Modifier.fillMaxWidth(), enabled = !ui.loading && email.isNotBlank() && secret.isNotBlank()) {
            if (ui.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Masuk")
        }
        Spacer(Modifier.height(14.dp))
        Text("Backend ${BuildConfig.BACKEND_CONTRACT}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(ui: ManagementUi, onWorkspace: (String) -> Unit, onOpen: (ManagementRow) -> Unit, onLogout: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text("GAWONE Management", fontWeight = FontWeight.Bold); Text("${ui.backendContract} • Production", style = MaterialTheme.typography.labelSmall) } },
            actions = { IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Keluar") } }
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Command Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.workspaces.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { workspace -> FilterChip(selected = ui.activeWorkspace == workspace, onClick = { onWorkspace(workspace) }, label = { Text(workspace.replace('_', ' ')) }) }
                        }
                    }
                }
            }
            if (ui.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!ui.error.isNullOrBlank()) item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(ui.error, Modifier.padding(14.dp)) } }
            if (!ui.loading && ui.rows.isEmpty()) item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(20.dp)) { Icon(Icons.Default.CheckCircle, null, tint = ManagementPurple); Spacer(Modifier.height(8.dp)); Text("Tidak ada antrean", fontWeight = FontWeight.SemiBold); Text("Workspace ini sedang bersih.", color = Color.Gray) }
                }
            }
            items(ui.rows, key = { it.id }) { row ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(row) }, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.Inbox, null, tint = ManagementPurple) }
                        Column(Modifier.weight(1f)) { Text(row.title, fontWeight = FontWeight.SemiBold); Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray); if (row.status.isNotBlank()) Text(row.status, style = MaterialTheme.typography.labelSmall, color = ManagementPurple) }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(ui: ManagementUi, onBack: () -> Unit, onAction: (String) -> Unit) {
    var pending by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text(ui.selected?.title ?: "Detail") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(ui.selected?.subtitle.orEmpty(), fontWeight = FontWeight.Medium); if (!ui.selected?.status.isNullOrBlank()) Text(ui.selected!!.status, color = ManagementPurple) } } }
            if (ui.loading || ui.runningAction) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (ui.actions.isNotEmpty()) {
                item { Text("Aksi tersedia", fontWeight = FontWeight.Bold) }
                items(ui.actions, key = { it }) { action -> OutlinedButton(onClick = { pending = action }, modifier = Modifier.fillMaxWidth(), enabled = !ui.runningAction) { Text(action.replace('_', ' ')) } }
            }
            if (ui.detail.isNotBlank()) item { Text("Data backend", fontWeight = FontWeight.Bold); Text(ui.detail, style = MaterialTheme.typography.bodySmall) }
            if (!ui.error.isNullOrBlank()) item { Text(ui.error, color = MaterialTheme.colorScheme.error) }
        }
    }
    pending?.let { action ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Konfirmasi aksi") },
            text = { Text("Jalankan ${action.replace('_', ' ')}? Backend tetap memvalidasi role, capability, idempotency, dan audit.") },
            confirmButton = { TextButton(onClick = { pending = null; onAction(action) }) { Text("Jalankan") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Batal") } }
        )
    }
}
