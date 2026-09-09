package site.garsyanimultiusaha.gawone.mitra

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class Stage4GNotification(
    val notificationId: String,
    val kind: String,
    val title: String,
    val body: String,
    val route: String?,
    val readAt: String?,
    val createdAt: String?
)

internal data class Stage4GNotificationCenter(
    val unreadCount: Int,
    val chatUnreadCount: Int,
    val notifications: List<Stage4GNotification>
)

internal data class Stage4GChatOrder(
    val orderId: String,
    val orderNo: String,
    val serviceName: String,
    val assignmentId: String,
    val assignmentStatus: String,
    val lastMessage: String?,
    val unreadCount: Int
)

internal data class Stage4GChatMessage(
    val messageId: String,
    val senderType: String,
    val body: String,
    val mine: Boolean,
    val createdAt: String?
)

internal data class Stage4GSendResult(
    val messageId: String?,
    val queuedOffline: Boolean,
    val idempotent: Boolean
)

internal data class Stage4GQueuedChat(
    val clientMessageId: String,
    val orderId: String,
    val body: String
)

private class Stage4GApiException(val statusCode: Int, message: String) : Exception(message)

private class Stage4GOfflineQueue(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "gawone_stage4g_offline.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            create table chat_outbox(
              client_message_id text primary key,
              order_id text not null,
              body text not null,
              created_at integer not null
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun enqueue(item: Stage4GQueuedChat) {
        writableDatabase.insertWithOnConflict(
            "chat_outbox",
            null,
            ContentValues().apply {
                put("client_message_id", item.clientMessageId)
                put("order_id", item.orderId)
                put("body", item.body)
                put("created_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun pending(limit: Int = 50): List<Stage4GQueuedChat> {
        val out = mutableListOf<Stage4GQueuedChat>()
        readableDatabase.query(
            "chat_outbox",
            arrayOf("client_message_id", "order_id", "body"),
            null, null, null, null,
            "created_at asc",
            limit.coerceIn(1, 100).toString()
        ).use { c ->
            while (c.moveToNext()) {
                out += Stage4GQueuedChat(
                    clientMessageId = c.getString(0),
                    orderId = c.getString(1),
                    body = c.getString(2)
                )
            }
        }
        return out
    }

    fun remove(clientMessageId: String) {
        writableDatabase.delete(
            "chat_outbox",
            "client_message_id=?",
            arrayOf(clientMessageId)
        )
    }

    fun count(): Int {
        readableDatabase.rawQuery("select count(*) from chat_outbox", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }
}

internal class Stage4GCommunicationClient(context: Context) {
    private val appContext = context.applicationContext
    private val store = SecureSessionStore(appContext)
    private val offline = Stage4GOfflineQueue(appContext)
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val refreshMutex = Mutex()

    suspend fun notificationCenter(): Stage4GNotificationCenter {
        val j = JSONObject(rpc("get_my_notification_center", JSONObject().put("p_limit", 40)))
        return Stage4GNotificationCenter(
            unreadCount = j.optInt("unreadCount"),
            chatUnreadCount = j.optInt("chatUnreadCount"),
            notifications = j.optJSONArray("notifications").notificationList()
        )
    }

    suspend fun markNotificationRead(notificationId: String) {
        rpc(
            "mark_my_notification_read",
            JSONObject().put("p_notification_id", notificationId)
        )
    }

    suspend fun markAllNotificationsRead() {
        rpc("mark_all_my_notifications_read", JSONObject())
    }

    suspend fun chatOrders(): List<Stage4GChatOrder> {
        val a = JSONArray(rpc("get_my_chat_orders", JSONObject().put("p_limit", 30)))
        return buildList {
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                add(
                    Stage4GChatOrder(
                        orderId = j.optString("orderId"),
                        orderNo = j.optString("orderNo"),
                        serviceName = j.optString("serviceName"),
                        assignmentId = j.optString("assignmentId"),
                        assignmentStatus = j.optString("assignmentStatus"),
                        lastMessage = j.textOrNull4G("lastMessage"),
                        unreadCount = j.optInt("unreadCount")
                    )
                )
            }
        }
    }

    suspend fun chat(orderId: String): List<Stage4GChatMessage> {
        val j = JSONObject(
            rpc(
                "get_order_chat",
                JSONObject().put("p_order_id", orderId).put("p_limit", 80)
            )
        )
        val a = j.optJSONArray("messages") ?: JSONArray()
        return buildList {
            for (i in 0 until a.length()) {
                val m = a.getJSONObject(i)
                add(
                    Stage4GChatMessage(
                        messageId = m.optString("messageId"),
                        senderType = m.optString("senderType"),
                        body = m.optString("body"),
                        mine = m.optBoolean("mine"),
                        createdAt = m.textOrNull4G("createdAt")
                    )
                )
            }
        }
    }

    suspend fun markChatRead(orderId: String) {
        rpc(
            "mark_order_chat_read",
            JSONObject()
                .put("p_order_id", orderId)
                .put("p_message_id", JSONObject.NULL)
        )
    }

    suspend fun sendChat(orderId: String, body: String): Stage4GSendResult {
        val clean = body.trim()
        require(clean.isNotEmpty()) { "Pesan kosong." }
        require(clean.length <= 2000) { "Pesan maksimal 2000 karakter." }

        val clientId = UUID.randomUUID().toString()
        return try {
            sendChatNow(orderId, clientId, clean)
        } catch (io: IOException) {
            offline.enqueue(Stage4GQueuedChat(clientId, orderId, clean))
            Stage4GSendResult(null, true, false)
        }
    }

    suspend fun syncPending(): Int {
        var sent = 0
        for (item in offline.pending()) {
            try {
                sendChatNow(item.orderId, item.clientMessageId, item.body)
                offline.remove(item.clientMessageId)
                sent++
            } catch (io: IOException) {
                break
            } catch (api: Stage4GApiException) {
                if (api.statusCode in 400..499) {
                    offline.remove(item.clientMessageId)
                } else {
                    break
                }
            }
        }
        return sent
    }

    fun pendingOfflineCount(): Int = offline.count()

    suspend fun registerPushToken(token: String, appVersion: String, deviceName: String) {
        rpc(
            "register_my_push_token",
            JSONObject()
                .put("p_token", token)
                .put("p_app_version", appVersion)
                .put("p_device_name", deviceName)
        )
    }

    private suspend fun sendChatNow(
        orderId: String,
        clientMessageId: String,
        body: String
    ): Stage4GSendResult {
        val j = JSONObject(
            rpc(
                "send_order_chat_message",
                JSONObject()
                    .put("p_order_id", orderId)
                    .put("p_client_message_id", clientMessageId)
                    .put("p_body", body)
            )
        )
        return Stage4GSendResult(
            messageId = j.optString("messageId").takeIf { it.isNotBlank() },
            queuedOffline = false,
            idempotent = j.optBoolean("idempotent")
        )
    }

    private suspend fun rpc(name: String, body: JSONObject): String {
        val session = validSession()
        return request(
            "/rest/v1/rpc/" + name,
            "POST",
            body.toString(),
            session.accessToken
        )
    }

    private suspend fun validSession(): Session = refreshMutex.withLock {
        val current = store.read()
            ?: throw IllegalStateException("Sesi Mitra tidak tersedia. Silakan login ulang.")
        val now = System.currentTimeMillis() / 1000L
        if (current.expiresAt - now > 90L) return@withLock current

        val raw = request(
            "/auth/v1/token?grant_type=refresh_token",
            "POST",
            JSONObject().put("refresh_token", current.refreshToken).toString(),
            null
        )
        val j = JSONObject(raw)
        val user = j.getJSONObject("user")
        val refreshed = Session(
            accessToken = j.getString("access_token"),
            refreshToken = j.getString("refresh_token"),
            expiresAt = j.optLong("expires_at").takeIf { it > 0L }
                ?: (now + j.optLong("expires_in", 3600L)),
            userId = user.getString("id"),
            phone = user.optString("phone", current.phone)
        )
        store.save(refreshed)
        refreshed
    }

    private suspend fun request(
        path: String,
        method: String,
        body: String?,
        token: String?
    ): String = withContext(Dispatchers.IO) {
        val c = try {
            URL(baseUrl + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw IOException("Tidak dapat membuka koneksi.", e)
        }

        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.doInput = true
            c.useCaches = false
            c.setRequestProperty("apikey", key)
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) c.setRequestProperty("Authorization", "Bearer " + token)

            if (body != null) {
                c.doOutput = true
                c.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }

            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val server = runCatching {
                    val j = JSONObject(raw)
                    j.optString("message")
                        .ifBlank { j.optString("msg") }
                        .ifBlank { j.optString("error") }
                }.getOrDefault("")
                throw Stage4GApiException(
                    code,
                    server.ifBlank { "Permintaan komunikasi gagal (HTTP " + code + ")." }
                )
            }
            raw
        } catch (e: Stage4GApiException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Koneksi jaringan bermasalah.", e)
        } finally {
            c.disconnect()
        }
    }
}

private fun JSONArray?.notificationList(): List<Stage4GNotification> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val j = getJSONObject(i)
            add(
                Stage4GNotification(
                    notificationId = j.optString("notificationId"),
                    kind = j.optString("kind"),
                    title = j.optString("title"),
                    body = j.optString("body"),
                    route = j.textOrNull4G("route"),
                    readAt = j.textOrNull4G("readAt"),
                    createdAt = j.textOrNull4G("createdAt")
                )
            )
        }
    }
}

private fun JSONObject.textOrNull4G(name: String): String? =
    optString(name).takeIf { it.isNotBlank() && it != "null" }
