package com.garsyanimultiusaha.gmuedutrans.sales

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class SalesE2EHealth(
    val quotationDecisionReady: Boolean = false,
    val paymentToWonReady: Boolean = false,
    val invoiceToHandoverReady: Boolean = false,
    val requestHandoverProgressReady: Boolean = false,
    val bookingStatusSyncReady: Boolean = false,
    val blockCommissionReady: Boolean = false,
    val onboardingReady: Boolean = false,
    val fieldOpsReady: Boolean = false,
    val healthy: Boolean = false
) {
    val readyCount: Int
        get() = listOf(
            quotationDecisionReady,
            paymentToWonReady,
            invoiceToHandoverReady,
            requestHandoverProgressReady,
            bookingStatusSyncReady,
            blockCommissionReady,
            onboardingReady,
            fieldOpsReady
        ).count { it }
}

private class SalesE2EHealthApi {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun check(session: SalesSession): SalesE2EHealth = withContext(Dispatchers.IO) {
        val connection = (URL(base.trimEnd('/') + "/rest/v1/rpc/gmu_sales_e2e_health").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = if (stream == null) "" else BufferedReader(InputStreamReader(stream)).use { it.readText() }
        connection.disconnect()
        if (code !in 200..299) {
            val message = runCatching {
                val obj = JSONObject(text)
                obj.optString("message").ifBlank { obj.optString("error") }
            }.getOrDefault("")
            throw IllegalStateException(message.ifBlank { "E2E health HTTP $code" })
        }
        val arr = JSONArray(text.ifBlank { "[]" })
        if (arr.length() == 0) throw IllegalStateException("E2E health belum tersedia")
        val x = arr.getJSONObject(0)
        SalesE2EHealth(
            quotationDecisionReady = x.optBoolean("quotation_decision_ready", false),
            paymentToWonReady = x.optBoolean("payment_to_won_ready", false),
            invoiceToHandoverReady = x.optBoolean("invoice_to_handover_ready", false),
            requestHandoverProgressReady = x.optBoolean("request_handover_progress_ready", false),
            bookingStatusSyncReady = x.optBoolean("booking_status_sync_ready", false),
            blockCommissionReady = x.optBoolean("block_commission_ready", false),
            onboardingReady = x.optBoolean("onboarding_ready", false),
            fieldOpsReady = x.optBoolean("field_ops_ready", false),
            healthy = x.optBoolean("healthy", false)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesAppV64(vm: SalesViewModel) {
    val api = remember { SalesE2EHealthApi() }
    var health by remember { mutableStateOf<SalesE2EHealth?>(null) }
    var healthError by remember { mutableStateOf<String?>(null) }
    var showCommercial by remember { mutableStateOf(false) }
    val loggedIn = vm.state as? SalesAppState.LoggedIn

    LaunchedEffect(loggedIn?.session?.accessToken) {
        val session = loggedIn?.session
        if (session == null) {
            health = null
            healthError = null
            showCommercial = false
        } else {
            runCatching { api.check(session) }
                .onSuccess {
                    health = it
                    healthError = null
                }
                .onFailure {
                    health = null
                    healthError = it.message ?: "Health check gagal"
                }
        }
    }

    Box(Modifier.fillMaxSize()) {
        SalesAppV63(vm)

        if (loggedIn != null) {
            ExtendedFloatingActionButton(
                onClick = { showCommercial = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 88.dp),
                containerColor = Color(0xFFD5A300),
                contentColor = Color(0xFF07580F),
                icon = { Icon(Icons.Default.PriceCheck, contentDescription = null) },
                text = { Text("PUBLIC / B2B", fontWeight = FontWeight.Black) }
            )

            when {
                healthError != null -> E2EStatusBanner(
                    title = "Koneksi E2E belum terverifikasi",
                    subtitle = healthError ?: "Health check gagal",
                    danger = true
                )
                health != null && health?.healthy == false -> E2EStatusBanner(
                    title = "E2E AUTO ${health?.readyCount ?: 0}/8",
                    subtitle = "Ada sambungan backend kritis yang belum aktif. Hindari closing baru sampai status kembali 8/8.",
                    danger = true
                )
            }
        }
    }

    if (showCommercial && loggedIn != null) {
        ModalBottomSheet(
            onDismissRequest = { showCommercial = false },
            containerColor = Color(0xFFF7F9F6)
        ) {
            SalesCommercialV65Sheet(
                session = loggedIn.session,
                onDismiss = { showCommercial = false },
                onCreated = {
                    vm.refresh()
                    showCommercial = false
                }
            )
        }
    }
}

@Composable
private fun BoxScope.E2EStatusBanner(title: String, subtitle: String, danger: Boolean) {
    Card(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (danger) Color(0xFFFFE9E7) else Color(0xFFEAF6E8)
        )
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                title,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                color = if (danger) Color(0xFFB3261E) else Color(0xFF07580F)
            )
            Text(
                subtitle,
                fontSize = 9.sp,
                color = Color.DarkGray
            )
        }
    }
}
