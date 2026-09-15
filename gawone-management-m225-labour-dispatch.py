from pathlib import Path

pkg = Path('app/src/main/java/site/garsyanimultiusaha/gawone/management')
main = pkg / 'FullMainActivity.kt'
client = pkg / 'SupabaseManagementClient.java'
mapper = pkg / 'GawoneManagementFailure.kt'
build = Path('app/build.gradle.kts')

for p in (main, client, mapper, build):
    if not p.exists():
        raise SystemExit(f'missing {p}')

s = main.read_text()

model_anchor = 'data class ManagementUi(\n'
if 'data class LabourDispatchSlotUi(' not in s:
    models = '''data class LabourDispatchSlotUi(
    val slotId: String,
    val slotNo: Int,
    val slotStatus: String,
    val dispatchId: String,
    val dispatchStatus: String,
    val assignmentStatus: String,
    val partnerId: String,
    val partnerName: String,
    val partnerAvailability: String,
    val partnerRating: Double
)

data class LabourDispatchBoardUi(
    val orderNo: String,
    val orderStatus: String,
    val requiredWorkers: Int,
    val assignedWorkers: Int,
    val openWorkers: Int,
    val canAssign: Boolean,
    val slots: List<LabourDispatchSlotUi>
)

data class LabourCandidateUi(
    val partnerId: String,
    val fullName: String,
    val rating: Double,
    val completedJobs: Int,
    val availability: String
)

'''
    if model_anchor not in s:
        raise SystemExit('ManagementUi model anchor missing')
    s = s.replace(model_anchor, models + model_anchor, 1)

ui_old = '''    val detail: String = "",
    val actions: List<String> = emptyList(),
    val runningAction: Boolean = false,
    val error: String? = null
)'''
ui_new = '''    val detail: String = "",
    val actions: List<String> = emptyList(),
    val runningAction: Boolean = false,
    val labourBoard: LabourDispatchBoardUi? = null,
    val selectedLabourDispatch: String? = null,
    val labourCandidates: List<LabourCandidateUi> = emptyList(),
    val loadingCandidates: Boolean = false,
    val error: String? = null
)'''
if ui_old not in s:
    raise SystemExit('ManagementUi fields anchor missing')
s = s.replace(ui_old, ui_new, 1)

callback_old = '''                        onBack = ::closeDetail,
                        onAction = ::executeAction,
                        onLogout = ::logout'''
callback_new = '''                        onBack = ::closeDetail,
                        onAction = ::executeAction,
                        onLoadLabourCandidates = ::loadLabourCandidates,
                        onAssignLabourPartner = ::assignLabourPartner,
                        onLogout = ::logout'''
if callback_old not in s:
    raise SystemExit('ManagementApp callback wiring anchor missing')
s = s.replace(callback_old, callback_new, 1)

open_start = s.find('    private fun openRow(row: ManagementRow) {')
open_end = s.find('\n\n    private fun executeAction', open_start)
if open_start < 0 or open_end < 0:
    raise SystemExit('openRow block missing')
new_open = '''    private fun openRow(row: ManagementRow) {
        if (row.resource.isBlank()) {
            state.value = state.value.copy(selected = row, detail = row.raw, actions = emptyList(), labourBoard = null, selectedLabourDispatch = null, labourCandidates = emptyList(), error = "Resource detail tidak diberikan backend; aksi dinonaktifkan.")
            return
        }
        state.value = state.value.copy(selected = row, loading = true, detail = "", actions = emptyList(), labourBoard = null, selectedLabourDispatch = null, labourCandidates = emptyList(), loadingCandidates = false, error = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val detailObject = api.detail(row.resource, row.id)
                    val board = if (row.resource.equals("BULK_WORKFORCE", ignoreCase = true)) {
                        parseLabourBoard(api.workforceDispatchBoard(row.id))
                    } else null
                    secureStore.save(api.accessToken(), api.refreshToken())
                    Triple(detailObject.toString(2), SupabaseManagementClient.extractActions(detailObject), board)
                }
            }.onSuccess {
                state.value = state.value.copy(loading = false, detail = it.first, actions = it.second, labourBoard = it.third, selectedLabourDispatch = null, labourCandidates = emptyList(), loadingCandidates = false)
            }.onFailure {
                state.value = state.value.copy(loading = false, error = friendly(it))
            }
        }
    }'''
s = s[:open_start] + new_open + s[open_end:]

exec_start = s.find('    private fun executeAction(action: String, payload: JSONObject) {')
exec_end = s.find('\n\n    private fun closeDetail', exec_start)
if exec_start < 0 or exec_end < 0:
    raise SystemExit('executeAction M2.23 block missing')
new_exec = '''    private fun executeAction(action: String, payload: JSONObject) {
        val row = state.value.selected ?: return
        state.value = state.value.copy(runningAction = true, error = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    when (row.resource.uppercase()) {
                        "BUSINESS" -> api.businessAction(action, row.id, payload)
                        "BULK_WORKFORCE" -> api.workforceAction(action, row.id, payload)
                        else -> api.action(action, row.id, payload)
                    }
                    val detailObject = api.detail(row.resource, row.id)
                    val board = if (row.resource.equals("BULK_WORKFORCE", ignoreCase = true)) {
                        parseLabourBoard(api.workforceDispatchBoard(row.id))
                    } else null
                    secureStore.save(api.accessToken(), api.refreshToken())
                    Triple(detailObject.toString(2), SupabaseManagementClient.extractActions(detailObject), board)
                }
            }.onSuccess {
                state.value = state.value.copy(runningAction = false, detail = it.first, actions = it.second, labourBoard = it.third, selectedLabourDispatch = null, labourCandidates = emptyList(), loadingCandidates = false)
            }.onFailure {
                state.value = state.value.copy(runningAction = false, error = friendly(it))
            }
        }
    }

    private fun loadLabourCandidates(dispatchId: String) {
        if (dispatchId.isBlank()) return
        state.value = state.value.copy(selectedLabourDispatch = dispatchId, loadingCandidates = true, labourCandidates = emptyList(), error = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val result = api.workforceCandidates(dispatchId, 50)
                    secureStore.save(api.accessToken(), api.refreshToken())
                    parseLabourCandidates(result)
                }
            }.onSuccess {
                state.value = state.value.copy(loadingCandidates = false, labourCandidates = it)
            }.onFailure {
                state.value = state.value.copy(loadingCandidates = false, error = friendly(it))
            }
        }
    }

    private fun assignLabourPartner(dispatchId: String, partnerId: String) {
        val row = state.value.selected ?: return
        if (!row.resource.equals("BULK_WORKFORCE", ignoreCase = true) || dispatchId.isBlank() || partnerId.isBlank()) return
        state.value = state.value.copy(runningAction = true, error = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.assignWorkforcePartner(dispatchId, partnerId)
                    val detailObject = api.detail(row.resource, row.id)
                    val board = parseLabourBoard(api.workforceDispatchBoard(row.id))
                    secureStore.save(api.accessToken(), api.refreshToken())
                    Triple(detailObject.toString(2), SupabaseManagementClient.extractActions(detailObject), board)
                }
            }.onSuccess {
                state.value = state.value.copy(runningAction = false, detail = it.first, actions = it.second, labourBoard = it.third, selectedLabourDispatch = null, labourCandidates = emptyList(), loadingCandidates = false)
            }.onFailure {
                state.value = state.value.copy(runningAction = false, error = friendly(it))
            }
        }
    }

    private fun parseLabourBoard(result: JSONObject): LabourDispatchBoardUi {
        val arr = result.optJSONArray("items") ?: JSONArray()
        val slots = (0 until arr.length()).mapNotNull { index ->
            val item = arr.optJSONObject(index) ?: return@mapNotNull null
            LabourDispatchSlotUi(
                slotId = first(item, "slotId"),
                slotNo = item.optInt("slotNo", index + 1),
                slotStatus = first(item, "slotStatus"),
                dispatchId = first(item, "dispatchId"),
                dispatchStatus = first(item, "dispatchStatus"),
                assignmentStatus = first(item, "assignmentStatus"),
                partnerId = first(item, "partnerId"),
                partnerName = first(item, "partnerName"),
                partnerAvailability = first(item, "partnerAvailability"),
                partnerRating = item.optDouble("partnerRating", 0.0)
            )
        }
        return LabourDispatchBoardUi(
            orderNo = first(result, "orderNo"),
            orderStatus = first(result, "orderStatus"),
            requiredWorkers = result.optInt("requiredWorkers", slots.size),
            assignedWorkers = result.optInt("assignedWorkers", slots.count { it.slotStatus == "ASSIGNED" }),
            openWorkers = result.optInt("openWorkers", slots.count { it.slotStatus == "OPEN" }),
            canAssign = result.optBoolean("canAssign", false),
            slots = slots
        )
    }

    private fun parseLabourCandidates(result: JSONObject): List<LabourCandidateUi> {
        val arr = result.optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { index ->
            val item = arr.optJSONObject(index) ?: return@mapNotNull null
            val partnerId = first(item, "partnerId")
            if (partnerId.isBlank()) return@mapNotNull null
            LabourCandidateUi(
                partnerId = partnerId,
                fullName = first(item, "fullName").ifBlank { "Mitra ${partnerId.take(8)}" },
                rating = item.optDouble("rating", 0.0),
                completedJobs = item.optInt("completedJobs", 0),
                availability = first(item, "availability")
            )
        }
    }'''
s = s[:exec_start] + new_exec + s[exec_end:]

app_sig_old = '''    onBack: () -> Unit,
    onAction: (String, JSONObject) -> Unit,
    onLogout: () -> Unit'''
app_sig_new = '''    onBack: () -> Unit,
    onAction: (String, JSONObject) -> Unit,
    onLoadLabourCandidates: (String) -> Unit,
    onAssignLabourPartner: (String, String) -> Unit,
    onLogout: () -> Unit'''
if app_sig_old not in s:
    raise SystemExit('ManagementApp M2.23 signature missing')
s = s.replace(app_sig_old, app_sig_new, 1)

app_call_old = '        ui.selected != null -> DetailScreen(ui, onBack, onAction)'
app_call_new = '        ui.selected != null -> DetailScreen(ui, onBack, onAction, onLoadLabourCandidates, onAssignLabourPartner)'
if app_call_old not in s:
    raise SystemExit('DetailScreen call anchor missing')
s = s.replace(app_call_old, app_call_new, 1)

marker = '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun DetailScreen'
pos = s.find(marker)
if pos < 0:
    raise SystemExit('DetailScreen marker missing')
new_detail = r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    ui: ManagementUi,
    onBack: () -> Unit,
    onAction: (String, JSONObject) -> Unit,
    onLoadLabourCandidates: (String) -> Unit,
    onAssignLabourPartner: (String, String) -> Unit
) {
    var pending by remember { mutableStateOf<String?>(null) }
    var pendingAssignment by remember { mutableStateOf<Pair<String, LabourCandidateUi>?>(null) }
    var showRaw by remember { mutableStateOf(false) }
    var quoteAmount by remember { mutableStateOf("") }
    var managementFee by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val board = ui.labourBoard
    Scaffold(
        containerColor = GawoneManagementTokens.Canvas,
        topBar = {
            TopAppBar(
                title = { Column { Text(ui.selected?.title ?: "Detail", fontWeight = FontWeight.ExtraBold); Text(ui.selected?.resource.orEmpty(), style = MaterialTheme.typography.labelSmall, color = GawoneManagementTokens.Muted) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") } }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Surface(color = GawoneManagementTokens.PrimarySoft, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ui.selected?.subtitle.orEmpty(), fontWeight = FontWeight.ExtraBold)
                                Text("Resource ID • ${ui.selected?.id?.take(12).orEmpty()}", style = MaterialTheme.typography.labelSmall, color = GawoneManagementTokens.Muted)
                            }
                            if (!ui.selected?.status.isNullOrBlank()) GawoneManagementStatusPill(ui.selected!!.status)
                        }
                        Text("Aksi diverifikasi backend: role, state transition, idempotency, dan audit trail.", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Ink)
                    }
                }
            }
            if (ui.loading || ui.runningAction) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = GawoneManagementTokens.Primary) }
            if (ui.actions.isNotEmpty()) {
                item { Text("Aksi tersedia", fontWeight = FontWeight.ExtraBold) }
                items(ui.actions, key = { it }) { action ->
                    Button(onClick = { pending = action }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !ui.runningAction, shape = RoundedCornerShape(15.dp)) {
                        Text(action.replace('_', ' '), fontWeight = FontWeight.Bold)
                    }
                }
            } else if (!ui.loading && ui.selected?.resource != "BULK_WORKFORCE") {
                item { GawoneManagementCard { Icon(Icons.Default.Lock, null, tint = GawoneManagementTokens.Muted); Text("Tidak ada aksi untuk role ini", fontWeight = FontWeight.ExtraBold); Text("Backend hanya mengembalikan aksi yang diizinkan untuk akun Management saat ini.", color = GawoneManagementTokens.Muted, style = MaterialTheme.typography.bodySmall) } }
            }

            if (ui.selected?.resource.equals("BULK_WORKFORCE", ignoreCase = true) && board != null && board.orderNo.isNotBlank()) {
                item {
                    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GawoneManagementTokens.Border), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Dispatch tenaga", fontWeight = FontWeight.ExtraBold)
                                    Text(board.orderNo, style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                                }
                                GawoneManagementStatusPill(board.orderStatus)
                            }
                            Text("${board.assignedWorkers}/${board.requiredWorkers} tenaga terisi • ${board.openWorkers} slot terbuka", fontWeight = FontWeight.Bold)
                            Text(if (board.canAssign) "Pilih Mitra terverifikasi untuk setiap slot. Backend mencegah double assignment." else "Mode pantau. Akun ini tidak memiliki izin assignment.", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                        }
                    }
                }

                if (ui.selectedLabourDispatch != null) {
                    item {
                        Text("Kandidat Mitra", fontWeight = FontWeight.ExtraBold)
                        Text("Hanya Mitra ACTIVE dan terverifikasi untuk Labour yang ditampilkan.", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                    }
                    if (ui.loadingCandidates) {
                        item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = GawoneManagementTokens.Primary) }
                    } else if (ui.labourCandidates.isEmpty()) {
                        item { GawoneManagementCard { Icon(Icons.Default.PersonOff, null, tint = GawoneManagementTokens.Muted); Text("Belum ada Mitra yang memenuhi syarat", fontWeight = FontWeight.ExtraBold); Text("Slot tetap WAITING dan dapat dicoba lagi setelah Mitra tersedia.", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted) } }
                    } else {
                        items(ui.labourCandidates, key = { it.partnerId }) { candidate ->
                            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GawoneManagementTokens.Border), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.Person, null, tint = GawoneManagementTokens.Primary)
                                    Column(Modifier.weight(1f)) {
                                        Text(candidate.fullName, fontWeight = FontWeight.ExtraBold)
                                        Text("Rating ${candidate.rating} • ${candidate.completedJobs} job • ${candidate.availability.ifBlank { "UNKNOWN" }}", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                                    }
                                    Button(onClick = { pendingAssignment = ui.selectedLabourDispatch to candidate }, enabled = !ui.runningAction) { Text("Assign") }
                                }
                            }
                        }
                    }
                }

                item { Text("Slot tenaga", fontWeight = FontWeight.ExtraBold) }
                items(board.slots, key = { if (it.slotId.isBlank()) "slot-${it.slotNo}" else it.slotId }) { slot ->
                    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GawoneManagementTokens.Border), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Slot ${slot.slotNo}", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                GawoneManagementStatusPill(slot.assignmentStatus.ifBlank { slot.slotStatus })
                            }
                            if (slot.partnerName.isNotBlank()) {
                                Text(slot.partnerName, fontWeight = FontWeight.Bold)
                                Text("${slot.partnerAvailability.ifBlank { "UNKNOWN" }} • Rating ${slot.partnerRating}", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                            } else {
                                Text("Belum ada Mitra", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                            }
                            if (slot.dispatchStatus == "WAITING" && board.canAssign && slot.dispatchId.isNotBlank()) {
                                OutlinedButton(onClick = { onLoadLabourCandidates(slot.dispatchId) }, enabled = !ui.runningAction && !ui.loadingCandidates, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.PersonSearch, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Pilih Mitra")
                                }
                            }
                        }
                    }
                }
            } else if (ui.selected?.resource.equals("BULK_WORKFORCE", ignoreCase = true) && !ui.loading) {
                item { GawoneManagementCard { Icon(Icons.Default.Schedule, null, tint = GawoneManagementTokens.Muted); Text("Belum masuk tahap dispatch", fontWeight = FontWeight.ExtraBold); Text("Setelah request disetujui dan dikonversi menjadi order, slot 10–500 tenaga akan muncul di sini.", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted) } }
            }

            item { OutlinedButton(onClick = { showRaw = !showRaw }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) { Icon(if (showRaw) Icons.Default.ExpandLess else Icons.Default.DataObject, null); Spacer(Modifier.width(8.dp)); Text(if (showRaw) "Sembunyikan data backend" else "Lihat data backend") } }
            if (showRaw && ui.detail.isNotBlank()) item { Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GawoneManagementTokens.Border), modifier = Modifier.fillMaxWidth()) { Text(ui.detail, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall) } }
            if (!ui.error.isNullOrBlank()) item { Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Text(ui.error, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }

    pending?.let { action ->
        val needsQuote = action == "QUOTE_WORKFORCE"
        val needsReason = action in setOf("REJECT_BUSINESS", "SUSPEND_BUSINESS", "REJECT_WORKFORCE")
        val quoteOk = !needsQuote || (quoteAmount.toDoubleOrNull()?.let { it > 0 } == true && managementFee.toDoubleOrNull()?.let { it >= 0 } == true)
        val valid = quoteOk && (!needsReason || reason.trim().length >= 3)
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(action.replace('_', ' ')) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Backend akan memvalidasi status terbaru sebelum aksi disimpan.")
                if (needsQuote) {
                    OutlinedTextField(quoteAmount, { quoteAmount = it.filter(Char::isDigit) }, label = { Text("Nilai quotation (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(managementFee, { managementFee = it.filter(Char::isDigit) }, label = { Text("Management fee (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                if (needsReason) OutlinedTextField(reason, { reason = it }, label = { Text("Alasan") }, minLines = 2)
            } },
            confirmButton = { TextButton(enabled = valid, onClick = {
                val payload = JSONObject()
                if (needsQuote) { payload.put("quotedAmount", quoteAmount.toDouble()); payload.put("managementFee", managementFee.toDouble()) }
                if (needsReason) payload.put("reason", reason.trim())
                pending = null; quoteAmount = ""; managementFee = ""; reason = ""; onAction(action, payload)
            }) { Text("Jalankan") } },
            dismissButton = { TextButton(onClick = { pending = null; quoteAmount = ""; managementFee = ""; reason = "" }) { Text("Batal") } }
        )
    }

    pendingAssignment?.let { pair ->
        val dispatchId = pair.first
        val candidate = pair.second
        AlertDialog(
            onDismissRequest = { pendingAssignment = null },
            title = { Text("Assign Mitra") },
            text = { Text("Tempatkan ${candidate.fullName} ke slot tenaga ini? Backend akan memeriksa status Mitra, verifikasi Labour, slot, dan mencegah assignment ganda.") },
            confirmButton = { TextButton(onClick = { pendingAssignment = null; onAssignLabourPartner(dispatchId, candidate.partnerId) }) { Text("Assign") } },
            dismissButton = { TextButton(onClick = { pendingAssignment = null }) { Text("Batal") } }
        )
    }
}
'''
s = s[:pos] + new_detail
main.write_text(s)

c = client.read_text()
changes_anchor = '    JSONArray changes(long afterEventId) throws Exception {'
if changes_anchor not in c:
    raise SystemExit('client changes anchor missing')
methods = '''    JSONObject workforceDispatchBoard(String requestId) throws Exception {
        return rpc("get_management_bulk_dispatch_board", new JSONObject().put("p_request_id", requestId));
    }

    JSONObject workforceCandidates(String dispatchId, int limit) throws Exception {
        return rpc("get_management_bulk_dispatch_candidates", new JSONObject().put("p_dispatch_id", dispatchId).put("p_limit", limit));
    }

    JSONObject assignWorkforcePartner(String dispatchId, String partnerId) throws Exception {
        return rpc("management_assign_bulk_workforce_partner", new JSONObject()
                .put("p_dispatch_id", dispatchId)
                .put("p_partner_id", partnerId)
                .put("p_idempotency_key", UUID.randomUUID().toString()));
    }

'''
if 'JSONObject workforceDispatchBoard(' not in c:
    c = c.replace(changes_anchor, methods + changes_anchor, 1)
client.write_text(c)

m = mapper.read_text()
m = m.replace(
    'if (has("MANAGEMENT_ROLE_REQUIRED", "MANAGEMENT_PERMISSION_DENIED", "MANAGEMENT_QUEUE_ACCESS_DENIED")) {',
    'if (has("MANAGEMENT_ROLE_REQUIRED", "MANAGEMENT_PERMISSION_DENIED", "MANAGEMENT_QUEUE_ACCESS_DENIED", "ORDER_ASSIGN_PERMISSION_REQUIRED")) {',
    1
)
m = m.replace(
    'if (has("PARTNER_NOT_FOUND", "DISPATCH_ITEM_NOT_FOUND", "SUPPORT_TICKET_NOT_FOUND", "PAYOUT_NOT_FOUND", "AUDIT_EVENT_NOT_FOUND")) {',
    'if (has("PARTNER_NOT_FOUND", "DISPATCH_ITEM_NOT_FOUND", "DISPATCH_NOT_FOUND", "BULK_WORKFORCE_REQUEST_NOT_FOUND", "DISPATCH_NOT_WAITING", "WORKER_SLOT_NOT_OPEN", "ORDER_NOT_ASSIGNABLE", "SUPPORT_TICKET_NOT_FOUND", "PAYOUT_NOT_FOUND", "AUDIT_EVENT_NOT_FOUND")) {',
    1
)
partner_anchor = '''        if (has("IDEMPOTENCY_KEY_CONFLICT", "ACTION_ALREADY_IN_PROGRESS")) {'''
partner_mapping = '''        if (has("PARTNER_NOT_ACTIVE", "PARTNER_NOT_VERIFIED_FOR_BULK_WORKFORCE")) {
            return ManagementFailureUi(
                "PARTNER_NOT_ELIGIBLE", "Mitra belum memenuhi syarat",
                "Pilih Mitra ACTIVE yang sudah terverifikasi untuk layanan Labour.", true
            )
        }
        if (has("PARTNER_ALREADY_ASSIGNED")) {
            return ManagementFailureUi(
                "PARTNER_BUSY", "Mitra sedang bertugas",
                "Pilih Mitra lain atau muat ulang daftar kandidat untuk melihat ketersediaan terbaru.", true
            )
        }
'''
if partner_anchor not in m:
    raise SystemExit('failure mapper idempotency anchor missing')
if 'PARTNER_NOT_ELIGIBLE' not in m:
    m = m.replace(partner_anchor, partner_mapping + partner_anchor, 1)
mapper.write_text(m)

b = build.read_text()
if 'versionCode = 9' not in b or 'versionName = "1.0.7-gated-nav-m2.24"' not in b:
    raise SystemExit('M2.24 version anchor missing')
b = b.replace('versionCode = 9', 'versionCode = 10', 1)
b = b.replace('versionName = "1.0.7-gated-nav-m2.24"', 'versionName = "1.0.8-labour-dispatch-m2.25"', 1)
build.write_text(b)

out = main.read_text()
client_out = client.read_text()
if 'Dispatch tenaga' not in out or 'Pilih Mitra' not in out or 'assignLabourPartner' not in out:
    raise SystemExit('M2.25 Labour dispatch UI missing')
for token in ('get_management_bulk_dispatch_board', 'get_management_bulk_dispatch_candidates', 'management_assign_bulk_workforce_partner'):
    if token not in client_out:
        raise SystemExit(f'M2.25 client RPC missing: {token}')
if 'versionCode = 10' not in build.read_text():
    raise SystemExit('M2.25 version bump missing')

print('GAWONE Management M2.25 Labour dispatch workspace applied')
