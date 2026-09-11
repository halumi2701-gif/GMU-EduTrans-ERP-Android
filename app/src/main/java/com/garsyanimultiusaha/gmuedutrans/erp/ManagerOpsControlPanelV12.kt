package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manager Ops Control Panel v1.2.
 *
 * Important safety rule: every production write is two-step:
 * PREVIEW_ACTION -> explicit Manager confirmation -> COMMIT_ACTION.
 * This UI never auto-publishes customer information and never changes booking status.
 */
@Composable
fun GmuNativeAppWithManagerOpsControlPanel(vm: MainViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithDecisionNotifications(vm)

        val session = (vm.state as? AppState.LoggedIn)?.session
        if (
            session != null &&
            (ErpRoles.isManagerEduTrans(session.profile.role) || session.profile.role == ErpRoles.OWNER) &&
            vm.currentPage == AppPage.DASHBOARD
        ) {
            ManagerOpsControlDock(vm = vm, session = session)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.ManagerOpsControlDock(vm: MainViewModel, session: SessionState) {
    var opened by remember { mutableStateOf(false) }
    val activeBookings = remember(vm.bookings) {
        vm.bookings
            .filter { it.status.lowercase() !in setOf("completed", "closed", "cancelled", "canceled", "rejected") }
            .sortedBy { it.tripDate }
    }
    val nextTrip = activeBookings.firstOrNull()

    Surface(
        onClick = { opened = true },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 18.dp, bottom = 152.dp),
        shape = RoundedCornerShape(18.dp),
        color = GmuDark,
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = GmuGold,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Ops Control v1.2", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text(
                    nextTrip?.let { "${it.bookingNo} • preview first" } ?: "Controlled actions",
                    color = Color.White.copy(alpha = .68f),
                    fontSize = 9.sp
                )
            }
        }
    }

    if (opened) {
        ModalBottomSheet(
            onDismissRequest = { opened = false },
            containerColor = GmuBg,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            ManagerOpsControlPanel(
                vm = vm,
                session = session,
                activeBookings = activeBookings,
                onClose = { opened = false }
            )
        }
    }
}

@Composable
private fun ManagerOpsControlPanel(
    vm: MainViewModel,
    session: SessionState,
    activeBookings: List<Booking>,
    onClose: () -> Unit
) {
    val api = remember { ManagerOpsControlApi() }
    val scope = rememberCoroutineScope()
    var selectedBookingId by remember(activeBookings) { mutableStateOf(activeBookings.firstOrNull()?.id.orEmpty()) }
    var panel by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var editingAction by remember { mutableStateOf<String?>(null) }
    var previewResult by remember { mutableStateOf<JSONObject?>(null) }
    var committing by remember { mutableStateOf(false) }

    LaunchedEffect(selectedBookingId, session.accessToken, reloadKey) {
        if (selectedBookingId.isBlank()) {
            panel = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching { api.controlPanel(session.accessToken, selectedBookingId) }
            .onSuccess { panel = it }
            .onFailure { error = it.message ?: "Control Panel gagal dimuat." }
        loading = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 42.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = GmuDark) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = GmuGold,
                    modifier = Modifier.size(48.dp).padding(12.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Manager Ops Control Panel", fontSize = 19.sp, fontWeight = FontWeight.Black, color = GmuDark)
                Text("v1.2 • Preview → Confirm & Apply", fontSize = 10.sp, color = Color.Gray)
            }
            TextButton(onClick = onClose) { Text("Tutup") }
        }

        Spacer(Modifier.height(14.dp))

        if (activeBookings.isEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Text(
                    "Belum ada booking aktif untuk dikontrol.",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            return@Column
        }

        Text("Pilih trip", fontSize = 12.sp, fontWeight = FontWeight.Black, color = GmuDark)
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            activeBookings.take(8).forEach { booking ->
                FilterChip(
                    selected = selectedBookingId == booking.id,
                    onClick = {
                        selectedBookingId = booking.id
                        previewResult = null
                        editingAction = null
                        message = null
                    },
                    label = { Text(booking.bookingNo, fontSize = 10.sp) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
        }
        error?.let {
            OpsInfoCard(text = it, danger = true)
            Spacer(Modifier.height(12.dp))
        }
        message?.let {
            OpsInfoCard(text = it, danger = false)
            Spacer(Modifier.height(12.dp))
        }

        panel?.let { data ->
            val readiness = data.optJSONObject("trip_readiness")
            val preparation = data.optJSONObject("preparation")
            val readyPct = readiness?.optInt("progress", 0) ?: 0
            val prepPct = preparation?.optInt("progress", 0) ?: 0
            val tripLocked = preparation?.optBoolean("trip_locked", false) ?: false
            val openPreviews = preparation?.optInt("open_previews", 0) ?: 0

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Trip Readiness", fontWeight = FontWeight.Black, color = GmuDark)
                            Text(
                                if (tripLocked) "Trip sudah dikunci untuk perubahan pre-trip" else "Kesiapan produksi & customer",
                                fontSize = 10.sp,
                                color = if (tripLocked) GmuWarn else Color.Gray
                            )
                        }
                        Text("$readyPct%", fontSize = 20.sp, fontWeight = FontWeight.Black, color = if (readyPct >= 80) GmuGreen else GmuWarn)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { readyPct / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (readyPct >= 80) GmuGreen else GmuGold,
                        trackColor = GmuSoft
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Preparation draft", fontSize = 11.sp, color = Color.Gray)
                        Text("$prepPct%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GmuDark)
                    }
                    if (openPreviews > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("$openPreviews preview masih aktif. Preview kedaluwarsa otomatis setelah 15 menit.", fontSize = 10.sp, color = GmuWarn)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Controlled Actions", fontSize = 15.sp, fontWeight = FontWeight.Black, color = GmuDark)
            Text("Tidak ada perubahan produksi sebelum Anda melihat preview lalu menekan Confirm & Apply.", fontSize = 10.sp, color = Color.Gray)
            Spacer(Modifier.height(10.dp))

            val controls = data.optJSONArray("controls") ?: JSONArray()
            val rows = buildList<JSONObject> {
                for (i in 0 until controls.length()) add(controls.optJSONObject(i) ?: JSONObject())
            }
            rows.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { control ->
                        val action = control.optString("action")
                        ControlledActionCard(
                            action = action,
                            label = control.optString("label", action),
                            enabled = control.optBoolean("enabled", false),
                            modifier = Modifier.weight(1f),
                            onClick = { editingAction = action }
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E8))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Publication Guard", fontWeight = FontWeight.Black, fontSize = 11.sp, color = GmuDark)
                    Text(
                        "Rundown dan departure info dari panel ini selalu DRAFT. Publish customer, perubahan status booking, dan transaksi keuangan tidak dilakukan otomatis.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            editingAction?.let { action ->
                ControlledActionEditor(
                    action = action,
                    forms = data.optJSONObject("forms") ?: JSONObject(),
                    onDismiss = { editingAction = null },
                    onPreview = { payload ->
                        scope.launch {
                            loading = true
                            message = null
                            error = null
                            runCatching {
                                api.preview(session.accessToken, selectedBookingId, action, payload)
                            }.onSuccess { result ->
                                previewResult = result
                                editingAction = null
                            }.onFailure {
                                error = it.message ?: "Preview gagal dibuat."
                            }
                            loading = false
                        }
                    }
                )
            }

            previewResult?.let { result ->
                val requestId = result.optString("request_id")
                val confirmToken = result.optString("confirm_token")
                val preview = result.optJSONObject("preview") ?: JSONObject()
                AlertDialog(
                    onDismissRequest = { if (!committing) previewResult = null },
                    title = { Text("Preview perubahan") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(opsPreviewSummary(preview), fontSize = 11.sp, color = GmuDark)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Belum ada data produksi yang diubah. Confirm & Apply hanya berlaku untuk preview ini dan token akan kedaluwarsa.",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            if (committing) {
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(enabled = !committing, onClick = { previewResult = null }) { Text("Batal") }
                    },
                    confirmButton = {
                        Button(
                            enabled = !committing && requestId.isNotBlank() && confirmToken.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    committing = true
                                    error = null
                                    runCatching { api.commit(session.accessToken, requestId, confirmToken) }
                                        .onSuccess {
                                            message = "Perubahan berhasil diterapkan dan tercatat di audit log."
                                            previewResult = null
                                            reloadKey++
                                        }
                                        .onFailure { error = it.message ?: "Commit gagal." }
                                    committing = false
                                }
                            }
                        ) { Text("Confirm & Apply") }
                    }
                )
            }
        }
    }
}

@Composable
private fun ControlledActionCard(
    action: String,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp)) {
            Surface(shape = RoundedCornerShape(13.dp), color = GmuSoft) {
                Icon(
                    opsActionIcon(action),
                    contentDescription = null,
                    tint = if (enabled) GmuGreen else Color.Gray,
                    modifier = Modifier.size(38.dp).padding(8.dp)
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (enabled) GmuDark else Color.Gray)
            Text(if (enabled) "Preview →" else "Locked", fontSize = 9.sp, color = if (enabled) GmuGreen else Color.Gray)
        }
    }
}

@Composable
private fun ControlledActionEditor(
    action: String,
    forms: JSONObject,
    onDismiss: () -> Unit,
    onPreview: (JSONObject) -> Unit
) {
    val form = forms.optJSONObject(action) ?: JSONObject()
    var validationError by remember(action) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(opsActionLabel(action)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Isi data lalu buat preview. Belum ada perubahan produksi pada tahap ini.", fontSize = 10.sp, color = Color.Gray)
                Spacer(Modifier.height(10.dp))
                when (action) {
                    "ASSIGN_CREW" -> AssignCrewEditor(form, onPreview = onPreview, onError = { validationError = it })
                    "UPDATE_OPERATION_SHEET" -> OperationSheetEditor(form, onPreview = onPreview, onError = { validationError = it })
                    "DRAFT_RUNDOWN" -> RundownEditor(onPreview = onPreview, onError = { validationError = it })
                    "PREPARE_DEPARTURE_INFO" -> DepartureInfoEditor(form, onPreview = onPreview, onError = { validationError = it })
                    "REFRESH_DOCUMENT_CHECKLIST" -> {
                        Text("Sistem akan mengambil snapshot terbaru dari Crew, Operation Sheet, Rundown, Departure Info, dan Trip Documents.", fontSize = 11.sp, color = GmuDark)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onPreview(JSONObject()) }, modifier = Modifier.fillMaxWidth()) { Text("Buat Preview Checklist") }
                    }
                }
                validationError?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = GmuDanger, fontSize = 10.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun AssignCrewEditor(
    form: JSONObject,
    onPreview: (JSONObject) -> Unit,
    onError: (String) -> Unit
) {
    val current = form.optJSONObject("current") ?: JSONObject()
    val tlOptions = form.optJSONArray("tl_options").toOpsOptions()
    val opsOptions = form.optJSONArray("operation_pic_options").toOpsOptions()
    var tlId by remember(form.toString()) { mutableStateOf(jsonText(current, "tl_id")) }
    var opsId by remember(form.toString()) { mutableStateOf(jsonText(current, "operation_pic_id")) }

    OpsOptionSelector("Tour Leader", tlOptions, tlId) { tlId = it }
    Spacer(Modifier.height(8.dp))
    OpsOptionSelector("Operation PIC", opsOptions, opsId) { opsId = it }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = {
            if (tlId.isBlank() || opsId.isBlank()) {
                onError("Pilih Tour Leader dan Operation PIC.")
            } else {
                onPreview(JSONObject().put("tl_id", tlId).put("operation_pic_id", opsId))
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Preview Assign Crew") }
}

@Composable
private fun OperationSheetEditor(
    form: JSONObject,
    onPreview: (JSONObject) -> Unit,
    onError: (String) -> Unit
) {
    var meetingTime by remember(form.toString()) { mutableStateOf(jsonText(form, "meeting_time")) }
    var readiness by remember(form.toString()) { mutableStateOf(jsonText(form, "readiness_status").ifBlank { "Draft" }) }
    var transport by remember(form.toString()) { mutableStateOf(jsonText(form, "transport")) }
    var driverContact by remember(form.toString()) { mutableStateOf(jsonText(form, "driver_contact")) }
    var operationPic by remember(form.toString()) { mutableStateOf(jsonText(form, "operation_pic")) }
    var emergencyContact by remember(form.toString()) { mutableStateOf(jsonText(form, "emergency_contact")) }
    var equipment by remember(form.toString()) { mutableStateOf(jsonText(form, "equipment")) }
    var briefingNotes by remember(form.toString()) { mutableStateOf(jsonText(form, "briefing_notes")) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = readiness.equals("Draft", true), onClick = { readiness = "Draft" }, label = { Text("Draft") })
        FilterChip(selected = readiness.equals("Ready", true), onClick = { readiness = "Ready" }, label = { Text("Ready") })
    }
    Spacer(Modifier.height(8.dp))
    OpsField("Meeting time (HH:mm)", meetingTime) { meetingTime = it }
    OpsField("Transport", transport) { transport = it }
    OpsField("Driver contact", driverContact) { driverContact = it }
    OpsField("Operation PIC", operationPic) { operationPic = it }
    OpsField("Emergency contact", emergencyContact) { emergencyContact = it }
    OpsField("Equipment", equipment) { equipment = it }
    OpsField("Briefing notes", briefingNotes, singleLine = false) { briefingNotes = it }
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = {
            if (readiness.equals("Ready", true) && (meetingTime.isBlank() || operationPic.isBlank() || emergencyContact.isBlank())) {
                onError("Status Ready membutuhkan meeting time, Operation PIC, dan emergency contact.")
            } else {
                onPreview(
                    JSONObject()
                        .put("meeting_time", meetingTime)
                        .put("readiness_status", readiness)
                        .put("transport", transport)
                        .put("driver_contact", driverContact)
                        .put("operation_pic", operationPic)
                        .put("emergency_contact", emergencyContact)
                        .put("equipment", equipment)
                        .put("briefing_notes", briefingNotes)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Preview Operation Sheet") }
}

@Composable
private fun RundownEditor(
    onPreview: (JSONObject) -> Unit,
    onError: (String) -> Unit
) {
    var lines by remember { mutableStateOf("") }
    Text("Satu baris per aktivitas. Format: HH:mm | Aktivitas | Lokasi | PIC", fontSize = 10.sp, color = Color.Gray)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = lines,
        onValueChange = { lines = it },
        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
        placeholder = { Text("06:30 | Kumpul peserta | Sekolah | TL\n07:00 | Berangkat | Sekolah | Operation") },
        minLines = 5
    )
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = {
            val parsed = runCatching { parseRundownLines(lines) }
            parsed.onSuccess { items ->
                if (items.length() == 0) onError("Tambahkan minimal satu aktivitas rundown.")
                else onPreview(JSONObject().put("items", items))
            }.onFailure { onError(it.message ?: "Format rundown tidak valid.") }
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Preview Draft Rundown") }
}

@Composable
private fun DepartureInfoEditor(
    form: JSONObject,
    onPreview: (JSONObject) -> Unit,
    onError: (String) -> Unit
) {
    var meetingPoint by remember(form.toString()) { mutableStateOf(jsonText(form, "meeting_point")) }
    var meetingTime by remember(form.toString()) { mutableStateOf(jsonText(form, "meeting_time")) }
    var instruction by remember(form.toString()) { mutableStateOf(jsonText(form, "departure_instruction")) }
    var bring by remember(form.toString()) { mutableStateOf(jsonText(form, "what_to_bring")) }
    var notes by remember(form.toString()) { mutableStateOf(jsonText(form, "customer_notes")) }
    var contactLabel by remember(form.toString()) { mutableStateOf(jsonText(form, "public_contact_label").ifBlank { "GMU EduTrans" }) }
    var contactPhone by remember(form.toString()) { mutableStateOf(jsonText(form, "public_contact_phone").ifBlank { "+62 877-8390-6545" }) }

    OpsField("Meeting point", meetingPoint) { meetingPoint = it }
    OpsField("Meeting time (HH:mm)", meetingTime) { meetingTime = it }
    OpsField("Departure instruction", instruction, singleLine = false) { instruction = it }
    OpsField("What to bring", bring, singleLine = false) { bring = it }
    OpsField("Customer notes", notes, singleLine = false) { notes = it }
    OpsField("Public contact label", contactLabel) { contactLabel = it }
    OpsField("Public contact phone", contactPhone) { contactPhone = it }
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = {
            if (meetingPoint.isBlank() || meetingTime.isBlank()) {
                onError("Meeting point dan meeting time wajib diisi.")
            } else {
                onPreview(
                    JSONObject()
                        .put("meeting_point", meetingPoint)
                        .put("meeting_time", meetingTime)
                        .put("departure_instruction", instruction)
                        .put("what_to_bring", bring)
                        .put("customer_notes", notes)
                        .put("public_contact_label", contactLabel)
                        .put("public_contact_phone", contactPhone)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Preview Departure Draft") }
}

@Composable
private fun OpsField(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2
    )
}

private data class OpsPersonOption(val id: String, val name: String, val role: String)

@Composable
private fun OpsOptionSelector(
    label: String,
    options: List<OpsPersonOption>,
    selectedId: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    Column {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let { "${it.name} • ${it.role}" } ?: "Pilih $label", modifier = Modifier.weight(1f))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option.name} • ${option.role}") },
                        onClick = {
                            onSelected(option.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OpsInfoCard(text: String, danger: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (danger) Color(0xFFFFEDEC) else Color(0xFFEAF7EF))
    ) {
        Text(text, modifier = Modifier.padding(12.dp), fontSize = 10.sp, color = if (danger) GmuDanger else GmuDark)
    }
}

private fun opsActionIcon(action: String): ImageVector = when (action) {
    "ASSIGN_CREW" -> Icons.Rounded.Groups
    "UPDATE_OPERATION_SHEET" -> Icons.Rounded.Badge
    "DRAFT_RUNDOWN" -> Icons.Rounded.Description
    "PREPARE_DEPARTURE_INFO" -> Icons.Rounded.Luggage
    "REFRESH_DOCUMENT_CHECKLIST" -> Icons.Rounded.CheckCircle
    else -> Icons.Rounded.AutoAwesome
}

private fun opsActionLabel(action: String): String = when (action) {
    "ASSIGN_CREW" -> "Assign Crew"
    "UPDATE_OPERATION_SHEET" -> "Update Operation Sheet"
    "DRAFT_RUNDOWN" -> "Draft Rundown"
    "PREPARE_DEPARTURE_INFO" -> "Prepare Departure Info"
    "REFRESH_DOCUMENT_CHECKLIST" -> "Refresh Document Checklist"
    else -> action
}

private fun opsPreviewSummary(preview: JSONObject): String {
    val action = preview.optString("action", "Controlled Action")
    val writes = preview.optJSONArray("writes")?.let { arr ->
        buildList {
            for (i in 0 until arr.length()) add(arr.optString(i))
        }.joinToString(", ")
    }.orEmpty()
    val itemCount = preview.optInt("item_count", 0)
    val proposed = preview.optJSONObject("proposed")
    return buildString {
        append("Aksi: ").append(opsActionLabel(action)).append("\n")
        if (writes.isNotBlank()) append("Akan menulis: ").append(writes).append("\n")
        if (itemCount > 0) append("Jumlah item: ").append(itemCount).append("\n")
        append("Customer visible change: Tidak\n")
        append("Booking status change: Tidak")
        if (proposed != null) {
            append("\n\nProposed:\n")
            proposed.keys().forEach { key ->
                val value = proposed.opt(key)
                if (value != null && value != JSONObject.NULL) append("• ").append(key.replace('_', ' ')).append(": ").append(value).append("\n")
            }
        }
    }.trim()
}

private fun parseRundownLines(raw: String): JSONArray {
    val items = JSONArray()
    raw.lines().map { it.trim() }.filter { it.isNotBlank() }.take(50).forEach { line ->
        val parts = line.split('|').map { it.trim() }
        val timeLike = parts.firstOrNull()?.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$")) == true
        val time = if (timeLike) parts[0] else ""
        val activityIndex = if (timeLike) 1 else 0
        val activity = parts.getOrNull(activityIndex).orEmpty()
        if (activity.isBlank()) throw IllegalArgumentException("Setiap baris wajib memiliki aktivitas.")
        items.put(
            JSONObject()
                .put("activity_time", time)
                .put("activity", activity)
                .put("location", parts.getOrNull(activityIndex + 1).orEmpty())
                .put("pic", parts.getOrNull(activityIndex + 2).orEmpty())
                .put("notes", parts.drop(activityIndex + 3).joinToString(" | "))
        )
    }
    return items
}

private fun JSONArray?.toOpsOptions(): List<OpsPersonOption> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val x = optJSONObject(i) ?: continue
            val id = x.optString("id")
            if (id.isNotBlank()) add(OpsPersonOption(id, x.optString("name", "Staff"), x.optString("role", "")))
        }
    }
}

private fun jsonText(o: JSONObject, key: String): String {
    if (!o.has(key) || o.isNull(key)) return ""
    return o.optString(key, "").takeUnless { it == "null" } ?: ""
}

private class ManagerOpsControlApi {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun controlPanel(accessToken: String, bookingId: String): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("action", "CONTROL_PANEL").put("booking_id", bookingId)
        resultOf(post(accessToken, payload))
    }

    suspend fun preview(accessToken: String, bookingId: String, controlledAction: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("action", "PREVIEW_ACTION")
            .put("booking_id", bookingId)
            .put("controlled_action", controlledAction)
            .put("payload", payload)
        resultOf(post(accessToken, body))
    }

    suspend fun commit(accessToken: String, requestId: String, confirmToken: String): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("action", "COMMIT_ACTION")
            .put("request_id", requestId)
            .put("confirm_token", confirmToken)
        resultOf(post(accessToken, body))
    }

    private fun resultOf(root: JSONObject): JSONObject {
        return root.optJSONObject("result")
            ?: throw IllegalStateException(root.optString("error", "Manager Ops Agent gagal diproses."))
    }

    private fun post(accessToken: String, payload: JSONObject): JSONObject {
        val connection = URL(base.trimEnd('/') + "/functions/v1/internal-manager-ops-agent").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 25_000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("apikey", key)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = BufferedReader(InputStreamReader(stream ?: throw IllegalStateException("Respons server kosong."))).use { it.readText() }
        connection.disconnect()
        val root = JSONObject(body)
        if (code !in 200..299) throw IllegalStateException(root.optString("error", "HTTP $code"))
        return root
    }
}
