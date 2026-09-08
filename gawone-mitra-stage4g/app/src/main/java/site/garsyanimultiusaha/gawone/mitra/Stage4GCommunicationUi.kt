package site.garsyanimultiusaha.gawone.mitra

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val CommunicationSoft = Color(0xFFE8F4EE)
private val CommunicationGreen = Color(0xFF0A6B47)

@Composable
internal fun Stage4GCommunicationPanel() {
    val context = LocalContext.current
    val client = remember { Stage4GCommunicationClient(context.applicationContext) }
    val realtime = remember { Stage4GRealtimeSocket(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var center by remember { mutableStateOf<Stage4GNotificationCenter?>(null) }
    var chatOrders by remember { mutableStateOf<List<Stage4GChatOrder>>(emptyList()) }
    var selectedOrder by remember { mutableStateOf<Stage4GChatOrder?>(null) }
    var messages by remember { mutableStateOf<List<Stage4GChatMessage>>(emptyList()) }
    var pendingOffline by remember { mutableIntStateOf(client.pendingOfflineCount()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val deepLinkTarget by Stage4GDeepLinkRouter.target.collectAsState()

    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationGranted = granted }

    suspend fun refreshCenter() {
        center = client.notificationCenter()
        chatOrders = client.chatOrders()
        pendingOffline = client.pendingOfflineCount()
    }

    suspend fun refreshSelectedChat() {
        val order = selectedOrder ?: return
        messages = client.chat(order.orderId)
        client.markChatRead(order.orderId)
        center = client.notificationCenter()
        chatOrders = client.chatOrders()
    }

    DisposableEffect(Unit) {
        realtime.start()
        onDispose { realtime.stop() }
    }

    LaunchedEffect(Unit) {
        loading = true
        runCatching {
            client.syncPending()
            refreshCenter()
        }.onFailure {
            error = it.message ?: "Gagal memuat komunikasi."
        }
        pendingOffline = client.pendingOfflineCount()
        loading = false

        launch {
            realtime.events.collect { event ->
                when (event.table) {
                    "partner_notification_inbox",
                    "order_assignments",
                    "order_assignment_offers",
                    "orders",
                    "partner_documents",
                    "partner_onboarding",
                    "partner_earnings",
                    "partner_wallet_ledger",
                    "partner_payout_requests" -> {
                        runCatching { refreshCenter() }
                    }
                    "order_chat_messages" -> {
                        runCatching {
                            refreshCenter()
                            refreshSelectedChat()
                        }
                    }
                }
            }
        }

        launch {
            while (isActive) {
                delay(30_000)
                runCatching {
                    val synced = client.syncPending()
                    if (synced > 0) info = synced.toString() + " pesan offline berhasil disinkronkan."
                    refreshCenter()
                    if (selectedOrder != null) refreshSelectedChat()
                }
                pendingOffline = client.pendingOfflineCount()
            }
        }
    }

    LaunchedEffect(deepLinkTarget, chatOrders) {
        val target = deepLinkTarget
        val orderId = when (target) {
            is Stage4GDeepLinkTarget.Chat -> target.orderId
            is Stage4GDeepLinkTarget.Order -> target.orderId
            else -> null
        }
        if (orderId != null) {
            chatOrders.firstOrNull { it.orderId == orderId }?.let {
                selectedOrder = it
                runCatching { refreshSelectedChat() }
                if (target != null) Stage4GDeepLinkRouter.consume(target)
            }
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
                Text("Notifikasi & Chat", fontWeight = FontWeight.Bold)
                Text(
                    "Stage 4G • realtime + offline retry",
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
                        runCatching {
                            client.syncPending()
                            refreshCenter()
                            if (selectedOrder != null) refreshSelectedChat()
                        }.onFailure { error = it.message ?: "Gagal refresh." }
                        pendingOffline = client.pendingOfflineCount()
                        loading = false
                    }
                }
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh komunikasi")
            }
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (!BuildConfig.PUSH_PROVIDER_CONFIGURED) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Push production belum diaktifkan", fontWeight = FontWeight.Bold)
                    Text(
                        "Inbox, chat, dan Supabase Realtime sudah aktif di source. FCM baru dinyalakan setelah project Firebase dan credential server production tersedia.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Button(
                onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Izinkan Notifikasi")
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        info?.let {
            Surface(
                color = CommunicationSoft,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(it, Modifier.padding(12.dp))
            }
        }

        if (pendingOffline > 0) {
            Text(
                pendingOffline.toString() + " pesan menunggu sinkronisasi jaringan.",
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold
            )
        }

        center?.let { c ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommunicationCounter(
                    "Inbox",
                    c.unreadCount,
                    Icons.Outlined.Notifications,
                    Modifier.weight(1f)
                )
                CommunicationCounter(
                    "Chat",
                    c.chatUnreadCount,
                    Icons.Outlined.ChatBubbleOutline,
                    Modifier.weight(1f)
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Inbox terbaru", fontWeight = FontWeight.Bold)
                if (c.unreadCount > 0) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    client.markAllNotificationsRead()
                                    refreshCenter()
                                }
                            }
                        }
                    ) { Text("Tandai dibaca") }
                }
            }

            if (c.notifications.isEmpty()) {
                Text("Belum ada notifikasi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                c.notifications.take(6).forEach { n ->
                    NotificationCard(
                        notification = n,
                        onOpen = {
                            scope.launch {
                                runCatching {
                                    client.markNotificationRead(n.notificationId)
                                    n.route?.let { Stage4GDeepLinkRouter.accept(Uri.parse(it)) }
                                    refreshCenter()
                                }
                            }
                        }
                    )
                }
            }
        }

        HorizontalDivider()
        Text("Chat Order", fontWeight = FontWeight.Bold)

        if (chatOrders.isEmpty()) {
            Text(
                "Chat tersedia setelah Anda memiliki assignment.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            chatOrders.take(8).forEach { order ->
                ChatOrderCard(order) {
                    selectedOrder = order
                    scope.launch {
                        error = null
                        runCatching { refreshSelectedChat() }
                            .onFailure { error = it.message ?: "Gagal membuka chat." }
                    }
                }
            }
        }
    }

    selectedOrder?.let { order ->
        ChatDialog(
            order = order,
            messages = messages,
            pendingOffline = pendingOffline,
            onDismiss = { selectedOrder = null },
            onRefresh = {
                scope.launch { runCatching { refreshSelectedChat() } }
            },
            onSend = { body ->
                scope.launch {
                    error = null
                    val result = runCatching { client.sendChat(order.orderId, body) }
                    result.onSuccess {
                        if (it.queuedOffline) {
                            info = "Pesan disimpan offline dan akan dikirim otomatis saat jaringan kembali."
                        } else {
                            runCatching { refreshSelectedChat() }
                        }
                        pendingOffline = client.pendingOfflineCount()
                    }.onFailure {
                        error = it.message ?: "Gagal mengirim pesan."
                    }
                }
            }
        )
    }
}

@Composable
private fun CommunicationCounter(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier
) {
    Surface(
        color = if (count > 0) CommunicationSoft else Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = CommunicationGreen)
            Column {
                Text(count.toString(), fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: Stage4GNotification,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onOpen
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(notification.title, fontWeight = FontWeight.Bold)
                if (notification.readAt == null) {
                    Surface(
                        color = CommunicationGreen,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
            }
            Text(notification.body)
            Text(
                notification.kind,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatOrderCard(order: Stage4GChatOrder, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onOpen
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(order.serviceName, fontWeight = FontWeight.Bold)
                    Text(order.orderNo, style = MaterialTheme.typography.bodySmall)
                }
                if (order.unreadCount > 0) {
                    Badge { Text(order.unreadCount.toString()) }
                }
            }
            Text(
                order.lastMessage ?: "Belum ada pesan.",
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                order.assignmentStatus,
                style = MaterialTheme.typography.labelSmall,
                color = CommunicationGreen
            )
        }
    }
}

@Composable
private fun ChatDialog(
    order: Stage4GChatOrder,
    messages: List<Stage4GChatMessage>,
    pendingOffline: Int,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSend: (String) -> Unit
) {
    var input by remember(order.orderId) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(order.serviceName)
                Text(order.orderNo, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier
                        .heightIn(max = 330.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty()) {
                        Text(
                            "Belum ada pesan.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        messages.forEach { message ->
                            Surface(
                                color = if (message.mine)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = if (message.mine) 40.dp else 0.dp,
                                        end = if (message.mine) 0.dp else 40.dp
                                    )
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(message.body)
                                    Text(
                                        if (message.mine) "Anda" else message.senderType,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (pendingOffline > 0) {
                    Text(
                        pendingOffline.toString() + " pesan offline tertunda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(2000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tulis pesan") },
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                enabled = input.trim().isNotEmpty(),
                onClick = {
                    val body = input.trim()
                    input = ""
                    onSend(body)
                }
            ) { Text("Kirim") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRefresh) { Text("Refresh") }
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        }
    )
}
