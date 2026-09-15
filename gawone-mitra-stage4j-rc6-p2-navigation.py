from pathlib import Path
import sys
root=Path(sys.argv[1] if len(sys.argv)>1 else 'gawone-mitra-stage4j')
main=root/'app/src/main/java/site/garsyanimultiusaha/gawone/mitra/MainActivity.kt'
build=root/'app/build.gradle.kts'
for p in (main,build):
    if not p.exists(): raise SystemExit(f'missing {p}')

s=main.read_text()
old='''        items(assignments.sortedByDescending { JsonUtil.firstString(it,"assignmentId","assignment_id","id") == targetId }, key={ JsonUtil.firstString(it,"assignmentId","assignment_id","id") ?: it.toString().hashCode().toString() }) { a ->
            val id = JsonUtil.firstString(a,"assignmentId","assignment_id","id") ?: ""
            val status = (JsonUtil.firstString(a,"assignmentStatus","assignment_status","status") ?: "ASSIGNED").uppercase()
            DataCard(title = JsonUtil.firstString(a,"serviceName","service_name") ?: "Pekerjaan aktif", subtitle = "$status • #${id.take(8)}", data = a) {
                val next = when(status) { "ASSIGNED"->"EN_ROUTE"; "EN_ROUTE"->"ARRIVED"; "ARRIVED"->"CHECKED_IN"; "CHECKED_IN"->"START_WORK"; "WORKING"->"FINISH_WORK"; else->null }
'''
new='''        items(assignments.sortedByDescending { JsonUtil.firstString(it,"assignmentId","assignment_id","id") == targetId }, key={ JsonUtil.firstString(it,"assignmentId","assignment_id","id") ?: it.toString().hashCode().toString() }) { a ->
            val id = JsonUtil.firstString(a,"assignmentId","assignment_id","id") ?: ""
            val status = (JsonUtil.firstString(a,"assignmentStatus","assignment_status","status") ?: "ASSIGNED").uppercase()
            val serviceCode=(JsonUtil.firstString(a,"serviceCode","service_code")?:"").uppercase()
            val mobilityService=serviceCode in setOf("RIDE","CAR","DELIVERY")
            var mobility by remember(id){ mutableStateOf<JSONObject?>(null) }
            LaunchedEffect(id,status,mobilityService){
                if(mobilityService){
                    mobility=runCatching { api.rpc("get_my_partner_mobility_assignment",JSONObject().put("p_assignment_id",id)) as? JSONObject }.getOrNull()
                }
            }
            DataCard(title = JsonUtil.firstString(a,"serviceName","service_name") ?: "Pekerjaan aktif", subtitle = "$status • #${id.take(8)}", data = a) {
                if(mobilityService){
                    val targetKey=if(status in setOf("ASSIGNED","EN_ROUTE")) "pickup" else "destination"
                    val target=mobility?.optJSONObject(targetKey)
                    val route=mobility?.optJSONObject("route")
                    val lat=target?.optDouble("latitude",Double.NaN)?:Double.NaN
                    val lng=target?.optDouble("longitude",Double.NaN)?:Double.NaN
                    val label=target?.optString("address")?.ifBlank{target.optString("label")}?.ifBlank{if(targetKey=="pickup")"Lokasi jemput" else "Lokasi tujuan"} ?: if(targetKey=="pickup")"Lokasi jemput" else "Lokasi tujuan"
                    if(lat.isFinite()&&lng.isFinite()){
                        OutlinedButton(onClick={launchNavigation(context,lat,lng,label)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
                            Icon(Icons.Default.Navigation,null);Spacer(Modifier.width(7.dp));Text(if(targetKey=="pickup")"Navigasi ke Jemput" else "Navigasi ke Tujuan")
                        }
                        route?.let{r->
                            val km=r.optDouble("distanceKm",0.0);val eta=r.optInt("etaMinutes",0)
                            if(km>0)Text("Rute order ${String.format(java.util.Locale.US,"%.1f",km)} km • ETA ${eta} mnt",fontSize=9.sp,color=GMuted,modifier=Modifier.padding(top=6.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }else InfoCard("Rute belum siap","Koordinat jemput/tujuan belum tersedia dari server.")
                }
                val next = when(status) { "ASSIGNED"->"EN_ROUTE"; "EN_ROUTE"->"ARRIVED"; "ARRIVED"->"CHECKED_IN"; "CHECKED_IN"->"START_WORK"; "WORKING"->"FINISH_WORK"; else->null }
'''
if old not in s: raise SystemExit('assignment anchor missing')
s=s.replace(old,new,1)

marker='''@Composable
private fun ChatScreen(api: SupabaseApi, queue: OfflineQueue, tick: Int, deepRoute: Uri?, onDeepLinkConsumed: () -> Unit) {'''
helper='''private fun launchNavigation(context: Context, lat:Double, lng:Double, label:String){
    val geo=Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
    val nativeIntent=Intent(Intent.ACTION_VIEW,geo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching{context.startActivity(nativeIntent)}.onFailure{
        val web=Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
        runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
    }
}

@Composable
private fun ChatScreen(api: SupabaseApi, queue: OfflineQueue, tick: Int, deepRoute: Uri?, onDeepLinkConsumed: () -> Unit) {'''
if marker not in s: raise SystemExit('chat marker missing')
s=s.replace(marker,helper,1)
main.write_text(s)

b=build.read_text()
if 'versionCode = 14' not in b or 'versionName = "1.0.4-stage4j-nine-services-rc5"' not in b: raise SystemExit('RC5 version anchor missing')
b=b.replace('versionCode = 14','versionCode = 15',1)
b=b.replace('versionName = "1.0.4-stage4j-nine-services-rc5"','versionName = "1.0.5-p2-navigation-rc6"',1)
build.write_text(b)

out=main.read_text()
for token in ('get_my_partner_mobility_assignment','Navigasi ke Jemput','Navigasi ke Tujuan','launchNavigation'):
    if token not in out: raise SystemExit('RC6 token missing '+token)
print('GAWONE Mitra RC6 P2 navigation applied')
