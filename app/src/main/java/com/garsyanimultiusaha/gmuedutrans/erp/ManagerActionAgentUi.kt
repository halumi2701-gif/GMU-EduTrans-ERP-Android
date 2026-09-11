package com.garsyanimultiusaha.gmuedutrans.erp

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * Adds an execution layer on top of the existing Manager Ops Agent.
 * The agent never writes operational records before a Manager reviews and confirms the draft.
 */
@Composable
fun GmuNativeAppWithManagerActionAgent(vm: MainViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithManagerAgent(vm)

        val session = (vm.state as? AppState.LoggedIn)?.session
        if (
            session != null &&
            ErpRoles.isManagerEduTrans(session.profile.role) &&
            vm.currentPage == AppPage.DASHBOARD
        ) {
            ManagerActionAgentButton(vm = vm, session = session)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.ManagerActionAgentButton(vm: MainViewModel, session: SessionState) {
    var opened by remember { mutableStateOf(false) }

    ExtendedFloatingActionButton(
        onClick = { opened = true },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 20.dp, bottom = 166.dp),
        containerColor = GmuGreen,
        contentColor = Color.White,
        icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
        text = { Text("Action Agent", fontWeight = FontWeight.Bold) }
    )

    if (opened) {
        ModalBottomSheet(
            onDismissRequest = { opened = false },
            containerColor = GmuBg,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            ManagerActionAgentSheet(
                vm = vm,
                session = session,
                onClose = { opened = false }
            )
        }
    }
}

@Composable
private fun ManagerActionAgentSheet(
    vm: MainViewModel,
    session: SessionState,
    onClose: () -> Unit
) {
    val api = remember { SupabaseApi() }
    val scope = rememberCoroutineScope()
    val activeBookings = vm.bookings
        .filter { it.status !in listOf("Completed", "Closed") }
        .sortedBy { it.tripDate }

    var selectedId by remember(activeBookings) { mutableStateOf(activeBookings.firstOrNull()?.id.orEmpty()) }
    val selectedBooking = activeBookings.firstOrNull { it.id == selectedId }
    var bookingMenu by remember { mutableStateOf(false) }

    var profiles by remember { mutableStateOf<List<ErpRow>>(emptyList()) }
    var rundownRows by remember { mutableStateOf<List<ErpRow>>(emptyList()) }
    var operationSheets by remember { mutableStateOf<List<ErpRow>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<ErpRow>>(emptyList()) }
    var loadingContext by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<ManagerActionDraft?>(null) }
    var message by remember { mutableStateOf("Pilih trip, lalu minta agent menyiapkan draft.") }

    fun refreshContext() {
        if (loadingContext) return
        loadingContext = true
        scope.launch {
            try {
                profiles = runCatching { api.getRows(session.accessToken, "profiles", "created_at.desc") }.getOrElse { emptyList() }
                rundownRows = runCatching { api.getRows(session.accessToken, "rundown_items", null) }.getOrElse { emptyList() }
                operationSheets = runCatching { api.getRows(session.accessToken, "operation_sheets", "updated_at.desc") }.getOrElse { emptyList() }
                assignments = runCatching { api.getRows(session.accessToken, "staff_assignments", "created_at.desc") }.getOrElse { emptyList() }
                message = "Konteks operasional diperbarui."
            } finally {
                loadingContext = false
            }
        }
    }

    LaunchedEffect(session.accessToken) { refreshContext() }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 38.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = GmuDark) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = GmuGold,
                    modifier = Modifier.size(54.dp).padding(13.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("GMU EduTrans Action Agent", fontSize = 20.sp, fontWeight = FontWeight.Black, color = GmuDark)
                Text("Draft → Review Manager → Confirm → ERP", fontSize = 11.sp, color = Color.Gray)
            }
            TextButton(onClick = onClose) { Text("Tutup") }
        }

        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp)) {
                Text("Trip yang dikerjakan", fontWeight = FontWeight.Black, color = GmuDark)
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { bookingMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        val label = selectedBooking?.let { "${it.bookingNo} • ${it.programName}" } ?: "Belum ada trip aktif"
                        Text(label, maxLines = 1)
                    }
                    DropdownMenu(expanded = bookingMenu, onDismissRequest = { bookingMenu = false }) {
                        activeBookings.forEach { booking ->
                            DropdownMenuItem(
                                text = { Text("${booking.bookingNo} • ${booking.programName}") },
                                onClick = {
                                    selectedId = booking.id
                                    bookingMenu = false
                                    draft = null
                                    message = "Trip dipilih. Pilih aksi yang ingin dibuat."
                                }
                            )
                        }
                    }
                }
                if (selectedBooking != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${selectedBooking.customerName} • ${selectedBooking.tripDate} • ${selectedBooking.pax} pax",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Action Agent", fontWeight = FontWeight.Black, fontSize = 16.sp, color = GmuDark)
        Text("Agent hanya membuat draft. Data baru disimpan setelah Manager menekan konfirmasi.", fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AgentDraftButton(
                title = "Buat Rundown",
                icon = Icons.Rounded.Description,
                modifier = Modifier.weight(1f),
                enabled = selectedBooking != null && !loadingContext,
                onClick = {
                    val booking = selectedBooking ?: return@AgentDraftButton
                    draft = buildRundownActionDraft(booking, rundownRows)
                    message = draft?.message.orEmpty()
                }
            )
            AgentDraftButton(
                title = "Operation Sheet",
                icon = Icons.Rounded.Badge,
                modifier = Modifier.weight(1f),
                enabled = selectedBooking != null && !loadingContext,
                onClick = {
                    val booking = selectedBooking ?: return@AgentDraftButton
                    draft = buildOperationSheetActionDraft(booking, operationSheets, session.userId)
                    message = draft?.message.orEmpty()
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AgentDraftButton(
                title = "Susun Crew",
                icon = Icons.Rounded.Groups,
                modifier = Modifier.weight(1f),
                enabled = selectedBooking != null && !loadingContext,
                onClick = {
                    val booking = selectedBooking ?: return@AgentDraftButton
                    draft = buildCrewActionDraft(booking, profiles, assignments, session.userId)
                    message = draft?.message.orEmpty()
                }
            )
            AgentDraftButton(
                title = "Siapkan Trip",
                icon = Icons.Rounded.Luggage,
                modifier = Modifier.weight(1f),
                enabled = selectedBooking != null && !loadingContext,
                onClick = {
                    val booking = selectedBooking ?: return@AgentDraftButton
                    draft = buildPrepareTripActionDraft(
                        booking = booking,
                        rundownRows = rundownRows,
                        operationSheets = operationSheets,
                        profiles = profiles,
                        assignments = assignments,
                        userId = session.userId
                    )
                    message = draft?.message.orEmpty()
                }
            )
        }

        Spacer(Modifier.height(14.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E8))
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = GmuGreen)
                Spacer(Modifier.width(10.dp))
                Text(message, modifier = Modifier.weight(1f), fontSize = 11.sp, color = GmuDark)
                if (loadingContext) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        draft?.let { currentDraft ->
            Spacer(Modifier.height(14.dp))
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Review Draft", fontWeight = FontWeight.Black, fontSize = 16.sp, color = GmuDark)
                    Text(currentDraft.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GmuGreen)
                    Spacer(Modifier.height(10.dp))
                    currentDraft.previewLines.forEach { line ->
                        Text("• $line", fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    if (currentDraft.mutations.isEmpty()) {
                        Text(
                            "Tidak ada data baru yang perlu dibuat. Agent mencegah duplikasi.",
                            fontSize = 11.sp,
                            color = GmuWarn,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            "${currentDraft.mutations.size} perubahan akan ditulis ke ERP setelah konfirmasi Manager.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (applying) return@Button
                                applying = true
                                scope.launch {
                                    try {
                                        currentDraft.mutations.forEach { mutation ->
                                            when (mutation) {
                                                is AgentMutation.Insert -> api.insertRow(
                                                    session.accessToken,
                                                    mutation.table,
                                                    mutation.values
                                                )
                                                is AgentMutation.Update -> api.updateRow(
                                                    session.accessToken,
                                                    mutation.table,
                                                    mutation.id,
                                                    mutation.values
                                                )
                                            }
                                        }
                                        api.audit(
                                            session.accessToken,
                                            session.userId,
                                            "AI_AGENT_CONFIRM",
                                            "operations",
                                            selectedBooking?.id.orEmpty(),
                                            "Manager confirmed ${currentDraft.title} via GMU EduTrans Action Agent"
                                        )
                                        message = "Berhasil: ${currentDraft.title} tersimpan setelah konfirmasi Manager."
                                        draft = null
                                        vm.loadAll()
                                        refreshContext()
                                    } catch (e: Exception) {
                                        message = "Gagal menyimpan draft: ${e.message ?: "unknown error"}"
                                    } finally {
                                        applying = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !applying,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (applying) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (applying) "Menyimpan…" else "Konfirmasi & Simpan ke ERP")
                        }
                        TextButton(
                            onClick = { draft = null; message = "Draft dibatalkan. Tidak ada data yang diubah." },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !applying
                        ) {
                            Text("Batalkan Draft")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        TextButton(onClick = { refreshContext() }, enabled = !loadingContext) {
            Text("Refresh konteks Agent")
        }
    }
}

@Composable
private fun AgentDraftButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean,
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
            Icon(icon, contentDescription = null, tint = GmuGreen)
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black, color = GmuDark)
            Text("Buat draft →", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

private data class ManagerActionDraft(
    val title: String,
    val message: String,
    val previewLines: List<String>,
    val mutations: List<AgentMutation>
)

private sealed interface AgentMutation {
    val table: String

    data class Insert(
        override val table: String,
        val values: Map<String, Any?>
    ) : AgentMutation

    data class Update(
        override val table: String,
        val id: String,
        val values: Map<String, Any?>
    ) : AgentMutation
}

private fun buildRundownActionDraft(booking: Booking, rows: List<ErpRow>): ManagerActionDraft {
    val existing = rows.filter { it.text("booking_id") == booking.id }
    if (existing.isNotEmpty()) {
        return ManagerActionDraft(
            title = "Rundown • ${booking.bookingNo}",
            message = "Rundown sudah tersedia. Agent tidak membuat duplikat.",
            previewLines = listOf("${existing.size} item rundown sudah tersimpan", "Buka Trip Operation untuk review/edit item yang ada"),
            mutations = emptyList()
        )
    }

    val meeting = booking.meetingPoint.ifBlank { "Titik kumpul sesuai booking" }
    val plan = listOf(
        Triple("06:30", "Registrasi peserta & briefing keberangkatan", meeting),
        Triple("07:00", "Keberangkatan", meeting),
        Triple("08:30", "Kedatangan & safety briefing", "Lokasi kegiatan"),
        Triple("09:00", "Sesi edukasi utama", "Lokasi kegiatan"),
        Triple("10:30", "Istirahat & refreshment", "Area kegiatan"),
        Triple("11:00", "Sesi edukasi lanjutan / eksplorasi", "Lokasi kegiatan"),
        Triple("12:30", "Penutupan, evaluasi singkat & persiapan pulang", "Lokasi kegiatan"),
        Triple("13:00", "Perjalanan kembali", "Titik kepulangan")
    )
    val mutations = plan.map { (time, activity, location) ->
        AgentMutation.Insert(
            table = "rundown_items",
            values = mapOf(
                "booking_id" to booking.id,
                "activity_time" to time,
                "activity" to activity,
                "location" to location,
                "pic" to "Manager/TL"
            )
        )
    }
    return ManagerActionDraft(
        title = "Draft Rundown • ${booking.bookingNo}",
        message = "Draft rundown dibuat. Review waktunya sebelum disimpan.",
        previewLines = plan.map { "${it.first} • ${it.second} • ${it.third}" },
        mutations = mutations
    )
}

private fun buildOperationSheetActionDraft(
    booking: Booking,
    rows: List<ErpRow>,
    userId: String
): ManagerActionDraft {
    val existing = rows.firstOrNull { it.text("booking_id") == booking.id }
    if (existing != null) {
        return ManagerActionDraft(
            title = "Operation Sheet • ${booking.bookingNo}",
            message = "Operation Sheet sudah tersedia. Agent tidak menimpa data existing.",
            previewLines = listOf("Operation Sheet existing terdeteksi", "Gunakan Trip Operation untuk perubahan detail"),
            mutations = emptyList()
        )
    }

    val transport = when {
        booking.programName.contains("kereta", true) || booking.programName.contains("stasiun", true) -> "Kereta / transport terkonfirmasi"
        else -> "Transport sesuai booking / vendor"
    }
    val equipment = listOfNotNull(
        "Manifest & absensi",
        "P3K",
        "HT/komunikasi",
        "Dokumen vendor",
        booking.specialRequirements.takeIf { it.isNotBlank() }?.let { "Kebutuhan khusus: $it" }
    ).joinToString(", ")

    return ManagerActionDraft(
        title = "Draft Operation Sheet • ${booking.bookingNo}",
        message = "Operation Sheet draft siap direview Manager.",
        previewLines = listOf(
            "Meeting 06:30 • ${booking.meetingPoint.ifBlank { "sesuai booking" }}",
            "Transport: $transport",
            "Operation PIC: Manager EduTrans",
            "Equipment: $equipment"
        ),
        mutations = listOf(
            AgentMutation.Insert(
                table = "operation_sheets",
                values = mapOf(
                    "booking_id" to booking.id,
                    "meeting_time" to "06:30",
                    "transport" to transport,
                    "operation_pic" to "Manager EduTrans",
                    "equipment" to equipment,
                    "updated_by" to userId
                )
            )
        )
    )
}

private fun buildCrewActionDraft(
    booking: Booking,
    profiles: List<ErpRow>,
    assignments: List<ErpRow>,
    userId: String
): ManagerActionDraft {
    val alreadyAssigned = assignments.filter {
        it.text("title").contains(booking.bookingNo, true) && it.text("status") !in listOf("Cancelled", "Done", "Completed")
    }
    if (alreadyAssigned.isNotEmpty()) {
        return ManagerActionDraft(
            title = "Crew Plan • ${booking.bookingNo}",
            message = "Crew assignment untuk booking ini sudah ditemukan.",
            previewLines = alreadyAssigned.map { it.text("title") + " • " + it.text("status") },
            mutations = emptyList()
        )
    }

    val active = profiles.filter { it.text("is_active") != "false" }
    val tl = active.firstOrNull { it.text("role").equals("TL", true) }
    val operation = active.firstOrNull { it.text("role").equals("Operation", true) }
    val selected = listOfNotNull(tl, operation).distinctBy { it.id }

    if (selected.isEmpty()) {
        return ManagerActionDraft(
            title = "Crew Plan • ${booking.bookingNo}",
            message = "Belum ada staff TL/Operation aktif yang dapat direkomendasikan.",
            previewLines = listOf("Tambahkan/aktifkan staff TL atau Operation terlebih dahulu"),
            mutations = emptyList()
        )
    }

    val mutations = selected.map { staff ->
        val role = staff.text("role").ifBlank { "Crew" }
        AgentMutation.Insert(
            table = "staff_assignments",
            values = mapOf(
                "staff_id" to staff.id,
                "title" to "$role • ${booking.bookingNo} • ${booking.programName}",
                "due_date" to booking.tripDate,
                "status" to "Assigned",
                "priority" to "High",
                "notes" to "Trip ${booking.customerName} • ${booking.pax} pax • disusun oleh GMU EduTrans Action Agent",
                "assigned_by" to userId
            )
        )
    }

    return ManagerActionDraft(
        title = "Draft Crew Plan • ${booking.bookingNo}",
        message = "Agent memilih staff aktif berdasarkan role TL/Operation. Review sebelum simpan.",
        previewLines = selected.map { "${it.text("full_name")} • ${it.text("role")}" },
        mutations = mutations
    )
}

private fun buildPrepareTripActionDraft(
    booking: Booking,
    rundownRows: List<ErpRow>,
    operationSheets: List<ErpRow>,
    profiles: List<ErpRow>,
    assignments: List<ErpRow>,
    userId: String
): ManagerActionDraft {
    val rundown = buildRundownActionDraft(booking, rundownRows)
    val sheet = buildOperationSheetActionDraft(booking, operationSheets, userId)
    val crew = buildCrewActionDraft(booking, profiles, assignments, userId)
    val mutations = rundown.mutations + sheet.mutations + crew.mutations
    val preview = buildList {
        add("Rundown: ${if (rundown.mutations.isEmpty()) "sudah ada / tidak dibuat" else "${rundown.mutations.size} item draft"}")
        add("Operation Sheet: ${if (sheet.mutations.isEmpty()) "sudah ada / tidak dibuat" else "draft siap"}")
        add("Crew: ${if (crew.mutations.isEmpty()) "existing / belum tersedia" else "${crew.mutations.size} assignment draft"}")
        add("Semua perubahan tetap membutuhkan konfirmasi Manager")
    }
    return ManagerActionDraft(
        title = "Prepare Trip • ${booking.bookingNo}",
        message = if (mutations.isEmpty()) "Tidak ada draft baru; data utama sudah tersedia atau belum memenuhi prasyarat." else "Paket persiapan trip siap direview.",
        previewLines = preview,
        mutations = mutations
    )
}
