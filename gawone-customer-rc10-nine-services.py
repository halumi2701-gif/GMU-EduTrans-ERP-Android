from pathlib import Path

root=Path('gawone-customer-production')
main=root/'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
build=root/'app/build.gradle.kts'
s=main.read_text()
old='''                val mapsReady=featureEnabled(runtime,"MAPS")
                val pilotServices=services.filter{it.availability=="AVAILABLE"}.filter{mapsReady || it.code=="CLEANING"}
                if(!mapsReady)item{PilotNoticeCard()}
                items(pilotServices){service->ServiceCard(service){onService(service)}}'''
new='''                val mapsReady=featureEnabled(runtime,"MAPS")
                val codes=listOf("RIDE","CAR","DELIVERY","CLEANING","HANDYMAN","HELPER","TECHNICIAN","DRIVER","BULK_WORKFORCE")
                val byCode=services.associateBy{it.code.uppercase()}
                item{NineServicesSummary(services,mapsReady)}
                if(!mapsReady)item{PilotNoticeCard()}
                items(codes){code->
                    val service=byCode[code]
                    val routeReady=mapsReady || code !in setOf("RIDE","CAR","DELIVERY")
                    if(service!=null && service.availability=="AVAILABLE" && routeReady) ServiceCard(service){onService(service)}
                    else PlannedServiceCard(serviceTitle(code),if(!routeReady)"Menunggu Maps + routing production" else "Belum tersedia di area Anda")
                }'''
if old not in s: raise SystemExit('service block missing')
s=s.replace(old,new,1)
anchor='@Composable fun BrandMark()'
helper='''private fun serviceTitle(code:String)=when(code){"RIDE"->"Ride";"CAR"->"Car";"DELIVERY"->"Delivery";"CLEANING"->"Cleaning";"HANDYMAN"->"Tukang";"HELPER"->"Helper";"TECHNICIAN"->"Teknisi";"DRIVER"->"Driver";"BULK_WORKFORCE"->"Labour";else->code}\n\n@Composable fun NineServicesSummary(services:List<ServiceUi>,mapsReady:Boolean){\n val codes=listOf("RIDE","CAR","DELIVERY","CLEANING","HANDYMAN","HELPER","TECHNICIAN","DRIVER","BULK_WORKFORCE");val live=services.filter{it.availability=="AVAILABLE"}.map{it.code.uppercase()}.toSet();val n=codes.count{live.contains(it)&&(mapsReady||it !in setOf("RIDE","CAR","DELIVERY"))}\n Surface(color=Color.White,shape=RoundedCornerShape(18.dp),border=androidx.compose.foundation.BorderStroke(1.dp,GBorder),modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(14.dp)){Row(Modifier.fillMaxWidth()){Column(Modifier.weight(1f)){Text("9 layanan GAWONE",fontWeight=FontWeight.ExtraBold,fontSize=14.sp);Text("Mobilitas, pengantaran, rumah, teknis, driver, dan tenaga kerja.",fontSize=9.sp,color=GMuted)}};Text("$n/9 aktif",fontSize=9.sp,color=GGreen,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Text(codes.joinToString(" • "){serviceTitle(it)},fontSize=8.sp,color=GMuted,lineHeight=13.sp)}}}\n}\n\n@Composable fun PlannedServiceCard(title:String,reason:String){Surface(color=Color.White,shape=RoundedCornerShape(18.dp),border=androidx.compose.foundation.BorderStroke(1.dp,GBorder),modifier=Modifier.fillMaxWidth().padding(vertical=5.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Schedule,null,tint=GMuted);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.ExtraBold,fontSize=13.sp);Text(reason,fontSize=9.sp,color=GMuted)};Text("SEGERA",fontSize=7.sp,fontWeight=FontWeight.Bold,color=GMuted)}}}\n\n'''
if anchor not in s: raise SystemExit('helper anchor missing')
s=s.replace(anchor,helper+anchor,1)
main.write_text(s)
b=build.read_text()
if 'versionCode = 18' not in b: raise SystemExit('RC9 version missing')
b=b.replace('versionCode = 18','versionCode = 19',1).replace('versionName = "1.0.8-reliability-rc9"','versionName = "1.0.9-nine-services-rc10"',1)
build.write_text(b)
print('GAWONE Customer RC10 nine services applied')
