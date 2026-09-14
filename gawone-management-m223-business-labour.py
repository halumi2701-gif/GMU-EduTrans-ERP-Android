from pathlib import Path

pkg=Path('app/src/main/java/site/garsyanimultiusaha/gawone/management')
main=pkg/'FullMainActivity.kt'
client=pkg/'SupabaseManagementClient.java'
mapper=pkg/'GawoneManagementFailure.kt'
build=Path('app/build.gradle.kts')
for p in (main,client,mapper,build):
    if not p.exists(): raise SystemExit(f'missing {p}')

s=main.read_text()
s=s.replace('import androidx.compose.foundation.shape.RoundedCornerShape\n','import androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.foundation.text.KeyboardOptions\n',1)
s=s.replace('import androidx.compose.ui.text.input.PasswordVisualTransformation\n','import androidx.compose.ui.text.input.PasswordVisualTransformation\nimport androidx.compose.ui.text.input.KeyboardType\n',1)

old='''                        onAction = ::executeAction,
                        onLogout = ::logout'''
new='''                        onAction = ::executeAction,
                        onLogout = ::logout'''
if old not in s: raise SystemExit('root action anchor missing')
# no textual change needed; signature below changes type

old='''    private fun parseWorkspaces(nav: JSONObject): List<String> {
        val raw = nav.toString().uppercase()
        return listOf("PARTNER_REVIEW", "DISPATCH", "SUPPORT", "PAYOUT", "AUDIT").filter { raw.contains(it) }
    }'''
new='''    private fun parseWorkspaces(nav: JSONObject): List<String> {
        val raw = nav.toString().uppercase()
        val out = listOf("PARTNER_REVIEW", "DISPATCH", "SUPPORT", "PAYOUT", "AUDIT").filter { raw.contains(it) }.toMutableList()
        val businessAllowed = raw.contains("BUSINESS.READ") || raw.contains("BUSINESS.MANAGE")
        if (businessAllowed) {
            if (!out.contains("BUSINESS")) out.add(1, "BUSINESS")
            if (!out.contains("LABOUR")) out.add(2.coerceAtMost(out.size), "LABOUR")
        }
        return out
    }'''
if old not in s: raise SystemExit('parseWorkspaces anchor missing')
s=s.replace(old,new,1)

old='''            val id = first(item, "resource_id", "id", "partner_id", "order_id", "ticket_id", "payout_id")
            if (id.isBlank()) return@mapNotNull null
            val resource = first(item, "resource", "resource_type", "detail_resource", "type")
            val title = first(item, "title", "name", "full_name", "ticket_no", "order_no", "status").ifBlank { "${resource.ifBlank { workspace }} • ${id.take(8)}" }
            val subtitle = first(item, "subtitle", "subject", "service_name", "reason", "priority").ifBlank { workspace.replace('_', ' ') }
            val status = first(item, "status", "state")
            ManagementRow(id, resource, title, subtitle, status, item.toString())'''
new='''            val id = first(item, "resource_id", "id", "partner_id", "order_id", "ticket_id", "payout_id")
            if (id.isBlank()) return@mapNotNull null
            val explicitResource = first(item, "resource", "resource_type", "detail_resource", "type")
            val resource = explicitResource.ifBlank { when (workspace) { "BUSINESS" -> "BUSINESS"; "LABOUR" -> "BULK_WORKFORCE"; else -> "" } }
            val title = first(item, "title", "name", "display_name", "business_name", "role_title", "full_name", "ticket_no", "order_no", "status").ifBlank { "${resource.ifBlank { workspace }} • ${id.take(8)}" }
            val subtitle = first(item, "subtitle", "legal_name", "work_address", "subject", "service_name", "reason", "priority").ifBlank { workspace.replace('_', ' ') }
            val status = first(item, "status", "state", "account_status", "queue_status")
            ManagementRow(id, resource, title, subtitle, status, item.toString())'''
if old not in s: raise SystemExit('parseRows anchor missing')
s=s.replace(old,new,1)

old='''    private fun executeAction(action: String) {
        val row = state.value.selected ?: return
        state.value = state.value.copy(runningAction = true, error = null)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.action(action, row.id, JSONObject())
                    val detailObject = api.detail(row.resource, row.id)
                    secureStore.save(api.accessToken(), api.refreshToken())
                    Pair(detailObject.toString(2), SupabaseManagementClient.extractActions(detailObject))
                }
            }.onSuccess {
                state.value = state.value.copy(runningAction = false, detail = it.first, actions = it.second)
            }.onFailure {
                state.value = state.value.copy(runningAction = false, error = friendly(it))
            }
        }
    }'''
new='''    private fun executeAction(action: String, payload: JSONObject) {
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
                    secureStore.save(api.accessToken(), api.refreshToken())
                    Pair(detailObject.toString(2), SupabaseManagementClient.extractActions(detailObject))
                }
            }.onSuccess {
                state.value = state.value.copy(runningAction = false, detail = it.first, actions = it.second)
                loadQueue(state.value.activeWorkspace)
            }.onFailure {
                state.value = state.value.copy(runningAction = false, error = friendly(it))
            }
        }
    }'''
if old not in s: raise SystemExit('executeAction anchor missing')
s=s.replace(old,new,1)

old='''    onAction: (String) -> Unit,
    onLogout: () -> Unit'''
new='''    onAction: (String, JSONObject) -> Unit,
    onLogout: () -> Unit'''
if old not in s: raise SystemExit('ManagementApp callback anchor missing')
s=s.replace(old,new,1)

old='''private fun DetailScreen(ui: ManagementUi, onBack: () -> Unit, onAction: (String) -> Unit) {
    var pending by remember { mutableStateOf<String?>(null) }'''
new='''private fun DetailScreen(ui: ManagementUi, onBack: () -> Unit, onAction: (String, JSONObject) -> Unit) {
    var pending by remember { mutableStateOf<String?>(null) }
    var quoteAmount by remember { mutableStateOf("") }
    var managementFee by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }'''
if old not in s: raise SystemExit('DetailScreen signature anchor missing')
s=s.replace(old,new,1)

old='''    pending?.let { action ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Konfirmasi aksi") },
            text = { Text("Jalankan ${action.replace('_', ' ')}? Backend tetap memvalidasi role, capability, idempotency, dan audit.") },
            confirmButton = { TextButton(onClick = { pending = null; onAction(action) }) { Text("Jalankan") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Batal") } }
        )
    }
}'''
new='''    pending?.let { action ->
        val needsQuote = action == "QUOTE_WORKFORCE"
        val needsReason = action in setOf("REJECT_BUSINESS","SUSPEND_BUSINESS","REJECT_WORKFORCE")
        val valid = (!needsQuote || (quoteAmount.toDoubleOrNull()?.let { it > 0 } == true && managementFee.toDoubleOrNull()?.let { it >= 0 } == true)) && (!needsReason || reason.trim().length >= 3)
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(action.replace('_', ' ')) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Backend memvalidasi role, state transition, idempotency, dan audit sebelum perubahan disimpan.")
                    if (needsQuote) {
                        OutlinedTextField(quoteAmount,{quoteAmount=it.filter { c -> c.isDigit() }},label={Text("Nilai quotation (Rp)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
                        OutlinedTextField(managementFee,{managementFee=it.filter { c -> c.isDigit() }},label={Text("Management fee (Rp)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
                    }
                    if (needsReason) OutlinedTextField(reason,{reason=it},label={Text("Alasan")},minLines=2)
                }
            },
            confirmButton = { TextButton(enabled=valid,onClick = {
                val payload=JSONObject()
                if(needsQuote){payload.put("quotedAmount",quoteAmount.toDouble());payload.put("managementFee",managementFee.toDouble())}
                if(needsReason)payload.put("reason",reason.trim())
                pending=null;quoteAmount="";managementFee="";reason="";onAction(action,payload)
            }) { Text("Jalankan") } },
            dismissButton = { TextButton(onClick = { pending = null;quoteAmount="";managementFee="";reason="" }) { Text("Batal") } }
        )
    }
}'''
if old not in s: raise SystemExit('Detail dialog anchor missing')
s=s.replace(old,new,1)
main.write_text(s)

c=client.read_text()
old='''    JSONObject queue(String queue, String status, int limit, int offset) throws Exception {
        JSONObject p = new JSONObject().put("p_queue", queue).put("p_limit", limit).put("p_offset", offset).put("p_version_code", VERSION_CODE);
        if (status != null && !status.isEmpty()) p.put("p_status", status);
        return rpc("get_management_queue", p);
    }

    JSONObject detail(String resource, String id) throws Exception {
        return rpc("get_management_detail", new JSONObject().put("p_resource", resource).put("p_resource_id", id).put("p_version_code", VERSION_CODE));
    }'''
new='''    JSONObject queue(String queue, String status, int limit, int offset) throws Exception {
        String q = queue == null ? "" : queue.trim().toUpperCase();
        if ("BUSINESS".equals(q)) {
            JSONObject p = new JSONObject().put("p_limit",limit).put("p_offset",offset);
            if(status!=null&&!status.isEmpty())p.put("p_status",status);
            return rpc("get_management_business_queue",p);
        }
        if ("LABOUR".equals(q)) {
            JSONObject p = new JSONObject().put("p_limit",limit).put("p_offset",offset);
            if(status!=null&&!status.isEmpty())p.put("p_status",status);
            return rpc("get_management_bulk_workforce_queue",p);
        }
        JSONObject p = new JSONObject().put("p_queue", queue).put("p_limit", limit).put("p_offset", offset).put("p_version_code", VERSION_CODE);
        if (status != null && !status.isEmpty()) p.put("p_status", status);
        return rpc("get_management_queue", p);
    }

    JSONObject detail(String resource, String id) throws Exception {
        String r=resource==null?"":resource.trim().toUpperCase();
        if("BUSINESS".equals(r)) return rpc("get_management_business_detail",new JSONObject().put("p_business_id",id));
        if("BULK_WORKFORCE".equals(r)) return rpc("get_management_bulk_workforce_detail",new JSONObject().put("p_request_id",id));
        return rpc("get_management_detail", new JSONObject().put("p_resource", resource).put("p_resource_id", id).put("p_version_code", VERSION_CODE));
    }'''
if old not in c: raise SystemExit('client queue/detail anchor missing')
c=c.replace(old,new,1)
anchor='''    JSONObject action(String action, String resourceId, JSONObject payload) throws Exception {
        return rpc("execute_management_action", new JSONObject()
                .put("p_action", action)
                .put("p_resource_id", resourceId)
                .put("p_idempotency_key", UUID.randomUUID().toString())
                .put("p_payload", payload == null ? new JSONObject() : payload)
                .put("p_version_code", VERSION_CODE));
    }
'''
addition=anchor+'''
    JSONObject businessAction(String action, String businessId, JSONObject payload) throws Exception {
        String a=action==null?"":action.trim().toUpperCase();
        String status;
        if("ACTIVATE_BUSINESS".equals(a)) status="ACTIVE";
        else if("SUSPEND_BUSINESS".equals(a)) status="SUSPENDED";
        else if("REJECT_BUSINESS".equals(a)) status="BLOCKED";
        else throw new ApiException(400,"INVALID_BUSINESS_ACTION");
        return rpc("management_set_business_status",new JSONObject()
                .put("p_business_id",businessId).put("p_status",status)
                .put("p_reason",payload==null?JSONObject.NULL:payload.optString("reason",null)));
    }

    JSONObject workforceAction(String action, String requestId, JSONObject payload) throws Exception {
        String a=action==null?"":action.trim().toUpperCase();
        if("CONVERT_TO_ORDER".equals(a)) return rpc("convert_approved_bulk_workforce_request",new JSONObject().put("p_request_id",requestId).put("p_idempotency_key",UUID.randomUUID().toString()));
        String backendAction;
        if("START_REVIEW".equals(a)) backendAction="START_REVIEW";
        else if("QUOTE_WORKFORCE".equals(a)) backendAction="QUOTE";
        else if("APPROVE_WORKFORCE".equals(a)) backendAction="APPROVE";
        else if("REJECT_WORKFORCE".equals(a)) backendAction="REJECT";
        else throw new ApiException(400,"INVALID_WORKFORCE_ACTION");
        JSONObject p=new JSONObject().put("p_request_id",requestId).put("p_action",backendAction);
        JSONObject data=payload==null?new JSONObject():payload;
        p.put("p_quoted_amount",data.has("quotedAmount")?data.optDouble("quotedAmount"):JSONObject.NULL);
        p.put("p_management_fee",data.has("managementFee")?data.optDouble("managementFee"):JSONObject.NULL);
        p.put("p_reason",data.optString("reason",null)==null?JSONObject.NULL:data.optString("reason"));
        return rpc("management_bulk_workforce_action",p);
    }
'''
if anchor not in c: raise SystemExit('client action anchor missing')
c=c.replace(anchor,addition,1)
client.write_text(c)

m=mapper.read_text()
old='''        if (has("PARTNER_NOT_FOUND", "DISPATCH_ITEM_NOT_FOUND", "SUPPORT_TICKET_NOT_FOUND", "PAYOUT_NOT_FOUND", "AUDIT_EVENT_NOT_FOUND")) {'''
new='''        if (has("BUSINESS_NOT_ACTIVE", "BULK_WORKFORCE_REQUEST_NOT_APPROVED", "BULK_WORKFORCE_QUOTE_REQUIRED")) {
            return ManagementFailureUi("BUSINESS_STATE_BLOCKED","Status belum memenuhi syarat","Periksa status Business, quotation, dan approval sebelum melanjutkan aksi ini.",false)
        }
        if (has("BUSINESS_NOT_FOUND", "BULK_WORKFORCE_REQUEST_NOT_FOUND")) {
            return ManagementFailureUi("RESOURCE_CHANGED","Data sudah berubah","Business atau permintaan Labour tidak lagi tersedia. Muat ulang workspace.",true)
        }
        if (has("PARTNER_NOT_FOUND", "DISPATCH_ITEM_NOT_FOUND", "SUPPORT_TICKET_NOT_FOUND", "PAYOUT_NOT_FOUND", "AUDIT_EVENT_NOT_FOUND")) {'''
if old not in m: raise SystemExit('mapper resource anchor missing')
m=m.replace(old,new,1)
mapper.write_text(m)

b=build.read_text()
if 'versionCode = 7' not in b or 'versionName = "1.0.5-reliability-v4"' not in b: raise SystemExit('V4 version anchor missing')
b=b.replace('versionCode = 7','versionCode = 8',1)
b=b.replace('versionName = "1.0.5-reliability-v4"','versionName = "1.0.6-business-labour-m2.23"',1)
build.write_text(b)

checks={main:['"BUSINESS"','"LABOUR"','QUOTE_WORKFORCE','onAction: (String, JSONObject) -> Unit'],client:['get_management_business_queue','get_management_bulk_workforce_queue','get_management_business_detail','get_management_bulk_workforce_detail','convert_approved_bulk_workforce_request'],mapper:['BUSINESS_STATE_BLOCKED'],build:['versionCode = 8','versionName = "1.0.6-business-labour-m2.23"']}
for path,tokens in checks.items():
    text=path.read_text()
    for token in tokens:
        if token not in text: raise SystemExit(f'missing {token} in {path}')
print('GAWONE Management M2.23 Business + Labour applied')
