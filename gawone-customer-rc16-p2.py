from pathlib import Path
import shutil

root=Path('gawone-customer-production')
build=root/'app/build.gradle.kts'
main=root/'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
repo=root/'app/src/main/java/site/garsyanimultiusaha/gawone/CustomerRepository.kt'
api=root/'app/src/main/java/site/garsyanimultiusaha/gawone/SupabaseApi.kt'
loc=root/'app/src/main/java/site/garsyanimultiusaha/gawone/LocationBridge.kt'
mobility=root/'app/src/main/java/site/garsyanimultiusaha/gawone/MobilityUi.kt'
for p in (build,main,repo,api,loc):
    if not p.exists(): raise SystemExit(f'missing {p}')

# Build/version/dependencies
s=build.read_text()
if 'versionCode = 24' not in s or '1.0.14-p1-nine-services-complete-rc15' not in s: raise SystemExit('RC15 build anchor missing')
s=s.replace('versionCode = 24','versionCode = 25',1)
s=s.replace('versionName = "1.0.14-p1-nine-services-complete-rc15"','versionName = "1.0.15-p2-maps-live-mobility-rc16"',1)
anchor='    implementation("com.squareup.okhttp3:okhttp:4.12.0")\n'
if anchor not in s: raise SystemExit('dependency anchor missing')
s=s.replace(anchor,anchor+'    implementation("com.google.android.gms:play-services-location:21.3.0")\n    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")\n',1)
build.write_text(s)

# Fused current location
s=loc.read_text()
if 'suspend fun current(context: Context)' not in s:
    s=s.replace('import androidx.core.content.ContextCompat\n','import androidx.core.content.ContextCompat\nimport com.google.android.gms.location.LocationServices\nimport com.google.android.gms.location.Priority\nimport kotlinx.coroutines.tasks.await\n',1)
    anchor='    fun bestLastKnown(context: Context): Location? {\n'
    insert='''    suspend fun current(context: Context): Location {\n        if (!hasLocationPermission(context)) throw SecurityException("LOCATION_PERMISSION_REQUIRED")\n        val client=LocationServices.getFusedLocationProviderClient(context)\n        return client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,null).await()\n            ?: client.lastLocation.await()\n            ?: throw IllegalStateException("LOCATION_UNAVAILABLE")\n    }\n\n'''
    if anchor not in s: raise SystemExit('location anchor missing')
    s=s.replace(anchor,insert+anchor,1)
loc.write_text(s)

# Edge Functions client with refresh-on-401
s=api.read_text()
if 'suspend fun edge(' not in s:
    marker='    fun realtimeUrl():String=base.replace("https://","wss://")+"/realtime/v1/websocket?apikey="+key+"&vsn=1.0.0"\n'
    edge='''    suspend fun edge(name:String,body:JSONObject=JSONObject(),authenticated:Boolean=true):Any=withContext(Dispatchers.IO){\n        val url="$base/functions/v1/$name"\n        try{ request("POST",url,body,authenticated) }\n        catch(e:ApiException){\n            if(authenticated && e.status==401){\n                if(refresh()) request("POST",url,body,true)\n                else{ store.clear();Handler(Looper.getMainLooper()).post{onSessionExpired()};throw ApiException(401,"Sesi berakhir. Verifikasi ulang untuk melanjutkan.") }\n            }else throw e\n        }\n    }\n'''
    if marker not in s: raise SystemExit('api marker missing')
    s=s.replace(marker,edge+marker,1)
api.write_text(s)

# Mobility repository contract
s=repo.read_text()
marker='    suspend fun routeMetric(orderId:String):JSONObject = api.rpc("get_my_order_route_metric",JSONObject().put("p_order_id",orderId)) as JSONObject\n'
if 'suspend fun mobilityState(' not in s:
    add='''    suspend fun mobilityState(orderId:String):JSONObject = api.rpc("get_my_mobility_state",JSONObject().put("p_order_id",orderId)) as JSONObject\n    suspend fun mobilitySearch(query:String):JSONObject = api.edge("mobility-route",JSONObject().put("action","search").put("query",query).put("limit",6)) as JSONObject\n    suspend fun mobilityReverse(latitude:Double,longitude:Double):JSONObject = api.edge("mobility-route",JSONObject().put("action","reverse").put("point",JSONObject().put("lat",latitude).put("lng",longitude))) as JSONObject\n    suspend fun calculateRoute(orderId:String):JSONObject = api.edge("mobility-route",JSONObject().put("action","route").put("orderId",orderId)) as JSONObject\n    suspend fun mobilityTracking(orderId:String):JSONObject = api.edge("mobility-tracking",JSONObject().put("orderId",orderId)) as JSONObject\n'''
    if marker not in s: raise SystemExit('repo marker missing')
    s=s.replace(marker,marker+add,1)
repo.write_text(s)

# Main UI wiring
s=main.read_text()
old='AppScreen.ORDER_FORM->OrderFormScreen(selectedService,orderDraft,onBack={screen=AppScreen.HOME},onContinue={orderDraft=it;screen=AppScreen.CONFIRM})'
if old not in s: raise SystemExit('root order form anchor missing')
s=s.replace(old,'AppScreen.ORDER_FORM->OrderFormScreen(repo,selectedService,orderDraft,onBack={screen=AppScreen.HOME},onContinue={orderDraft=it;screen=AppScreen.CONFIRM})',1)
old='fun OrderFormScreen(service:ServiceUi?,draft:OrderDraftUi,onBack:()->Unit,onContinue:(OrderDraftUi)->Unit){'
if old not in s: raise SystemExit('order form signature missing')
s=s.replace(old,'fun OrderFormScreen(repo:CustomerRepository,service:ServiceUi?,draft:OrderDraftUi,onBack:()->Unit,onContinue:(OrderDraftUi)->Unit){',1)

start=s.index('fun OrderFormScreen('); end=s.index('\n@Composable\nfun ConfirmOrderScreen',start); seg=s[start:end]
seg=seg.replace('    val code=service?.code?.uppercase()?:"CLEANING"\n','    val code=service?.code?.uppercase()?:"CLEANING"\n    val mobilityService=code in setOf("RIDE","CAR","DELIVERY")\n',1)
anchor='    var workerCount by remember(draft){mutableIntStateOf(draft.workerCount.coerceIn(1,20))}\n'
if anchor not in seg: raise SystemExit('worker state anchor missing')
seg=seg.replace(anchor,anchor+'    var pickup by remember(draft){mutableStateOf(MobilityPointUi(draft.pickupAddress,draft.pickupLatitude,draft.pickupLongitude))}\n    var destination by remember(draft){mutableStateOf(MobilityPointUi(draft.destinationAddress,draft.destinationLatitude,draft.destinationLongitude))}\n',1)
old='    val formReady=address.isNotBlank() && (!scheduledService || scheduledStart.isNotBlank()) && (!problemRequired || problemDetail.isNotBlank()) && (!workersRequired || workerCount>=1)\n'
new='    val mobilityReady=!mobilityService || (pickup.label.isNotBlank() && pickup.lat!=null && pickup.lng!=null && destination.label.isNotBlank() && destination.lat!=null && destination.lng!=null)\n    val formReady=(if(mobilityService) mobilityReady else address.isNotBlank()) && (!scheduledService || scheduledStart.isNotBlank()) && (!problemRequired || problemDetail.isNotBlank()) && (!workersRequired || workerCount>=1)\n'
if old not in seg: raise SystemExit('formReady anchor missing')
seg=seg.replace(old,new,1)
old='''                SectionTitle("Lokasi pekerjaan")\n                GTextField(address,{address=it},"Alamat lengkap",KeyboardType.Text)\n                Spacer(Modifier.height(9.dp))\n                OutlinedButton(onClick={capture()},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.MyLocation,null);Spacer(Modifier.width(8.dp));Text(if(latitude!=null&&longitude!=null)"Perbarui Lokasi GPS" else "Gunakan Lokasi Saat Ini")}\n                if(latitude!=null&&longitude!=null){Spacer(Modifier.height(7.dp));InfoBox("GPS siap untuk matching • ${String.format(Locale.US,"%.5f",latitude)}, ${String.format(Locale.US,"%.5f",longitude)}")}\n                if(locationInfo.isNotBlank()){Spacer(Modifier.height(7.dp));InfoBox(locationInfo)}\n                Spacer(Modifier.height(10.dp));GTextField(notes,{notes=it},"Catatan untuk Mitra",KeyboardType.Text)\n'''
new='''                if(mobilityService){\n                    MobilityEndpointEditor(repo,"Lokasi jemput",pickup,true){pickup=it}\n                    Spacer(Modifier.height(14.dp))\n                    MobilityEndpointEditor(repo,"Lokasi tujuan",destination,false){destination=it}\n                    if(pickup.lat!=null&&destination.lat!=null){Spacer(Modifier.height(12.dp));MobilityMapPreview(pickup,destination)}\n                }else{\n                    SectionTitle("Lokasi pekerjaan")\n                    GTextField(address,{address=it},"Alamat lengkap",KeyboardType.Text)\n                    Spacer(Modifier.height(9.dp))\n                    OutlinedButton(onClick={capture()},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.MyLocation,null);Spacer(Modifier.width(8.dp));Text(if(latitude!=null&&longitude!=null)"Perbarui Lokasi GPS" else "Gunakan Lokasi Saat Ini")}\n                    if(latitude!=null&&longitude!=null){Spacer(Modifier.height(7.dp));InfoBox("GPS siap untuk matching • ${String.format(Locale.US,"%.5f",latitude)}, ${String.format(Locale.US,"%.5f",longitude)}")}\n                    if(locationInfo.isNotBlank()){Spacer(Modifier.height(7.dp));InfoBox(locationInfo)}\n                }\n                Spacer(Modifier.height(10.dp));GTextField(notes,{notes=it},"Catatan untuk Mitra",KeyboardType.Text)\n'''
if old not in seg: raise SystemExit('location form block missing')
seg=seg.replace(old,new,1)
old='            onContinue(OrderDraftUi(draft.draftId,address,duration,notes,latitude,longitude,scheduledStart,problemDetail,workerCount))\n'
new='''            onContinue(OrderDraftUi(\n                draftId=draft.draftId,address=if(mobilityService)pickup.label else address,duration=duration,notes=notes,\n                latitude=if(mobilityService)pickup.lat else latitude,longitude=if(mobilityService)pickup.lng else longitude,\n                scheduledStart=scheduledStart,problemDetail=problemDetail,workerCount=workerCount,\n                pickupAddress=pickup.label,pickupLatitude=pickup.lat,pickupLongitude=pickup.lng,\n                destinationAddress=destination.label,destinationLatitude=destination.lat,destinationLongitude=destination.lng\n            ))\n'''
if old not in seg: raise SystemExit('draft constructor anchor missing')
seg=seg.replace(old,new,1)
s=s[:start]+seg+s[end:]

start=s.index('fun ConfirmOrderScreen('); end=s.index('\n@Composable fun OrdersScreen',start); seg=s[start:end]
seg=seg.replace('    val code=service?.code?.uppercase()?:"CLEANING"\n','    val code=service?.code?.uppercase()?:"CLEANING"\n    val mobilityService=code in setOf("RIDE","CAR","DELIVERY")\n',1)
old='                DataCard("Lokasi",draft.address)\n'
new='''                if(mobilityService){\n                    DataCard("Jemput",draft.pickupAddress);DataCard("Tujuan",draft.destinationAddress)\n                    MobilityMapPreview(MobilityPointUi(draft.pickupAddress,draft.pickupLatitude,draft.pickupLongitude),MobilityPointUi(draft.destinationAddress,draft.destinationLatitude,draft.destinationLongitude))\n                }else DataCard("Lokasi",draft.address)\n'''
if old not in seg: raise SystemExit('confirm location anchor missing')
seg=seg.replace(old,new,1)
old='                DataCard("GPS",if(draft.latitude!=null&&draft.longitude!=null)"Tersimpan" else "Belum tersedia")\n'
new='                DataCard("GPS",if(if(mobilityService) draft.pickupLatitude!=null&&draft.destinationLatitude!=null else draft.latitude!=null&&draft.longitude!=null)"Tersimpan" else "Belum tersedia")\n'
if old not in seg: raise SystemExit('confirm GPS anchor missing')
seg=seg.replace(old,new,1)
old='        val gpsReady=draft.latitude!=null&&draft.longitude!=null\n'
new='        val gpsReady=if(mobilityService) draft.pickupLatitude!=null&&draft.pickupLongitude!=null&&draft.destinationLatitude!=null&&draft.destinationLongitude!=null else draft.latitude!=null&&draft.longitude!=null\n'
if old not in seg: raise SystemExit('gpsReady anchor missing')
seg=seg.replace(old,new,1)
old='''                    val d=repo.createDraft(code,draft)\n                    orderId=d.getString("id")\n                    estimate=repo.estimate(orderId!!,if(durationRequired)draft.duration else 1)\n                    info="Estimasi authoritative diterima dari server."\n'''
new='''                    val d=repo.createDraft(code,draft)\n                    orderId=d.getString("id")\n                    if(mobilityService){\n                        val route=repo.calculateRoute(orderId!!)\n                        val km=route.optDouble("distanceMeters",0.0)/1000.0\n                        val eta=route.optInt("etaMinutes",0)\n                        info="Rute server siap • ${String.format(Locale.US,"%.1f",km)} km • ETA ${eta} mnt"\n                    }\n                    estimate=repo.estimate(orderId!!,if(durationRequired)draft.duration else 1)\n                    if(!mobilityService)info="Estimasi authoritative diterima dari server."\n'''
if old not in seg: raise SystemExit('route-before-estimate anchor missing')
seg=seg.replace(old,new,1)
s=s[:start]+seg+s[end:]

start=s.index('fun OrderDetailScreen('); end=s.index('\n@Composable\nfun ChatScreen',start); seg=s[start:end]
old='''                StatusHero(status);Spacer(Modifier.height(10.dp));OrderTimeline(status);Spacer(Modifier.height(10.dp));d?.optJSONObject("price")?.let{PriceCard(it.optDouble("totalAmount",0.0),true)}\n                Spacer(Modifier.height(10.dp));AssignmentCard(assignments);Spacer(Modifier.height(10.dp));FailureGuidance(status,paymentEnabled,matching)\n'''
new='''                StatusHero(status);Spacer(Modifier.height(10.dp));OrderTimeline(status);Spacer(Modifier.height(10.dp));d?.optJSONObject("price")?.let{PriceCard(it.optDouble("totalAmount",0.0),true)}\n                Spacer(Modifier.height(10.dp));AssignmentCard(assignments)\n                val serviceCode=d?.optJSONObject("service")?.optString("code")?.uppercase().orEmpty()\n                if(orderId!=null && serviceCode in setOf("RIDE","CAR","DELIVERY")){\n                    Spacer(Modifier.height(10.dp));MobilityTrackingCard(repo,orderId,featureEnabled(runtime,"MAPS")&&featureEnabled(runtime,"MOBILITY"))\n                }\n                Spacer(Modifier.height(10.dp));FailureGuidance(status,paymentEnabled,matching)\n'''
if old not in seg: raise SystemExit('order detail mobility anchor missing')
seg=seg.replace(old,new,1)
s=s[:start]+seg+s[end:]
main.write_text(s)

# Copy dedicated P2 UI source staged at repo root
source=Path('gawone-customer-rc16-MobilityUi.kt')
if not source.exists(): raise SystemExit('MobilityUi source missing')
shutil.copyfile(source,mobility)

# Static contract gate
checks={
    build:['versionCode = 25','1.0.15-p2-maps-live-mobility-rc16','play-services-location'],
    repo:['mobilitySearch','mobilityReverse','calculateRoute','mobilityTracking','get_my_mobility_state'],
    main:['MobilityEndpointEditor','MobilityTrackingCard','repo.calculateRoute'],
    mobility:['Live Mobility','tile.openstreetmap.org','decodePolyline','Lokasi tujuan'],
}
for path,tokens in checks.items():
    text=path.read_text()
    for token in tokens:
        if token not in text: raise SystemExit(f'RC16 token missing: {path}: {token}')
print('GAWONE Customer RC16 P2 Maps & Live Mobility applied')
