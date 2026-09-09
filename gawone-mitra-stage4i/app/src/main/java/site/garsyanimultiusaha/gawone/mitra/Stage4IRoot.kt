package site.garsyanimultiusaha.gawone.mitra

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal val LocalStage4IRuntime = staticCompositionLocalOf<Stage4IRuntimeConfig?> { null }

private sealed interface Stage4IRootState {
    data object Loading : Stage4IRootState
    data class Ready(val bootstrap: Stage4IBootstrap) : Stage4IRootState
    data class Error(val message: String) : Stage4IRootState
}

@Composable
internal fun Stage4IRoot(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runtimeClient = remember { Stage4IRuntimeClient(context.applicationContext) }
    val connectivity = remember { Stage4IConnectivityMonitor(context.applicationContext) }
    val online by connectivity.online.collectAsState()
    val crashNotice = remember { Stage4ICrashShield.consumeNotice(context.applicationContext) }

    var state by remember { mutableStateOf<Stage4IRootState>(Stage4IRootState.Loading) }

    suspend fun bootstrap() {
        state = Stage4IRootState.Loading
        state = runCatching { runtimeClient.recover() }
            .fold(
                onSuccess = { Stage4IRootState.Ready(it) },
                onFailure = {
                    Stage4IRootState.Error(
                        when (it) {
                            is java.io.IOException ->
                                "Jaringan belum tersedia dan belum ada konfigurasi offline yang aman."
                            is Stage4IRuntimeHttpException ->
                                "Backend GAWONE belum dapat memulai aplikasi (HTTP " +
                                    it.statusCode + ")."
                            else ->
                                "Aplikasi belum dapat dimulai dengan aman."
                        }
                    )
                }
            )
    }

    LaunchedEffect(Unit) { bootstrap() }

    LaunchedEffect(online) {
        if (online && state is Stage4IRootState.Error) {
            bootstrap()
        }
    }

    DisposableEffect(Unit) {
        onDispose { connectivity.close() }
    }

    when (val current = state) {
        Stage4IRootState.Loading -> {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Menyiapkan GAWONE Mitra…")
                    Text(
                        "Memulihkan sesi dan konfigurasi aman",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is Stage4IRootState.Error -> {
            Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Tidak dapat memulai", fontWeight = FontWeight.Bold)
                        Text(current.message)
                        Button(
                            onClick = { scope.launch { bootstrap() } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Coba Lagi")
                        }
                    }
                }
            }
        }

        is Stage4IRootState.Ready -> {
            LaunchedEffect(current.bootstrap.runtime.backendContractVersion) {
                Stage4IStartupTrace.markRuntimeReady()
            }
            val runtime = current.bootstrap.runtime
            when {
                runtime.maintenanceMode -> {
                    Stage4IBlockingScreen(
                        title = "Sedang Pemeliharaan",
                        body = runtime.maintenanceMessage,
                        onRetry = { scope.launch { bootstrap() } }
                    )
                }

                runtime.forceUpdate -> {
                    Stage4IBlockingScreen(
                        title = "Pembaruan Diperlukan",
                        body = "Versi aplikasi ini sudah tidak didukung. " +
                            "Minimum versionCode " + runtime.minSupportedVersionCode +
                            ". Paket release final disediakan pada Stage 4J.",
                        onRetry = { scope.launch { bootstrap() } }
                    )
                }

                else -> {
                    CompositionLocalProvider(LocalStage4IRuntime provides runtime) {
                        Column(Modifier.fillMaxSize()) {
                            if (!online || current.bootstrap.offlineDegraded) {
                                Surface(
                                    color = Color(0xFFFFF3CD),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Mode offline terbatas • perubahan akan disinkronkan saat jaringan kembali.",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (crashNotice != null) {
                                Surface(
                                    color = Color(0xFFE8F4EE),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Sesi aplikasi dipulihkan setelah error sebelumnya • ref " +
                                            crashNotice.fingerprint,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            Box(Modifier.weight(1f)) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stage4IBlockingScreen(
    title: String,
    body: String,
    onRetry: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(body)
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Periksa Lagi")
                }
            }
        }
    }
}

@Composable
internal fun Stage4IFeatureUnavailable(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(
                "Source sudah tersedia, tetapi feature flag production masih dikunci.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
