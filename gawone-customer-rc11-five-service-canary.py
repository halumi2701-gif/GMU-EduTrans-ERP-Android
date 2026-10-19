from pathlib import Path

root = Path('gawone-customer-production')
main = root / 'app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt'
build = root / 'app/build.gradle.kts'

if not main.exists() or not build.exists():
    raise SystemExit('GAWONE Customer source missing')

s = main.read_text()
old = '''                val mapsReady=featureEnabled(runtime,"MAPS")
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
new = '''                val mapsReady=featureEnabled(runtime,"MAPS")
                val bookingReady=featureEnabled(runtime,"BOOKING")
                val matchingReady=featureEnabled(runtime,"MATCHING")
                val codes=listOf("RIDE","CAR","DELIVERY","CLEANING","HANDYMAN","HELPER","TECHNICIAN","DRIVER","BULK_WORKFORCE")
                val controlledPilotCodes=setOf("CLEANING","HANDYMAN","HELPER","TECHNICIAN","DRIVER")
                val byCode=services.associateBy{it.code.uppercase()}
                item{NineServicesSummary(services,mapsReady,bookingReady,matchingReady)}
                if(!mapsReady)item{PilotNoticeCard()}
                items(codes){code->
                    val service=byCode[code]
                    val routeReady=mapsReady || code !in setOf("RIDE","CAR","DELIVERY")
                    val pilotEnabled=code in controlledPilotCodes
                    val liveNow=service!=null && service.availability=="AVAILABLE" && routeReady && pilotEnabled && bookingReady && matchingReady
                    if(liveNow && service!=null){
                        ServiceCard(service){onService(service)}
                    }else{
                        PlannedServiceCard(
                            serviceTitle(code),
                            when{
                                !routeReady -> "Menunggu Maps + routing production"
                                !pilotEnabled -> "Dibuka bertahap setelah gate layanan siap"
                                !bookingReady || !matchingReady -> "Pilot terbatas — belum dibuka untuk akun ini"
                                else -> "Belum tersedia di area Anda"
                            }
                        )
                    }
                }'''
if old not in s:
    raise SystemExit('RC10 service block missing')
s = s.replace(old,new,1)

s = s.replace(
    'fun NineServicesSummary(services:List<ServiceUi>, mapsReady:Boolean){',
    'fun NineServicesSummary(services:List<ServiceUi>, mapsReady:Boolean, bookingReady:Boolean, matchingReady:Boolean){',
    1
)
s = s.replace(
    'val controlledPilotCodes=setOf("CLEANING")',
    'val controlledPilotCodes=setOf("CLEANING","HANDYMAN","HELPER","TECHNICIAN","DRIVER")',
    1
)
s = s.replace(
    'val ready=codes.count{code -> code in controlledPilotCodes && available.contains(code) && (mapsReady || code !in setOf("RIDE","CAR","DELIVERY"))}',
    'val ready=codes.count{code -> code in controlledPilotCodes && available.contains(code) && bookingReady && matchingReady && (mapsReady || code !in setOf("RIDE","CAR","DELIVERY"))}',
    1
)
main.write_text(s)

b = build.read_text()
if 'versionCode = 19' not in b or 'versionName = "1.0.9-nine-services-rc10"' not in b:
    raise SystemExit('RC10 version anchor missing')
b = b.replace('versionCode = 19','versionCode = 20',1)
b = b.replace('versionName = "1.0.9-nine-services-rc10"','versionName = "1.0.10-five-service-canary-rc11"',1)
build.write_text(b)

result = main.read_text()
for token in [
    'controlledPilotCodes=setOf("CLEANING","HANDYMAN","HELPER","TECHNICIAN","DRIVER")',
    'val bookingReady=featureEnabled(runtime,"BOOKING")',
    'val matchingReady=featureEnabled(runtime,"MATCHING")',
    'Pilot terbatas — belum dibuka untuk akun ini',
    'NineServicesSummary(services,mapsReady,bookingReady,matchingReady)'
]:
    if token not in result:
        raise SystemExit('RC11 verification failed: '+token)

print('GAWONE Customer RC11 five-service canary applied')
