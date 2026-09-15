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

old = '''data class LabourDispatchSlotUi(
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
)'''
new = '''data class LabourDispatchSlotUi(
    val slotId: String,
    val slotNo: Int,
    val slotStatus: String,
    val dispatchId: String,
    val dispatchStatus: String,
    val assignmentStatus: String,
    val partnerId: String,
    val partnerName: String,
    val partnerAvailability: String,
    val partnerRating: Double,
    val recoveryActions: List<String>
)'''
if old not in s: raise SystemExit('LabourDispatchSlotUi M2.25 anchor missing')
s = s.replace(old, new, 1)

old = '''data class LabourDispatchBoardUi(
    val orderNo: String,
    val orderStatus: String,
    val requiredWorkers: Int,
    val assignedWorkers: Int,
    val openWorkers: Int,
    val canAssign: Boolean,
    val slots: List<LabourDispatchSlotUi>
)'''
new = '''data class LabourDispatchBoardUi(
    val orderNo: String,
    val orderStatus: String,
    val requiredWorkers: Int,
    val assignedWorkers: Int,
    val openWorkers: Int,
    val waitingDispatch: Int,
    val coveragePercent: Double,
    val canAssign: Boolean,
    val slots: List<LabourDispatchSlotUi>
)'''
if old not in s: raise SystemExit('LabourDispatchBoardUi M2.25 anchor missing')
s = s.replace(old, new, 1)

old = '''                        onLoadLabourCandidates = ::loadLabourCandidates,
                        onAssignLabourPartner = ::assignLabourPartner,
                        onLogout = ::logout'''
new = '''                        onLoadLabourCandidates = ::loadLabourCandidates,
                        onAssignLabourPartner = ::assignLabourPartner,
                        onRecoverLabourSlot = ::recoverLabourSlot,
                        onAutoAssignLabour = ::autoAssignLabour,
                        onLogout = ::logout'''
if old not in s: raise SystemExit('M2.25 callback wiring missing')
s = s.replace(old, new, 1)

anchor = '    private fun parseLabourBoard(result: JSONObject): LabourDispatchBoardUi {'
if anchor not in s: raise SystemExit('parseLabourBoard anchor missing')
methods = '''    private fun recoverLabourSlot(slotId: String, action: String, reason: String) {
        val row = state.value.selected ?: return
        if (!row.resource.equals("BULK_WORKFORCE", ignoreCase = true) || slotId.isBlank()) return
        state.value = state.value.copy(runningAction = true, error = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.recoverWorkforceSlot(slotId, action, reason)
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

    private fun autoAssignLabour() {
        val row = state.value.selected ?: return
        if (!row.resource.equals("BULK_WORKFORCE", ignoreCase = true)) return
        state.value = state.value.copy(runningAction = true, error = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.autoAssignWorkforce(row.id, 25)
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

'''
s = s.replace(anchor, methods + anchor, 1)

old = '''            val item = arr.optJSONObject(index) ?: return@mapNotNull null
            LabourDispatchSlotUi('''
new = '''            val item = arr.optJSONObject(index) ?: return@mapNotNull null
            val recoveryArray = item.optJSONArray("recoveryActions") ?: JSONArray()
            val recoveryActions = (0 until recoveryArray.length()).mapNotNull { i -> recoveryArray.optString(i).takeIf { it.isNotBlank() } }
            LabourDispatchSlotUi('''
if old not in s: raise SystemExit('slot parser anchor missing')
s = s.replace(old, new, 1)

old = '''                partnerAvailability = first(item, "partnerAvailability"),
                partnerRating = item.optDouble("partnerRating", 0.0)
            )'''
new = '''                partnerAvailability = first(item, "partnerAvailability"),
                partnerRating = item.optDouble("partnerRating", 0.0),
                recoveryActions = recoveryActions
            )'''
if old not in s: raise SystemExit('slot recovery parser anchor missing')
s = s.replace(old, new, 1)

old = '''            assignedWorkers = result.optInt("assignedWorkers", slots.count { it.slotStatus == "ASSIGNED" }),
            openWorkers = result.optInt("openWorkers", slots.count { it.slotStatus == "OPEN" }),
            canAssign = result.optBoolean("canAssign", false),'''
new = '''            assignedWorkers = result.optInt("assignedWorkers", slots.count { it.slotStatus == "ASSIGNED" }),
            openWorkers = result.optInt("openWorkers", slots.count { it.slotStatus == "OPEN" }),
            waitingDispatch = result.optInt("waitingDispatch", slots.count { it.dispatchStatus == "WAITING" }),
            coveragePercent = result.optDouble("coveragePercent", 0.0),
            canAssign = result.optBoolean("canAssign", false),'''
if old not in s: raise SystemExit('board parser anchor missing')
s = s.replace(old, new, 1)

old = '''    onLoadLabourCandidates: (String) -> Unit,
    onAssignLabourPartner: (String, String) -> Unit,
    onLogout: () -> Unit'''
new = '''    onLoadLabourCandidates: (String) -> Unit,
    onAssignLabourPartner: (String, String) -> Unit,
    onRecoverLabourSlot: (String, String, String) -> Unit,
    onAutoAssignLabour: () -> Unit,
    onLogout: () -> Unit'''
if old not in s: raise SystemExit('ManagementApp M2.25 signature missing')
s = s.replace(old, new, 1)

old = '        ui.selected != null -> DetailScreen(ui, onBack, onAction, onLoadLabourCandidates, onAssignLabourPartner)'
new = '        ui.selected != null -> DetailScreen(ui, onBack, onAction, onLoadLabourCandidates, onAssignLabourPartner, onRecoverLabourSlot, onAutoAssignLabour)'
if old not in s: raise SystemExit('M2.25 DetailScreen call missing')
s = s.replace(old, new, 1)

old = '''    onAction: (String, JSONObject) -> Unit,
    onLoadLabourCandidates: (String) -> Unit,
    onAssignLabourPartner: (String, String) -> Unit
) {'''
new = '''    onAction: (String, JSONObject) -> Unit,
    onLoadLabourCandidates: (String) -> Unit,
    onAssignLabourPartner: (String, String) -> Unit,
    onRecoverLabourSlot: (String, String, String) -> Unit,
    onAutoAssignLabour: () -> Unit
) {'''
if old not in s: raise SystemExit('DetailScreen M2.25 signature missing')
s = s.replace(old, new, 1)

old = '''    var pendingAssignment by remember { mutableStateOf<Pair<String, LabourCandidateUi>?>(null) }
    var showRaw by remember { mutableStateOf(false) }'''
new = '''    var pendingAssignment by remember { mutableStateOf<Pair<String, LabourCandidateUi>?>(null) }
    var pendingRecovery by remember { mutableStateOf<Pair<LabourDispatchSlotUi, String>?>(null) }
    var recoveryReason by remember { mutableStateOf("") }
    var showRaw by remember { mutableStateOf(false) }'''
if old not in s: raise SystemExit('DetailScreen state anchor missing')
s = s.replace(old, new, 1)

old = '''                            Text("${board.assignedWorkers}/${board.requiredWorkers} tenaga terisi • ${board.openWorkers} slot terbuka", fontWeight = FontWeight.Bold)
                            Text(if (board.canAssign) "Pilih Mitra terverifikasi untuk setiap slot. Backend mencegah double assignment." else "Mode pantau. Akun ini tidak memiliki izin assignment.", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)'''
new = '''                            Text("${board.assignedWorkers}/${board.requiredWorkers} tenaga terisi • ${board.coveragePercent}% coverage", fontWeight = FontWeight.Bold)
                            Text("${board.openWorkers} slot terbuka • ${board.waitingDispatch} menunggu dispatch", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                            Text(if (board.canAssign) "Pilih Mitra satu per satu atau isi otomatis. Recovery aman tersedia sebelum pekerjaan dimulai." else "Mode pantau. Akun ini tidak memiliki izin assignment.", style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
                            if (board.canAssign && board.openWorkers > 0) {
                                Button(onClick = onAutoAssignLabour, enabled = !ui.runningAction, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.AutoAwesome, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Isi otomatis hingga 25 Mitra")
                                }
                            }'''
if old not in s: raise SystemExit('dispatch header anchor missing')
s = s.replace(old, new, 1)

old = '''                            if (slot.dispatchStatus == "WAITING" && board.canAssign && slot.dispatchId.isNotBlank()) {
                                OutlinedButton(onClick = { onLoadLabourCandidates(slot.dispatchId) }, enabled = !ui.runningAction && !ui.loadingCandidates, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.PersonSearch, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Pilih Mitra")
                                }
                            }'''
new = '''                            if (slot.dispatchStatus == "WAITING" && board.canAssign && slot.dispatchId.isNotBlank()) {
                                OutlinedButton(onClick = { onLoadLabourCandidates(slot.dispatchId) }, enabled = !ui.runningAction && !ui.loadingCandidates, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.PersonSearch, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Pilih Mitra")
                                }
                            }
                            if (board.canAssign && slot.recoveryActions.isNotEmpty()) {
                                slot.recoveryActions.forEach { action ->
                                    val label = when (action) {
                                        "UNASSIGN" -> "Batalkan penempatan"
                                        "MARK_NO_SHOW" -> "Tandai no-show"
                                        "REPLACE_PARTNER" -> "Ganti Mitra"
                                        "RETRY_DISPATCH" -> "Retry dispatch"
                                        else -> action.replace('_', ' ')
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (action == "RETRY_DISPATCH") onRecoverLabourSlot(slot.slotId, action, "Retry dispatch")
                                            else pendingRecovery = slot to action
                                        },
                                        enabled = !ui.runningAction,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(label) }
                                }
                            }'''
if old not in s: raise SystemExit('slot action anchor missing')
s = s.replace(old, new, 1)

insert_at = s.rfind('\n}')
if insert_at < 0: raise SystemExit('DetailScreen closing brace missing')
recovery_dialog = r'''

    pendingRecovery?.let { pending ->
        val slot = pending.first
        val action = pending.second
        val label = when (action) {
            "UNASSIGN" -> "Batalkan penempatan"
            "MARK_NO_SHOW" -> "Tandai no-show"
            "REPLACE_PARTNER" -> "Ganti Mitra"
            else -> action.replace('_', ' ')
        }
        AlertDialog(
            onDismissRequest = { pendingRecovery = null; recoveryReason = "" },
            title = { Text(label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Slot ${slot.slotNo} akan dibuka kembali dan dispatch pengganti dibuat. Backend menjaga audit serta mencegah aksi ganda.")
                    OutlinedTextField(recoveryReason, { recoveryReason = it }, label = { Text("Alasan operasional") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(enabled = recoveryReason.trim().length >= 3, onClick = {
                    val reason = recoveryReason.trim()
                    pendingRecovery = null
                    recoveryReason = ""
                    onRecoverLabourSlot(slot.slotId, action, reason)
                }) { Text("Jalankan") }
            },
            dismissButton = { TextButton(onClick = { pendingRecovery = null; recoveryReason = "" }) { Text("Batal") } }
        )
    }
'''
s = s[:insert_at] + recovery_dialog + s[insert_at:]
main.write_text(s)

c = client.read_text()
changes_anchor = '    JSONArray changes(long afterEventId) throws Exception {'
if changes_anchor not in c: raise SystemExit('client changes anchor missing')
client_methods = '''    JSONObject recoverWorkforceSlot(String slotId, String action, String reason) throws Exception {
        return rpc("management_recover_bulk_workforce_slot", new JSONObject()
                .put("p_slot_id", slotId)
                .put("p_action", action)
                .put("p_reason", reason)
                .put("p_idempotency_key", UUID.randomUUID().toString()));
    }

    JSONObject autoAssignWorkforce(String requestId, int limit) throws Exception {
        return rpc("management_auto_assign_bulk_workforce", new JSONObject()
                .put("p_request_id", requestId)
                .put("p_limit", limit)
                .put("p_idempotency_key", UUID.randomUUID().toString()));
    }

'''
if 'JSONObject recoverWorkforceSlot(' not in c:
    c = c.replace(changes_anchor, client_methods + changes_anchor, 1)
client.write_text(c)

m = mapper.read_text()
anchor = '        if (has("PARTNER_NOT_ACTIVE", "PARTNER_NOT_VERIFIED_FOR_BULK_WORKFORCE")) {'
extra = '''        if (has("ORDER_NOT_RECOVERABLE", "ASSIGNMENT_NOT_RECOVERABLE", "ACTIVE_ASSIGNMENT_NOT_FOUND", "SLOT_STILL_ASSIGNED")) {
            return ManagementFailureUi(
                "RECOVERY_STATE_CHANGED", "Status tenaga sudah berubah",
                "Muat ulang Labour Dispatch sebelum menjalankan recovery agar tidak menimpa proses terbaru.", true
            )
        }
        if (has("RECOVERY_REASON_REQUIRED", "INVALID_RECOVERY_ACTION")) {
            return ManagementFailureUi(
                "RECOVERY_INPUT_INVALID", "Recovery belum dapat dijalankan",
                "Pilih aksi recovery yang tersedia dan isi alasan operasional yang jelas.", false
            )
        }
        if (has("BULK_WORKFORCE_NOT_CONVERTED")) {
            return ManagementFailureUi(
                "LABOUR_NOT_READY", "Labour belum siap dispatch",
                "Selesaikan approval dan konversi request menjadi order sebelum mengisi tenaga.", false
            )
        }
'''
if anchor not in m: raise SystemExit('M2.25 failure mapper anchor missing')
if 'RECOVERY_STATE_CHANGED' not in m:
    m = m.replace(anchor, extra + anchor, 1)
mapper.write_text(m)

b = build.read_text()
if 'versionCode = 10' not in b or 'versionName = "1.0.8-labour-dispatch-m2.25"' not in b:
    raise SystemExit('M2.25 version anchor missing')
b = b.replace('versionCode = 10', 'versionCode = 11', 1)
b = b.replace('versionName = "1.0.8-labour-dispatch-m2.25"', 'versionName = "1.0.9-labour-recovery-m2.26"', 1)
build.write_text(b)

out = main.read_text(); cout = client.read_text()
for token in ('recoveryActions', 'Isi otomatis hingga 25 Mitra', 'recoverLabourSlot', 'autoAssignLabour'):
    if token not in out: raise SystemExit(f'M2.26 UI token missing: {token}')
for token in ('management_recover_bulk_workforce_slot', 'management_auto_assign_bulk_workforce'):
    if token not in cout: raise SystemExit(f'M2.26 RPC token missing: {token}')
if 'versionCode = 11' not in build.read_text(): raise SystemExit('M2.26 version bump missing')
print('GAWONE Management M2.26 Labour recovery applied')
