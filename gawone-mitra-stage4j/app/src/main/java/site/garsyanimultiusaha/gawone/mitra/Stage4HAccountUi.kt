package site.garsyanimultiusaha.gawone.mitra

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

private val AccountGreen = Color(0xFF0A6B47)
private val AccountSoft = Color(0xFFE8F4EE)

@Composable
internal fun Stage4HAccountPanel() {
    val context = LocalContext.current
    val client = remember { Stage4HAccountClient(context.applicationContext) }
    val realtime = remember { Stage4GRealtimeSocket(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var center by remember { mutableStateOf<Stage4HCenter?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    var appealTarget by remember { mutableStateOf<Stage4HRestriction?>(null) }
    var supportDialog by remember { mutableStateOf(false) }
    var supportTarget by remember { mutableStateOf<Stage4HSupportTicket?>(null) }
    var supportDetail by remember { mutableStateOf<Stage4HSupportDetail?>(null) }
    var accountRequestType by remember { mutableStateOf<String?>(null) }

    val deepLinkTarget by Stage4GDeepLinkRouter.target.collectAsState()

    suspend fun refresh() {
        center = client.center()
    }

    DisposableEffect(Unit) {
        realtime.start()
        onDispose { realtime.stop() }
    }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { refresh() }
            .onFailure { error = it.message ?: "Gagal memuat akun Mitra." }
        loading = false

        launch {
            realtime.events.collect { event ->
                if (
                    event.table in setOf(
                        "partner_ratings",
                        "partner_account_restrictions",
                        "partner_appeals",
                        "partner_support_tickets",
                        "partner_support_messages",
                        "partner_account_requests",
                        "order_assignments",
                        "partner_documents",
                        "partner_services",
                        "partner_vehicles"
                    )
                ) {
                    runCatching { refresh() }
                }
            }
        }

        launch {
            while (isActive) {
                delay(30_000)
                runCatching { refresh() }
            }
        }
    }

    LaunchedEffect(deepLinkTarget) {
        if (deepLinkTarget is Stage4GDeepLinkTarget.Account) {
            info = "Pusat akun dibuka dari notifikasi."
            Stage4GDeepLinkRouter.consume(Stage4GDeepLinkTarget.Account)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Akun & Performa", fontWeight = FontWeight.Bold)
                Text(
                    "Stage 4H • performa, jadwal, bantuan, banding, privacy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                enabled = !loading,
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        runCatching { refresh() }
                            .onFailure { error = it.message ?: "Gagal refresh akun." }
                        loading = false
                    }
                }
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh akun")
            }
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        info?.let {
            Surface(
                color = AccountSoft,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(it, Modifier.padding(12.dp))
            }
        }

        center?.let { c ->
            AccountStatusCard(c.account)
            PerformanceCards(c.performance)

            Text("Rating terbaru", fontWeight = FontWeight.Bold)
            if (c.ratings.isEmpty()) {
                Text("Belum ada rating.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                c.ratings.take(4).forEach { RatingCard(it) }
            }

            HorizontalDivider()
            Text("Jadwal", fontWeight = FontWeight.Bold)
            if (c.schedule.isEmpty()) {
                Text("Belum ada jadwal aktif.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                c.schedule.take(6).forEach { ScheduleCard(it) }
            }

            HorizontalDivider()
            Text("Layanan", fontWeight = FontWeight.Bold)
            c.services.forEach { ServiceCard(it) }
            if (c.services.isEmpty()) {
                Text("Belum ada layanan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (c.vehicles.isNotEmpty()) {
                Text("Kendaraan", fontWeight = FontWeight.Bold)
                c.vehicles.forEach { VehicleCard(it) }
            }

            Text("Dokumen", fontWeight = FontWeight.Bold)
            if (c.documents.isEmpty()) {
                Text("Belum ada dokumen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                c.documents.forEach { DocumentCard(it) }
            }

            HorizontalDivider()
            Text("Pembatasan & Banding", fontWeight = FontWeight.Bold)
            val activeRestrictions = c.restrictions.filter { it.status == "ACTIVE" }
            if (activeRestrictions.isEmpty()) {
                Text(
                    "Tidak ada pembatasan aktif.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                activeRestrictions.forEach { restriction ->
                    RestrictionCard(
                        restriction = restriction,
                        hasOpenAppeal = c.appeals.any {
                            it.restrictionId == restriction.restrictionId &&
                                it.status in setOf("SUBMITTED", "UNDER_REVIEW")
                        },
                        onAppeal = { appealTarget = restriction }
                    )
                }
            }

            if (c.appeals.isNotEmpty()) {
                Text("Riwayat banding", fontWeight = FontWeight.Bold)
                c.appeals.take(5).forEach { AppealCard(it) }
            }

            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bantuan", fontWeight = FontWeight.Bold)
                TextButton(onClick = { supportDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Buat Tiket")
                }
            }

            if (c.supportTickets.isEmpty()) {
                Text(
                    "Belum ada tiket bantuan.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                c.supportTickets.take(6).forEach { ticket ->
                    SupportTicketCard(ticket) {
                        supportTarget = ticket
                        scope.launch {
                            runCatching { client.supportDetail(ticket.ticketId) }
                                .onSuccess { supportDetail = it }
                                .onFailure { error = it.message ?: "Gagal membuka tiket." }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Privasi & Kontrol Akun", fontWeight = FontWeight.Bold)
            Text(
                "Permintaan ekspor/deaktivasi/penghapusan diproses melalui review backend. Aplikasi tidak menghapus riwayat transaksi secara langsung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { accountRequestType = "DATA_EXPORT" },
                    modifier = Modifier.weight(1f)
                ) { Text("Ekspor Data") }
                OutlinedButton(
                    onClick = { accountRequestType = "ACCOUNT_DEACTIVATION" },
                    modifier = Modifier.weight(1f)
                ) { Text("Deaktivasi") }
            }

            OutlinedButton(
                onClick = { accountRequestType = "ACCOUNT_DELETION" },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ajukan Penghapusan Akun")
            }

            if (c.accountRequests.isNotEmpty()) {
                Text("Permintaan akun", fontWeight = FontWeight.Bold)
                c.accountRequests.take(5).forEach { req ->
                    AccountRequestCard(
                        request = req,
                        onCancel = {
                            scope.launch {
                                runCatching { client.cancelAccountRequest(req.requestId) }
                                    .onSuccess {
                                        info = "Permintaan dibatalkan."
                                        refresh()
                                    }
                                    .onFailure { error = it.message ?: "Gagal membatalkan." }
                            }
                        }
                    )
                }
            }
        }
    }

    appealTarget?.let { restriction ->
        AppealDialog(
            restriction = restriction,
            onDismiss = { appealTarget = null },
            onSubmit = { statement ->
                scope.launch {
                    error = null
                    runCatching {
                        client.submitAppeal(restriction.restrictionId, statement)
                    }.onSuccess {
                        info = "Banding berhasil dikirim."
                        appealTarget = null
                        refresh()
                    }.onFailure {
                        error = it.message ?: "Gagal mengirim banding."
                    }
                }
            }
        )
    }

    if (supportDialog) {
        NewSupportDialog(
            onDismiss = { supportDialog = false },
            onSubmit = { category, subject, message ->
                scope.launch {
                    error = null
                    runCatching {
                        client.createSupportTicket(category, subject, message)
                    }.onSuccess { ticketNo ->
                        info = "Tiket " + ticketNo + " berhasil dibuat."
                        supportDialog = false
                        refresh()
                    }.onFailure {
                        error = it.message ?: "Gagal membuat tiket."
                    }
                }
            }
        )
    }

    supportDetail?.let { detail ->
        SupportDetailDialog(
            detail = detail,
            onDismiss = {
                supportDetail = null
                supportTarget = null
            },
            onReply = { body ->
                scope.launch {
                    runCatching {
                        client.addSupportMessage(detail.ticketId, body)
                        client.supportDetail(detail.ticketId)
                    }.onSuccess {
                        supportDetail = it
                        refresh()
                    }.onFailure {
                        error = it.message ?: "Gagal mengirim balasan."
                    }
                }
            }
        )
    }

    accountRequestType?.let { type ->
        AccountRequestDialog(
            type = type,
            onDismiss = { accountRequestType = null },
            onSubmit = { reason ->
                scope.launch {
                    error = null
                    runCatching {
                        client.createAccountRequest(type, reason)
                    }.onSuccess {
                        info = "Permintaan akun berhasil dikirim."
                        accountRequestType = null
                        refresh()
                    }.onFailure {
                        error = it.message ?: "Gagal membuat permintaan akun."
                    }
                }
            }
        )
    }
}

@Composable
private fun AccountStatusCard(a: Stage4HAccountSummary) {
    Surface(
        color = if (a.canGoOnline) AccountSoft else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(a.fullName.ifBlank { "GAWONE Mitra" }, fontWeight = FontWeight.Bold)
            Text(a.phone, style = MaterialTheme.typography.bodySmall)
            AccountLine("Akun", a.accountStatus)
            AccountLine("Onboarding", a.onboardingStatus)
            AccountLine("Ketersediaan", a.availabilityStatus)
            AccountLine("Boleh Online", if (a.canGoOnline) "YA" else "TIDAK")
        }
    }
}

@Composable
private fun PerformanceCards(p: Stage4HPerformance) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Rating", String.format(Locale.US, "%.2f", p.ratingAverage), Modifier.weight(1f))
        MetricCard("Selesai", p.completedJobs.toString(), Modifier.weight(1f))
        MetricCard(
            "Terima 30h",
            p.acceptanceRate?.let { String.format(Locale.US, "%.1f%%", it) } ?: "-",
            Modifier.weight(1f)
        )
    }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("30 hari terakhir", fontWeight = FontWeight.Bold)
            AccountLine("Offer diterima", p.offersReceived.toString())
            AccountLine("Accept", p.offersAccepted.toString())
            AccountLine("Reject", p.offersRejected.toString())
            AccountLine("Pekerjaan selesai", p.finishedAssignments.toString())
            AccountLine("Dibatalkan", p.cancelledAssignments.toString())
            AccountLine("No-show", p.noShowCount.toString())
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Surface(color = AccountSoft, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RatingCard(r: Stage4HRating) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(r.serviceName, fontWeight = FontWeight.Bold)
                Text("★".repeat(r.score.coerceIn(0, 5)), color = AccountGreen)
            }
            Text(r.orderNo, style = MaterialTheme.typography.bodySmall)
            r.comment?.let { Text(it) }
        }
    }
}

@Composable
private fun ScheduleCard(s: Stage4HSchedule) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.serviceName, fontWeight = FontWeight.Bold)
                Text(s.status, color = AccountGreen, fontWeight = FontWeight.Bold)
            }
            Text(s.orderNo, style = MaterialTheme.typography.bodySmall)
            Text(
                s.scheduledStart ?: "On-demand / mengikuti assignment aktif",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ServiceCard(s: Stage4HService) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.name, fontWeight = FontWeight.Bold)
                Text(if (s.isActive) "AKTIF" else "NONAKTIF")
            }
            Text(s.verificationStatus, style = MaterialTheme.typography.bodySmall)
            s.suspendedReason?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VehicleCard(v: Stage4HVehicle) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                listOfNotNull(v.brand, v.model).joinToString(" ").ifBlank { v.type },
                fontWeight = FontWeight.Bold
            )
            Text(v.plate ?: "-", style = MaterialTheme.typography.bodySmall)
            AccountLine("Verifikasi", v.verificationStatus)
        }
    }
}

@Composable
private fun DocumentCard(d: Stage4HDocument) {
    Surface(
        color = if (d.isExpired || d.status == "REJECTED") MaterialTheme.colorScheme.errorContainer else Color.White,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(d.type, fontWeight = FontWeight.Bold)
                Text(if (d.isExpired) "EXPIRED" else d.status)
            }
            d.rejectionReason?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RestrictionCard(
    restriction: Stage4HRestriction,
    hasOpenAppeal: Boolean,
    onAppeal: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(restriction.type, fontWeight = FontWeight.Bold)
            Text(restriction.reasonCode)
            restriction.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            restriction.effectiveUntil?.let {
                Text("Berlaku sampai: " + it, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onAppeal, enabled = !hasOpenAppeal) {
                Text(if (hasOpenAppeal) "Banding Diproses" else "Ajukan Banding")
            }
        }
    }
}

@Composable
private fun AppealCard(a: Stage4HAppeal) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Banding", fontWeight = FontWeight.Bold)
                Text(a.status)
            }
            Text(a.statement, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            a.resolutionNote?.let {
                Text("Hasil: " + it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SupportTicketCard(ticket: Stage4HSupportTicket, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onOpen
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ticket.ticketNo, fontWeight = FontWeight.Bold)
                Text(ticket.status)
            }
            Text(ticket.subject)
            ticket.lastMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Text(ticket.category + " • " + ticket.priority, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AccountRequestCard(
    request: Stage4HAccountRequest,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(request.type, fontWeight = FontWeight.Bold)
                Text(request.status)
            }
            request.resolutionNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (request.status == "RECEIVED") {
                TextButton(onClick = onCancel) { Text("Batalkan") }
            }
        }
    }
}

@Composable
private fun AppealDialog(
    restriction: Stage4HRestriction,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var statement by remember(restriction.restrictionId) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajukan Banding") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(restriction.reasonCode, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = statement,
                    onValueChange = { statement = it.take(3000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Penjelasan banding") },
                    minLines = 4
                )
                Text("Minimal 20 karakter.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = statement.trim().length >= 20,
                onClick = { onSubmit(statement.trim()) }
            ) { Text("Kirim Banding") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun NewSupportDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    val categories = listOf("ORDER", "PAYMENT", "ACCOUNT", "KYC", "SAFETY", "APP", "OTHER")
    var category by remember { mutableStateOf("APP") }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Tiket Bantuan") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Kategori", fontWeight = FontWeight.Bold)
                categories.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { code ->
                            FilterChip(
                                selected = category == code,
                                onClick = { category = code },
                                label = { Text(code) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it.take(160) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Judul") }
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(3000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pesan") },
                    minLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                enabled = subject.trim().length >= 5 && message.trim().length >= 3,
                onClick = { onSubmit(category, subject.trim(), message.trim()) }
            ) { Text("Kirim Tiket") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun SupportDetailDialog(
    detail: Stage4HSupportDetail,
    onDismiss: () -> Unit,
    onReply: (String) -> Unit
) {
    var reply by remember(detail.ticketId) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(detail.ticketNo)
                Text(detail.subject, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(detail.status + " • " + detail.priority, color = AccountGreen)
                Column(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detail.messages.forEach { m ->
                        Surface(
                            color = if (m.mine) AccountSoft else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(m.body)
                                Text(
                                    if (m.mine) "Anda" else m.senderType,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                if (detail.status != "CLOSED") {
                    OutlinedTextField(
                        value = reply,
                        onValueChange = { reply = it.take(3000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Balas tiket") },
                        minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            if (detail.status != "CLOSED") {
                Button(
                    enabled = reply.trim().isNotEmpty(),
                    onClick = {
                        val body = reply.trim()
                        reply = ""
                        onReply(body)
                    }
                ) { Text("Kirim Balasan") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun AccountRequestDialog(
    type: String,
    onDismiss: () -> Unit,
    onSubmit: (String?) -> Unit
) {
    var reason by remember(type) { mutableStateOf("") }
    val destructive = type != "DATA_EXPORT"
    val title = when (type) {
        "DATA_EXPORT" -> "Minta Ekspor Data"
        "ACCOUNT_DEACTIVATION" -> "Ajukan Deaktivasi Akun"
        else -> "Ajukan Penghapusan Akun"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (type == "ACCOUNT_DELETION")
                        "Permintaan ini masuk review. Data transaksi/keuangan yang wajib dipertahankan tidak dihapus otomatis dari aplikasi."
                    else if (type == "ACCOUNT_DEACTIVATION")
                        "Akun akan dinonaktifkan setelah permintaan disetujui dan diselesaikan oleh tim."
                    else
                        "Tim akan menyiapkan proses ekspor data akun secara aman."
                )
                if (destructive) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(2000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Alasan") },
                        minLines = 3
                    )
                    Text("Minimal 10 karakter.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !destructive || reason.trim().length >= 10,
                onClick = { onSubmit(reason.trim().takeIf { it.isNotBlank() }) }
            ) { Text("Kirim Permintaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun AccountLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
