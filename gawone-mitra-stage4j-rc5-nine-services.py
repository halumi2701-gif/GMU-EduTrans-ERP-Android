from pathlib import Path
import sys

root=Path(sys.argv[1] if len(sys.argv)>1 else 'gawone-mitra-stage4j')
main=root/'app/src/main/java/site/garsyanimultiusaha/gawone/mitra/MainActivity.kt'
build=root/'app/build.gradle.kts'
for p in (main,build):
    if not p.exists(): raise SystemExit(f'missing {p}')

s=main.read_text()
s=s.replace('var service by remember { mutableStateOf("RIDE") }','var service by remember { mutableStateOf("CLEANING") }',1)
old='''    LaunchedEffect(tick) { refresh() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(bottom = 18.dp)) {'''
new='''    LaunchedEffect(tick) { refresh() }
    val eligibleServices = remember(dashboard) { partnerEligibleServiceCodes(dashboard) }
    LaunchedEffect(eligibleServices) {
        if (eligibleServices.isNotEmpty() && service !in eligibleServices) service = eligibleServices.first()
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(bottom = 18.dp)) {'''
if old not in s: raise SystemExit('home launched anchor missing')
s=s.replace(old,new,1)
old='''            SectionTitle("Pilih layanan online")
            ServiceSelector(service) { service = it }
            Spacer(Modifier.height(10.dp))
            Button('''
new='''            SectionTitle("Pilih layanan online")
            EligibleServiceSelector(service, eligibleServices) { service = it }
            if (eligibleServices.isEmpty()) InfoCard("Belum ada layanan yang bisa online", "Layanan akan aktif setelah KYC dan verifikasi skill/kendaraan sesuai layanan selesai.")
            Spacer(Modifier.height(10.dp))
            Button('''
if old not in s: raise SystemExit('online selector anchor missing')
s=s.replace(old,new,1)
old='''                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = GGreen)
            ) { if (busy)'''
new='''                enabled = !busy && service in eligibleServices && activePresence == null,
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = GGreen)
            ) { if (busy)'''
if old not in s: raise SystemExit('online button anchor missing')
s=s.replace(old,new,1)
old='''private val SERVICES = listOf("RIDE","CAR","DELIVERY","DRIVER","CLEANING","HANDYMAN","TECHNICIAN","HELPER","WAREHOUSE_HELPER","OFFICE_SUPPORT","EVENT_CREW","BULK_WORKFORCE")

@Composable
private fun ServiceSelector(selected:String,onSelect:(String)->Unit){Column{SERVICES.chunked(3).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){row.forEach{s->FilterChip(selected=selected==s,onClick={onSelect(s)},label={Text(s.replace('_',' ').lowercase().replaceFirstChar{it.uppercase()},fontSize=9.sp)},modifier=Modifier.weight(1f),colors=FilterChipDefaults.filterChipColors(selectedContainerColor=GLight,selectedLabelColor=Color(0xFF15803D)));};repeat(3-row.size){Spacer(Modifier.weight(1f))}}}}}
'''
new='''private val OFFICIAL_SERVICES = listOf("RIDE","CAR","DELIVERY","CLEANING","HANDYMAN","HELPER","TECHNICIAN","DRIVER","BULK_WORKFORCE")

private fun serviceLabel(code:String):String = when(code){
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

private fun partnerEligibleServiceCodes(dashboard:Any?):List<String>{
    val root=dashboard as? JSONObject ?: return emptyList()
    val services=JsonUtil.items(root,"services")
    return OFFICIAL_SERVICES.filter { code ->
        services.any { item ->
            JsonUtil.firstString(item,"serviceCode","service_code")?.uppercase()==code &&
                JsonUtil.firstBoolean(item,"eligible")==true &&
                JsonUtil.firstString(item,"verificationStatus","verification_status")?.uppercase()=="VERIFIED"
        }
    }
}

@Composable
private fun ServiceSelector(selected:String,onSelect:(String)->Unit){Column{OFFICIAL_SERVICES.chunked(3).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){row.forEach{s->FilterChip(selected=selected==s,onClick={onSelect(s)},label={Text(serviceLabel(s),fontSize=9.sp)},modifier=Modifier.weight(1f),colors=FilterChipDefaults.filterChipColors(selectedContainerColor=GLight,selectedLabelColor=Color(0xFF15803D)));};repeat(3-row.size){Spacer(Modifier.weight(1f))}}}}}

@Composable
private fun EligibleServiceSelector(selected:String,eligible:List<String>,onSelect:(String)->Unit){
    val shown=OFFICIAL_SERVICES.filter{it in eligible}
    if(shown.isEmpty()) return
    Column{shown.chunked(3).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){row.forEach{s->FilterChip(selected=selected==s,onClick={onSelect(s)},label={Text(serviceLabel(s),fontSize=9.sp)},modifier=Modifier.weight(1f),colors=FilterChipDefaults.filterChipColors(selectedContainerColor=GLight,selectedLabelColor=Color(0xFF15803D)));};repeat(3-row.size){Spacer(Modifier.weight(1f))}}}}
}
'''
if old not in s: raise SystemExit('services block missing')
s=s.replace(old,new,1)
main.write_text(s)

b=build.read_text()
if 'versionCode = 13' not in b or 'versionName = "1.0.3-stage4j-reliability-rc4"' not in b: raise SystemExit('RC4 version anchor missing')
b=b.replace('versionCode = 13','versionCode = 14',1)
b=b.replace('versionName = "1.0.3-stage4j-reliability-rc4"','versionName = "1.0.4-stage4j-nine-services-rc5"',1)
build.write_text(b)

out=main.read_text()
for token in ['OFFICIAL_SERVICES = listOf("RIDE","CAR","DELIVERY","CLEANING","HANDYMAN","HELPER","TECHNICIAN","DRIVER","BULK_WORKFORCE")','partnerEligibleServiceCodes','EligibleServiceSelector','service in eligibleServices']:
    if token not in out: raise SystemExit('verification missing '+token)
for forbidden in ['WAREHOUSE_HELPER','OFFICE_SUPPORT','EVENT_CREW']:
    if forbidden in out: raise SystemExit('legacy service remains '+forbidden)
print('GAWONE Mitra RC5 nine-service eligibility sync applied')
