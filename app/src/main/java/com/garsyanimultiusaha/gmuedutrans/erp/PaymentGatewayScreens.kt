package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PaymentGatewayScreen(
    vm: MainViewModel,
    session: SessionState,
    onNotice: (String) -> Unit
) {
    if (!FinancialAccess.canView(session.profile.role)) {
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            EmptyCard("Payment Gateway hanya tersedia untuk Owner / Manager.")
        }
        return
    }

    val data = vm.paymentGateway

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle(
                "Payment Gateway",
                "QRIS • Virtual Account • E-Wallet • Auto Confirmation"
            )
            OutlinedButton(onClick = vm::refreshCommerce, enabled = !vm.actionBusy) {
                Text("Refresh")
            }
        }
        Spacer(Modifier.height(10.dp))

        if (data == null) {
            EmptyCard(vm.paymentGatewayError ?: "Payment Gateway belum termuat.")
            return
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (data.providerConfigured) Color(0xFFEAF7EF) else Color(0xFFFFF7E8)
            )
        ) {
            Column(Modifier.padding(15.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Midtrans", fontWeight = FontWeight.Black, color = GmuDark)
                    StatusChip(if (data.providerConfigured) data.providerEnvironment else "NOT CONFIGURED")
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    if (data.providerConfigured) {
                        "Merchant credential tersedia. Channel dapat diaktifkan satu per satu."
                    } else {
                        "Merchant credential belum tersedia. Semua channel live tetap terkunci."
                    },
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Pending", data.pending.toString(), Modifier.weight(1f))
            MetricCard("Paid", data.paid.toString(), Modifier.weight(1f), accent = data.paid > 0)
            MetricCard("Review", data.review.toString(), Modifier.weight(1f), accent = data.review > 0)
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Payment Channels",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = GmuDark
                )
            }

            items(data.channels, key = { it.code }) { ch ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ch.displayName, fontWeight = FontWeight.Black)
                                Text(
                                    ch.code + " • " + ch.methodType + " • " + ch.integrationMode,
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                            StatusChip(if (ch.enabled) "ACTIVE" else "OFF")
                        }
                        Spacer(Modifier.height(7.dp))
                        CommerceLine("Expiry", ch.expiryMinutes.toString() + " menit")
                        CommerceLine("Provider Ready", if (ch.providerReady) "YES" else "NO")
                        if (ch.requiresPhone) {
                            Text("Memerlukan nomor HP customer.", fontSize = 10.sp, color = GmuWarn)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                vm.setPaymentChannel(ch.code, !ch.enabled) { _, msg -> onNotice(msg) }
                            },
                            enabled = !vm.actionBusy && (ch.enabled || ch.providerReady),
                            colors = if (ch.enabled) {
                                ButtonDefaults.buttonColors(containerColor = GmuDanger)
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            Text(if (ch.enabled) "Disable" else "Enable")
                        }
                    }
                }
            }

            item {
                Text(
                    "Recent Payment Orders",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = GmuDark
                )
            }

            if (data.recentOrders.isEmpty()) {
                item {
                    EmptyCard(
                        "Belum ada payment order live. Order akan muncul setelah Customer Portal membuat pembayaran."
                    )
                }
            } else {
                items(data.recentOrders.take(50), key = { it.id }) { o ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(o.orderNo, fontWeight = FontWeight.Bold)
                                    Text(
                                        o.channelCode + " • " + o.paymentType,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                                StatusChip(o.status)
                            }
                            Spacer(Modifier.height(6.dp))
                            CommerceLine("Amount", rupiah(o.amount))
                            CommerceLine("Provider", o.providerStatus.ifBlank { "-" })
                            if (o.expiresAt.isNotBlank()) CommerceLine("Expires", o.expiresAt)
                            if (o.paidAt.isNotBlank()) CommerceLine("Paid", o.paidAt)
                        }
                    }
                }
            }
        }
    }
}

