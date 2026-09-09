package site.garsyanimultiusaha.gawone.mitra

import android.content.Context
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject

internal data class Stage4GRealtimeEvent(
    val table: String,
    val eventType: String,
    val record: JSONObject?
)

internal class Stage4GRealtimeSocket(context: Context) {
    private val appContext = context.applicationContext
    private val store = SecureSessionStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val started = AtomicBoolean(false)
    private val ref = AtomicLong(1L)
    private val mutableEvents = MutableSharedFlow<Stage4GRealtimeEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val events: SharedFlow<Stage4GRealtimeEvent> = mutableEvents

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectDelayMs = 1_000L

    fun start() {
        if (started.compareAndSet(false, true)) connect()
    }

    fun stop() {
        started.set(false)
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        socket?.close(1000, "screen_closed")
        socket = null
    }

    private fun connect() {
        if (!started.get()) return
        val session = store.read() ?: return
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        val wsBase = BuildConfig.SUPABASE_URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/')
        val url = wsBase + "/realtime/v1/websocket?apikey=" +
            URLEncoder.encode(key, StandardCharsets.UTF_8.toString()) +
            "&vsn=1.0.0"

        socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectDelayMs = 1_000L
                    webSocket.send(joinPayload(session.accessToken).toString())
                    startHeartbeat(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    heartbeatJob?.cancel()
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    heartbeatJob?.cancel()
                    scheduleReconnect()
                }
            }
        )
    }

    private fun joinPayload(accessToken: String): JSONObject {
        val changes = JSONArray()
        listOf(
            "partner_notification_inbox",
            "order_chat_messages",
            "order_assignments",
            "order_assignment_offers",
            "orders",
            "partner_documents",
            "partner_onboarding",
            "partner_earnings",
            "partner_wallet_ledger",
            "partner_payout_requests",
            "partner_ratings",
            "partner_account_restrictions",
            "partner_appeals",
            "partner_support_tickets",
            "partner_support_messages",
            "partner_account_requests"
        ).forEach { table ->
            changes.put(
                JSONObject()
                    .put("event", "*")
                    .put("schema", "public")
                    .put("table", table)
            )
        }

        val config = JSONObject()
            .put("broadcast", JSONObject().put("ack", false).put("self", false))
            .put("presence", JSONObject().put("key", ""))
            .put("postgres_changes", changes)

        return JSONObject()
            .put("topic", "realtime:gawone-mitra")
            .put("event", "phx_join")
            .put(
                "payload",
                JSONObject()
                    .put("config", config)
                    .put("access_token", accessToken)
            )
            .put("ref", ref.getAndIncrement().toString())
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && started.get()) {
                delay(25_000)
                webSocket.send(
                    JSONObject()
                        .put("topic", "phoenix")
                        .put("event", "heartbeat")
                        .put("payload", JSONObject())
                        .put("ref", ref.getAndIncrement().toString())
                        .toString()
                )
            }
        }
    }

    private fun handleMessage(text: String) {
        runCatching {
            val root = JSONObject(text)
            if (root.optString("event") != "postgres_changes") return
            val payload = root.optJSONObject("payload") ?: return
            val data = payload.optJSONObject("data") ?: return
            val table = data.optString("table")
            val type = data.optString("type")
            val record = data.optJSONObject("record")
            if (table.isNotBlank()) {
                mutableEvents.tryEmit(Stage4GRealtimeEvent(table, type, record))
            }
        }
    }

    private fun scheduleReconnect() {
        if (!started.get() || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(30_000L)
            connect()
        }
    }
}
