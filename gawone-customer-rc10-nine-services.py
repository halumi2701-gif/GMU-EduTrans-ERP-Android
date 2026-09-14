from pathlib import Path

root = Path('gawone-customer-production')
main = root / 'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
build = root / 'app/build.gradle.kts'

s = main.read_text()
old = '''                val mapsReady=featureEnabled(runtime,"MAPS")
                val pilotServices=services.filter{it.availability=="AVAILABLE"}.filter{mapsReady || it.code=="CLEANING"}
                if(!mapsReady)item{PilotNoticeCard()}
                items(pilotServices){service->ServiceCard(service){onService(service)}}'''
new = '''                val mapsReady=featureEnabled(runtime,"MAPS")
                val codes=listOf("RIDE","CAR","DELIVERY","CLEANING","HANDYMAN","HELPER","TECHNICIAN","DRIVER","BULK_WORKFORCE")
                val controlledPilotCodes=setOf("CLEANING")
                val byCode=services.associateBy{it.code.uppercase()}
                item{NineServicesSummary(services,mapsReady)}
                if(!mapsReady)item{PilotNoticeCard()}
                items(codes){code->
                    val service=byCode[code]
                    val routeReady=mapsReady || code !in setOf("RIDE","CAR","DELIVERY")
                    val pilotEnabled=code in controlledPilotCodes
                    val liveNow=service!=null && service.availability=="AVAILABLE" && routeReady && pilotEnabled
                    if(liveNow && service!=null){
                        ServiceCard(service){onService(service)}
                    }else{
                        PlannedServiceCard(
                            serviceTitle(code),
                            when{
                                !routeReady -> "Menunggu Maps + routing production"
                                !pilotEnabled -> "Dibuka bertahap setelah gate booking & matching siap"
                                else -> "Belum tersedia di area Anda"
                            }
                        )
                    }
                }'''
if old not in s:
    raise SystemExit('service block missing')
s = s.replace(old, new, 1)

anchor = '@Composable fun BrandMark()'
helper = r'''private fun serviceTitle(code:String):String = when(code){
    "RIDE" -> "Ride"
    "CAR" -> "Car"
    "DELIVERY" -> "Delivery"
    "CLEANING" -> "Cleaning"
    "HANDYMAN" -> "Tukang"
    "HELPER" -> "Helper"
    "TECHNICIAN" -> "Teknisi"
    "DRIVER" -> "Driver"
    "BULK_WORKFORCE" -> "Labour"
    else -> code
}

@Composable
fun NineServicesSummary(services:List<ServiceUi>, mapsReady:Boolean){
    val codes=listOf("RIDE","CAR","DELIVERY","CLEANING","HANDYMAN","HELPER","TECHNICIAN","DRIVER","BULK_WORKFORCE")
    val controlledPilotCodes=setOf("CLEANING")
    val available=services.filter{it.availability=="AVAILABLE"}.map{it.code.uppercase()}.toSet()
    val ready=codes.count{code -> code in controlledPilotCodes && available.contains(code) && (mapsReady || code !in setOf("RIDE","CAR","DELIVERY"))}

    Surface(
        color=Color.White,
        shape=RoundedCornerShape(18.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,GBorder),
        modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)
    ){
        Column(
            Modifier.padding(14.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)
        ){
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment=Alignment.CenterVertically
            ){
                Column(Modifier.weight(1f)){
                    Text("9 layanan GAWONE",fontWeight=FontWeight.ExtraBold,fontSize=14.sp)
                    Text(
                        "Mobilitas, pengantaran, rumah, teknis, driver, dan tenaga kerja.",
                        fontSize=9.sp,
                        color=GMuted,
                        lineHeight=13.sp
                    )
                }
                Text("$ready/9 aktif",fontSize=9.sp,color=GGreen,fontWeight=FontWeight.Bold)
            }
            Text(
                codes.joinToString(" • "){serviceTitle(it)},
                fontSize=8.sp,
                color=GMuted,
                lineHeight=13.sp
            )
        }
    }
}

@Composable
fun PlannedServiceCard(title:String, reason:String){
    Surface(
        color=Color.White,
        shape=RoundedCornerShape(18.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,GBorder),
        modifier=Modifier.fillMaxWidth().padding(vertical=5.dp)
    ){
        Row(
            Modifier.padding(14.dp),
            verticalAlignment=Alignment.CenterVertically
        ){
            Icon(Icons.Default.Schedule,null,tint=GMuted)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)){
                Text(title,fontWeight=FontWeight.ExtraBold,fontSize=13.sp)
                Text(reason,fontSize=9.sp,color=GMuted,lineHeight=13.sp)
            }
            Surface(color=GNeutral,shape=RoundedCornerShape(999.dp)){
                Text(
                    "SEGERA",
                    Modifier.padding(horizontal=8.dp,vertical=4.dp),
                    fontSize=7.sp,
                    fontWeight=FontWeight.Bold,
                    color=GMuted
                )
            }
        }
    }
}

'''
if anchor not in s:
    raise SystemExit('helper anchor missing')
s = s.replace(anchor, helper + anchor, 1)
main.write_text(s)

b = build.read_text()
if 'versionCode = 18' not in b or 'versionName = "1.0.8-reliability-rc9"' not in b:
    raise SystemExit('RC9 version missing')
b = b.replace('versionCode = 18','versionCode = 19',1)
b = b.replace('versionName = "1.0.8-reliability-rc9"','versionName = "1.0.9-nine-services-rc10"',1)
build.write_text(b)

result = main.read_text()
for token in [
    '9 layanan GAWONE',
    '"RIDE","CAR","DELIVERY","CLEANING","HANDYMAN","HELPER","TECHNICIAN","DRIVER","BULK_WORKFORCE"',
    'controlledPilotCodes=setOf("CLEANING")',
    'fun NineServicesSummary',
    'fun PlannedServiceCard'
]:
    if token not in result:
        raise SystemExit('RC10 verification failed: ' + token)

print('GAWONE Customer RC10 nine services applied')
