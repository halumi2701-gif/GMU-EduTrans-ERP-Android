package site.garsyanimultiusaha.gawone

import android.Manifest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val MobilityGreen=Color(0xFF22C55E)
private val MobilityDark=Color(0xFF1F2937)
private val MobilityMuted=Color(0xFF64748B)
private val MobilityBorder=Color(0xFFE5E7EB)

data class MobilityPointUi(
    val label:String="",
    val lat:Double?=null,
    val lng:Double?=null,
    val placeId:String?=null,
)

data class MobilityPlaceUi(
    val label:String,
    val lat:Double,
    val lng:Double,
    val placeId:String?,
    val provider:String,
)

fun mobilityPlaces(payload:JSONObject):List<MobilityPlaceUi>{
    val arr=payload.optJSONArray("results")?:JSONArray()
    return (0 until arr.length()).mapNotNull { i ->
        val o=arr.optJSONObject(i)?:return@mapNotNull null
        val lat=o.optDouble("lat",Double.NaN);val lng=o.optDouble("lng",Double.NaN)
        if(!lat.isFinite()||!lng.isFinite())null else MobilityPlaceUi(
            o.optString("label","Lokasi"),lat,lng,o.optString("placeId").ifBlank{null},o.optString("provider")
        )
    }
}

@Composable
fun MobilityEndpointEditor(
    repo:CustomerRepository,
    title:String,
    value:MobilityPointUi,
    currentLocationAllowed:Boolean,
    onChange:(MobilityPointUi)->Unit,
){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var query by remember(value.label){mutableStateOf(value.label)}
    var results by remember{mutableStateOf<List<MobilityPlaceUi>>(emptyList())}
    var info by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}

    suspend fun useCurrent(){
        busy=true;info=""
        try{
            val loc=LocationBridge.current(context)
            val reverse=repo.mobilityReverse(loc.latitude,loc.longitude).optJSONObject("result")
            val label=reverse?.optString("label")?.ifBlank{null}?:"Lokasi saat ini"
            val place=MobilityPointUi(label,loc.latitude,loc.longitude,reverse?.optString("placeId")?.ifBlank{null})
            query=label;results=emptyList();onChange(place);info="Lokasi GPS siap"
        }catch(e:Exception){info=customerSafeError(e,"Lokasi saat ini belum tersedia")}
        finally{busy=false}
    }

    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->
        if(grants[Manifest.permission.ACCESS_FINE_LOCATION]==true || grants[Manifest.permission.ACCESS_COARSE_LOCATION]==true){
            scope.launch{useCurrent()}
        }else info="Izin lokasi diperlukan untuk memakai lokasi saat ini."
    }

    Column(Modifier.fillMaxWidth()){
        Text(title,fontSize=11.sp,fontWeight=FontWeight.ExtraBold,color=MobilityDark)
        Spacer(Modifier.height(6.dp))
        GTextField(query,{query=it;if(it!=value.label)onChange(MobilityPointUi(label=it))},"Cari alamat / tempat",KeyboardType.Text)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
            OutlinedButton(
                onClick={scope.launch{
                    if(query.trim().length<3){info="Ketik minimal 3 karakter.";return@launch}
                    busy=true;info=""
                    try{results=mobilityPlaces(repo.mobilitySearch(query.trim()));if(results.isEmpty())info="Lokasi tidak ditemukan."}
                    catch(e:Exception){info=customerSafeError(e,"Pencarian lokasi gagal")}
                    finally{busy=false}
                }},
                enabled=!busy,
                modifier=Modifier.weight(1f),
                shape=RoundedCornerShape(13.dp)
            ){Icon(Icons.Default.Search,null);Spacer(Modifier.width(5.dp));Text("Cari")}
            if(currentLocationAllowed){
                OutlinedButton(
                    onClick={
                        if(LocationBridge.hasLocationPermission(context))scope.launch{useCurrent()}
                        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    enabled=!busy,
                    modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(13.dp)
                ){Icon(Icons.Default.MyLocation,null);Spacer(Modifier.width(5.dp));Text("Lokasi Saya")}
            }
        }
        if(busy){Spacer(Modifier.height(7.dp));LinearProgressIndicator(Modifier.fillMaxWidth(),color=MobilityGreen)}
        if(results.isNotEmpty()){
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().border(1.dp,MobilityBorder,RoundedCornerShape(14.dp))){
                results.take(6).forEachIndexed{index,p->
                    Row(
                        Modifier.fillMaxWidth().clickable{
                            val selected=MobilityPointUi(p.label,p.lat,p.lng,p.placeId)
                            query=p.label;results=emptyList();onChange(selected);info="Lokasi dipilih"
                        }.padding(11.dp)
                    ){
                        Icon(Icons.Default.LocationOn,null,tint=MobilityGreen,modifier=Modifier.size(18.dp));Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)){Text(p.label,fontSize=9.sp,fontWeight=FontWeight.SemiBold,color=MobilityDark,maxLines=2);Text(p.provider,fontSize=7.sp,color=MobilityMuted)}
                    }
                    if(index<results.take(6).lastIndex)HorizontalDivider(color=MobilityBorder)
                }
            }
        }
        if(value.lat!=null&&value.lng!=null){
            Spacer(Modifier.height(7.dp));Text("${String.format(Locale.US,"%.5f",value.lat)}, ${String.format(Locale.US,"%.5f",value.lng)}",fontSize=8.sp,color=MobilityMuted)
        }
        if(info.isNotBlank()){Spacer(Modifier.height(6.dp));InfoBox(info)}
    }
}

@Composable
fun MobilityMapPreview(
    pickup:MobilityPointUi?,
    destination:MobilityPointUi?,
    partner:MobilityPointUi?=null,
    encodedPolyline:String?=null,
    polylineFormat:String?=null,
    modifier:Modifier=Modifier.fillMaxWidth().height(220.dp),
){
    val points=remember(encodedPolyline,polylineFormat){
        if(encodedPolyline.isNullOrBlank()) emptyList() else decodePolyline(encodedPolyline,if(polylineFormat=="POLYLINE6")6 else 5)
    }
    val html=remember(pickup,destination,partner,points){mobilityMapHtml(pickup,destination,partner,points)}
    AndroidView(
        modifier=modifier.border(1.dp,MobilityBorder,RoundedCornerShape(16.dp)),
        factory={ctx->
            WebView(ctx).apply{
                settings.javaScriptEnabled=true
                settings.domStorageEnabled=false
                settings.allowFileAccess=false
                settings.allowContentAccess=false
                settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient=WebViewClient()
                setBackgroundColor(android.graphics.Color.WHITE)
            }
        },
        update={it.loadDataWithBaseURL("https://gawone.local/",html,"text/html","UTF-8",null)}
    )
}

private fun mobilityMapHtml(pickup:MobilityPointUi?,destination:MobilityPointUi?,partner:MobilityPointUi?,route:List<Pair<Double,Double>>):String{
    val markers=mutableListOf<String>()
    pickup?.takeIf{it.lat!=null&&it.lng!=null}?.let{markers+="L.marker([${it.lat},${it.lng}]).addTo(map).bindPopup('Jemput');bounds.push([${it.lat},${it.lng}]);"}
    destination?.takeIf{it.lat!=null&&it.lng!=null}?.let{markers+="L.marker([${it.lat},${it.lng}]).addTo(map).bindPopup('Tujuan');bounds.push([${it.lat},${it.lng}]);"}
    partner?.takeIf{it.lat!=null&&it.lng!=null}?.let{markers+="L.circleMarker([${it.lat},${it.lng}],{radius:8,color:'#16a34a',fillColor:'#22c55e',fillOpacity:1}).addTo(map).bindPopup('Mitra');bounds.push([${it.lat},${it.lng}]);"}
    val routeJs=if(route.isEmpty())"" else "const route=[${route.joinToString(","){"[${it.first},${it.second}]"}}];L.polyline(route,{color:'#16a34a',weight:5,opacity:.85}).addTo(map);route.forEach(p=>bounds.push(p));"
    return """<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no'><link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'><style>html,body,#map{height:100%;margin:0;background:#f8fafc}.leaflet-control-attribution{font-size:7px}</style></head><body><div id='map'></div><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>const map=L.map('map',{zoomControl:true,attributionControl:true});L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);const bounds=[];${markers.joinToString("")}$routeJs;if(bounds.length>1){map.fitBounds(bounds,{padding:[24,24]});}else if(bounds.length===1){map.setView(bounds[0],15);}else{map.setView([-6.82,107.14],12);}</script></body></html>"""
}

fun decodePolyline(encoded:String,precision:Int):List<Pair<Double,Double>>{
    val factor=Math.pow(10.0,precision.toDouble())
    var index=0;var lat=0;var lng=0
    val out=ArrayList<Pair<Double,Double>>()
    while(index<encoded.length){
        var result=0;var shift=0;var b:Int
        do{if(index>=encoded.length)return out;b=encoded[index++].code-63;result=result or ((b and 0x1f) shl shift);shift+=5}while(b>=0x20)
        lat+=if((result and 1)!=0)(result shr 1).inv() else result shr 1
        result=0;shift=0
        do{if(index>=encoded.length)return out;b=encoded[index++].code-63;result=result or ((b and 0x1f) shl shift);shift+=5}while(b>=0x20)
        lng+=if((result and 1)!=0)(result shr 1).inv() else result shr 1
        out+=lat/factor to lng/factor
    }
    return out
}

@Composable
fun MobilityTrackingCard(repo:CustomerRepository,orderId:String,enabled:Boolean){
    if(!enabled)return
    var state by remember(orderId){mutableStateOf<JSONObject?>(null)}
    var tracking by remember(orderId){mutableStateOf<JSONObject?>(null)}
    var info by remember{mutableStateOf("")}
    var refreshing by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()

    suspend fun load(){
        try{
            state=repo.mobilityState(orderId)
            tracking=runCatching{repo.mobilityTracking(orderId)}.getOrNull()
            info=""
        }catch(e:Exception){info=customerSafeError(e,"Live Mobility belum dapat dimuat")}
    }
    LaunchedEffect(orderId,enabled){while(enabled){load();delay(5000)}}

    val route=state?.optJSONObject("route")
    val partnerObj=state?.optJSONObject("partner")
    val locations=state?.optJSONArray("locations")
    fun point(type:String):MobilityPointUi?{
        if(locations==null)return null
        for(i in 0 until locations.length()){
            val o=locations.optJSONObject(i)?:continue
            if(o.optString("type")==type){
                val lat=o.optDouble("latitude",Double.NaN);val lng=o.optDouble("longitude",Double.NaN)
                if(lat.isFinite()&&lng.isFinite())return MobilityPointUi(o.optString("address"),lat,lng,o.optString("placeId").ifBlank{null})
            }
        }
        return null
    }
    val pickup=point("PICKUP");val destination=point("DESTINATION")
    val partner=partnerObj?.let{p->
        val lat=p.optDouble("latitude",Double.NaN);val lng=p.optDouble("longitude",Double.NaN)
        if(lat.isFinite()&&lng.isFinite())MobilityPointUi(p.optString("displayName","Mitra GAWONE"),lat,lng)else null
    }
    val distanceKm=route?.optDouble("distanceKm",0.0)?:0.0
    val eta=route?.optInt("etaMinutes",0)?:0
    val trackingEta=tracking?.optInt("etaMinutes",0)?:0
    val trackingDistance=(tracking?.optDouble("distanceMeters",0.0)?:0.0)/1000.0

    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFBBF7D0)),colors=CardDefaults.cardColors(containerColor=Color.White)){
        Column(Modifier.padding(14.dp)){
            Row(Modifier.fillMaxWidth()){
                Column(Modifier.weight(1f)){Text("Live Mobility",fontSize=12.sp,fontWeight=FontWeight.ExtraBold,color=MobilityDark);Text("Rute, ETA, dan posisi Mitra",fontSize=8.sp,color=MobilityMuted)}
                IconButton(onClick={scope.launch{refreshing=true;runCatching{repo.calculateRoute(orderId)};load();refreshing=false}},enabled=!refreshing){Icon(Icons.Default.Refresh,null,tint=MobilityGreen)}
            }
            Spacer(Modifier.height(8.dp))
            MobilityMapPreview(pickup,destination,partner,route?.optString("encodedPolyline")?.ifBlank{null},route?.optString("polylineFormat")?.ifBlank{null})
            Spacer(Modifier.height(9.dp))
            if(distanceKm>0)Text("Perjalanan ${String.format(Locale.US,"%.1f",distanceKm)} km • ETA perjalanan ${eta} mnt",fontSize=9.sp,fontWeight=FontWeight.Bold,color=MobilityDark)
            if(partnerObj!=null){
                Text("${partnerObj.optString("displayName","Mitra GAWONE")} • ${partnerObj.optString("assignmentStatus")}",fontSize=9.sp,fontWeight=FontWeight.Bold,color=MobilityDark)
                if(trackingEta>0)Text("ETA Mitra ${trackingEta} mnt • ${String.format(Locale.US,"%.1f",trackingDistance)} km ke titik berikutnya",fontSize=9.sp,color=MobilityGreen)
                Text(if(partnerObj.optBoolean("locationFresh"))"Lokasi live" else "Lokasi Mitra perlu diperbarui",fontSize=8.sp,color=if(partnerObj.optBoolean("locationFresh"))MobilityGreen else MobilityMuted)
            }else Text("Menunggu Mitra ditetapkan.",fontSize=8.sp,color=MobilityMuted)
            route?.let{r->val provider=r.optString("provider").ifBlank{r.optString("source")};if(provider.isNotBlank())Text("Routing: $provider",fontSize=7.sp,color=MobilityMuted)}
            if(info.isNotBlank()){Spacer(Modifier.height(6.dp));InfoBox(info)}
        }
    }
}
