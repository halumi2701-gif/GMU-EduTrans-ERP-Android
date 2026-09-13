from pathlib import Path

root = Path("gawone-customer-production")
main = root / "app/src/main/java/site/garsyanimultiusaha/gawone/MainActivity.kt"
build = root / "app/build.gradle.kts"

src = main.read_text()
old = 'else items(services.filter{it.availability=="AVAILABLE"}){s->ServiceCard(s){onService(s)}}'
new = '''else {
                val mapsReady=featureEnabled(runtime,"MAPS")
                val pilotServices=services.filter{it.availability=="AVAILABLE"}.filter{mapsReady || it.code=="CLEANING"}
                if(!mapsReady)item{PilotNoticeCard()}
                items(pilotServices){s->ServiceCard(s){onService(s)}}
            }'''
if old not in src and 'val pilotServices=services.filter' not in src:
    raise SystemExit("RC5 service-filter anchor not found")
if old in src:
    src = src.replace(old, new, 1)

marker = '@Composable fun ServiceCard(s:ServiceUi,onClick:()->Unit)'
notice = '''@Composable fun PilotNoticeCard(){
    Surface(color=Color(0xFFFFFBEB),shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFFDE68A)),modifier=Modifier.fillMaxWidth().padding(vertical=6.dp)){
        Column(Modifier.padding(13.dp)){
            Text("E2E Pilot • Cleaning",fontWeight=FontWeight.ExtraBold,fontSize=12.sp,color=Color(0xFF92400E))
            Text("Ride, Car, dan Delivery dibuka setelah Maps + trusted route production aktif. Pilot ini memakai transaksi backend nyata tanpa simulator.",fontSize=9.sp,color=Color(0xFF92400E),lineHeight=14.sp)
        }
    }
}

'''
if 'fun PilotNoticeCard()' not in src:
    if marker not in src:
        raise SystemExit("RC5 notice anchor not found")
    src = src.replace(marker, notice + marker, 1)
main.write_text(src)

b = build.read_text()
b = b.replace('versionCode = 13', 'versionCode = 14', 1)
b = b.replace('versionName = "1.0.3-customer-rc4"', 'versionName = "1.0.4-customer-e2e-pilot"', 1)
if 'versionCode = 14' not in b or '1.0.4-customer-e2e-pilot' not in b:
    raise SystemExit("RC5 version patch failed")
build.write_text(b)

print("GAWONE Customer RC5 E2E pilot patch applied")
