from pathlib import Path
import sys
root=Path(sys.argv[1])
main=root/'app/src/main/java/site/garsyanimultiusaha/gawone/mitra/MainActivity.kt'
build=root/'app/build.gradle.kts'

b=build.read_text()
b=b.replace('versionCode = 11','versionCode = 12',1)
b=b.replace('versionName = "1.0.1-stage4j-rc2"','versionName = "1.0.2-stage4j-pilot-rc3"',1)
build.write_text(b)

s=main.read_text()
s=s.replace('JSONObject().put("p_version_code",10)','JSONObject().put("p_version_code",BuildConfig.VERSION_CODE)',1)
s=s.replace('.put("p_app_version","1.0.1-stage4j-rc2")','.put("p_app_version",BuildConfig.VERSION_NAME)',1)
s=s.replace('JSONObject().put("clientVersion","1.0.1-stage4j-rc2")','JSONObject().put("clientVersion",BuildConfig.VERSION_NAME)',1)

old='''    var busyId by remember { mutableStateOf<String?>(null) }\n    var info by remember { mutableStateOf("") }\n    val targetId = remember(deepRoute) { deepRoute?.pathSegments?.getOrNull(1) }'''
new='''    var busyId by remember { mutableStateOf<String?>(null) }\n    var info by remember { mutableStateOf("") }\n    var runtime by remember { mutableStateOf<Any?>(null) }\n    val targetId = remember(deepRoute) { deepRoute?.pathSegments?.getOrNull(1) }'''
if old not in s: raise SystemExit('orders state anchor not found')
s=s.replace(old,new,1)

old='''    suspend fun refresh() {\n        try { offers = JsonUtil.items(api.rpc("get_my_partner_offers"), "offers","items","data") } catch (e:Exception) { info = e.message ?: "Gagal memuat offer" }\n        try { assignments = JsonUtil.items(api.rpc("get_my_partner_assignments"), "assignments","items","data") } catch (_:Exception) {}\n    }'''
new='''    suspend fun refresh() {\n        try { runtime = api.rpc("get_mitra_runtime_config", JSONObject().put("p_version_code",BuildConfig.VERSION_CODE)) } catch (_:Exception) {}\n        try { offers = JsonUtil.items(api.rpc("get_my_partner_offers"), "offers","items","data") } catch (e:Exception) { info = e.message ?: "Gagal memuat offer" }\n        try { assignments = JsonUtil.items(api.rpc("get_my_partner_assignments"), "assignments","items","data") } catch (_:Exception) {}\n    }'''
if old not in s: raise SystemExit('orders refresh anchor not found')
s=s.replace(old,new,1)

old='''    LaunchedEffect(tick) { refresh() }\n\n    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(bottom=18.dp)) {'''
new='''    LaunchedEffect(tick) { refresh() }\n    val offerEnabled = runtimeFeatureEnabled(runtime, "OFFER")\n    val jobExecutionEnabled = runtimeFeatureEnabled(runtime, "JOB_EXECUTION")\n\n    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(bottom=18.dp)) {'''
if s.count(old)!=1: raise SystemExit(f'orders launched anchor count {s.count(old)}')
s=s.replace(old,new,1)

old='''        item { Spacer(Modifier.height(8.dp)); if(targetId!=null) InfoCard("Dibuka dari notifikasi", "GAWONE memprioritaskan konteks ${targetId.take(8)}… dan tetap memvalidasi status terbaru dari server."); SectionTitle("Penawaran masuk"); if (offers.isEmpty()) EmptyCard("Belum ada offer aktif") }'''
new='''        item {\n            Spacer(Modifier.height(8.dp))\n            if(targetId!=null) InfoCard("Dibuka dari notifikasi", "GAWONE memprioritaskan konteks ${targetId.take(8)}… dan tetap memvalidasi status terbaru dari server.")\n            if(!offerEnabled) InfoCard("Offer belum dibuka", "Akun ini belum masuk controlled pilot. Penawaran tidak dapat diterima sampai gate server mengizinkan OFFER.")\n            if(!jobExecutionEnabled) InfoCard("Eksekusi order belum dibuka", "Lifecycle pekerjaan tetap terkunci sampai gate JOB_EXECUTION diaktifkan untuk akun pilot.")\n            SectionTitle("Penawaran masuk")\n            if (offers.isEmpty()) EmptyCard("Belum ada offer aktif")\n        }'''
if old not in s: raise SystemExit('offer header anchor not found')
s=s.replace(old,new,1)

old='''                    Button(onClick={ scope.launch { busyId=id; try { api.rpc("accept_assignment_offer",JSONObject().put("p_offer_id",id)); info="Order diterima"; refresh() } catch(e:Exception){info=e.message?:"Gagal"}; busyId=null } }, modifier=Modifier.weight(1.3f), colors=ButtonDefaults.buttonColors(containerColor=GGreen), shape=RoundedCornerShape(12.dp)) { if(busyId==id) CircularProgressIndicator(Modifier.size(16.dp),color=Color.White,strokeWidth=2.dp) else Text("Terima Order") }'''
new='''                    Button(onClick={ scope.launch { busyId=id; try { api.rpc("accept_assignment_offer",JSONObject().put("p_offer_id",id)); info="Order diterima"; refresh() } catch(e:Exception){info=e.message?:"Gagal"}; busyId=null } }, enabled=offerEnabled && busyId==null, modifier=Modifier.weight(1.3f), colors=ButtonDefaults.buttonColors(containerColor=GGreen), shape=RoundedCornerShape(12.dp)) { if(busyId==id) CircularProgressIndicator(Modifier.size(16.dp),color=Color.White,strokeWidth=2.dp) else Text(if(offerEnabled) "Terima Order" else "Pilot terkunci") }'''
if old not in s: raise SystemExit('accept button anchor not found')
s=s.replace(old,new,1)

old='''                    } }, modifier=Modifier.fillMaxWidth(), colors=ButtonDefaults.buttonColors(containerColor=GGreen), shape=RoundedCornerShape(12.dp)) {'''
new='''                    } }, enabled=jobExecutionEnabled && busyId==null, modifier=Modifier.fillMaxWidth(), colors=ButtonDefaults.buttonColors(containerColor=GGreen), shape=RoundedCornerShape(12.dp)) {'''
if old not in s: raise SystemExit('assignment button anchor not found')
s=s.replace(old,new,1)

anchor='''private fun StatusLine(text:String){Text(text,Modifier.fillMaxWidth().padding(vertical=8.dp),fontSize=10.sp,color=if(text.contains("gagal",true)||text.contains("error",true))Color(0xFFB91C1C) else Color(0xFF15803D))}\n\nprivate val SERVICES'''
helper='''private fun StatusLine(text:String){Text(text,Modifier.fillMaxWidth().padding(vertical=8.dp),fontSize=10.sp,color=if(text.contains("gagal",true)||text.contains("error",true))Color(0xFFB91C1C) else Color(0xFF15803D))}\n\nprivate fun runtimeFeatureEnabled(runtime: Any?, code: String): Boolean {\n    val root = runtime as? JSONObject ?: return false\n    return root.optJSONObject("features")?.optBoolean(code, false) ?: false\n}\n\nprivate val SERVICES'''
if anchor not in s: raise SystemExit('helper anchor not found')
s=s.replace(anchor,helper,1)
main.write_text(s)
print('Mitra pilot RC3 patch applied')