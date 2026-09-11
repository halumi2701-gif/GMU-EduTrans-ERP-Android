package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Send
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Natural-language command layer for Manager EduTrans.
 * The agent resolves a trip, prepares a safe operational draft, then requires
 * one explicit Manager confirmation before any mutation is written to ERP.
 */
@Composable
fun GmuNativeAppWithManagerNaturalAgent(vm: MainViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithManagerActionAgent(vm)

        val session = (vm.state as? AppState.LoggedIn)?.session
        if (
            session != null &&
            ErpRoles.isManagerEduTrans(session.profile.role) &&
            vm.currentPage == AppPage.DASHBOARD
        ) {
            ManagerNaturalCommandButton(vm = vm, session = session)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.ManagerNaturalCommandButton(vm: MainViewModel, session: SessionState) {
    var opened by remember { mutableStateOf(false) }

    ExtendedFloatingActionButton(
        onClick = { opened = true },
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 20.dp, bottom = 166.dp),
        containerColor = GmuDark,
        contentColor = Color.White,
        icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = GmuGold) },
        text = { Text("AI Command", fontWeight = FontWeight.Bold) }
    )

    if (opened) {
        ModalBottomSheet(
            onDismissRequest = { opened = false },
            containerColor = GmuBg,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            ManagerNaturalCommandSheet(vm = vm, session = session)
        }
    }
}

@Composable
private fun ManagerNaturalCommandSheet(vm: MainViewModel, session: SessionState) {
    val api = remember { SupabaseApi() }
    val scope = rememberCoroutineScope()
    val activeBookings = vm.bookings
        .filter { it.status !in listOf("Completed", "Closed") }
        .sortedBy { it.tripDate }

    var command by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Ketik perintah operasional. Contoh: Siapkan trip SMP ABC hari Senin.") }
    var pendingAction by remember { mutableStateOf(NaturalAgentAction.UNKNOWN) }
    var candidates by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var resolvedBooking by remember { mutableStateOf<Booking?>(null) }
    var draft by remember { mutableStateOf<NaturalCommandDraft?>(null) }
    var loadingContext by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }

    var profiles by remember { mutableStateOf<List<ErpRow>>(emptyList()) }
    var rundownRows by remember { mutableStateOf<List<ErpRow>>(emptyList()) }
    var operationSheets by remember { mutableStateOf<List<ErpRow>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<ErpRow>>(emptyList()) }
    var vendorPos by remember { mutableStateOf<List<ErpRow>>(emptyList()) }
    var manifests by remember { mutableStateOf<List<ErpRow>>(emptyList()) }
    var documents by remember { mutableStateOf<List<ErpRow>>(emptyList()) }
    var tripCosts by remember { mutableStateOf<List<ErpRow>>(emptyList()) }

    fun refreshContext() {
        if (loadingContext) return
        loadingContext = true
        scope.launch {
            try {
                profiles = runCatching { api.getRows(session.accessToken, "profiles", "created_at.desc") }.getOrElse { emptyList() }
                rundownRows = runCatching { api.getRows(session.accessToken, "rundown_items", null) }.getOrElse { emptyList() }
                operationSheets = runCatching { api.getRows(session.accessToken, "operation_sheets", "updated_at.desc") }.getOrElse { emptyList() }
                assignments = runCatching { api.getRows(session.accessToken, "staff_assignments", "created_at.desc") }.getOrElse { emptyList() }
                vendorPos = runCatching { api.getRows(session.accessToken, "vendor_pos", "created_at.desc") }.getOrElse { emptyList() }
                manifests = runCatching { api.getRows(session.accessToken, "manifests", null) }.getOrElse { emptyList() }
                documents = runCatching { api.getRows(session.accessToken, "documents", "generated_at.desc") }.getOrElse { emptyList() }
                tripCosts = runCatching { api.getRows(session.accessToken, "trip_costs", "created_at.desc") }.getOrElse { emptyList() }
            } finally {
                loadingContext = false
            }
        }
    }

    fun buildFor(booking: Booking, action: NaturalAgentAction) {
        resolvedBooking = booking
        candidates = emptyList()
        draft = buildNaturalCommandDraft(
            action = action,
            booking = booking,
            rundownRows = rundownRows,
            operationSheets = operationSheets,
            profiles = profiles,
            assignments = assignments,
            vendorPos = vendorPos,
            manifests = manifests,
            documents = documents,
            tripCosts = tripCosts,
            userId = session.userId
        )
        message = draft?.message.orEmpty()
    }

    fun processCommand() {
        val interpreted = interpretNaturalManagerCommand(command, activeBookings)
        pendingAction = interpreted.action
        draft = null
        resolvedBooking = interpreted.resolved
        candidates = interpreted.candidates
        message = interpreted.message
        interpreted.resolved?.let { buildFor(it, interpreted.action) }
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
                Text("GMU EduTrans Natural Command", fontSize = 20.sp, fontWeight = FontWeight.Black, color = GmuDark)
                Text("Perintah biasa → cari trip → draft → 1x konfirmasi", fontSize = 11.sp, color = Color.Gray)
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp)) {
                Text("Perintahkan Ops Agent", fontWeight = FontWeight.Black, color = GmuDark)
                Text(
                    "Bisa mengenali customer/sekolah, program, nomor booking, hari ini, besok, lusa, atau nama hari.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Siapkan semua kebutuhan trip SMP ABC hari Senin") },
                    trailingIcon = {
                        IconButton(onClick = { processCommand() }, enabled = command.isNotBlank() && !loadingContext) {
                            Icon(Icons.Rounded.Send, contentDescription = "Proses")
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    minLines = 2,
                    maxLines = 4
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NaturalExampleChip("Siapkan trip besok") { command = "Siapkan trip besok" }
                    NaturalExampleChip("Cek kesiapan") { command = "Cek kesiapan trip terdekat" }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { processCommand() },
                    enabled = command.isNotBlank() && !loadingContext,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (loadingContext) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Proses Perintah")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E8))
        ) {
            Text(message, modifier = Modifier.padding(14.dp), fontSize = 11.sp, color = GmuDark)
        }

        if (candidates.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Pilih trip yang dimaksud", fontWeight = FontWeight.Black, fontSize = 16.sp, color = GmuDark)
            Text("Agent menemukan lebih dari satu kemungkinan.", fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            candidates.forEach { booking ->
                Card(
                    onClick = { buildFor(booking, pendingAction) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${booking.bookingNo} • ${booking.programName}", fontWeight = FontWeight.Black, color = GmuDark)
                        Text("${booking.customerName} • ${booking.tripDate} • ${booking.pax} pax", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }

        resolvedBooking?.let { booking ->
            Spacer(Modifier.height(14.dp))
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = GmuSoft)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Trip terpilih", fontWeight = FontWeight.Black, color = GmuDark)
                    Text("${booking.bookingNo} • ${booking.programName}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${booking.customerName} • ${booking.tripDate} • ${booking.pax} pax", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        draft?.let { currentDraft ->
            Spacer(Modifier.height(14.dp))
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Hasil Agent", fontWeight = FontWeight.Black, fontSize = 16.sp, color = GmuDark)
                    Text(currentDraft.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GmuGreen)
                    Spacer(Modifier.height(10.dp))
                    currentDraft.previewLines.forEach { line ->
                        Text("• $line", fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }

                    if (currentDraft.mutations.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "${currentDraft.mutations.size} perubahan siap ditulis. Tidak ada data yang berubah sebelum tombol di bawah ditekan.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (applying) return@Button
                                val booking = resolvedBooking ?: return@Button
                                applying = true
                                scope.launch {
                                    try {
                                        currentDraft.mutations.forEach { mutation ->
                                            api.insertRow(session.accessToken, mutation.table, mutation.values)
                                        }
                                        api.audit(
                                            session.accessToken,
                                            session.userId,
                                            "AI_AGENT_NATURAL_CONFIRM",
                                            "operations",
                                            booking.id,
                                            "Manager confirmed natural command: ${command.take(180)}"
                                        )
                                        message = "Berhasil. Perintah diterapkan setelah konfirmasi Manager dan dicatat di Audit Trail."
                                        draft = null
                                        vm.loadAll()
                                        refreshContext()
                                    } catch (e: Exception) {
                                        message = "Gagal menerapkan draft: ${e.message ?: "unknown error"}"
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
                            Text(if (applying) "Menerapkan…" else "Konfirmasi Sekali & Terapkan")
                        }
                        TextButton(
                            onClick = {
                                draft = null
                                message = "Draft dibatalkan. Tidak ada perubahan data."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !applying
                        ) { Text("Batalkan") }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text("Ini hasil pemeriksaan/read-only atau tidak ada data baru yang aman dibuat otomatis.", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { refreshContext() }, enabled = !loadingContext) {
            Text("Refresh konteks Agent")
        }
    }
}

@Composable
private fun NaturalExampleChip(text: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(text, fontSize = 10.sp) })
}

private enum class NaturalAgentAction {
    PREPARE_TRIP,
    RUNDOWN,
    OPERATION_SHEET,
    CREW,
    CHECK_READINESS,
    CHECK_VENDOR,
    CHECK_RAB,
    UNKNOWN
}

private data class NaturalInterpretation(
    val action: NaturalAgentAction,
    val resolved: Booking?,
    val candidates: List<Booking>,
    val message: String
)

private data class NaturalCommandDraft(
    val title: String,
    val message: String,
    val previewLines: List<String>,
    val mutations: List<NaturalMutation>
)

private data class NaturalMutation(
    val table: String,
    val values: Map<String, Any?>
)

private fun interpretNaturalManagerCommand(command: String, bookings: List<Booking>): NaturalInterpretation {
    val normalized = normalizeNatural(command)
    val padded = " $normalized "
    val action = when {
        listOf("siapkan", "persiapkan", "lengkapi", "semua kebutuhan").any { normalized.contains(it) } -> NaturalAgentAction.PREPARE_TRIP
        normalized.contains("rundown") -> NaturalAgentAction.RUNDOWN
        normalized.contains("operation sheet") || normalized.contains("ops sheet") -> NaturalAgentAction.OPERATION_SHEET
        listOf("crew", "tour leader", " tl ", "susun tim", "assign tim").any { padded.contains(it) } -> NaturalAgentAction.CREW
        normalized.contains("vendor") || padded.contains(" po ") -> NaturalAgentAction.CHECK_VENDOR
        normalized.contains("rab") || normalized.contains("biaya") || normalized.contains("cost") -> NaturalAgentAction.CHECK_RAB
        normalized.contains("kesiapan") || normalized.contains("ready") || normalized.contains("siap belum") -> NaturalAgentAction.CHECK_READINESS
        else -> NaturalAgentAction.UNKNOWN
    }

    if (action == NaturalAgentAction.UNKNOWN) {
        return NaturalInterpretation(
            action,
            null,
            emptyList(),
            "Perintah belum dikenali. Coba: Siapkan trip..., Buat rundown..., Susun crew..., Cek vendor..., Cek RAB..., atau Cek kesiapan...."
        )
    }
    if (bookings.isEmpty()) {
        return NaturalInterpretation(action, null, emptyList(), "Tidak ada booking aktif yang bisa dikerjakan.")
    }

    val dateHint = resolveNaturalDateHint(normalized)
    val scored = bookings.map { booking -> booking to scoreNaturalBooking(normalized, dateHint, booking) }
        .sortedWith(compareByDescending<Pair<Booking, Int>> { it.second }.thenBy { it.first.tripDate })
    val top = scored.first()
    val second = scored.getOrNull(1)
    val hasUsefulTarget = top.second >= 30
    val uniqueEnough = second == null || top.second - second.second >= 15

    if (hasUsefulTarget && uniqueEnough) {
        return NaturalInterpretation(
            action,
            top.first,
            emptyList(),
            "Trip dikenali otomatis: ${top.first.bookingNo} • ${top.first.customerName}. Saya menyiapkan hasil untuk direview."
        )
    }
    if (bookings.size == 1) {
        return NaturalInterpretation(action, bookings.first(), emptyList(), "Hanya ada satu trip aktif. Agent menggunakan ${bookings.first().bookingNo}.")
    }

    val candidateList = scored.filter { it.second > 0 }.take(5).map { it.first }.ifEmpty { bookings.take(5) }
    return NaturalInterpretation(
        action,
        null,
        candidateList,
        if (dateHint != null) "Ada beberapa trip yang cocok dengan tanggal $dateHint. Pilih trip yang dimaksud." else "Agent belum yakin trip mana yang dimaksud. Pilih salah satu kandidat."
    )
}

private fun scoreNaturalBooking(command: String, dateHint: String?, booking: Booking): Int {
    var score = 0
    val bookingNo = normalizeNatural(booking.bookingNo)
    val customer = normalizeNatural(booking.customerName)
    val program = normalizeNatural(booking.programName)

    if (bookingNo.isNotBlank() && command.contains(bookingNo)) score += 220
    if (dateHint != null && booking.tripDate == dateHint) score += 120
    if (customer.length >= 4 && command.contains(customer)) score += 90
    if (program.length >= 4 && command.contains(program)) score += 70

    val stop = setOf(
        "siapkan", "persiapkan", "lengkapi", "semua", "kebutuhan", "trip", "hari", "buat", "cek",
        "kesiapan", "rundown", "crew", "tim", "vendor", "rab", "biaya", "besok", "lusa",
        "senin", "selasa", "rabu", "kamis", "jumat", "sabtu", "minggu", "terdekat"
    )
    val tokens = command.split(' ').filter { it.length >= 3 && it !in stop }.toSet()
    tokens.forEach { token ->
        if (customer.split(' ').contains(token)) score += 18
        if (program.split(' ').contains(token)) score += 12
        if (bookingNo.contains(token)) score += 25
    }
    return score
}

private fun normalizeNatural(value: String): String {
    val cleaned = value.lowercase(Locale("id", "ID")).map { c ->
        if (c.isLetterOrDigit() || c == '/' || c == '-' || c == ' ') c else ' '
    }.joinToString("")
    return cleaned.split(' ').filter { it.isNotBlank() }.joinToString(" ")
}

private fun resolveNaturalDateHint(text: String): String? {
    val tokens = text.split(' ').filter { it.isNotBlank() }
    tokens.firstOrNull { token ->
        token.length == 10 && token[4] == '-' && token[7] == '-' && token.filter { it.isDigit() }.length == 8
    }?.let { return it }

    tokens.firstOrNull { token ->
        (token.contains('/') || token.count { it == '-' } == 2) && token.length in 8..10
    }?.let { token ->
        val separator = if (token.contains('/')) '/' else '-'
        val parts = token.split(separator)
        if (parts.size == 3) {
            val day = parts[0].toIntOrNull()
            val month = parts[1].toIntOrNull()
            val year = parts[2].toIntOrNull()
            if (day != null && month != null && year != null && year >= 2000) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            }
        }
    }

    val cal = Calendar.getInstance()
    when {
        text.contains("hari ini") -> Unit
        text.contains("besok") -> cal.add(Calendar.DAY_OF_MONTH, 1)
        text.contains("lusa") -> cal.add(Calendar.DAY_OF_MONTH, 2)
        else -> {
            val dayMap = linkedMapOf(
                "minggu" to Calendar.SUNDAY,
                "senin" to Calendar.MONDAY,
                "selasa" to Calendar.TUESDAY,
                "rabu" to Calendar.WEDNESDAY,
                "kamis" to Calendar.THURSDAY,
                "jumat" to Calendar.FRIDAY,
                "sabtu" to Calendar.SATURDAY
            )
            val target = dayMap.entries.firstOrNull { text.contains(it.key) }?.value ?: return null
            var diff = (target - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
            if (diff == 0 && text.contains("depan")) diff = 7
            cal.add(Calendar.DAY_OF_MONTH, diff)
        }
    }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

private fun buildNaturalCommandDraft(
    action: NaturalAgentAction,
    booking: Booking,
    rundownRows: List<ErpRow>,
    operationSheets: List<ErpRow>,
    profiles: List<ErpRow>,
    assignments: List<ErpRow>,
    vendorPos: List<ErpRow>,
    manifests: List<ErpRow>,
    documents: List<ErpRow>,
    tripCosts: List<ErpRow>,
    userId: String
): NaturalCommandDraft = when (action) {
    NaturalAgentAction.RUNDOWN -> naturalRundownDraft(booking, rundownRows)
    NaturalAgentAction.OPERATION_SHEET -> naturalOperationSheetDraft(booking, operationSheets, userId)
    NaturalAgentAction.CREW -> naturalCrewDraft(booking, profiles, assignments, userId)
    NaturalAgentAction.CHECK_VENDOR -> naturalVendorCheck(booking, vendorPos)
    NaturalAgentAction.CHECK_RAB -> naturalRabCheck(booking, tripCosts)
    NaturalAgentAction.CHECK_READINESS -> naturalReadinessCheck(booking, rundownRows, operationSheets, assignments, vendorPos, manifests, documents, tripCosts)
    NaturalAgentAction.PREPARE_TRIP -> naturalPrepareTripDraft(
        booking, rundownRows, operationSheets, profiles, assignments, vendorPos, manifests, documents, tripCosts, userId
    )
    NaturalAgentAction.UNKNOWN -> NaturalCommandDraft("Perintah belum dikenali", "Tidak ada aksi.", emptyList(), emptyList())
}

private fun naturalRundownDraft(booking: Booking, rows: List<ErpRow>): NaturalCommandDraft {
    val existing = rows.filter { it.text("booking_id") == booking.id }
    if (existing.isNotEmpty()) {
        return NaturalCommandDraft(
            "Rundown • ${booking.bookingNo}",
            "Rundown sudah tersedia. Agent mencegah duplikasi.",
            listOf("${existing.size} item rundown existing", "Tidak ada data yang akan ditimpa"),
            emptyList()
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
        NaturalMutation(
            "rundown_items",
            mapOf(
                "booking_id" to booking.id,
                "activity_time" to time,
                "activity" to activity,
                "location" to location,
                "pic" to "Manager/TL"
            )
        )
    }
    return NaturalCommandDraft(
        "Draft Rundown • ${booking.bookingNo}",
        "Rundown draft siap. Review sebelum konfirmasi.",
        plan.map { "${it.first} • ${it.second} • ${it.third}" },
        mutations
    )
}

private fun naturalOperationSheetDraft(booking: Booking, rows: List<ErpRow>, userId: String): NaturalCommandDraft {
    if (rows.any { it.text("booking_id") == booking.id }) {
        return NaturalCommandDraft(
            "Operation Sheet • ${booking.bookingNo}",
            "Operation Sheet sudah tersedia. Agent tidak menimpa data existing.",
            listOf("Operation Sheet existing terdeteksi", "Gunakan Trip Operation bila perlu perubahan detail"),
            emptyList()
        )
    }

    val transport = if (booking.programName.contains("kereta", true) || booking.programName.contains("stasiun", true)) {
        "Kereta / transport terkonfirmasi"
    } else {
        "Transport sesuai booking / vendor"
    }
    val equipment = listOfNotNull(
        "Manifest & absensi",
        "P3K",
        "HT/komunikasi",
        "Dokumen vendor",
        booking.specialRequirements.takeIf { it.isNotBlank() }?.let { "Kebutuhan khusus: $it" }
    ).joinToString(", ")

    return NaturalCommandDraft(
        "Draft Operation Sheet • ${booking.bookingNo}",
        "Operation Sheet draft siap direview.",
        listOf(
            "Meeting 06:30 • ${booking.meetingPoint.ifBlank { "sesuai booking" }}",
            "Transport: $transport",
            "Operation PIC: Manager EduTrans",
            "Equipment: $equipment"
        ),
        listOf(
            NaturalMutation(
                "operation_sheets",
                mapOf(
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

private fun naturalCrewDraft(
    booking: Booking,
    profiles: List<ErpRow>,
    assignments: List<ErpRow>,
    userId: String
): NaturalCommandDraft {
    val existing = assignments.filter {
        it.text("title").contains(booking.bookingNo, true) && it.text("status") !in listOf("Cancelled", "Done", "Completed")
    }
    if (existing.isNotEmpty()) {
        return NaturalCommandDraft(
            "Crew • ${booking.bookingNo}",
            "Crew assignment sudah ditemukan.",
            existing.map { "${it.text("title")} • ${it.text("status")}" },
            emptyList()
        )
    }

    val active = profiles.filter { it.text("is_active") != "false" }
    val tl = active.firstOrNull { it.text("role").equals("TL", true) }
    val operation = active.firstOrNull { it.text("role").equals("Operation", true) }
    val selected = listOfNotNull(tl, operation).distinctBy { it.id }
    if (selected.isEmpty()) {
        return NaturalCommandDraft(
            "Crew • ${booking.bookingNo}",
            "Belum ada staff TL/Operation aktif untuk direkomendasikan.",
            listOf("Aktifkan atau tambahkan staff TL/Operation terlebih dahulu"),
            emptyList()
        )
    }

    val mutations = selected.map { staff ->
        val role = staff.text("role").ifBlank { "Crew" }
        NaturalMutation(
            "staff_assignments",
            mapOf(
                "staff_id" to staff.id,
                "title" to "$role • ${booking.bookingNo} • ${booking.programName}",
                "due_date" to booking.tripDate,
                "status" to "Assigned",
                "priority" to "High",
                "notes" to "Trip ${booking.customerName} • ${booking.pax} pax • disusun Natural Command Agent",
                "assigned_by" to userId
            )
        )
    }
    return NaturalCommandDraft(
        "Draft Crew • ${booking.bookingNo}",
        "Agent memilih staff aktif berdasarkan role TL/Operation.",
        selected.map { "${it.text("full_name")} • ${it.text("role")}" },
        mutations
    )
}

private fun naturalVendorCheck(booking: Booking, vendorPos: List<ErpRow>): NaturalCommandDraft {
    val pos = vendorPos.filter { it.text("booking_id") == booking.id }
    val lines = if (pos.isEmpty()) {
        listOf("Belum ada PO/vendor yang terkait booking ini", "Manager perlu memilih dan mengonfirmasi vendor")
    } else {
        pos.map { row ->
            "${row.text("po_no").ifBlank { "PO Vendor" }} • ${row.text("status").ifBlank { "status belum diisi" }} • ${row.text("description")}".trim()
        }
    }
    return NaturalCommandDraft(
        "Vendor Check • ${booking.bookingNo}",
        if (pos.isEmpty()) "Vendor belum siap." else "Ditemukan ${pos.size} PO/vendor terkait trip.",
        lines,
        emptyList()
    )
}

private fun naturalRabCheck(booking: Booking, tripCosts: List<ErpRow>): NaturalCommandDraft {
    val costs = tripCosts.filter { it.text("booking_id") == booking.id }
    if (costs.isEmpty()) {
        return NaturalCommandDraft(
            "RAB Operasional • ${booking.bookingNo}",
            "Belum ada data RAB operasional yang dapat dibaca untuk trip ini.",
            listOf("Isi RAB trip terlebih dahulu atau pastikan role Manager memiliki akses biaya operasional trip"),
            emptyList()
        )
    }
    val rab = costs.sumOf { it.number("rab_amount") }
    val actual = costs.sumOf { it.number("actual_amount") }
    val variance = rab - actual
    return NaturalCommandDraft(
        "RAB Operasional • ${booking.bookingNo}",
        if (variance < 0) "Aktual melewati RAB. Perlu perhatian Manager." else "RAB masih dalam kontrol.",
        listOf(
            "RAB: ${naturalRupiah(rab)}",
            "Aktual: ${naturalRupiah(actual)}",
            if (variance >= 0) "Sisa RAB: ${naturalRupiah(variance)}" else "Over budget: ${naturalRupiah(-variance)}"
        ),
        emptyList()
    )
}

private fun naturalReadinessCheck(
    booking: Booking,
    rundownRows: List<ErpRow>,
    operationSheets: List<ErpRow>,
    assignments: List<ErpRow>,
    vendorPos: List<ErpRow>,
    manifests: List<ErpRow>,
    documents: List<ErpRow>,
    tripCosts: List<ErpRow>
): NaturalCommandDraft {
    val checks = naturalReadinessChecks(booking, rundownRows, operationSheets, assignments, vendorPos, manifests, documents, tripCosts)
    val ready = checks.count { it.second }
    val score = ready * 100 / checks.size
    val missing = checks.filterNot { it.second }.map { it.first }
    return NaturalCommandDraft(
        "Readiness • ${booking.bookingNo} • $score%",
        if (missing.isEmpty()) "Trip siap dari checklist utama." else "Masih ada ${missing.size} area yang perlu dibereskan.",
        checks.map { (name, ok) -> (if (ok) "✓ " else "⚠ ") + name },
        emptyList()
    )
}

private fun naturalPrepareTripDraft(
    booking: Booking,
    rundownRows: List<ErpRow>,
    operationSheets: List<ErpRow>,
    profiles: List<ErpRow>,
    assignments: List<ErpRow>,
    vendorPos: List<ErpRow>,
    manifests: List<ErpRow>,
    documents: List<ErpRow>,
    tripCosts: List<ErpRow>,
    userId: String
): NaturalCommandDraft {
    val rundown = naturalRundownDraft(booking, rundownRows)
    val sheet = naturalOperationSheetDraft(booking, operationSheets, userId)
    val crew = naturalCrewDraft(booking, profiles, assignments, userId)
    val checks = naturalReadinessChecks(booking, rundownRows, operationSheets, assignments, vendorPos, manifests, documents, tripCosts)
    val missing = checks.filterNot { it.second }.map { it.first }
    val mutations = rundown.mutations + sheet.mutations + crew.mutations

    val preview = buildList {
        add("Rundown: ${if (rundown.mutations.isEmpty()) "existing / tidak dibuat" else "${rundown.mutations.size} item draft"}")
        add("Operation Sheet: ${if (sheet.mutations.isEmpty()) "existing / tidak dibuat" else "draft siap"}")
        add("Crew: ${if (crew.mutations.isEmpty()) "existing / belum tersedia" else "${crew.mutations.size} assignment draft"}")
        if (missing.isEmpty()) add("Readiness: seluruh checklist utama sudah terpenuhi")
        else add("Masih perlu Manager: ${missing.joinToString(", ")}")
        if ("Vendor/PO" in missing) add("Vendor tidak dibuat otomatis karena pilihan vendor harus ditentukan Manager")
        if ("Manifest" in missing) add("Manifest tidak dibuat otomatis karena data peserta harus valid")
        if ("Dokumen" in missing) add("Dokumen yang belum ada tetap perlu dilengkapi dari data trip")
    }

    return NaturalCommandDraft(
        "Prepare Trip • ${booking.bookingNo}",
        if (mutations.isEmpty()) "Tidak ada draft baru yang aman dibuat. Lihat daftar kekurangan di bawah." else "Draft persiapan trip lengkap siap untuk satu kali konfirmasi Manager.",
        preview,
        mutations
    )
}

private fun naturalReadinessChecks(
    booking: Booking,
    rundownRows: List<ErpRow>,
    operationSheets: List<ErpRow>,
    assignments: List<ErpRow>,
    vendorPos: List<ErpRow>,
    manifests: List<ErpRow>,
    documents: List<ErpRow>,
    tripCosts: List<ErpRow>
): List<Pair<String, Boolean>> = listOf(
    "Rundown" to rundownRows.any { it.text("booking_id") == booking.id },
    "Operation Sheet" to operationSheets.any { it.text("booking_id") == booking.id },
    "Crew" to assignments.any { it.text("title").contains(booking.bookingNo, true) && it.text("status") !in listOf("Cancelled", "Done", "Completed") },
    "Vendor/PO" to vendorPos.any { it.text("booking_id") == booking.id },
    "Manifest" to manifests.any { it.text("booking_id") == booking.id },
    "Dokumen" to (documents.count { it.text("booking_id") == booking.id } >= 5),
    "RAB Operasional" to tripCosts.any { it.text("booking_id") == booking.id && it.number("rab_amount") > 0.0 }
)

private fun naturalRupiah(value: Double): String {
    return "Rp" + String.format(Locale("id", "ID"), "%,d", value.toLong()).replace(',', '.')
}
