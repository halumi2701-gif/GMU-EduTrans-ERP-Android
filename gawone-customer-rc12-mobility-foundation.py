from pathlib import Path
import sys

root=Path(sys.argv[1] if len(sys.argv)>1 else 'gawone-customer-production')
main=root/'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
repo=root/'app/src/main/java/site/garsyanimultiusaha/gawone/CustomerRepository.kt'
mapper=root/'app/src/main/java/site/garsyanimultiusaha/gawone/GawoneCustomerFailure.kt'
build=root/'app/build.gradle.kts'

for p in (main,repo,mapper,build):
    if not p.exists(): raise SystemExit(f'missing {p}')

s=main.read_text()
old='data class OrderDraftUi(val draftId:String=java.util.UUID.randomUUID().toString(),val address:String="",val duration:Int=2,val notes:String="",val latitude:Double?=null,val longitude:Double?=null,val scheduledStart:String="",val problemDetail:String="",val workerCount:Int=1)'
new='data class OrderDraftUi(val draftId:String=java.util.UUID.randomUUID().toString(),val address:String="",val duration:Int=2,val notes:String="",val latitude:Double?=null,val longitude:Double?=null,val scheduledStart:String="",val problemDetail:String="",val workerCount:Int=1,val pickupAddress:String="",val pickupLatitude:Double?=null,val pickupLongitude:Double?=null,val destinationAddress:String="",val destinationLatitude:Double?=null,val destinationLongitude:Double?=null)'
if old not in s: raise SystemExit('OrderDraftUi anchor missing')
s=s.replace(old,new,1)

old='''                val mapsReady=featureEnabled(runtime,"MAPS")\n                val bookingReady=featureEnabled(runtime,"BOOKING")\n                val matchingReady=featureEnabled(runtime,"MATCHING")'''
new='''                val mapsReady=featureEnabled(runtime,"MAPS")\n                val mobilityReady=featureEnabled(runtime,"MOBILITY")\n                val bookingReady=featureEnabled(runtime,"BOOKING")\n                val matchingReady=featureEnabled(runtime,"MATCHING")'''
if old not in s: raise SystemExit('runtime gate anchor missing')
s=s.replace(old,new,1)
s=s.replace('NineServicesSummary(services,mapsReady,bookingReady,matchingReady)','NineServicesSummary(services,mapsReady,mobilityReady,bookingReady,matchingReady)',1)
s=s.replace('val routeReady=mapsReady || code !in setOf("RIDE","CAR","DELIVERY")','val routeReady=(mapsReady && mobilityReady) || code !in setOf("RIDE","CAR","DELIVERY")',1)
old='''                                !routeReady -> "Menunggu Maps + routing production"\n                                !pilotEnabled -> "Dibuka bertahap setelah gate layanan siap"'''
new='''                                !mapsReady && code in setOf("RIDE","CAR","DELIVERY") -> "Menunggu Maps + routing production"\n                                !mobilityReady && code in setOf("RIDE","CAR","DELIVERY") -> "Flow mobilitas masih dikunci sampai endpoint pickup/tujuan siap"\n                                !pilotEnabled -> "Dibuka bertahap setelah gate layanan siap"'''
if old not in s: raise SystemExit('planned reason anchor missing')
s=s.replace(old,new,1)
old='fun NineServicesSummary(services:List<ServiceUi>, mapsReady:Boolean, bookingReady:Boolean, matchingReady:Boolean){'
new='fun NineServicesSummary(services:List<ServiceUi>, mapsReady:Boolean, mobilityReady:Boolean, bookingReady:Boolean, matchingReady:Boolean){'
if old not in s: raise SystemExit('summary signature missing')
s=s.replace(old,new,1)
s=s.replace('val ready=codes.count{code -> code in controlledPilotCodes && available.contains(code) && bookingReady && matchingReady && (mapsReady || code !in setOf("RIDE","CAR","DELIVERY"))}',
            'val ready=codes.count{code -> code in controlledPilotCodes && available.contains(code) && bookingReady && matchingReady && ((mapsReady && mobilityReady) || code !in setOf("RIDE","CAR","DELIVERY"))}',1)
main.write_text(s)

r=repo.read_text()
anchor='''    suspend fun orderDetail(orderId:String):JSONObject = api.rpc("get_my_customer_order_detail",JSONObject().put("p_order_id",orderId)) as JSONObject\n\n    suspend fun createDraft(serviceCode:String,draft:OrderDraftUi):JSONObject{'''
replacement='''    suspend fun orderDetail(orderId:String):JSONObject = api.rpc("get_my_customer_order_detail",JSONObject().put("p_order_id",orderId)) as JSONObject\n    suspend fun routeMetric(orderId:String):JSONObject = api.rpc("get_my_order_route_metric",JSONObject().put("p_order_id",orderId)) as JSONObject\n\n    suspend fun createDraft(serviceCode:String,draft:OrderDraftUi):JSONObject{'''
if anchor not in r: raise SystemExit('repo method anchor missing')
r=r.replace(anchor,replacement,1)
old='''        val scheduled=code in setOf("HANDYMAN","HELPER","TECHNICIAN","DRIVER")\n        val location=JSONObject().put("type","SERVICE_LOCATION").put("sequence",0).put("label","Lokasi pekerjaan").put("address",draft.address)\n        if(draft.latitude!=null) location.put("latitude",draft.latitude)\n        if(draft.longitude!=null) location.put("longitude",draft.longitude)\n        val req=JSONObject()'''
new='''        val scheduled=code in setOf("HANDYMAN","HELPER","TECHNICIAN","DRIVER")\n        val mobility=code in setOf("RIDE","CAR","DELIVERY")\n        val locations=JSONArray()\n        if(mobility){\n            if(draft.pickupAddress.isBlank() || draft.pickupLatitude==null || draft.pickupLongitude==null ||\n               draft.destinationAddress.isBlank() || draft.destinationLatitude==null || draft.destinationLongitude==null){\n                throw IllegalArgumentException("MOBILITY_ENDPOINTS_REQUIRED")\n            }\n            locations.put(JSONObject().put("type","PICKUP").put("sequence",1).put("label","Lokasi jemput").put("address",draft.pickupAddress).put("latitude",draft.pickupLatitude).put("longitude",draft.pickupLongitude))\n            locations.put(JSONObject().put("type","DESTINATION").put("sequence",2).put("label","Lokasi tujuan").put("address",draft.destinationAddress).put("latitude",draft.destinationLatitude).put("longitude",draft.destinationLongitude))\n        }else{\n            val location=JSONObject().put("type","SERVICE_LOCATION").put("sequence",0).put("label","Lokasi pekerjaan").put("address",draft.address)\n            if(draft.latitude!=null) location.put("latitude",draft.latitude)\n            if(draft.longitude!=null) location.put("longitude",draft.longitude)\n            locations.put(location)\n        }\n        val req=JSONObject()'''
if old not in r: raise SystemExit('repo location anchor missing')
r=r.replace(old,new,1)
old='.put("p_locations",JSONArray().put(location))'
new='.put("p_locations",locations)'
if old not in r: raise SystemExit('p_locations anchor missing')
r=r.replace(old,new,1)
repo.write_text(r)

m=mapper.read_text()
anchor='''        if (has("MAPS_UNAVAILABLE", "CUSTOMER_MAPS_FEATURE_DISABLED", "MAPS_FEATURE_DISABLED")) {'''
block='''        if (has("MOBILITY_ENDPOINTS_REQUIRED", "ROUTE_ENDPOINTS_REQUIRED")) {\n            return CustomerFailureUi(\n                "MOBILITY_ENDPOINTS_REQUIRED",\n                "Lokasi perjalanan belum lengkap",\n                "Tentukan titik jemput dan tujuan dari peta sebelum menghitung rute.",\n                false,\n                CustomerRecoveryAction.RETRY\n            )\n        }\n        if (has("TRUSTED_ROUTE_REQUIRED", "TRUSTED_ROUTE_PENDING")) {\n            return CustomerFailureUi(\n                "TRUSTED_ROUTE_PENDING",\n                "Rute sedang disiapkan",\n                "GAWONE belum menerima jarak dan ETA terpercaya untuk perjalanan ini. Muat ulang setelah rute selesai dihitung.",\n                true,\n                CustomerRecoveryAction.RETRY\n            )\n        }\n        if (has("MAPS_UNAVAILABLE", "CUSTOMER_MAPS_FEATURE_DISABLED", "MAPS_FEATURE_DISABLED")) {'''
if anchor not in m: raise SystemExit('failure mapper maps anchor missing')
m=m.replace(anchor,block,1)
mapper.write_text(m)

b=build.read_text()
if 'versionCode = 20' not in b or 'versionName = "1.0.10-five-service-canary-rc11"' not in b: raise SystemExit('RC11 version anchor missing')
b=b.replace('versionCode = 20','versionCode = 21',1)
b=b.replace('versionName = "1.0.10-five-service-canary-rc11"','versionName = "1.0.11-mobility-foundation-rc12"',1)
build.write_text(b)

checks={
    main:['featureEnabled(runtime,"MOBILITY")','mobilityReady','pickupAddress:String','destinationAddress:String'],
    repo:['get_my_order_route_metric','MOBILITY_ENDPOINTS_REQUIRED','"PICKUP"','"DESTINATION"'],
    mapper:['TRUSTED_ROUTE_PENDING','MOBILITY_ENDPOINTS_REQUIRED'],
    build:['versionCode = 21','versionName = "1.0.11-mobility-foundation-rc12"']
}
for path,tokens in checks.items():
    text=path.read_text()
    for token in tokens:
        if token not in text: raise SystemExit(f'verification missing {token} in {path}')
print('GAWONE Customer RC12 mobility foundation applied')
