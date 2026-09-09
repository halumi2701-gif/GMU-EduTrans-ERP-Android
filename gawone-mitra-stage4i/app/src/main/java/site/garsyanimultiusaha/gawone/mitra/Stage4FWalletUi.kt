package site.garsyanimultiusaha.gawone.mitra

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val WalletGreen = Color(0xFF0A6B47)
private val WalletSoft = Color(0xFFE8F4EE)

@Composable
internal fun Stage4FWalletPanel() {
    val context = LocalContext.current
    val client = remember { Stage4FWalletClient(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var wallet by remember { mutableStateOf<WalletSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var payoutAmount by remember { mutableStateOf("") }

    suspend fun refresh() { wallet = client.wallet() }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { refresh() }.onFailure { error = it.message ?: "Gagal memuat pendapatan." }
        loading = false
        while (isActive) {
            delay(10_000)
            runCatching { refresh() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Pendapatan", fontWeight = FontWeight.Bold)
                Text(
                    "Stage 4F • ledger server sebagai sumber kebenaran",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                enabled = !loading && !busy,
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        runCatching { refresh() }.onFailure { error = it.message ?: "Gagal refresh." }
                        loading = false
                    }
                }
            ) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh pendapatan") }
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer) }
        }
        message?.let {
            Surface(color = WalletSoft, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp))
            }
        }

        wallet?.let { w ->
            WalletBalanceGrid(w.balances)

            Surface(color = WalletSoft, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cara saldo bekerja", fontWeight = FontWeight.Bold)
                    Text(
                        "PENDING = pekerjaan selesai tetapi dana customer belum settle. AVAILABLE = sudah dapat diproses untuk payout. HELD = sedang dicairkan. Tip settle masuk 100% ke Mitra.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (w.isFrozen) {
                Text("Wallet sedang dibekukan.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }

            Text("Pendapatan terbaru", fontWeight = FontWeight.Bold)
            if (w.earnings.isEmpty()) {
                Text("Belum ada earning.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                w.earnings.take(6).forEach { EarningCard(it) }
            }

            HorizontalDivider()
            Text("Pencairan", fontWeight = FontWeight.Bold)

            if (!w.policy.enabled) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Payout production belum diaktifkan", fontWeight = FontWeight.Bold)
                        Text(
                            "Ledger tetap aktif. Pencairan baru dibuka setelah provider payout dan verifikasi rekening production siap.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                val verified = w.destinations.filter { it.status == "VERIFIED" }
                Text(
                    "Minimum " + idr(w.policy.minimumAmount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (verified.isEmpty()) {
                    Text("Belum ada rekening payout terverifikasi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val selected = verified.first()
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(selected.displayLabel, fontWeight = FontWeight.Bold)
                            selected.holderName?.let { Text(it) }
                            Text(selected.provider, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    OutlinedTextField(
                        value = payoutAmount,
                        onValueChange = { payoutAmount = it.filter(Char::isDigit).take(10) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nominal pencairan") },
                        prefix = { Text("Rp") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    val amount = payoutAmount.toLongOrNull() ?: 0L
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && !w.isFrozen &&
                            amount >= w.policy.minimumAmount && amount <= w.balances.available,
                        onClick = {
                            if (!busy) scope.launch {
                                busy = true
                                error = null
                                message = null
                                runCatching {
                                    val result = client.requestPayout(selected.destinationId, amount)
                                    message = "Payout " + idr(result.amount) + " masuk antrean: " + result.status
                                    payoutAmount = ""
                                    refresh()
                                }.onFailure { error = it.message ?: "Payout gagal." }
                                busy = false
                            }
                        }
                    ) { Text(if (busy) "Memproses..." else "Ajukan Pencairan") }
                }
            }

            if (w.payouts.isNotEmpty()) {
                Text("Riwayat payout", fontWeight = FontWeight.Bold)
                w.payouts.take(5).forEach { PayoutCard(it) }
            }
        }
    }
}

@Composable
private fun WalletBalanceGrid(b: WalletBalances) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BalanceCard("Tersedia", b.available, Modifier.weight(1f), true)
            BalanceCard("Tertunda", b.pending, Modifier.weight(1f), false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BalanceCard("Ditahan", b.held, Modifier.weight(1f), false)
            BalanceCard("Dicairkan", b.paidTotal, Modifier.weight(1f), false)
        }
    }
}

@Composable
private fun BalanceCard(label: String, amount: Long, modifier: Modifier, primary: Boolean) {
    Surface(
        color = if (primary) WalletSoft else Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                tint = if (primary) WalletGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(idr(amount), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EarningCard(e: WalletEarning) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(e.serviceName, fontWeight = FontWeight.Bold)
                    Text(e.orderNo, style = MaterialTheme.typography.bodySmall)
                }
                Text(e.status, fontWeight = FontWeight.Bold, color = if (e.status == "AVAILABLE") WalletGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WalletLine("Pendapatan dasar", idr(e.baseNetAmount))
            WalletLine("Komisi GAWONE", "-" + idr(e.commissionAmount))
            if (e.tipAmount > 0) WalletLine("Tip • 100% Mitra", idr(e.tipAmount))
            HorizontalDivider()
            WalletLine("Total Mitra", idr(e.totalNetAmount), true)
        }
    }
}

@Composable
private fun PayoutCard(p: WalletPayout) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(idr(p.amount), fontWeight = FontWeight.Bold)
                Text(p.status, fontWeight = FontWeight.Bold)
            }
            p.externalReference?.let { Text("Ref: " + it, style = MaterialTheme.typography.bodySmall) }
            p.rejectionReason?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            p.failureReason?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun WalletLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun idr(value: Long): String =
    NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")
