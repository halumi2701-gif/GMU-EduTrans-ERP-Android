from pathlib import Path
import sys

root=Path(sys.argv[1] if len(sys.argv)>1 else 'gawone-customer-production')
main=root/'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
repo=root/'app/src/main/java/site/garsyanimultiusaha/gawone/CustomerRepository.kt'
build=root/'app/build.gradle.kts'
for p in (main, repo, build):
    if not p.exists(): raise SystemExit(f'missing {p}')

r=repo.read_text()
old='''    suspend fun startMatching(orderId:String):JSONObject = api.rpc("start_matching",JSONObject().put("p_order_id",orderId).put("p_slot_no",JSONObject.NULL).put("p_idempotency_key","match-$orderId")) as JSONObject\n'''
new=old+'''    suspend fun retryMatching(orderId:String):JSONObject = api.rpc("start_matching",JSONObject().put("p_order_id",orderId).put("p_slot_no",JSONObject.NULL).put("p_idempotency_key","retry-$orderId-${UUID.randomUUID()}")) as JSONObject\n'''
if old not in r: raise SystemExit('startMatching anchor missing')
r=r.replace(old,new,1)
repo.write_text(r)

s=main.read_text()
s=s.replace(
'''    var d by remember{mutableStateOf<JSONObject?>(null)};var info by remember{mutableStateOf("")};var completing by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()''',
'''    var d by remember{mutableStateOf<JSONObject?>(null)};var info by remember{mutableStateOf("")};var completing by remember{mutableStateOf(false)};var retryingMatching by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()''',1)
s=s.replace(
'''    val chatEnabled=featureEnabled(runtime,"CHAT");val supportEnabled=featureEnabled(runtime,"SUPPORT");val ratingEnabled=featureEnabled(runtime,"RATING")\n    val assignments=d?.optJSONArray("assignments");val first=if(assignments!=null&&assignments.length()>0)assignments.optJSONObject(0)else null\n    val canRate=ratingEnabled&&first?.optString("status")=="FINISHED"''',
'''    val chatEnabled=featureEnabled(runtime,"CHAT");val supportEnabled=featureEnabled(runtime,"SUPPORT");val ratingEnabled=featureEnabled(runtime,"RATING");val matchingEnabled=featureEnabled(runtime,"MATCHING")\n    val matching=d?.optJSONObject("matching")\n    val assignments=d?.optJSONArray("assignments");val first=if(assignments!=null&&assignments.length()>0)assignments.optJSONObject(0)else null\n    val canRate=ratingEnabled&&first?.optString("status")=="FINISHED"\n    val canRetryMatching=matchingEnabled&&status=="SEARCHING"&&matchingUnavailable(matching)''',1)
s=s.replace('FailureGuidance(status,paymentEnabled,d?.optJSONObject("matching"))','FailureGuidance(status,paymentEnabled,matching)',1)
anchor='''                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){\n                    OutlinedButton(onClick=onChat,enabled=chatEnabled,modifier=Modifier.weight(1f)){Icon(Icons.Default.Chat,null);Spacer(Modifier.width(6.dp));Text("Chat")}\n                    OutlinedButton(onClick=onSupport,enabled=supportEnabled,modifier=Modifier.weight(1f)){Icon(Icons.Default.SupportAgent,null);Spacer(Modifier.width(6.dp));Text("Bantuan")}\n                }\n'''
block=anchor+'''                if(canRetryMatching){\n                    Spacer(Modifier.height(9.dp))\n                    Button(\n                        enabled=!retryingMatching,\n                        onClick={\n                            val id=orderId\n                            if(id!=null)scope.launch{\n                                retryingMatching=true;info=""\n                                try{\n                                    val out=repo.retryMatching(id)\n                                    load()\n                                    info=if(out.optString("code")=="NO_PARTNER_AVAILABLE")\n                                        out.optString("message","Belum ada Mitra tersedia. Coba lagi beberapa saat lagi.")\n                                    else "Pencarian Mitra diperbarui. GAWONE akan menyinkronkan status terbaru."\n                                }catch(e:Exception){info=customerSafeError(e,"Belum dapat mencari Mitra lagi")}finally{retryingMatching=false}\n                            }\n                        },\n                        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=GGreen)\n                    ){Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(7.dp));Text(if(retryingMatching)"Mencari ulang…" else "Cari Mitra Lagi")}\n                }\n'''
if anchor not in s: raise SystemExit('button row anchor missing')
s=s.replace(anchor,block,1)
anchor='''@Composable fun FailureGuidance(status:String,paymentEnabled:Boolean,matching:JSONObject?){\n'''
helper='''fun matchingUnavailable(matching:JSONObject?):Boolean{\n    if(matching==null)return false\n    val code=matching.optString("code").uppercase()\n    val status=matching.optString("status").uppercase()\n    val reason=matching.optString("failureReason").uppercase()\n    return code=="NO_PARTNER_AVAILABLE" || status=="FAILED" || reason in setOf("NO_PARTNER_AVAILABLE","NO_ELIGIBLE_PARTNER","OFFER_EXPIRED_ALL")\n}\n\n'''+anchor
if anchor not in s: raise SystemExit('FailureGuidance anchor missing')
s=s.replace(anchor,helper,1)
s=s.replace(
'''        status=="SEARCHING" && matching?.optString("status")=="FAILED"->WarningCard("Mitra belum tersedia",if(matching.optString("failureReason").isBlank())"Belum ada Mitra eligible di area Anda. Coba lagi nanti." else "Pencarian selesai tanpa Mitra eligible. Pesanan tetap tersimpan aman.")''',
'''        status=="SEARCHING" && matchingUnavailable(matching)->WarningCard("Mitra belum tersedia","Belum ada Mitra eligible saat ini. Pesanan tetap tersimpan aman dan Anda dapat mencoba pencarian ulang.")''',1)
main.write_text(s)

b=build.read_text()
if 'versionCode = 21' not in b or 'versionName = "1.0.11-mobility-foundation-rc12"' not in b: raise SystemExit('RC12 version anchor missing')
b=b.replace('versionCode = 21','versionCode = 22',1)
b=b.replace('versionName = "1.0.11-mobility-foundation-rc12"','versionName = "1.0.12-authoritative-recovery-rc13"',1)
build.write_text(b)

checks={
    main:['retryingMatching','Cari Mitra Lagi','matchingUnavailable(matching)','repo.retryMatching(id)'],
    repo:['suspend fun retryMatching','retry-$orderId-${UUID.randomUUID()}'],
    build:['versionCode = 22','versionName = "1.0.12-authoritative-recovery-rc13"']
}
for path,tokens in checks.items():
    text=path.read_text()
    for token in tokens:
        if token not in text: raise SystemExit(f'verification missing {token} in {path}')
print('GAWONE Customer RC13 authoritative recovery applied')
