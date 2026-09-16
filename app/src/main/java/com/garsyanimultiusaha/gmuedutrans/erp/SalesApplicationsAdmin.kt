package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private val JoinGreen = Color(0xFF176A3B)
private val JoinDark = Color(0xFF0D4E2C)
private val JoinGold = Color(0xFFC79B2C)
private val JoinSoft = Color(0xFFEEF7F0)
private val JoinWarn = Color(0xFFFFF5D6)

private data class SalesApplicationAdminRow(
    val id: String,
    val code: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val city: String,
    val education: String,
    val experience: String,
    val motivation: String,
    val status: String,
    val rejectionReason: String,
    val staffUserId: String,
    val createdAt: String,
    val reviewedAt: String,
    val activatedAt: String
)

private data class InitialCredential(
    val applicationCode: String,
    val email: String,
    val password: String
)

private class SalesApplicationsAdminApi {
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun list(accessToken: String): List<SalesApplicationAdminRow> = withContext(Dispatchers.IO) {
        val root = JSONObject(call(accessToken, JSONObject().put("action", "sales_application_list")))
        val arr = root.optJSONArray("applications") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(
                    SalesApplicationAdminRow(
                        id = x.optString("id"),
                        code = x.optString("application_code"),
                        fullName = x.optString("full_name"),
                        email = x.optString("email"),
                        phone = x.optString("phone"),
                        city = x.optString("city"),
                        education = x.optString("education"),
                        experience = x.optString("experience"),
                        motivation = x.optString("motivation"),
                        status = x.optString("status", "PENDING"),
                        rejectionReason = x.optString("rejection_reason"),
                        staffUserId = x.optString("staff_user_id"),
                        createdAt = x.optString("created_at"),
                        reviewedAt = x.optString("reviewed_at"),
                        activatedAt = x.optString("activated_at")
                    )
                )
            }
        }
    }

    suspend fun provision(accessToken: String, applicationId: String): InitialCredential = withContext(Dispatchers.IO) {
        val root = JSONObject(
            call(
                accessToken,
                JSONObject().put("action", "sales_application_provision").put("application_id", applicationId)
            )
        )
        val password = root.optString("temporary_password")
        if (password.isBlank()) {
            throw IllegalStateException(root.optString("message", "Akun sudah dibuat dan tidak ada password awal baru untuk ditampilkan."))
        }
        InitialCredential(
            applicationCode = root.optString("application_code"),
            email = root.optString("email"),
            password = password
        )
    }

    suspend fun resetInitialCredential(accessToken: String, applicationId: String): InitialCredential = withContext(Dispatchers.IO) {
        val root = JSONObject(
            call(
                accessToken,
                JSONObject().put("action", "sales_application_reset_temp").put("application_id", applicationId)
            )
        )
        InitialCredential(
            applicationCode = root.optString("application_code"),
            email = root.optString("email"),
            password = root.optString("temporary_password")
        )
    }

    suspend fun reject(accessToken: String, applicationId: String, reason: String) = withContext(Dispatchers.IO) {
        call(
            accessToken,
            JSONObject()
                .put("action", "sales_application_reject")
                .put("application_id", applicationId)
                .put("reason", reason)
        )
        Unit
    }

    private fun call(accessToken: String, payload: JSONObject): String {
        val connection = (URL("$base/functions/v1/create-staff-user").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 35_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = if (stream == null) "" else BufferedReader(InputStreamReader(stream)).use { it.readText() }
        connection.disconnect()
        if (code !in 200..299) {
            val message = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
            throw IllegalStateException(message.ifBlank { "Aksi pengajuan Sales gagal." })
        }
        return text.ifBlank { "{}" }
    }
}

@Composable
fun UsersAndSalesApplicationsScreen(
    vm: MainViewModel,
    session: SessionState,
    onNotice: (String) -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Akun Staff") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Join Sales") })
        }
        when (tab) {
            0 -> UsersScreen(vm, session, onNotice)
            else -> SalesApplicationsAdminScreen(session, onNotice)
        }
    }
}

@Composable
private fun SalesApplicationsAdminScreen(session: SessionState, onNotice: (String) -> Unit) {
    if (session.profile.role != "Owner") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Hanya Owner yang dapat memprovision akun Sales.")
        }
        return
    }

    val api = remember { SalesApplicationsAdminApi() }
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<SalesApplicationAdminRow>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("PENDING") }
    var credential by remember { mutableStateOf<InitialCredential?>(null) }
    var rejectRow by remember { mutableStateOf<SalesApplicationAdminRow?>(null) }

    fun reload() {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { api.list(session.accessToken) }
                .onSuccess { rows = it }
                .onFailure { onNotice(it.message ?: "Pengajuan Sales gagal dimuat.") }
            busy = false
        }
    }

    LaunchedEffect(session.accessToken) {
        busy = true
        runCatching { api.list(session.accessToken) }
            .onSuccess { rows = it }
            .onFailure { onNotice(it.message ?: "Pengajuan Sales gagal dimuat.") }
        busy = false
    }

    val visible = rows.filter { filter == "ALL" || it.status == filter }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sales Join & Activation", fontWeight = FontWeight.Black, fontSize = 20.sp, color = JoinDark)
                Text("PENDING → approve/provision → ACCOUNT_CREATED → first-password → ACTIVATED", fontSize = 10.sp, color = Color.Gray)
            }
            OutlinedButton(onClick = { reload() }, enabled = !busy) { Text("Refresh") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("PENDING", "ACCOUNT_CREATED", "ACTIVATED", "REJECTED", "ALL").forEach { status ->
                FilterChip(selected = filter == status, onClick = { filter = status }, label = { Text(status.replace('_', ' '), fontSize = 8.sp) })
            }
        }
        Spacer(Modifier.height(6.dp))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (!busy && visible.isEmpty()) item {
                Card(shape = RoundedCornerShape(18.dp)) { Text("Belum ada pengajuan pada filter ini.", Modifier.padding(16.dp), color = Color.Gray) }
            }
            items(visible, key = { it.id }) { row ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (row.status) {
                            "ACTIVATED" -> JoinSoft
                            "ACCOUNT_CREATED" -> JoinWarn
                            else -> Color.White
                        }
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(row.fullName, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Text("${row.code} • ${row.city.ifBlank { "Wilayah belum diisi" }}", fontSize = 9.sp, color = Color.Gray)
                            }
                            Surface(shape = RoundedCornerShape(99.dp), color = JoinSoft) {
                                Text(row.status.replace('_', ' '), Modifier.padding(horizontal = 8.dp, vertical = 5.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = JoinDark)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(row.email, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(row.phone, fontSize = 10.sp, color = Color.Gray)
                        if (row.education.isNotBlank()) Text("Pendidikan: ${row.education}", fontSize = 10.sp)
                        if (row.experience.isNotBlank()) Text("Pengalaman: ${row.experience}", fontSize = 10.sp)
                        if (row.motivation.isNotBlank()) Text("Motivasi: ${row.motivation}", fontSize = 10.sp, color = Color.Gray)
                        if (row.rejectionReason.isNotBlank()) Text("Alasan: ${row.rejectionReason}", fontSize = 10.sp, color = Color.Red)
                        Text("Diajukan ${row.createdAt.take(16).replace('T', ' ')}", fontSize = 8.sp, color = Color.Gray)
                        Spacer(Modifier.height(10.dp))
                        when (row.status) {
                            "PENDING", "APPROVED" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                OutlinedButton(onClick = { rejectRow = row }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Tolak") }
                                Button(
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            runCatching { api.provision(session.accessToken, row.id) }
                                                .onSuccess {
                                                    credential = it
                                                    onNotice("Akun Sales ${row.code} berhasil dibuat. Password awal hanya tampil sekali.")
                                                }
                                                .onFailure { onNotice(it.message ?: "Provision akun gagal.") }
                                            rows = runCatching { api.list(session.accessToken) }.getOrDefault(rows)
                                            busy = false
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Approve & Buat Akun") }
                            }
                            "ACCOUNT_CREATED" -> Button(
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        runCatching { api.resetInitialCredential(session.accessToken, row.id) }
                                            .onSuccess {
                                                credential = it
                                                onNotice("Password awal ${row.code} direset. Nilai baru hanya tampil sekali.")
                                            }
                                            .onFailure { onNotice(it.message ?: "Reset password awal gagal.") }
                                        rows = runCatching { api.list(session.accessToken) }.getOrDefault(rows)
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Reset Password Awal") }
                            "ACTIVATED" -> Text("✓ Akun aktif dan onboarding selesai.", color = JoinGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }

    credential?.let { value -> InitialCredentialDialog(value) { credential = null } }
    rejectRow?.let { row ->
        var reason by remember(row.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!busy) rejectRow = null },
            title = { Text("Tolak ${row.fullName}") },
            text = { OutlinedTextField(reason, { reason = it }, label = { Text("Alasan penolakan") }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { api.reject(session.accessToken, row.id, reason) }
                                .onSuccess { onNotice("Pengajuan ${row.code} ditolak.") }
                                .onFailure { onNotice(it.message ?: "Penolakan gagal.") }
                            rows = runCatching { api.list(session.accessToken) }.getOrDefault(rows)
                            busy = false
                            rejectRow = null
                        }
                    },
                    enabled = !busy && reason.trim().length >= 3
                ) { Text("Tolak Pengajuan") }
            },
            dismissButton = { TextButton(onClick = { rejectRow = null }, enabled = !busy) { Text("Batal") } }
        )
    }
}

@Composable
private fun InitialCredentialDialog(value: InitialCredential, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Kredensial Awal — Tampil Sekali", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Card(colors = CardDefaults.cardColors(containerColor = JoinWarn), shape = RoundedCornerShape(14.dp)) {
                    Text(
                        "Sistem tidak menyimpan password ini. Kirim ke Sales melalui kanal privat. Sales wajib mengganti password sebelum workspace terbuka.",
                        Modifier.padding(12.dp),
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("${value.applicationCode}\n${value.email}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(value.password, fontSize = 18.sp, fontWeight = FontWeight.Black, color = JoinDark)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString("Email: ${value.email}\nPassword awal: ${value.password}")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Salin Kredensial") }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Saya Sudah Menyimpan") } }
    )
}
