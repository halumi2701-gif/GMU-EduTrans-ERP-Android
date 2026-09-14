from pathlib import Path

pkg=Path('app/src/main/java/site/garsyanimultiusaha/gawone/management')
main=pkg/'FullMainActivity.kt'; client=pkg/'SupabaseManagementClient.java'; mapper=pkg/'GawoneManagementFailure.kt'; build=Path('app/build.gradle.kts')
for p in (main,client,mapper,build):
    if not p.exists(): raise SystemExit(f'missing {p}')

s=main.read_text()
if 'import androidx.compose.foundation.text.KeyboardOptions' not in s:
    s=s.replace('import androidx.compose.foundation.shape.RoundedCornerShape\n','import androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.foundation.text.KeyboardOptions\n',1)
if 'import androidx.compose.ui.text.input.KeyboardType' not in s:
    s=s.replace('import androidx.compose.ui.text.input.PasswordVisualTransformation\n','import androidx.compose.ui.text.input.PasswordVisualTransformation\nimport androidx.compose.ui.text.input.KeyboardType\n',1)

old='''    private fun parseWorkspaces(nav: JSONObject): List<String> {
        val raw = nav.toString().uppercase()
        return listOf("PARTNER_REVIEW", "DISPATCH", "SUPPORT", "PAYOUT", "AUDIT").filter { raw.contains(it) }
    }'''
new='''    private fun parseWorkspaces(nav: JSONObject): List<String> {
        val raw = nav.toString().uppercase()
        val out = listOf("PARTNER_REVIEW", "DISPATCH", "SUPPORT", "PAYOUT", "AUDIT").filter { raw.contains(it) }.toMutableList()
        if (raw.contains("BUSINESS.READ") || raw.contains("BUSINESS.MANAGE")) {
            if (!out.contains("BUSINESS")) out.add(1.coerceAtMost(out.size), "BUSINESS")
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

marker='@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun DetailScreen'
pos=s.find(marker)
if pos<0: raise SystemExit('DetailScreen marker missing')
new_detail=r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(ui: ManagementUi, onBack: () -> Unit, onAction: (String, JSONObject) -> Unit) {
    var pending by remember { mutableStateOf<String?>(null) }
    var showRaw by remember { mutableStateOf(false) }
    var quoteAmount by remember { mutableStateOf("") }
    var managementFee by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
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
            } else if (!ui.loading) {
                item { GawoneManagementCard { Icon(Icons.Default.Lock, null, tint = GawoneManagementTokens.Muted); Text("Tidak ada aksi untuk role ini", fontWeight = FontWeight.ExtraBold); Text("Backend hanya mengembalikan aksi yang diizinkan untuk akun Management saat ini.", color = GawoneManagementTokens.Muted, style = MaterialTheme.typography.bodySmall) } }
            }
            item { OutlinedButton(onClick = { showRaw = !showRaw }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) { Icon(if (showRaw) Icons.Default.ExpandLess else Icons.Default.DataObject, null); Spacer(Modifier.width(8.dp)); Text(if (showRaw) "Sembunyikan data backend" else "Lihat data backend") } }
            if (showRaw && ui.detail.isNotBlank()) item { Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, GawoneManagementTokens.Border), modifier = Modifier.fillMaxWidth()) { Text(ui.detail, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall) } }
            if (!ui.error.isNullOrBlank()) item { Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Text(ui.error, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer) } }
        }
    }
    pending?.let { action ->
        val needsQuote=action=="QUOTE_WORKFORCE"
        val needsReason=action in setOf("REJECT_BUSINESS","SUSPEND_BUSINESS","REJECT_WORKFORCE")
        val quoteOk=!needsQuote || (quoteAmount.toDoubleOrNull()?.let{it>0}==true && managementFee.toDoubleOrNull()?.let{it>=0}==true)
        val valid=quoteOk && (!needsReason || reason.trim().length>=3)
        AlertDialog(
            onDismissRequest={pending=null},
            title={Text(action.replace('_',' '))},
            text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Backend akan memvalidasi status terbaru sebelum aksi disimpan.")
                if(needsQuote){
                    OutlinedTextField(quoteAmount,{quoteAmount=it.filter(Char::isDigit)},label={Text("Nilai quotation (Rp)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
                    OutlinedTextField(managementFee,{managementFee=it.filter(Char::isDigit)},label={Text("Management fee (Rp)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
                }
                if(needsReason)OutlinedTextField(reason,{reason=it},label={Text("Alasan")},minLines=2)
            }},
            confirmButton={TextButton(enabled=valid,onClick={
                val payload=JSONObject()
                if(needsQuote){payload.put("quotedAmount",quoteAmount.toDouble());payload.put("managementFee",managementFee.toDouble())}
                if(needsReason)payload.put("reason",reason.trim())
                pending=null;quoteAmount="";managementFee="";reason="";onAction(action,payload)
            }){Text("Jalankan")}},
            dismissButton={TextButton(onClick={pending=null;quoteAmount="";managementFee="";reason=""}){Text("Batal")}}
        )
    }
}
'''
s=s[:pos]+new_detail
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
        String q=queue==null?"":queue.trim().toUpperCase();
        if("BUSINESS".equals(q)){JSONObject p=new JSONObject().put("p_limit",limit).put("p_offset",offset);if(status!=null&&!status.isEmpty())p.put("p_status",status);return rpc("get_management_business_queue",p);}
        if("LABOUR".equals(q)){JSONObject p=new JSONObject().put("p_limit",limit).put("p_offset",offset);if(status!=null&&!status.isEmpty())p.put("p_status",status);return rpc("get_management_bulk_workforce_queue",p);}
        JSONObject p = new JSONObject().put("p_queue", queue).put("p_limit", limit).put("p_offset", offset).put("p_version_code", VERSION_CODE);
        if (status != null && !status.isEmpty()) p.put("p_status", status);
        return rpc("get_management_queue", p);
    }

    JSONObject detail(String resource, String id) throws Exception {
        String r=resource==null?"":resource.trim().toUpperCase();
        if("BUSINESS".equals(r))return rpc("get_management_business_detail",new JSONObject().put("p_business_id",id));
        if("BULK_WORKFORCE".equals(r))return rpc("get_management_bulk_workforce_detail",new JSONObject().put("p_request_id",id));
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
    JSONObject businessAction(String action,String businessId,JSONObject payload)throws Exception{
        String a=action==null?"":action.trim().toUpperCase();String status;
        if("ACTIVATE_BUSINESS".equals(a))status="ACTIVE";else if("SUSPEND_BUSINESS".equals(a))status="SUSPENDED";else if("REJECT_BUSINESS".equals(a))status="BLOCKED";else throw new ApiException(400,"INVALID_BUSINESS_ACTION");
        Object reason=(payload!=null&&payload.has("reason"))?payload.optString("reason"):JSONObject.NULL;
        return rpc("management_set_business_status",new JSONObject().put("p_business_id",businessId).put("p_status",status).put("p_reason",reason));
    }

    JSONObject workforceAction(String action,String requestId,JSONObject payload)throws Exception{
        String a=action==null?"":action.trim().toUpperCase();
        if("CONVERT_TO_ORDER".equals(a))return rpc("convert_approved_bulk_workforce_request",new JSONObject().put("p_request_id",requestId).put("p_idempotency_key",UUID.randomUUID().toString()));
        String backendAction;if("START_REVIEW".equals(a))backendAction="START_REVIEW";else if("QUOTE_WORKFORCE".equals(a))backendAction="QUOTE";else if("APPROVE_WORKFORCE".equals(a))backendAction="APPROVE";else if("REJECT_WORKFORCE".equals(a))backendAction="REJECT";else throw new ApiException(400,"INVALID_WORKFORCE_ACTION");
        JSONObject data=payload==null?new JSONObject():payload;JSONObject p=new JSONObject().put("p_request_id",requestId).put("p_action",backendAction);
        p.put("p_quoted_amount",data.has("quotedAmount")?data.optDouble("quotedAmount"):JSONObject.NULL).put("p_management_fee",data.has("managementFee")?data.optDouble("managementFee"):JSONObject.NULL).put("p_reason",data.has("reason")?data.optString("reason"):JSONObject.NULL);
        return rpc("management_bulk_workforce_action",p);
    }
'''
if anchor not in c: raise SystemExit('client action anchor missing')
c=c.replace(anchor,addition,1);client.write_text(c)

m=mapper.read_text(); old='''        if (has("PARTNER_NOT_FOUND", "DISPATCH_ITEM_NOT_FOUND", "SUPPORT_TICKET_NOT_FOUND", "PAYOUT_NOT_FOUND", "AUDIT_EVENT_NOT_FOUND")) {'''
new='''        if (has("BUSINESS_NOT_ACTIVE", "BULK_WORKFORCE_REQUEST_NOT_APPROVED", "BULK_WORKFORCE_QUOTE_REQUIRED")) return ManagementFailureUi("BUSINESS_STATE_BLOCKED","Status belum memenuhi syarat","Periksa status Business, quotation, dan approval sebelum melanjutkan.",false)
        if (has("BUSINESS_NOT_FOUND", "BULK_WORKFORCE_REQUEST_NOT_FOUND")) return ManagementFailureUi("RESOURCE_CHANGED","Data sudah berubah","Business atau permintaan Labour tidak lagi tersedia. Muat ulang workspace.",true)
        if (has("PARTNER_NOT_FOUND", "DISPATCH_ITEM_NOT_FOUND", "SUPPORT_TICKET_NOT_FOUND", "PAYOUT_NOT_FOUND", "AUDIT_EVENT_NOT_FOUND")) {'''
if old not in m: raise SystemExit('mapper anchor missing')
mapper.write_text(m.replace(old,new,1))

b=build.read_text()
if 'versionCode = 7' not in b or 'versionName = "1.0.5-reliability-v4"' not in b: raise SystemExit('V4 version anchor missing')
b=b.replace('versionCode = 7','versionCode = 8',1).replace('versionName = "1.0.5-reliability-v4"','versionName = "1.0.6-business-labour-m2.23"',1);build.write_text(b)
for path,tokens in {main:['"BUSINESS"','"LABOUR"','QUOTE_WORKFORCE','onAction: (String, JSONObject) -> Unit'],client:['get_management_business_queue','get_management_bulk_workforce_queue','get_management_business_detail','get_management_bulk_workforce_detail','CONVERT_TO_ORDER'],mapper:['BUSINESS_STATE_BLOCKED'],build:['versionCode = 8','1.0.6-business-labour-m2.23']}.items():
    text=path.read_text()
    for token in tokens:
        if token not in text: raise SystemExit(f'missing {token} in {path}')
print('GAWONE Management M2.23 Business + Labour V2 applied')
